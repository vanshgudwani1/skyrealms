package com.skyrealms.player.service;

import com.skyrealms.player.model.PlayerProfile;
import com.skyrealms.player.model.PlayerSession;
import com.skyrealms.player.model.PlayerSettings;
import com.skyrealms.player.model.PlayerStats;
import com.skyrealms.player.repository.PlayerRepository;
import com.skyrealms.platform.api.event.EventBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Authoritative service managing in-memory player state and asynchronous persistence.
 */
public final class PlayerService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerRepository repository;
    private final EventBus eventBus;
    private final ExecutorService executor;

    private final Map<UUID, PlayerProfile> profileCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();

    public PlayerService(@NotNull PlayerRepository repository, @NotNull EventBus eventBus) {
        this(repository, eventBus, Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "SkyRealms-Player-Worker");
                    t.setDaemon(true);
                    return t;
                }
        ));
    }

    public PlayerService(
            @NotNull PlayerRepository repository,
            @NotNull EventBus eventBus,
            @NotNull ExecutorService executor
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
    }

    @NotNull
    public CompletableFuture<PlayerProfile> loadOrCreateAsync(
            @NotNull UUID uuid,
            @NotNull String username,
            @Nullable String ipAddress
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repository.findOrCreate(uuid, username, ipAddress);
            } catch (SQLException e) {
                log.error("Failed to load or create profile for player '{}' ({}): {}", username, uuid, e.getMessage(), e);
                throw new CompletionException(e);
            }
        }, executor);
    }

    public void activate(@NotNull UUID uuid, @NotNull PlayerProfile profile, @Nullable String ipAddress) {
        profileCache.put(uuid, profile);
        activeSessions.put(uuid, PlayerSession.start(uuid, ipAddress));
        log.debug("Activated session and cached profile for {}", profile.username());
    }

    @NotNull
    public CompletableFuture<Void> deactivateAsync(@NotNull UUID uuid) {
        PlayerProfile profile = profileCache.remove(uuid);
        PlayerSession session = activeSessions.remove(uuid);

        if (profile == null && session == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                if (session != null) {
                    PlayerSession endedSession = session.end();
                    repository.saveSession(endedSession);

                    if (profile != null) {
                        PlayerStats updatedStats = profile.stats().addPlaytime(endedSession.durationSeconds());
                        PlayerProfile finalProfile = profile.withUpdatedStats(updatedStats);
                        repository.save(finalProfile);
                    }
                } else if (profile != null) {
                    repository.save(profile);
                }
            } catch (SQLException e) {
                log.error("Error saving player session and profile on disconnect for {}: {}", uuid, e.getMessage(), e);
            }
        }, executor);
    }

    @NotNull
    public Optional<PlayerProfile> getCached(@NotNull UUID uuid) {
        return Optional.ofNullable(profileCache.get(uuid));
    }

    public void updateStats(@NotNull UUID uuid, @NotNull Function<PlayerStats, PlayerStats> modifier) {
        profileCache.computeIfPresent(uuid, (id, profile) -> {
            PlayerStats newStats = modifier.apply(profile.stats());
            return profile.withUpdatedStats(newStats);
        });
    }

    public void updateSettings(@NotNull UUID uuid, @NotNull Function<PlayerSettings, PlayerSettings> modifier) {
        profileCache.computeIfPresent(uuid, (id, profile) -> {
            PlayerSettings newSettings = modifier.apply(profile.settings());
            return profile.withSettings(newSettings);
        });
    }

    @NotNull
    public CompletableFuture<Void> saveProfileAsync(@NotNull PlayerProfile profile) {
        return CompletableFuture.runAsync(() -> {
            try {
                repository.save(profile);
            } catch (SQLException e) {
                log.error("Failed to asynchronously save profile for {}: {}", profile.username(), e.getMessage(), e);
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public void close() {
        log.info("Flushing active player sessions and profiles to database...");
        for (UUID uuid : activeSessions.keySet()) {
            try {
                deactivateAsync(uuid).get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Error during shutdown flush for {}: {}", uuid, e.getMessage());
            }
        }
        profileCache.clear();
        activeSessions.clear();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
