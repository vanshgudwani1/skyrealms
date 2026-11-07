package com.skyrealms.platform.api.module;

/**
 * Represents the lifecycle state of a SkyRealms module.
 */
public enum ModuleState {
    /**
     * Module has been instantiated but not yet registered.
     */
    UNLOADED,

    /**
     * Module is resolving dependencies and preparing resources.
     */
    INITIALIZING,

    /**
     * Module is actively running and serving traffic.
     */
    ENABLED,

    /**
     * Module has been gracefully stopped.
     */
    DISABLED,

    /**
     * Module encountered a critical failure during initialization or operation.
     */
    FAILED
}
