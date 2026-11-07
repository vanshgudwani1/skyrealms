package com.skyrealms.platform.testing;

import com.skyrealms.platform.api.module.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controllable module implementation for lifecycle and resolution unit tests.
 */
public final class DummyTestModule implements SkyModule {

    private final ModuleMetadata metadata;
    private ModuleState state = ModuleState.UNLOADED;

    private final AtomicInteger enableCount = new AtomicInteger();
    private final AtomicInteger disableCount = new AtomicInteger();
    private final AtomicInteger reloadCount = new AtomicInteger();

    private final AtomicBoolean failOnEnable = new AtomicBoolean(false);

    public DummyTestModule(@NotNull ModuleMetadata metadata) {
        this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
    }

    public static DummyTestModule of(@NotNull String id) {
        return new DummyTestModule(ModuleMetadata.builder(ModuleId.of(id)).build());
    }

    public void setFailOnEnable(boolean fail) {
        this.failOnEnable.set(fail);
    }

    @Override
    @NotNull
    public ModuleId id() {
        return metadata.id();
    }

    @Override
    @NotNull
    public ModuleMetadata metadata() {
        return metadata;
    }

    @Override
    @NotNull
    public ModuleState state() {
        return state;
    }

    @Override
    public void onEnable(@NotNull ModuleContext context) throws Exception {
        state = ModuleState.INITIALIZING;
        if (failOnEnable.get()) {
            state = ModuleState.FAILED;
            throw new RuntimeException("Simulated onEnable failure in module: " + id());
        }
        enableCount.incrementAndGet();
        state = ModuleState.ENABLED;
    }

    @Override
    public void onDisable(@NotNull ModuleContext context) throws Exception {
        disableCount.incrementAndGet();
        state = ModuleState.DISABLED;
    }

    @Override
    public void onReload(@NotNull ModuleContext context) throws Exception {
        reloadCount.incrementAndGet();
    }

    public int enableCount() {
        return enableCount.get();
    }

    public int disableCount() {
        return disableCount.get();
    }

    public int reloadCount() {
        return reloadCount.get();
    }
}
