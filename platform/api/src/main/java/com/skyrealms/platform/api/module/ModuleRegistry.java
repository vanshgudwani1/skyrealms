package com.skyrealms.platform.api.module;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry holding all loaded SkyRealms modules.
 */
public interface ModuleRegistry {

    /**
     * Registers a module.
     *
     * @param module the module to register.
     */
    void register(@NotNull SkyModule module);

    /**
     * Finds a module by its identifier.
     *
     * @param id the module id.
     * @return an Optional containing the module if registered.
     */
    @NotNull
    Optional<SkyModule> get(@NotNull ModuleId id);

    /**
     * @return an unmodifiable collection of all registered modules.
     */
    @NotNull
    Collection<SkyModule> all();

    /**
     * Checks if a module is registered.
     *
     * @param id the module id.
     * @return true if present.
     */
    boolean isRegistered(@NotNull ModuleId id);
}
