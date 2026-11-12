package com.skyrealms.end;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.progression.CombatContributionTracker;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.DisciplineProgressRepository;
import com.skyrealms.end.progression.DisciplineProgressState.LeaderboardEntry;
import com.skyrealms.end.progression.DisciplineProgressState.PlayerDisciplineSnapshot;
import com.skyrealms.end.progression.DisciplineProgressState.XpAwardResult;
import com.skyrealms.end.progression.EndXpCurve;
import com.skyrealms.end.progression.XpSource;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DisciplineProgressRepositoryAndServiceTest {

    private HikariDatabaseService databaseService;
    private DisciplineProgressRepository repository;
    private CombatProgressionService service;

    @BeforeEach
    void setUp() {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("EndDiscTest-" + UUID.randomUUID());
        cfg.setJdbcUrl("jdbc:h2:mem:enddisc_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(cfg);
        DatabaseMigrationService migrations = new DatabaseMigrationService(databaseService.getDataSource(), getClass().getClassLoader());
        MigrateResult res = migrations.runMigrations();
        assertThat(res.success).isTrue();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("config.yml")),
                StandardCharsets.UTF_8
        ));
        EndVillageConfig config = EndVillageConfig.load(yaml);

        Plugin mockPlugin = Mockito.mock(Plugin.class);
        Mockito.when(mockPlugin.isEnabled()).thenReturn(false);

        repository = new DisciplineProgressRepository(databaseService);
        service = new CombatProgressionService(
                mockPlugin,
                config,
                repository,
                new CombatContributionTracker(),
                LoggerFactory.getLogger("EndDiscTest")
        );
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    void persistsIndependentDisciplinesSwitchAndLevel30Mastery() throws Exception {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        // Grant independent XP across Archery & Mage
        XpAwardResult archRes = service.awardXp(playerA, CombatDiscipline.ARCHERY, 46_110L, XpSource.MONSTER_KILL, "enderman");
        assertThat(archRes.newLevel()).isEqualTo(30);
        assertThat(archRes.leveledUp()).isTrue();
        assertThat(archRes.newTotalXp()).isEqualTo(46_110L);

        // Awarding extra XP clamps at cap without breaking melee or mage
        XpAwardResult capped = service.awardXp(playerA, CombatDiscipline.ARCHERY, 5000L, XpSource.GUILD_QUEST, "overflow");
        assertThat(capped.newTotalXp()).isEqualTo(46_110L);
        assertThat(capped.grantedXp()).isZero();

        service.awardXp(playerA, CombatDiscipline.MAGE, 500L, XpSource.TRAINING_CHALLENGE, "arcane_dummy");
        repository.updateActiveDiscipline(playerA, CombatDiscipline.ARCHERY);
        service.markMasteryCompleted(playerA, CombatDiscipline.ARCHERY);

        PlayerDisciplineSnapshot reloaded = service.getOrLoadSnapshot(playerA);
        assertThat(reloaded.activeDiscipline()).isEqualTo(CombatDiscipline.ARCHERY);
        assertThat(reloaded.get(CombatDiscipline.ARCHERY).level()).isEqualTo(30);
        assertThat(reloaded.get(CombatDiscipline.ARCHERY).masteryCompleted()).isTrue();
        assertThat(reloaded.get(CombatDiscipline.MAGE).level()).isEqualTo(EndXpCurve.levelForTotalXp(500L));
        assertThat(reloaded.get(CombatDiscipline.MELEE).level()).isEqualTo(1);
        assertThat(reloaded.get(CombatDiscipline.MELEE).totalXp()).isZero();
        assertThat(reloaded.allMasteriesUnlockedAndMaxed()).isFalse();

        // Max out remaining disciplines & masteries for playerA
        service.setDisciplineLevel(playerA, CombatDiscipline.MELEE, 30);
        service.setDisciplineLevel(playerA, CombatDiscipline.MAGE, 30);
        service.markMasteryCompleted(playerA, CombatDiscipline.MELEE);
        service.markMasteryCompleted(playerA, CombatDiscipline.MAGE);
        assertThat(service.getOrLoadSnapshot(playerA).allMasteriesUnlockedAndMaxed()).isTrue();

        // Verify leaderboard ordering across multiple players
        service.awardXp(playerB, CombatDiscipline.ARCHERY, 1_500L, XpSource.MONSTER_KILL, "shulker");
        List<LeaderboardEntry> top = repository.getTopByDiscipline(CombatDiscipline.ARCHERY, 5);
        assertThat(top).hasSize(2);
        assertThat(top.get(0).playerUuid()).isEqualTo(playerA);
        assertThat(top.get(0).totalXp()).isEqualTo(46_110L);
        assertThat(top.get(1).playerUuid()).isEqualTo(playerB);
        assertThat(top.get(1).totalXp()).isEqualTo(1_500L);
    }
}

