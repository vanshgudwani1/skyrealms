package com.skyrealms.platform.api.event;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Event bus contract for publishing and subscribing to versioned domain events.
 */
public interface EventBus {

    /**
     * Subscribes a listener to an event type with specified priority.
     *
     * @param eventClass the event class.
     * @param priority   execution priority.
     * @param listener   the event callback.
     * @param <T>        the event type.
     * @return an EventSubscription handle to unregister when needed.
     */
    @NotNull
    <T extends SkyEvent> EventSubscription subscribe(
            @NotNull Class<T> eventClass,
            @NotNull EventPriority priority,
            @NotNull EventListener<T> listener
    );

    /**
     * Subscribes a listener with NORMAL priority.
     */
    @NotNull
    default <T extends SkyEvent> EventSubscription subscribe(
            @NotNull Class<T> eventClass,
            @NotNull EventListener<T> listener
    ) {
        return subscribe(eventClass, EventPriority.NORMAL, listener);
    }

    /**
     * Synchronously publishes an event across all registered listeners in priority order.
     *
     * @param event the event to dispatch.
     * @param <T>   the event type.
     * @return the published event (which may have been modified or cancelled).
     */
    @NotNull
    <T extends SkyEvent> T publish(@NotNull T event);

    /**
     * Asynchronously publishes an event using the underlying executor.
     *
     * @param event the event to dispatch.
     * @param <T>   the event type.
     * @return a CompletableFuture completing with the published event.
     */
    @NotNull
    <T extends SkyEvent> CompletableFuture<T> publishAsync(@NotNull T event);
}
