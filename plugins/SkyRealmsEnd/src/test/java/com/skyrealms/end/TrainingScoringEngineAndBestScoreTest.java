package com.skyrealms.end;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.progression.CombatContributionTracker;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.DisciplineProgressRepository;
import com.skyrealms.end.progression.XpSource;
import com.skyrealms.end.training.TrainingChallengeType;
import com.skyrealms.end.training.TrainingScoringEngine;
import com.skyrealms.end.training.TrainingScoringEngine.HitScoreBreakdown;
import com.skyrealms.end.training.TrainingScoringEngine.TrainingCompletionSummary;
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
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingScoringEngineAndBestScoreTest {

    private HikariDatabaseService databaseService;
    private CombatProgressionService progressionService;
    private TrainingScoringEngine scoringEngine;

    @BeforeEach
    void setUp() {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("EndTrainTest-" + UUID.randomUUID());
        cfg.setJdbcUrl("jdbc:h2:mem:endtrain_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
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

        DisciplineProgressRepository repo = new DisciplineProgressRepository(databaseService);
        progressionService = new CombatProgressionService(
                mockPlugin, config, repo, new CombatContributionTracker(), LoggerFactory.getLogger("EndTrainTest")
        );
        scoringEngine = new TrainingScoringEngine();
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    void coversAllFifteenChallengesAcrossAllThreeCourtyards() {
        assertThat(TrainingChallengeType.values()).hasSize(15);
        assertThat(TrainingChallengeType.byDiscipline(CombatDiscipline.ARCHERY)).hasSize(5);
        assertThat(TrainingChallengeType.byDiscipline(CombatDiscipline.MELEE)).hasSize(5);
        assertThat(TrainingChallengeType.byDiscipline(CombatDiscipline.MAGE)).hasSize(5);

        assertThat(TrainingScoringEngine.matchesNpcTarget(TrainingChallengeType.ARCHERY_FLYING, "archery_range")).isTrue();
        assertThat(TrainingScoringEngine.matchesNpcTarget(TrainingChallengeType.MELEE_PARRY, "melee_combos")).isTrue();
        assertThat(TrainingScoringEngine.matchesNpcTarget(TrainingChallengeType.MELEE_SURVIVAL, "melee_arena")).isTrue();
        assertThat(TrainingScoringEngine.matchesNpcTarget(TrainingChallengeType.MAGE_OBSTACLES, "arcane_trials")).isTrue();
        assertThat(TrainingScoringEngine.matchesNpcTarget(TrainingChallengeType.MAGE_COMBO, "arcane_elements")).isTrue();
    }

    @Test
    void scoresBullseyeParryAndMageCombosAndAppliesAntiSpamDiminishingReturns() {
        HitScoreBreakdown longBullseye = scoringEngine.scoreArcheryHit(TrainingChallengeType.ARCHERY_DISTANCE, 20.0, 0.2, 4);
        HitScoreBreakdown closeMissEdge = scoringEngine.scoreArcheryHit(TrainingChallengeType.ARCHERY_STATIONARY, 6.0, 0.9, 1);
        assertThat(longBullseye.bullseyeOrCritical()).isTrue();
        assertThat(longBullseye.pointsAwarded()).isGreaterThan(closeMissEdge.pointsAwarded());

        HitScoreBreakdown parryCounter = scoringEngine.scoreMeleeHit(TrainingChallengeType.MELEE_PARRY, 10.0, 5, true);
        assertThat(parryCounter.bullseyeOrCritical()).isTrue();
        assertThat(parryCounter.pointsAwarded()).isGreaterThan(100);

        HitScoreBreakdown mageCombo = scoringEngine.scoreMageHit(TrainingChallengeType.MAGE_COMBO, true, 3, 1.5);
        assertThat(mageCombo.bullseyeOrCritical()).isTrue();
        assertThat(mageCombo.pointsAwarded()).isGreaterThan(100);

        UUID player = UUID.randomUUID();
        long now = 1_000_000L;
        for (int i = 0; i < TrainingScoringEngine.FULL_XP_SESSIONS_PER_WINDOW; i++) {
            TrainingCompletionSummary full = scoringEngine.summarizeAndComputeXp(
                    player, TrainingChallengeType.ARCHERY_ACCURACY, 450, 12, 0, 8, now + i * 1000L);
            assertThat(full.xpScaleApplied()).isEqualTo(1.0);
            assertThat(full.accuracyPercent()).isEqualTo(100.0);
            assertThat(full.xpAwarded()).isGreaterThan(0L);
        }

        TrainingCompletionSummary diminished = scoringEngine.summarizeAndComputeXp(
                player, TrainingChallengeType.ARCHERY_ACCURACY, 450, 12, 0, 8, now + 10_000L);
        assertThat(diminished.xpScaleApplied()).isLessThan(1.0);

        TrainingCompletionSummary zeroHits = scoringEngine.summarizeAndComputeXp(
                player, TrainingChallengeType.ARCHERY_ACCURACY, 0, 0, 5, 0, now + 12_000L);
        assertThat(zeroHits.xpAwarded()).isZero();
    }

    @Test
    void persistsTrainingBestScoreAndTrainingXpToDatabase() throws Exception {
        UUID player = UUID.randomUUID();
        assertThat(progressionService.recordTrainingBestScore(player, CombatDiscipline.MAGE, 320)).isTrue();
        assertThat(progressionService.recordTrainingBestScore(player, CombatDiscipline.MAGE, 200)).isFalse();
        assertThat(progressionService.recordTrainingBestScore(player, CombatDiscipline.MAGE, 540)).isTrue();

        progressionService.awardXp(player, CombatDiscipline.MAGE, 180L, XpSource.TRAINING_CHALLENGE, "mage_combo");
        var snap = progressionService.getOrLoadSnapshot(player);
        assertThat(snap.get(CombatDiscipline.MAGE).trainingBestScore()).isEqualTo(540);
        assertThat(snap.get(CombatDiscipline.MAGE).totalXp()).isEqualTo(180L);
        assertThat(snap.get(CombatDiscipline.MAGE).level()).isEqualTo(2);
    }
}

