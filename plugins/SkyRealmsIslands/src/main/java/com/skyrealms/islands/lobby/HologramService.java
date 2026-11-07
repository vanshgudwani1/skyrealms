package com.skyrealms.islands.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service managing modern, high-performance Paper 1.21.1 TextDisplay holograms
 * integrated with SkyRealms core plugins (Islands, Economy, Player).
 */
public final class HologramService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HologramService.class);
    private static final String HOLOGRAM_TAG = "skyrealms_hologram";

    private final Plugin plugin;
    private final List<TextDisplay> activeHolograms = new ArrayList<>();

    public HologramService(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Clears all existing SkyRealms holograms in the lobby world to prevent duplicates.
     */
    public void clearHolograms(World world) {
        if (world == null) return;
        for (Entity entity : world.getEntities()) {
            if (entity.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                entity.remove();
            }
        }
        activeHolograms.clear();
        LOGGER.info("Cleared previous SkyRealms holograms from world '{}'.", world.getName());
    }

    /**
     * Spawns all server aspect holograms at the designated lobby and castle locations.
     */
    public void spawnAllHolograms(World world) {
        if (world == null) return;
        clearHolograms(world);

        LOGGER.info("Spawning modern Celestial Holograms in lobby and castle...");

        // 1. Spawn Dais Welcome Hologram
        spawnHologram(world, 0.5, 73.2, 0.5, List.of(
                Component.text("✦ ꜱ ᴋ ʏ ʀ ᴇ ᴀ ʟ ᴍ ꜱ ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("Celestial Cherry SkyBlock Network", NamedTextColor.WHITE),
                Component.text("Create your floating realm & rule the skies", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Type ", NamedTextColor.YELLOW)
                        .append(Component.text("/is", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" or walk to the Gateway to play!", NamedTextColor.YELLOW))
        ));

        // 2. SkyBlock Portal Gateway Hologram
        spawnHologram(world, 13.0, 73.2, 0.5, List.of(
                Component.text("✦ SKYBLOCK GATEWAY ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("Step into the Void or Click below", NamedTextColor.WHITE),
                Component.text("✔ Create Island ", NamedTextColor.GREEN)
                        .append(Component.text("| ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("⚡ Teleport Home", NamedTextColor.YELLOW)),
                Component.empty(),
                Component.text("[ CLICK TO OPEN ISLAND MENU ]", NamedTextColor.AQUA, TextDecoration.BOLD)
        ));

        // 3. Castle Gatehouse & Citadel Entrance Hologram
        spawnHologram(world, 0.5, 80.5, 30.5, List.of(
                Component.text("✦ CITADEL OF THE CHERRY DRAGON ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("Royal Stronghold of SkyRealms", NamedTextColor.WHITE),
                Component.empty(),
                Component.text("West: ", NamedTextColor.GRAY)
                        .append(Component.text("Royal Treasury & Bank", NamedTextColor.GOLD)),
                Component.text("East: ", NamedTextColor.GRAY)
                        .append(Component.text("Arcane Upgrades Spire", NamedTextColor.LIGHT_PURPLE)),
                Component.text("South: ", NamedTextColor.GRAY)
                        .append(Component.text("Grand Citadel & Hall of Fame", NamedTextColor.YELLOW))
        ));

        // 4. Castle West Wing — Royal Treasury & Bank
        spawnHologram(world, -20.5, 79.8, 35.5, List.of(
                Component.text("✦ ROYAL TREASURY & BANK ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("SkyRealms Double-Entry Economy", NamedTextColor.WHITE),
                Component.text("Audited & Secured by PostgreSQL Engine", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Commands: ", NamedTextColor.YELLOW)
                        .append(Component.text("/balance", NamedTextColor.AQUA))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("/pay <player>", NamedTextColor.AQUA)),
                Component.text("Right-Click Banker to check balance", NamedTextColor.GREEN)
        ));

        // 5. Castle East Wing — Arcane Upgrades Spire
        spawnHologram(world, 20.5, 79.8, 35.5, List.of(
                Component.text("✦ ARCANE UPGRADES SPIRE ✦", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("Expand Island Bounds & Member Slots", NamedTextColor.WHITE),
                Component.text("Island Size: 100x100 up to 200x200 blocks", NamedTextColor.GRAY),
                Component.text("Co-op Capacity: Up to 12 Members", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("[ RIGHT-CLICK TO OPEN UPGRADES ]", NamedTextColor.AQUA, TextDecoration.BOLD)
        ));

        // 6. Castle Citadel — Grand Throne Room & Hall of Fame
        spawnHologram(world, 0.5, 84.8, 55.5, List.of(
                Component.text("✦ HALL OF FAME & LEADERBOARDS ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("The Realm of Champions", NamedTextColor.WHITE),
                Component.text("Top Island Levels & Richest Realms", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Type ", NamedTextColor.YELLOW)
                        .append(Component.text("/is top", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text(" to view live leaderboards!", NamedTextColor.YELLOW))
        ));

        // 7. Castle Armory & Forge
        spawnHologram(world, -45.5, 70.8, -25.5, List.of(
                Component.text("✦ ROYAL ARMORY & FORGE ✦", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Industrial Smelting & Equipment", NamedTextColor.WHITE),
                Component.text("Foundations for Future Mining & Machines", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("Craft, smelt, and gear up for the Void!", NamedTextColor.YELLOW)
        ));

        // 8. Rewards & Crates Pavilion
        spawnHologram(world, 0.5, 73.2, 13.5, List.of(
                Component.text("✦ CELESTIAL REWARDS VAULT ✦", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Daily Streaks & SkyBlock Perks", NamedTextColor.WHITE),
                Component.text("Claim your daily celestial gifts", NamedTextColor.GRAY),
                Component.empty(),
                Component.text("[ RIGHT-CLICK CHEST TO CLAIM ]", NamedTextColor.YELLOW, TextDecoration.BOLD)
        ));

        LOGGER.info("Spawned {} active TextDisplay holograms successfully.", activeHolograms.size());
    }

    /**
     * Spawns an individual multi-line TextDisplay hologram entity.
     */
    public TextDisplay spawnHologram(World world, double x, double y, double z, List<Component> lines) {
        Location location = new Location(world, x, y, z);
        TextDisplay display = (TextDisplay) world.spawnEntity(location, EntityType.TEXT_DISPLAY);

        Component composite = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            composite = composite.append(lines.get(i));
            if (i < lines.size() - 1) {
                composite = composite.append(Component.newline());
            }
        }

        display.text(composite);
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(140, 20, 10, 25)); // Sleek celestial cherry backdrop
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.addScoreboardTag(HOLOGRAM_TAG);
        display.setPersistent(true);

        activeHolograms.add(display);
        return display;
    }
}
