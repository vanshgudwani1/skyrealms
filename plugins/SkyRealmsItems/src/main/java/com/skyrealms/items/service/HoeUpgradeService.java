package com.skyrealms.items.service;

import com.skyrealms.items.catalog.ItemCatalog;
import com.skyrealms.items.factory.SkyRealmsItemFactory;
import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.platform.integrations.EconomyProvider;
import com.skyrealms.platform.integrations.SkyItemStats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages upgrade progression, tier evolution, and stat scaling for SkyRealms Farming Hoes.
 * Preserves all EliteEnchantments custom enchant data and lore during upgrades and evolutions.
 */
public final class HoeUpgradeService {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL_PER_TIER = 5;
    public static final double BONUS_PER_LEVEL = 0.25;
    public static final String CURRENCY_ID = "coins";

    private static final long[] SPROUT_UPGRADE_COSTS = {1_000L, 2_500L, 5_000L, 10_000L};
    private static final long SPROUT_EVOLVE_COST = 25_000L;

    private static final long[] SAKURA_UPGRADE_COSTS = {15_000L, 30_000L, 60_000L, 100_000L};
    private static final long SAKURA_EVOLVE_COST = 250_000L;

    private static final long[] VERDANT_UPGRADE_COSTS = {150_000L, 300_000L, 500_000L, 1_000_000L};

    public record UpgradeStep(
            @NotNull CustomItemDefinition targetDefinition,
            int targetLevel,
            long coinCost,
            boolean isEvolution
    ) {
    }

    public record UpgradeAttemptResult(
            boolean success,
            @NotNull String message,
            @Nullable UpgradeStep appliedStep
    ) {
        public static UpgradeAttemptResult ok(String message, UpgradeStep step) {
            return new UpgradeAttemptResult(true, message, step);
        }

        public static UpgradeAttemptResult fail(String message) {
            return new UpgradeAttemptResult(false, message, null);
        }
    }

    private final ItemCatalog catalog;
    private final SkyRealmsItemFactory factory;

    public HoeUpgradeService(@NotNull ItemCatalog catalog, @NotNull SkyRealmsItemFactory factory) {
        this.catalog = Objects.requireNonNull(catalog, "catalog cannot be null");
        this.factory = Objects.requireNonNull(factory, "factory cannot be null");
    }

    public static int clampLevel(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL_PER_TIER, level));
    }

    public static double multiplierForLevel(int level) {
        return 1.0 + (clampLevel(level) - 1) * BONUS_PER_LEVEL;
    }

    /**
     * Scales farming stats by upgrade level while preserving tier movement speed.
     */
    @NotNull
    public static SkyItemStats scaleStats(@NotNull CustomItemDefinition def, int level) {
        Objects.requireNonNull(def, "def cannot be null");
        double mult = multiplierForLevel(level);
        SkyItemStats base = def.stats();
        return SkyItemStats.builder()
                .farmingFortune(base.farmingFortune() * mult)
                .farmingXpBonus(base.farmingXpBonus() * mult)
                .miningFortune(base.miningFortune())
                .miningSpeed(base.miningSpeed())
                .foragingFortune(base.foragingFortune())
                .fishingLuck(base.fishingLuck())
                .combatDamage(base.combatDamage())
                .slayerDamageBonus(base.slayerDamageBonus())
                .maxHealthBonus(base.maxHealthBonus())
                .defense(base.defense())
                .speedBonus(base.speedBonus())
                .build();
    }

    @NotNull
    public Optional<UpgradeStep> nextUpgrade(@NotNull CustomItemDefinition currentDef, int currentLevel) {
        Objects.requireNonNull(currentDef, "currentDef cannot be null");
        int clamped = clampLevel(currentLevel);
        String id = currentDef.id();

        if (clamped < MAX_LEVEL_PER_TIER) {
            long cost = switch (id) {
                case ItemCatalog.SPROUT_HOE -> SPROUT_UPGRADE_COSTS[clamped - 1];
                case ItemCatalog.SAKURA_SCYTHE -> SAKURA_UPGRADE_COSTS[clamped - 1];
                case ItemCatalog.VERDANT_REAPER -> VERDANT_UPGRADE_COSTS[clamped - 1];
                default -> -1L;
            };
            if (cost < 0L) {
                return Optional.empty();
            }
            return Optional.of(new UpgradeStep(currentDef, clamped + 1, cost, false));
        }

        return switch (id) {
            case ItemCatalog.SPROUT_HOE -> {
                CustomItemDefinition nextTier = catalog.find(ItemCatalog.SAKURA_SCYTHE);
                yield nextTier != null
                        ? Optional.of(new UpgradeStep(nextTier, MIN_LEVEL, SPROUT_EVOLVE_COST, true))
                        : Optional.empty();
            }
            case ItemCatalog.SAKURA_SCYTHE -> {
                CustomItemDefinition nextTier = catalog.find(ItemCatalog.VERDANT_REAPER);
                yield nextTier != null
                        ? Optional.of(new UpgradeStep(nextTier, MIN_LEVEL, SAKURA_EVOLVE_COST, true))
                        : Optional.empty();
            }
            default -> Optional.empty();
        };
    }

    @NotNull
    public Optional<UpgradeStep> inspectNextUpgrade(@Nullable ItemStack stack) {
        Optional<CustomItemDefinition> defOpt = factory.readDefinition(stack);
        if (defOpt.isEmpty()) {
            return Optional.empty();
        }
        int level = factory.readItemLevel(stack);
        return nextUpgrade(defOpt.get(), level);
    }

    @NotNull
    public UpgradeAttemptResult upgradeHeldHoe(@NotNull Player player, @Nullable ItemStack stack, boolean bypassCost) {
        Objects.requireNonNull(player, "player cannot be null");
        Optional<CustomItemDefinition> defOpt = factory.readDefinition(stack);
        if (defOpt.isEmpty() || stack == null) {
            return UpgradeAttemptResult.fail("Hold one of the 3 SkyRealms Farming Hoes in your main hand to upgrade.");
        }
        if (stack.getAmount() > 1) {
            return UpgradeAttemptResult.fail("Hold only a single hoe to upgrade it.");
        }

        CustomItemDefinition currentDef = defOpt.get();
        int currentLevel = factory.readItemLevel(stack);
        Optional<UpgradeStep> stepOpt = nextUpgrade(currentDef, currentLevel);
        if (stepOpt.isEmpty()) {
            return UpgradeAttemptResult.fail(currentDef.displayName() + " is already at Maximum Tier & Level (Lv." + MAX_LEVEL_PER_TIER + ")!");
        }

        UpgradeStep step = stepOpt.get();
        if (!bypassCost) {
            EconomyProvider economy = Bukkit.getServicesManager().load(EconomyProvider.class);
            if (economy == null) {
                return UpgradeAttemptResult.fail("Economy service is unavailable right now.");
            }
            BigDecimal cost = BigDecimal.valueOf(step.coinCost());
            if (!economy.has(player.getUniqueId(), CURRENCY_ID, cost)) {
                BigDecimal balance = economy.getBalance(player.getUniqueId(), CURRENCY_ID);
                return UpgradeAttemptResult.fail(String.format("Not enough coins! Need %,d coins (you have %,.0f).",
                        step.coinCost(), balance.doubleValue()));
            }
            economy.withdraw(player.getUniqueId(), CURRENCY_ID, cost,
                    (step.isEvolution() ? "Evolve Hoe -> " : "Upgrade Hoe -> ")
                            + step.targetDefinition().id() + " Lv." + step.targetLevel());
        }

        ItemStack upgraded = factory.upgradeItem(stack, step.targetDefinition(), step.targetLevel());
        player.getInventory().setItemInMainHand(upgraded);
        String verb = step.isEvolution() ? "Evolved into" : "Upgraded to";
        return UpgradeAttemptResult.ok(
                verb + " " + step.targetDefinition().displayName() + " [Lv." + step.targetLevel() + "]!",
                step
        );
    }
}

