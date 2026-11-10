package com.skyrealms.farming.garden.harvest;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.event.GardenCropHarvestEvent;
import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.garden.ability.VerdantAuraManager;
import com.skyrealms.farming.garden.journal.BotanicalJournalService;
import com.skyrealms.farming.garden.mastery.CropMastery;
import com.skyrealms.farming.garden.mastery.CropMasteryService;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.farming.service.FarmingProgressionService;
import com.skyrealms.platform.api.event.EventBus;
import com.skyrealms.platform.integrations.CustomEnchantProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Single authoritative Garden harvest reward pipeline.
 * Ensures every eligible crop harvest computes drops, bonus rolls, mcMMO XP,
 * Crop Mastery, collections, Botanical Journal progress, and Golden Sprout coins exactly once.
 */
public final class HarvestRewardPipeline {

    public record BlockClaimKey(String worldName, int x, int y, int z) {}

    private final GardenBalanceConfig config;
    private final GardenAbilityService abilityService;
    private final McMMOFarmingService mcMmoService;
    private final CropMasteryService masteryService;
    private final FarmingProgressionService progressionService;
    private final BotanicalJournalService journalService;
    private final EconomyService economyService;
    private final VerdantAuraManager auraManager;
    private final EventBus eventBus;
    private final Logger logger;

    // Active per-block harvest locks preventing concurrent or recursive duplicate processing
    private final Map<BlockClaimKey, UUID> activeBlockClaims = new ConcurrentHashMap<>();
    // Already-completed harvest IDs to guarantee idempotent execution
    private final Set<UUID> committedHarvestIds = ConcurrentHashMap.newKeySet();

    public HarvestRewardPipeline(
            @NotNull GardenBalanceConfig config,
            @NotNull GardenAbilityService abilityService,
            @Nullable McMMOFarmingService mcMmoService,
            @Nullable CropMasteryService masteryService,
            @Nullable FarmingProgressionService progressionService,
            @Nullable BotanicalJournalService journalService,
            @Nullable EconomyService economyService,
            @Nullable VerdantAuraManager auraManager,
            @Nullable EventBus eventBus,
            @Nullable Logger logger
    ) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.abilityService = Objects.requireNonNull(abilityService, "abilityService cannot be null");
        this.mcMmoService = mcMmoService;
        this.masteryService = masteryService;
        this.progressionService = progressionService;
        this.journalService = journalService;
        this.economyService = economyService;
        this.auraManager = auraManager;
        this.eventBus = eventBus;
        this.logger = logger != null ? logger : Logger.getLogger(HarvestRewardPipeline.class.getName());
    }

    /**
     * Pure, deterministic constructor for unit tests of calculatePlan() and block locking.
     */
    public static HarvestRewardPipeline forCalculationOnly(@NotNull GardenBalanceConfig config, @NotNull GardenAbilityService abilityService) {
        return new HarvestRewardPipeline(config, abilityService, null, null, null, null, null, null, null, null);
    }

    /**
     * Claims a block coordinate for exclusive harvest processing under the given harvestId.
     */
    public boolean tryClaimBlock(@NotNull String worldName, int x, int y, int z, @NotNull UUID harvestId) {
        BlockClaimKey key = new BlockClaimKey(worldName, x, y, z);
        return activeBlockClaims.putIfAbsent(key, harvestId) == null;
    }

    /**
     * Releases the harvest lock for the given block coordinate if owned by harvestId.
     */
    public void releaseBlock(@NotNull String worldName, int x, int y, int z, @NotNull UUID harvestId) {
        activeBlockClaims.remove(new BlockClaimKey(worldName, x, y, z), harvestId);
    }

    /**
     * Computes the authoritative reward plan for a single validated Garden crop harvest.
     */
    @NotNull
    public HarvestRewardPlan calculatePlan(@NotNull HarvestRewardContext ctx) {
        CropType crop = ctx.cropType();
        int farmingLevel = Math.max(0, ctx.farmingLevel());
        CropMastery mastery = ctx.cropMastery();

        // 1. Unified bonus drop chance (Bountiful Harvest + Crop Mastery + modifiers, clamped to cap)
        double bountifulChance = config.isBountifulEligible(crop)
                ? abilityService.getBountifulHarvestChance(farmingLevel)
                : 0.0;
        double masteryBonusChance = mastery.getTotalDoubleDropBonus();
        double combinedBonusChance = Math.min(
                config.maxCombinedBonusDropChance(),
                Math.max(0.0, bountifulChance + masteryBonusChance + ctx.extraDropChanceModifier())
        );

        boolean bonusDropTriggered = combinedBonusChance > 0.0 && ctx.bonusDropRoll() < combinedBonusChance;
        List<ItemStack> bonusDrops = new ArrayList<>();
        if (bonusDropTriggered && config.maxBonusDropsPerHarvest() > 0) {
            int remainingCap = config.maxBonusDropsPerHarvest();
            for (ItemStack base : ctx.baseDrops()) {
                if (base == null || base.getAmount() <= 0 || remainingCap <= 0) continue;
                int addQty = Math.min(base.getAmount(), remainingCap);
                ItemStack extra = base.clone();
                extra.setAmount(addQty);
                bonusDrops.add(extra);
                remainingCap -= addQty;
            }
        }

        // 2. Unified mcMMO Farming XP calculation (prevents double base XP on primary block break)
        float baseCustomMcMmoXp = ctx.primaryBlockBreak() ? 0.0f : (float) crop.baseMcMmoXp();
        double rawMultiplier = 1.0 + mastery.getTotalMcMmoXpMultiplier() + ctx.extraXpMultiplierModifier();
        double clampedMultiplier = Math.min(config.maxXpMultiplier(), Math.max(1.0, rawMultiplier));
        float bonusCustomMcMmoXp = (float) Math.max(0.0, crop.baseMcMmoXp() * (clampedMultiplier - 1.0));

        // 3. Controlled Golden Sprout calculation (capped per-harvest and per-day)
        double sproutChance = abilityService.getGoldenSproutChance(farmingLevel);
        boolean sproutTriggered = false;
        double approvedCoins = 0.0;
        if (sproutChance > 0.0 && ctx.goldenSproutRoll() < sproutChance) {
            double calculated = abilityService.calculateGoldenSproutCoins(crop, farmingLevel, mastery.tier());
            approvedCoins = abilityService.reserveDailyGoldenSproutCoins(ctx.playerUuid(), calculated);
            sproutTriggered = approvedCoins > 0.0;
        }

        return new HarvestRewardPlan(
                ctx.harvestId(),
                crop,
                ctx.baseDrops(),
                bonusDrops,
                combinedBonusChance,
                bonusDropTriggered,
                baseCustomMcMmoXp,
                bonusCustomMcMmoXp,
                clampedMultiplier,
                sproutTriggered,
                approvedCoins,
                1L,
                1L
        );
    }

    /**
     * Commits all calculated rewards atomically (drops, mcMMO XP, mastery, collections, journal, coins, aura, event).
     */
    public boolean commitHarvest(
            @NotNull Player player,
            @NotNull Block block,
            @NotNull Garden garden,
            @NotNull GardenPlot plot,
            @NotNull HarvestRewardPlan plan,
            int farmingLevel,
            boolean sendFeedback
    ) {
        if (!committedHarvestIds.add(plan.harvestId())) {
            return false;
        }
        // Bound memory of committed IDs cleanly
        if (committedHarvestIds.size() > 10_000) {
            committedHarvestIds.clear();
            committedHarvestIds.add(plan.harvestId());
        }

        CropType crop = plan.cropType();
        Location dropLoc = block.getLocation().add(0.5, 0.3, 0.5);

        // 1. Deliver or spawn bonus drops (routing directly to inventory when Telekinesis is active)
        CustomEnchantProvider enchantProvider = (Bukkit.getServer() != null)
                ? Bukkit.getServicesManager().load(CustomEnchantProvider.class)
                : null;
        boolean directToInv = enchantProvider != null
                && enchantProvider.hasTelekinesis(player.getInventory().getItemInMainHand());

        if (enchantProvider != null) {
            enchantProvider.deliverOrDrop(player, dropLoc, plan.bonusDrops(), directToInv);
        } else {
            for (ItemStack bonus : plan.bonusDrops()) {
                if (bonus != null && bonus.getAmount() > 0) {
                    block.getWorld().dropItemNaturally(dropLoc, bonus.clone());
                }
            }
        }

        // 2. Award custom mcMMO Farming XP once
        if (mcMmoService != null && plan.totalCustomMcMmoXp() > 0f) {
            mcMmoService.addFarmingXp(player, plan.totalCustomMcMmoXp());
        }

        // 3. Record Crop Mastery harvest once
        CropMastery updatedMastery = null;
        if (masteryService != null) {
            masteryService.recordCropHarvest(player, crop, plan.masteryIncrement(), farmingLevel);
            updatedMastery = masteryService.getMastery(player.getUniqueId(), crop);
        }

        // 4. Increment legacy/permanent Crop Collection once
        if (progressionService != null) {
            progressionService.recordCropHarvest(player, crop.naturalCropId(), plan.collectionIncrement());
        }

        // 5. Update Botanical Journal once
        if (journalService != null && updatedMastery != null) {
            journalService.recordHarvest(player, crop, plan.totalDropItemCount(), updatedMastery);
        }

        // 6. Deposit Golden Sprout coins via EconomyService once
        if (economyService != null && plan.goldenSproutTriggered() && plan.goldenSproutCoins() > 0.0) {
            double payout = plan.goldenSproutCoins();
            economyService.deposit(
                    player.getUniqueId(),
                    "coins",
                    BigDecimal.valueOf(payout),
                    "Garden Ability: Golden Sprout (" + crop.name() + ")",
                    "sprout-" + plan.harvestId()
            ).whenComplete((res, err) -> {
                if (err != null) {
                    logger.warning("Golden Sprout deposit failed for " + player.getName() + ": " + err.getMessage());
                } else if (res != null && !res.isSuccess()) {
                    logger.warning("Golden Sprout deposit rejected for " + player.getName() + ": " + res.message());
                } else if (payout >= config.goldenSproutLogThreshold()) {
                    logger.fine("[GoldenSprout] Exceptional payout of " + payout + " coins to " + player.getName()
                            + " (crop=" + crop.name() + ", harvestId=" + plan.harvestId() + ")");
                }
            });
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
        }

        // 7. Refresh Verdant Aura
        if (auraManager != null) {
            auraManager.refreshAura(player, farmingLevel, true);
        }

        // 8. Dispatch single GardenCropHarvestEvent
        if (eventBus != null) {
            eventBus.publish(new GardenCropHarvestEvent(
                    player, garden, plot, crop.naturalCropId(), plan.baseDrops(), Math.round(plan.totalCustomMcMmoXp())
            ));
        }

        // 9. Concise action-bar feedback
        if (sendFeedback && updatedMastery != null) {
            Component bar = Component.text("Farming Lv." + farmingLevel, TextColor.fromHexString("#77DD77"))
                    .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(crop.displayName() + " Tier " + updatedMastery.tier() + "/" + CropMastery.MAX_TIER
                            + " (" + updatedMastery.harvestCount() + ")", NamedTextColor.GOLD));

            if (abilityService.isSurgeActive(player.getUniqueId())) {
                bar = bar.append(Component.text(" [SURGE]", NamedTextColor.LIGHT_PURPLE));
            }
            if (plan.bonusDropTriggered()) {
                bar = bar.append(Component.text(" [BONUS DROP!]", NamedTextColor.AQUA));
            }
            if (plan.goldenSproutTriggered()) {
                bar = bar.append(Component.text(String.format(" [+%.0f Coins]", plan.goldenSproutCoins()), NamedTextColor.YELLOW));
            }
            player.sendActionBar(bar);
        }

        return true;
    }
}

