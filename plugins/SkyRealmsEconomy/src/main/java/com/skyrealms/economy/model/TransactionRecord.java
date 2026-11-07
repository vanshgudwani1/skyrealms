package com.skyrealms.economy.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted transaction header and associated journal entries.
 */
public record TransactionRecord(
        @NotNull UUID id,
        @Nullable String idempotencyKey,
        @NotNull TransactionType type,
        @NotNull String description,
        @NotNull Instant createdAt,
        @NotNull List<TransactionEntry> entries
) {
    public TransactionRecord {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(entries, "entries cannot be null");
    }
}
