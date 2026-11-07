package com.skyrealms.economy.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of an atomic economy mutation.
 */
public record TransactionResult(
        @NotNull Status status,
        @Nullable UUID transactionId,
        @NotNull BigDecimal balanceAfter,
        @NotNull String message
) {
    public enum Status {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        CURRENCY_NOT_FOUND,
        WALLET_NOT_FOUND,
        DUPLICATE_IDEMPOTENCY,
        INVALID_AMOUNT,
        NOT_TRANSFERABLE,
        ERROR
    }

    public TransactionResult {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.DUPLICATE_IDEMPOTENCY;
    }

    @NotNull
    public static TransactionResult success(@NotNull UUID transactionId, @NotNull BigDecimal balanceAfter, @NotNull String message) {
        return new TransactionResult(Status.SUCCESS, transactionId, balanceAfter, message);
    }

    @NotNull
    public static TransactionResult duplicate(@NotNull UUID transactionId, @NotNull BigDecimal balanceAfter) {
        return new TransactionResult(Status.DUPLICATE_IDEMPOTENCY, transactionId, balanceAfter, "Transaction already executed (idempotent).");
    }

    @NotNull
    public static TransactionResult failure(@NotNull Status status, @NotNull BigDecimal currentBalance, @NotNull String message) {
        return new TransactionResult(status, null, currentBalance, message);
    }
}
