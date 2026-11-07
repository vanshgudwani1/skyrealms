package com.skyrealms.platform.api.event;

/**
 * Handle representing an active event listener registration.
 */
public interface EventSubscription {

    /**
     * Unregisters the listener from the event bus.
     */
    void unsubscribe();

    /**
     * @return true if the subscription is currently active.
     */
    boolean isActive();
}
