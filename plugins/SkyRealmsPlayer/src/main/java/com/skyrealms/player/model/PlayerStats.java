package com.skyrealms.player.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Player gameplay statistics and counters.
 */
public final class PlayerStats {

    private final long playtimeSeconds;
    private final int joinsCount;
    private final int deaths;
    private final int kills;
    private final Instant updatedAt;

    public PlayerStats(
            long playtimeSeconds,
            int joinsCount,
            int deaths,
            int kills,
            @NotNull Instant updatedAt
    ) {
        this.playtimeSeconds = playtimeSeconds;
        this.joinsCount = joinsCount;
        this.deaths = deaths;
        this.kills = kills;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    @NotNull
    public static PlayerStats createDefault() {
        return new PlayerStats(0, 1, 0, 0, Instant.now());
    }

    public long playtimeSeconds() {
        return playtimeSeconds;
    }

    public int joinsCount() {
        return joinsCount;
    }

    public int deaths() {
        return deaths;
    }

    public int kills() {
        return kills;
    }

    @NotNull
    public Instant updatedAt() {
        return updatedAt;
    }

    public PlayerStats addPlaytime(long additionalSeconds) {
        return new PlayerStats(this.playtimeSeconds + additionalSeconds, this.joinsCount, this.deaths, this.kills, Instant.now());
    }

    public PlayerStats incrementJoins() {
        return new PlayerStats(this.playtimeSeconds, this.joinsCount + 1, this.deaths, this.kills, Instant.now());
    }

    public PlayerStats incrementDeaths() {
        return new PlayerStats(this.playtimeSeconds, this.joinsCount, this.deaths + 1, this.kills, Instant.now());
    }

    public PlayerStats incrementKills() {
        return new PlayerStats(this.playtimeSeconds, this.joinsCount, this.deaths, this.kills + 1, Instant.now());
    }
}
