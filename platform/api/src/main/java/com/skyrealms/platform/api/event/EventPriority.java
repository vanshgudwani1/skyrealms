package com.skyrealms.platform.api.event;

/**
 * Execution priority for event listeners.
 * Executed in order from LOWEST to MONITOR.
 */
public enum EventPriority {
    LOWEST(0),
    LOW(1),
    NORMAL(2),
    HIGH(3),
    HIGHEST(4),
    MONITOR(5);

    private final int slot;

    EventPriority(int slot) {
        this.slot = slot;
    }

    public int slot() {
        return slot;
    }
}
