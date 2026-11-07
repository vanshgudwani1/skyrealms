package com.skyrealms.player.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable domain model representing a player's core identity, settings, and statistics.
 */
public final class PlayerProfile {

    private final UUID id;
    private final String username;
    private final Instant firstJoined;
    private final Instant lastSeen;
    private final String lastIp;
    private final PlayerSettings settings;
    private final PlayerStats stats;
    private final Instant createdAt;
    private final Instant updatedAt;

    public PlayerProfile(
            @NotNull UUID id,
            @NotNull String username,
            @NotNull Instant firstJoined,
            @NotNull Instant lastSeen,
            @Nullable String lastIp,
            @NotNull PlayerSettings settings,
            @NotNull PlayerStats stats,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.firstJoined = Objects.requireNonNull(firstJoined, "firstJoined cannot be null");
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen cannot be null");
        this.lastIp = lastIp;
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");
        this.stats = Objects.requireNonNull(stats, "stats cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    @NotNull
    public static PlayerProfile createNew(@NotNull UUID id, @NotNull String username, @Nullable String ipAddress) {
        Instant now = Instant.now();
        return new PlayerProfile(
                id,
                username,
                now,
                now,
                ipAddress,
                PlayerSettings.createDefault(),
                PlayerStats.createDefault(),
                now,
                now
        );
    }

    @NotNull
    public UUID id() {
        return id;
    }

    @NotNull
    public String username() {
        return username;
    }

    @NotNull
    public Instant firstJoined() {
        return firstJoined;
    }

    @NotNull
    public Instant lastSeen() {
        return lastSeen;
    }

    @Nullable
    public String lastIp() {
        return lastIp;
    }

    @NotNull
    public PlayerSettings settings() {
        return settings;
    }

    @NotNull
    public PlayerStats stats() {
        return stats;
    }

    @NotNull
    public Instant createdAt() {
        return createdAt;
    }

    @NotNull
    public Instant updatedAt() {
        return updatedAt;
    }

    public PlayerProfile withLogin(@NotNull String currentUsername, @Nullable String currentIp) {
        Instant now = Instant.now();
        return new PlayerProfile(
                id,
                currentUsername,
                firstJoined,
                now,
                currentIp,
                settings,
                stats.incrementJoins(),
                createdAt,
                now
        );
    }

    public PlayerProfile withUpdatedStats(@NotNull PlayerStats newStats) {
        return new PlayerProfile(
                id,
                username,
                firstJoined,
                Instant.now(),
                lastIp,
                settings,
                newStats,
                createdAt,
                Instant.now()
        );
    }

    public PlayerProfile withSettings(@NotNull PlayerSettings newSettings) {
        return new PlayerProfile(
                id,
                username,
                firstJoined,
                lastSeen,
                lastIp,
                newSettings,
                stats,
                createdAt,
                Instant.now()
        );
    }
}
