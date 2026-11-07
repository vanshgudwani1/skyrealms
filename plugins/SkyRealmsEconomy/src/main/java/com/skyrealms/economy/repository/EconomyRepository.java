package com.skyrealms.economy.repository;

import com.skyrealms.economy.model.Currency;
import com.skyrealms.economy.model.TransactionEntry;
import com.skyrealms.economy.model.TransactionRecord;
import com.skyrealms.economy.model.TransactionResult;
import com.skyrealms.economy.model.TransactionType;
import com.skyrealms.economy.model.Wallet;
import com.skyrealms.platform.database.DatabaseService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles authoritative SQL operations for currencies, wallets, transactions,
 * double-entry journals, idempotency keys, and audit logging.
 */
public final class EconomyRepository {

    private final DatabaseService databaseService;

    public EconomyRepository(@NotNull DatabaseService databaseService) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService cannot be null");
    }

    /**
     * Loads all active currency configurations from sr_currencies.
     */
    @NotNull
    public Map<String, Currency> loadCurrencies() throws SQLException {
        String sql = "SELECT id, display_name, symbol, decimal_places, default_balance, transferable FROM sr_currencies";
        Map<String, Currency> result = new HashMap<>();

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Currency currency = new Currency(
                        rs.getString("id"),
                        rs.getString("display_name"),
                        rs.getString("symbol"),
                        rs.getInt("decimal_places"),
                        rs.getBigDecimal("default_balance"),
                        rs.getBoolean("transferable")
                );
                result.put(currency.id(), currency);
            }
        }
        return result;
    }

    /**
     * Retrieves or initializes a player's wallet for a specific currency.
     */
    @NotNull
    public Wallet getOrCreateWallet(@NotNull UUID ownerId, @NotNull Currency currency) throws SQLException {
        return databaseService.executeTransaction(conn -> {
            Wallet existing = findWallet(conn, ownerId, currency.id());
            if (existing != null) {
                return existing;
            }

            // Create wallet with default starting balance
            String insert = """
                INSERT INTO sr_wallets (owner_id, currency_id, balance, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setObject(1, ownerId);
                ps.setString(2, currency.id());
                ps.setBigDecimal(3, currency.defaultBalance());
                try {
                    ps.executeUpdate();
                } catch (SQLException ignored) {
                    // Handled if concurrent insert occurred
                }
            }

            Wallet created = findWallet(conn, ownerId, currency.id());
            if (created != null) {
                return created;
            }
            return new Wallet(ownerId, currency.id(), currency.defaultBalance(), Instant.now());
        });
    }

    /**
     * Retrieves all existing wallets for a given player.
     */
    @NotNull
    public Map<String, BigDecimal> getBalances(@NotNull UUID ownerId) throws SQLException {
        String sql = "SELECT currency_id, balance FROM sr_wallets WHERE owner_id = ?";
        Map<String, BigDecimal> balances = new HashMap<>();

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    balances.put(rs.getString("currency_id"), rs.getBigDecimal("balance"));
                }
            }
        }
        return balances;
    }

    /**
     * Atomically executes a deposit with double-entry ledger journaling and idempotency.
     */
    @NotNull
    public TransactionResult deposit(
            @NotNull UUID ownerId,
            @NotNull Currency currency,
            @NotNull BigDecimal amount,
            @NotNull String description,
            @Nullable String idempotencyKey
    ) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return TransactionResult.failure(TransactionResult.Status.INVALID_AMOUNT, BigDecimal.ZERO, "Deposit amount must be positive.");
        }

        return databaseService.executeTransaction(conn -> {
            if (idempotencyKey != null) {
                TransactionResult duplicate = checkIdempotency(conn, idempotencyKey, ownerId, currency.id());
                if (duplicate != null) {
                    return duplicate;
                }
            }

            BigDecimal current = getLockedBalanceOrCreate(conn, ownerId, currency);
            BigDecimal newBalance = current.add(amount);

            UUID txId = UUID.randomUUID();
            updateWalletBalance(conn, ownerId, currency.id(), newBalance);
            insertTransactionHeader(conn, txId, idempotencyKey, TransactionType.DEPOSIT, description);
            insertTransactionEntry(conn, txId, ownerId, currency.id(), amount, newBalance);

            if (idempotencyKey != null) {
                insertIdempotencyKey(conn, idempotencyKey, txId);
            }

            return TransactionResult.success(txId, newBalance, "Deposit of " + currency.format(amount) + " succeeded.");
        });
    }

    /**
     * Atomically executes a withdrawal with double-entry ledger journaling and idempotency.
     */
    @NotNull
    public TransactionResult withdraw(
            @NotNull UUID ownerId,
            @NotNull Currency currency,
            @NotNull BigDecimal amount,
            @NotNull String description,
            @Nullable String idempotencyKey
    ) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return TransactionResult.failure(TransactionResult.Status.INVALID_AMOUNT, BigDecimal.ZERO, "Withdrawal amount must be positive.");
        }

        return databaseService.executeTransaction(conn -> {
            if (idempotencyKey != null) {
                TransactionResult duplicate = checkIdempotency(conn, idempotencyKey, ownerId, currency.id());
                if (duplicate != null) {
                    return duplicate;
                }
            }

            BigDecimal current = getLockedBalanceOrCreate(conn, ownerId, currency);
            if (current.compareTo(amount) < 0) {
                return TransactionResult.failure(
                        TransactionResult.Status.INSUFFICIENT_FUNDS,
                        current,
                        "Insufficient funds. Required: " + currency.format(amount) + ", Available: " + currency.format(current)
                );
            }

            BigDecimal newBalance = current.subtract(amount);
            UUID txId = UUID.randomUUID();

            updateWalletBalance(conn, ownerId, currency.id(), newBalance);
            insertTransactionHeader(conn, txId, idempotencyKey, TransactionType.WITHDRAW, description);
            insertTransactionEntry(conn, txId, ownerId, currency.id(), amount.negate(), newBalance);

            if (idempotencyKey != null) {
                insertIdempotencyKey(conn, idempotencyKey, txId);
            }

            return TransactionResult.success(txId, newBalance, "Withdrawal of " + currency.format(amount) + " succeeded.");
        });
    }

    /**
     * Atomically executes a peer-to-peer transfer between two player accounts.
     */
    @NotNull
    public TransactionResult transfer(
            @NotNull UUID senderId,
            @NotNull UUID receiverId,
            @NotNull Currency currency,
            @NotNull BigDecimal amount,
            @NotNull String description,
            @Nullable String idempotencyKey
    ) throws SQLException {
        if (senderId.equals(receiverId)) {
            return TransactionResult.failure(TransactionResult.Status.INVALID_AMOUNT, BigDecimal.ZERO, "Sender and receiver cannot be the same account.");
        }
        if (!currency.transferable()) {
            return TransactionResult.failure(TransactionResult.Status.NOT_TRANSFERABLE, BigDecimal.ZERO, "Currency '" + currency.displayName() + "' cannot be transferred.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return TransactionResult.failure(TransactionResult.Status.INVALID_AMOUNT, BigDecimal.ZERO, "Transfer amount must be positive.");
        }

        return databaseService.executeTransaction(conn -> {
            if (idempotencyKey != null) {
                TransactionResult duplicate = checkIdempotency(conn, idempotencyKey, senderId, currency.id());
                if (duplicate != null) {
                    return duplicate;
                }
            }

            // Lock wallets in canonical UUID order to eliminate deadlocks
            UUID first = senderId.compareTo(receiverId) < 0 ? senderId : receiverId;
            UUID second = senderId.compareTo(receiverId) < 0 ? receiverId : senderId;

            BigDecimal senderBal;
            BigDecimal receiverBal;

            if (first.equals(senderId)) {
                senderBal = getLockedBalanceOrCreate(conn, senderId, currency);
                receiverBal = getLockedBalanceOrCreate(conn, receiverId, currency);
            } else {
                receiverBal = getLockedBalanceOrCreate(conn, receiverId, currency);
                senderBal = getLockedBalanceOrCreate(conn, senderId, currency);
            }

            if (senderBal.compareTo(amount) < 0) {
                return TransactionResult.failure(
                        TransactionResult.Status.INSUFFICIENT_FUNDS,
                        senderBal,
                        "Insufficient funds to transfer " + currency.format(amount) + "."
                );
            }

            BigDecimal senderAfter = senderBal.subtract(amount);
            BigDecimal receiverAfter = receiverBal.add(amount);
            UUID txId = UUID.randomUUID();

            updateWalletBalance(conn, senderId, currency.id(), senderAfter);
            updateWalletBalance(conn, receiverId, currency.id(), receiverAfter);

            insertTransactionHeader(conn, txId, idempotencyKey, TransactionType.TRANSFER, description);
            // Debit entry
            insertTransactionEntry(conn, txId, senderId, currency.id(), amount.negate(), senderAfter);
            // Credit entry
            insertTransactionEntry(conn, txId, receiverId, currency.id(), amount, receiverAfter);

            if (idempotencyKey != null) {
                insertIdempotencyKey(conn, idempotencyKey, txId);
            }

            return TransactionResult.success(txId, senderAfter, "Transferred " + currency.format(amount) + " successfully.");
        });
    }

    /**
     * Executes an administrative balance modification with audit trail.
     */
    @NotNull
    public TransactionResult adminSet(
            @Nullable UUID actorId,
            @NotNull String actorName,
            @NotNull UUID targetId,
            @NotNull Currency currency,
            @NotNull BigDecimal newBalance,
            @NotNull String reason
    ) throws SQLException {
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            return TransactionResult.failure(TransactionResult.Status.INVALID_AMOUNT, BigDecimal.ZERO, "Balance cannot be set to a negative number.");
        }

        return databaseService.executeTransaction(conn -> {
            BigDecimal current = getLockedBalanceOrCreate(conn, targetId, currency);
            BigDecimal delta = newBalance.subtract(current);
            UUID txId = UUID.randomUUID();

            updateWalletBalance(conn, targetId, currency.id(), newBalance);
            insertTransactionHeader(conn, txId, null, TransactionType.ADMIN_SET, "Admin Set: " + reason);
            insertTransactionEntry(conn, txId, targetId, currency.id(), delta, newBalance);
            insertAuditLog(conn, actorId, actorName, targetId, currency.id(), "SET", newBalance, reason);

            return TransactionResult.success(txId, newBalance, "Balance set to " + currency.format(newBalance) + ".");
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Internal Helper Queries
    // ---------------------------------------------------------------------------------------------

    private Wallet findWallet(Connection conn, UUID ownerId, String currencyId) throws SQLException {
        String sql = "SELECT balance, updated_at FROM sr_wallets WHERE owner_id = ? AND currency_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, ownerId);
            ps.setString(2, currencyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Wallet(
                            ownerId,
                            currencyId,
                            rs.getBigDecimal("balance"),
                            rs.getTimestamp("updated_at").toInstant()
                    );
                }
            }
        }
        return null;
    }

    private BigDecimal getLockedBalanceOrCreate(Connection conn, UUID ownerId, Currency currency) throws SQLException {
        String lockSql = "SELECT balance FROM sr_wallets WHERE owner_id = ? AND currency_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
            ps.setObject(1, ownerId);
            ps.setString(2, currency.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        }

        // Insert wallet if not present
        String insert = """
            INSERT INTO sr_wallets (owner_id, currency_id, balance, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
        """;
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setObject(1, ownerId);
            ps.setString(2, currency.id());
            ps.setBigDecimal(3, currency.defaultBalance());
            try {
                ps.executeUpdate();
            } catch (SQLException ignored) {
                // Handled if concurrent insert occurred
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
            ps.setObject(1, ownerId);
            ps.setString(2, currency.id());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                }
            }
        }
        return currency.defaultBalance();
    }

    private void updateWalletBalance(Connection conn, UUID ownerId, String currencyId, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE sr_wallets SET balance = ?, updated_at = CURRENT_TIMESTAMP WHERE owner_id = ? AND currency_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newBalance);
            ps.setObject(2, ownerId);
            ps.setString(3, currencyId);
            ps.executeUpdate();
        }
    }

    private void insertTransactionHeader(Connection conn, UUID id, String idempotencyKey, TransactionType type, String description) throws SQLException {
        String sql = "INSERT INTO sr_transactions (id, idempotency_key, transaction_type, description, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, idempotencyKey);
            ps.setString(3, type.name());
            ps.setString(4, description);
            ps.executeUpdate();
        }
    }

    private void insertTransactionEntry(Connection conn, UUID txId, UUID ownerId, String currencyId, BigDecimal amount, BigDecimal balanceAfter) throws SQLException {
        String sql = "INSERT INTO sr_transaction_entries (transaction_id, owner_id, currency_id, amount, balance_after, created_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, txId);
            ps.setObject(2, ownerId);
            ps.setString(3, currencyId);
            ps.setBigDecimal(4, amount);
            ps.setBigDecimal(5, balanceAfter);
            ps.executeUpdate();
        }
    }

    private void insertIdempotencyKey(Connection conn, String key, UUID txId) throws SQLException {
        String sql = "INSERT INTO sr_idempotency_keys (idempotency_key, transaction_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setObject(2, txId);
            ps.executeUpdate();
        }
    }

    private void insertAuditLog(Connection conn, UUID actorId, String actorName, UUID targetId, String currencyId, String action, BigDecimal amount, String reason) throws SQLException {
        String sql = "INSERT INTO sr_economy_audit (actor_id, actor_name, target_id, currency_id, action, amount, reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, actorId);
            ps.setString(2, actorName);
            ps.setObject(3, targetId);
            ps.setString(4, currencyId);
            ps.setString(5, action);
            ps.setBigDecimal(6, amount);
            ps.setString(7, reason);
            ps.executeUpdate();
        }
    }

    private TransactionResult checkIdempotency(Connection conn, String key, UUID ownerId, String currencyId) throws SQLException {
        String sql = """
            SELECT k.transaction_id, e.balance_after
            FROM sr_idempotency_keys k
            JOIN sr_transaction_entries e ON k.transaction_id = e.transaction_id
            WHERE k.idempotency_key = ? AND e.owner_id = ? AND e.currency_id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setObject(2, ownerId);
            ps.setString(3, currencyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    UUID txId = (UUID) rs.getObject("transaction_id");
                    BigDecimal balAfter = rs.getBigDecimal("balance_after");
                    return TransactionResult.duplicate(txId, balAfter);
                }
            }
        }
        return null;
    }
}
