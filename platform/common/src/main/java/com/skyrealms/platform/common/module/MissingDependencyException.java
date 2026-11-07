package com.skyrealms.platform.common.module;

import com.skyrealms.platform.api.module.ModuleId;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown when a module depends on a required module that is not registered.
 */
public final class MissingDependencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ModuleId dependent;
    private final ModuleId required;

    public MissingDependencyException(@NotNull ModuleId dependent, @NotNull ModuleId required) {
        super("Module '" + dependent + "' requires missing dependency '" + required + "'");
        this.dependent = dependent;
        this.required = required;
    }

    @NotNull
    public ModuleId dependent() {
        return dependent;
    }

    @NotNull
    public ModuleId required() {
        return required;
    }
}
