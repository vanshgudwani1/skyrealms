package com.skyrealms.islands.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages animated Paper 1.21.1 Display entities (TextDisplay banners, spinning ItemDisplay relics,
 * and clickable Interaction pedestals) in the Sakura Spawn hub without leaving stale persistent entities.
 */
public final class HologramService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HologramService.class);
    private static final String HOLOGRAM_TAG = "skyrealms_hologram";

    private final NamespacedKey stationCommandKey;
    private final NamespacedKey stationTitleKey;
    private final List<Display> trackedDisplays = new ArrayList<>();
    private final List<Entity> trackedAll = new ArrayList<>();
    private final List<AnimatedRelic> animatedRelics = new ArrayList<>();
    private @Nullable TextDisplay welcomeBanner;

    private record AnimatedRelic(ItemDisplay display, float baseScale, double phaseOffset) {}

    public HologramService(@NotNull Plugin plugin) {
        this.stationCommandKey = new NamespacedKey(plugin, "lobby_station_cmd");
        this.stationTitleKey = new NamespacedKey(plugin, "lobby_station_title");
    }

    @NotNull
    public NamespacedKey stationCommandKey() {
        return stationCommandKey;
    }

    @NotNull
    public NamespacedKey stationTitleKey() {
        return stationTitleKey;
    }

    /**
     * Removes all tracked and tagged SkyRealms lobby display/interaction entities in the target world.
     */
    public void clearHolograms(@Nullable World world) {
        for (Entity entity : trackedAll) {
            if (entity.isValid()) {
                entity.remove();
            }
        }
        if (world != null) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                    entity.remove();
                }
            }
        }
        trackedDisplays.clear();
        trackedAll.clear();
        animatedRelics.clear();
        welcomeBanner = null;
        if (world != null) {
            LOGGER.info("Cleared previous SkyRealms lobby visuals from world '{}'.", world.getName());
        }
    }

    /**
     * Spawns the animated Sakura core relic, main welcome banner, and all interactive pedestal stations.
     */
    public void spawnAllHolograms(@Nullable World world) {
        if (world == null) return;
        clearHolograms(world);

        // 1. Welcome Banner above South Promenade entry
        this.welcomeBanner = spawnTextDisplay(world, 0.5, 92.3, 7.5, buildBannerLines(0));

        // 2. Central Levitating Sakura Core Relic above (0.5, 89.0, 0.5)
        ItemDisplay centerCore = spawnItemDisplay(
                world, 0.5, 92.6, 0.5,
                Material.AMETHYST_CLUSTER, 1.35f, Color.fromRGB(255, 145, 210)
        );
        animatedRelics.add(new AnimatedRelic(centerCore, 1.35f, 0.0));

        // 3. All interactive dais & promenade pedestals
        for (int i = 0; i < LobbyStation.DEFAULT_STATIONS.size(); i++) {
            LobbyStation station = LobbyStation.DEFAULT_STATIONS.get(i);
            spawnStation(world, station, i * 0.85);
        }

        LOGGER.info("Spawned {} interactive Sakura Hub stations and {} animated displays.",
                LobbyStation.DEFAULT_STATIONS.size(), trackedDisplays.size());
    }

    /**
     * Advances client-interpolated rotation, levitation, and banner shimmer smoothly.
     */
    public void animateStep(double angleRad, int step) {
        for (AnimatedRelic relic : animatedRelics) {
            ItemDisplay display = relic.display();
            if (!display.isValid()) continue;

            float bobY = (float) (Math.sin(angleRad * 2.0 + relic.phaseOffset()) * 0.14);
            Transformation next = new Transformation(
                    new Vector3f(0.0f, bobY, 0.0f),
                    new Quaternionf().rotationY((float) (angleRad + relic.phaseOffset())),
                    new Vector3f(relic.baseScale(), relic.baseScale(), relic.baseScale()),
                    new Quaternionf()
            );
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(10);
            display.setTransformation(next);
        }

        if (welcomeBanner != null && welcomeBanner.isValid() && step % 4 == 0) {
            setLines(welcomeBanner, buildBannerLines((step / 4) % 3));
        }
    }

    private void spawnStation(@NotNull World world, @NotNull LobbyStation station, double phaseOffset) {
        ItemDisplay itemDisplay = spawnItemDisplay(
                world, station.x(), station.y() + 1.45, station.z(),
                station.icon(), 0.95f, station.glowColor()
        );
        animatedRelics.add(new AnimatedRelic(itemDisplay, 0.95f, phaseOffset));

        List<Component> lines = List.of(
                Component.text(station.title(), TextColor.color(station.glowColor().asRGB()), TextDecoration.BOLD),
                Component.text(station.subtitle(), NamedTextColor.GRAY),
                Component.text("[ CLICK: /" + station.command() + " ]", TextColor.fromHexString("#FFD166"), TextDecoration.BOLD)
        );
        spawnTextDisplay(world, station.x(), station.y() + 2.55, station.z(), lines);

        Interaction hitbox = (Interaction) world.spawnEntity(
                new Location(world, station.x(), station.y(), station.z()),
                EntityType.INTERACTION
        );
        hitbox.setInteractionWidth(1.4f);
        hitbox.setInteractionHeight(2.3f);
        hitbox.setResponsive(true);
        hitbox.setPersistent(false);
        hitbox.addScoreboardTag(HOLOGRAM_TAG);
        hitbox.getPersistentDataContainer().set(stationCommandKey, PersistentDataType.STRING, station.command());
        hitbox.getPersistentDataContainer().set(stationTitleKey, PersistentDataType.STRING, station.title());
        trackedAll.add(hitbox);
    }

    @NotNull
    private ItemDisplay spawnItemDisplay(
            @NotNull World world,
            double x,
            double y,
            double z,
            @NotNull Material material,
            float scale,
            @NotNull Color glowColor
    ) {
        ItemDisplay display = (ItemDisplay) world.spawnEntity(new Location(world, x, y, z), EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(material));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
        display.setBillboard(Display.Billboard.FIXED);
        display.setTransformation(new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        ));
        display.setInterpolationDuration(10);
        display.setInterpolationDelay(0);
        display.setGlowing(true);
        display.setGlowColorOverride(glowColor);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(1.2f);
        display.setPersistent(false);
        display.addScoreboardTag(HOLOGRAM_TAG);

        trackedDisplays.add(display);
        trackedAll.add(display);
        return display;
    }

    @NotNull
    public TextDisplay spawnTextDisplay(
            @NotNull World world,
            double x,
            double y,
            double z,
            @NotNull List<Component> lines
    ) {
        TextDisplay display = (TextDisplay) world.spawnEntity(new Location(world, x, y, z), EntityType.TEXT_DISPLAY);
        setLines(display, lines);
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(150, 22, 10, 28));
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(1.2f);
        display.setPersistent(false);
        display.addScoreboardTag(HOLOGRAM_TAG);

        trackedDisplays.add(display);
        trackedAll.add(display);
        return display;
    }

    private static void setLines(@NotNull TextDisplay display, @NotNull List<Component> lines) {
        Component composite = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            composite = composite.append(lines.get(i));
            if (i < lines.size() - 1) {
                composite = composite.append(Component.newline());
            }
        }
        display.text(composite);
    }

    @NotNull
    private static List<Component> buildBannerLines(int frame) {
        TextColor accent = switch (frame) {
            case 1 -> TextColor.fromHexString("#A8E6CF");
            case 2 -> TextColor.fromHexString("#FFD166");
            default -> TextColor.fromHexString("#FF9EBB");
        };
        return List.of(
                Component.text("✦ ꜱ ᴋ ʏ ʀ ᴇ ᴀ ʟ ᴍ ꜱ ✦", accent, TextDecoration.BOLD),
                Component.text("Celestial Sakura Sanctuary Hub", NamedTextColor.WHITE),
                Component.empty(),
                Component.text("Click glowing Pedestals around the Dais", NamedTextColor.GRAY),
                Component.text("Double-tap Space for Sakura Leap • ", TextColor.fromHexString("#E0AAFF"))
                        .append(Component.text("/menu", NamedTextColor.AQUA, TextDecoration.BOLD))
        );
    }
}
