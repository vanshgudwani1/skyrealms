package com.skyrealms.end;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.gear.EndGearCatalog;
import com.skyrealms.end.gear.EndGearService;
import com.skyrealms.end.progression.CombatContributionTracker;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.DisciplineProgressRepository;
import com.skyrealms.end.progression.XpSource;
import com.skyrealms.end.quest.EndQuestService;
import com.skyrealms.end.quest.EndQuestService.ClaimOutcome;
import com.skyrealms.end.quest.QuestDefinition;
import com.skyrealms.end.quest.QuestObjectiveType;
import com.skyrealms.end.quest.QuestProgressRepository;
import com.skyrealms.end.quest.QuestProgressRepository.QuestStatus;
import com.skyrealms.end.quest.QuestRegistry;
import com.skyrealms.end.service.EndProfileRepository;
import com.skyrealms.items.catalog.ItemCatalog;
import com.skyrealms.items.factory.SkyRealmsItemFactory;
import com.skyrealms.items.service.HoeUpgradeService;
import com.skyrealms.items.service.ItemRegistryService;
import com.skyrealms.platform.integrations.EconomyProvider;
import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EndQuestSystemTest {

    private HikariDatabaseService databaseService;
    private EndProfileRepository profileRepository;
    private CombatProgressionService progressionService;
    private QuestRegistry questRegistry;
    private QuestProgressRepository questRepository;
    private EconomyProvider economyMock;
    private EndQuestService questService;

    @BeforeEach
    void setUp() {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("EndQuestTest-" + UUID.randomUUID());
        cfg.setJdbcUrl("jdbc:h2:mem:endquest_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(cfg);
        MigrateResult res = new DatabaseMigrationService(databaseService.getDataSource(), getClass().getClassLoader()).runMigrations();
        assertThat(res.success).isTrue();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("config.yml")),
                StandardCharsets.UTF_8
        ));
        EndVillageConfig config = EndVillageConfig.load(yaml);
        Plugin mockPlugin = mock(Plugin.class);
        when(mockPlugin.isEnabled()).thenReturn(false);

        ItemCatalog itemCatalog = new ItemCatalog();
        SkyRealmsItemFactory factory = new SkyRealmsItemFactory(itemCatalog);
        ItemRegistryService itemService = new ItemRegistryService(itemCatalog, factory, new HoeUpgradeService(itemCatalog, factory));
        EndGearCatalog gearCatalog = new EndGearCatalog();
        gearCatalog.registerAllInto(itemService);

        profileRepository = new EndProfileRepository(databaseService);
        progressionService = new CombatProgressionService(
                mockPlugin, config, new DisciplineProgressRepository(databaseService),
                new CombatContributionTracker(), LoggerFactory.getLogger("EndQuestTest")
        );
        EndGearService gearService = new EndGearService(gearCatalog, itemService, progressionService);
        questRegistry = new QuestRegistry();
        questRepository = new QuestProgressRepository(databaseService);
        economyMock = mock(EconomyProvider.class);

        questService = new EndQuestService(
                questRegistry, questRepository, profileRepository,
                progressionService, itemService, gearService, economyMock,
                LoggerFactory.getLogger("EndQuestTest")
        );
        progressionService.setQuestService(questService);
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    void enforcesGuildPrerequisitesDrivesEventsAndGuaranteesClaimIdempotency() throws Exception {
        UUID player = UUID.randomUUID();

        // Prerequisite enforcement: cannot jump directly to quest 2
        ClaimOutcome blockedPrereq = questService.acceptQuest(player, "archery_2_eyes_on_wind");
        assertThat(blockedPrereq.success()).isFalse();

        // Accept Archery Path I -> complete via 2 training events -> claim -> check idempotency
        assertThat(questService.acceptQuest(player, "archery_1_steady_draw").success()).isTrue();
        questService.recordEvent(player, QuestObjectiveType.TRAINING_COMPLETE, CombatDiscipline.ARCHERY, 1, false);
        questService.recordEvent(player, QuestObjectiveType.TRAINING_COMPLETE, CombatDiscipline.ARCHERY, 1, false);

        ClaimOutcome firstClaim = questService.claimReward(player, "archery_1_steady_draw");
        assertThat(firstClaim.success()).isTrue();

        // Duplicate claim attempt MUST fail atomically — no double coins/XP/reputation!
        ClaimOutcome duplicateClaim = questService.claimReward(player, "archery_1_steady_draw");
        assertThat(duplicateClaim.success()).isFalse();

        verify(economyMock, times(1)).deposit(eq(player), eq("coins"), eq(BigDecimal.valueOf(300L)), anyString());
        assertThat(profileRepository.loadOrCreate(player).reputation()).isEqualTo(30);
        assertThat(progressionService.getOrLoadSnapshot(player).get(CombatDiscipline.ARCHERY).totalXp()).isEqualTo(180L);

        // Mastery trial completion unlocks persistent discipline mastery flag
        progressionService.setDisciplineLevel(player, CombatDiscipline.ARCHERY, 25);
        questService.forceCompleteForTesting(player, "archery_4_mastery_trial");
        assertThat(questService.claimReward(player, "archery_4_mastery_trial").success()).isTrue();
        assertThat(progressionService.getOrLoadSnapshot(player).get(CombatDiscipline.ARCHERY).masteryCompleted()).isTrue();
    }

    @Test
    void supportsDailyWeeklyCycleKeysAndEventDrivenMobKills() throws Exception {
        UUID player = UUID.randomUUID();
        assertThat(questService.acceptQuest(player, "daily_wilderness_patrol").success()).isTrue();

        for (int i = 0; i < 8; i++) {
            progressionService.awardXp(player, CombatDiscipline.MELEE, 20L, XpSource.MONSTER_KILL, "enderman");
        }

        QuestDefinition daily = questRegistry.get("daily_wilderness_patrol");
        assertThat(daily).isNotNull();
        String dailyKey = QuestProgressRepository.key(daily.id(), QuestRegistry.cycleKeyFor(daily, LocalDate.now()));
        var row = questRepository.loadAll(player).get(dailyKey);
        assertThat(row).isNotNull();
        assertThat(row.status()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(row.progressValue()).isEqualTo(8);
    }
}

