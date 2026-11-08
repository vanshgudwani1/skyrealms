package com.skyrealms.farming.repository;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class GardenOfferingAndQuestRepositoryTest {

    private HikariDatabaseService databaseService;
    private FarmingRepository repository;

    private UUID testPlayerId;
    private UUID testIslandId;

    @BeforeEach
    void setUp() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("GardenOffering-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:offeringdb_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(config);

        DatabaseMigrationService migrationService = new DatabaseMigrationService(
                databaseService.getDataSource(),
                HikariDatabaseService.class.getClassLoader()
        );
        migrationService.runMigrations();

        repository = new FarmingRepository(databaseService);

        testPlayerId = UUID.randomUUID();
        testIslandId = UUID.randomUUID();
        seedPlayerAndIsland(testPlayerId, testIslandId);
    }

    @AfterEach
    void tearDown() {
        if (databaseService != null) {
            databaseService.close();
        }
    }

    private void seedPlayerAndIsland(UUID playerId, UUID islandId) throws SQLException {
        try (Connection conn = databaseService.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO sr_players (id, username, first_joined, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, playerId);
                ps.setString(2, "TestFarmer");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO sr_islands (island_id, owner_uuid, world_name, center_x, center_y, center_z, island_size, status) " +
                            "VALUES (?, ?, 'skyrealms_islands', 0, 100, 0, 100, 'ACTIVE')")) {
                ps.setObject(1, islandId);
                ps.setObject(2, playerId);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("Should persist and retrieve Garden Offering submission")
    void shouldPersistAndRetrieveGardenOffering() throws SQLException {
        assertThat(repository.findOfferingByIsland(testIslandId)).isEmpty();

        Instant now = Instant.now();
        FarmingRepository.GardenOffering offering = new FarmingRepository.GardenOffering(
                testIslandId,
                testPlayerId,
                3,
                3,
                3,
                "COMPLETED",
                now
        );
        repository.saveOffering(offering);

        Optional<FarmingRepository.GardenOffering> loaded = repository.findOfferingByIsland(testIslandId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().islandId()).isEqualTo(testIslandId);
        assertThat(loaded.get().submittedBy()).isEqualTo(testPlayerId);
        assertThat(loaded.get().hayBalesCount()).isEqualTo(3);
        assertThat(loaded.get().carrotsCount()).isEqualTo(3);
        assertThat(loaded.get().sugarCaneCount()).isEqualTo(3);
        assertThat(loaded.get().status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Should persist and retrieve Garden Unlock state")
    void shouldPersistAndRetrieveGardenUnlock() throws SQLException {
        assertThat(repository.findGardenUnlock(testIslandId)).isEmpty();

        Instant now = Instant.now();
        FarmingRepository.GardenUnlock unlock = new FarmingRepository.GardenUnlock(
                testIslandId,
                true,
                true,
                now
        );
        repository.saveGardenUnlock(unlock);

        Optional<FarmingRepository.GardenUnlock> loaded = repository.findGardenUnlock(testIslandId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().offeringCompleted()).isTrue();
        assertThat(loaded.get().cinematicCompleted()).isTrue();
    }

    @Test
    @DisplayName("Should update and retrieve Elarion Intro completion flag")
    void shouldManageElarionIntroCompletion() throws SQLException {
        assertThat(repository.hasCompletedElarionIntro(testPlayerId)).isFalse();

        repository.setElarionIntroCompleted(testPlayerId, true);
        assertThat(repository.hasCompletedElarionIntro(testPlayerId)).isTrue();
    }

    @Test
    @DisplayName("Should persist and retrieve Farming Quest progress")
    void shouldManageFarmingQuestProgress() throws SQLException {
        String questId = "first_harvest";
        assertThat(repository.findQuestProgress(testPlayerId, questId)).isEmpty();

        Instant now = Instant.now();
        FarmingRepository.FarmingQuest quest = new FarmingRepository.FarmingQuest(
                testPlayerId,
                questId,
                "IN_PROGRESS",
                16,
                16,
                8,
                null,
                false,
                now
        );
        repository.saveQuestProgress(quest);

        Optional<FarmingRepository.FarmingQuest> loaded = repository.findQuestProgress(testPlayerId, questId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().tilledCount()).isEqualTo(16);
        assertThat(loaded.get().plantedCount()).isEqualTo(16);
        assertThat(loaded.get().harvestedCount()).isEqualTo(8);
        assertThat("COMPLETED".equalsIgnoreCase(loaded.get().status())).isFalse();

        // Update to complete
        FarmingRepository.FarmingQuest completedQuest = new FarmingRepository.FarmingQuest(
                testPlayerId,
                questId,
                "COMPLETED",
                16,
                16,
                16,
                now,
                true,
                now
        );
        repository.saveQuestProgress(completedQuest);

        Optional<FarmingRepository.FarmingQuest> updated = repository.findQuestProgress(testPlayerId, questId);
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo("COMPLETED");
        assertThat(updated.get().claimedRewards()).isTrue();
    }

    @Test
    @DisplayName("Should persist and retrieve Crop Mastery records")
    void shouldManageCropMasteryRecords() throws SQLException {
        assertThat(repository.findCropMastery(testPlayerId, "WHEAT")).isEmpty();

        Instant now = Instant.now();
        FarmingRepository.CropMasteryRecord record = new FarmingRepository.CropMasteryRecord(
                testPlayerId,
                "WHEAT",
                1500L,
                5,
                "1,5",
                now
        );
        repository.saveCropMastery(record);

        Optional<FarmingRepository.CropMasteryRecord> loaded = repository.findCropMastery(testPlayerId, "WHEAT");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().masteryXp()).isEqualTo(1500L);
        assertThat(loaded.get().masteryLevel()).isEqualTo(5);
        assertThat(loaded.get().claimedMilestones()).isEqualTo("1,5");

        List<FarmingRepository.CropMasteryRecord> all = repository.findAllCropMasteries(testPlayerId);
        assertThat(all).hasSize(1);
    }
}
