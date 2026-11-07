package com.skyrealms.platform.common.module;

import com.skyrealms.platform.api.module.SkyModule;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Resolves module dependency graphs into an executable topological order.
 */
public interface DependencyResolver {

    /**
     * Resolves the given collection of modules into an ordered list where each module appears
     * AFTER all of its required and active optional dependencies.
     *
     * @param modules collection of modules to resolve.
     * @return ordered list of modules ready for sequential onEnable execution.
     * @throws CyclicDependencyException  if a cycle is detected.
     * @throws MissingDependencyException if a required dependency is missing.
     */
    @NotNull
    List<SkyModule> resolve(@NotNull Collection<SkyModule> modules);
}
