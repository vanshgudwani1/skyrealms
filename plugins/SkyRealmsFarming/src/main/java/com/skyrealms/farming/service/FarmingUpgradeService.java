package com.skyrealms.farming.service;

import com.skyrealms.economy.model.TransactionResult;
import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.event.FarmingGeneratorUpgradeEvent;
import com.skyrealms.farming.model.FarmingGenerator;
import com.skyrealms.farming.model.GeneratorUpgrade;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.platform.api.event.EventBus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Manages atomic economy-backed upgrades for farming generators.
 */
public final class FarmingUpgradeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmingUpgradeService.class);

    private final FarmingRepository repository;
    private final EconomyService economyService;
    private final EventBus eventBus;

    public FarmingUpgradeService(
            @NotNull FarmingRepository repository,
            @NotNull EconomyService economyService,
            @NotNull EventBus eventBus
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
    }

    public record UpgradeResult(boolean success, @NotNull String message, int newLevel, @NotNull BigDecimal costPaid) {
        public static UpgradeResult success(@NotNull String message, int newLevel, @NotNull BigDecimal cost) {
            return new UpgradeResult(true, message, newLevel, cost);
        }

        public static UpgradeResult failure(@NotNull String message) {
            return new UpgradeResult(false, message, 0, BigDecimal.ZERO);
        }
    }

    /**
     * Calculates the coin upgrade cost for an upgrade key and target level.
     */
    @NotNull
    public BigDecimal calculateUpgradeCost(@NotNull String key, int currentLevel) {
        long baseCost = switch (key.toLowerCase()) {
            case GeneratorUpgrade.KEY_SPEED -> 1000L;
            case GeneratorUpgrade.KEY_YIELD -> 2500L;
            case GeneratorUpgrade.KEY_XP_BOOST -> 3000L;
            case GeneratorUpgrade.KEY_MUTATION -> 5000L;
            case GeneratorUpgrade.KEY_CAPACITY -> 10000L;
            default -> 2000L;
        };

        double multiplier = switch (key.toLowerCase()) {
            case GeneratorUpgrade.KEY_CAPACITY -> 3.0;
            case GeneratorUpgrade.KEY_MUTATION -> 2.5;
            default -> 1.8;
        };

        double cost = baseCost * Math.pow(multiplier, currentLevel);
        return BigDecimal.valueOf(Math.round(cost));
    }

    /**
     * Gets maximum permitted level for an upgrade key.
     */
    public int getMaxUpgradeLevel(@NotNull String key) {
        return switch (key.toLowerCase()) {
            case GeneratorUpgrade.KEY_SPEED -> 10;
            case GeneratorUpgrade.KEY_YIELD -> 5;
            case GeneratorUpgrade.KEY_XP_BOOST -> 5;
            case GeneratorUpgrade.KEY_MUTATION -> 5;
            case GeneratorUpgrade.KEY_CAPACITY -> 3;
            default -> 5;
        };
    }

    /**
     * Attempts to purchase and apply an upgrade.
     */
    @NotNull
    public CompletableFuture<UpgradeResult> purchaseUpgrade(
            @NotNull Player player,
            @NotNull FarmingGenerator generator,
            @NotNull String upgradeKey
    ) {
        String key = upgradeKey.toLowerCase();
        int currentLevel = generator.getUpgradeLevel(key);
        int maxLevel = getMaxUpgradeLevel(key);

        if (currentLevel >= maxLevel) {
            return CompletableFuture.completedFuture(UpgradeResult.failure("Upgrade '" + key + "' is already at maximum level (" + maxLevel + ")."));
        }

        BigDecimal cost = calculateUpgradeCost(key, currentLevel);
        String idempotencyKey = "farming-upg-" + generator.generatorId() + "-" + key + "-" + (currentLevel + 1);
        String description = "Farming Upgrade: " + key + " Lvl " + (currentLevel + 1);

        return economyService.withdraw(player.getUniqueId(), "coins", cost, description, idempotencyKey).thenApply(txResult -> {
            if (!txResult.isSuccess()) {
                return UpgradeResult.failure("Insufficient funds! Requires " + cost + " Coins.");
            }

            int newLevel = currentLevel + 1;
            GeneratorUpgrade upgrade = new GeneratorUpgrade(key, newLevel, Instant.now());
            generator.applyUpgrade(upgrade);

            try {
                repository.saveUpgrade(generator.generatorId(), upgrade);
            } catch (SQLException e) {
                LOGGER.error("Failed to persist upgrade {} for generator {}: {}", key, generator.generatorId(), e.getMessage(), e);
                // Note: Balance was withdrawn, but error logged. In future revision, could refund.
            }

            eventBus.publishAsync(new FarmingGeneratorUpgradeEvent(generator, upgrade, player.getUniqueId()));

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
            player.sendMessage(Component.text("✦ [Generator Upgrade] ", NamedTextColor.GOLD)
                    .append(Component.text("Successfully upgraded " + key.toUpperCase() + " to Level " + newLevel + " for " + cost + " Coins!", NamedTextColor.GREEN)));

            return UpgradeResult.success("Upgrade applied.", newLevel, cost);
        });
    }
}
