package com.skyrealms.islands.repository;

import com.skyrealms.islands.model.*;
import com.skyrealms.platform.database.DatabaseService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Handles authoritative persistence for islands, members, roles, settings, upgrades, and visits.
 */
public final class IslandRepository {

    private final DatabaseService databaseService;

    public IslandRepository(@NotNull DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService cannot be null");
    }

    /**
     * Counts total registered islands to compute spatial allocation slot.
     */
    public int getNextGridIndex() throws SQLException {
        String sql = "SELECT COUNT(*) FROM sr_islands";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Loads all active islands on server boot into the in-memory spatial grid.
     */
    @NotNull
    public List<Island> loadAllActiveIslands() throws SQLException {
        String sql = "SELECT island_id FROM sr_islands WHERE status = 'ACTIVE'";
        List<UUID> ids = new ArrayList<>();

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add((UUID) rs.getObject("island_id"));
            }
        }

        List<Island> islands = new ArrayList<>();
        for (UUID id : ids) {
            findIslandById(id).ifPresent(islands::add);
        }
        return islands;
    }

    /**
     * Queries an island by its unique ID with all members, settings, and upgrades.
     */
    @NotNull
    public Optional<Island> findIslandById(@NotNull UUID islandId) throws SQLException {
        String sql = """
            SELECT island_id, owner_uuid, world_name, center_x, center_y, center_z,
                   island_size, island_level, island_name, status, created_at, updated_at
            FROM sr_islands
            WHERE island_id = ?
        """;

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapIsland(conn, rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Queries an island owned by a specific player.
     */
    @NotNull
    public Optional<Island> findIslandByOwner(@NotNull UUID ownerUuid) throws SQLException {
        String sql = "SELECT island_id FROM sr_islands WHERE owner_uuid = ? AND status = 'ACTIVE'";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, ownerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID id = (UUID) rs.getObject("island_id");
                    return findIslandById(id);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Queries an island where the player is a member (either owner, officer, or member).
     */
    @NotNull
    public Optional<Island> findIslandByMember(@NotNull UUID playerUuid) throws SQLException {
        String sql = """
            SELECT i.island_id
            FROM sr_islands i
            JOIN sr_island_members m ON i.island_id = m.island_id
            WHERE m.player_uuid = ? AND i.status = 'ACTIVE'
        """;
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID id = (UUID) rs.getObject("island_id");
                    return findIslandById(id);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Atomically creates a new island, leader member row, and default settings in a single transaction.
     */
    public void createIsland(@NotNull Island island) throws SQLException {
        databaseService.executeTransaction(conn -> {
            // 1. Insert Island row
            String insertIsland = """
                INSERT INTO sr_islands (
                    island_id, owner_uuid, world_name, center_x, center_y, center_z,
                    island_size, island_level, island_name, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = conn.prepareStatement(insertIsland)) {
                ps.setObject(1, island.id());
                ps.setObject(2, island.ownerUuid());
                ps.setString(3, island.location().worldName());
                ps.setInt(4, island.location().centerX());
                ps.setInt(5, island.location().centerY());
                ps.setInt(6, island.location().centerZ());
                ps.setInt(7, island.size());
                ps.setLong(8, island.level());
                ps.setString(9, island.name());
                ps.setString(10, island.status().name());
                ps.setTimestamp(11, Timestamp.from(island.createdAt()));
                ps.setTimestamp(12, Timestamp.from(island.updatedAt()));
                ps.executeUpdate();
            }

            // 2. Insert Leader member row
            String insertMember = """
                INSERT INTO sr_island_members (island_id, player_uuid, role, joined_at)
                VALUES (?, ?, ?, ?)
            """;
            try (PreparedStatement ps = conn.prepareStatement(insertMember)) {
                ps.setObject(1, island.id());
                ps.setObject(2, island.ownerUuid());
                ps.setString(3, IslandRole.LEADER.name());
                ps.setTimestamp(4, Timestamp.from(island.createdAt()));
                ps.executeUpdate();
            }

            // 3. Insert Default Settings row
            String insertSettings = """
                INSERT INTO sr_island_settings (
                    island_id, public_visits, visitor_block_break, visitor_block_place,
                    visitor_container_access, pvp_enabled, settings_json, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = conn.prepareStatement(insertSettings)) {
                IslandSettings s = island.settings();
                ps.setObject(1, island.id());
                ps.setBoolean(2, s.publicVisits());
                ps.setBoolean(3, s.visitorBlockBreak());
                ps.setBoolean(4, s.visitorBlockPlace());
                ps.setBoolean(5, s.visitorContainerAccess());
                ps.setBoolean(6, s.pvpEnabled());
                ps.setString(7, s.settingsJson());
                ps.setTimestamp(8, Timestamp.from(island.createdAt()));
                ps.executeUpdate();
            }

            return null;
        });
    }

    /**
     * Updates core metadata of an island.
     */
    public void updateIsland(@NotNull Island island) throws SQLException {
        String sql = """
            UPDATE sr_islands
            SET island_size = ?, island_level = ?, island_name = ?, status = ?, updated_at = ?
            WHERE island_id = ?
        """;
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, island.size());
            ps.setLong(2, island.level());
            ps.setString(3, island.name());
            ps.setString(4, island.status().name());
            ps.setTimestamp(5, Timestamp.from(island.updatedAt()));
            ps.setObject(6, island.id());
            ps.executeUpdate();
        }
    }

    /**
     * Persists updated settings.
     */
    public void updateSettings(@NotNull UUID islandId, @NotNull IslandSettings settings) throws SQLException {
        String sql = """
            UPDATE sr_island_settings
            SET public_visits = ?, visitor_block_break = ?, visitor_block_place = ?,
                visitor_container_access = ?, pvp_enabled = ?, settings_json = ?, updated_at = CURRENT_TIMESTAMP
            WHERE island_id = ?
        """;
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, settings.publicVisits());
            ps.setBoolean(2, settings.visitorBlockBreak());
            ps.setBoolean(3, settings.visitorBlockPlace());
            ps.setBoolean(4, settings.visitorContainerAccess());
            ps.setBoolean(5, settings.pvpEnabled());
            ps.setString(6, settings.settingsJson());
            ps.setObject(7, islandId);
            ps.executeUpdate();
        }
    }

    /**
     * Adds a member to an island.
     */
    public void addMember(@NotNull IslandMember member) throws SQLException {
        String sql = "INSERT INTO sr_island_members (island_id, player_uuid, role, joined_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, member.islandId());
            ps.setObject(2, member.playerUuid());
            ps.setString(3, member.role().name());
            ps.setTimestamp(4, Timestamp.from(member.joinedAt()));
            ps.executeUpdate();
        }
    }

    /**
     * Updates an existing member's role.
     */
    public void updateMemberRole(@NotNull UUID islandId, @NotNull UUID playerUuid, @NotNull IslandRole newRole) throws SQLException {
        String sql = "UPDATE sr_island_members SET role = ? WHERE island_id = ? AND player_uuid = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newRole.name());
            ps.setObject(2, islandId);
            ps.setObject(3, playerUuid);
            ps.executeUpdate();
        }
    }

    /**
     * Removes a member from an island.
     */
    public void removeMember(@NotNull UUID islandId, @NotNull UUID playerUuid) throws SQLException {
        String sql = "DELETE FROM sr_island_members WHERE island_id = ? AND player_uuid = ?";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, islandId);
            ps.setObject(2, playerUuid);
            ps.executeUpdate();
        }
    }

    /**
     * Saves or updates an island upgrade tier.
     */
    public void saveUpgrade(@NotNull IslandUpgrade upgrade) throws SQLException {
        databaseService.executeTransaction(conn -> {
            String check = "SELECT 1 FROM sr_island_upgrades WHERE island_id = ? AND upgrade_key = ?";
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement(check)) {
                ps.setObject(1, upgrade.islandId());
                ps.setString(2, upgrade.upgradeKey());
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                String update = "UPDATE sr_island_upgrades SET upgrade_level = ?, updated_at = ? WHERE island_id = ? AND upgrade_key = ?";
                try (PreparedStatement ps = conn.prepareStatement(update)) {
                    ps.setInt(1, upgrade.upgradeLevel());
                    ps.setTimestamp(2, Timestamp.from(upgrade.updatedAt()));
                    ps.setObject(3, upgrade.islandId());
                    ps.setString(4, upgrade.upgradeKey());
                    ps.executeUpdate();
                }
            } else {
                String insert = "INSERT INTO sr_island_upgrades (island_id, upgrade_key, upgrade_level, updated_at) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setObject(1, upgrade.islandId());
                    ps.setString(2, upgrade.upgradeKey());
                    ps.setInt(3, upgrade.upgradeLevel());
                    ps.setTimestamp(4, Timestamp.from(upgrade.updatedAt()));
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    /**
     * Records a visit entry.
     */
    public void recordVisit(@NotNull IslandVisit visit) throws SQLException {
        String sql = "INSERT INTO sr_island_visits (visit_id, island_id, visitor_uuid, visited_at, visit_duration_seconds) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, visit.visitId());
            ps.setObject(2, visit.islandId());
            ps.setObject(3, visit.visitorUuid());
            ps.setTimestamp(4, Timestamp.from(visit.visitedAt()));
            ps.setLong(5, visit.visitDurationSeconds());
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helper Row Mappers
    // ---------------------------------------------------------------------------------------------

    private Island mapIsland(Connection conn, ResultSet rs) throws SQLException {
        UUID islandId = (UUID) rs.getObject("island_id");
        UUID ownerUuid = (UUID) rs.getObject("owner_uuid");
        String worldName = rs.getString("world_name");
        int cx = rs.getInt("center_x");
        int cy = rs.getInt("center_y");
        int cz = rs.getInt("center_z");
        int size = rs.getInt("island_size");
        long level = rs.getLong("island_level");
        String name = rs.getString("island_name");
        IslandStatus status = IslandStatus.valueOf(rs.getString("status"));
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

        IslandLocation loc = new IslandLocation(worldName, cx, cy, cz);
        IslandSettings settings = loadSettings(conn, islandId);
        List<IslandMember> members = loadMembers(conn, islandId);
        List<IslandUpgrade> upgrades = loadUpgrades(conn, islandId);

        return new Island(islandId, ownerUuid, loc, size, level, name, status, createdAt, updatedAt, settings, members, upgrades);
    }

    private IslandSettings loadSettings(Connection conn, UUID islandId) throws SQLException {
        String sql = "SELECT public_visits, visitor_block_break, visitor_block_place, visitor_container_access, pvp_enabled, settings_json FROM sr_island_settings WHERE island_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new IslandSettings(
                            rs.getBoolean("public_visits"),
                            rs.getBoolean("visitor_block_break"),
                            rs.getBoolean("visitor_block_place"),
                            rs.getBoolean("visitor_container_access"),
                            rs.getBoolean("pvp_enabled"),
                            rs.getString("settings_json")
                    );
                }
            }
        }
        return IslandSettings.defaultSettings();
    }

    private List<IslandMember> loadMembers(Connection conn, UUID islandId) throws SQLException {
        String sql = "SELECT player_uuid, role, joined_at FROM sr_island_members WHERE island_id = ?";
        List<IslandMember> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new IslandMember(
                            islandId,
                            (UUID) rs.getObject("player_uuid"),
                            IslandRole.valueOf(rs.getString("role")),
                            rs.getTimestamp("joined_at").toInstant()
                    ));
                }
            }
        }
        return list;
    }

    private List<IslandUpgrade> loadUpgrades(Connection conn, UUID islandId) throws SQLException {
        String sql = "SELECT upgrade_key, upgrade_level, updated_at FROM sr_island_upgrades WHERE island_id = ?";
        List<IslandUpgrade> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, islandId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new IslandUpgrade(
                            islandId,
                            rs.getString("upgrade_key"),
                            rs.getInt("upgrade_level"),
                            rs.getTimestamp("updated_at").toInstant()
                    ));
                }
            }
        }
        return list;
    }
}
