package com.skyrealms.platform.api.module;

import org.jetbrains.annotations.NotNull;

/**
 * Foundational contract for all SkyRealms gameplay, infrastructure, and feature modules.
 */
public interface SkyModule {

    /**
     * @return unique module identifier.
     */
    @NotNull
    ModuleId id();

    /**
     * @return module metadata and dependency constraints.
     */
    @NotNull
    ModuleMetadata metadata();

    /**
     * @return current lifecycle state.
     */
    @NotNull
    ModuleState state();

    /**
     * Invoked when the module is being enabled after all required dependencies have successfully enabled.
     *
     * @param context the module execution context.
     * @throws Exception if initialization fails.
     */
    void onEnable(@NotNull ModuleContext context) throws Exception;

    /**
     * Invoked when the module is being disabled. Should release all resources, listeners, and tasks.
     *
     * @param context the module execution context.
     * @throws Exception if clean shutdown encounters an error.
     */
    void onDisable(@NotNull ModuleContext context) throws Exception;

    /**
     * Invoked when module configuration is reloaded.
     *
     * @param context the module execution context.
     * @throws Exception if reload fails.
     */
    default void onReload(@NotNull ModuleContext context) throws Exception {
        // Optional hook for modules supporting hot configuration reload
    }
}
