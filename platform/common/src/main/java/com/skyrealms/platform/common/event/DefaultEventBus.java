package com.skyrealms.platform.common.event;

import com.skyrealms.platform.api.event.CancellableEvent;
import com.skyrealms.platform.api.event.EventBus;
import com.skyrealms.platform.api.event.EventListener;
import com.skyrealms.platform.api.event.EventPriority;
import com.skyrealms.platform.api.event.EventSubscription;
import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe event bus supporting prioritized execution and async publishing.
 */
public final class DefaultEventBus implements EventBus, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventBus.class);

    private final Map<Class<?>, List<SubscriptionImpl<?>>> subscriptions = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor;
    private final boolean ownsExecutor;

    public DefaultEventBus() {
        this(Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "SkyRealms-EventBus-Worker");
                    t.setDaemon(true);
                    return t;
                }
        ), true);
    }

    public DefaultEventBus(@NotNull ExecutorService asyncExecutor, boolean ownsExecutor) {
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
        this.ownsExecutor = ownsExecutor;
    }

    @Override
    @NotNull
    public <T extends SkyEvent> EventSubscription subscribe(
            @NotNull Class<T> eventClass,
            @NotNull EventPriority priority,
            @NotNull EventListener<T> listener
    ) {
        Objects.requireNonNull(eventClass, "eventClass cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        SubscriptionImpl<T> sub = new SubscriptionImpl<>(this, eventClass, priority, listener);
        subscriptions.compute(eventClass, (k, list) -> {
            List<SubscriptionImpl<?>> newList = (list == null) ? new CopyOnWriteArrayList<>() : list;
            newList.add(sub);
            // Sort by priority slot ascending
            newList.sort(Comparator.comparingInt(s -> s.priority.slot()));
            return newList;
        });

        return sub;
    }

    @Override
    @NotNull
    @SuppressWarnings("unchecked")
    public <T extends SkyEvent> T publish(@NotNull T event) {
        Objects.requireNonNull(event, "event cannot be null");

        // Collect all compatible listeners (exact type or superclasses/interfaces)
        List<SubscriptionImpl<?>> matched = new ArrayList<>();
        for (Map.Entry<Class<?>, List<SubscriptionImpl<?>>> entry : subscriptions.entrySet()) {
            if (entry.getKey().isInstance(event)) {
                matched.addAll(entry.getValue());
            }
        }

        matched.sort(Comparator.comparingInt(s -> s.priority.slot()));

        for (SubscriptionImpl<?> sub : matched) {
            if (!sub.isActive()) {
                continue;
            }
            try {
                ((SubscriptionImpl<T>) sub).listener.onEvent(event);
            } catch (Throwable t) {
                log.error("Unhandled exception in event listener for '{}': {}", event.eventName(), t.getMessage(), t);
            }
        }

        return event;
    }

    @Override
    @NotNull
    public <T extends SkyEvent> CompletableFuture<T> publishAsync(@NotNull T event) {
        Objects.requireNonNull(event, "event cannot be null");
        return CompletableFuture.supplyAsync(() -> publish(event), asyncExecutor);
    }

    void removeSubscription(SubscriptionImpl<?> sub) {
        subscriptions.computeIfPresent(sub.eventClass, (k, list) -> {
            list.remove(sub);
            return list.isEmpty() ? null : list;
        });
    }

    @Override
    public void close() {
        subscriptions.clear();
        if (ownsExecutor) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class SubscriptionImpl<T extends SkyEvent> implements EventSubscription {
        private final DefaultEventBus bus;
        private final Class<T> eventClass;
        private final EventPriority priority;
        private final EventListener<T> listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private SubscriptionImpl(
                DefaultEventBus bus,
                Class<T> eventClass,
                EventPriority priority,
                EventListener<T> listener
        ) {
            this.bus = bus;
            this.eventClass = eventClass;
            this.priority = priority;
            this.listener = listener;
        }

        @Override
        public void unsubscribe() {
            if (active.compareAndSet(true, false)) {
                bus.removeSubscription(this);
            }
        }

        @Override
        public boolean isActive() {
            return active.get();
        }
    }
}
