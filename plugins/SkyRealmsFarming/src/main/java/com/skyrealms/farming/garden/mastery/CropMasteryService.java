package com.skyrealms.farming.garden.mastery;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing individual player agricultural crop mastery progression,
 * milestone tracking, and bonus perk application.
 */
public interface CropMasteryService {

    /**
     * Gets a player's crop mastery progression for a specific crop.
     */
    @NotNull
    CropMastery getMastery(@NotNull UUID playerUuid, @NotNull CropType cropType);

    /**
     * Gets all 10 crop masteries for a player.
     */
    @NotNull
    List<CropMastery> getAllMasteries(@NotNull UUID playerUuid);

    /**
     * Awards crop mastery XP to a player for a verified harvest.
     * Evaluates level ups, celebration announcements, and unlocks perks.
     */
    void awardMasteryXp(@NotNull Player player, @NotNull CropType cropType, long xp);

    /**
     * Claims a milestone reward for an achieved level.
     */
    boolean claimMilestoneReward(@NotNull Player player, @NotNull CropType cropType, int milestoneLevel);

    /**
     * Opens the 54-slot main Crop Mastery GUI.
     */
    void openMasteryGui(@NotNull Player player);

    /**
     * Opens the detailed milestone progression GUI for a specific crop.
     */
    void openCropDetailGui(@NotNull Player player, @NotNull CropType cropType);
}
