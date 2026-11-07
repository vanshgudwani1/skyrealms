package com.skyrealms.platform.common.module;

import com.skyrealms.platform.api.event.EventBus;
import com.skyrealms.platform.api.module.ModuleContext;
import com.skyrealms.platform.api.module.ModuleId;
import com.skyrealms.platform.api.service.ServiceRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Standard implementation of {@link ModuleContext}.
 */
public final class DefaultModuleContext implements ModuleContext {

    private final ModuleId moduleId;
    private final ServiceRegistry services;
    private final EventBus eventBus;
    private final Path dataDirectory;
    private final Logger logger;

    public DefaultModuleContext(
            @NotNull ModuleId moduleId,
            @NotNull ServiceRegistry services,
            @NotNull EventBus eventBus,
            @NotNull Path dataDirectory,
            @NotNull Logger logger
    ) {
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId cannot be null");
        this.services = Objects.requireNonNull(services, "services cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
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
