package com.skyrealms.farming.mcmmo;

import com.gmail.nossr50.api.ExperienceAPI;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Bridge service exposing mcMMO's Farming (Herbalism) skill progression to SkyRealms Garden.
 * All other mcMMO skills and built-in abilities are suppressed by McMMOIntegrationListener.
 */
public final class McMMOFarmingService {

    public static final String SKILL_NAME = "HERBALISM";
    public static final String DISPLAY_NAME = "Farming";

    /**
     * Returns the player's current mcMMO Farming (Herbalism) level, or 0 if not yet loaded.
     */
    public int getFarmingLevel(@NotNull Player player) {
        try {
            return Math.max(0, ExperienceAPI.getLevel(player, PrimarySkillType.HERBALISM));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /**
     * Returns the player's current XP within the active mcMMO Farming level.
     */
    public int getFarmingXp(@NotNull Player player) {
        try {
            return Math.max(0, ExperienceAPI.getXP(player, SKILL_NAME));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /**
     * Returns total XP required to advance from the player's current Farming level to the next.
     */
    public int getFarmingXpToNextLevel(@NotNull Player player) {
        try {
            return Math.max(1, ExperienceAPI.getXPToNextLevel(player, SKILL_NAME));
        } catch (RuntimeException ignored) {
            return 1020;
        }
    }

    /**
     * Returns normalized progress [0.0, 1.0] toward the next mcMMO Farming level.
     */
    public double getProgressToNextLevel(@NotNull Player player) {
        int needed = getFarmingXpToNextLevel(player);
        if (needed <= 0) return 1.0;
        return Math.min(1.0, Math.max(0.0, (double) getFarmingXp(player) / needed));
    }

    /**
     * Awards raw mcMMO Farming (Herbalism) XP to a player (used for Garden abilities and Crop Mastery synergy).
     */
    public void addFarmingXp(@NotNull Player player, float amount) {
        if (amount <= 0f) return;
        try {
            ExperienceAPI.addRawXP(player, SKILL_NAME, amount, XPGainReason.PVE.name(), true);
        } catch (RuntimeException ignored) {
            // Player mcMMO profile not ready; safely skip
        }
    }
}

