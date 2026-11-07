package com.skyrealms.islands.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

/**
 * Root domain entity representing a SkyRealms floating island.
 */
public final class Island {

    private final UUID id;
    private final UUID ownerUuid;
    private final IslandLocation location;
    private int size;
    private long level;
    private String name;
    private IslandStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private IslandSettings settings;
    private final Map<UUID, IslandMember> members = new HashMap<>();
    private final Map<String, IslandUpgrade> upgrades = new HashMap<>();

    public Island(
            @NotNull UUID id,
            @NotNull UUID ownerUuid,
            @NotNull IslandLocation location,
            int size,
            long level,
            @Nullable String name,
            @NotNull IslandStatus status,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull IslandSettings settings,
            @NotNull Collection<IslandMember> members,
            @NotNull Collection<IslandUpgrade> upgrades
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid cannot be null");
        this.location = Objects.requireNonNull(location, "location cannot be null");
        this.size = size;
        this.level = level;
        this.name = name;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
        this.settings = Objects.requireNonNull(settings, "settings cannot be null");

        for (IslandMember m : members) {
            this.members.put(m.playerUuid(), m);
        }
        for (IslandUpgrade u : upgrades) {
            this.upgrades.put(u.upgradeKey(), u);
        }
    }

    @NotNull
    public UUID id() {
        return id;
    }

    @NotNull
    public UUID ownerUuid() {
        return ownerUuid;
    }

    @NotNull
    public IslandLocation location() {
        return location;
    }

    public int size() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
        this.updatedAt = Instant.now();
    }

    public long level() {
        return level;
    }

    public void setLevel(long level) {
        this.level = level;
        this.updatedAt = Instant.now();
    }

    @Nullable
    public String name() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    @NotNull
    public IslandStatus status() {
        return status;
    }

    public void setStatus(@NotNull IslandStatus status) {
        this.status = Objects.requireNonNull(status);
        this.updatedAt = Instant.now();
    }

    @NotNull
    public Instant createdAt() {
        return createdAt;
    }

    @NotNull
    public Instant updatedAt() {
        return updatedAt;
    }

    @NotNull
    public IslandSettings settings() {
        return settings;
    }

    public void setSettings(@NotNull IslandSettings settings) {
        this.settings = Objects.requireNonNull(settings);
        this.updatedAt = Instant.now();
    }

    @NotNull
    public Map<UUID, IslandMember> members() {
        return Collections.unmodifiableMap(members);
    }

    @NotNull
    public Map<String, IslandUpgrade> upgrades() {
        return Collections.unmodifiableMap(upgrades);
    }

    public void addMember(@NotNull IslandMember member) {
        members.put(member.playerUuid(), member);
        this.updatedAt = Instant.now();
    }

    public void removeMember(@NotNull UUID playerUuid) {
        members.remove(playerUuid);
        this.updatedAt = Instant.now();
    }

    public void addUpgrade(@NotNull IslandUpgrade upgrade) {
        upgrades.put(upgrade.upgradeKey(), upgrade);
        this.updatedAt = Instant.now();
    }

    @NotNull
    public IslandRole getRole(@NotNull UUID playerUuid) {
        if (ownerUuid.equals(playerUuid)) {
            return IslandRole.LEADER;
        }
        IslandMember member = members.get(playerUuid);
        if (member != null) {
            return member.role();
        }
        return IslandRole.VISITOR;
    }

    public boolean isMember(@NotNull UUID playerUuid) {
        return ownerUuid.equals(playerUuid) || members.containsKey(playerUuid);
    }

    /**
     * Determines whether a player has the required permission on this island.
     */
    public boolean hasPermission(@NotNull UUID playerUuid, @NotNull IslandPermission permission) {
        IslandRole role = getRole(playerUuid);

        // Leader has all permissions
        if (role == IslandRole.LEADER) {
            return true;
        }

        // Officer has most management, all building and interaction
        if (role == IslandRole.OFFICER) {
            return permission != IslandPermission.RESET_ISLAND;
        }

        // Member has building, containers, interactions, and entity damage
        if (role == IslandRole.MEMBER) {
            return switch (permission) {
                case BLOCK_BREAK, BLOCK_PLACE, CONTAINER_ACCESS, INTERACT, ENTITY_DAMAGE -> true;
                default -> false;
            };
        }

        // Visitor permissions evaluate against IslandSettings
        if (role == IslandRole.VISITOR) {
            return switch (permission) {
                case BLOCK_BREAK -> settings.visitorBlockBreak();
                case BLOCK_PLACE -> settings.visitorBlockPlace();
                case CONTAINER_ACCESS -> settings.visitorContainerAccess();
                case INTERACT -> true; // Basic interaction (chat/walk/view)
                default -> false;
            };
        }

        return false;
    }
}
