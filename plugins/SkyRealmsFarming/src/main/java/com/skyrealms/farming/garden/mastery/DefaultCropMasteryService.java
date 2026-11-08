package com.skyrealms.farming.garden.mastery;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.repository.FarmingRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Authoritative implementation of CropMasteryService.
 * Handles XP accumulation across 30 levels, milestone reward disbursement via EconomyService,
 * and asynchronous persistence to the database.
 */
public final class DefaultCropMasteryService implements CropMasteryService {

    private final Plugin plugin;
    private final FarmingRepository repository;
    private final EconomyService economyService;
    private final Logger logger;

    // Cache: Player UUID -> Map of CropType -> CropMastery
    private final Map<UUID, Map<CropType, CropMastery>> cache = new ConcurrentHashMap<>();

    public DefaultCropMasteryService(
            @NotNull Plugin plugin,
            @NotNull FarmingRepository repository,
            @NotNull EconomyService economyService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.logger = plugin.getLogger();
    }

    @Override
    @NotNull
    public CropMastery getMastery(@NotNull UUID playerUuid, @NotNull CropType cropType) {
        Map<CropType, CropMastery> playerMap = cache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
        CropMastery cached = playerMap.get(cropType);
        if (cached != null) {
            return cached;
        }

        try {
            Optional<FarmingRepository.CropMasteryRecord> opt = repository.findCropMastery(playerUuid, cropType.name());
            if (opt.isPresent()) {
                FarmingRepository.CropMasteryRecord rec = opt.get();
                CropMastery mastery = new CropMastery(
                        rec.playerUuid(),
                        cropType,
                        rec.masteryXp(),
                        rec.masteryLevel(),
                        CropMastery.decodeClaimedMilestones(rec.claimedMilestones() != null ? rec.claimedMilestones() : "")
                );
                playerMap.put(cropType, mastery);
                return mastery;
            }
        } catch (SQLException e) {
            logger.warning("Failed to load crop mastery for " + playerUuid + " / " + cropType + ": " + e.getMessage());
        }

        CropMastery fresh = new CropMastery(playerUuid, cropType, 0L, 0, new HashSet<>());
        playerMap.put(cropType, fresh);
        return fresh;
    }

    @Override
    @NotNull
    public List<CropMastery> getAllMasteries(@NotNull UUID playerUuid) {
        List<CropMastery> list = new ArrayList<>();
        for (CropType type : CropType.values()) {
            list.add(getMastery(playerUuid, type));
        }
        return list;
    }

    @Override
    public void awardMasteryXp(@NotNull Player player, @NotNull CropType cropType, long xp) {
        if (xp <= 0) return;
        UUID uuid = player.getUniqueId();
        CropMastery mastery = getMastery(uuid, cropType);

        int oldLevel = mastery.level();
        mastery.addXp(xp);
        int newLevel = mastery.level();

        // Check for level-up
        if (newLevel > oldLevel) {
            onLevelUp(player, mastery, oldLevel, newLevel);
        }

        // Save asynchronously
        saveAsync(mastery);
    }

    private void onLevelUp(@NotNull Player player, @NotNull CropMastery mastery, int oldLevel, int newLevel) {
        CropType crop = mastery.cropType();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.1f);

        Title title = Title.title(
                Component.text("✦ MASTERY LEVEL UP! ✦", TextColor.fromHexString("#77DD77"), TextDecoration.BOLD),
                Component.text(crop.displayName() + " Mastery Level " + newLevel + " / 30", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(800))
        );
        player.showTitle(title);

        Component levelMsg = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.LIGHT_PURPLE, TextDecoration.STRIKETHROUGH)
                .append(Component.newline())
                .append(Component.text("🌟 CROP MASTERY LEVEL UP! ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("[" + crop.displayName() + " " + oldLevel + " ➜ " + newLevel + "]", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("Current Double Drop Bonus: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("+%.1f%%", mastery.getTotalDoubleDropBonus() * 100.0), NamedTextColor.AQUA, TextDecoration.BOLD));

        // Check if any milestone was reached
        for (int mLevel : MasteryMilestone.MILESTONE_LEVELS) {
            if (newLevel >= mLevel && !mastery.isMilestoneClaimed(mLevel)) {
                claimMilestoneReward(player, crop, mLevel);
            }
        }

        levelMsg = levelMsg.append(Component.newline())
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.LIGHT_PURPLE, TextDecoration.STRIKETHROUGH));

        player.sendMessage(levelMsg);
    }

    @Override
    public boolean claimMilestoneReward(@NotNull Player player, @NotNull CropType cropType, int milestoneLevel) {
        CropMastery mastery = getMastery(player.getUniqueId(), cropType);
        if (mastery.level() < milestoneLevel || mastery.isMilestoneClaimed(milestoneLevel)) {
            return false;
        }

        // Find milestone definition
        MasteryMilestone targetMilestone = null;
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (m.level() == milestoneLevel) {
                targetMilestone = m;
                break;
            }
        }
        if (targetMilestone == null) return false;

        mastery.claimMilestone(milestoneLevel);

        // Award bonus coins via EconomyService
        int bonusCoins = targetMilestone.bonusCoinsPerHarvest();
        if (bonusCoins > 0) {
            economyService.deposit(
                    player.getUniqueId(),
                    "coins",
                    BigDecimal.valueOf(bonusCoins),
                    "Crop Mastery Milestone L" + milestoneLevel + " (" + cropType.name() + ")",
                    "mastery-" + cropType.name().toLowerCase() + "-" + milestoneLevel + "-" + player.getUniqueId()
            );
        }

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.2f);
        player.sendMessage(Component.text("🏆 Milestone Unlocked: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(targetMilestone.title() + " (Level " + milestoneLevel + ")! ", NamedTextColor.YELLOW))
                .append(Component.text(targetMilestone.description() + " [+" + bonusCoins + " Coins]", NamedTextColor.AQUA)));

        saveAsync(mastery);
        return true;
    }

    @Override
    public void openMasteryGui(@NotNull Player player) {
        CropMasteryGui.openOverview(player, this);
    }

    @Override
    public void openCropDetailGui(@NotNull Player player, @NotNull CropType cropType) {
        CropMasteryGui.openCropDetail(player, cropType, this);
    }

    private void saveAsync(@NotNull CropMastery mastery) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.saveCropMastery(new FarmingRepository.CropMasteryRecord(
                        mastery.playerUuid(),
                        mastery.cropType().name(),
                        mastery.masteryXp(),
                        mastery.level(),
                        mastery.encodeClaimedMilestones(),
                        Instant.now()
                ));
            } catch (SQLException e) {
                logger.severe("Failed to save crop mastery for " + mastery.playerUuid() + ": " + e.getMessage());
            }
        });
    }
}
