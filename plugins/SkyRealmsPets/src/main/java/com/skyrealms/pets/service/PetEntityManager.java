package com.skyrealms.pets.service;

import com.skyrealms.pets.model.PetDefinition;
import com.skyrealms.pets.model.PlayerPetData;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns, animates, and cleanly moves 3D ModelEngine Cubee companions.
 * Pets stay stationary in place (ignoring player head/camera rotation) and only
 * follow when the player actually moves out of follow range.
 */
public final class PetEntityManager {

    private static final Logger log = LoggerFactory.getLogger(PetEntityManager.class);
    private static final NamespacedKey PET_ENTITY_KEY = new NamespacedKey("skyrealms", "pet_owner_uuid");

    private static final double HOVER_HEIGHT_OFFSET = 1.15;
    private static final double START_FOLLOW_DISTANCE_SQ = 2.2 * 2.2;
    private static final double STOP_FOLLOW_DISTANCE = 1.5;
    private static final double TELEPORT_DISTANCE_SQ = 18.0 * 18.0;

    private static final class SpawnedPet {
        final Pig entity;
        final ModeledEntity modeledEntity;
        final ActiveModel activeModel;
        double anchorX;
        double anchorY;
        double anchorZ;
        float anchorYaw;
        boolean walking;

        SpawnedPet(
                Pig entity,
                ModeledEntity modeledEntity,
                ActiveModel activeModel,
                double anchorX,
                double anchorY,
                double anchorZ,
                float anchorYaw
        ) {
            this.entity = entity;
            this.modeledEntity = modeledEntity;
            this.activeModel = activeModel;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.anchorYaw = anchorYaw;
        }
    }

    private final Plugin plugin;
    private final Map<UUID, SpawnedPet> activeEntities = new ConcurrentHashMap<>();
    private BukkitTask followTask;
    private long tickCounter;

    public PetEntityManager(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public void start() {
        stop();
        cleanupStrayPetsInLoadedWorlds();
        this.followTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter++;
                tickAll();
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    public void stop() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        for (UUID ownerId : activeEntities.keySet().toArray(UUID[]::new)) {
            despawnPet(ownerId);
        }
        activeEntities.clear();
    }

    public boolean spawnPet(@NotNull Player owner, @NotNull PetDefinition def, @NotNull PlayerPetData data) {
        Objects.requireNonNull(owner, "owner cannot be null");
        Objects.requireNonNull(def, "def cannot be null");
        Objects.requireNonNull(data, "data cannot be null");

        despawnPet(owner.getUniqueId());

        ModelBlueprint blueprint = ModelEngineAPI.getBlueprint(def.blueprintId());
        if (blueprint == null) {
            log.warn("Missing ModelEngine blueprint '{}' for pet '{}'. Ensure /meg reload succeeded.",
                    def.blueprintId(), def.id());
            return false;
        }

        Location spawnLoc = computeInitialSpawnLocation(owner);
        World world = spawnLoc.getWorld();
        if (world == null) {
            return false;
        }

        Pig base = world.spawn(spawnLoc, Pig.class, pig -> {
            pig.setAI(false);
            pig.setGravity(false);
            pig.setSilent(true);
            pig.setInvulnerable(true);
            pig.setCollidable(false);
            pig.setPersistent(false);
            pig.setRemoveWhenFarAway(false);
            pig.setAdult();
            pig.getPersistentDataContainer().set(PET_ENTITY_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
            applyDisplayName(pig, def, data);
        });

        try {
            ModeledEntity modeled = ModelEngineAPI.createModeledEntity(base);
            modeled.setBaseEntityVisible(false);
            ActiveModel activeModel = ModelEngineAPI.createActiveModel(blueprint);
            activeModel.setScale(1.0);
            modeled.addModel(activeModel, true);

            SpawnedPet spawned = new SpawnedPet(
                    base,
                    modeled,
                    activeModel,
                    spawnLoc.getX(),
                    spawnLoc.getY(),
                    spawnLoc.getZ(),
                    spawnLoc.getYaw()
            );
            activeEntities.put(owner.getUniqueId(), spawned);
            playAnimationSafe(activeModel, "idle", true);
            return true;
        } catch (Throwable ex) {
            log.error("Failed attaching ModelEngine model '{}' to pet entity: {}", def.blueprintId(), ex.getMessage(), ex);
            base.remove();
            return false;
        }
    }

    public void updatePetName(@NotNull UUID ownerId, @NotNull PetDefinition def, @NotNull PlayerPetData data) {
        SpawnedPet spawned = activeEntities.get(ownerId);
        if (spawned != null && spawned.entity.isValid()) {
            applyDisplayName(spawned.entity, def, data);
        }
    }

    public void despawnPet(@NotNull UUID ownerId) {
        SpawnedPet removed = activeEntities.remove(ownerId);
        if (removed == null) {
            return;
        }
        try {
            if (!removed.modeledEntity.isDestroyed()) {
                removed.modeledEntity.destroy();
            }
        } catch (Throwable ignored) {
        }
        if (removed.entity.isValid() && !removed.entity.isDead()) {
            removed.entity.remove();
        }
    }

    public boolean hasSpawnedPet(@NotNull UUID ownerId) {
        SpawnedPet spawned = activeEntities.get(ownerId);
        return spawned != null && spawned.entity.isValid() && !spawned.entity.isDead();
    }

    private void tickAll() {
        double bob = Math.sin(tickCounter * 0.22) * 0.10;
        for (Map.Entry<UUID, SpawnedPet> entry : activeEntities.entrySet()) {
            UUID ownerId = entry.getKey();
            SpawnedPet spawned = entry.getValue();
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline() || !spawned.entity.isValid() || spawned.entity.isDead()) {
                despawnPet(ownerId);
                continue;
            }

            Location ownerLoc = owner.getLocation();
            World ownerWorld = ownerLoc.getWorld();
            if (ownerWorld == null) {
                continue;
            }

            if (!Objects.equals(spawned.entity.getWorld(), ownerWorld)) {
                Location reset = computeInitialSpawnLocation(owner);
                spawned.anchorX = reset.getX();
                spawned.anchorY = reset.getY();
                spawned.anchorZ = reset.getZ();
                spawned.anchorYaw = reset.getYaw();
                spawned.entity.teleport(reset);
                setAnimationState(spawned, false);
                continue;
            }

            double dxToOwner = ownerLoc.getX() - spawned.anchorX;
            double dyToOwner = (ownerLoc.getY() + HOVER_HEIGHT_OFFSET) - spawned.anchorY;
            double dzToOwner = ownerLoc.getZ() - spawned.anchorZ;
            double distSq = dxToOwner * dxToOwner + dyToOwner * dyToOwner + dzToOwner * dzToOwner;

            if (distSq > TELEPORT_DISTANCE_SQ) {
                Location reset = computeInitialSpawnLocation(owner);
                spawned.anchorX = reset.getX();
                spawned.anchorY = reset.getY();
                spawned.anchorZ = reset.getZ();
                spawned.anchorYaw = reset.getYaw();
                spawned.entity.teleport(reset);
                setAnimationState(spawned, false);
                continue;
            }

            double horizSq = dxToOwner * dxToOwner + dzToOwner * dzToOwner;
            double stopSq = STOP_FOLLOW_DISTANCE * STOP_FOLLOW_DISTANCE;
            boolean shouldFollow = spawned.walking ? (horizSq > stopSq || Math.abs(dyToOwner) > 1.2) : (distSq > START_FOLLOW_DISTANCE_SQ);

            if (shouldFollow) {
                double horizDist = Math.sqrt(horizSq);
                double targetX = spawned.anchorX;
                double targetZ = spawned.anchorZ;
                if (horizDist > 0.05) {
                    // Glide smoothly toward a point STOP_FOLLOW_DISTANCE away from the player along the line of approach
                    double desiredDist = Math.max(0.0, horizDist - STOP_FOLLOW_DISTANCE);
                    double ratio = Math.min(1.0, (desiredDist * 0.35) / horizDist);
                    targetX = spawned.anchorX + dxToOwner * ratio;
                    targetZ = spawned.anchorZ + dzToOwner * ratio;
                    spawned.anchorYaw = (float) Math.toDegrees(Math.atan2(-dxToOwner, dzToOwner));
                }
                double targetY = spawned.anchorY + dyToOwner * 0.35;

                spawned.anchorX = targetX;
                spawned.anchorY = targetY;
                spawned.anchorZ = targetZ;

                Location next = new Location(ownerWorld, targetX, targetY + bob, targetZ, spawned.anchorYaw, 0f);
                spawned.entity.teleport(next);
                setAnimationState(spawned, true);
            } else {
                // Stay in exact place (X, Z, yaw unchanged) when owner stands still or rotates camera
                Location hover = new Location(ownerWorld, spawned.anchorX, spawned.anchorY + bob, spawned.anchorZ, spawned.anchorYaw, 0f);
                spawned.entity.teleport(hover);
                setAnimationState(spawned, false);
            }
        }
    }

    private static void setAnimationState(SpawnedPet spawned, boolean walking) {
        if (spawned.walking == walking) {
            return;
        }
        spawned.walking = walking;
        playAnimationSafe(spawned.activeModel, walking ? "walk" : "idle", true);
    }

    private static void playAnimationSafe(ActiveModel model, String animationId, boolean force) {
        try {
            AnimationHandler handler = model.getAnimationHandler();
            if (handler != null) {
                handler.playAnimation(animationId, 0.2, 0.2, 1.0, force);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Location computeInitialSpawnLocation(Player owner) {
        Location eye = owner.getLocation();
        double yawRad = Math.toRadians(eye.getYaw());
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        Location spawn = eye.clone().add(rightX * 1.4, HOVER_HEIGHT_OFFSET, rightZ * 1.4);
        spawn.setYaw(eye.getYaw());
        spawn.setPitch(0f);
        return spawn;
    }

    private static void applyDisplayName(Pig entity, PetDefinition def, PlayerPetData data) {
        String nameText = (data.nickname() != null && !data.nickname().isBlank())
                ? data.nickname()
                : def.displayName();
        Component name = Component.text("[Lv." + data.level() + "] ", NamedTextColor.GRAY)
                .append(Component.text(nameText, def.color()));
        entity.customName(name);
        entity.setCustomNameVisible(true);
    }

    private static void cleanupStrayPetsInLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Pig.class)) {
                if (entity.getPersistentDataContainer().has(PET_ENTITY_KEY, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }
}
