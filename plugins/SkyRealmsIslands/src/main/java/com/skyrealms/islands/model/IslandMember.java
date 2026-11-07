package com.skyrealms.islands.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Member record for a player associated with an island.
 */
public record IslandMember(
        @NotNull UUID islandId,
        @NotNull UUID playerUuid,
        @NotNull IslandRole role,
        @NotNull Instant joinedAt
) {
    public IslandMember {
        Objects.requireNonNull(islandId, "islandId cannot be null");
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(joinedAt, "joinedAt cannot be null");
    }

    @NotNull
    public IslandMember withRole(@NotNull IslandRole newRole) {
        return new IslandMember(islandId, playerUuid, newRole, joinedAt);
    }
}
