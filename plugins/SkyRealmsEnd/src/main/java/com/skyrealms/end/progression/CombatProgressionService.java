package com.skyrealms.end.progression;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.npc.NpcInteractionHandler;
import com.skyrealms.end.quest.EndQuestService;
import com.skyrealms.end.quest.QuestObjectiveType;
import com.skyrealms.end.progression.CombatContributionTracker.AntiAfkVerdict;
import com.skyrealms.end.progression.CombatContributionTracker.AttributedShare;
import com.skyrealms.end.progression.DisciplineProgressState.DisciplineEntry;
import com.skyrealms.end.progression.DisciplineProgressState.LeaderboardEntry;
import com.skyrealms.end.progression.DisciplineProgressState.MutablePlayerState;
import com.skyrealms.end.progression.DisciplineProgressState.PlayerDisciplineSnapshot;
import com.skyrealms.end.progression.DisciplineProgressState.XpAwardResult;
import com.skyrealms.end.progression.EndXpCurve.DisciplineAttributes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative runtime coordinator for independent Archery, Melee, and Mage progression.
 */
public final class CombatProgressionService {

    private static final NamespacedKey MELEE_DMG_KEY = new NamespacedKey("skyrealms", "end_disc_melee_damage");
    private static final NamespacedKey MELEE_HP_KEY = new NamespacedKey("skyrealms", "end_disc_max_health");
    private static final NamespacedKey MELEE_TOUGH_KEY = new NamespacedKey("skyrealms", "end_disc_toughness");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("skyrealms", "end_disc_agility");

    private final Plugin plugin;
    private final EndVillageConfig villageConfig;
    private final DisciplineProgressRepository repository;
    private final CombatContributionTracker contributionTracker;
    private final Logger logger;

    private final Map<UUID, MutablePlayerState> onlineStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSwitchNoticeAt = new ConcurrentHashMap<>();
    private BukkitTask maintenanceTask;
    private @Nullable EndQuestService questService;

    public CombatProgressionService(
            @NotNull Plugin plugin,
            @NotNull EndVillageConfig villageConfig,
            @NotNull DisciplineProgressRepository repository,
            @NotNull CombatContributionTracker contributionTracker,
            @NotNull Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.villageConfig = Objects.requireNonNull(villageConfig, "villageConfig cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.contributionTracker = Objects.requireNonNull(contributionTracker, "contributionTracker cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    public void setQuestService(@Nullable EndQuestService questService) {
        this.questService = questService;
    }

    public void start() {
        stop();
        // Load already-online players (hot reload safe)
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadOnlinePlayerAsync(player);
        }
        // Single lightweight 1Hz centralized maintenance task: Mage mana regen + stale combat ledger purge.
        this.maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            contributionTracker.purgeExpiredLedgers(now);
            for (Player player : Bukkit.getOnlinePlayers()) {
                MutablePlayerState state = onlineStates.get(player.getUniqueId());
                if (state == null) {
                    continue;
                }
                int mageLevel = state.entry(CombatDiscipline.MAGE).level();
                double regen = EndXpCurve.attributesFor(CombatDiscipline.MAGE, mageLevel).manaRegenPerSecond();
                state.regenerateMana(regen);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearModifiers(player);
        }
        flushAllBlocking();
        onlineStates.clear();
        lastSwitchNoticeAt.clear();
    }

    public void loadOnlinePlayerAsync(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        runAsync(() -> {
            try {
                PlayerDisciplineSnapshot loaded = repository.loadOrCreateAll(uuid);
                MutablePlayerState state = new MutablePlayerState(
                        uuid,
                        loaded.activeDiscipline(),
                        loaded.entries()
                );
                onlineStates.put(uuid, state);
                runSync(() -> {
                    if (player.isOnline()) {
                        applyAttributes(player, state);
                    }
                });
            } catch (SQLException ex) {
                logger.error("Failed loading End discipline progress for {}", uuid, ex);
            }
        });
    }

    public void unloadOnlinePlayer(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        clearModifiers(player);
        lastSwitchNoticeAt.remove(uuid);
        contributionTracker.forgetPlayer(uuid);
        MutablePlayerState removed = onlineStates.remove(uuid);
        if (removed != null && removed.isDirty()) {
            PlayerDisciplineSnapshot snapshot = removed.snapshot();
            runAsync(() -> persistSnapshotSafe(snapshot));
        }
    }

    @NotNull
    public PlayerDisciplineSnapshot getOrLoadSnapshot(@NotNull UUID playerUuid) throws SQLException {
        MutablePlayerState cached = onlineStates.get(playerUuid);
        if (cached != null) {
            return cached.snapshot();
        }
        return repository.loadOrCreateAll(playerUuid);
    }

    @NotNull
    public CombatDiscipline getActiveDiscipline(@NotNull UUID playerUuid) {
        MutablePlayerState cached = onlineStates.get(playerUuid);
        return cached != null ? cached.activeDiscipline() : CombatDiscipline.MELEE;
    }

    /**
     * Switches a player's active discipline cleanly while preserving all three discipline levels & XP.
     */
    public boolean switchDiscipline(@NotNull Player player, @NotNull CombatDiscipline target, boolean announce) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(target, "target cannot be null");

        MutablePlayerState state = ensureCachedSync(player.getUniqueId());
        if (state == null) {
            return false;
        }
        boolean changed = state.setActiveDiscipline(target);
        if (!changed) {
            return false;
        }
        applyAttributes(player, state);
        runAsync(() -> {
            try {
                repository.updateActiveDiscipline(player.getUniqueId(), target);
                state.clearDirty();
            } catch (SQLException ex) {
                logger.warn("Failed persisting active discipline switch for {}", player.getUniqueId(), ex);
            }
        });

        if (announce) {
            long now = System.currentTimeMillis();
            Long lastNotice = lastSwitchNoticeAt.get(player.getUniqueId());
            if (lastNotice == null || (now - lastNotice) >= 1500L) {
                lastSwitchNoticeAt.put(player.getUniqueId(), now);
                DisciplineEntry entry = state.entry(target);
                player.sendActionBar(
                        Component.text("Active Discipline: ", NamedTextColor.GRAY)
                                .append(Component.text(target.displayName() + " Lv." + entry.level(),
                                        TextColor.fromHexString(target.accentHex()), TextDecoration.BOLD))
                                .append(Component.text(" (" + entry.totalXp() + " XP)", NamedTextColor.DARK_GRAY))
                );
            }
        }
        return true;
    }

    /**
     * Grants authoritative XP to a specific discipline, applying level-ups, attribute updates, and persistence.
     */
    @NotNull
    public XpAwardResult awardXp(
            @NotNull UUID playerUuid,
            @NotNull CombatDiscipline discipline,
            long amount,
            @NotNull XpSource source,
            @NotNull String detail
    ) throws SQLException {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(discipline, "discipline cannot be null");
        Objects.requireNonNull(source, "source cannot be null");

        if (amount <= 0L) {
            PlayerDisciplineSnapshot snap = getOrLoadSnapshot(playerUuid);
            DisciplineEntry existing = snap.get(discipline);
            return new XpAwardResult(discipline, amount, 0L, existing.level(), existing.level(),
                    existing.totalXp(), false, false, "non-positive amount");
        }

        MutablePlayerState cached = onlineStates.get(playerUuid);
        if (cached == null) {
            // Authoritative direct DB update for offline/admin awards
            PlayerDisciplineSnapshot loaded = repository.loadOrCreateAll(playerUuid);
            DisciplineEntry current = loaded.get(discipline);
            long updatedXp = EndXpCurve.clampTotalXp(current.totalXp() + amount);
            DisciplineEntry next = current.withXp(updatedXp, System.currentTimeMillis());
            repository.saveEntry(playerUuid, next);
            long actualGranted = Math.max(0L, next.totalXp() - current.totalXp());
            dispatchQuestCombatEvents(playerUuid, discipline, next.level(), next.level() > current.level(), source);
            return new XpAwardResult(
                    discipline, amount, actualGranted, current.level(), next.level(),
                    next.totalXp(), next.level() > current.level(), false, detail
            );
        }

        DisciplineEntry previous;
        DisciplineEntry next;
        synchronized (cached) {
            previous = cached.entry(discipline);
            long updatedXp = EndXpCurve.clampTotalXp(previous.totalXp() + amount);
            next = previous.withXp(updatedXp, System.currentTimeMillis());
            cached.putEntry(next);
        }

        final DisciplineEntry toSave = next;
        runAsync(() -> {
            try {
                repository.saveEntry(playerUuid, toSave);
            } catch (SQLException ex) {
                logger.error("Failed saving End discipline XP for {}", playerUuid, ex);
            }
        });

        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null && online.isOnline()) {
            applyAttributes(online, cached);
            notifyXpAndLevelUp(online, discipline, amount, previous.level(), next);
        }
        dispatchQuestCombatEvents(playerUuid, discipline, next.level(), next.level() > previous.level(), source);

        return new XpAwardResult(
                discipline,
                amount,
                Math.max(0L, next.totalXp() - previous.totalXp()),
                previous.level(),
                next.level(),
                next.totalXp(),
                next.level() > previous.level(),
                false,
                detail
        );
    }

    private void dispatchQuestCombatEvents(
            UUID playerUuid,
            CombatDiscipline discipline,
            int newLevel,
            boolean leveledUp,
            XpSource source
    ) {
        if (questService == null) {
            return;
        }
        if (leveledUp) {
            questService.recordEvent(playerUuid, QuestObjectiveType.DISCIPLINE_LEVEL, discipline, newLevel, true);
        }
        if (source == XpSource.MONSTER_KILL) {
            questService.recordEvent(playerUuid, QuestObjectiveType.MOB_KILL, discipline, 1, false);
            if (newLevel >= 25) {
                questService.recordEvent(playerUuid, QuestObjectiveType.MASTERY_TRIAL_CLEAR, discipline, 1, false);
            }
        }
    }

    /**
     * Distributes kill XP across all tracked player-discipline contributors for a dying mob,
     * applying atomic single-settlement and Anti-AFK evaluation.
     */
    public void distributeMobKillXp(@NotNull UUID mobUuid, long baseXp, @NotNull String mobLabel) {
        if (baseXp <= 0L) {
            contributionTracker.consumeKillShares(mobUuid, 0L, false);
            return;
        }
        List<AttributedShare> shares = contributionTracker.consumeKillShares(mobUuid, baseXp, true);
        if (shares.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (AttributedShare share : shares) {
            Player player = Bukkit.getPlayer(share.playerUuid());
            double mult = 1.0;
            if (player != null && player.isOnline()) {
                AntiAfkVerdict verdict = contributionTracker.evaluateKillActivity(
                        player.getUniqueId(),
                        player.getLocation().getX(),
                        player.getLocation().getY(),
                        player.getLocation().getZ(),
                        player.getLocation().getYaw(),
                        player.getLocation().getPitch(),
                        now
                );
                if (!verdict.allowed()) {
                    player.sendActionBar(Component.text(verdict.reason(), NamedTextColor.RED));
                    continue;
                }
                mult = verdict.multiplier();
            }

            long adjusted = Math.round(share.xpAward() * mult);
            if (adjusted <= 0L) {
                continue;
            }
            try {
                awardXp(share.playerUuid(), share.discipline(), adjusted, XpSource.MONSTER_KILL, mobLabel);
            } catch (SQLException ex) {
                logger.warn("Error awarding kill XP to {}", share.playerUuid(), ex);
            }
        }
    }

    public void setDisciplineLevel(@NotNull UUID playerUuid, @NotNull CombatDiscipline discipline, int level)
            throws SQLException {
        int clamped = Math.max(EndXpCurve.MIN_LEVEL, Math.min(EndXpCurve.MAX_LEVEL, level));
        long targetXp = EndXpCurve.totalXpForLevel(clamped);

        MutablePlayerState cached = onlineStates.get(playerUuid);
        if (cached != null) {
            DisciplineEntry next;
            synchronized (cached) {
                next = cached.entry(discipline).withXp(targetXp, System.currentTimeMillis());
                cached.putEntry(next);
            }
            repository.saveEntry(playerUuid, next);
            Player online = Bukkit.getPlayer(playerUuid);
            if (online != null && online.isOnline()) {
                applyAttributes(online, cached);
            }
        } else {
            PlayerDisciplineSnapshot loaded = repository.loadOrCreateAll(playerUuid);
            DisciplineEntry next = loaded.get(discipline).withXp(targetXp, System.currentTimeMillis());
            repository.saveEntry(playerUuid, next);
        }
    }

    public void markMasteryCompleted(@NotNull UUID playerUuid, @NotNull CombatDiscipline discipline) throws SQLException {
        long now = System.currentTimeMillis();
        mutateEntry(playerUuid, discipline, entry -> entry.withMastery(true, now), false);
    }

    /**
     * Records a training high score if it beats the player's stored best score for the given discipline.
     *
     * @return true if this score is a new personal record
     */
    public boolean recordTrainingBestScore(@NotNull UUID playerUuid, @NotNull CombatDiscipline discipline, int score)
            throws SQLException {
        if (score <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        DisciplineEntry[] holder = new DisciplineEntry[2];
        mutateEntry(playerUuid, discipline, entry -> {
            holder[0] = entry;
            DisciplineEntry next = entry.withTrainingBestScore(score, now);
            holder[1] = next;
            return next;
        }, true);
        return holder[1] != null && holder[0] != null && holder[1].trainingBestScore() > holder[0].trainingBestScore();
    }

    public boolean tryConsumeMana(@NotNull UUID playerUuid, double cost) {
        MutablePlayerState cached = onlineStates.get(playerUuid);
        if (cached == null) {
            return cost <= 100.0;
        }
        return cached.consumeMana(cost);
    }

    public double currentMana(@NotNull UUID playerUuid) {
        MutablePlayerState cached = onlineStates.get(playerUuid);
        return cached != null ? cached.currentMana() : 100.0;
    }

    public double maxMana(@NotNull UUID playerUuid) {
        MutablePlayerState cached = onlineStates.get(playerUuid);
        return cached != null ? cached.maxMana() : 100.0;
    }

    private void mutateEntry(
            UUID playerUuid,
            CombatDiscipline discipline,
            java.util.function.UnaryOperator<DisciplineEntry> updater,
            boolean saveAsyncIfOnline
    ) throws SQLException {
        MutablePlayerState cached = onlineStates.get(playerUuid);
        if (cached != null) {
            DisciplineEntry next;
            synchronized (cached) {
                next = updater.apply(cached.entry(discipline));
                cached.putEntry(next);
            }
            if (saveAsyncIfOnline) {
                runAsync(() -> {
                    try {
                        repository.saveEntry(playerUuid, next);
                    } catch (SQLException ex) {
                        logger.error("Failed saving End discipline entry for {}", playerUuid, ex);
                    }
                });
            } else {
                repository.saveEntry(playerUuid, next);
            }
        } else {
            PlayerDisciplineSnapshot loaded = repository.loadOrCreateAll(playerUuid);
            DisciplineEntry next = updater.apply(loaded.get(discipline));
            repository.saveEntry(playerUuid, next);
        }
    }

    @NotNull
    public DisciplineAttributes activeAttributes(@NotNull UUID playerUuid) {
        MutablePlayerState state = onlineStates.get(playerUuid);
        if (state == null) {
            return EndXpCurve.attributesFor(CombatDiscipline.MELEE, 1);
        }
        CombatDiscipline active = state.activeDiscipline();
        int level = state.entry(active).level();
        return EndXpCurve.attributesFor(active, level);
    }

    /**
     * NPC interaction handler for Leaderboard Keeper (`NpcActionType.LEADERBOARD`).
     */
    @NotNull
    public NpcInteractionHandler leaderboardNpcHandler() {
        return (player, npc) -> {
            player.sendMessage(Component.text("━━━ End Civilization Discipline Rankings ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
            runAsync(() -> {
                try {
                    for (CombatDiscipline disc : CombatDiscipline.values()) {
                        List<LeaderboardEntry> top = repository.getTopByDiscipline(disc, 3);
                        runSync(() -> sendLeaderboardSection(player, disc, top));
                    }
                } catch (SQLException ex) {
                    logger.warn("Failed loading leaderboard for NPC {}", npc.id(), ex);
                    runSync(() -> player.sendMessage(Component.text("Rankings are temporarily unavailable.", NamedTextColor.RED)));
                }
            });
        };
    }

    public void sendLeaderboardSection(@NotNull Player viewer, @NotNull CombatDiscipline disc, @NotNull List<LeaderboardEntry> entries) {
        TextColor color = TextColor.fromHexString(disc.accentHex());
        viewer.sendMessage(Component.text("• " + disc.displayName() + " Top 3:", color, TextDecoration.BOLD));
        if (entries.isEmpty()) {
            viewer.sendMessage(Component.text("  (No recorded challengers yet)", NamedTextColor.DARK_GRAY));
            return;
        }
        int rank = 1;
        for (LeaderboardEntry entry : entries) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(entry.playerUuid());
            String name = off.getName() != null ? off.getName() : entry.playerUuid().toString().substring(0, 8);
            String seal = entry.masteryCompleted() ? " ★" : "";
            viewer.sendMessage(Component.text(
                    "  #" + rank++ + " " + name + " — Lv." + entry.level() + " (" + entry.totalXp() + " XP)" + seal,
                    NamedTextColor.GRAY
            ));
        }
    }

    public void applyAttributes(@NotNull Player player, @NotNull MutablePlayerState state) {
        CombatDiscipline active = state.activeDiscipline();
        int activeLevel = state.entry(active).level();
        int meleeLevel = state.entry(CombatDiscipline.MELEE).level();

        DisciplineAttributes activeAttrs = EndXpCurve.attributesFor(active, activeLevel);
        DisciplineAttributes meleeAttrs = EndXpCurve.attributesFor(CombatDiscipline.MELEE, meleeLevel);

        // Melee passive resilience applies when Melee is trained; active attack damage scales when Melee is active
        syncModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, MELEE_DMG_KEY,
                active == CombatDiscipline.MELEE ? meleeAttrs.bonusMeleeDamage() : 0.0,
                AttributeModifier.Operation.ADD_NUMBER);
        syncModifier(player, Attribute.GENERIC_MAX_HEALTH, MELEE_HP_KEY,
                meleeAttrs.bonusMaxHealth(),
                AttributeModifier.Operation.ADD_NUMBER);
        syncModifier(player, Attribute.GENERIC_ARMOR_TOUGHNESS, MELEE_TOUGH_KEY,
                meleeAttrs.bonusArmorToughness(),
                AttributeModifier.Operation.ADD_NUMBER);
        syncModifier(player, Attribute.GENERIC_MOVEMENT_SPEED, SPEED_KEY,
                activeAttrs.walkSpeedBonusScalar(),
                AttributeModifier.Operation.ADD_SCALAR);
    }

    public void clearModifiers(@NotNull Player player) {
        removeModifier(player, Attribute.GENERIC_ATTACK_DAMAGE, MELEE_DMG_KEY);
        removeModifier(player, Attribute.GENERIC_MAX_HEALTH, MELEE_HP_KEY);
        removeModifier(player, Attribute.GENERIC_ARMOR_TOUGHNESS, MELEE_TOUGH_KEY);
        removeModifier(player, Attribute.GENERIC_MOVEMENT_SPEED, SPEED_KEY);
    }

    public boolean isConfiguredWorld(@Nullable String worldName) {
        return worldName != null && worldName.equalsIgnoreCase(villageConfig.worldName());
    }

    private void notifyXpAndLevelUp(
            Player player,
            CombatDiscipline discipline,
            long granted,
            int oldLevel,
            DisciplineEntry next
    ) {
        TextColor accent = TextColor.fromHexString(discipline.accentHex());
        long intoLevel = EndXpCurve.xpIntoCurrentLevel(next.totalXp());
        long stepTotal = EndXpCurve.xpRequiredForNextLevel(next.level());
        String progressSuffix = next.level() >= EndXpCurve.MAX_LEVEL
                ? "MAX LEVEL"
                : intoLevel + "/" + stepTotal + " XP";

        player.sendActionBar(
                Component.text("+" + granted + " " + discipline.displayName() + " XP ", accent, TextDecoration.BOLD)
                        .append(Component.text("[Lv." + next.level() + " • " + progressSuffix + "]", NamedTextColor.GRAY))
        );

        if (next.level() > oldLevel) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.25f);
            player.showTitle(Title.title(
                    Component.text(discipline.displayName().toUpperCase() + " LEVEL UP!", accent, TextDecoration.BOLD),
                    Component.text("Reached Level " + next.level() + " — " + discipline.attributeSummary(), NamedTextColor.GOLD),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2200), Duration.ofMillis(500))
            ));
            player.sendMessage(
                    Component.text("✦ " + discipline.displayName() + " Discipline reached Level " + next.level() + "!",
                            accent, TextDecoration.BOLD)
            );
        }
    }

    private @Nullable MutablePlayerState ensureCachedSync(UUID uuid) {
        MutablePlayerState existing = onlineStates.get(uuid);
        if (existing != null) {
            return existing;
        }
        try {
            PlayerDisciplineSnapshot loaded = repository.loadOrCreateAll(uuid);
            MutablePlayerState created = new MutablePlayerState(uuid, loaded.activeDiscipline(), loaded.entries());
            onlineStates.put(uuid, created);
            return created;
        } catch (SQLException ex) {
            logger.error("Failed synchronous load for {}", uuid, ex);
            return null;
        }
    }

    private void flushAllBlocking() {
        for (MutablePlayerState state : onlineStates.values()) {
            if (state.isDirty()) {
                persistSnapshotSafe(state.snapshot());
                state.clearDirty();
            }
        }
    }

    private void persistSnapshotSafe(PlayerDisciplineSnapshot snapshot) {
        try {
            repository.saveSnapshot(snapshot);
        } catch (SQLException ex) {
            logger.error("Failed persisting discipline snapshot for {}", snapshot.playerUuid(), ex);
        }
    }

    private void runAsync(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    private void runSync(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        } else {
            task.run();
        }
    }

    private static void syncModifier(
            Player player,
            Attribute attribute,
            NamespacedKey key,
            double amount,
            AttributeModifier.Operation operation
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(key);
        if (amount <= 0.0) {
            if (existing != null) {
                instance.removeModifier(key);
            }
            return;
        }
        if (existing != null) {
            if (Double.compare(existing.getAmount(), amount) == 0 && existing.getOperation() == operation) {
                return;
            }
            instance.removeModifier(key);
        }
        instance.addTransientModifier(new AttributeModifier(key, amount, operation));
    }

    private static void removeModifier(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.getModifier(key) != null) {
            instance.removeModifier(key);
        }
    }
}
