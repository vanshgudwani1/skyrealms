package com.skyrealms.economy.service;

import com.skyrealms.economy.event.TransactionCompletedEvent;
import com.skyrealms.economy.model.Currency;
import com.skyrealms.economy.model.TransactionResult;
import com.skyrealms.economy.model.TransactionType;
import com.skyrealms.economy.model.Wallet;
import com.skyrealms.economy.repository.EconomyRepository;
import com.skyrealms.platform.api.event.EventBus;
import com.skyrealms.platform.integrations.EconomyProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/**
 * High-performance authoritative economy service with concurrent caching,
 * non-blocking database dispatch, and event propagation.
 */
public final class EconomyService implements EconomyProvider, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(EconomyService.class);

    private final EconomyRepository repository;
    private final EventBus eventBus;
    private final ExecutorService executor;

    // Cache: currencyId -> Currency definition
    private final Map<String, Currency> currencies = new ConcurrentHashMap<>();

    // Cache: ownerId -> (currencyId -> balance)
    private final Map<UUID, Map<String, BigDecimal>> balanceCache = new ConcurrentHashMap<>();

    public EconomyService(@NotNull EconomyRepository repository, @NotNull EventBus eventBus) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus cannot be null");
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "SkyRealms-Economy-AsyncWorker");
            t.setDaemon(true);
            return t;
        });

        loadCurrencies();
    }

    /**
     * Initializes or refreshes all currencies from the database.
     */
    public void loadCurrencies() {
        try {
            Map<String, Currency> loaded = repository.loadCurrencies();
            currencies.clear();
            currencies.putAll(loaded);
            LOGGER.info("Loaded {} active currencies from database: {}", currencies.size(), currencies.keySet());
        } catch (SQLException e) {
            LOGGER.error("Failed to load currencies from database: {}", e.getMessage(), e);
        }
    }

    @NotNull
    public Optional<Currency> getCurrency(@NotNull String id) {
        return Optional.ofNullable(currencies.get(id.toLowerCase(Locale.ROOT)));
    }

    @NotNull
    public Collection<Currency> getCurrencies() {
        return Collections.unmodifiableCollection(currencies.values());
    }

    /**
     * Preloads player balances into memory asynchronously (e.g. on join).
     */
    @NotNull
    public CompletableFuture<Void> preloadBalances(@NotNull UUID ownerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Ensure default wallets exist for all currencies
                for (Currency c : currencies.values()) {
                    repository.getOrCreateWallet(ownerId, c);
                }
                Map<String, BigDecimal> dbBalances = repository.getBalances(ownerId);
                balanceCache.computeIfAbsent(ownerId, k -> new ConcurrentHashMap<>()).putAll(dbBalances);
            } catch (SQLException e) {
                LOGGER.error("Failed to preload balances for player {}: {}", ownerId, e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * Evicts player balances from cache (e.g. on quit after idle delay).
     */
    public void evict(@NotNull UUID ownerId) {
        balanceCache.remove(ownerId);
    }

    /**
     * Retrieves player balance from cache if present; otherwise queries database synchronously.
     */
    @NotNull
    public BigDecimal getCachedBalance(@NotNull UUID ownerId, @NotNull String currencyId) {
        String key = currencyId.toLowerCase(Locale.ROOT);
        Map<String, BigDecimal> playerWallets = balanceCache.get(ownerId);
        if (playerWallets != null && playerWallets.containsKey(key)) {
            return playerWallets.get(key);
        }

        Currency currency = currencies.get(key);
        if (currency == null) {
            return BigDecimal.ZERO;
        }

        try {
            Wallet wallet = repository.getOrCreateWallet(ownerId, currency);
            balanceCache.computeIfAbsent(ownerId, k -> new ConcurrentHashMap<>()).put(key, wallet.balance());
            return wallet.balance();
        } catch (SQLException e) {
            LOGGER.error("Database error retrieving balance for {}: {}", ownerId, e.getMessage(), e);
            return currency.defaultBalance();
        }
    }

    /**
     * Asynchronously retrieves a player's balance.
     */
    @NotNull
    public CompletableFuture<BigDecimal> getBalanceAsync(@NotNull UUID ownerId, @NotNull String currencyId) {
        String key = currencyId.toLowerCase(Locale.ROOT);
        Map<String, BigDecimal> playerWallets = balanceCache.get(ownerId);
        if (playerWallets != null && playerWallets.containsKey(key)) {
            return CompletableFuture.completedFuture(playerWallets.get(key));
        }

        return CompletableFuture.supplyAsync(() -> getCachedBalance(ownerId, key), executor);
    }

    /**
     * Asynchronously deposits funds with ledger logging and event dispatch.
     */
    @NotNull
    public CompletableFuture<TransactionResult> deposit(
            @NotNull UUID ownerId,
            @NotNull String currencyId,
            @NotNull BigDecimal amount,
            @NotNull String description,
            @Nullable String idempotencyKey
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Currency currency = currencies.get(currencyId.toLowerCase(Locale.ROOT));
            if (currency == null) {
                return TransactionResult.failure(TransactionResult.Status.CURRENCY_NOT_FOUND, BigDecimal.ZERO, "Currency '" + currencyId + "' not found.");
            }

            try {
                TransactionResult result = repository.deposit(ownerId, currency, amount, description, idempotencyKey);
                if (result.isSuccess()) {
                    updateCache(ownerId, currency.id(), result.balanceAfter());
                    if (result.transactionId() != null) {
                        eventBus.publishAsync(new TransactionCompletedEvent(
                                result.transactionId(), ownerId, currency.id(), TransactionType.DEPOSIT, amount, result.balanceAfter()
                        ));
                    }
                }
                return result;
            } catch (SQLException e) {
                LOGGER.error("SQL Error during deposit for {}: {}", ownerId, e.getMessage(), e);
                return TransactionResult.failure(TransactionResult.Status.ERROR, getCachedBalance(ownerId, currency.id()), "Database transaction failed.");
            }
        }, executor);
    }

    /**
     * Asynchronously withdraws funds with ledger logging and event dispatch.
     */
    @NotNull
    public CompletableFuture<TransactionResult> withdraw(
            @NotNull UUID ownerId,
            @NotNull String currencyId,
            @NotNull BigDecimal amount,
            @NotNull String description,
            @Nullable String idempotencyKey
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Currency currency = currencies.get(currencyId.toLowerCase(Locale.ROOT));
            if (currency == null) {
                return TransactionResult.failure(TransactionResult.Status.CURRENCY_NOT_FOUND, BigDecimal.ZERO, "Currency '" + currencyId + "' not found.");
            }

            try {
                TransactionResult result = repository.withdraw(ownerId, currency, amount, description, idempotencyKey);
                if (result.isSuccess()) {
                    updateCache(ownerId, currency.id(), result.balanceAfter());
                    if (result.transactionId() != null) {
                        eventBus.publishAsync(new TransactionCompletedEvent(
                                result.transactionId(), ownerId, currency.id(), TransactionType.WITHDRAW, amount.negate(), result.balanceAfter()
                        ));
                    }
                }
                return result;
            } catch (SQLException e) {
                LOGGER.error("SQL Error during withdrawal for {}: {}", ownerId, e.getMessage(), e);
                return TransactionResult.failure(TransactionResult.Status.ERROR, getCachedBalance(ownerId, currency.id()), "Database transaction failed.");
            }
        }, executor);
    }

    /**
     * Asynchronously executes a transfer between two accounts.
     */
    @NotNull
    public CompletableFuture<TransactionResult> transfer(
            @NotNull UUID senderId,
            @NotNull UUID receiverId,
            @NotNull String currencyId,
            @NotNull BigDecimal amount,
            @NotNull String description,
            @Nullable String idempotencyKey
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Currency currency = currencies.get(currencyId.toLowerCase(Locale.ROOT));
            if (currency == null) {
                return TransactionResult.failure(TransactionResult.Status.CURRENCY_NOT_FOUND, BigDecimal.ZERO, "Currency '" + currencyId + "' not found.");
            }

            try {
                TransactionResult result = repository.transfer(senderId, receiverId, currency, amount, description, idempotencyKey);
                if (result.isSuccess()) {
                    updateCache(senderId, currency.id(), result.balanceAfter());
                    // Invalidate receiver cache so next read pulls updated balance
                    evict(receiverId);

                    if (result.transactionId() != null) {
                        eventBus.publishAsync(new TransactionCompletedEvent(
                                result.transactionId(), senderId, currency.id(), TransactionType.TRANSFER, amount.negate(), result.balanceAfter()
                        ));
                    }
                }
                return result;
            } catch (SQLException e) {
                LOGGER.error("SQL Error during transfer from {} to {}: {}", senderId, receiverId, e.getMessage(), e);
                return TransactionResult.failure(TransactionResult.Status.ERROR, getCachedBalance(senderId, currency.id()), "Database transfer failed.");
            }
        }, executor);
    }

    /**
     * Asynchronously sets an account's balance administratively.
     */
    @NotNull
    public CompletableFuture<TransactionResult> adminSet(
            @Nullable UUID actorId,
            @NotNull String actorName,
            @NotNull UUID targetId,
            @NotNull String currencyId,
            @NotNull BigDecimal newBalance,
            @NotNull String reason
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Currency currency = currencies.get(currencyId.toLowerCase(Locale.ROOT));
            if (currency == null) {
                return TransactionResult.failure(TransactionResult.Status.CURRENCY_NOT_FOUND, BigDecimal.ZERO, "Currency '" + currencyId + "' not found.");
            }

            try {
                TransactionResult result = repository.adminSet(actorId, actorName, targetId, currency, newBalance, reason);
                if (result.isSuccess()) {
                    updateCache(targetId, currency.id(), result.balanceAfter());
                    if (result.transactionId() != null) {
                        eventBus.publishAsync(new TransactionCompletedEvent(
                                result.transactionId(), targetId, currency.id(), TransactionType.ADMIN_SET, newBalance, result.balanceAfter()
                        ));
                    }
                }
                return result;
            } catch (SQLException e) {
                LOGGER.error("SQL Error during admin set for {}: {}", targetId, e.getMessage(), e);
                return TransactionResult.failure(TransactionResult.Status.ERROR, getCachedBalance(targetId, currency.id()), "Database operation failed.");
            }
        }, executor);
    }

    private void updateCache(UUID ownerId, String currencyId, BigDecimal newBalance) {
        balanceCache.computeIfAbsent(ownerId, k -> new ConcurrentHashMap<>()).put(currencyId, newBalance);
    }

    // ---------------------------------------------------------------------------------------------
    // EconomyProvider Bridge Implementation
    // ---------------------------------------------------------------------------------------------

    @Override
    @NotNull
    public BigDecimal getBalance(@NotNull UUID playerId, @NotNull String currencyId) {
        return getCachedBalance(playerId, currencyId);
    }

    @Override
    public boolean has(@NotNull UUID playerId, @NotNull String currencyId, @NotNull BigDecimal amount) {
        return getBalance(playerId, currencyId).compareTo(amount) >= 0;
    }

    @Override
    public void deposit(@NotNull UUID playerId, @NotNull String currencyId, @NotNull BigDecimal amount, @NotNull String reason) {
        deposit(playerId, currencyId, amount, reason, null).join();
    }

    @Override
    public void withdraw(@NotNull UUID playerId, @NotNull String currencyId, @NotNull BigDecimal amount, @NotNull String reason) {
        withdraw(playerId, currencyId, amount, reason, null).join();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        balanceCache.clear();
    }
}
