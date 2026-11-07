package com.skyrealms.platform.testing;

import com.skyrealms.platform.api.event.EventBus;
import com.skyrealms.platform.api.module.ModuleContext;
import com.skyrealms.platform.api.module.ModuleId;
import com.skyrealms.platform.api.service.ServiceRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Test harness implementation of {@link ModuleContext}.
 */
public final class TestModuleContext implements ModuleContext {

    private final ModuleId moduleId;
    private final ServiceRegistry services;
    private final EventBus eventBus;
    private final Path dataDirectory;
    private final Logger logger;

    public TestModuleContext(
            @NotNull ModuleId moduleId,
            @NotNull ServiceRegistry services,
            @NotNull EventBus eventBus
    ) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId cannot be null");
        this.services = Objects.requireNonNull(services, "services cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
        try {
            this.dataDirectory = Files.createTempDirectory("skyrealms-test-" + moduleId.value());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary test directory", e);
        }
        this.logger = LoggerFactory.getLogger("Test:" + moduleId.value());
    }

    @Override
    @NotNull
    public ModuleId moduleId() {
        return moduleId;
    }

    @Override
    @NotNull
    public ServiceRegistry services() {
        return services;
    }

    @Override
    @NotNull
    public EventBus eventBus() {
        return eventBus;
    }

    @Override
    @NotNull
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    @NotNull
    public Logger logger() {
        return logger;
    }
}
