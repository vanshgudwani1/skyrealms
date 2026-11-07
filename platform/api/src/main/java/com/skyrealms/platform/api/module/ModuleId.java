package com.skyrealms.platform.api.module;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Unique identifier for a SkyRealms module.
 */
public final class ModuleId implements Comparable<ModuleId> {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-z0-9-_]{2,32}$");

    private final String value;

    private ModuleId(@NotNull String value) {
        this.value = value.toLowerCase().trim();
        if (!VALID_NAME.matcher(this.value).matches()) {
            throw new IllegalArgumentException("Module ID must match [a-z0-9-_]{2,32}, got: '" + value + "'");
        }
    }

    @NotNull
    public static ModuleId of(@NotNull String value) {
        return new ModuleId(value);
    }

    @NotNull
    public String value() {
        return value;
    }

    @Override
    public int compareTo(@NotNull ModuleId o) {
        return this.value.compareTo(o.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModuleId moduleId = (ModuleId) o;
        return Objects.equals(value, moduleId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
