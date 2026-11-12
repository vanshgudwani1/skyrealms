package com.skyrealms.end.quest;

import com.skyrealms.end.gear.EndGearService;
import com.skyrealms.end.npc.NpcInteractionHandler;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.DisciplineProgressState.PlayerDisciplineSnapshot;
import com.skyrealms.end.quest.QuestProgressRepository.QuestProgressRow;
import com.skyrealms.end.quest.QuestProgressRepository.QuestStatus;
import com.skyrealms.end.service.EndProfileRepository;
import com.skyrealms.end.service.EndProfileRepository.EndPlayerProfile;
import com.skyrealms.items.service.ItemRegistryService;
import com.skyrealms.platform.integrations.EconomyProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative event-driven quest progression & idempotent reward service for SkyRealms End.
 */
public final class EndQuestService {

    public record ClaimOutcome(boolean success, @NotNull String message) {}

    private final QuestRegistry questRegistry;
    private final QuestProgressRepository questRepository;
    private final EndProfileRepository profileRepository;
    private final CombatProgressionService progressionService;
    private final ItemRegistryService itemService;
    private final EndGearService gearService;
    private final @Nullable EconomyProvider economyProvider;
    private final Logger logger;

    public EndQuestService(
            @NotNull QuestRegistry questRegistry,
            @NotNull QuestProgressRepository questRepository,
            @NotNull EndProfileRepository profileRepository,
            @NotNull CombatProgressionService progressionService,
            @NotNull ItemRegistryService itemService,
            @NotNull EndGearService gearService,
            @Nullable EconomyProvider economyProvider,
            @NotNull Logger logger
    ) {
        this.questRegistry = Objects.requireNonNull(questRegistry, "questRegistry cannot be null");
        this.questRepository = Objects.requireNonNull(questRepository, "questRepository cannot be null");
        this.profileRepository = Objects.requireNonNull(profileRepository, "profileRepository cannot be null");
        this.progressionService = Objects.requireNonNull(progressionService, "progressionService cannot be null");
        this.itemService = Objects.requireNonNull(itemService, "itemService cannot be null");
        this.gearService = Objects.requireNonNull(gearService, "gearService cannot be null");
        this.economyProvider = economyProvider;
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
    }

    /**
     * Accepts a quest for the player after verifying prerequisite & discipline level gates.
     */
    @NotNull
    public ClaimOutcome acceptQuest(@NotNull UUID playerUuid, @NotNull String questId) throws SQLException {
        QuestDefinition def = questRegistry.get(questId);
        if (def == null) {
            return new ClaimOutcome(false, "Unknown quest: " + questId);
        }
        String cycleKey = QuestRegistry.cycleKeyFor(def, LocalDate.now());
        Map<String, QuestProgressRow> rows = questRepository.loadAll(playerUuid);
        QuestProgressRow current = rows.get(QuestProgressRepository.key(def.id(), cycleKey));
        if (current != null && current.status() != QuestStatus.AVAILABLE) {
            return new ClaimOutcome(false, "Quest is already " + current.status().name() + ".");
        }

        if (def.prerequisiteQuestId() != null) {
            QuestDefinition prereq = questRegistry.get(def.prerequisiteQuestId());
            if (prereq != null) {
                String prereqKey = QuestProgressRepository.key(prereq.id(), QuestRegistry.cycleKeyFor(prereq, LocalDate.now()));
                QuestProgressRow prereqRow = rows.get(prereqKey);
                if (prereqRow == null || prereqRow.status() != QuestStatus.CLAIMED) {
                    return new ClaimOutcome(false, "Requires completing: " + prereq.title());
                }
            }
        }

        if (def.disciplineFilter() != null && def.requiredDisciplineLevel() > 1) {
            int lv = progressionService.getOrLoadSnapshot(playerUuid).get(def.disciplineFilter()).level();
            if (lv < def.requiredDisciplineLevel()) {
                return new ClaimOutcome(false, "Requires " + def.disciplineFilter().displayName()
                        + " Lv." + def.requiredDisciplineLevel() + " (currently Lv." + lv + ").");
            }
        }

        int initialProgress = computeImmediateProgress(playerUuid, def);
        questRepository.upsertProgress(playerUuid, def.id(), cycleKey, initialProgress, def.targetValue(),
                QuestStatus.ACTIVE, System.currentTimeMillis());
        return new ClaimOutcome(true, "Accepted quest: " + def.title());
    }

    /**
     * Event-driven progress incrementer across all active quests matching the event objective.
     */
    public void recordEvent(
            @NotNull UUID playerUuid,
            @NotNull QuestObjectiveType objectiveType,
            @Nullable CombatDiscipline disciplineContext,
            int increment,
            boolean setAbsoluteIfHigher
    ) {
        if (increment <= 0) {
            return;
        }
        try {
            LocalDate today = LocalDate.now();
            Map<String, QuestProgressRow> rows = questRepository.loadAll(playerUuid);
            long now = System.currentTimeMillis();

            for (QuestDefinition def : questRegistry.all()) {
                if (def.objectiveType() != objectiveType) {
                    continue;
                }
                if (def.targetItemOrKey() != null && objectiveType != QuestObjectiveType.COLLECTION_DELIVER) {
                    if (disciplineContext == null || !def.targetItemOrKey().equalsIgnoreCase(disciplineContext.name())) {
                        continue;
                    }
                }
                String cycleKey = QuestRegistry.cycleKeyFor(def, today);
                QuestProgressRow row = rows.get(QuestProgressRepository.key(def.id(), cycleKey));
                if (row == null || row.status() != QuestStatus.ACTIVE) {
                    continue;
                }
                int nextVal = setAbsoluteIfHigher
                        ? Math.max(row.progressValue(), increment)
                        : row.progressValue() + increment;
                QuestProgressRow updated = questRepository.upsertProgress(
                        playerUuid, def.id(), cycleKey, nextVal, def.targetValue(), QuestStatus.ACTIVE, now);
                if (updated.status() == QuestStatus.COMPLETED && row.status() != QuestStatus.COMPLETED) {
                    notifyQuestReady(playerUuid, def);
                }
            }
        } catch (SQLException ex) {
            logger.warn("Failed recording quest progress event {} for {}", objectiveType, playerUuid, ex);
        }
    }

    /**
     * Delivers collection quest items from a player's inventory into active collection quests.
     */
    @NotNull
    public ClaimOutcome deliverCollectionItems(@NotNull Player player, @NotNull String questId) throws SQLException {
        QuestDefinition def = questRegistry.get(questId);
        if (def == null || def.objectiveType() != QuestObjectiveType.COLLECTION_DELIVER || def.targetItemOrKey() == null) {
            return new ClaimOutcome(false, "Quest does not accept item deliveries.");
        }
        String cycleKey = QuestRegistry.cycleKeyFor(def, LocalDate.now());
        Map<String, QuestProgressRow> rows = questRepository.loadAll(player.getUniqueId());
        QuestProgressRow row = rows.get(QuestProgressRepository.key(def.id(), cycleKey));
        if (row == null || row.status() != QuestStatus.ACTIVE) {
            return new ClaimOutcome(false, "Quest must be ACTIVE before delivering materials.");
        }
        int needed = def.targetValue() - row.progressValue();
        if (needed <= 0) {
            return new ClaimOutcome(true, "Collection goal already complete! Claim your reward.");
        }
        int available = gearService.countCustomItem(player.getInventory(), def.targetItemOrKey());
        if (available <= 0) {
            return new ClaimOutcome(false, "You are not carrying any required materials (" + def.targetItemOrKey() + ").");
        }
        int deliver = Math.min(needed, available);
        gearService.consumeCustomItem(player.getInventory(), def.targetItemOrKey(), deliver);
        QuestProgressRow updated = questRepository.upsertProgress(
                player.getUniqueId(), def.id(), cycleKey, row.progressValue() + deliver, def.targetValue(),
                QuestStatus.ACTIVE, System.currentTimeMillis());
        if (updated.status() == QuestStatus.COMPLETED) {
            notifyQuestReady(player.getUniqueId(), def);
            return new ClaimOutcome(true, "Delivered " + deliver + "x items — Quest READY to claim!");
        }
        return new ClaimOutcome(true, "Delivered " + deliver + "x items (" + updated.progressValue() + "/" + def.targetValue() + ").");
    }

    /**
     * Force-completes a quest row for admin/console testing without bypassing atomic claim idempotency.
     */
    public void forceCompleteForTesting(@NotNull UUID playerUuid, @NotNull String questId) throws SQLException {
        QuestDefinition def = questRegistry.get(questId);
        if (def == null) {
            throw new IllegalArgumentException("Unknown quest: " + questId);
        }
        String cycleKey = QuestRegistry.cycleKeyFor(def, LocalDate.now());
        questRepository.upsertProgress(playerUuid, def.id(), cycleKey, def.targetValue(), def.targetValue(),
                QuestStatus.COMPLETED, System.currentTimeMillis());
    }

    /**
     * Authoritatively and idempotently claims a completed quest's XP, Coins, Reputation, Item, and Mastery rewards.
     */
    @NotNull
    public ClaimOutcome claimReward(@NotNull UUID playerUuid, @NotNull String questId) throws SQLException {
        QuestDefinition def = questRegistry.get(questId);
        if (def == null) {
            return new ClaimOutcome(false, "Unknown quest: " + questId);
        }

        // Check online inventory slot BEFORE claiming if reward includes an item
        Player online = Bukkit.getServer() != null ? Bukkit.getPlayer(playerUuid) : null;
        ItemStack rewardStack = null;
        if (def.itemRewardId() != null && def.itemRewardQty() > 0 && online != null && online.isOnline()) {
            if (online.getInventory().firstEmpty() == -1) {
                return new ClaimOutcome(false, "Free 1 inventory slot before claiming your item reward.");
            }
            rewardStack = itemService.createCustomItem(def.itemRewardId(), def.itemRewardQty()).orElse(null);
        }

        String cycleKey = QuestRegistry.cycleKeyFor(def, LocalDate.now());
        boolean wonLock = questRepository.tryClaimRewardAtomic(playerUuid, def.id(), cycleKey, System.currentTimeMillis());
        if (!wonLock) {
            return new ClaimOutcome(false, "Quest cannot be claimed (incomplete or already claimed).");
        }

        if (def.xpReward() > 0L && def.xpDiscipline() != null) {
            progressionService.awardXp(playerUuid, def.xpDiscipline(), def.xpReward(),
                    def.category().xpSource(), "quest:" + def.id());
        }
        if (def.coinReward() > 0L && economyProvider != null) {
            economyProvider.deposit(playerUuid, "coins", BigDecimal.valueOf(def.coinReward()),
                    "End Quest Reward: " + def.id());
        }
        if (def.reputationReward() > 0) {
            profileRepository.addReputation(playerUuid, def.reputationReward());
        }
        if (def.grantsMastery() && def.disciplineFilter() != null) {
            progressionService.markMasteryCompleted(playerUuid, def.disciplineFilter());
        }
        if (rewardStack != null && online != null && online.isOnline()) {
            online.getInventory().addItem(rewardStack);
        }

        if (online != null && online.isOnline()) {
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.15f);
            online.sendMessage(Component.text(
                    "✦ Claimed [" + def.title() + "] — +" + def.xpReward() + " XP, +"
                            + def.coinReward() + " Coins, +" + def.reputationReward() + " Rep"
                            + (def.grantsMastery() ? " ★ MASTERY UNLOCKED!" : ""),
                    NamedTextColor.GREEN, TextDecoration.BOLD
            ));
        }
        return new ClaimOutcome(true, "Claimed quest rewards for " + def.title() + ".");
    }

    @NotNull
    public NpcInteractionHandler questBoardNpcHandler() {
        return (player, npc) -> {
            try {
                List<QuestDefinition> quests = questRegistry.byBoardTag(npc.actionTarget());
                Map<String, QuestProgressRow> rows = questRepository.loadAll(player.getUniqueId());
                LocalDate today = LocalDate.now();

                player.sendMessage(Component.text("━━━ " + npc.displayName() + " Quests ━━━",
                        NamedTextColor.GOLD, TextDecoration.BOLD));
                if (quests.isEmpty()) {
                    player.sendMessage(Component.text("Use /endquest to view active End quests.", NamedTextColor.GRAY));
                    return;
                }
                for (QuestDefinition q : quests) {
                    String cycleKey = QuestRegistry.cycleKeyFor(q, today);
                    QuestProgressRow row = rows.get(QuestProgressRepository.key(q.id(), cycleKey));
                    QuestStatus status = row != null ? row.status() : QuestStatus.AVAILABLE;
                    int cur = row != null ? row.progressValue() : 0;

                    TextColor accent = TextColor.fromHexString(q.category().hexColor());
                    String badge = switch (status) {
                        case AVAILABLE -> "[ACCEPT]";
                        case ACTIVE -> "[" + cur + "/" + q.targetValue() + "]";
                        case COMPLETED -> "[CLAIM]";
                        case CLAIMED -> "[DONE]";
                    };
                    Component line = Component.text(" " + badge + " ", statusColor(status), TextDecoration.BOLD)
                            .append(Component.text(q.title(), accent, TextDecoration.BOLD))
                            .append(Component.text(" — " + q.description(), NamedTextColor.GRAY));
                    if (status == QuestStatus.AVAILABLE) {
                        line = line.clickEvent(ClickEvent.runCommand("/endquest accept " + q.id()))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to accept " + q.title(), NamedTextColor.YELLOW)));
                    } else if (status == QuestStatus.COMPLETED) {
                        line = line.clickEvent(ClickEvent.runCommand("/endquest claim " + q.id()))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to claim rewards", NamedTextColor.GREEN)));
                    } else if (status == QuestStatus.ACTIVE && q.objectiveType() == QuestObjectiveType.COLLECTION_DELIVER) {
                        line = line.clickEvent(ClickEvent.runCommand("/endquest deliver " + q.id()))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to deliver held items", NamedTextColor.AQUA)));
                    }
                    player.sendMessage(line);
                }
            } catch (SQLException ex) {
                player.sendMessage(Component.text("Unable to load quest log right now.", NamedTextColor.RED));
            }
        };
    }

    @NotNull
    public NpcInteractionHandler reputationNpcHandler() {
        return (player, npc) -> {
            try {
                EndPlayerProfile profile = profileRepository.loadOrCreate(player.getUniqueId());
                int rep = profile.reputation();
                String tier = reputationTierName(rep);
                player.sendMessage(Component.text("━━━ End Reputation Status ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
                player.sendMessage(Component.text("Current Reputation: " + rep + " (" + tier + ")", NamedTextColor.AQUA));
                player.sendMessage(Component.text("Districts Discovered: " + profile.discoveredDistricts().size() + "/7", NamedTextColor.GRAY));
            } catch (SQLException ex) {
                player.sendMessage(Component.text("Failed querying reputation.", NamedTextColor.RED));
            }
        };
    }

    @NotNull
    public static String reputationTierName(int reputation) {
        if (reputation >= 1500) return "Citadel Paragon";
        if (reputation >= 750) return "End Vindicator";
        if (reputation >= 300) return "Trusted Pathfinder";
        if (reputation >= 100) return "Sworn Defender";
        return "Citadel Initiate";
    }

    private int computeImmediateProgress(UUID playerUuid, QuestDefinition def) throws SQLException {
        if (def.objectiveType() == QuestObjectiveType.DISCOVER_DISTRICT) {
            return profileRepository.loadOrCreate(playerUuid).discoveredDistricts().size();
        }
        if (def.objectiveType() == QuestObjectiveType.DISCIPLINE_LEVEL) {
            PlayerDisciplineSnapshot snap = progressionService.getOrLoadSnapshot(playerUuid);
            int max = 1;
            for (CombatDiscipline d : CombatDiscipline.values()) {
                max = Math.max(max, snap.get(d).level());
            }
            return max;
        }
        return 0;
    }

    private static void notifyQuestReady(UUID playerUuid, QuestDefinition def) {
        Player player = Bukkit.getServer() != null ? Bukkit.getPlayer(playerUuid) : null;
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
            player.sendMessage(Component.text("✦ Quest Objective Complete: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text(def.title(), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" — Click here or run /endquest claim " + def.id(), NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.runCommand("/endquest claim " + def.id())));
        }
    }

    private static NamedTextColor statusColor(QuestStatus status) {
        return switch (status) {
            case AVAILABLE -> NamedTextColor.YELLOW;
            case ACTIVE -> NamedTextColor.AQUA;
            case COMPLETED -> NamedTextColor.GREEN;
            case CLAIMED -> NamedTextColor.DARK_GRAY;
        };
    }
}
