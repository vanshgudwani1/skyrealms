package com.skyrealms.farming.service;

import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.farming.model.FarmingGeneratorTier;
import com.skyrealms.farming.model.GeneratorUpgrade;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance Hologram engine for SkyRealms Farming Generators.
 * Integrates directly with DecentHolograms (DHAPI) with native Paper TextDisplay fallback.
 */
public final class FarmingHologramService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmingHologramService.class);
    private static final String HOLO_PREFIX = "skyrealms_farm_";

    private final Plugin plugin;
    private final boolean decentHologramsPresent;

    // Fallback: Generator UUID -> TextDisplay
    private final Map<UUID, TextDisplay> fallbackDisplays = new ConcurrentHashMap<>();

    public FarmingHologramService(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.decentHologramsPresent = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        if (decentHologramsPresent) {
            LOGGER.info("DecentHolograms detected! Hooking into DHAPI for 3D animated crop holograms.");
        } else {
            LOGGER.info("DecentHolograms not present; using native Paper 1.21.1 TextDisplay fallback.");
        }
    }

    /**
     * Spawns or updates a generator's hologram.
     */
    public void createOrUpdateHologram(@NotNull FarmingGenerator generator, @NotNull String statusLine) {
        String holoName = HOLO_PREFIX + generator.generatorId();
        Location holoLoc = getHoloLocation(generator);

        OfflinePlayer owner = Bukkit.getOfflinePlayer(generator.ownerUuid());
        String ownerName = owner.getName() != null ? owner.getName() : "Unknown";

        if (decentHologramsPresent) {
            try {
                List<String> lines = buildDecentHologramLines(generator, ownerName, statusLine);
                Hologram holo = DHAPI.getHologram(holoName);
                if (holo == null) {
                    DHAPI.createHologram(holoName, holoLoc, lines);
                } else {
                    DHAPI.setHologramLines(holo, lines);
                }
                return;
            } catch (Throwable t) {
                LOGGER.warn("DecentHolograms error, falling back to native TextDisplay: {}", t.getMessage());
            }
        }

        // Native TextDisplay Fallback
        spawnNativeFallback(generator, holoLoc, ownerName, statusLine);
    }

    /**
     * Updates the status line of the hologram (e.g. countdown or ready).
     */
    public void updateStatus(@NotNull FarmingGenerator generator, @NotNull String statusLine) {
        createOrUpdateHologram(generator, statusLine);
    }

    /**
     * Permanently deletes the hologram for a generator.
     */
    public void removeHologram(@NotNull FarmingGenerator generator) {
        String holoName = HOLO_PREFIX + generator.generatorId();

        if (decentHologramsPresent) {
            try {
                if (DHAPI.getHologram(holoName) != null) {
                    DHAPI.removeHologram(holoName);
                }
            } catch (Throwable ignored) {}
        }

        TextDisplay display = fallbackDisplays.remove(generator.generatorId());
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    private List<String> buildDecentHologramLines(FarmingGenerator generator, String ownerName, String statusLine) {
        FarmingGeneratorTier tier = generator.tier();
        int speed = generator.getUpgradeLevel(GeneratorUpgrade.KEY_SPEED);
        int yield = generator.getUpgradeLevel(GeneratorUpgrade.KEY_YIELD);
        int mutation = generator.getUpgradeLevel(GeneratorUpgrade.KEY_MUTATION);

        String icon = switch (tier) {
            case TIER_1_WHEAT -> "#ICON: WHEAT";
            case TIER_2_ROOT -> "#ICON: CARROT";
            case TIER_3_FRUIT -> "#ICON: MELON_SLICE";
            case TIER_4_TROPICAL -> "#ICON: SUGAR_CANE";
            case TIER_5_MYSTIC -> "#ICON: NETHER_WART";
        };

        return List.of(
                icon,
                "<#gradient:#FFD700:#FFA500>✦ " + tier.displayName().toUpperCase() + " ✦</#gradient>",
                "&fOwner: &e" + ownerName + " &8• &a" + generator.cropNodes().size() + " Nodes",
                statusLine,
                "&7Speed: &eLvl " + speed + " ⚡ &8| &7Yield: &aLvl " + yield + " 🌿 &8| &7Mut: &dLvl " + mutation,
                "&7&o[ Right-Click Core for Menu ]"
        );
    }

    private void spawnNativeFallback(FarmingGenerator generator, Location loc, String ownerName, String statusLine) {
        TextDisplay existing = fallbackDisplays.get(generator.generatorId());
        if (existing != null && existing.isValid()) {
            existing.text(buildNativeComponent(generator, ownerName, statusLine));
            return;
        }

        if (loc.getWorld() == null) return;

        TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.text(buildNativeComponent(generator, ownerName, statusLine));
            td.setBillboard(Display.Billboard.CENTER);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(180, 20, 10, 30));
            td.setShadowed(true);
            td.addScoreboardTag("skyrealms_farming_hologram");
        });

        fallbackDisplays.put(generator.generatorId(), display);
    }

    private Component buildNativeComponent(FarmingGenerator generator, String ownerName, String statusLine) {
        FarmingGeneratorTier tier = generator.tier();
        int speed = generator.getUpgradeLevel(GeneratorUpgrade.KEY_SPEED);
        int yield = generator.getUpgradeLevel(GeneratorUpgrade.KEY_YIELD);

        return Component.text("⚡ " + tier.displayName() + " ⚡", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("Owner: " + ownerName, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(cleanColorCodes(statusLine), NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("Speed: Lvl " + speed + " | Yield: Lvl " + yield, NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("[ Right-Click Core for Menu ]", NamedTextColor.DARK_GRAY, TextDecoration.ITALIC));
    }

    private String cleanColorCodes(String input) {
        return input.replaceAll("&[0-9a-fk-or]", "").replaceAll("<[^>]*>", "");
    }

    private Location getHoloLocation(FarmingGenerator generator) {
        var world = Bukkit.getWorld(generator.worldName());
        return new Location(world, generator.coreX() + 0.5, generator.coreY() + 2.2, generator.coreZ() + 0.5);
    }
}
