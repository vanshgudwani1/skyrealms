package com.skyrealms.end.npc;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.config.EndVillageConfig.AnchorPoint;
import com.skyrealms.end.npc.EndNpcManager.NpcRuntimeHandle;
import com.skyrealms.end.npc.NpcPoseCalculator.PatrolStep;
import com.skyrealms.end.npc.NpcPoseCalculator.PoseFrame;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized, single-task animation & patrol scheduler for all End Civilization NPCs.
 * Activates only when players are within activation proximity in fullya_end, preserving 20 TPS.
 */
public final class EndNpcAnimationScheduler {

    private static final long TICK_INTERVAL = 4L; // 5 Hz smooth vanilla pose & particle cadence
    private static final double ACTIVATION_RADIUS_SQ = 26.0 * 26.0;
    private static final double INTERACT_LOOK_RADIUS_SQ = 8.0 * 8.0;
    private static final double PATROL_STEP_BLOCKS = 0.28;

    private final Plugin plugin;
    private final EndVillageConfig config;
    private final EndNpcManager npcManager;
    private final Map<String, Long> interactionUntilMs = new ConcurrentHashMap<>();

    private BukkitTask task;
    private int lastActiveCount;
    private long totalTicks;

    public EndNpcAnimationScheduler(
            @NotNull Plugin plugin,
            @NotNull EndVillageConfig config,
            @NotNull EndNpcManager npcManager
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.npcManager = Objects.requireNonNull(npcManager, "npcManager cannot be null");
    }

    public void start() {
        stop();
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void triggerInteraction(@NotNull String npcId) {
        interactionUntilMs.put(npcId, System.currentTimeMillis() + 1400L);
    }

    /**
     * Triggers an immediate celebratory End-energy flourish across all currently loaded village NPCs
     * (used when unlocking masteries or Dragon Qualification).
     */
    public void triggerVillageEvent(@NotNull Particle particle, int countPerNpc) {
        World world = Bukkit.getWorld(config.worldName());
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        int clampedCount = Math.min(8, Math.max(1, countPerNpc));
        for (NpcRuntimeHandle handle : npcManager.runtimeHandles()) {
            ArmorStand stand = npcManager.getBodyEntity(handle.definition().id());
            if (stand == null) {
                continue;
            }
            interactionUntilMs.put(handle.definition().id(), System.currentTimeMillis() + 2000L);
            world.spawnParticle(particle, stand.getLocation().add(0, 1.8, 0), clampedCount, 0.35, 0.45, 0.35, 0.04);
        }
    }

    public int lastActiveCount() {
        return lastActiveCount;
    }

    public long totalTicks() {
        return totalTicks;
    }

    private void tickAll() {
        totalTicks++;
        World world = Bukkit.getWorld(config.worldName());
        if (world == null) {
            lastActiveCount = 0;
            return;
        }
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) {
            lastActiveCount = 0;
            return;
        }

        List<Location> playerLocs = new ArrayList<>(players.size());
        for (Player p : players) {
            playerLocs.add(p.getLocation());
        }

        long now = System.currentTimeMillis();
        int active = 0;

        for (NpcRuntimeHandle handle : npcManager.runtimeHandles()) {
            EndNpcDefinition def = handle.definition();
            ArmorStand stand = npcManager.getBodyEntity(def.id());
            if (stand == null) {
                continue;
            }

            Location standLoc = stand.getLocation();
            Location nearest = null;
            double bestDistSq = Double.MAX_VALUE;
            for (Location pl : playerLocs) {
                double dx = pl.getX() - standLoc.getX();
                double dy = pl.getY() - standLoc.getY();
                double dz = pl.getZ() - standLoc.getZ();
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    nearest = pl;
                }
            }

            if (bestDistSq > ACTIVATION_RADIUS_SQ) {
                continue;
            }
            active++;

            handle.advancePhase();
            int phase = handle.animationPhase();
            boolean nearPlayer = bestDistSq <= (def.proximityRadius() * def.proximityRadius());
            boolean closeLook = bestDistSq <= INTERACT_LOOK_RADIUS_SQ;
            Long interactExpiry = interactionUntilMs.get(def.id());
            boolean interacting = interactExpiry != null && interactExpiry > now;

            // Optional guard patrol stepping when not locked in conversation
            if (def.hasPatrol() && !interacting && !closeLook && (phase % 2 == 0)) {
                List<AnchorPoint> route = def.patrolRoute();
                PatrolStep step = NpcPoseCalculator.stepPatrol(
                        route, handle.patrolIndex(),
                        standLoc.getX(), standLoc.getY(), standLoc.getZ(),
                        PATROL_STEP_BLOCKS
                );
                handle.setPatrolIndex(step.nextIndex());
                Location moved = new Location(world, step.x(), step.y(), step.z(), step.yaw(), 0f);
                stand.teleport(moved);
                TextDisplay label = npcManager.getLabelEntity(def.id());
                if (label != null) {
                    label.teleport(moved.clone().add(0, 2.25, 0));
                }
                standLoc = moved;
            } else if (closeLook && nearest != null) {
                float lookYaw = NpcPoseCalculator.computeLookYaw(
                        nearest.getX() - standLoc.getX(),
                        nearest.getZ() - standLoc.getZ()
                );
                stand.setRotation(lookYaw, 0f);
            } else if (!def.hasPatrol()) {
                stand.setRotation(def.anchor().yaw(), 0f);
            }

            PoseFrame pose = NpcPoseCalculator.computePose(def.animationProfile(), phase, nearPlayer, interacting);
            stand.setHeadPose(pose.head());
            stand.setRightArmPose(pose.rightArm());
            stand.setLeftArmPose(pose.leftArm());

            if (pose.emitAccentEffect() && nearPlayer) {
                playBoundedAccent(world, standLoc, def.animationProfile());
            }
        }

        lastActiveCount = active;
    }

    private static void playBoundedAccent(@NotNull World world, @NotNull Location loc, @NotNull AnimationProfile profile) {
        switch (profile) {
            case BLACKSMITH -> {
                world.spawnParticle(Particle.FLAME, loc.clone().add(0.4, 1.05, 0.35), 3, 0.12, 0.08, 0.12, 0.02);
                world.spawnParticle(Particle.SMOKE, loc.clone().add(0.4, 1.15, 0.35), 2, 0.08, 0.1, 0.08, 0.01);
                world.playSound(loc, Sound.BLOCK_ANVIL_PLACE, 0.22f, 1.45f);
            }
            case MAGE -> {
                world.spawnParticle(Particle.WITCH, loc.clone().add(0, 1.55, 0), 4, 0.28, 0.25, 0.28, 0.02);
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1.85, 0), 2, 0.18, 0.18, 0.18, 0.01);
            }
            case ARCHER -> {
                world.spawnParticle(Particle.CRIT, loc.clone().add(0, 1.45, 0), 3, 0.15, 0.1, 0.15, 0.04);
                world.playSound(loc, Sound.ENTITY_ARROW_SHOOT, 0.18f, 1.25f);
            }
            case WARRIOR -> {
                world.spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 1.25, 0), 1, 0.0, 0.0, 0.0, 0.0);
                world.playSound(loc, Sound.ITEM_SHIELD_BLOCK, 0.18f, 1.2f);
            }
            case GUARD -> world.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1.2, 0), 3, 0.22, 0.35, 0.22, 0.04);
            case QUEST -> world.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1.65, 0), 3, 0.25, 0.2, 0.25, 0.05);
            case MERCHANT -> world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1.75, 0), 2, 0.2, 0.15, 0.2, 0.02);
            case IDLE -> { /* Quiet idle pose */ }
        }
    }
}

