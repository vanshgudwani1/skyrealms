package com.skyrealms.farming.module;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.command.FarmCommand;
import com.skyrealms.farming.gui.FarmingGui;
import com.skyrealms.farming.listener.FarmingListener;
import com.skyrealms.farming.mutation.CropMutationEngine;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.farming.service.*;
import com.skyrealms.islands.service.IslandService;
import com.skyrealms.platform.api.module.*;
import com.skyrealms.platform.common.command.DynamicCommandRegistrar;
import com.skyrealms.platform.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * SkyRealms Farming Module.
 * Provides physical structure-based farming generators, progression, crop mutations, DecentHolograms, and economy upgrades.
 */
public final class FarmingModule implements SkyModule {

    public static final ModuleId ID = ModuleId.of("farming");

    private final JavaPlugin plugin;
    private final ModuleMetadata metadata;
    private ModuleState state = ModuleState.UNLOADED;

    private FarmingGeneratorManager generatorManager;
    private FarmingProgressionService progressionService;
    private FarmingHologramService hologramService;
    private FarmingService farmingService;

    public FarmingModule(@NotNull JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.metadata = ModuleMetadata.builder(ID)
                .name("SkyRealms Farming")
                .version("1.0.0")
                .dependsOn(ModuleId.of("database"))
                .dependsOn(ModuleId.of("player"))
                .dependsOn(ModuleId.of("economy"))
                .dependsOn(ModuleId.of("islands"))
                .build();
    }

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

        DatabaseService databaseService = context.services().get(DatabaseService.class);
        EconomyService economyService = context.services().get(EconomyService.class);
        IslandService islandService = Bukkit.getServicesManager().load(IslandService.class);
        if (islandService == null) {
            islandService = context.services().get(IslandService.class);
        }

        // 1. Initialize Repositories and Engines
        FarmingRepository repository = new FarmingRepository(databaseService);
        this.progressionService = new FarmingProgressionService(repository, context.eventBus());
        CropMutationEngine mutationEngine = new CropMutationEngine(
                plugin.getConfig().getDouble("generator.mutations.base-chance", 0.015),
                plugin.getConfig().getDouble("generator.upgrades.mutation.bonus-mutation-chance-per-level", 0.005)
        );

        FarmingUpgradeService upgradeService = new FarmingUpgradeService(repository, economyService, context.eventBus());

        Material coreMaterial = Material.matchMaterial(plugin.getConfig().getString("generator.default.core-material", "LODESTONE"));
        if (coreMaterial == null) coreMaterial = Material.LODESTONE;

        int maxGenerators = plugin.getConfig().getInt("generator.default.max-generators-per-island", 5);
        FarmingGeneratorValidator validator = new FarmingGeneratorValidator(islandService, coreMaterial, maxGenerators);

        this.hologramService = new FarmingHologramService(plugin);
        FarmingTerraformService terraformService = new FarmingTerraformService(plugin, hologramService);
        OverclockManager overclockManager = new OverclockManager(plugin, hologramService);
        HarvestComboTracker comboTracker = new HarvestComboTracker();

        this.generatorManager = new FarmingGeneratorManager(plugin, repository, validator, context.eventBus(), islandService);
        generatorManager.setServices(hologramService, terraformService, overclockManager, comboTracker, economyService, progressionService);

        FarmingGeneratorProcessor processor = new FarmingGeneratorProcessor(
                plugin, progressionService, mutationEngine, repository, context.eventBus(),
                hologramService, overclockManager, comboTracker
        );

        this.farmingService = new DefaultFarmingService(generatorManager, progressionService, upgradeService, mutationEngine, islandService);

        // 2. Register Service in DI Registry and Bukkit ServicesManager
        context.services().register(FarmingService.class, farmingService);
        Bukkit.getServicesManager().register(FarmingService.class, farmingService, plugin, ServicePriority.High);

        // 3. Load active generators from database and re-spawn DecentHolograms
        generatorManager.loadActiveGenerators();

        // 4. Register Event Listeners
        FarmingListener farmingListener = new FarmingListener(farmingService, processor, islandService);
        Bukkit.getPluginManager().registerEvents(farmingListener, plugin);
        Bukkit.getPluginManager().registerEvents(new FarmingGui(farmingService, FarmingGui.GuiType.MAIN_MENU, net.kyori.adventure.text.Component.empty(), null), plugin);

        // 5. Register /farm command via DynamicCommandRegistrar
        FarmCommand farmCommand = new FarmCommand(farmingService, islandService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "farm",
                "Primary SkyRealms farming generator and progression command",
                "/farm [help|stats|generators|generator|core]",
                List.of("farming"),
                "skyrealms.farming.base",
                PermissionDefault.TRUE,
                "You do not have permission to use /farm.",
                farmCommand,
                farmCommand
        );

        state = ModuleState.ENABLED;
        context.logger().info("SkyRealms Farming module successfully enabled with DecentHolograms & Auto-Terraforming.");
    }

    @Override
    public void onDisable(@NotNull ModuleContext context) throws Exception {
        if (generatorManager != null) {
            generatorManager.close();
        }
        if (progressionService != null) {
            progressionService.close();
        }

        Bukkit.getServicesManager().unregister(FarmingService.class, farmingService);

        state = ModuleState.DISABLED;
        context.logger().info("SkyRealms Farming module disabled.");
    }

    public FarmingService farmingService() {
        return farmingService;
    }

    public FarmingHologramService hologramService() {
        return hologramService;
    }
}
