package com.skyrealms.end.config;

import com.skyrealms.end.district.EndDistrict;
import com.skyrealms.end.npc.AnimationProfile;
import com.skyrealms.end.npc.EndNpcDefinition;
import com.skyrealms.end.npc.NpcActionType;
import com.skyrealms.end.npc.NpcRole;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration snapshot for the SkyRealms End Village world, safe-zone, districts,
 * outdoor training courtyards, and all configurable NPC definitions.
 */
public record EndVillageConfig(
        @NotNull String worldName,
        int offsetX,
        int offsetY,
        int offsetZ,
        @NotNull AnchorPoint spawnPoint,
        double voidRescueY,
        @NotNull SafeZoneConfig safeZone,
        @NotNull Map<EndDistrict, DistrictSpec> districts,
        @NotNull Map<String, TrainingArenaSpec> trainingArenas,
        @NotNull Map<String, EndNpcDefinition> npcs
) {

    public record AnchorPoint(double x, double y, double z, float yaw, float pitch) {}

    public record Box3D(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX + 1.0
                    && y >= minY && y <= maxY + 1.0
                    && z >= minZ && z <= maxZ + 1.0;
        }
    }

    public record Ellipse2D(double centerX, double centerZ, double radiusX, double radiusZ) {
        public boolean contains(double x, double z) {
            double dx = (x - centerX) / radiusX;
            double dz = (z - centerZ) / radiusZ;
            return (dx * dx) + (dz * dz) <= 1.0;
        }
    }

    public record SafeZoneConfig(
            boolean protectBlocks,
            boolean preventPvp,
            boolean preventHostileSpawns,
            boolean preventExplosions,
            @NotNull Ellipse2D ellipse,
            @NotNull Box3D causewayBox,
            @NotNull Box3D voidCryptBox
    ) {}

    public record DistrictSpec(
            @NotNull EndDistrict district,
            @NotNull String displayName,
            @NotNull String subtitle,
            @NotNull String hexColor,
            @NotNull AnchorPoint spawn
    ) {}

    public record TrainingArenaSpec(
            @NotNull String id,
            @NotNull EndDistrict district,
            @NotNull AnchorPoint center,
            @NotNull Box3D bounds
    ) {}

    @NotNull
    public static EndVillageConfig load(@NotNull FileConfiguration cfg) {
        Objects.requireNonNull(cfg, "cfg cannot be null");

        String worldName = cfg.getString("world.name", "fullya_end");
        int offsetX = cfg.getInt("world.offset.x", 145);
        int offsetY = cfg.getInt("world.offset.y", 40);
        int offsetZ = cfg.getInt("world.offset.z", -100);
        AnchorPoint spawnPoint = readAnchor(cfg.getConfigurationSection("world.spawn"),
                new AnchorPoint(251.5, 99.0, -3.5, 0f, 0f));
        double voidRescueY = cfg.getDouble("world.void-rescue-y", 30.0);

        SafeZoneConfig safeZone = new SafeZoneConfig(
                cfg.getBoolean("safe-zone.protect-blocks", true),
                cfg.getBoolean("safe-zone.prevent-pvp", true),
                cfg.getBoolean("safe-zone.prevent-hostile-spawns", true),
                cfg.getBoolean("safe-zone.prevent-explosions", true),
                new Ellipse2D(
                        cfg.getDouble("safe-zone.ellipse.center-x", 249.0),
                        cfg.getDouble("safe-zone.ellipse.center-z", 0.0),
                        cfg.getDouble("safe-zone.ellipse.radius-x", 105.0),
                        cfg.getDouble("safe-zone.ellipse.radius-z", 98.0)
                ),
                readBox(cfg.getConfigurationSection("safe-zone.causeway-box"), new Box3D(245, 272, 40, 120, 60, 98)),
                readBox(cfg.getConfigurationSection("safe-zone.void-crypt-box"), new Box3D(185, 238, 40, 72, 20, 82))
        );

        Map<EndDistrict, DistrictSpec> districts = new EnumMap<>(EndDistrict.class);
        ConfigurationSection distSec = cfg.getConfigurationSection("districts");
        for (EndDistrict d : EndDistrict.values()) {
            ConfigurationSection sub = distSec != null ? distSec.getConfigurationSection(d.id()) : null;
            String dName = sub != null ? sub.getString("display-name", d.displayName()) : d.displayName();
            String subTitle = sub != null ? sub.getString("subtitle", d.subtitle()) : d.subtitle();
            String hex = sub != null ? sub.getString("color", d.hexColor()) : d.hexColor();
            AnchorPoint dSpawn = readAnchor(sub != null ? sub.getConfigurationSection("spawn") : null, spawnPoint);
            districts.put(d, new DistrictSpec(d, dName, subTitle, hex, dSpawn));
        }

        Map<String, TrainingArenaSpec> arenas = new LinkedHashMap<>();
        ConfigurationSection arenaSec = cfg.getConfigurationSection("training-arenas");
        if (arenaSec != null) {
            for (String key : arenaSec.getKeys(false)) {
                ConfigurationSection sub = arenaSec.getConfigurationSection(key);
                if (sub == null) {
                    continue;
                }
                EndDistrict d = EndDistrict.fromId(sub.getString("district", "central_plaza"));
                if (d == null) {
                    d = EndDistrict.CENTRAL_PLAZA;
                }
                AnchorPoint center = readAnchor(sub.getConfigurationSection("center"), spawnPoint);
                Box3D bounds = readBox(sub.getConfigurationSection("bounds"), new Box3D(240, 260, 90, 110, -10, 10));
                arenas.put(key.toLowerCase(Locale.ROOT), new TrainingArenaSpec(key.toLowerCase(Locale.ROOT), d, center, bounds));
            }
        }

        Map<String, EndNpcDefinition> npcs = new LinkedHashMap<>();
        ConfigurationSection npcSec = cfg.getConfigurationSection("npcs");
        if (npcSec != null) {
            for (String key : npcSec.getKeys(false)) {
                ConfigurationSection sub = npcSec.getConfigurationSection(key);
                if (sub == null) {
                    continue;
                }
                String id = key.toLowerCase(Locale.ROOT);
                EndDistrict d = EndDistrict.fromId(sub.getString("district", "central_plaza"));
                if (d == null) {
                    d = EndDistrict.CENTRAL_PLAZA;
                }
                String name = sub.getString("name", key);
                String title = sub.getString("title", d.displayName());
                NpcRole role = NpcRole.fromString(sub.getString("role", "MERCHANT"));
                AnimationProfile anim = AnimationProfile.fromString(sub.getString("anim", "IDLE"));
                NpcActionType actionType = NpcActionType.fromString(sub.getString("action", "DIALOGUE"));
                String actionTarget = sub.getString("action-target", id);
                AnchorPoint anchor = readAnchor(sub, spawnPoint);
                List<AnchorPoint> patrol = readPatrolRoute(sub, anchor);
                List<String> dialogue = List.copyOf(sub.getStringList("dialogue"));
                String modelId = sub.getString("model-id");
                double prox = sub.getDouble("proximity-radius", 10.0);

                npcs.put(id, new EndNpcDefinition(
                        id, name, title, d, role, anim, actionType, actionTarget,
                        anchor, patrol, dialogue, modelId, prox
                ));
            }
        }

        return new EndVillageConfig(
                worldName,
                offsetX,
                offsetY,
                offsetZ,
                spawnPoint,
                voidRescueY,
                safeZone,
                Collections.unmodifiableMap(districts),
                Collections.unmodifiableMap(arenas),
                Collections.unmodifiableMap(npcs)
        );
    }

    @NotNull
    private static List<AnchorPoint> readPatrolRoute(@NotNull ConfigurationSection sub, @NotNull AnchorPoint base) {
        List<Map<?, ?>> rawList = sub.getMapList("patrol");
        if (rawList.isEmpty()) {
            return List.of();
        }
        List<AnchorPoint> pts = new ArrayList<>(rawList.size());
        for (Map<?, ?> entry : rawList) {
            double px = toDouble(entry.get("x"), base.x());
            double py = toDouble(entry.get("y"), base.y());
            double pz = toDouble(entry.get("z"), base.z());
            float yaw = (float) toDouble(entry.get("yaw"), base.yaw());
            pts.add(new AnchorPoint(px, py, pz, yaw, 0f));
        }
        return List.copyOf(pts);
    }

    private static double toDouble(@Nullable Object raw, double fallback) {
        return raw instanceof Number num ? num.doubleValue() : fallback;
    }

    @NotNull
    private static AnchorPoint readAnchor(@Nullable ConfigurationSection sec, @NotNull AnchorPoint fallback) {
        if (sec == null) {
            return fallback;
        }
        return new AnchorPoint(
                sec.getDouble("x", fallback.x()),
                sec.getDouble("y", fallback.y()),
                sec.getDouble("z", fallback.z()),
                (float) sec.getDouble("yaw", fallback.yaw()),
                (float) sec.getDouble("pitch", fallback.pitch())
        );
    }

    @NotNull
    private static Box3D readBox(@Nullable ConfigurationSection sec, @NotNull Box3D fallback) {
        if (sec == null) {
            return fallback;
        }
        return new Box3D(
                sec.getInt("min-x", fallback.minX()),
                sec.getInt("max-x", fallback.maxX()),
                sec.getInt("min-y", fallback.minY()),
                sec.getInt("max-y", fallback.maxY()),
                sec.getInt("min-z", fallback.minZ()),
                sec.getInt("max-z", fallback.maxZ())
        );
    }
}
