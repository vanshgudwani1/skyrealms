package com.skyrealms.end.npc;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.config.EndVillageConfig.AnchorPoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reusable End Civilization NPC runtime engine.
 * Spawns configurable vanilla-compatible NPCs in loaded chunks, tracks proximity & patrol waypoints,
 * and dispatches interactions to modular quest/shop/training/wilderness handlers.
 */
public final class EndNpcManager {

    public static final NamespacedKey NPC_ID_KEY = new NamespacedKey("skyrealms", "end_npc_id");
    public static final NamespacedKey NPC_LABEL_KEY = new NamespacedKey("skyrealms", "end_npc_label_for");

    public static final class NpcRuntimeHandle {
        private final EndNpcDefinition definition;
        private UUID bodyEntityId;
        private UUID labelEntityId;
        private int patrolIndex;
        private int animationPhase;

        NpcRuntimeHandle(@NotNull EndNpcDefinition definition) {
            this.definition = definition;
        }

        public EndNpcDefinition definition() { return definition; }
        public @Nullable UUID bodyEntityId() { return bodyEntityId; }
        public @Nullable UUID labelEntityId() { return labelEntityId; }
        public int patrolIndex() { return patrolIndex; }
        public void setPatrolIndex(int patrolIndex) { this.patrolIndex = patrolIndex; }
        public int animationPhase() { return animationPhase; }
        public void advancePhase() { this.animationPhase++; }
    }

    private final Plugin plugin;
    private final EndVillageConfig config;
    private final EndNpcRegistry registry;
    private final Logger logger;

    private final Map<String, NpcRuntimeHandle> handles = new ConcurrentHashMap<>();
    private final Map<NpcActionType, NpcInteractionHandler> actionHandlers = new EnumMap<>(NpcActionType.class);
    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();
    private @Nullable EndNpcAnimationScheduler animationScheduler;

    public EndNpcManager(
            @NotNull Plugin plugin,
            @NotNull EndVillageConfig config,
            @NotNull EndNpcRegistry registry,
            @NotNull Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");

        for (EndNpcDefinition def : registry.all()) {
            handles.put(def.id(), new NpcRuntimeHandle(def));
        }
    }

    public void setAnimationScheduler(@Nullable EndNpcAnimationScheduler animationScheduler) {
        this.animationScheduler = animationScheduler;
    }

    public boolean isConfiguredWorld(@Nullable String worldName) {
        return worldName != null && worldName.equalsIgnoreCase(config.worldName());
    }

    public void registerHandler(@NotNull NpcActionType type, @NotNull NpcInteractionHandler handler) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");
        actionHandlers.put(type, handler);
    }

    /**
     * Purges any orphaned End NPC or floating nameplate entities inside the configured world
     * and spawns every NPC whose chunk is currently loaded.
     */
    public int spawnAllLoaded() {
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            logger.warn("End NPC spawn skipped: world '{}' is not loaded yet.", config.worldName());
            return 0;
        }
        purgeStaleNpcEntities(world);

        int spawned = 0;
        for (EndNpcDefinition def : registry.all()) {
            int cx = ((int) Math.floor(def.anchor().x())) >> 4;
            int cz = ((int) Math.floor(def.anchor().z())) >> 4;
            if (world.isChunkLoaded(cx, cz)) {
                if (ensureSpawned(world, def)) {
                    spawned++;
                }
            }
        }
        logger.info("Spawned {}/{} End Village NPCs in currently loaded chunks.", spawned, registry.size());
        return spawned;
    }

    public void ensureNpcsInChunk(@NotNull Chunk chunk) {
        if (!chunk.getWorld().getName().equalsIgnoreCase(config.worldName())) {
            return;
        }
        int cx = chunk.getX();
        int cz = chunk.getZ();
        for (EndNpcDefinition def : registry.all()) {
            int ncx = ((int) Math.floor(def.anchor().x())) >> 4;
            int ncz = ((int) Math.floor(def.anchor().z())) >> 4;
            if (ncx == cx && ncz == cz) {
                ensureSpawned(chunk.getWorld(), def);
            }
        }
    }

    public boolean ensureSpawned(@NotNull World world, @NotNull EndNpcDefinition def) {
        NpcRuntimeHandle handle = handles.get(def.id());
        if (handle == null) {
            return false;
        }
        if (handle.bodyEntityId != null) {
            Entity existing = Bukkit.getEntity(handle.bodyEntityId);
            if (existing != null && existing.isValid()) {
                return true;
            }
        }

        AnchorPoint ap = def.anchor();
        Location bodyLoc = new Location(world, ap.x(), ap.y(), ap.z(), ap.yaw(), ap.pitch());
        ArmorStand stand = world.spawn(bodyLoc, ArmorStand.class, as -> {
            as.setPersistent(false);
            as.setGravity(false);
            as.setArms(true);
            as.setBasePlate(false);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setCanPickupItems(false);
            as.setCustomNameVisible(false);
            as.getPersistentDataContainer().set(NPC_ID_KEY, PersistentDataType.STRING, def.id());
            if (as.getEquipment() != null) {
                EndNpcEquipmentFactory.applyLoadout(as.getEquipment(), def);
            }
        });

        Location labelLoc = bodyLoc.clone().add(0, 2.25, 0);
        TextColor accent = TextColor.fromHexString(def.district().hexColor());
        if (accent == null) {
            accent = NamedTextColor.LIGHT_PURPLE;
        }
        final TextColor finalAccent = accent;
        TextDisplay label = world.spawn(labelLoc, TextDisplay.class, td -> {
            td.setPersistent(false);
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(130, 18, 10, 28));
            td.text(Component.text(def.displayName(), finalAccent, TextDecoration.BOLD)
                    .appendNewline()
                    .append(Component.text(def.titleTag(), NamedTextColor.GRAY)));
            td.getPersistentDataContainer().set(NPC_LABEL_KEY, PersistentDataType.STRING, def.id());
        });

        handle.bodyEntityId = stand.getUniqueId();
        handle.labelEntityId = label.getUniqueId();
        return true;
    }

    public void despawnAll() {
        for (NpcRuntimeHandle handle : handles.values()) {
            removeEntityIfPresent(handle.bodyEntityId);
            removeEntityIfPresent(handle.labelEntityId);
            handle.bodyEntityId = null;
            handle.labelEntityId = null;
        }
        World world = Bukkit.getWorld(config.worldName());
        if (world != null) {
            purgeStaleNpcEntities(world);
        }
    }

    @Nullable
    public String resolveNpcId(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = entity.getPersistentDataContainer().get(NPC_ID_KEY, PersistentDataType.STRING);
        if (id != null) {
            return id;
        }
        return entity.getPersistentDataContainer().get(NPC_LABEL_KEY, PersistentDataType.STRING);
    }

    @Nullable
    public ArmorStand getBodyEntity(@NotNull String npcId) {
        NpcRuntimeHandle handle = handles.get(npcId);
        if (handle == null || handle.bodyEntityId == null) {
            return null;
        }
        Entity ent = Bukkit.getEntity(handle.bodyEntityId);
        return ent instanceof ArmorStand stand && stand.isValid() ? stand : null;
    }

    @Nullable
    public TextDisplay getLabelEntity(@NotNull String npcId) {
        NpcRuntimeHandle handle = handles.get(npcId);
        if (handle == null || handle.labelEntityId == null) {
            return null;
        }
        Entity ent = Bukkit.getEntity(handle.labelEntityId);
        return ent instanceof TextDisplay td && td.isValid() ? td : null;
    }

    @NotNull
    public Collection<NpcRuntimeHandle> runtimeHandles() {
        return handles.values();
    }

    /**
     * Finds players currently within proximityRadius of the NPC anchor in the End Village world.
     */
    @NotNull
    public List<Player> findNearbyPlayers(@NotNull EndNpcDefinition def) {
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            return List.of();
        }
        double rSq = def.proximityRadius() * def.proximityRadius();
        List<Player> nearby = new ArrayList<>();
        AnchorPoint ap = def.anchor();
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            double dx = l.getX() - ap.x();
            double dy = l.getY() - ap.y();
            double dz = l.getZ() - ap.z();
            if (dx * dx + dy * dy + dz * dz <= rSq) {
                nearby.add(p);
            }
        }
        return nearby;
    }

    public void handleInteraction(@NotNull Player player, @NotNull String npcId) {
        EndNpcDefinition def = registry.get(npcId);
        if (def == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = clickCooldowns.get(player.getUniqueId());
        if (last != null && now - last < 350L) {
            return;
        }
        clickCooldowns.put(player.getUniqueId(), now);

        if (animationScheduler != null) {
            animationScheduler.triggerInteraction(def.id());
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.55f, 1.15f);
        sendDialogue(player, def);

        NpcInteractionHandler handler = actionHandlers.get(def.actionType());
        if (handler != null) {
            handler.handle(player, def);
        } else if (def.actionType() != NpcActionType.DIALOGUE) {
            player.sendMessage(
                    Component.text("  [" + def.actionType().name() + " -> " + def.actionTarget() + "] ", NamedTextColor.DARK_PURPLE)
                            .append(Component.text("(System hooks active as subsequent modules unlock.)", NamedTextColor.DARK_GRAY))
            );
        }
    }

    private void sendDialogue(@NotNull Player player, @NotNull EndNpcDefinition def) {
        TextColor accent = TextColor.fromHexString(def.district().hexColor());
        if (accent == null) {
            accent = NamedTextColor.LIGHT_PURPLE;
        }
        List<String> lines = def.dialogue();
        String line = lines.isEmpty()
                ? "Welcome to " + def.district().displayName() + ", traveler."
                : lines.get((int) ((System.currentTimeMillis() / 1000L) % lines.size()));

        player.sendMessage(
                Component.text("[" + def.displayName() + "] ", accent, TextDecoration.BOLD)
                        .append(Component.text(line, NamedTextColor.WHITE))
        );
    }

    private void purgeStaleNpcEntities(@NotNull World world) {
        for (Entity entity : world.getEntities()) {
            if (entity.getPersistentDataContainer().has(NPC_ID_KEY, PersistentDataType.STRING)
                    || entity.getPersistentDataContainer().has(NPC_LABEL_KEY, PersistentDataType.STRING)) {
                entity.remove();
            }
        }
    }

    private static void removeEntityIfPresent(@Nullable UUID entityId) {
        if (entityId == null) {
            return;
        }
        Entity ent = Bukkit.getEntity(entityId);
        if (ent != null) {
            ent.remove();
        }
    }
}

