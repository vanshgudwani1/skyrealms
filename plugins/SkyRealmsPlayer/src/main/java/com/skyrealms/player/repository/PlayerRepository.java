package com.skyrealms.player.repository;

import com.skyrealms.player.model.PlayerProfile;
import com.skyrealms.player.model.PlayerSession;
import com.skyrealms.player.model.PlayerSettings;
import com.skyrealms.player.model.PlayerStats;
import com.skyrealms.platform.database.DatabaseService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative SQL repository for player profiles, settings, stats, and sessions.
 */
public final class PlayerRepository {

    private final DatabaseService databaseService;

    public PlayerRepository(@NotNull DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService cannot be null");
    }

    @NotNull
    public Optional<PlayerProfile> findById(@NotNull UUID uuid) throws SQLException {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        String sql = """
            SELECT p.id, p.username, p.first_joined, p.last_seen, p.last_ip, p.created_at, p.updated_at,
                   s.scoreboard_visible, s.chat_visible, s.private_messages_enabled, s.island_fly_enabled, s.updated_at AS s_updated,
                   st.playtime_seconds, st.joins_count, st.deaths, st.kills, st.updated_at AS st_updated
            FROM sr_players p
            JOIN sr_player_settings s ON p.id = s.player_id
            JOIN sr_player_stats st ON p.id = st.player_id
            WHERE p.id = ?
        """;

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapProfile(rs));
                }
            }
        }
        return Optional.empty();
    }

    @NotNull
    public PlayerProfile findOrCreate(@NotNull UUID uuid, @NotNull String username, @Nullable String ipAddress) throws SQLException {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        Objects.requireNonNull(username, "username cannot be null");

        return databaseService.executeTransaction(conn -> {
            // Check existing
            String selectSql = """
                SELECT p.id, p.username, p.first_joined, p.last_seen, p.last_ip, p.created_at, p.updated_at,
                       s.scoreboard_visible, s.chat_visible, s.private_messages_enabled, s.island_fly_enabled, s.updated_at AS s_updated,
                       st.playtime_seconds, st.joins_count, st.deaths, st.kills, st.updated_at AS st_updated
                FROM sr_players p
                JOIN sr_player_settings s ON p.id = s.player_id
                JOIN sr_player_stats st ON p.id = st.player_id
                WHERE p.id = ?
            """;

            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setObject(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        PlayerProfile existing = mapProfile(rs);
                        PlayerProfile updated = existing.withLogin(username, ipAddress);
                        saveInTransaction(conn, updated);
                        return updated;
                    }
                }
            }

            // Create new
            PlayerProfile newProfile = PlayerProfile.createNew(uuid, username, ipAddress);
            insertInTransaction(conn, newProfile);
            return newProfile;
        });
    }

    public void save(@NotNull PlayerProfile profile) throws SQLException {
        Objects.requireNonNull(profile, "profile cannot be null");
        databaseService.executeTransactionVoid(conn -> saveInTransaction(conn, profile));
    }

    public void saveSession(@NotNull PlayerSession session) throws SQLException {
        Objects.requireNonNull(session, "session cannot be null");
        String sql = """
            INSERT INTO sr_player_sessions (id, player_id, started_at, ended_at, duration_seconds, ip_address)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, session.sessionId());
            ps.setObject(2, session.playerId());
            ps.setTimestamp(3, Timestamp.from(session.startedAt()));
            ps.setTimestamp(4, session.endedAt() != null ? Timestamp.from(session.endedAt()) : null);
            ps.setLong(5, session.durationSeconds());
            ps.setString(6, session.ipAddress());
            ps.executeUpdate();
        }
    }

    private void insertInTransaction(Connection conn, PlayerProfile profile) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());

        String insertPlayer = """
            INSERT INTO sr_players (id, username, first_joined, last_seen, last_ip, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(insertPlayer)) {
            ps.setObject(1, profile.id());
            ps.setString(2, profile.username());
            ps.setTimestamp(3, Timestamp.from(profile.firstJoined()));
            ps.setTimestamp(4, Timestamp.from(profile.lastSeen()));
            ps.setString(5, profile.lastIp());
            ps.setTimestamp(6, Timestamp.from(profile.createdAt()));
            ps.setTimestamp(7, now);
            ps.executeUpdate();
        }

        String insertSettings = """
            INSERT INTO sr_player_settings (player_id, scoreboard_visible, chat_visible, private_messages_enabled, island_fly_enabled, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(insertSettings)) {
            ps.setObject(1, profile.id());
            ps.setBoolean(2, profile.settings().isScoreboardVisible());
            ps.setBoolean(3, profile.settings().isChatVisible());
            ps.setBoolean(4, profile.settings().isPrivateMessagesEnabled());
            ps.setBoolean(5, profile.settings().isIslandFlyEnabled());
            ps.setTimestamp(6, now);
            ps.executeUpdate();
        }

        String insertStats = """
            INSERT INTO sr_player_stats (player_id, playtime_seconds, joins_count, deaths, kills, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(insertStats)) {
            ps.setObject(1, profile.id());
            ps.setLong(2, profile.stats().playtimeSeconds());
            ps.setInt(3, profile.stats().joinsCount());
            ps.setInt(4, profile.stats().deaths());
            ps.setInt(5, profile.stats().kills());
            ps.setTimestamp(6, now);
            ps.executeUpdate();
        }
    }

    private void saveInTransaction(Connection conn, PlayerProfile profile) throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());

        String updatePlayer = """
            UPDATE sr_players
            SET username = ?, last_seen = ?, last_ip = ?, updated_at = ?
            WHERE id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(updatePlayer)) {
            ps.setString(1, profile.username());
            ps.setTimestamp(2, Timestamp.from(profile.lastSeen()));
            ps.setString(3, profile.lastIp());
            ps.setTimestamp(4, now);
            ps.setObject(5, profile.id());
            ps.executeUpdate();
        }

        String updateSettings = """
            UPDATE sr_player_settings
            SET scoreboard_visible = ?, chat_visible = ?, private_messages_enabled = ?, island_fly_enabled = ?, updated_at = ?
            WHERE player_id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(updateSettings)) {
            ps.setBoolean(1, profile.settings().isScoreboardVisible());
            ps.setBoolean(2, profile.settings().isChatVisible());
            ps.setBoolean(3, profile.settings().isPrivateMessagesEnabled());
            ps.setBoolean(4, profile.settings().isIslandFlyEnabled());
            ps.setTimestamp(5, now);
            ps.setObject(6, profile.id());
            ps.executeUpdate();
        }

        String updateStats = """
            UPDATE sr_player_stats
            SET playtime_seconds = ?, joins_count = ?, deaths = ?, kills = ?, updated_at = ?
            WHERE player_id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(updateStats)) {
            ps.setLong(1, profile.stats().playtimeSeconds());
            ps.setInt(2, profile.stats().joinsCount());
            ps.setInt(3, profile.stats().deaths());
            ps.setInt(4, profile.stats().kills());
            ps.setTimestamp(5, now);
            ps.setObject(6, profile.id());
            ps.executeUpdate();
        }
    }

    private PlayerProfile mapProfile(ResultSet rs) throws SQLException {
        UUID id = (UUID) rs.getObject("id");
        String username = rs.getString("username");
        Instant firstJoined = rs.getTimestamp("first_joined").toInstant();
        Instant lastSeen = rs.getTimestamp("last_seen").toInstant();
        String lastIp = rs.getString("last_ip");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

        PlayerSettings settings = new PlayerSettings(
                rs.getBoolean("scoreboard_visible"),
                rs.getBoolean("chat_visible"),
                rs.getBoolean("private_messages_enabled"),
                rs.getBoolean("island_fly_enabled"),
                rs.getTimestamp("s_updated").toInstant()
        );

        PlayerStats stats = new PlayerStats(
                rs.getLong("playtime_seconds"),
                rs.getInt("joins_count"),
                rs.getInt("deaths"),
                rs.getInt("kills"),
                rs.getTimestamp("st_updated").toInstant()
        );

        return new PlayerProfile(id, username, firstJoined, lastSeen, lastIp, settings, stats, createdAt, updatedAt);
    }
}
