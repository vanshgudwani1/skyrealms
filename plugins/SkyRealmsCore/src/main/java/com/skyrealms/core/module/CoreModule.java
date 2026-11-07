package com.skyrealms.core.module;

import com.skyrealms.platform.api.module.*;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Root core module for SkyRealms.
 */
public final class CoreModule implements SkyModule {

    private static final ModuleId ID = ModuleId.of("core");

    private final ModuleMetadata metadata = ModuleMetadata.builder(ID)
            .name("SkyRealms Core")
            .version("1.0.0")
            .build();

    private ModuleState state = ModuleState.UNLOADED;

    @Override
    @NotNull
    public ModuleId id() {
        return ID;
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
        context.logger().info("========================================");
        context.logger().info("  SkyRealms Core Engine Initialized");
        context.logger().info("  Version: {}", metadata.version());
        context.logger().info("========================================");
        state = ModuleState.ENABLED;
    }

    @Override
    public void onDisable(@NotNull ModuleContext context) throws Exception {
        context.logger().info("SkyRealms Core Module disabling...");
        state = ModuleState.DISABLED;
    }

    @Override
    public void onReload(@NotNull ModuleContext context) throws Exception {
        context.logger().info("SkyRealms Core Module reloaded.");
    }
}
