package com.skyrealms.economy.event;

import com.skyrealms.economy.model.TransactionType;
import com.skyrealms.platform.api.event.CancellableEvent;
import com.skyrealms.platform.api.event.SkyEvent;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published whenever an atomic economy transaction finishes successfully.
 * Event name: skyrealms.economy.transaction.completed.v1
 */
public final class TransactionCompletedEvent implements SkyEvent, CancellableEvent {

    public static final String EVENT_NAME = "skyrealms.economy.transaction.completed.v1";

    private final UUID transactionId;
    private final UUID ownerId;
    private final String currencyId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final Instant timestamp;
    private boolean cancelled = false;

    public TransactionCompletedEvent(
            @NotNull UUID transactionId,
            @NotNull UUID ownerId,
            @NotNull String currencyId,
            @NotNull TransactionType type,
            @NotNull BigDecimal amount,
            @NotNull BigDecimal balanceAfter
    ) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId cannot be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId cannot be null");
        this.currencyId = Objects.requireNonNull(currencyId, "currencyId cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.amount = Objects.requireNonNull(amount, "amount cannot be null");
        this.balanceAfter = Objects.requireNonNull(balanceAfter, "balanceAfter cannot be null");
        this.timestamp = Instant.now();
    }

    @Override
    @NotNull
    public String eventName() {
        return EVENT_NAME;
    }

    @Override
    @NotNull
    public Instant timestamp() {
        return timestamp;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @NotNull
    public UUID transactionId() {
        return transactionId;
    }

    @NotNull
    public UUID ownerId() {
        return ownerId;
    }

    @NotNull
    public String currencyId() {
        return currencyId;
    }

    @NotNull
    public TransactionType type() {
        return type;
    }

    @NotNull
    public BigDecimal amount() {
        return amount;
    }

    @NotNull
    public BigDecimal balanceAfter() {
        return balanceAfter;
    }
}
