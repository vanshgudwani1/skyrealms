package com.skyrealms.economy.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable definition of a SkyRealms currency.
 */
public record Currency(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull String symbol,
        int decimalPlaces,
        @NotNull BigDecimal defaultBalance,
        boolean transferable
) {
    public Currency {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(symbol, "symbol cannot be null");
        Objects.requireNonNull(defaultBalance, "defaultBalance cannot be null");
    }

    /**
     * Standardized formatted string, e.g. "⛃1,250.50 Coins" or "✦50 Gems".
     */
    @NotNull
    public String format(@NotNull BigDecimal amount) {
        BigDecimal scaled = amount.setScale(decimalPlaces, RoundingMode.HALF_UP);
        return String.format("%s%,." + decimalPlaces + "f", symbol, scaled);
    }
}
