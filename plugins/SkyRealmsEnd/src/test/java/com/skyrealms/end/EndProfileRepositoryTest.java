package com.skyrealms.end;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.end.district.EndDistrict;
import com.skyrealms.end.service.EndProfileRepository;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EndProfileRepositoryTest {

    private HikariDatabaseService databaseService;
    private EndProfileRepository repository;

    @BeforeEach
    void setUp() {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("EndTestPool-" + UUID.randomUUID());
        cfg.setJdbcUrl("jdbc:h2:mem:endtest_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(cfg);
        DatabaseMigrationService migrations = new DatabaseMigrationService(databaseService.getDataSource(), getClass().getClassLoader());
        MigrateResult res = migrations.runMigrations();
        assertThat(res.success).isTrue();
        repository = new EndProfileRepository(databaseService);
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    void createsProfileAndPersistsIdempotentDistrictDiscovery() throws Exception {
        UUID uuid = UUID.randomUUID();

        EndProfileRepository.EndPlayerProfile initial = repository.loadOrCreate(uuid);
        assertThat(initial.playerUuid()).isEqualTo(uuid);
        assertThat(initial.reputation()).isZero();
        assertThat(initial.dragonLicenseUnlocked()).isFalse();
        assertThat(initial.activeDiscipline()).isEqualTo("MELEE");
        assertThat(initial.discoveredDistricts()).isEmpty();

        assertThat(repository.markDistrictDiscovered(uuid, EndDistrict.CENTRAL_PLAZA)).isTrue();
        assertThat(repository.markDistrictDiscovered(uuid, EndDistrict.CENTRAL_PLAZA)).isFalse();
        assertThat(repository.markDistrictDiscovered(uuid, EndDistrict.ARCANE_QUARTER)).isTrue();

        EndProfileRepository.EndPlayerProfile reloaded = repository.loadOrCreate(uuid);
        assertThat(reloaded.discoveredDistricts())
                .containsExactlyInAnyOrder(EndDistrict.CENTRAL_PLAZA, EndDistrict.ARCANE_QUARTER);
    }
}

