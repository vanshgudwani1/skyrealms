package com.skyrealms.farming.garden.mastery;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.farming.repository.FarmingRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
 * Authoritative mcMMO-driven implementation of CropMasteryService.
 * Tracks crop harvest counts, enforces mcMMO Farming (Herbalism) level gates,
 * awards tier milestone coin rewards via EconomyService, and persists to sr_crop_mastery.
 */
public final class DefaultCropMasteryService implements CropMasteryService {

    private final Plugin plugin;
    private final FarmingRepository repository;
    private final EconomyService economyService;
    private final McMMOFarmingService mcMmoService;
    private final GardenAbilityService abilityService;
    private final Logger logger;

    private final Map<UUID, Map<CropType, CropMastery>> cache = new ConcurrentHashMap<>();

    public DefaultCropMasteryService(
            @NotNull Plugin plugin,
            @NotNull FarmingRepository repository,
            @NotNull EconomyService economyService,
            @NotNull McMMOFarmingService mcMmoService,
            @NotNull GardenAbilityService abilityService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.mcMmoService = Objects.requireNonNull(mcMmoService, "mcMmoService cannot be null");
        this.abilityService = Objects.requireNonNull(abilityService, "abilityService cannot be null");
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
                        Math.min(CropMastery.MAX_TIER, rec.masteryLevel()),
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
    public void recordCropHarvest(@NotNull Player player, @NotNull CropType cropType, long harvestAmount, int mcMmoFarmingLevel) {
        if (harvestAmount <= 0) return;
        CropMastery mastery = getMastery(player.getUniqueId(), cropType);
        int oldTier = mastery.tier();
        mastery.recordHarvest(harvestAmount, mcMmoFarmingLevel);
        int newTier = mastery.tier();

        if (newTier > oldTier) {
            onTierUp(player, mastery, oldTier, newTier);
        }

        saveAsync(mastery);
    }

    @Override
    public void recheckAllMasteries(@NotNull Player player, int newMcMmoLevel) {
        for (CropType crop : CropType.values()) {
            CropMastery mastery = getMastery(player.getUniqueId(), crop);
            int oldTier = mastery.tier();
            mastery.recalculateTier(newMcMmoLevel);
            int newTier = mastery.tier();
            if (newTier > oldTier) {
                onTierUp(player, mastery, oldTier, newTier);
                saveAsync(mastery);
            }
        }
    }

    private void onTierUp(@NotNull Player player, @NotNull CropMastery mastery, int oldTier, int newTier) {
        CropType crop = mastery.cropType();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.2f, 1.15f);

        Title title = Title.title(
                Component.text("MASTERY TIER UP!", TextColor.fromHexString("#77DD77"), TextDecoration.BOLD),
                Component.text(crop.displayName() + " Tier " + newTier + " / " + CropMastery.MAX_TIER, NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1600), Duration.ofMillis(400))
        );
        player.showTitle(title);

        for (int t = 1; t <= newTier; t++) {
            if (!mastery.isMilestoneClaimed(t)) {
                claimMilestoneReward(player, crop, t);
            }
        }

        Component summary = Component.text("✦ ", NamedTextColor.GREEN)
                .append(Component.text(crop.displayName() + " Mastery ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("Tier " + oldTier + " -> " + newTier, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" (" + mastery.getCurrentTierTitle() + ")", NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text(
                        String.format("Bonus Drops: +%.0f%% | Farming XP: +%.0f%% (Click to view)",
                                mastery.getTotalDoubleDropBonus() * 100.0,
                                mastery.getTotalMcMmoXpMultiplier() * 100.0),
                        NamedTextColor.AQUA)))
                .clickEvent(ClickEvent.runCommand("/garden mastery"));
        player.sendMessage(summary);
    }

    @Override
    public boolean claimMilestoneReward(@NotNull Player player, @NotNull CropType cropType, int milestoneTier) {
        CropMastery mastery = getMastery(player.getUniqueId(), cropType);
        if (mastery.tier() < milestoneTier || mastery.isMilestoneClaimed(milestoneTier)) {
            return false;
        }

        MasteryMilestone target = null;
        for (MasteryMilestone m : MasteryMilestone.getMilestonesFor(cropType)) {
            if (m.tier() == milestoneTier) {
                target = m;
                break;
            }
        }
        if (target == null) return false;

        mastery.claimMilestone(milestoneTier);

        int coinReward = target.coinReward();
        if (coinReward > 0) {
            economyService.deposit(
                    player.getUniqueId(),
                    "coins",
                    BigDecimal.valueOf(coinReward),
                    "Crop Mastery Tier " + milestoneTier + " (" + cropType.name() + ")",
                    "mastery-t" + milestoneTier + "-" + cropType.name().toLowerCase() + "-" + player.getUniqueId()
            ).whenComplete((res, err) -> {
                if (err != null) {
                    logger.warning("Failed Crop Mastery coin deposit for " + player.getName() + ": " + err.getMessage());
                } else if (res != null && !res.isSuccess()) {
                    logger.warning("Crop Mastery coin deposit rejected for " + player.getName() + ": " + res.message());
                }
            });
        }

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.2f);
        player.sendMessage(Component.text("  ★ ", NamedTextColor.GOLD)
                .append(Component.text(target.title() + " Reward: ", NamedTextColor.YELLOW))
                .append(Component.text("+" + coinReward + " Coins", NamedTextColor.AQUA)));

        saveAsync(mastery);
        return true;
    }

    @Override
    public void openMasteryGui(@NotNull Player player) {
        CropMasteryGui.openOverview(player, this, mcMmoService, abilityService);
    }

    @Override
    public void openCropDetailGui(@NotNull Player player, @NotNull CropType cropType) {
        CropMasteryGui.openCropDetail(player, cropType, this, mcMmoService);
    }

    private void saveAsync(@NotNull CropMastery mastery) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repository.saveCropMastery(new FarmingRepository.CropMasteryRecord(
                        mastery.playerUuid(),
                        mastery.cropType().name(),
                        mastery.harvestCount(),
                        mastery.tier(),
                        mastery.encodeClaimedMilestones(),
                        Instant.now()
                ));
            } catch (SQLException e) {
                logger.severe("Failed to save crop mastery for " + mastery.playerUuid() + ": " + e.getMessage());
            }
        });
    }
}
