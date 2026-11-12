package com.skyrealms.end.quest;

import com.skyrealms.platform.database.DatabaseService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative PostgreSQL / H2 JDBC persistence for End Civilization quest states and
 * database-enforced idempotent reward claims (`sr_end_quest_progress`).
 */
public final class QuestProgressRepository {

    public enum QuestStatus {
        AVAILABLE,
        ACTIVE,
        COMPLETED,
        CLAIMED;

        public static QuestStatus fromString(@Nullable String raw) {
            if (raw == null || raw.isBlank()) {
                return AVAILABLE;
            }
            try {
                return valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return AVAILABLE;
            }
        }
    }

    public record QuestProgressRow(
            @NotNull String questId,
            @NotNull String cycleKey,
            @NotNull QuestStatus status,
            int progressValue,
            int targetValue,
            @Nullable Long completedAt,
            @Nullable Long claimedAt
    ) {
        public boolean isCompletedOrClaimed() {
            return status == QuestStatus.COMPLETED || status == QuestStatus.CLAIMED;
        }
    }

    private final DatabaseService databaseService;

    public QuestProgressRepository(@NotNull DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService cannot be null");
    }

    @NotNull
    public Map<String, QuestProgressRow> loadAll(@NotNull UUID playerUuid) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        String uuidStr = playerUuid.toString();

        return databaseService.executeTransaction(conn -> {
            String sql = """
                    SELECT quest_id, cycle_key, status, progress_value, target_value, completed_at, claimed_at
                    FROM sr_end_quest_progress
                    WHERE player_uuid = ?
                    """;
            Map<String, QuestProgressRow> out = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        QuestProgressRow row = mapRow(rs);
                        out.put(key(row.questId(), row.cycleKey()), row);
                    }
                }
            }
            return out;
        });
    }

    @NotNull
    public QuestProgressRow upsertProgress(
            @NotNull UUID playerUuid,
            @NotNull String questId,
            @NotNull String cycleKey,
            int progressValue,
            int targetValue,
            @NotNull QuestStatus desiredStatus,
            long nowMillis
    ) throws SQLException {
        String uuidStr = playerUuid.toString();
        int clampedProgress = Math.max(0, Math.min(targetValue, progressValue));
        QuestStatus finalStatus = desiredStatus == QuestStatus.CLAIMED
                ? QuestStatus.CLAIMED
                : (clampedProgress >= targetValue ? QuestStatus.COMPLETED : desiredStatus);
        Long completedAt = finalStatus == QuestStatus.COMPLETED || finalStatus == QuestStatus.CLAIMED ? nowMillis : null;

        return databaseService.executeTransaction(conn -> {
            QuestProgressRow existing = selectOne(conn, uuidStr, questId, cycleKey);
            if (existing == null) {
                insertRow(conn, uuidStr, questId, cycleKey, finalStatus, clampedProgress, targetValue, completedAt, null);
                return new QuestProgressRow(questId, cycleKey, finalStatus, clampedProgress, targetValue, completedAt, null);
            }
            if (existing.status() == QuestStatus.CLAIMED) {
                return existing;
            }
            int mergedProgress = Math.max(existing.progressValue(), clampedProgress);
            QuestStatus mergedStatus = existing.isCompletedOrClaimed() || mergedProgress >= targetValue
                    ? QuestStatus.COMPLETED
                    : finalStatus;
            Long mergedCompleted = existing.completedAt() != null
                    ? existing.completedAt()
                    : (mergedStatus == QuestStatus.COMPLETED ? nowMillis : null);

            String updateSql = """
                    UPDATE sr_end_quest_progress
                    SET status = ?, progress_value = ?, target_value = ?, completed_at = ?
                    WHERE player_uuid = ? AND quest_id = ? AND cycle_key = ? AND claimed_at IS NULL
                    """;
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, mergedStatus.name());
                ps.setInt(2, mergedProgress);
                ps.setInt(3, targetValue);
                if (mergedCompleted != null) {
                    ps.setLong(4, mergedCompleted);
                } else {
                    ps.setNull(4, java.sql.Types.BIGINT);
                }
                ps.setString(5, uuidStr);
                ps.setString(6, questId);
                ps.setString(7, cycleKey);
                ps.executeUpdate();
            }
            return new QuestProgressRow(questId, cycleKey, mergedStatus, mergedProgress, targetValue, mergedCompleted, null);
        });
    }

    /**
     * Atomically claims a completed quest ONLY if {@code claimed_at IS NULL} and progress has reached
     * {@code target_value}, guaranteeing strict idempotency under duplicate or concurrent requests.
     *
     * @return true if this call exclusively won the claim lock and transitioned the row to CLAIMED
     */
    public boolean tryClaimRewardAtomic(
            @NotNull UUID playerUuid,
            @NotNull String questId,
            @NotNull String cycleKey,
            long nowMillis
    ) throws SQLException {
        String uuidStr = playerUuid.toString();
        return databaseService.executeTransaction(conn -> {
            String sql = """
                    UPDATE sr_end_quest_progress
                    SET status = 'CLAIMED', claimed_at = ?
                    WHERE player_uuid = ? AND quest_id = ? AND cycle_key = ?
                      AND claimed_at IS NULL
                      AND progress_value >= target_value
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, nowMillis);
                ps.setString(2, uuidStr);
                ps.setString(3, questId);
                ps.setString(4, cycleKey);
                return ps.executeUpdate() == 1;
            }
        });
    }

    @NotNull
    public static String key(@NotNull String questId, @NotNull String cycleKey) {
        return questId.toLowerCase() + "::" + cycleKey;
    }

    private static @Nullable QuestProgressRow selectOne(Connection conn, String uuidStr, String questId, String cycleKey)
            throws SQLException {
        String sql = """
                SELECT quest_id, cycle_key, status, progress_value, target_value, completed_at, claimed_at
                FROM sr_end_quest_progress
                WHERE player_uuid = ? AND quest_id = ? AND cycle_key = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidStr);
            ps.setString(2, questId);
            ps.setString(3, cycleKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private static void insertRow(
            Connection conn,
            String uuidStr,
            String questId,
            String cycleKey,
            QuestStatus status,
            int progress,
            int target,
            @Nullable Long completedAt,
            @Nullable Long claimedAt
    ) throws SQLException {
        String sql = """
                INSERT INTO sr_end_quest_progress
                (player_uuid, quest_id, status, progress_value, target_value, cycle_key, completed_at, claimed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuidStr);
            ps.setString(2, questId);
            ps.setString(3, status.name());
            ps.setInt(4, progress);
            ps.setInt(5, target);
            ps.setString(6, cycleKey);
            if (completedAt != null) ps.setLong(7, completedAt); else ps.setNull(7, java.sql.Types.BIGINT);
            if (claimedAt != null) ps.setLong(8, claimedAt); else ps.setNull(8, java.sql.Types.BIGINT);
            ps.executeUpdate();
        }
    }

    private static QuestProgressRow mapRow(ResultSet rs) throws SQLException {
        long comp = rs.getLong("completed_at");
        Long compNullable = rs.wasNull() ? null : comp;
        long clm = rs.getLong("claimed_at");
        Long clmNullable = rs.wasNull() ? null : clm;
        return new QuestProgressRow(
                rs.getString("quest_id"),
                rs.getString("cycle_key"),
                QuestStatus.fromString(rs.getString("status")),
                rs.getInt("progress_value"),
                rs.getInt("target_value"),
                compNullable,
                clmNullable
        );
    }
}

