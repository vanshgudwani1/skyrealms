package com.skyrealms.economy.model;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Single entry within a double-entry transaction record.
 */
public record TransactionEntry(
        @NotNull UUID ownerId,
        @NotNull String currencyId,
        @NotNull BigDecimal amount,
        @NotNull BigDecimal balanceAfter
) {
    public TransactionEntry {
        Objects.requireNonNull(ownerId, "ownerId cannot be null");
        Objects.requireNonNull(currencyId, "currencyId cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter cannot be null");
    }
}
