package com.skyrealms.platform.api.module;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Metadata defining a module's name, version, and dependency graph constraints.
 */
public final class ModuleMetadata {

    private final ModuleId id;
    private final String name;
    private final String version;
    private final Set<ModuleId> dependencies;
    private final Set<ModuleId> optionalDependencies;

    private ModuleMetadata(
            @NotNull ModuleId id,
            @NotNull String name,
            @NotNull String version,
            @NotNull Set<ModuleId> dependencies,
            @NotNull Set<ModuleId> optionalDependencies
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.dependencies = Collections.unmodifiableSet(new HashSet<>(dependencies));
        this.optionalDependencies = Collections.unmodifiableSet(new HashSet<>(optionalDependencies));
    }

    @NotNull
    public static Builder builder(@NotNull ModuleId id) {
        return new Builder(id);
    }

    @NotNull
    public ModuleId id() {
        return id;
    }

    @NotNull
    public String name() {
        return name;
    }

    @NotNull
    public String version() {
        return version;
    }

    @NotNull
    public Set<ModuleId> dependencies() {
        return dependencies;
    }

    @NotNull
    public Set<ModuleId> optionalDependencies() {
        return optionalDependencies;
    }

    @Override
    public String toString() {
        return "ModuleMetadata{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", dependencies=" + dependencies +
                ", optionalDependencies=" + optionalDependencies +
                '}';
    }

    public static final class Builder {
        private final ModuleId id;
        private String name;
        private String version = "1.0.0";
        private final Set<ModuleId> dependencies = new HashSet<>();
        private final Set<ModuleId> optionalDependencies = new HashSet<>();

        private Builder(@NotNull ModuleId id) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.name = id.value();
        }

        public Builder name(@NotNull String name) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            return this;
        }

        public Builder version(@NotNull String version) {
            this.version = Objects.requireNonNull(version, "version cannot be null");
            return this;
        }

        public Builder dependsOn(@NotNull ModuleId dependency) {
            this.dependencies.add(Objects.requireNonNull(dependency, "dependency cannot be null"));
            return this;
        }

        public Builder optionallyDependsOn(@NotNull ModuleId optionalDependency) {
            this.optionalDependencies.add(Objects.requireNonNull(optionalDependency, "optionalDependency cannot be null"));
            return this;
        }

        public ModuleMetadata build() {
            return new ModuleMetadata(id, name, version, dependencies, optionalDependencies);
        }
    }
}
