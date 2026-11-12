package com.skyrealms.end.module;

import com.skyrealms.end.command.EndBossCommand;
import com.skyrealms.end.command.EndCombatCommand;
import com.skyrealms.end.command.EndGearCommand;
import com.skyrealms.end.command.EndNpcCommand;
import com.skyrealms.end.command.EndQuestCommand;
import com.skyrealms.end.command.EndTrainingCommand;
import com.skyrealms.end.command.EndVillageCommand;
import com.skyrealms.end.command.EndWildernessCommand;
import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.district.EndVillageBounds;
import com.skyrealms.end.gear.EndGearCatalog;
import com.skyrealms.end.gear.EndGearService;
import com.skyrealms.end.listener.CombatProgressionListener;
import com.skyrealms.end.listener.EndNpcListener;
import com.skyrealms.end.listener.EndSafeZoneListener;
import com.skyrealms.end.listener.GearRequirementListener;
import com.skyrealms.end.listener.TrainingListener;
import com.skyrealms.end.listener.VaelkorBossListener;
import com.skyrealms.end.listener.WildernessListener;
import com.skyrealms.end.npc.EndNpcAnimationScheduler;
import com.skyrealms.end.npc.EndNpcManager;
import com.skyrealms.end.npc.EndNpcRegistry;
import com.skyrealms.end.npc.NpcActionType;
import com.skyrealms.end.progression.CombatContributionTracker;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.DisciplineProgressRepository;
import com.skyrealms.end.quest.EndQuestService;
import com.skyrealms.end.quest.QuestProgressRepository;
import com.skyrealms.end.quest.QuestRegistry;
import com.skyrealms.end.service.EndProfileRepository;
import com.skyrealms.end.training.EndTrainingService;
import com.skyrealms.end.training.TrainingScoringEngine;
import com.skyrealms.end.wilderness.BestiaryRepository;
import com.skyrealms.end.wilderness.EndLootTable;
import com.skyrealms.end.wilderness.EndWildernessService;
import com.skyrealms.items.service.ItemRegistryService;
import com.skyrealms.platform.integrations.EconomyProvider;
import com.skyrealms.platform.api.module.ModuleContext;
import com.skyrealms.platform.api.module.ModuleId;
import com.skyrealms.platform.api.module.ModuleMetadata;
import com.skyrealms.platform.api.module.ModuleState;
import com.skyrealms.platform.api.module.SkyModule;
import com.skyrealms.platform.common.command.DynamicCommandRegistrar;
import com.skyrealms.platform.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * SkyRealms End Civilization Module (Milestones 1-2: districts, surveyed coordinates,
 * PostgreSQL progression foundations, safe-zone protection, and configurable NPC engine).
 */
public final class EndModule implements SkyModule {

    public static final ModuleId ID = ModuleId.of("end");

    private final JavaPlugin plugin;
    private final ModuleMetadata metadata;
    private ModuleState state = ModuleState.UNLOADED;

    private EndVillageConfig villageConfig;
    private EndVillageBounds villageBounds;
    private EndProfileRepository profileRepository;
    private EndSafeZoneListener safeZoneListener;
    private EndNpcRegistry npcRegistry;
    private EndNpcManager npcManager;
    private EndNpcAnimationScheduler animationScheduler;
    private EndNpcListener npcListener;
    private DisciplineProgressRepository disciplineRepository;
    private CombatContributionTracker contributionTracker;
    private CombatProgressionService combatProgressionService;
    private CombatProgressionListener combatProgressionListener;
    private TrainingScoringEngine trainingScoringEngine;
    private EndTrainingService trainingService;
    private TrainingListener trainingListener;
    private EndGearCatalog gearCatalog;
    private EndGearService gearService;
    private GearRequirementListener gearRequirementListener;
    private QuestRegistry questRegistry;
    private QuestProgressRepository questRepository;
    private EndQuestService questService;
    private BestiaryRepository bestiaryRepository;
    private EndLootTable endLootTable;
    private EndWildernessService wildernessService;
    private WildernessListener wildernessListener;
    private VaelkorBossListener vaelkorBossListener;

    public EndModule(@NotNull JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.metadata = ModuleMetadata.builder(ID)
                .name("SkyRealms End")
                .version("1.0.0")
                .dependsOn(ModuleId.of("database"))
                .dependsOn(ModuleId.of("player"))
                .dependsOn(ModuleId.of("economy"))
                .dependsOn(ModuleId.of("items"))
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
    public void onEnable(@NotNull ModuleContext context) {
        state = ModuleState.INITIALIZING;

        plugin.reloadConfig();
        this.villageConfig = EndVillageConfig.load(plugin.getConfig());
        this.villageBounds = new EndVillageBounds(villageConfig);

        DatabaseService databaseService = context.services().get(DatabaseService.class);
        this.profileRepository = new EndProfileRepository(databaseService);

        this.safeZoneListener = new EndSafeZoneListener(
                plugin,
                villageConfig,
                villageBounds,
                profileRepository,
                context.logger()
        );
        Bukkit.getPluginManager().registerEvents(safeZoneListener, plugin);

        this.npcRegistry = new EndNpcRegistry(villageConfig);
        this.npcManager = new EndNpcManager(plugin, villageConfig, npcRegistry, context.logger());
        this.animationScheduler = new EndNpcAnimationScheduler(plugin, villageConfig, npcManager);
        npcManager.setAnimationScheduler(animationScheduler);
        this.npcListener = new EndNpcListener(npcManager);
        Bukkit.getPluginManager().registerEvents(npcListener, plugin);

        this.disciplineRepository = new DisciplineProgressRepository(databaseService);
        this.contributionTracker = new CombatContributionTracker();
        this.combatProgressionService = new CombatProgressionService(
                plugin,
                villageConfig,
                disciplineRepository,
                contributionTracker,
                context.logger()
        );
        npcManager.registerHandler(NpcActionType.LEADERBOARD, combatProgressionService.leaderboardNpcHandler());
        this.combatProgressionListener = new CombatProgressionListener(
                villageBounds,
                combatProgressionService,
                contributionTracker
        );
        Bukkit.getPluginManager().registerEvents(combatProgressionListener, plugin);

        this.trainingScoringEngine = new TrainingScoringEngine();
        this.trainingService = new EndTrainingService(
                plugin,
                villageConfig,
                combatProgressionService,
                trainingScoringEngine,
                context.logger()
        );
        npcManager.registerHandler(NpcActionType.TRAINING, trainingService.trainingNpcHandler());
        this.trainingListener = new TrainingListener(trainingService);
        Bukkit.getPluginManager().registerEvents(trainingListener, plugin);

        ItemRegistryService itemService = context.services().get(ItemRegistryService.class);
        this.gearCatalog = new EndGearCatalog();
        this.gearCatalog.registerAllInto(itemService);
        this.gearService = new EndGearService(gearCatalog, itemService, combatProgressionService);
        npcManager.registerHandler(NpcActionType.CRAFTING, gearService.craftingNpcHandler());
        this.gearRequirementListener = new GearRequirementListener(
                gearService,
                itemService,
                villageBounds,
                combatProgressionService,
                contributionTracker
        );
        Bukkit.getPluginManager().registerEvents(gearRequirementListener, plugin);

        this.questRegistry = new QuestRegistry();
        this.questRepository = new QuestProgressRepository(databaseService);
        EconomyProvider economyProvider = context.services().getOptional(EconomyProvider.class).orElse(null);
        this.questService = new EndQuestService(
                questRegistry,
                questRepository,
                profileRepository,
                combatProgressionService,
                itemService,
                gearService,
                economyProvider,
                context.logger()
        );
        safeZoneListener.setQuestService(questService);
        combatProgressionService.setQuestService(questService);
        trainingService.setQuestService(questService);

        this.bestiaryRepository = new BestiaryRepository(databaseService);
        this.endLootTable = new EndLootTable(itemService, gearService);
        this.wildernessService = new EndWildernessService(
                plugin,
                villageConfig,
                bestiaryRepository,
                endLootTable,
                questService,
                context.logger()
        );
        this.wildernessListener = new WildernessListener(wildernessService);
        Bukkit.getPluginManager().registerEvents(wildernessListener, plugin);

        npcManager.registerHandler(NpcActionType.QUEST_BOARD, (player, npc) -> {
            if ("bestiary_view".equalsIgnoreCase(npc.actionTarget())) {
                wildernessService.showBestiary(player);
            } else {
                questService.questBoardNpcHandler().handle(player, npc);
            }
        });
        npcManager.registerHandler(NpcActionType.REPUTATION, questService.reputationNpcHandler());
        npcManager.registerHandler(NpcActionType.WARP_WILDERNESS, wildernessService.wildernessWarpNpcHandler());

        EndVillageCommand cmd = new EndVillageCommand(villageConfig, villageBounds);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "endvillage",
                "Inspect SkyRealms End Civilization districts and safe-zone",
                "/endvillage [info|districts|tp <district>]",
                List.of("ev"),
                "skyrealms.end.use",
                PermissionDefault.TRUE,
                "§cYou lack permission to use /endvillage.",
                cmd
        );

        EndNpcCommand npcCmd = new EndNpcCommand(villageConfig, npcRegistry, npcManager);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "endnpc",
                "Inspect and manage SkyRealms End Village NPCs",
                "/endnpc [list|respawn|tp <id>|interact <id>]",
                List.of(),
                "skyrealms.end.admin",
                PermissionDefault.OP,
                "§cYou lack permission to manage End NPCs.",
                npcCmd
        );

        EndCombatCommand combatCmd = new EndCombatCommand(combatProgressionService, disciplineRepository);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "endcombat",
                "View and switch SkyRealms End combat disciplines (Archery, Melee, Mage)",
                "/discipline [stats|switch <disc>|top [disc]|givexp|setlevel|inspect]",
                List.of("discipline", "endstats"),
                "skyrealms.end.use",
                PermissionDefault.TRUE,
                "§cYou lack permission to use /discipline.",
                combatCmd
        );

        EndTrainingCommand trainCmd = new EndTrainingCommand(trainingService, trainingScoringEngine, combatProgressionService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "training",
                "Start or manage End Village outdoor training courtyard drills",
                "/training [list|start <mode>|stop|simulate]",
                List.of("endtrain"),
                "skyrealms.end.use",
                PermissionDefault.TRUE,
                "§cYou lack permission to use /training.",
                trainCmd
        );

        EndGearCommand gearCmd = new EndGearCommand(gearCatalog, gearService, itemService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "endgear",
                "Inspect, craft, or mint SkyRealms End weapons, armor sets, and materials",
                "/endgear [list|craft <id>|inspect|give]",
                List.of("endforge", "endarmor"),
                "skyrealms.end.use",
                PermissionDefault.TRUE,
                "§cYou lack permission to use /endgear.",
                gearCmd
        );

        EndQuestCommand questCmd = new EndQuestCommand(questRegistry, questRepository, profileRepository, questService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "endquest",
                "View and progress End Civilization quests, guild trials, contracts, and reputation",
                "/endquest [list|accept <id>|deliver <id>|claim <id>|reputation]",
                List.of("endquests", "eq"),
                "skyrealms.end.use",
                PermissionDefault.TRUE,
                "§cYou lack permission to use /endquest.",
                questCmd
        );

        EndWildernessCommand wildCmd = new EndWildernessCommand(wildernessService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "wilds",
                "Travel to the separate End Wilderness hunting regions and view the Bestiary",
                "/wilds [regions|warp <region>|bestiary|return|spawn <mobId>]",
                List.of("endwilds", "hunt"),
                "skyrealms.end.use",
                PermissionDefault.TRUE,
                "§cYou lack permission to use /wilds.",
                wildCmd
        );

        this.vaelkorBossListener = new VaelkorBossListener(plugin, itemService, combatProgressionService, contributionTracker);
        Bukkit.getPluginManager().registerEvents(vaelkorBossListener, plugin);
        vaelkorBossListener.start();
        EndBossCommand bossCmd = new EndBossCommand(itemService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "endboss",
                "Summon Vaelkor the Stormrake Wyvern or obtain boss prep gear",
                "/endboss [vaelkor|enderloper|prep]",
                List.of("boss"),
                "skyrealms.end.admin",
                PermissionDefault.OP,
                "§cYou lack permission to use /endboss.",
                bossCmd
        );

        Bukkit.getServicesManager().register(EndVillageConfig.class, villageConfig, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndVillageBounds.class, villageBounds, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndProfileRepository.class, profileRepository, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndNpcRegistry.class, npcRegistry, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndNpcManager.class, npcManager, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndNpcAnimationScheduler.class, animationScheduler, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(DisciplineProgressRepository.class, disciplineRepository, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(CombatContributionTracker.class, contributionTracker, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(CombatProgressionService.class, combatProgressionService, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(TrainingScoringEngine.class, trainingScoringEngine, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndTrainingService.class, trainingService, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndGearCatalog.class, gearCatalog, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndGearService.class, gearService, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(QuestRegistry.class, questRegistry, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(QuestProgressRepository.class, questRepository, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndQuestService.class, questService, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(BestiaryRepository.class, bestiaryRepository, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndLootTable.class, endLootTable, plugin, ServicePriority.Normal);
        Bukkit.getServicesManager().register(EndWildernessService.class, wildernessService, plugin, ServicePriority.Normal);

        npcManager.spawnAllLoaded();
        animationScheduler.start();
        combatProgressionService.start();
        trainingService.start();
        wildernessService.start();

        state = ModuleState.ENABLED;
        context.logger().info(
                "SkyRealms End enabled: world='{}', wilds='{}', districts={}, npcs={}, arenas={}, gearItems={}, quests={}, disciplines=3, anim=centralized(5Hz)",
                villageConfig.worldName(),
                wildernessService.wildernessWorldName(),
                villageConfig.districts().size(),
                npcRegistry.size(),
                villageConfig.trainingArenas().size(),
                gearCatalog.allSpecs().size(),
                questRegistry.all().size()
        );
    }

    @Override
    public void onDisable(@NotNull ModuleContext context) {
        state = ModuleState.DISABLED;
        if (wildernessService != null) {
            wildernessService.stop();
        }
        if (wildernessListener != null) {
            HandlerList.unregisterAll(wildernessListener);
        }
        if (vaelkorBossListener != null) {
            vaelkorBossListener.stop();
            HandlerList.unregisterAll(vaelkorBossListener);
        }
        if (gearRequirementListener != null) {
            HandlerList.unregisterAll(gearRequirementListener);
        }
        if (trainingService != null) {
            trainingService.stop();
        }
        if (trainingListener != null) {
            HandlerList.unregisterAll(trainingListener);
        }
        if (combatProgressionService != null) {
            combatProgressionService.stop();
        }
        if (combatProgressionListener != null) {
            HandlerList.unregisterAll(combatProgressionListener);
        }
        if (animationScheduler != null) {
            animationScheduler.stop();
        }
        if (npcManager != null) {
            npcManager.despawnAll();
        }
        if (npcListener != null) {
            HandlerList.unregisterAll(npcListener);
        }
        if (safeZoneListener != null) {
            HandlerList.unregisterAll(safeZoneListener);
        }
        Bukkit.getServicesManager().unregisterAll(plugin);
    }

    public EndVillageConfig villageConfig() {
        return villageConfig;
    }

    public EndVillageBounds villageBounds() {
        return villageBounds;
    }

    public EndProfileRepository profileRepository() {
        return profileRepository;
    }

    public EndNpcRegistry npcRegistry() {
        return npcRegistry;
    }

    public EndNpcManager npcManager() {
        return npcManager;
    }
}

