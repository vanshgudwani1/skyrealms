package com.skyrealms.farming.service;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks rhythmic player harvest combo streaks with musical note feedback and XP multipliers.
 */
public final class HarvestComboTracker {

    private record ComboEntry(int count, Instant lastHarvest) {}

    private final Map<UUID, ComboEntry> combos = new ConcurrentHashMap<>();

    /**
     * Records a harvest and returns the active combo multiplier.
     */
    public double registerHarvest(@NotNull Player player) {
        Instant now = Instant.now();
        ComboEntry current = combos.get(player.getUniqueId());

        int count = 1;
        if (current != null) {
            long millisDiff = Duration.between(current.lastHarvest(), now).toMillis();
            if (millisDiff <= 3500) {
                count = Math.min(10, current.count() + 1);
            }
        }

        combos.put(player.getUniqueId(), new ComboEntry(count, now));

        if (count > 1) {
            float pitch = 0.8f + (count * 0.1f);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.7f, pitch);

            double multiplier = 1.0 + (count * 0.2);
            Component bar = Component.text("🔥 COMBO x" + String.format("%.1f", multiplier) + " ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("(" + count + " Streak! +" + (int)((multiplier - 1.0) * 100) + "% XP)", NamedTextColor.YELLOW));
            player.sendActionBar(bar);
            return multiplier;
        }

        return 1.0;
    }
}
