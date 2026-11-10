package com.skyrealms.farming.repository;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.farming.garden.journal.BotanicalAchievement;
import com.skyrealms.farming.garden.journal.BotanicalJournalEntry;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BotanicalJournalRepositoryTest {

    private HikariDatabaseService databaseService;
    private FarmingRepository repository;
    private UUID testPlayerId;

    @BeforeEach
    void setUp() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("Journal-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:journaldb_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(config);
        new DatabaseMigrationService(databaseService.getDataSource(), HikariDatabaseService.class.getClassLoader()).runMigrations();
        repository = new FarmingRepository(databaseService);

        testPlayerId = UUID.randomUUID();
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO sr_players (id, username, first_joined, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setObject(1, testPlayerId);
            ps.setString(2, "BotanistTest");
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() {
        if (databaseService != null) {
            databaseService.close();
        }
    }

    @Test
    @DisplayName("Should persist and update Botanical Journal records and read mutation discoveries")
    void shouldPersistBotanicalJournalAndReadMutations() throws SQLException {
        assertThat(repository.findBotanicalJournal(testPlayerId)).isEmpty();

        BotanicalJournalEntry entry = new BotanicalJournalEntry(
                testPlayerId,
                42,
                9,
                Set.of(BotanicalAchievement.FIRST_BOUNTY.name(), BotanicalAchievement.SURGE_HARVESTER.name()),
                Set.of("Golden Wheat Strain")
        );

        repository.saveBotanicalJournal(new FarmingRepository.BotanicalJournalRecord(
                entry.playerUuid(),
                entry.highestHarvestStreak(),
                entry.bestSingleHarvest(),
                BotanicalJournalEntry.encodeSet(entry.unlockedAchievements()),
                BotanicalJournalEntry.encodeSet(entry.discoveredVarieties()),
                Instant.now()
        ));

        Optional<FarmingRepository.BotanicalJournalRecord> loaded = repository.findBotanicalJournal(testPlayerId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().highestHarvestStreak()).isEqualTo(42);
        assertThat(loaded.get().bestSingleHarvest()).isEqualTo(9);
        assertThat(BotanicalJournalEntry.decodeSet(loaded.get().unlockedAchievements()))
                .containsExactlyInAnyOrder(BotanicalAchievement.FIRST_BOUNTY.name(), BotanicalAchievement.SURGE_HARVESTER.name());
        assertThat(BotanicalJournalEntry.decodeSet(loaded.get().discoveredVarieties()))
                .containsExactly("Golden Wheat Strain");

        // Update existing journal
        repository.saveBotanicalJournal(new FarmingRepository.BotanicalJournalRecord(
                testPlayerId, 110, 12,
                BotanicalAchievement.HARVEST_STREAK_100.name(),
                "Golden Wheat Strain,Giant Melon Strain",
                Instant.now()
        ));

        Optional<FarmingRepository.BotanicalJournalRecord> updated = repository.findBotanicalJournal(testPlayerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().highestHarvestStreak()).isEqualTo(110);
        assertThat(updated.get().bestSingleHarvest()).isEqualTo(12);

        // Mutation discoveries reuse sr_farming_mutation_discoveries
        repository.recordMutationDiscovery(testPlayerId, "GOLDEN", "WHEAT");
        List<FarmingRepository.MutationDiscoveryRecord> mutations = repository.findMutationDiscoveries(testPlayerId);
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0).mutationType()).isEqualTo("GOLDEN");
        assertThat(mutations.get(0).cropType()).isEqualTo("WHEAT");
    }
}

