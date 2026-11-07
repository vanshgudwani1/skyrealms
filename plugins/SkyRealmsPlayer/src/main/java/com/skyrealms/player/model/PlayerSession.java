package com.skyrealms.player.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks an active or completed player login session.
 */
public final class PlayerSession {

    private final UUID sessionId;
    private final UUID playerId;
    private final Instant startedAt;
    private final Instant endedAt;
    private final long durationSeconds;
    private final String ipAddress;

    public PlayerSession(
            @NotNull UUID sessionId,
            @NotNull UUID playerId,
            @NotNull Instant startedAt,
            @Nullable Instant endedAt,
            long durationSeconds,
            @Nullable String ipAddress
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId cannot be null");
        this.playerId = Objects.requireNonNull(playerId, "playerId cannot be null");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt cannot be null");
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.ipAddress = ipAddress;
    }

    @NotNull
    public static PlayerSession start(@NotNull UUID playerId, @Nullable String ipAddress) {
        return new PlayerSession(UUID.randomUUID(), playerId, Instant.now(), null, 0, ipAddress);
    }

    @NotNull
    public PlayerSession end() {
        Instant now = Instant.now();
        long duration = Duration.between(startedAt, now).getSeconds();
        return new PlayerSession(sessionId, playerId, startedAt, now, Math.max(0, duration), ipAddress);
    }

    @NotNull
    public UUID sessionId() {
        return sessionId;
    }

    @NotNull
    public UUID playerId() {
        return playerId;
    }

    @NotNull
    public Instant startedAt() {
        return startedAt;
    }

    @Nullable
    public Instant endedAt() {
        return endedAt;
    }

    public long durationSeconds() {
        return durationSeconds;
    }

    @Nullable
    public String ipAddress() {
        return ipAddress;
    }
}
