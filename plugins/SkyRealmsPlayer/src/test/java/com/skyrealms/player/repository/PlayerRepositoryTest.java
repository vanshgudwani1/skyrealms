package com.skyrealms.player.repository;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.player.model.PlayerProfile;
import com.skyrealms.player.model.PlayerSession;
import com.skyrealms.player.model.PlayerSettings;
import com.skyrealms.player.model.PlayerStats;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerRepositoryTest {

    private HikariDatabaseService databaseService;
    private PlayerRepository repository;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("PlayerRepo-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:player_repo_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(config);

        // Apply migrations
        DatabaseMigrationService migrationService = new DatabaseMigrationService(
                databaseService.getDataSource(),
                getClass().getClassLoader()
        );
        migrationService.runMigrations();

        repository = new PlayerRepository(databaseService);
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    @DisplayName("Should create brand new player profile with default settings and stats")
    void shouldCreateNewProfile() throws SQLException {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = repository.findOrCreate(uuid, "Steve", "127.0.0.1");

        assertThat(profile.id()).isEqualTo(uuid);
        assertThat(profile.username()).isEqualTo("Steve");
        assertThat(profile.settings().isScoreboardVisible()).isTrue();
        assertThat(profile.stats().joinsCount()).isEqualTo(1);
        assertThat(profile.stats().playtimeSeconds()).isEqualTo(0);

        Optional<PlayerProfile> retrieved = repository.findById(uuid);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().username()).isEqualTo("Steve");
    }

    @Test
    @DisplayName("Should update login timestamp and increment join count for returning player")
    void shouldUpdateReturningPlayer() throws SQLException {
        UUID uuid = UUID.randomUUID();
        repository.findOrCreate(uuid, "Alex", "192.168.1.10");

        PlayerProfile returning = repository.findOrCreate(uuid, "Alex_Updated", "192.168.1.20");

        assertThat(returning.username()).isEqualTo("Alex_Updated");
        assertThat(returning.lastIp()).isEqualTo("192.168.1.20");
        assertThat(returning.stats().joinsCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should persist modified stats and settings")
    void shouldPersistModifiedStatsAndSettings() throws SQLException {
        UUID uuid = UUID.randomUUID();
        PlayerProfile profile = repository.findOrCreate(uuid, "Miner99", "127.0.0.1");

        PlayerSettings newSettings = profile.settings().withIslandFlyEnabled(true).withScoreboardVisible(false);
        PlayerStats newStats = profile.stats().addPlaytime(3600).incrementKills().incrementDeaths();

        PlayerProfile modified = profile.withSettings(newSettings).withUpdatedStats(newStats);
        repository.save(modified);

        Optional<PlayerProfile> retrieved = repository.findById(uuid);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().settings().isIslandFlyEnabled()).isTrue();
        assertThat(retrieved.get().settings().isScoreboardVisible()).isFalse();
        assertThat(retrieved.get().stats().playtimeSeconds()).isEqualTo(3600);
        assertThat(retrieved.get().stats().kills()).isEqualTo(1);
        assertThat(retrieved.get().stats().deaths()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should insert session record")
    void shouldInsertSession() throws SQLException {
        UUID playerId = UUID.randomUUID();
        repository.findOrCreate(playerId, "SessionPlayer", "127.0.0.1");

        PlayerSession session = PlayerSession.start(playerId, "127.0.0.1").end();
        repository.saveSession(session);

        assertThat(session.sessionId()).isNotNull();
        assertThat(session.durationSeconds()).isGreaterThanOrEqualTo(0);
    }
}
