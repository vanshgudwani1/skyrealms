package com.skyrealms.platform.api.module;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Orchestrates module lifecycle transitions.
 */
public interface ModuleLifecycleManager {

    /**
     * Resolves and enables all registered modules in dependency order.
     */
    void enableAll();

    /**
     * Disables all active modules in reverse dependency order.
     */
    void disableAll();

    /**
     * Reloads configuration for all active modules.
     */
    void reloadAll();

    /**
     * @return the list of currently enabled modules in their enablement order.
     */
    @NotNull
    List<SkyModule> enabledModules();

    /**
     * @return the list of modules that failed to enable.
     */
    @NotNull
    List<SkyModule> failedModules();
}
