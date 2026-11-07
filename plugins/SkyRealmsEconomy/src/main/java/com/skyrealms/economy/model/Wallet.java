package com.skyrealms.economy.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a persistent currency account balance for a player.
 */
public record Wallet(
        @NotNull UUID ownerId,
        @NotNull String currencyId,
        @NotNull BigDecimal balance,
        @NotNull Instant updatedAt
) {
    public Wallet {
        Objects.requireNonNull(ownerId, "ownerId cannot be null");
        Objects.requireNonNull(currencyId, "currencyId cannot be null");
        Objects.requireNonNull(balance, "balance cannot be null");
        Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }

    public boolean has(@NotNull BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }
}
