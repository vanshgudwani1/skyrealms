package com.skyrealms.end.progression;

import com.skyrealms.end.progression.DisciplineProgressState.DisciplineEntry;
import com.skyrealms.end.progression.DisciplineProgressState.LeaderboardEntry;
import com.skyrealms.end.progression.DisciplineProgressState.PlayerDisciplineSnapshot;
import com.skyrealms.platform.database.DatabaseService;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative PostgreSQL / H2-compatible JDBC repository for End Civilization discipline progression
 * (`sr_end_discipline_progress` and `sr_end_player_profile.active_discipline`).
 */
public final class DisciplineProgressRepository {

    private final DatabaseService databaseService;

    public DisciplineProgressRepository(@NotNull DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService cannot be null");
    }

    /**
     * Loads or initializes the player's profile row and all three discipline progress rows atomically.
     */
    @NotNull
    public PlayerDisciplineSnapshot loadOrCreateAll(@NotNull UUID playerUuid) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        String uuidStr = playerUuid.toString();

        return databaseService.executeTransaction(conn -> {
            long now = System.currentTimeMillis();
            CombatDiscipline active = selectOrInsertProfileDiscipline(conn, uuidStr, now);
            Map<CombatDiscipline, DisciplineEntry> loaded = selectEntries(conn, uuidStr);

            for (CombatDiscipline discipline : CombatDiscipline.values()) {
                if (!loaded.containsKey(discipline)) {
                    DisciplineEntry initial = DisciplineEntry.initial(discipline, now);
                    insertEntry(conn, uuidStr, initial);
                    loaded.put(discipline, initial);
                }
            }
            return new PlayerDisciplineSnapshot(playerUuid, active, loaded);
        });
    }

    public void saveSnapshot(@NotNull PlayerDisciplineSnapshot snapshot) throws SQLException {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        String uuidStr = snapshot.playerUuid().toString();
        long now = System.currentTimeMillis();

        databaseService.executeTransactionVoid(conn -> {
            selectOrInsertProfileDiscipline(conn, uuidStr, now);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE sr_end_player_profile SET active_discipline = ?, updated_at = ? WHERE player_uuid = ?"
            )) {
                ps.setString(1, snapshot.activeDiscipline().name());
                ps.setLong(2, now);
                ps.setString(3, uuidStr);
                ps.executeUpdate();
            }

            for (CombatDiscipline discipline : CombatDiscipline.values()) {
                DisciplineEntry entry = snapshot.get(discipline);
                upsertEntry(conn, uuidStr, entry);
            }
        });
    }

    public void saveEntry(@NotNull UUID playerUuid, @NotNull DisciplineEntry entry) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(entry, "entry cannot be null");
        String uuidStr = playerUuid.toString();
        long now = System.currentTimeMillis();

        databaseService.executeTransactionVoid(conn -> {
            selectOrInsertProfileDiscipline(conn, uuidStr, now);
            upsertEntry(conn, uuidStr, entry);
        });
    }

    public void updateActiveDiscipline(@NotNull UUID playerUuid, @NotNull CombatDiscipline active) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(active, "active cannot be null");
        String uuidStr = playerUuid.toString();
        long now = System.currentTimeMillis();

        databaseService.executeTransactionVoid(conn -> {
            selectOrInsertProfileDiscipline(conn, uuidStr, now);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE sr_end_player_profile SET active_discipline = ?, updated_at = ? WHERE player_uuid = ?"
            )) {
                ps.setString(1, active.name());
                ps.setLong(2, now);
                ps.setString(3, uuidStr);
                ps.executeUpdate();
            }
        });
    }

    @NotNull
    public List<LeaderboardEntry> getTopByDiscipline(@NotNull CombatDiscipline discipline, int limit) throws SQLException {
        Objects.requireNonNull(discipline, "discipline cannot be null");
        int capped = Math.max(1, Math.min(25, limit));

        return databaseService.executeTransaction(conn -> {
            String sql = """
                    SELECT player_uuid, level, total_xp, mastery_completed
                    FROM sr_end_discipline_progress
                    WHERE discipline = ?
                    ORDER BY total_xp DESC, level DESC, updated_at ASC
                    LIMIT ?
                    """;
            List<LeaderboardEntry> results = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, discipline.name());
                ps.setInt(2, capped);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new LeaderboardEntry(
                                UUID.fromString(rs.getString("player_uuid")),
                                discipline,
                                rs.getInt("level"),
                                rs.getLong("total_xp"),
                                rs.getBoolean("mastery_completed")
                        ));
                    }
                }
            }
            return results;
        });
    }

    private static CombatDiscipline selectOrInsertProfileDiscipline(
            Connection conn,
            String uuidStr,
            long now
    ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT active_discipline FROM sr_end_player_profile WHERE player_uuid = ?"
        )) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return CombatDiscipline.fromString(rs.getString("active_discipline"), CombatDiscipline.MELEE);
                }
            }
        }
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
        return CombatDiscipline.MELEE;
    }

    private static Map<CombatDiscipline, DisciplineEntry> selectEntries(Connection conn, String uuidStr)
            throws SQLException {
        String sql = """
                SELECT discipline, level, total_xp, mastery_completed, training_best_score, updated_at
                FROM sr_end_discipline_progress
                WHERE player_uuid = ?
                """;
        Map<CombatDiscipline, DisciplineEntry> result = new EnumMap<>(CombatDiscipline.class);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CombatDiscipline disc = CombatDiscipline.fromString(rs.getString("discipline"), null);
                    if (disc == null) {
                        continue;
                    }
                    result.put(disc, new DisciplineEntry(
                            disc,
                            rs.getInt("level"),
                            rs.getLong("total_xp"),
                            rs.getBoolean("mastery_completed"),
                            rs.getInt("training_best_score"),
                            rs.getLong("updated_at")
                    ));
                }
            }
        }
        return result;
    }

    private static void upsertEntry(Connection conn, String uuidStr, DisciplineEntry entry) throws SQLException {
        String updateSql = """
                UPDATE sr_end_discipline_progress
                SET level = ?, total_xp = ?, mastery_completed = ?, training_best_score = ?, updated_at = ?
                WHERE player_uuid = ? AND discipline = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setInt(1, entry.level());
            ps.setLong(2, entry.totalXp());
            ps.setBoolean(3, entry.masteryCompleted());
            ps.setInt(4, entry.trainingBestScore());
            ps.setLong(5, entry.updatedAt());
            ps.setString(6, uuidStr);
            ps.setString(7, entry.discipline().name());
            if (ps.executeUpdate() > 0) {
                return;
            }
        }
        insertEntry(conn, uuidStr, entry);
    }

    private static void insertEntry(Connection conn, String uuidStr, DisciplineEntry entry) throws SQLException {
        String insertSql = """
                INSERT INTO sr_end_discipline_progress
                (player_uuid, discipline, level, total_xp, mastery_completed, training_best_score, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, uuidStr);
            ps.setString(2, entry.discipline().name());
            ps.setInt(3, entry.level());
            ps.setLong(4, entry.totalXp());
            ps.setBoolean(5, entry.masteryCompleted());
            ps.setInt(6, entry.trainingBestScore());
            ps.setLong(7, entry.updatedAt());
            ps.executeUpdate();
        }
    }
}

