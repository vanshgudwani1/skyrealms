package com.skyrealms.farming.repository;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.farming.model.*;
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

class FarmingRepositoryTest {

    private HikariDatabaseService databaseService;
    private FarmingRepository repository;

    private UUID testPlayerId;
    private UUID testIslandId;

    @BeforeEach
    void setUp() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("FarmingRepo-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:farmingdb_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
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

        // Seed test player and test island for foreign key constraints
        testPlayerId = UUID.randomUUID();
        testIslandId = UUID.randomUUID();
        seedPlayerAndIsland(testPlayerId, testIslandId);
    }

    private void seedPlayerAndIsland(UUID playerId, UUID islandId) throws SQLException {
        try (Connection conn = databaseService.getConnection()) {
            // Seed player
            String playerSql = "INSERT INTO sr_players (id, username, first_joined, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = conn.prepareStatement(playerSql)) {
                ps.setObject(1, playerId);
                ps.setString(2, "FarmerSteve");
                ps.executeUpdate();
            }

            // Seed island
            String islandSql = "INSERT INTO sr_islands (island_id, owner_uuid, world_name, center_x, center_y, center_z, island_size, status) VALUES (?, ?, 'skyrealms_islands', 0, 100, 0, 100, 'ACTIVE')";
            try (PreparedStatement ps = conn.prepareStatement(islandSql)) {
                ps.setObject(1, islandId);
                ps.setObject(2, playerId);
                ps.executeUpdate();
            }
        }
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    @DisplayName("Should save, retrieve, and update farming generator with upgrades")
    void shouldSaveAndRetrieveGenerator() throws SQLException {
        UUID generatorId = UUID.randomUUID();
        Instant now = Instant.now();

        GeneratorUpgrade speedUpgrade = new GeneratorUpgrade(GeneratorUpgrade.KEY_SPEED, 3, now);
        FarmingGenerator generator = new FarmingGenerator(
                generatorId,
                testIslandId,
                testPlayerId,
                FarmingGeneratorTier.TIER_1_WHEAT,
                "skyrealms_islands",
                10, 100, 10,
                GeneratorStatus.ACTIVE,
                0L,
                now,
                now,
                List.of(speedUpgrade)
        );

        repository.saveGenerator(generator);

        // Find by ID
        Optional<FarmingGenerator> loadedOpt = repository.findGeneratorById(generatorId);
        assertThat(loadedOpt).isPresent();
        FarmingGenerator loaded = loadedOpt.get();

        assertThat(loaded.generatorId()).isEqualTo(generatorId);
        assertThat(loaded.tier()).isEqualTo(FarmingGeneratorTier.TIER_1_WHEAT);
        assertThat(loaded.coreX()).isEqualTo(10);
        assertThat(loaded.getUpgradeLevel(GeneratorUpgrade.KEY_SPEED)).isEqualTo(3);

        // Update harvests and status
        loaded.incrementHarvests();
        loaded.setStatus(GeneratorStatus.PAUSED);
        repository.saveGenerator(loaded);

        Optional<FarmingGenerator> updatedOpt = repository.findGeneratorById(generatorId);
        assertThat(updatedOpt).isPresent();
        assertThat(updatedOpt.get().totalHarvests()).isEqualTo(1);
        assertThat(updatedOpt.get().status()).isEqualTo(GeneratorStatus.PAUSED);
    }

    @Test
    @DisplayName("Should manage player progress and collections tracking")
    void shouldManagePlayerProgressAndCollections() throws SQLException {
        // Player progress
        FarmingPlayerProgress progress = repository.getOrCreatePlayerProgress(testPlayerId);
        assertThat(progress.farmingLevel()).isEqualTo(1);
        assertThat(progress.farmingXp()).isEqualTo(0);

        progress.addXp(500);
        progress.setFarmingLevel(3);
        repository.savePlayerProgress(progress);

        FarmingPlayerProgress reloaded = repository.getOrCreatePlayerProgress(testPlayerId);
        assertThat(reloaded.farmingLevel()).isEqualTo(3);
        assertThat(reloaded.farmingXp()).isEqualTo(500);

        // Crop collections
        repository.incrementCollection(testPlayerId, "WHEAT", 150);
        Map<String, FarmingCropCollection> collections = repository.loadCollections(testPlayerId);
        assertThat(collections).containsKey("WHEAT");
        assertThat(collections.get("WHEAT").amountCollected()).isEqualTo(150);

        // Increment again
        repository.incrementCollection(testPlayerId, "WHEAT", 50);
        collections = repository.loadCollections(testPlayerId);
        assertThat(collections.get("WHEAT").amountCollected()).isEqualTo(200);
    }

    @Test
    @DisplayName("Should automatically cascade delete generators when parent island is deleted")
    void shouldCascadeDeleteGeneratorsOnIslandDelete() throws SQLException {
        UUID generatorId = UUID.randomUUID();
        Instant now = Instant.now();

        FarmingGenerator generator = new FarmingGenerator(
                generatorId,
                testIslandId,
                testPlayerId,
                FarmingGeneratorTier.TIER_1_WHEAT,
                "skyrealms_islands",
                20, 100, 20,
                GeneratorStatus.ACTIVE,
                0L,
                now,
                now,
                Collections.emptyList()
        );
        repository.saveGenerator(generator);

        assertThat(repository.findGeneratorById(generatorId)).isPresent();

        // Delete parent island
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sr_islands WHERE island_id = ?")) {
            ps.setObject(1, testIslandId);
            ps.executeUpdate();
        }

        // Generator should be deleted via ON DELETE CASCADE
        assertThat(repository.findGeneratorById(generatorId)).isEmpty();
    }
}
