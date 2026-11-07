package com.skyrealms.platform.common.event;

import com.skyrealms.platform.api.event.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultEventBusTest {

    private DefaultEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new DefaultEventBus();
    }

    @AfterEach
    void tearDown() {
        eventBus.close();
    }

    static final class TestPlayerJoinEvent implements SkyEvent {
        private final String playerName;
        private final Instant timestamp = Instant.now();

        TestPlayerJoinEvent(String playerName) {
            this.playerName = playerName;
        }

        @Override
        @NotNull
        public String eventName() {
            return "skyrealms.player.join.v1";
        }

        @Override
        @NotNull
        public Instant timestamp() {
            return timestamp;
        }

        public String playerName() {
            return playerName;
        }
    }

    static final class TestCancellableEvent implements CancellableEvent {
        private boolean cancelled = false;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        @NotNull
        public String eventName() {
            return "skyrealms.test.cancellable.v1";
        }

        @Override
        @NotNull
        public Instant timestamp() {
            return Instant.now();
        }
    }

    @Test
    @DisplayName("Should dispatch event to registered listener")
    void shouldDispatchEvent() {
        List<String> received = new ArrayList<>();
        eventBus.subscribe(TestPlayerJoinEvent.class, e -> received.add(e.playerName()));

        eventBus.publish(new TestPlayerJoinEvent("SkyWarrior"));

        assertThat(received).containsExactly("SkyWarrior");
    }

    @Test
    @DisplayName("Should execute listeners in strict priority order (LOWEST to HIGHEST)")
    void shouldExecuteInPriorityOrder() {
        List<String> order = new ArrayList<>();

        eventBus.subscribe(TestPlayerJoinEvent.class, EventPriority.HIGH, e -> order.add("HIGH"));
        eventBus.subscribe(TestPlayerJoinEvent.class, EventPriority.LOWEST, e -> order.add("LOWEST"));
        eventBus.subscribe(TestPlayerJoinEvent.class, EventPriority.MONITOR, e -> order.add("MONITOR"));
        eventBus.subscribe(TestPlayerJoinEvent.class, EventPriority.NORMAL, e -> order.add("NORMAL"));

        eventBus.publish(new TestPlayerJoinEvent("OrderTest"));

        assertThat(order).containsExactly("LOWEST", "NORMAL", "HIGH", "MONITOR");
    }

    @Test
    @DisplayName("Should respect unsubscribe handle")
    void shouldUnsubscribe() {
        List<String> received = new ArrayList<>();
        EventSubscription sub = eventBus.subscribe(TestPlayerJoinEvent.class, e -> received.add(e.playerName()));

        eventBus.publish(new TestPlayerJoinEvent("First"));
        sub.unsubscribe();
        eventBus.publish(new TestPlayerJoinEvent("Second"));

        assertThat(received).containsExactly("First");
    }

    @Test
    @DisplayName("Should isolate listener exception and continue dispatching to others")
    void shouldIsolateListenerException() {
        List<String> received = new ArrayList<>();

        eventBus.subscribe(TestPlayerJoinEvent.class, EventPriority.LOW, e -> {
            throw new RuntimeException("Simulated error in listener");
        });

        eventBus.subscribe(TestPlayerJoinEvent.class, EventPriority.HIGH, e -> received.add(e.playerName()));

        TestPlayerJoinEvent event = eventBus.publish(new TestPlayerJoinEvent("Resilient"));

        assertThat(event.playerName()).isEqualTo("Resilient");
        assertThat(received).containsExactly("Resilient");
    }

    @Test
    @DisplayName("Should publish asynchronously and complete future")
    void shouldPublishAsync() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<TestPlayerJoinEvent> future = eventBus.publishAsync(new TestPlayerJoinEvent("AsyncPlayer"));
        TestPlayerJoinEvent result = future.get(3, TimeUnit.SECONDS);

        assertThat(result.playerName()).isEqualTo("AsyncPlayer");
    }
}
