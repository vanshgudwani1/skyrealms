package com.skyrealms.platform.api.module;

import com.skyrealms.platform.api.event.EventBus;
import com.skyrealms.platform.api.service.ServiceRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Execution context provided to a module during its lifecycle.
 */
public interface ModuleContext {

    /**
     * @return the module ID of the receiving module.
     */
    @NotNull
    ModuleId moduleId();

    /**
     * @return the global type-safe service registry.
     */
    @NotNull
    ServiceRegistry services();

    /**
     * @return the global event bus.
     */
    @NotNull
    EventBus eventBus();

    /**
     * @return the dedicated data directory for this module's configs and cache.
     */
    @NotNull
    Path dataDirectory();

    /**
     * @return the structured SLF4J logger for this module.
     */
    @NotNull
    Logger logger();
}
