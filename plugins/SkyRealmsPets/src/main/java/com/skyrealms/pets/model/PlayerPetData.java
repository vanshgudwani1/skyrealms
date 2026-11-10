package com.skyrealms.pets.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Persistent state of an unlocked Cubee pet belonging to a player.
 */
public final class PlayerPetData {

    private final UUID playerUuid;
    private final String petId;
    private int level;
    private double xp;
    private @Nullable String nickname;
    private boolean active;
    private final long unlockedAt;

    public PlayerPetData(
            @NotNull UUID playerUuid,
            @NotNull String petId,
            int level,
            double xp,
            @Nullable String nickname,
            boolean active,
            long unlockedAt
    ) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.petId = Objects.requireNonNull(petId, "petId cannot be null");
        this.level = Math.max(1, Math.min(PetDefinition.MAX_LEVEL, level));
        this.xp = Math.max(0.0, xp);
        this.nickname = nickname;
        this.active = active;
        this.unlockedAt = unlockedAt;
    }

    @NotNull
    public UUID playerUuid() {
        return playerUuid;
    }

    @NotNull
    public String petId() {
        return petId;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(PetDefinition.MAX_LEVEL, level));
    }

    public double xp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = Math.max(0.0, xp);
    }

    @Nullable
    public String nickname() {
        return nickname;
    }

    public void setNickname(@Nullable String nickname) {
        this.nickname = nickname;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long unlockedAt() {
        return unlockedAt;
    }

    public static double xpRequiredForNextLevel(int currentLevel) {
        if (currentLevel >= PetDefinition.MAX_LEVEL) {
            return 0.0;
        }
        return 110.0 * currentLevel;
    }
}

