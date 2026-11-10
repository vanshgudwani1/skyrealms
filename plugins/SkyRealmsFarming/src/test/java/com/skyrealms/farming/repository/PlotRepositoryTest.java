package com.skyrealms.farming.repository;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.garden.plot.*;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlotRepositoryTest {

    private HikariDatabaseService databaseService;
    private FarmingRepository repository;
    private UUID testPlayerId;
    private UUID testIslandId;

    @BeforeEach
    void setUp() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("PlotRepo-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:plotdb_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(config);
        new DatabaseMigrationService(
                databaseService.getDataSource(),
                HikariDatabaseService.class.getClassLoader()
        ).runMigrations();

        repository = new FarmingRepository(databaseService);
        testPlayerId = UUID.randomUUID();
        testIslandId = UUID.randomUUID();
        seedPlayerIslandAndPlot();
    }

    @AfterEach
    void tearDown() {
        if (databaseService != null) {
            databaseService.close();
        }
    }

    private void seedPlayerIslandAndPlot() throws SQLException {
        try (Connection conn = databaseService.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO sr_players (id, username, first_joined, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                ps.setObject(1, testPlayerId);
                ps.setString(2, "PlotTester");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO sr_islands (island_id, owner_uuid, world_name, center_x, center_y, center_z, island_size, status) " +
                            "VALUES (?, ?, 'skyrealms_islands', 0, 100, 0, 100, 'ACTIVE')")) {
                ps.setObject(1, testIslandId);
                ps.setObject(2, testPlayerId);
                ps.executeUpdate();
            }
        }
        repository.savePlot(new GardenPlot(testIslandId, 1, true, "WHEAT", "BALANCED", 1.0, 1.0, 1.0));
    }

    @Test
    @DisplayName("PlotConfiguration and CropBeds persist across repository instances after V8 migration")
    void plotConfigAndBedsPersistAcrossRestart() throws SQLException {
        List<CropBed> beds = List.of(
                new CropBed(UUID.randomUUID(), testIslandId, 1, BedType.RICH_SOIL, null, -10, -10, -2, -2),
                new CropBed(UUID.randomUUID(), testIslandId, 1, BedType.SPECIALIZED, CropType.NETHER_WART, 2, 2, 10, 10)
        );
        PlotConfiguration cfg = new PlotConfiguration(
                testIslandId, 1, PlotLayoutPreset.JAPANESE_GARDEN, IrrigationType.PRISMARINE_CANAL,
                DecorationStyle.CHERRY_LANTERNS, 3, 25, beds
        );
        repository.savePlotConfiguration(cfg);

        // Simulate server restart by querying with a fresh FarmingRepository instance
        FarmingRepository reloadedRepo = new FarmingRepository(databaseService);
        Optional<PlotConfiguration> loaded = reloadedRepo.findPlotConfiguration(testIslandId, 1);

        assertThat(loaded).isPresent();
        assertThat(loaded.get().selectedLayout()).isEqualTo(PlotLayoutPreset.JAPANESE_GARDEN);
        assertThat(loaded.get().irrigationType()).isEqualTo(IrrigationType.PRISMARINE_CANAL);
        assertThat(loaded.get().decorationStyle()).isEqualTo(DecorationStyle.CHERRY_LANTERNS);
        assertThat(loaded.get().upgradeLevel()).isEqualTo(3);
        assertThat(loaded.get().expandedRadius()).isEqualTo(PlotUpgradeTier.FULL_INTERIOR_RADIUS);
        assertThat(loaded.get().beds()).hasSize(2);
        assertThat(loaded.get().beds().get(1).bedType()).isEqualTo(BedType.SPECIALIZED);
        assertThat(loaded.get().beds().get(1).targetCrop()).isEqualTo(CropType.NETHER_WART);
    }

    @Test
    @DisplayName("SavedPlotLayout save, list, overwrite, and delete work cleanly alongside existing mastery/journal tables")
    void savedLayoutsPersistAndPreservedTablesStayIntact() throws SQLException {
        List<CropBed> beds = List.of(
                new CropBed(UUID.randomUUID(), testIslandId, 1, BedType.BASIC, CropType.WHEAT, -4, -4, 4, 4)
        );
        SavedPlotLayout layout = new SavedPlotLayout(
                UUID.randomUUID(), testIslandId, GardenPlotCustomizationService.CUSTOM_LAYOUT_NAME, PlotLayoutPreset.SUGAR_CANE_OPTIMAL,
                IrrigationType.BAMBOO_AQUEDUCT, DecorationStyle.TRELLIS_ARCH,
                SavedPlotLayout.serializeBeds(beds), Instant.now()
        );
        repository.savePlotLayout(layout);

        List<SavedPlotLayout> list = repository.findSavedLayouts(testIslandId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).layoutName()).isEqualTo(GardenPlotCustomizationService.CUSTOM_LAYOUT_NAME);
        assertThat(list.get(0).presetBase()).isEqualTo(PlotLayoutPreset.SUGAR_CANE_OPTIMAL);

        // Pre-existing V5/V7 Crop Mastery & Botanical Journal tables remain untouched and writable
        repository.saveCropMastery(new FarmingRepository.CropMasteryRecord(
                testPlayerId, "WHEAT", 500L, 2, "1", Instant.now()
        ));
        assertThat(repository.findCropMastery(testPlayerId, "WHEAT")).isPresent();

        repository.deleteSavedLayout(testIslandId, GardenPlotCustomizationService.CUSTOM_LAYOUT_NAME);
        assertThat(repository.findSavedLayouts(testIslandId)).isEmpty();
    }
}

