package com.skyrealms.platform.api.event;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Base marker interface for all SkyRealms events.
 */
public interface SkyEvent {

    /**
     * Versioned event identifier (e.g., 'skyrealms.player.created.v1').
     *
     * @return the unique event identifier.
     */
    @NotNull
    String eventName();

    /**
     * @return timestamp when the event occurred in UTC.
     */
    @NotNull
    Instant timestamp();

    /**
     * Indicates whether this event is a durable event that must be stored in the outbox
     * for asynchronous persistent processing.
     *
     * @return true if durable, false if transient in-memory only.
     */
    default boolean isDurable() {
        return false;
    }
}
