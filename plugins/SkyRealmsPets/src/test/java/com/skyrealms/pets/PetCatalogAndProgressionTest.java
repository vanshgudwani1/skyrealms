package com.skyrealms.pets;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.pets.catalog.PetCatalog;
import com.skyrealms.pets.model.PetDefinition;
import com.skyrealms.pets.model.PlayerPetData;
import com.skyrealms.pets.repository.PetRepository;
import com.skyrealms.platform.integrations.SkyItemStats;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PetCatalogAndProgressionTest {

    @Test
    @DisplayName("Should register all 8 Cubees Rework Currets pets with valid ModelEngine blueprint IDs and scaled stats")
    void shouldRegisterAllEightCubeesAndScaleStats() {
        PetCatalog catalog = new PetCatalog();

        assertThat(catalog.all()).hasSize(8);
        assertThat(catalog.find("baby_ghast")).isNotNull();
        assertThat(catalog.find("cubee-baby_ghast")).isSameAs(catalog.find("baby_ghast"));
        assertThat(catalog.find("hoody_fox")).isNotNull();
        assertThat(catalog.find("twinkle_witch")).isNotNull();
        assertThat(catalog.find("llama")).isNotNull();
        assertThat(catalog.find("slingshot")).isNotNull();
        assertThat(catalog.find("bombardier_creeper")).isNotNull();
        assertThat(catalog.find("cannon")).isNotNull();
        assertThat(catalog.find("swag_pillager")).isNotNull();

        PetDefinition fox = catalog.find("hoody_fox");
        assertThat(fox).isNotNull();
        assertThat(fox.blueprintId()).isEqualTo("cubee-hoody_fox");
        SkyItemStats lv1 = fox.statsForLevel(1);
        SkyItemStats lv10 = fox.statsForLevel(10);
        assertThat(lv1.farmingFortune()).isCloseTo(0.05, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(lv10.farmingFortune()).isCloseTo(0.25, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(lv10.farmingXpBonus()).isCloseTo(0.15, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("Should persist Cubees unlocks, active selection, level, XP, and nickname via V9 migration")
    void shouldPersistPlayerPetsInH2(@TempDir Path tempDir) throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:" + tempDir.resolve("pets_test").toAbsolutePath() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");

        try (HikariDatabaseService db = new HikariDatabaseService(config)) {
            DatabaseMigrationService migrator = new DatabaseMigrationService(db.getDataSource(), getClass().getClassLoader());
            migrator.runMigrations();

            PetRepository repo = new PetRepository(db);
            UUID playerId = UUID.randomUUID();
            PlayerPetData ghast = new PlayerPetData(playerId, "baby_ghast", 4, 55.5, "Casper", true, 123456L);
            PlayerPetData fox = new PlayerPetData(playerId, "hoody_fox", 1, 0.0, null, false, 123457L);

            repo.savePet(ghast);
            repo.savePet(fox);
            repo.setActivePet(playerId, "hoody_fox");

            Map<String, PlayerPetData> loaded = repo.loadPlayerPets(playerId);
            assertThat(loaded).containsKeys("baby_ghast", "hoody_fox");
            assertThat(loaded.get("baby_ghast").level()).isEqualTo(4);
            assertThat(loaded.get("baby_ghast").nickname()).isEqualTo("Casper");
            assertThat(loaded.get("baby_ghast").active()).isFalse();
            assertThat(loaded.get("hoody_fox").active()).isTrue();
        }
    }
}

