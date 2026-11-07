package com.skyrealms.platform.api.event;

import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for event consumers.
 *
 * @param <T> event type.
 */
@FunctionalInterface
public interface EventListener<T extends SkyEvent> {

    /**
     * Called when an event is published.
     *
     * @param event the event instance.
     * @throws Exception if listener processing throws an error.
     */
    void onEvent(@NotNull T event) throws Exception;
}
