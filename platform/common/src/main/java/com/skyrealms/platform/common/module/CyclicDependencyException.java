package com.skyrealms.platform.common.module;

import com.skyrealms.platform.api.module.ModuleId;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when a circular dependency is detected among modules.
 */
public final class CyclicDependencyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<ModuleId> cyclePath;

    public CyclicDependencyException(@NotNull List<ModuleId> cyclePath) {
        super("Cyclic dependency detected: " + formatCycle(cyclePath));
        this.cyclePath = Collections.unmodifiableList(cyclePath);
    }

    private static String formatCycle(List<ModuleId> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            sb.append(path.get(i));
            if (i < path.size() - 1) {
                sb.append(" -> ");
            }
        }
        return sb.toString();
    }

    @NotNull
    public List<ModuleId> cyclePath() {
        return cyclePath;
    }
}
