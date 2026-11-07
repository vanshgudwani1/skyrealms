package com.skyrealms.islands.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Historical record of a player visiting an island.
 */
public record IslandVisit(
        @NotNull UUID visitId,
        @NotNull UUID islandId,
        @NotNull UUID visitorUuid,
        @NotNull Instant visitedAt,
        long visitDurationSeconds
) {
    public IslandVisit {
        Objects.requireNonNull(visitId, "visitId cannot be null");
        Objects.requireNonNull(islandId, "islandId cannot be null");
        Objects.requireNonNull(visitorUuid, "visitorUuid cannot be null");
        Objects.requireNonNull(visitedAt, "visitedAt cannot be null");
    }
}
