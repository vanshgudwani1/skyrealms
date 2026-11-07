package com.skyrealms.player.service;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.player.model.PlayerProfile;
import com.skyrealms.player.repository.PlayerRepository;
import com.skyrealms.platform.common.event.DefaultEventBus;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerServiceTest {

    private HikariDatabaseService databaseService;
    private DefaultEventBus eventBus;
    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("PlayerService-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:player_srv_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(config);

        DatabaseMigrationService migrationService = new DatabaseMigrationService(
                databaseService.getDataSource(),
                getClass().getClassLoader()
        );
        migrationService.runMigrations();

        PlayerRepository repository = new PlayerRepository(databaseService);
        eventBus = new DefaultEventBus();
        playerService = new PlayerService(repository, eventBus);
    }

    @AfterEach
    void tearDown() {
        playerService.close();
        eventBus.close();
        databaseService.close();
    }

    @Test
    @DisplayName("Should load profile asynchronously and manage cache lifecycle")
    void shouldLoadAndCacheProfile() throws ExecutionException, InterruptedException, TimeoutException {
        UUID uuid = UUID.randomUUID();

        // 1. Asynchronously load profile
        PlayerProfile profile = playerService.loadOrCreateAsync(uuid, "CacheUser", "127.0.0.1")
                .get(3, TimeUnit.SECONDS);

        assertThat(profile.username()).isEqualTo("CacheUser");
        assertThat(playerService.getCached(uuid)).isEmpty(); // Not activated yet

        // 2. Activate on join
        playerService.activate(uuid, profile, "127.0.0.1");
        assertThat(playerService.getCached(uuid)).isPresent();

        // 3. Update stats in cache
        playerService.updateStats(uuid, stats -> stats.incrementKills());
        Optional<PlayerProfile> updatedCached = playerService.getCached(uuid);
        assertThat(updatedCached).isPresent();
        assertThat(updatedCached.get().stats().kills()).isEqualTo(1);

        // 4. Deactivate on quit
        playerService.deactivateAsync(uuid).get(3, TimeUnit.SECONDS);
        assertThat(playerService.getCached(uuid)).isEmpty();
    }
}
