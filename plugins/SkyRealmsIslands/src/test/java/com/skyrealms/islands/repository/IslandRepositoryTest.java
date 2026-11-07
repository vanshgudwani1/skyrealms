package com.skyrealms.islands.repository;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.islands.model.*;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IslandRepositoryTest {

    private HikariDatabaseService databaseService;
    private IslandRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("IslandRepo-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:islanddb_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(config);

        DatabaseMigrationService migrationService = new DatabaseMigrationService(
                databaseService.getDataSource(),
                HikariDatabaseService.class.getClassLoader()
        );
        migrationService.runMigrations();

        repository = new IslandRepository(databaseService);
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    @DisplayName("Should create island atomically with leader and settings, then retrieve it correctly")
    void shouldCreateAndRetrieveIsland() throws SQLException {
        UUID owner = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        Instant now = Instant.now();

        Island island = new Island(
                islandId,
                owner,
                new IslandLocation("skyrealms_islands", 0, 100, 0),
                100,
                0,
                "Genesis Island",
                IslandStatus.ACTIVE,
                now,
                now,
                IslandSettings.defaultSettings(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        repository.createIsland(island);

        assertThat(repository.getNextGridIndex()).isEqualTo(1);

        Optional<Island> loadedOpt = repository.findIslandById(islandId);
        assertThat(loadedOpt).isPresent();

        Island loaded = loadedOpt.get();
        assertThat(loaded.id()).isEqualTo(islandId);
        assertThat(loaded.ownerUuid()).isEqualTo(owner);
        assertThat(loaded.name()).isEqualTo("Genesis Island");
        assertThat(loaded.location().centerX()).isEqualTo(0);
        assertThat(loaded.location().centerY()).isEqualTo(100);
        assertThat(loaded.location().centerZ()).isEqualTo(0);
        assertThat(loaded.size()).isEqualTo(100);
        assertThat(loaded.status()).isEqualTo(IslandStatus.ACTIVE);

        // Leader member should exist
        assertThat(loaded.getRole(owner)).isEqualTo(IslandRole.LEADER);
        assertThat(loaded.members()).hasSize(1);

        // Settings should be default
        assertThat(loaded.settings().publicVisits()).isTrue();
        assertThat(loaded.settings().pvpEnabled()).isFalse();

        // Also test findIslandByOwner
        Optional<Island> byOwner = repository.findIslandByOwner(owner);
        assertThat(byOwner).isPresent();
        assertThat(byOwner.get().id()).isEqualTo(islandId);

        // Also test findIslandByMember
        Optional<Island> byMember = repository.findIslandByMember(owner);
        assertThat(byMember).isPresent();
        assertThat(byMember.get().id()).isEqualTo(islandId);
    }

    @Test
    @DisplayName("Should update island metadata, settings, and manage members correctly")
    void shouldUpdateMetadataAndManageMembers() throws SQLException {
        UUID owner = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        Instant now = Instant.now();

        Island island = new Island(
                islandId,
                owner,
                new IslandLocation("skyrealms_islands", 500, 100, 0),
                100,
                0,
                "My Island",
                IslandStatus.ACTIVE,
                now,
                now,
                IslandSettings.defaultSettings(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        repository.createIsland(island);

        // 1. Update island metadata
        island.setSize(150);
        island.setLevel(5);
        island.setName("Empire Island");
        repository.updateIsland(island);

        Island updatedMeta = repository.findIslandById(islandId).orElseThrow();
        assertThat(updatedMeta.size()).isEqualTo(150);
        assertThat(updatedMeta.level()).isEqualTo(5);
        assertThat(updatedMeta.name()).isEqualTo("Empire Island");

        // 2. Update settings
        IslandSettings newSettings = new IslandSettings(true, false, false, true, true, "{\"theme\":\"celestial\"}");
        repository.updateSettings(islandId, newSettings);

        Island updatedSettings = repository.findIslandById(islandId).orElseThrow();
        assertThat(updatedSettings.settings().publicVisits()).isTrue();
        assertThat(updatedSettings.settings().pvpEnabled()).isTrue();
        assertThat(updatedSettings.settings().visitorContainerAccess()).isTrue();
        assertThat(updatedSettings.settings().settingsJson()).contains("celestial");

        // 3. Add member
        UUID officer = UUID.randomUUID();
        repository.addMember(new IslandMember(islandId, officer, IslandRole.OFFICER, Instant.now()));

        Island withOfficer = repository.findIslandById(islandId).orElseThrow();
        assertThat(withOfficer.members()).hasSize(2);
        assertThat(withOfficer.getRole(officer)).isEqualTo(IslandRole.OFFICER);

        // 4. Update member role
        repository.updateMemberRole(islandId, officer, IslandRole.MEMBER);
        Island withMemberRole = repository.findIslandById(islandId).orElseThrow();
        assertThat(withMemberRole.getRole(officer)).isEqualTo(IslandRole.MEMBER);

        // 5. Remove member
        repository.removeMember(islandId, officer);
        Island withoutMember = repository.findIslandById(islandId).orElseThrow();
        assertThat(withoutMember.members()).hasSize(1);
        assertThat(withoutMember.getRole(officer)).isEqualTo(IslandRole.VISITOR);
    }

    @Test
    @DisplayName("Should persist and update upgrades and log visits")
    void shouldPersistUpgradesAndVisits() throws SQLException {
        UUID owner = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        Instant now = Instant.now();

        Island island = new Island(
                islandId,
                owner,
                new IslandLocation("skyrealms_islands", 0, 100, 500),
                100,
                0,
                "Upgrade Test Island",
                IslandStatus.ACTIVE,
                now,
                now,
                IslandSettings.defaultSettings(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        repository.createIsland(island);

        // Save initial upgrade
        IslandUpgrade sizeUpgrade = new IslandUpgrade(islandId, "size", 1, Instant.now());
        repository.saveUpgrade(sizeUpgrade);

        Island withUpgrade = repository.findIslandById(islandId).orElseThrow();
        assertThat(withUpgrade.upgrades()).containsKey("size");
        assertThat(withUpgrade.upgrades().get("size").upgradeLevel()).isEqualTo(1);

        // Update upgrade level
        IslandUpgrade sizeUpgrade2 = new IslandUpgrade(islandId, "size", 2, Instant.now());
        repository.saveUpgrade(sizeUpgrade2);

        Island withUpgrade2 = repository.findIslandById(islandId).orElseThrow();
        assertThat(withUpgrade2.upgrades().get("size").upgradeLevel()).isEqualTo(2);

        // Record visit
        UUID visitor = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        repository.recordVisit(new IslandVisit(visitId, islandId, visitor, Instant.now(), 120));

        // Load all active islands
        List<Island> activeIslands = repository.loadAllActiveIslands();
        assertThat(activeIslands).isNotEmpty();
        assertThat(activeIslands.stream().anyMatch(i -> i.id().equals(islandId))).isTrue();
    }
}
