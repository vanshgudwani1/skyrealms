package com.skyrealms.platform.api.event;

/**
 * Event that can be cancelled by listeners.
 */
public interface CancellableEvent extends SkyEvent {

    /**
     * @return true if the event has been cancelled.
     */
    boolean isCancelled();

    /**
     * Sets the cancelled state of the event.
     *
     * @param cancelled true to cancel.
     */
    void setCancelled(boolean cancelled);
}
