package com.skyrealms.farming.repository;

import com.skyrealms.farming.model.*;
import com.skyrealms.platform.database.DatabaseService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * High-performance database repository for SkyRealms Farming.
 * Manages generators, upgrades, player progress, crop collections, and mutations.
 */
public final class FarmingRepository {

    private final DatabaseService databaseService;

    public FarmingRepository(@NotNull DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService cannot be null");
    }

    /**
     * Loads all active farming generators across all worlds on server startup.
     */
    @NotNull
    public List<FarmingGenerator> loadAllActiveGenerators() throws SQLException {
        String sql = """
            SELECT generator_id, island_id, owner_uuid, generator_tier, world_name,
                   core_x, core_y, core_z, status, total_harvests, created_at, updated_at
            FROM sr_farming_generators
            WHERE status = 'ACTIVE'
        """;

        List<FarmingGenerator> generators = new ArrayList<>();
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                generators.add(mapGenerator(conn, rs));
            }
        }
        return generators;
    }

    /**
     * Finds a generator by its unique UUID.
     */
    @NotNull
    public Optional<FarmingGenerator> findGeneratorById(@NotNull UUID generatorId) throws SQLException {
        String sql = """
            SELECT generator_id, island_id, owner_uuid, generator_tier, world_name,
                   core_x, core_y, core_z, status, total_harvests, created_at, updated_at
            FROM sr_farming_generators
            WHERE generator_id = ?
        """;

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, generatorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapGenerator(conn, rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Loads all generators belonging to a specific island.
     */
    @NotNull
    public List<FarmingGenerator> loadGeneratorsByIsland(@NotNull UUID islandId) throws SQLException {
        String sql = """
            SELECT generator_id, island_id, owner_uuid, generator_tier, world_name,
                   core_x, core_y, core_z, status, total_harvests, created_at, updated_at
            FROM sr_farming_generators
            WHERE island_id = ?
        """;

        List<FarmingGenerator> generators = new ArrayList<>();
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    generators.add(mapGenerator(conn, rs));
                }
            }
        }
        return generators;
    }

    /**
     * Saves or updates a generator record.
     */
    public void saveGenerator(@NotNull FarmingGenerator generator) throws SQLException {
        String checkSql = "SELECT 1 FROM sr_farming_generators WHERE generator_id = ?";
        String insertSql = """
            INSERT INTO sr_farming_generators (
                generator_id, island_id, owner_uuid, generator_tier, world_name,
                core_x, core_y, core_z, status, total_harvests, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        String updateSql = """
            UPDATE sr_farming_generators
            SET generator_tier = ?, status = ?, total_harvests = ?, updated_at = ?
            WHERE generator_id = ?
        """;

        try (Connection conn = databaseService.getConnection()) {
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setObject(1, generator.generatorId());
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (!exists) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setObject(1, generator.generatorId());
                    ps.setObject(2, generator.islandId());
                    ps.setObject(3, generator.ownerUuid());
                    ps.setString(4, generator.tier().name());
                    ps.setString(5, generator.worldName());
                    ps.setInt(6, generator.coreX());
                    ps.setInt(7, generator.coreY());
                    ps.setInt(8, generator.coreZ());
                    ps.setString(9, generator.status().name());
                    ps.setLong(10, generator.totalHarvests());
                    ps.setTimestamp(11, Timestamp.from(generator.createdAt()));
                    ps.setTimestamp(12, Timestamp.from(generator.updatedAt()));
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, generator.tier().name());
                    ps.setString(2, generator.status().name());
                    ps.setLong(3, generator.totalHarvests());
                    ps.setTimestamp(4, Timestamp.from(generator.updatedAt()));
                    ps.setObject(5, generator.generatorId());
                    ps.executeUpdate();
                }
            }

            // Save upgrades
            for (GeneratorUpgrade upgrade : generator.upgrades().values()) {
                saveUpgrade(conn, generator.generatorId(), upgrade);
            }
        }
    }

    /**
     * Deletes a generator by ID.
     */
    public void deleteGenerator(@NotNull UUID generatorId) throws SQLException {
        String sql = "DELETE FROM sr_farming_generators WHERE generator_id = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, generatorId);
            ps.executeUpdate();
        }
    }

    /**
     * Saves or updates a generator upgrade.
     */
    public void saveUpgrade(@NotNull UUID generatorId, @NotNull GeneratorUpgrade upgrade) throws SQLException {
        try (Connection conn = databaseService.getConnection()) {
            saveUpgrade(conn, generatorId, upgrade);
        }
    }

    private void saveUpgrade(Connection conn, UUID generatorId, GeneratorUpgrade upgrade) throws SQLException {
        String checkSql = "SELECT 1 FROM sr_farming_generator_upgrades WHERE generator_id = ? AND upgrade_key = ?";
        String insertSql = """
            INSERT INTO sr_farming_generator_upgrades (generator_id, upgrade_key, upgrade_level, updated_at)
            VALUES (?, ?, ?, ?)
        """;
        String updateSql = """
            UPDATE sr_farming_generator_upgrades
            SET upgrade_level = ?, updated_at = ?
            WHERE generator_id = ? AND upgrade_key = ?
        """;

        boolean exists = false;
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setObject(1, generatorId);
            ps.setString(2, upgrade.key());
            try (ResultSet rs = ps.executeQuery()) {
                exists = rs.next();
            }
        }

        if (!exists) {
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, generatorId);
                ps.setString(2, upgrade.key());
                ps.setInt(3, upgrade.level());
                ps.setTimestamp(4, Timestamp.from(upgrade.updatedAt()));
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, upgrade.level());
                ps.setTimestamp(2, Timestamp.from(upgrade.updatedAt()));
                ps.setObject(3, generatorId);
                ps.setString(4, upgrade.key());
                ps.executeUpdate();
            }
        }
    }

    /**
     * Loads player farming progress or creates default.
     */
    @NotNull
    public FarmingPlayerProgress getOrCreatePlayerProgress(@NotNull UUID playerUuid) throws SQLException {
        String selectSql = "SELECT farming_xp, farming_level, total_harvests, updated_at FROM sr_farming_player_progress WHERE player_uuid = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setObject(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long xp = rs.getLong("farming_xp");
                    int level = rs.getInt("farming_level");
                    long harvests = rs.getLong("total_harvests");
                    Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
                    return new FarmingPlayerProgress(playerUuid, xp, level, harvests, updatedAt);
                }
            }
        }

        // Create default
        FarmingPlayerProgress def = FarmingPlayerProgress.createDefault(playerUuid);
        savePlayerProgress(def);
        return def;
    }

    /**
     * Saves player farming progress.
     */
    public void savePlayerProgress(@NotNull FarmingPlayerProgress progress) throws SQLException {
        String checkSql = "SELECT 1 FROM sr_farming_player_progress WHERE player_uuid = ?";
        String insertSql = "INSERT INTO sr_farming_player_progress (player_uuid, farming_xp, farming_level, total_harvests, updated_at) VALUES (?, ?, ?, ?, ?)";
        String updateSql = "UPDATE sr_farming_player_progress SET farming_xp = ?, farming_level = ?, total_harvests = ?, updated_at = ? WHERE player_uuid = ?";

        try (Connection conn = databaseService.getConnection()) {
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setObject(1, progress.playerUuid());
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (!exists) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setObject(1, progress.playerUuid());
                    ps.setLong(2, progress.farmingXp());
                    ps.setInt(3, progress.farmingLevel());
                    ps.setLong(4, progress.totalHarvests());
                    ps.setTimestamp(5, Timestamp.from(progress.updatedAt()));
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setLong(1, progress.farmingXp());
                    ps.setInt(2, progress.farmingLevel());
                    ps.setLong(3, progress.totalHarvests());
                    ps.setTimestamp(4, Timestamp.from(progress.updatedAt()));
                    ps.setObject(5, progress.playerUuid());
                    ps.executeUpdate();
                }
            }
        }
    }

    /**
     * Loads all collections for a player.
     */
    @NotNull
    public Map<String, FarmingCropCollection> loadCollections(@NotNull UUID playerUuid) throws SQLException {
        String sql = "SELECT crop_type, amount_collected, tier_unlocked, updated_at FROM sr_farming_crop_collections WHERE player_uuid = ?";
        Map<String, FarmingCropCollection> collections = new HashMap<>();

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String crop = rs.getString("crop_type").toUpperCase();
                    long amount = rs.getLong("amount_collected");
                    int tier = rs.getInt("tier_unlocked");
                    Instant updated = rs.getTimestamp("updated_at").toInstant();
                    collections.put(crop, new FarmingCropCollection(playerUuid, crop, amount, tier, updated));
                }
            }
        }
        return collections;
    }

    /**
     * Increments crop collection count atomically.
     */
    public void incrementCollection(@NotNull UUID playerUuid, @NotNull String cropType, long amount) throws SQLException {
        String normalizedCrop = cropType.toUpperCase();
        String checkSql = "SELECT amount_collected, tier_unlocked FROM sr_farming_crop_collections WHERE player_uuid = ? AND crop_type = ?";
        String insertSql = "INSERT INTO sr_farming_crop_collections (player_uuid, crop_type, amount_collected, tier_unlocked, updated_at) VALUES (?, ?, ?, 1, ?)";
        String updateSql = "UPDATE sr_farming_crop_collections SET amount_collected = amount_collected + ?, updated_at = ? WHERE player_uuid = ? AND crop_type = ?";

        try (Connection conn = databaseService.getConnection()) {
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setObject(1, playerUuid);
                ps.setString(2, normalizedCrop);
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (!exists) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setObject(1, playerUuid);
                    ps.setString(2, normalizedCrop);
                    ps.setLong(3, amount);
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setLong(1, amount);
                    ps.setTimestamp(2, Timestamp.from(Instant.now()));
                    ps.setObject(3, playerUuid);
                    ps.setString(4, normalizedCrop);
                    ps.executeUpdate();
                }
            }
        }
    }

    /**
     * Records a mutation discovery.
     */
    public void recordMutationDiscovery(@NotNull UUID playerUuid, @NotNull String mutationType, @NotNull String cropType) throws SQLException {
        String checkSql = "SELECT discovery_id FROM sr_farming_mutation_discoveries WHERE player_uuid = ? AND mutation_type = ? AND crop_type = ?";
        String insertSql = "INSERT INTO sr_farming_mutation_discoveries (discovery_id, player_uuid, mutation_type, crop_type, discovered_at, count) VALUES (?, ?, ?, ?, ?, 1)";
        String updateSql = "UPDATE sr_farming_mutation_discoveries SET count = count + 1 WHERE discovery_id = ?";

        try (Connection conn = databaseService.getConnection()) {
            UUID discoveryId = null;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setObject(1, playerUuid);
                ps.setString(2, mutationType.toUpperCase());
                ps.setString(3, cropType.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        discoveryId = (UUID) rs.getObject("discovery_id");
                    }
                }
            }

            if (discoveryId == null) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, playerUuid);
                    ps.setString(3, mutationType.toUpperCase());
                    ps.setString(4, cropType.toUpperCase());
                    ps.setTimestamp(5, Timestamp.from(Instant.now()));
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setObject(1, discoveryId);
                    ps.executeUpdate();
                }
            }
        }
    }

    private FarmingGenerator mapGenerator(Connection conn, ResultSet rs) throws SQLException {
        UUID generatorId = (UUID) rs.getObject("generator_id");
        UUID islandId = (UUID) rs.getObject("island_id");
        UUID ownerUuid = (UUID) rs.getObject("owner_uuid");
        FarmingGeneratorTier tier = FarmingGeneratorTier.valueOf(rs.getString("generator_tier"));
        String worldName = rs.getString("world_name");
        int coreX = rs.getInt("core_x");
        int coreY = rs.getInt("core_y");
        int coreZ = rs.getInt("core_z");
        GeneratorStatus status = GeneratorStatus.valueOf(rs.getString("status"));
        long totalHarvests = rs.getLong("total_harvests");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

        // Load upgrades for generator
        List<GeneratorUpgrade> upgrades = new ArrayList<>();
        String upSql = "SELECT upgrade_key, upgrade_level, updated_at FROM sr_farming_generator_upgrades WHERE generator_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(upSql)) {
            ps.setObject(1, generatorId);
            try (ResultSet upRs = ps.executeQuery()) {
                while (upRs.next()) {
                    String key = upRs.getString("upgrade_key");
                    int lvl = upRs.getInt("upgrade_level");
                    Instant upDate = upRs.getTimestamp("updated_at").toInstant();
                    upgrades.add(new GeneratorUpgrade(key, lvl, upDate));
                }
            }
        }

        return new FarmingGenerator(
                generatorId, islandId, ownerUuid, tier, worldName,
                coreX, coreY, coreZ, status, totalHarvests, createdAt, updatedAt, upgrades
        );
    }

    // =========================================================================
    // SkyRealms Garden Persistence
    // =========================================================================

    @NotNull
    public List<com.skyrealms.farming.garden.Garden> loadAllGardens() throws SQLException {
        String sql = """
            SELECT garden_id, island_id, region_index, world_name, center_x, center_y, center_z,
                   size, unlocked, unlocked_at, created_at
            FROM sr_gardens
        """;
        List<com.skyrealms.farming.garden.Garden> gardens = new ArrayList<>();
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                gardens.add(mapGarden(rs));
            }
        }
        return gardens;
    }

    @NotNull
    public Optional<com.skyrealms.farming.garden.Garden> findGardenByIsland(@NotNull UUID islandId) throws SQLException {
        String sql = """
            SELECT garden_id, island_id, region_index, world_name, center_x, center_y, center_z,
                   size, unlocked, unlocked_at, created_at
            FROM sr_gardens
            WHERE island_id = ?
        """;
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapGarden(rs));
                }
            }
        }
        return Optional.empty();
    }

    public int findNextGardenRegionIndex() throws SQLException {
        String sql = "SELECT COALESCE(MAX(region_index), -1) + 1 AS next_index FROM sr_gardens";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("next_index");
            }
        }
        return 0;
    }

    public void saveGarden(@NotNull com.skyrealms.farming.garden.Garden garden) throws SQLException {
        String checkSql = "SELECT 1 FROM sr_gardens WHERE garden_id = ?";
        String insertSql = """
            INSERT INTO sr_gardens (garden_id, island_id, region_index, world_name,
                                    center_x, center_y, center_z, size, unlocked,
                                    unlocked_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        String updateSql = """
            UPDATE sr_gardens
            SET unlocked = ?, unlocked_at = ?, updated_at = ?
            WHERE garden_id = ?
        """;

        try (Connection conn = databaseService.getConnection()) {
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setObject(1, garden.gardenId());
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }

            Timestamp now = Timestamp.from(Instant.now());
            if (!exists) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setObject(1, garden.gardenId());
                    ps.setObject(2, garden.islandId());
                    ps.setInt(3, garden.regionIndex());
                    ps.setString(4, garden.worldName());
                    ps.setInt(5, garden.centerX());
                    ps.setInt(6, garden.centerY());
                    ps.setInt(7, garden.centerZ());
                    ps.setInt(8, garden.size());
                    ps.setBoolean(9, garden.unlocked());
                    ps.setTimestamp(10, garden.unlockedAt() != null ? Timestamp.from(garden.unlockedAt()) : now);
                    ps.setTimestamp(11, Timestamp.from(garden.createdAt()));
                    ps.setTimestamp(12, now);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setBoolean(1, garden.unlocked());
                    ps.setTimestamp(2, garden.unlockedAt() != null ? Timestamp.from(garden.unlockedAt()) : now);
                    ps.setTimestamp(3, now);
                    ps.setObject(4, garden.gardenId());
                    ps.executeUpdate();
                }
            }
        }
    }

    @NotNull
    public List<com.skyrealms.farming.garden.GardenPlot> findPlotsByIsland(@NotNull UUID islandId) throws SQLException {
        String sql = """
            SELECT island_id, plot_index, unlocked, crop_type, soil_type,
                   fertility, moisture, nutrient_level
            FROM sr_garden_plots
            WHERE island_id = ?
            ORDER BY plot_index ASC
        """;
        List<com.skyrealms.farming.garden.GardenPlot> plots = new ArrayList<>();
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    plots.add(mapPlot(rs));
                }
            }
        }
        return plots;
    }

    public void savePlot(@NotNull com.skyrealms.farming.garden.GardenPlot plot) throws SQLException {
        String checkSql = "SELECT 1 FROM sr_garden_plots WHERE island_id = ? AND plot_index = ?";
        String insertSql = """
            INSERT INTO sr_garden_plots (island_id, plot_index, unlocked, crop_type, soil_type,
                                         fertility, moisture, nutrient_level, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        String updateSql = """
            UPDATE sr_garden_plots
            SET unlocked = ?, crop_type = ?, soil_type = ?, fertility = ?, moisture = ?,
                nutrient_level = ?, updated_at = ?
            WHERE island_id = ? AND plot_index = ?
        """;

        try (Connection conn = databaseService.getConnection()) {
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setObject(1, plot.islandId());
                ps.setInt(2, plot.plotIndex());
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }

            Timestamp now = Timestamp.from(Instant.now());
            if (!exists) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setObject(1, plot.islandId());
                    ps.setInt(2, plot.plotIndex());
                    ps.setBoolean(3, plot.unlocked());
                    ps.setString(4, plot.cropType());
                    ps.setString(5, plot.soilType());
                    ps.setDouble(6, plot.fertility());
                    ps.setDouble(7, plot.moisture());
                    ps.setDouble(8, plot.nutrientLevel());
                    ps.setTimestamp(9, now);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setBoolean(1, plot.unlocked());
                    ps.setString(2, plot.cropType());
                    ps.setString(3, plot.soilType());
                    ps.setDouble(4, plot.fertility());
                    ps.setDouble(5, plot.moisture());
                    ps.setDouble(6, plot.nutrientLevel());
                    ps.setTimestamp(7, now);
                    ps.setObject(8, plot.islandId());
                    ps.setInt(9, plot.plotIndex());
                    ps.executeUpdate();
                }
            }
        }
    }

    private com.skyrealms.farming.garden.Garden mapGarden(ResultSet rs) throws SQLException {
        UUID gardenId = (UUID) rs.getObject("garden_id");
        UUID islandId = (UUID) rs.getObject("island_id");
        int regionIndex = rs.getInt("region_index");
        String worldName = rs.getString("world_name");
        int centerX = rs.getInt("center_x");
        int centerY = rs.getInt("center_y");
        int centerZ = rs.getInt("center_z");
        int size = rs.getInt("size");
        boolean unlocked = rs.getBoolean("unlocked");
        Timestamp unlockedTs = rs.getTimestamp("unlocked_at");
        Instant unlockedAt = unlockedTs != null ? unlockedTs.toInstant() : null;
        Instant createdAt = rs.getTimestamp("created_at").toInstant();

        return new com.skyrealms.farming.garden.Garden(
                gardenId, islandId, regionIndex, worldName,
                centerX, centerY, centerZ, size, unlocked, unlockedAt, createdAt
        );
    }

    private com.skyrealms.farming.garden.GardenPlot mapPlot(ResultSet rs) throws SQLException {
        UUID islandId = (UUID) rs.getObject("island_id");
        int plotIndex = rs.getInt("plot_index");
        boolean unlocked = rs.getBoolean("unlocked");
        String cropType = rs.getString("crop_type");
        String soilType = rs.getString("soil_type");
        double fertility = rs.getDouble("fertility");
        double moisture = rs.getDouble("moisture");
        double nutrientLevel = rs.getDouble("nutrient_level");

        return new com.skyrealms.farming.garden.GardenPlot(
                islandId, plotIndex, unlocked, cropType, soilType,
                fertility, moisture, nutrientLevel
        );
    }

    // =========================================================================
    // Phase G3 & G4 Repository Methods
    // =========================================================================

    public record GardenOffering(UUID islandId, UUID submittedBy, int hayBalesCount, int carrotsCount, int sugarCaneCount, String status, Instant submittedAt) {}
    public record GardenUnlock(UUID islandId, boolean offeringCompleted, boolean cinematicCompleted, Instant unlockedAt) {}
    public record FarmingQuest(UUID playerUuid, String questId, String status, int tilledCount, int plantedCount, int harvestedCount, Instant completedAt, boolean claimedRewards, Instant updatedAt) {}
    public record CropMasteryRecord(UUID playerUuid, String cropType, long masteryXp, int masteryLevel, String claimedMilestones, Instant updatedAt) {}

    @NotNull
    public Optional<GardenOffering> findOfferingByIsland(@NotNull UUID islandId) throws SQLException {
        String sql = "SELECT island_id, submitted_by, hay_bales_count, carrots_count, sugar_cane_count, status, submitted_at FROM sr_garden_offerings WHERE island_id = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new GardenOffering(
                            (UUID) rs.getObject("island_id"),
                            (UUID) rs.getObject("submitted_by"),
                            rs.getInt("hay_bales_count"),
                            rs.getInt("carrots_count"),
                            rs.getInt("sugar_cane_count"),
                            rs.getString("status"),
                            rs.getTimestamp("submitted_at").toInstant()
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void saveOffering(@NotNull GardenOffering offering) throws SQLException {
        String sql = """
            MERGE INTO sr_garden_offerings (island_id, submitted_by, hay_bales_count, carrots_count, sugar_cane_count, status, submitted_at)
            KEY(island_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, offering.islandId());
            ps.setObject(2, offering.submittedBy());
            ps.setInt(3, offering.hayBalesCount());
            ps.setInt(4, offering.carrotsCount());
            ps.setInt(5, offering.sugarCaneCount());
            ps.setString(6, offering.status());
            ps.setTimestamp(7, Timestamp.from(offering.submittedAt()));
            ps.executeUpdate();
        }
    }

    @NotNull
    public Optional<GardenUnlock> findGardenUnlock(@NotNull UUID islandId) throws SQLException {
        String sql = "SELECT island_id, offering_completed, cinematic_completed, unlocked_at FROM sr_garden_unlocks WHERE island_id = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("unlocked_at");
                    return Optional.of(new GardenUnlock(
                            (UUID) rs.getObject("island_id"),
                            rs.getBoolean("offering_completed"),
                            rs.getBoolean("cinematic_completed"),
                            ts != null ? ts.toInstant() : null
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void saveGardenUnlock(@NotNull GardenUnlock unlock) throws SQLException {
        String sql = """
            MERGE INTO sr_garden_unlocks (island_id, offering_completed, cinematic_completed, unlocked_at)
            KEY(island_id)
            VALUES (?, ?, ?, ?)
        """;
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, unlock.islandId());
            ps.setBoolean(2, unlock.offeringCompleted());
            ps.setBoolean(3, unlock.cinematicCompleted());
            ps.setTimestamp(4, unlock.unlockedAt() != null ? Timestamp.from(unlock.unlockedAt()) : null);
            ps.executeUpdate();
        }
    }

    public boolean hasCompletedElarionIntro(@NotNull UUID playerUuid) throws SQLException {
        String sql = "SELECT elarion_intro_completed FROM sr_player_research WHERE player_uuid = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("elarion_intro_completed");
                }
            }
        }
        return false;
    }

    public void setElarionIntroCompleted(@NotNull UUID playerUuid, boolean completed) throws SQLException {
        String sql = """
            MERGE INTO sr_player_research (player_uuid, elarion_intro_completed, updated_at)
            KEY(player_uuid)
            VALUES (?, ?, ?)
        """;
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerUuid);
            ps.setBoolean(2, completed);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    @NotNull
    public Optional<FarmingQuest> findQuestProgress(@NotNull UUID playerUuid, @NotNull String questId) throws SQLException {
        String sql = """
            SELECT player_uuid, quest_id, status, tilled_count, planted_count, harvested_count, completed_at, claimed_rewards, updated_at
            FROM sr_farming_quests
            WHERE player_uuid = ? AND quest_id = ?
        """;
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerUuid);
            ps.setString(2, questId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp compTs = rs.getTimestamp("completed_at");
                    return Optional.of(new FarmingQuest(
                            (UUID) rs.getObject("player_uuid"),
                            rs.getString("quest_id"),
                            rs.getString("status"),
                            rs.getInt("tilled_count"),
                            rs.getInt("planted_count"),
                            rs.getInt("harvested_count"),
                            compTs != null ? compTs.toInstant() : null,
                            rs.getBoolean("claimed_rewards"),
                            rs.getTimestamp("updated_at").toInstant()
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void saveQuestProgress(@NotNull FarmingQuest quest) throws SQLException {
        String sql = """
            MERGE INTO sr_farming_quests (player_uuid, quest_id, status, tilled_count, planted_count, harvested_count, completed_at, claimed_rewards, updated_at)
            KEY(player_uuid, quest_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, quest.playerUuid());
            ps.setString(2, quest.questId());
            ps.setString(3, quest.status());
            ps.setInt(4, quest.tilledCount());
            ps.setInt(5, quest.plantedCount());
            ps.setInt(6, quest.harvestedCount());
            ps.setTimestamp(7, quest.completedAt() != null ? Timestamp.from(quest.completedAt()) : null);
            ps.setBoolean(8, quest.claimedRewards());
            ps.setTimestamp(9, Timestamp.from(quest.updatedAt()));
            ps.executeUpdate();
        }
    }

    @NotNull
    public Optional<CropMasteryRecord> findCropMastery(@NotNull UUID playerUuid, @NotNull String cropType) throws SQLException {
        String sql = "SELECT player_uuid, crop_type, mastery_xp, mastery_level, claimed_milestones, updated_at FROM sr_crop_mastery WHERE player_uuid = ? AND crop_type = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerUuid);
            ps.setString(2, cropType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new CropMasteryRecord(
                            (UUID) rs.getObject("player_uuid"),
                            rs.getString("crop_type"),
                            rs.getLong("mastery_xp"),
                            rs.getInt("mastery_level"),
                            rs.getString("claimed_milestones"),
                            rs.getTimestamp("updated_at").toInstant()
                    ));
                }
            }
        }
        return Optional.empty();
    }

    @NotNull
    public List<CropMasteryRecord> findAllCropMasteries(@NotNull UUID playerUuid) throws SQLException {
        String sql = "SELECT player_uuid, crop_type, mastery_xp, mastery_level, claimed_milestones, updated_at FROM sr_crop_mastery WHERE player_uuid = ?";
        List<CropMasteryRecord> masteries = new ArrayList<>();
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    masteries.add(new CropMasteryRecord(
                            (UUID) rs.getObject("player_uuid"),
                            rs.getString("crop_type"),
                            rs.getLong("mastery_xp"),
                            rs.getInt("mastery_level"),
                            rs.getString("claimed_milestones"),
                            rs.getTimestamp("updated_at").toInstant()
                    ));
                }
            }
        }
        return masteries;
    }

    public void saveCropMastery(@NotNull CropMasteryRecord mastery) throws SQLException {
        String sql = """
            MERGE INTO sr_crop_mastery (player_uuid, crop_type, mastery_xp, mastery_level, claimed_milestones, updated_at)
            KEY(player_uuid, crop_type)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, mastery.playerUuid());
            ps.setString(2, mastery.cropType());
            ps.setLong(3, mastery.masteryXp());
            ps.setInt(4, mastery.masteryLevel());
            ps.setString(5, mastery.claimedMilestones());
            ps.setTimestamp(6, Timestamp.from(mastery.updatedAt()));
            ps.executeUpdate();
        }
    }
}
