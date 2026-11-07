package com.skyrealms.islands.event;

import com.skyrealms.islands.model.IslandMember;
import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event: skyrealms.island.member.joined.v1
 */
public final class IslandMemberJoinedEvent implements SkyEvent {

    public static final String EVENT_NAME = "skyrealms.island.member.joined.v1";

    private final UUID islandId;
    private final IslandMember member;
    private final Instant timestamp;

    public IslandMemberJoinedEvent(@NotNull UUID islandId, @NotNull IslandMember member) {
        this.islandId = Objects.requireNonNull(islandId, "islandId cannot be null");
        this.member = Objects.requireNonNull(member, "member cannot be null");
        this.timestamp = Instant.now();
    }

    @Override
    @NotNull
    public String eventName() {
        return EVENT_NAME;
    }

    @Override
    @NotNull
    public Instant timestamp() {
        return timestamp;
    }

    @NotNull
    public UUID islandId() {
        return islandId;
    }

    @NotNull
    public IslandMember member() {
        return member;
    }
}
