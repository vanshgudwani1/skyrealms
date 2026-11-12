package com.skyrealms.end.wilderness;

import com.skyrealms.platform.database.DatabaseService;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative PostgreSQL / H2 JDBC persistence for the End Bestiary (`sr_end_bestiary`).
 */
public final class BestiaryRepository {

    public record BestiaryEntry(
            @NotNull String mobId,
            int kills,
            int elitesKilled,
            long firstDiscoveredAt
    ) {}

    public record KillRecordOutcome(
            boolean newlyDiscovered,
            @NotNull BestiaryEntry entry
    ) {}

    private final DatabaseService databaseService;

    public BestiaryRepository(@NotNull DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService cannot be null");
    }

    @NotNull
    public KillRecordOutcome recordKill(@NotNull UUID playerUuid, @NotNull String mobId, boolean eliteOrBoss)
            throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(mobId, "mobId cannot be null");
        String uuidStr = playerUuid.toString();
        long now = System.currentTimeMillis();

        return databaseService.executeTransaction(conn -> {
            BestiaryEntry existing = selectOne(conn, uuidStr, mobId);
            if (existing == null) {
                int eliteInc = eliteOrBoss ? 1 : 0;
                String insertSql = """
                        INSERT INTO sr_end_bestiary (player_uuid, mob_id, kills, elites_killed, first_discovered_at)
                        VALUES (?, ?, 1, ?, ?)
                        """;
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, uuidStr);
                    ps.setString(2, mobId);
                    ps.setInt(3, eliteInc);
                    ps.setLong(4, now);
                    ps.executeUpdate();
                }
                return new KillRecordOutcome(true, new BestiaryEntry(mobId, 1, eliteInc, now));
            }

            int nextKills = existing.kills() + 1;
            int nextElites = existing.elitesKilled() + (eliteOrBoss ? 1 : 0);
            String updateSql = """
                    UPDATE sr_end_bestiary
                    SET kills = ?, elites_killed = ?
                    WHERE player_uuid = ? AND mob_id = ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, nextKills);
                ps.setInt(2, nextElites);
                ps.setString(3, uuidStr);
                ps.setString(4, mobId);
                ps.executeUpdate();
            }
            return new KillRecordOutcome(false, new BestiaryEntry(mobId, nextKills, nextElites, existing.firstDiscoveredAt()));
        });
    }

    @NotNull
    public Map<String, BestiaryEntry> loadAll(@NotNull UUID playerUuid) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        String uuidStr = playerUuid.toString();

        return databaseService.executeTransaction(conn -> {
            String sql = """
                    SELECT mob_id, kills, elites_killed, first_discovered_at
                    FROM sr_end_bestiary
                    WHERE player_uuid = ?
                    """;
            Map<String, BestiaryEntry> out = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("mob_id");
                        out.put(id, new BestiaryEntry(
                                id,
                                rs.getInt("kills"),
                                rs.getInt("elites_killed"),
                                rs.getLong("first_discovered_at")
                        ));
                    }
                }
            }
            return out;
        });
    }

    private static BestiaryEntry selectOne(java.sql.Connection conn, String uuidStr, String mobId) throws SQLException {
        String sql = """
                SELECT mob_id, kills, elites_killed, first_discovered_at
                FROM sr_end_bestiary
                WHERE player_uuid = ? AND mob_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidStr);
            ps.setString(2, mobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new BestiaryEntry(
                        rs.getString("mob_id"),
                        rs.getInt("kills"),
                        rs.getInt("elites_killed"),
                        rs.getLong("first_discovered_at")
                );
            }
        }
    }
}

