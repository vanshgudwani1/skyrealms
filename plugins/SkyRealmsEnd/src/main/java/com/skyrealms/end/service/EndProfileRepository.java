package com.skyrealms.end.service;

import com.skyrealms.end.district.EndDistrict;
import com.skyrealms.platform.database.DatabaseService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative JDBC persistence for End Civilization player profiles (`sr_end_player_profile`).
 * Compatible with both PostgreSQL and H2 (PostgreSQL compatibility mode).
 */
public final class EndProfileRepository {

    public record EndPlayerProfile(
            @NotNull UUID playerUuid,
            int reputation,
            @NotNull Set<EndDistrict> discoveredDistricts,
            boolean dragonLicenseUnlocked,
            @Nullable Long dragonLicenseUnlockedAt,
            @NotNull String activeDiscipline,
            long updatedAt
    ) {}

    private final DatabaseService databaseService;

    public EndProfileRepository(@NotNull DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService cannot be null");
    }

    @NotNull
    public EndPlayerProfile loadOrCreate(@NotNull UUID playerUuid) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        String uuidStr = playerUuid.toString();

        return databaseService.executeTransaction(conn -> {
            EndPlayerProfile existing = selectByUuid(conn, uuidStr);
            if (existing != null) {
                return existing;
            }
            long now = System.currentTimeMillis();
            String insertSql = """
                    INSERT INTO sr_end_player_profile
                    (player_uuid, reputation, discovered_districts, dragon_license_unlocked, dragon_license_unlocked_at, active_discipline, updated_at)
                    VALUES (?, 0, '', FALSE, NULL, 'MELEE', ?)
                    """;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, uuidStr);
                ps.setLong(2, now);
                ps.executeUpdate();
            }
            return new EndPlayerProfile(playerUuid, 0, Set.of(), false, null, "MELEE", now);
        });
    }

    /**
     * Marks a district as discovered for the player if not already recorded.
     *
     * @return true if this was a newly discovered district
     */
    public boolean markDistrictDiscovered(@NotNull UUID playerUuid, @NotNull EndDistrict district) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(district, "district cannot be null");
        String uuidStr = playerUuid.toString();

        return databaseService.executeTransaction(conn -> {
            EndPlayerProfile profile = selectByUuid(conn, uuidStr);
            long now = System.currentTimeMillis();
            Set<EndDistrict> updated = new LinkedHashSet<>();
            if (profile == null) {
                updated.add(district);
                String insertSql = """
                        INSERT INTO sr_end_player_profile
                        (player_uuid, reputation, discovered_districts, dragon_license_unlocked, dragon_license_unlocked_at, active_discipline, updated_at)
                        VALUES (?, 0, ?, FALSE, NULL, 'MELEE', ?)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, uuidStr);
                    ps.setString(2, encodeDistricts(updated));
                    ps.setLong(3, now);
                    ps.executeUpdate();
                }
                return true;
            }

            if (profile.discoveredDistricts().contains(district)) {
                return false;
            }
            updated.addAll(profile.discoveredDistricts());
            updated.add(district);

            String updateSql = "UPDATE sr_end_player_profile SET discovered_districts = ?, updated_at = ? WHERE player_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, encodeDistricts(updated));
                ps.setLong(2, now);
                ps.setString(3, uuidStr);
                ps.executeUpdate();
            }
            return true;
        });
    }

    /**
     * Atomically increments (or initializes) a player's End Village reputation score.
     *
     * @return the updated reputation balance
     */
    public int addReputation(@NotNull UUID playerUuid, int delta) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        String uuidStr = playerUuid.toString();

        return databaseService.executeTransaction(conn -> {
            EndPlayerProfile existing = selectByUuid(conn, uuidStr);
            long now = System.currentTimeMillis();
            if (existing == null) {
                int initialRep = Math.max(0, delta);
                String insertSql = """
                        INSERT INTO sr_end_player_profile
                        (player_uuid, reputation, discovered_districts, dragon_license_unlocked, dragon_license_unlocked_at, active_discipline, updated_at)
                        VALUES (?, ?, '', FALSE, NULL, 'MELEE', ?)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, uuidStr);
                    ps.setInt(2, initialRep);
                    ps.setLong(3, now);
                    ps.executeUpdate();
                }
                return initialRep;
            }
            int updatedRep = Math.max(0, existing.reputation() + delta);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE sr_end_player_profile SET reputation = ?, updated_at = ? WHERE player_uuid = ?"
            )) {
                ps.setInt(1, updatedRep);
                ps.setLong(2, now);
                ps.setString(3, uuidStr);
                ps.executeUpdate();
            }
            return updatedRep;
        });
    }

    @Nullable
    private EndPlayerProfile selectByUuid(@NotNull Connection conn, @NotNull String uuidStr) throws SQLException {
        String sql = """
                SELECT player_uuid, reputation, discovered_districts, dragon_license_unlocked, dragon_license_unlocked_at, active_discipline, updated_at
                FROM sr_end_player_profile
                WHERE player_uuid = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long rawUnlock = rs.getLong("dragon_license_unlocked_at");
                Long unlockedAt = rs.wasNull() ? null : rawUnlock;
                return new EndPlayerProfile(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getInt("reputation"),
                        decodeDistricts(rs.getString("discovered_districts")),
                        rs.getBoolean("dragon_license_unlocked"),
                        unlockedAt,
                        rs.getString("active_discipline"),
                        rs.getLong("updated_at")
                );
            }
        }
    }

    @NotNull
    private static String encodeDistricts(@NotNull Set<EndDistrict> districts) {
        StringBuilder sb = new StringBuilder();
        for (EndDistrict d : districts) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(d.id());
        }
        return sb.toString();
    }

    @NotNull
    private static Set<EndDistrict> decodeDistricts(@Nullable String csv) {
        Set<EndDistrict> out = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String token : csv.split(",")) {
            EndDistrict d = EndDistrict.fromId(token);
            if (d != null) {
                out.add(d);
            }
        }
        return Set.copyOf(out);
    }
}

