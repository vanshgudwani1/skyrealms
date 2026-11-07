package com.skyrealms.platform.common.module;

import com.skyrealms.platform.api.event.EventBus;
import com.skyrealms.platform.api.module.*;
import com.skyrealms.platform.api.service.ServiceRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Robust lifecycle manager executing modules in DAG order with fault isolation.
 */
public final class DefaultModuleLifecycleManager implements ModuleLifecycleManager {

    private final ModuleRegistry registry;
    private final DependencyResolver resolver;
    private final ServiceRegistry services;
    private final EventBus eventBus;
    private final Path baseDataDirectory;
    private final Logger logger;

    private final List<SkyModule> enabledModules = new CopyOnWriteArrayList<>();
    private final List<SkyModule> failedModules = new CopyOnWriteArrayList<>();
    private final Map<ModuleId, ModuleContext> contexts = new HashMap<>();

    public DefaultModuleLifecycleManager(
            @NotNull ModuleRegistry registry,
            @NotNull DependencyResolver resolver,
            @NotNull ServiceRegistry services,
            @NotNull EventBus eventBus,
            @NotNull Path baseDataDirectory,
            @NotNull Logger logger
    ) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
        this.services = Objects.requireNonNull(services, "services cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
        this.baseDataDirectory = Objects.requireNonNull(baseDataDirectory, "baseDataDirectory cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    @Override
    public void enableAll() {
        Collection<SkyModule> allModules = registry.all();
        if (allModules.isEmpty()) {
            logger.info("No SkyRealms modules registered to enable.");
            return;
        }

        List<SkyModule> ordered;
        try {
            ordered = resolver.resolve(allModules);
        } catch (Exception e) {
            logger.error("Failed to resolve module dependency graph: {}", e.getMessage(), e);
            throw e;
        }

        Set<ModuleId> failedIds = new HashSet<>();

        for (SkyModule module : ordered) {
            ModuleId id = module.id();

            if (module.state() == ModuleState.ENABLED || enabledModules.contains(module)) {
                continue;
            }

            // Check if any required dependency failed
            boolean dependencyFailed = false;
            for (ModuleId dep : module.metadata().dependencies()) {
                if (failedIds.contains(dep)) {
                    logger.error("Module '{}' cannot enable because required dependency '{}' failed.", id, dep);
                    dependencyFailed = true;
                    break;
                }
            }

            if (dependencyFailed) {
                failedIds.add(id);
                failedModules.add(module);
                continue;
            }

            try {
                Path moduleDir = baseDataDirectory.resolve(id.value());
                if (!Files.exists(moduleDir)) {
                    Files.createDirectories(moduleDir);
                }

                Logger moduleLogger = LoggerFactory.getLogger("SkyRealms:" + id.value());
                ModuleContext context = new DefaultModuleContext(id, services, eventBus, moduleDir, moduleLogger);
                contexts.put(id, context);

                logger.info("Enabling module '{}' (v{})...", module.metadata().name(), module.metadata().version());
                module.onEnable(context);
                enabledModules.add(module);
                logger.info("Module '{}' successfully enabled.", id);
            } catch (Throwable t) {
                logger.error("Error enabling module '{}': {}", id, t.getMessage(), t);
                failedIds.add(id);
                failedModules.add(module);
            }
        }

        logger.info("Module enable complete. Active: {}, Failed: {}", enabledModules.size(), failedModules.size());
    }

    @Override
    public void disableAll() {
        logger.info("Disabling active SkyRealms modules in reverse dependency order...");
        List<SkyModule> reverseList = new ArrayList<>(enabledModules);
        Collections.reverse(reverseList);

        for (SkyModule module : reverseList) {
            ModuleId id = module.id();
            ModuleContext context = contexts.get(id);
            try {
                logger.info("Disabling module '{}'...", id);
                module.onDisable(context);
                logger.info("Module '{}' disabled.", id);
            } catch (Throwable t) {
                logger.error("Error during disable of module '{}': {}", id, t.getMessage(), t);
            }
        }

        enabledModules.clear();
        contexts.clear();
        logger.info("All SkyRealms modules disabled.");
    }

    @Override
    public void reloadAll() {
        logger.info("Reloading active SkyRealms modules...");
        for (SkyModule module : enabledModules) {
            ModuleId id = module.id();
            ModuleContext context = contexts.get(id);
            try {
                logger.info("Reloading module '{}'...", id);
                module.onReload(context);
                logger.info("Module '{}' reloaded.", id);
            } catch (Throwable t) {
                logger.error("Error during reload of module '{}': {}", id, t.getMessage(), t);
            }
        }
    }

    @Override
    @NotNull
    public List<SkyModule> enabledModules() {
        return Collections.unmodifiableList(enabledModules);
    }

    @Override
    @NotNull
    public List<SkyModule> failedModules() {
        return Collections.unmodifiableList(failedModules);
    }
}
