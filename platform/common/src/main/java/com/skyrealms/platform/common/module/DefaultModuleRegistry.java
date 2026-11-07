package com.skyrealms.platform.common.module;

import com.skyrealms.platform.api.module.ModuleId;
import com.skyrealms.platform.api.module.ModuleRegistry;
import com.skyrealms.platform.api.module.SkyModule;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe implementation of {@link ModuleRegistry}.
 */
public final class DefaultModuleRegistry implements ModuleRegistry {

    private final Map<ModuleId, SkyModule> modules = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull SkyModule module) {
        Objects.requireNonNull(module, "module cannot be null");
        SkyModule existing = modules.putIfAbsent(module.id(), module);
        if (existing != null) {
            throw new IllegalStateException("Module already registered with ID: " + module.id());
        }
    }

    @Override
    @NotNull
    public Optional<SkyModule> get(@NotNull ModuleId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return Optional.ofNullable(modules.get(id));
    }

    @Override
    @NotNull
    public Collection<SkyModule> all() {
        return Collections.unmodifiableCollection(modules.values());
    }

    @Override
    public boolean isRegistered(@NotNull ModuleId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return modules.containsKey(id);
    }

    public void clear() {
        modules.clear();
    }
}
