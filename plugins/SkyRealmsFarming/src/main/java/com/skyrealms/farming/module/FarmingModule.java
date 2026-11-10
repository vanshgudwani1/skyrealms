package com.skyrealms.farming.module;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.command.FarmCommand;
import com.skyrealms.farming.command.GardenCommand;
import com.skyrealms.farming.garden.*;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.garden.ability.GardenHarvestAndAbilityListener;
import com.skyrealms.farming.garden.ability.VerdantAuraManager;
import com.skyrealms.farming.garden.cinematic.DivineCinematicService;
import com.skyrealms.farming.garden.harvest.GardenBalanceConfig;
import com.skyrealms.farming.garden.harvest.HarvestRewardPipeline;
import com.skyrealms.farming.garden.item.GardenItemFactory;
import com.skyrealms.farming.garden.journal.BotanicalJournalService;
import com.skyrealms.farming.garden.journal.DefaultBotanicalJournalService;
import com.skyrealms.farming.garden.mastery.CropMasteryGuiListener;
import com.skyrealms.farming.garden.mastery.CropMasteryService;
import com.skyrealms.farming.garden.mastery.DefaultCropMasteryService;
import com.skyrealms.farming.garden.offering.DefaultOfferingService;
import com.skyrealms.farming.garden.offering.OfferingListener;
import com.skyrealms.farming.garden.offering.OfferingService;
import com.skyrealms.farming.garden.plot.GardenPlotCustomizationService;
import com.skyrealms.farming.garden.plot.PlotEditorAndPlantListener;
import com.skyrealms.farming.garden.plot.PlotEditorService;
import com.skyrealms.farming.gui.FarmingGui;
import com.skyrealms.farming.listener.FarmingListener;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.farming.mcmmo.McMMOIntegrationListener;
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
 * Integrates mcMMO (Farming/Herbalism only) with the Sakura Glass Garden conservatory,
 * centralized HarvestRewardPipeline, Garden-only abilities, Crop Mastery, and Botanical Journal.
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

        // 1. Repositories, Collections, Legacy Retirement Support
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

        // 2. SkyRealms Garden Foundation (World, Allocator, Dome Generator)
        GardenBalanceConfig balanceConfig = GardenBalanceConfig.fromConfig(plugin.getConfig());
        GardenWorldManager worldManager = new GardenWorldManager(plugin);
        worldManager.getOrCreateGardenWorld();
        GardenSpatialAllocator allocator = new GardenSpatialAllocator(
                plugin.getConfig().getInt("garden.grid.spacing", GardenSpatialAllocator.DEFAULT_SPACING),
                plugin.getConfig().getInt("garden.grid.region-size", GardenSpatialAllocator.DEFAULT_REGION_SIZE)
        );
        this.gardenService = new DefaultGardenService(plugin, repository, worldManager, allocator, islandService);

        FarmingGeneratorProcessor processor = new FarmingGeneratorProcessor(
                plugin, progressionService, mutationEngine, repository, context.eventBus(),
                hologramService, overclockManager, comboTracker
        );
        this.farmingService = new DefaultFarmingService(generatorManager, progressionService, upgradeService, mutationEngine, islandService, gardenService);

        context.services().register(FarmingService.class, farmingService);
        context.services().register(GardenService.class, gardenService);
        Bukkit.getServicesManager().register(FarmingService.class, farmingService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(GardenService.class, gardenService, plugin, ServicePriority.High);

        // 3. Offering, mcMMO Farming Bridge, Garden Abilities, Crop Mastery, Botanical Journal & Reward Pipeline
        DivineCinematicService cinematicService = new DivineCinematicService(plugin, gardenService, repository, islandService);
        DefaultOfferingService offeringService = new DefaultOfferingService(plugin, repository, islandService, cinematicService);
        McMMOFarmingService mcMmoService = new McMMOFarmingService();
        GardenAbilityService abilityService = new GardenAbilityService(balanceConfig);
        VerdantAuraManager auraManager = new VerdantAuraManager(gardenService, islandService, mcMmoService, balanceConfig);
        DefaultCropMasteryService masteryService = new DefaultCropMasteryService(plugin, repository, economyService, mcMmoService, abilityService);
        DefaultBotanicalJournalService journalService = new DefaultBotanicalJournalService(plugin, repository, progressionService, masteryService, mcMmoService);

        HarvestRewardPipeline rewardPipeline = new HarvestRewardPipeline(
                balanceConfig,
                abilityService,
                mcMmoService,
                masteryService,
                progressionService,
                journalService,
                economyService,
                auraManager,
                context.eventBus(),
                plugin.getLogger()
        );

        GardenItemFactory.registerRecipes(plugin);

        GardenPlotCustomizationService customizationService = new GardenPlotCustomizationService(
                plugin, repository, gardenService, economyService, mcMmoService, masteryService
        );
        PlotEditorService editorService = new PlotEditorService(
                gardenService, islandService, customizationService, mcMmoService
        );

        context.services().register(OfferingService.class, offeringService);
        context.services().register(McMMOFarmingService.class, mcMmoService);
        context.services().register(GardenAbilityService.class, abilityService);
        context.services().register(CropMasteryService.class, masteryService);
        context.services().register(BotanicalJournalService.class, journalService);
        context.services().register(HarvestRewardPipeline.class, rewardPipeline);
        context.services().register(GardenPlotCustomizationService.class, customizationService);
        context.services().register(PlotEditorService.class, editorService);

        Bukkit.getServicesManager().register(OfferingService.class, offeringService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(McMMOFarmingService.class, mcMmoService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(GardenAbilityService.class, abilityService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(CropMasteryService.class, masteryService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(BotanicalJournalService.class, journalService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(HarvestRewardPipeline.class, rewardPipeline, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(GardenPlotCustomizationService.class, customizationService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(PlotEditorService.class, editorService, plugin, ServicePriority.High);

        // 4. Register Event Listeners
        FarmingListener farmingListener = new FarmingListener(farmingService, processor, islandService);
        GardenProtectionListener gardenProtectionListener = new GardenProtectionListener(gardenService, islandService);
        OfferingListener offeringListener = new OfferingListener(offeringService, islandService, cinematicService);
        McMMOIntegrationListener mcMmoListener = new McMMOIntegrationListener(gardenService, islandService, masteryService, abilityService);
        GardenHarvestAndAbilityListener harvestAndAbilityListener = new GardenHarvestAndAbilityListener(
                gardenService, islandService, mcMmoService, abilityService, masteryService, journalService, rewardPipeline,
                customizationService, editorService
        );
        CropMasteryGuiListener guiListener = new CropMasteryGuiListener(masteryService, journalService, mcMmoService, abilityService, auraManager);
        PlotEditorAndPlantListener plotEditorListener = new PlotEditorAndPlantListener(
                gardenService, customizationService, editorService, mcMmoService, repository
        );

        Bukkit.getPluginManager().registerEvents(farmingListener, plugin);
        Bukkit.getPluginManager().registerEvents(gardenProtectionListener, plugin);
        Bukkit.getPluginManager().registerEvents(offeringListener, plugin);
        Bukkit.getPluginManager().registerEvents(mcMmoListener, plugin);
        Bukkit.getPluginManager().registerEvents(auraManager, plugin);
        Bukkit.getPluginManager().registerEvents(harvestAndAbilityListener, plugin);
        Bukkit.getPluginManager().registerEvents(guiListener, plugin);
        Bukkit.getPluginManager().registerEvents(plotEditorListener, plugin);
        Bukkit.getPluginManager().registerEvents(new FarmingGui(farmingService, FarmingGui.GuiType.MAIN_MENU, net.kyori.adventure.text.Component.empty(), null), plugin);

        // 5. Register /garden and /farm commands via DynamicCommandRegistrar
        GardenCommand gardenCommand = new GardenCommand(
                gardenService, islandService, offeringService, masteryService,
                mcMmoService, abilityService, auraManager, journalService,
                customizationService, editorService
        );
        DynamicCommandRegistrar.register(
                "skyrealms",
                "garden",
                "Primary SkyRealms Greenhouse Garden command",
                "/garden [home|leave|info|plots|plot|mastery|abilities|journal|help]",
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
        context.logger().info("SkyRealms Farming enabled: RewardPipeline + mcMMO Farming + Garden Abilities + Crop Mastery + Botanical Journal.");
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
