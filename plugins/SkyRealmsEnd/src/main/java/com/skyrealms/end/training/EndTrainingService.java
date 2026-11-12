package com.skyrealms.end.training;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.config.EndVillageConfig.Box3D;
import com.skyrealms.end.config.EndVillageConfig.TrainingArenaSpec;
import com.skyrealms.end.listener.CombatProgressionListener;
import com.skyrealms.end.npc.NpcInteractionHandler;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.DisciplineProgressState.DisciplineEntry;
import com.skyrealms.end.progression.DisciplineProgressState.PlayerDisciplineSnapshot;
import com.skyrealms.end.progression.XpSource;
import com.skyrealms.end.quest.EndQuestService;
import com.skyrealms.end.quest.QuestObjectiveType;
import com.skyrealms.end.training.TrainingScoringEngine.HitScoreBreakdown;
import com.skyrealms.end.training.TrainingScoringEngine.TrainingCompletionSummary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Centralized runtime engine for safe, controlled outdoor End Village training activities
 * across Archery, Melee, and Mage courtyards.
 */
public final class EndTrainingService {

    public static final NamespacedKey TRAINING_TARGET_KEY = new NamespacedKey("skyrealms", "training_target");
    public static final NamespacedKey TRAINING_OWNER_KEY = new NamespacedKey("skyrealms", "training_owner");
    public static final NamespacedKey TRAINING_ELEMENT_KEY = new NamespacedKey("skyrealms", "training_element");
    public static final NamespacedKey TRAINING_SLOT_KEY = new NamespacedKey("skyrealms", "training_slot");

    private static final double MAGE_BOLT_COST = 8.0;
    private static final long TICK_PERIOD = 4L; // 5 Hz shared scheduler

    public static final class ActiveSession {
        final UUID ownerId;
        final TrainingChallengeType challenge;
        final TrainingArenaSpec arena;
        final long startedAt;
        final long endsAt;
        final List<UUID> spawnedEntities = new ArrayList<>();

        int ticksElapsed;
        int score;
        int hits;
        int misses;
        int streak;
        int maxStreak;
        int activeComboSlot;
        int currentWave = 1;
        boolean parryWindowOpen;
        long parryWindowExpiresAt;
        long lastSpellCastAt;

        ActiveSession(UUID ownerId, TrainingChallengeType challenge, TrainingArenaSpec arena, long now) {
            this.ownerId = ownerId;
            this.challenge = challenge;
            this.arena = arena;
            this.startedAt = now;
            this.endsAt = now + (challenge.durationSeconds() * 1000L);
        }

        public @NotNull CombatDiscipline discipline() {
            return challenge.discipline();
        }
    }

    private final Plugin plugin;
    private final EndVillageConfig config;
    private final CombatProgressionService progressionService;
    private final TrainingScoringEngine scoringEngine;
    private final Logger logger;

    private final Map<UUID, ActiveSession> sessions = new ConcurrentHashMap<>();
    private BukkitTask tickTask;
    private @Nullable EndQuestService questService;

    public EndTrainingService(
            @NotNull Plugin plugin,
            @NotNull EndVillageConfig config,
            @NotNull CombatProgressionService progressionService,
            @NotNull TrainingScoringEngine scoringEngine,
            @NotNull Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.progressionService = Objects.requireNonNull(progressionService, "progressionService cannot be null");
        this.scoringEngine = Objects.requireNonNull(scoringEngine, "scoringEngine cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    public void setQuestService(@Nullable EndQuestService questService) {
        this.questService = questService;
    }

    public void start() {
        stop();
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAllSessions, TICK_PERIOD, TICK_PERIOD);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (UUID ownerId : new ArrayList<>(sessions.keySet())) {
            endSession(ownerId, false, "Training closed during shutdown.");
        }
        sessions.clear();
    }

    public boolean hasActiveSession(@NotNull UUID playerUuid) {
        return sessions.containsKey(playerUuid);
    }

    @Nullable
    public ActiveSession activeSession(@NotNull UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    public boolean isConfiguredWorld(@Nullable String worldName) {
        return worldName != null && worldName.equalsIgnoreCase(config.worldName());
    }

    @NotNull
    public NpcInteractionHandler trainingNpcHandler() {
        return (player, npc) -> {
            try {
                PlayerDisciplineSnapshot snap = progressionService.getOrLoadSnapshot(player.getUniqueId());
                player.sendMessage(Component.text("━━━ Outdoor Courtyard Training (" + npc.displayName() + ") ━━━",
                        NamedTextColor.GOLD, TextDecoration.BOLD));
                for (TrainingChallengeType challenge : TrainingChallengeType.values()) {
                    if (!TrainingScoringEngine.matchesNpcTarget(challenge, npc.actionTarget())) {
                        continue;
                    }
                    DisciplineEntry entry = snap.get(challenge.discipline());
                    boolean unlocked = entry.level() >= challenge.minLevel();
                    TextColor accent = TextColor.fromHexString(challenge.discipline().accentHex());
                    String statusTag = unlocked ? "[START]" : "[Lv." + challenge.minLevel() + "]";
                    Component row = Component.text(" " + statusTag + " ", unlocked ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                            .append(Component.text(challenge.displayName(), accent, TextDecoration.BOLD))
                            .append(Component.text(" — " + challenge.description(), NamedTextColor.GRAY));
                    if (unlocked) {
                        row = row.clickEvent(ClickEvent.runCommand("/training start " + challenge.key()))
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        "Click to start " + challenge.displayName() + " (" + challenge.durationSeconds() + "s)",
                                        NamedTextColor.YELLOW)));
                    }
                    player.sendMessage(row);
                }
                player.sendMessage(Component.text("Click a drill above or use /training start <mode> inside the courtyard.",
                        NamedTextColor.DARK_GRAY));
            } catch (SQLException ex) {
                player.sendMessage(Component.text("Unable to load discipline stats right now.", NamedTextColor.RED));
            }
        };
    }

    public boolean startChallenge(@NotNull Player player, @NotNull TrainingChallengeType challenge) {
        UUID uuid = player.getUniqueId();
        TrainingArenaSpec arena = config.trainingArenas().get(challenge.arenaId());
        if (arena == null) {
            player.sendMessage(Component.text("Arena configuration missing for " + challenge.arenaId(), NamedTextColor.RED));
            return false;
        }
        World world = Bukkit.getWorld(config.worldName());
        if (world == null || !player.getWorld().equals(world)) {
            player.sendMessage(Component.text("You must be inside the End Village (" + config.worldName() + ") to train.", NamedTextColor.RED));
            return false;
        }

        // Verify player level meets trial minimum
        try {
            PlayerDisciplineSnapshot snap = progressionService.getOrLoadSnapshot(uuid);
            int level = snap.get(challenge.discipline()).level();
            if (level < challenge.minLevel()) {
                player.sendMessage(Component.text(
                        "Requires " + challenge.discipline().displayName() + " Level " + challenge.minLevel()
                                + " (currently Lv." + level + ").",
                        NamedTextColor.RED
                ));
                return false;
            }
        } catch (SQLException ex) {
            logger.warn("Error checking training level for {}", uuid, ex);
        }

        // Check courtyard proximity (must be within 24 blocks of the arena center)
        Location arenaCenter = new Location(world, arena.center().x(), arena.center().y(), arena.center().z());
        if (player.getLocation().distanceSquared(arenaCenter) > 24.0 * 24.0) {
            player.sendMessage(Component.text(
                    "Step closer to the " + arena.district().displayName() + " courtyard to begin " + challenge.displayName() + ".",
                    NamedTextColor.YELLOW
            ));
            return false;
        }

        if (sessions.containsKey(uuid)) {
            endSession(uuid, false, "Switched training challenge.");
        }

        progressionService.switchDiscipline(player, challenge.discipline(), false);

        long now = System.currentTimeMillis();
        ActiveSession session = new ActiveSession(uuid, challenge, arena, now);
        sessions.put(uuid, session);

        spawnInitialTargets(world, session);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.3f);
        player.sendMessage(Component.text(
                "✦ Started [" + challenge.displayName() + "] (" + challenge.durationSeconds() + "s) — " + challenge.description(),
                TextColor.fromHexString(challenge.discipline().accentHex()),
                TextDecoration.BOLD
        ));
        return true;
    }

    public void endSession(@NotNull UUID playerUuid, boolean awardXp, @Nullable String customReason) {
        ActiveSession session = sessions.remove(playerUuid);
        if (session == null) {
            return;
        }
        cleanupSessionEntities(session);

        Player player = Bukkit.getPlayer(playerUuid);
        if (!awardXp) {
            if (player != null && player.isOnline() && customReason != null) {
                player.sendMessage(Component.text("• Training ended: " + customReason, NamedTextColor.GRAY));
            }
            return;
        }

        TrainingCompletionSummary summary = scoringEngine.summarizeAndComputeXp(
                playerUuid,
                session.challenge,
                session.score,
                session.hits,
                session.misses,
                session.maxStreak,
                System.currentTimeMillis()
        );

        boolean newBest = false;
        if (summary.xpAwarded() > 0L) {
            try {
                newBest = progressionService.recordTrainingBestScore(
                        playerUuid, session.challenge.discipline(), summary.finalScore());
                progressionService.awardXp(
                        playerUuid,
                        session.challenge.discipline(),
                        summary.xpAwarded(),
                        XpSource.TRAINING_CHALLENGE,
                        session.challenge.key()
                );
                if (questService != null) {
                    questService.recordEvent(playerUuid, QuestObjectiveType.TRAINING_COMPLETE,
                            session.challenge.discipline(), 1, false);
                    int discLv = progressionService.getOrLoadSnapshot(playerUuid)
                            .get(session.challenge.discipline()).level();
                    if (discLv >= 25) {
                        questService.recordEvent(playerUuid, QuestObjectiveType.MASTERY_TRIAL_CLEAR,
                                session.challenge.discipline(), 1, false);
                    }
                }
            } catch (SQLException ex) {
                logger.warn("Failed recording training reward for {}", playerUuid, ex);
            }
        }

        if (player != null && player.isOnline()) {
            TextColor accent = TextColor.fromHexString(session.challenge.discipline().accentHex());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            player.sendMessage(Component.text("━━━ " + session.challenge.displayName() + " Complete ━━━", accent, TextDecoration.BOLD));
            player.sendMessage(Component.text(
                    String.format(Locale.US, "Score: %d%s | Hits: %d | Accuracy: %.0f%% | Max Streak: %d | +%d %s XP",
                            summary.finalScore(),
                            newBest ? " ★NEW RECORD" : "",
                            summary.hits(),
                            summary.accuracyPercent(),
                            summary.maxStreak(),
                            summary.xpAwarded(),
                            session.challenge.discipline().displayName()),
                    NamedTextColor.YELLOW
            ));
        }
    }

    /**
     * Handles hitting an archery/melee/mage training target or construct.
     */
    public void handleTargetHit(
            @NotNull Player player,
            @NotNull Entity target,
            double damage,
            @Nullable Location impactPoint,
            boolean isRangedShot,
            boolean isMageBolt
    ) {
        UUID uuid = player.getUniqueId();
        ActiveSession session = sessions.get(uuid);
        if (session == null) {
            player.sendActionBar(Component.text("Start a drill at a courtyard Trainer NPC or /training start!", NamedTextColor.GRAY));
            return;
        }
        String ownerStr = target.getPersistentDataContainer().get(TRAINING_OWNER_KEY, PersistentDataType.STRING);
        if (!uuid.toString().equals(ownerStr)) {
            player.sendActionBar(Component.text("That target belongs to another challenger's trial.", NamedTextColor.RED));
            return;
        }

        TrainingChallengeType ch = session.challenge;
        if (ch.discipline() == CombatDiscipline.ARCHERY && !isRangedShot) {
            player.sendActionBar(Component.text("Use a Bow or Crossbow for Archery Courtyard targets!", NamedTextColor.YELLOW));
            return;
        }
        if (ch.discipline() == CombatDiscipline.MAGE && !isMageBolt) {
            player.sendActionBar(Component.text("Channel an Arcane Bolt with your staff/catalyst!", NamedTextColor.LIGHT_PURPLE));
            return;
        }

        Location targetLoc = target.getLocation();
        HitScoreBreakdown breakdown;

        if (ch.discipline() == CombatDiscipline.ARCHERY) {
            double dist = player.getLocation().distance(targetLoc);
            if (ch == TrainingChallengeType.ARCHERY_DISTANCE && dist < 10.0) {
                player.sendActionBar(Component.text("Step farther back! (Min 10m for Distance Challenge)", NamedTextColor.RED));
                return;
            }
            double offset = impactPoint != null ? impactPoint.distance(targetLoc.clone().add(0, 1.3, 0)) : 0.6;
            session.streak++;
            breakdown = scoringEngine.scoreArcheryHit(ch, dist, offset, session.streak);
            relocateSingleTarget(session, target);
        } else if (ch.discipline() == CombatDiscipline.MELEE) {
            if (ch == TrainingChallengeType.MELEE_SPARRING || ch == TrainingChallengeType.MELEE_SURVIVAL) {
                handleMeleeConstructHit(player, session, target, damage);
                return;
            }
            Integer slot = target.getPersistentDataContainer().get(TRAINING_SLOT_KEY, PersistentDataType.INTEGER);
            if (ch == TrainingChallengeType.MELEE_COMBO) {
                if (slot != null && slot != session.activeComboSlot) {
                    session.streak = 0;
                    player.playSound(targetLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
                    player.sendActionBar(Component.text("Combo broken! Strike the GLOWING dummy!", NamedTextColor.RED));
                    return;
                }
                advanceComboHighlight(session);
            }
            boolean parrySuccess = ch == TrainingChallengeType.MELEE_PARRY && session.parryWindowOpen;
            if (parrySuccess) {
                session.parryWindowOpen = false;
                player.playSound(targetLoc, Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.5f);
            }
            session.streak++;
            breakdown = scoringEngine.scoreMeleeHit(ch, damage, session.streak, parrySuccess);
        } else {
            // MAGE
            if ("BARRIER".equals(target.getPersistentDataContainer().get(TRAINING_ELEMENT_KEY, PersistentDataType.STRING))) {
                session.streak = 0;
                player.playSound(targetLoc, Sound.BLOCK_GLASS_BREAK, 0.7f, 0.8f);
                player.sendActionBar(Component.text("Void Barrier blocked your bolt!", NamedTextColor.DARK_PURPLE));
                return;
            }
            String elem = target.getPersistentDataContainer().get(TRAINING_ELEMENT_KEY, PersistentDataType.STRING);
            String expected = TrainingScoringEngine.elementNameForPhase(session.activeComboSlot);
            boolean matched = (ch != TrainingChallengeType.MAGE_ELEMENTAL && ch != TrainingChallengeType.MAGE_COMBO)
                    || expected.equals(elem);
            if (matched) {
                session.streak++;
                session.activeComboSlot = (session.activeComboSlot + 1) % 3;
            } else {
                session.streak = 0;
            }
            double spellMult = progressionService.activeAttributes(uuid).spellPowerMultiplier();
            breakdown = scoringEngine.scoreMageHit(ch, matched, session.streak, spellMult);
            if (ch == TrainingChallengeType.MAGE_SPELL_TARGETS || ch == TrainingChallengeType.MAGE_MANA_CHALLENGE) {
                relocateSingleTarget(session, target);
            }
        }

        session.hits++;
        session.maxStreak = Math.max(session.maxStreak, session.streak);
        session.score += breakdown.pointsAwarded();

        targetLoc.getWorld().spawnParticle(
                breakdown.bullseyeOrCritical() ? Particle.CRIT : Particle.END_ROD,
                targetLoc.clone().add(0, 1.2, 0),
                8, 0.25, 0.25, 0.25, 0.04
        );
        player.playSound(targetLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65f, breakdown.bullseyeOrCritical() ? 1.55f : 1.15f);
        sendLiveHud(player, session);
    }

    /**
     * Handles active player Parry blocking inside the Melee Courtyard during the parry telegraph.
     */
    public boolean handleParryBlock(@NotNull Player player) {
        ActiveSession session = sessions.get(player.getUniqueId());
        if (session == null || session.challenge != TrainingChallengeType.MELEE_PARRY || !session.parryWindowOpen) {
            return false;
        }
        session.parryWindowOpen = false;
        session.streak++;
        session.hits++;
        session.maxStreak = Math.max(session.maxStreak, session.streak);
        HitScoreBreakdown pts = scoringEngine.scoreMeleeHit(TrainingChallengeType.MELEE_PARRY, 10.0, session.streak, true);
        session.score += pts.pointsAwarded();

        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.6f);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1.1, 0), 3, 0.3, 0.2, 0.3, 0.0);
        player.sendActionBar(Component.text("✦ PERFECT PARRY! +" + pts.pointsAwarded() + " pts (Streak x" + session.streak + ")",
                NamedTextColor.GOLD, TextDecoration.BOLD));
        return true;
    }

    /**
     * Casts a vanilla particle Arcane Bolt for active Mage Courtyard training.
     */
    public boolean handleMageBoltCast(@NotNull Player player) {
        ActiveSession session = sessions.get(player.getUniqueId());
        if (session == null || session.challenge.discipline() != CombatDiscipline.MAGE) {
            return false;
        }
        long now = System.currentTimeMillis();
        double hastePct = progressionService.activeAttributes(player.getUniqueId()).cooldownHastePercent();
        long cooldownMs = Math.round(450L * Math.max(0.55, 1.0 - hastePct / 100.0));
        if (now - session.lastSpellCastAt < cooldownMs) {
            return true;
        }
        double cost = session.challenge == TrainingChallengeType.MAGE_MANA_CHALLENGE ? MAGE_BOLT_COST * 1.6 : MAGE_BOLT_COST;
        if (!progressionService.tryConsumeMana(player.getUniqueId(), cost)) {
            player.sendActionBar(Component.text("Low Mana! Recovering...", NamedTextColor.BLUE));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f);
            return true;
        }
        session.lastSpellCastAt = now;

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();
        player.playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.65f);

        Entity hitTarget = null;
        Location hitPoint = null;
        for (double d = 1.0; d <= 18.0; d += 0.6) {
            Location pt = eye.clone().add(dir.clone().multiply(d));
            if (d % 1.2 < 0.65) {
                world.spawnParticle(Particle.WITCH, pt, 1, 0.03, 0.03, 0.03, 0.0);
            }
            for (Entity nearby : world.getNearbyEntities(pt, 0.9, 1.2, 0.9)) {
                if (nearby.getPersistentDataContainer().has(TRAINING_TARGET_KEY, PersistentDataType.BYTE)) {
                    hitTarget = nearby;
                    hitPoint = pt;
                    break;
                }
            }
            if (hitTarget != null || pt.getBlock().getType().isSolid()) {
                break;
            }
        }

        if (hitTarget != null) {
            handleTargetHit(player, hitTarget, 8.0, hitPoint, false, true);
        } else {
            session.misses++;
            session.streak = 0;
            sendLiveHud(player, session);
        }
        return true;
    }

    public void recordMissedArcheryShot(@NotNull Player player) {
        ActiveSession session = sessions.get(player.getUniqueId());
        if (session != null && session.challenge.discipline() == CombatDiscipline.ARCHERY) {
            session.misses++;
            if (session.challenge == TrainingChallengeType.ARCHERY_ACCURACY) {
                session.streak = 0;
            }
            sendLiveHud(player, session);
        }
    }

    private void tickAllSessions() {
        long now = System.currentTimeMillis();
        for (ActiveSession session : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(session.ownerId);
            if (player == null || !player.isOnline()) {
                endSession(session.ownerId, false, null);
                continue;
            }
            if (!isConfiguredWorld(player.getWorld().getName())
                    || player.getLocation().distanceSquared(new Location(
                    player.getWorld(), session.arena.center().x(), session.arena.center().y(), session.arena.center().z())) > 32.0 * 32.0) {
                endSession(session.ownerId, false, "Left the training courtyard.");
                continue;
            }
            if (now >= session.endsAt) {
                endSession(session.ownerId, true, "Time completed.");
                continue;
            }

            session.ticksElapsed++;
            animateSessionEntities(player, session, now);
            if (session.ticksElapsed % 3 == 0) {
                sendLiveHud(player, session);
            }
        }
    }

    private void animateSessionEntities(Player player, ActiveSession session, long now) {
        World world = player.getWorld();
        Box3D b = session.arena.bounds();
        double cx = (b.minX() + b.maxX()) / 2.0 + 0.5;
        double cy = b.minY() + 1.0;
        double cz = (b.minZ() + b.maxZ()) / 2.0 + 0.5;

        double phase = session.ticksElapsed * 0.25;
        int idx = 0;
        for (UUID eid : session.spawnedEntities) {
            Entity entity = Bukkit.getEntity(eid);
            if (entity == null || !entity.isValid()) {
                continue;
            }
            switch (session.challenge) {
                case ARCHERY_MOVING -> {
                    double span = Math.max(2.0, (b.maxZ() - b.minZ()) * 0.35);
                    double z = clamp(cz + Math.sin(phase + idx * 1.9) * span, b.minZ() + 1.5, b.maxZ() - 0.5);
                    double x = clamp(b.maxX() - 2.0 - (idx * 2.5), b.minX() + 1.5, b.maxX() - 1.0);
                    entity.teleport(new Location(world, x, cy + 0.8 + (idx % 2), z));
                }
                case ARCHERY_FLYING -> {
                    double angle = phase * 0.9 + idx * 2.1;
                    double rx = Math.max(2.5, (b.maxX() - b.minX()) * 0.32);
                    double rz = Math.max(2.5, (b.maxZ() - b.minZ()) * 0.32);
                    double y = clamp(b.minY() + 4.5 + Math.sin(phase * 1.4 + idx) * 2.2, b.minY() + 2.0, b.maxY() - 2.0);
                    entity.teleport(new Location(world, cx + Math.cos(angle) * rx, y, cz + Math.sin(angle) * rz));
                    world.spawnParticle(Particle.CLOUD, entity.getLocation().add(0, 1.3, 0), 2, 0.2, 0.1, 0.2, 0.01);
                }
                case MAGE_OBSTACLES -> {
                    if ("BARRIER".equals(entity.getPersistentDataContainer().get(TRAINING_ELEMENT_KEY, PersistentDataType.STRING))) {
                        double angle = phase * 1.15 + idx * Math.PI;
                        Location next = new Location(world, cx + Math.cos(angle) * 3.0, cy + 1.2, cz + Math.sin(angle) * 3.0);
                        entity.teleport(next);
                        world.spawnParticle(Particle.PORTAL, next.clone().add(0, 1.0, 0), 4, 0.2, 0.3, 0.2, 0.02);
                    }
                }
                default -> {
                    // Gently float arcane crystals in place
                    if (session.challenge.discipline() == CombatDiscipline.MAGE && idx > 0) {
                        world.spawnParticle(Particle.ENCHANT, entity.getLocation().add(0, 1.2, 0), 2, 0.2, 0.2, 0.2, 0.02);
                    }
                }
            }
            idx++;
        }

        // Melee Parry Telegraph Trigger every ~3.5s
        if (session.challenge == TrainingChallengeType.MELEE_PARRY) {
            if (!session.parryWindowOpen && session.ticksElapsed % 16 == 6) {
                session.parryWindowOpen = true;
                session.parryWindowExpiresAt = now + 1800L;
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 0.9f, 1.7f);
                world.spawnParticle(Particle.FLAME, player.getLocation().add(0, 1.4, 0), 14, 0.6, 0.4, 0.6, 0.02);
                player.sendActionBar(Component.text("⚠ TELEGRAPH! Right-click Block or Strike Dummy NOW!",
                        NamedTextColor.RED, TextDecoration.BOLD));
            } else if (session.parryWindowOpen && now > session.parryWindowExpiresAt) {
                session.parryWindowOpen = false;
                session.streak = 0;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            }
        }
    }

    private void spawnInitialTargets(World world, ActiveSession session) {
        TrainingChallengeType ch = session.challenge;
        Box3D b = session.arena.bounds();
        double cx = (b.minX() + b.maxX()) / 2.0 + 0.5;
        double cz = (b.minZ() + b.maxZ()) / 2.0 + 0.5;
        double floorY = session.arena.center().y();

        switch (ch.discipline()) {
            case ARCHERY -> {
                for (int i = 0; i < 3; i++) {
                    double z = clamp(cz - 3.5 + i * 3.5, b.minZ() + 1.5, b.maxZ() - 1.5);
                    double x = ch == TrainingChallengeType.ARCHERY_DISTANCE
                            ? b.maxX() - 1.5
                            : clamp(cx + 3.0 + (i % 2) * 2.5, b.minX() + 1.5, b.maxX() - 1.5);
                    double y = ch == TrainingChallengeType.ARCHERY_FLYING ? floorY + 3.5 : floorY + 0.5;
                    Material helmet = i == 0 ? Material.TARGET : (i == 1 ? Material.RED_GLAZED_TERRACOTTA : Material.GLOWSTONE);
                    ArmorStand target = spawnTargetStand(world, new Location(world, x, y, z, -90f, 0f),
                            session.ownerId, helmet, Color.LIME, "§a§l◎ Archery Target", null, i);
                    session.spawnedEntities.add(target.getUniqueId());
                }
            }
            case MELEE -> {
                if (ch == TrainingChallengeType.MELEE_SPARRING || ch == TrainingChallengeType.MELEE_SURVIVAL) {
                    spawnSparringConstructs(world, session);
                } else {
                    for (int i = 0; i < 3; i++) {
                        double x = clamp(cx - 2.5 + i * 2.5, b.minX() + 1.5, b.maxX() - 1.5);
                        Location loc = new Location(world, x, floorY, cz, 0f, 0f);
                        String title = ch == TrainingChallengeType.MELEE_COMBO && i == 0
                                ? "§e§l★ STRIKE NEXT"
                                : "§c⚔ Combat Dummy #" + (i + 1);
                        ArmorStand dummy = spawnTargetStand(world, loc, session.ownerId,
                                Material.NETHERITE_HELMET, Color.RED, title, null, i);
                        if (ch == TrainingChallengeType.MELEE_COMBO && i == 0) {
                            dummy.setGlowing(true);
                        }
                        session.spawnedEntities.add(dummy.getUniqueId());
                    }
                }
            }
            case MAGE -> {
                String[] elems = {"IGNIS", "FROST", "VOID"};
                Material[] mats = {Material.MAGMA_BLOCK, Material.PACKED_ICE, Material.AMETHYST_CLUSTER};
                String[] names = {"§6✦ Ignis Focus", "§b✦ Frost Focus", "§d✦ Void Focus"};
                for (int i = 0; i < 3; i++) {
                    double angle = (Math.PI * 2.0 * i) / 3.0;
                    Location loc = new Location(world, cx + Math.cos(angle) * 4.0, floorY + 1.5, cz + Math.sin(angle) * 4.0);
                    ArmorStand focus = spawnTargetStand(world, loc, session.ownerId,
                            mats[i], Color.PURPLE, names[i], elems[i], i);
                    focus.setGlowing(i == 0 && (ch == TrainingChallengeType.MAGE_ELEMENTAL || ch == TrainingChallengeType.MAGE_COMBO));
                    session.spawnedEntities.add(focus.getUniqueId());
                }
                if (ch == TrainingChallengeType.MAGE_OBSTACLES) {
                    ArmorStand barrier = spawnTargetStand(world, new Location(world, cx + 2.5, floorY + 1.3, cz),
                            session.ownerId, Material.CRYING_OBSIDIAN, Color.BLACK, "§5☠ Void Barrier", "BARRIER", 9);
                    session.spawnedEntities.add(barrier.getUniqueId());
                }
            }
        }
    }

    private void spawnSparringConstructs(World world, ActiveSession session) {
        int count = session.challenge == TrainingChallengeType.MELEE_SURVIVAL ? Math.min(3, session.currentWave) : 1;
        Location c = new Location(world, session.arena.center().x(), session.arena.center().y(), session.arena.center().z());
        for (int i = 0; i < count; i++) {
            Location spawnLoc = c.clone().add((i - 0.5) * 2.2, 0.2, 1.5);
            Husk construct = (Husk) world.spawnEntity(spawnLoc, EntityType.HUSK);
            construct.setAdult();
            construct.setShouldBurnInDay(false);
            construct.customName(Component.text("Endblade Construct (W" + session.currentWave + ")", NamedTextColor.RED, TextDecoration.BOLD));
            construct.setCustomNameVisible(true);
            EntityEquipment eq = construct.getEquipment();
            if (eq != null) {
                eq.setHelmet(new ItemStack(Material.IRON_HELMET));
                eq.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                eq.setHelmetDropChance(0f);
                eq.setItemInMainHandDropChance(0f);
            }
            construct.getPersistentDataContainer().set(TRAINING_TARGET_KEY, PersistentDataType.BYTE, (byte) 1);
            construct.getPersistentDataContainer().set(TRAINING_OWNER_KEY, PersistentDataType.STRING, session.ownerId.toString());
            construct.getPersistentDataContainer().set(CombatProgressionListener.NO_XP_KEY, PersistentDataType.BYTE, (byte) 1);
            session.spawnedEntities.add(construct.getUniqueId());
        }
    }

    private void handleMeleeConstructHit(Player player, ActiveSession session, Entity target, double damage) {
        session.hits++;
        session.streak++;
        session.maxStreak = Math.max(session.maxStreak, session.streak);
        HitScoreBreakdown pts = scoringEngine.scoreMeleeHit(session.challenge, damage, session.streak, false);
        session.score += pts.pointsAwarded();

        if (target instanceof LivingEntity living && living.getHealth() - Math.max(4.0, damage) <= 0.5) {
            target.remove();
            session.spawnedEntities.remove(target.getUniqueId());
            session.score += 60;
            player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 0.6f, 1.4f);

            if (session.spawnedEntities.isEmpty()) {
                session.currentWave++;
                if (session.challenge == TrainingChallengeType.MELEE_SURVIVAL && session.currentWave <= 3) {
                    player.sendMessage(Component.text("✦ Wave " + session.currentWave + " incoming!", NamedTextColor.GOLD, TextDecoration.BOLD));
                    spawnSparringConstructs(player.getWorld(), session);
                } else {
                    endSession(session.ownerId, true, "Sparring defeated!");
                    return;
                }
            }
        }
        sendLiveHud(player, session);
    }

    private void relocateSingleTarget(ActiveSession session, Entity target) {
        Box3D b = session.arena.bounds();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double x = rng.nextDouble(b.minX() + 1.5, Math.max(b.minX() + 2.0, b.maxX() - 0.5));
        double y = session.arena.center().y() + rng.nextDouble(0.5, 3.0);
        double z = rng.nextDouble(b.minZ() + 1.5, Math.max(b.minZ() + 2.0, b.maxZ() - 0.5));
        target.teleport(new Location(target.getWorld(), x, y, z, target.getLocation().getYaw(), 0f));
    }

    private void advanceComboHighlight(ActiveSession session) {
        session.activeComboSlot = (session.activeComboSlot + 1) % 3;
        int i = 0;
        for (UUID eid : session.spawnedEntities) {
            Entity e = Bukkit.getEntity(eid);
            if (e != null) {
                boolean active = (i == session.activeComboSlot);
                e.setGlowing(active);
                e.customName(Component.text(active ? "★ STRIKE NEXT" : "Combat Dummy #" + (i + 1),
                        active ? NamedTextColor.YELLOW : NamedTextColor.RED));
            }
            i++;
        }
    }

    private void sendLiveHud(Player player, ActiveSession session) {
        long remainSec = Math.max(0L, (session.endsAt - System.currentTimeMillis() + 999L) / 1000L);
        TextColor accent = TextColor.fromHexString(session.challenge.discipline().accentHex());
        String extra = session.challenge == TrainingChallengeType.MAGE_ELEMENTAL
                || session.challenge == TrainingChallengeType.MAGE_COMBO
                ? " | Cast: " + TrainingScoringEngine.elementNameForPhase(session.activeComboSlot)
                : "";
        if (session.challenge.discipline() == CombatDiscipline.MAGE) {
            extra += String.format(Locale.US, " | Mana: %.0f/%.0f",
                    progressionService.currentMana(player.getUniqueId()),
                    progressionService.maxMana(player.getUniqueId()));
        }
        player.sendActionBar(
                Component.text(session.challenge.displayName() + " ", accent, TextDecoration.BOLD)
                        .append(Component.text("[" + remainSec + "s] ", NamedTextColor.WHITE))
                        .append(Component.text("Score: " + session.score + " | Streak: " + session.streak + extra, NamedTextColor.GRAY))
        );
    }

    private ArmorStand spawnTargetStand(
            World world,
            Location loc,
            UUID ownerId,
            Material helmetMat,
            Color chestColor,
            String customName,
            @Nullable String elementTag,
            int slotIndex
    ) {
        ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setCanPickupItems(false);
        stand.setCustomNameVisible(true);
        stand.customName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(customName));
        stand.getPersistentDataContainer().set(TRAINING_TARGET_KEY, PersistentDataType.BYTE, (byte) 1);
        stand.getPersistentDataContainer().set(TRAINING_OWNER_KEY, PersistentDataType.STRING, ownerId.toString());
        stand.getPersistentDataContainer().set(TRAINING_SLOT_KEY, PersistentDataType.INTEGER, slotIndex);
        stand.getPersistentDataContainer().set(CombatProgressionListener.NO_XP_KEY, PersistentDataType.BYTE, (byte) 1);
        if (elementTag != null) {
            stand.getPersistentDataContainer().set(TRAINING_ELEMENT_KEY, PersistentDataType.STRING, elementTag);
        }

        EntityEquipment eq = stand.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(helmetMat));
            ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
            if (chest.getItemMeta() instanceof LeatherArmorMeta meta) {
                meta.setColor(chestColor);
                chest.setItemMeta(meta);
            }
            eq.setChestplate(chest);
        }
        return stand;
    }

    private void cleanupSessionEntities(ActiveSession session) {
        for (UUID eid : session.spawnedEntities) {
            Entity e = Bukkit.getEntity(eid);
            if (e != null && e.isValid()) {
                e.remove();
            }
        }
        session.spawnedEntities.clear();
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
