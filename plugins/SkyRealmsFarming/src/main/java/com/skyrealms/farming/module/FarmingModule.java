package com.skyrealms.farming.module;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.command.FarmCommand;
import com.skyrealms.farming.command.GardenCommand;
import com.skyrealms.farming.garden.*;
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
 * Completely redesigned natural progression Garden Engine with shared skyrealms_garden world,
 * 256x256 isolated regions, 16 crop plots (2x8) in a glass greenhouse dome, and retired generator mechanics.
 */
public final class FarmingModule implements SkyModule {

    public static final ModuleId ID = ModuleId.of("farming");

    private final JavaPlugin plugin;
    private final ModuleMetadata metadata;
    private ModuleState state = ModuleState.UNLOADED;

    private FarmingGeneratorManager generatorManager;
    private FarmingProgressionService progressionService;
    private FarmingHologramService hologramService;
    private DefaultGardenService gardenService;
    private DefaultFarmingService farmingService;

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

        // 2. Initialize SkyRealms Garden Foundation (World, Allocator, Dome Generator)
        GardenWorldManager worldManager = new GardenWorldManager(plugin);
        worldManager.getOrCreateGardenWorld();
        GardenSpatialAllocator allocator = new GardenSpatialAllocator(512, 256);
        this.gardenService = new DefaultGardenService(plugin, repository, worldManager, allocator, islandService);

        FarmingGeneratorProcessor processor = new FarmingGeneratorProcessor(
                plugin, progressionService, mutationEngine, repository, context.eventBus(),
                hologramService, overclockManager, comboTracker
        );

        this.farmingService = new DefaultFarmingService(generatorManager, progressionService, upgradeService, mutationEngine, islandService, gardenService);

        // 3. Register Services in DI Registry and Bukkit ServicesManager
        context.services().register(FarmingService.class, farmingService);
        context.services().register(GardenService.class, gardenService);
        Bukkit.getServicesManager().register(FarmingService.class, farmingService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(GardenService.class, gardenService, plugin, ServicePriority.High);

        // 4. Register Event Listeners
        NaturalFarmingEngine naturalEngine = new NaturalFarmingEngine(plugin, gardenService, progressionService, context.eventBus());
        NaturalFarmingListener naturalFarmingListener = new NaturalFarmingListener(gardenService, naturalEngine, context.eventBus());
        FarmingListener farmingListener = new FarmingListener(farmingService, processor, islandService);
        GardenProtectionListener gardenProtectionListener = new GardenProtectionListener(gardenService, islandService);

        Bukkit.getPluginManager().registerEvents(naturalFarmingListener, plugin);
        Bukkit.getPluginManager().registerEvents(farmingListener, plugin);
        Bukkit.getPluginManager().registerEvents(gardenProtectionListener, plugin);
        Bukkit.getPluginManager().registerEvents(new FarmingGui(farmingService, FarmingGui.GuiType.MAIN_MENU, net.kyori.adventure.text.Component.empty(), null), plugin);

        // 5. Register /garden and /farm commands via DynamicCommandRegistrar
        GardenCommand gardenCommand = new GardenCommand(gardenService, islandService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "garden",
                "Primary SkyRealms Greenhouse Garden command",
                "/garden [home|leave|info|plots|help]",
                List.of("g"),
                "skyrealms.garden.base",
                PermissionDefault.TRUE,
                "You do not have permission to use /garden.",
                gardenCommand,
                gardenCommand
        );

        FarmCommand farmCommand = new FarmCommand(farmingService, islandService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "farm",
                "SkyRealms farming command (redirects to Garden)",
                "/farm [stats|help]",
                List.of("farming"),
                "skyrealms.farming.base",
                PermissionDefault.TRUE,
                "You do not have permission to use /farm.",
                farmCommand,
                farmCommand
        );

        state = ModuleState.ENABLED;
        context.logger().info("SkyRealms Farming module enabled with Greenhouse Garden Foundation. Generators safely retired.");
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
        if (gardenService != null) {
            Bukkit.getServicesManager().unregister(GardenService.class, gardenService);
        }

        state = ModuleState.DISABLED;
        context.logger().info("SkyRealms Farming module disabled.");
    }

    public FarmingService farmingService() {
        return farmingService;
    }

    public GardenService gardenService() {
        return gardenService;
    }

    public FarmingHologramService hologramService() {
        return hologramService;
    }
}
