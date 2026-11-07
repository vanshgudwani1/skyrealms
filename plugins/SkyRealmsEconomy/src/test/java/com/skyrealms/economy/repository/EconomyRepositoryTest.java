package com.skyrealms.economy.repository;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.economy.model.Currency;
import com.skyrealms.economy.model.TransactionResult;
import com.skyrealms.economy.model.Wallet;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EconomyRepositoryTest {

    private HikariDatabaseService databaseService;
    private EconomyRepository repository;
    private Currency coins;
    private Currency gems;

    @BeforeEach
    void setUp() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("EcoRepo-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:ecodb_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(config);

        DatabaseMigrationService migrationService = new DatabaseMigrationService(
                databaseService.getDataSource(),
                HikariDatabaseService.class.getClassLoader()
        );
        migrationService.runMigrations();

        repository = new EconomyRepository(databaseService);
        Map<String, Currency> currencies = repository.loadCurrencies();
        coins = currencies.get("coins");
        gems = currencies.get("gems");
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    @DisplayName("Should load seeded currencies correctly")
    void shouldLoadSeededCurrencies() {
        assertThat(coins).isNotNull();
        assertThat(coins.displayName()).isEqualTo("Coins");
        assertThat(coins.symbol()).isEqualTo("⛃");
        assertThat(coins.defaultBalance()).isEqualByComparingTo("100.00");
        assertThat(coins.transferable()).isTrue();

        assertThat(gems).isNotNull();
        assertThat(gems.displayName()).isEqualTo("Gems");
        assertThat(gems.symbol()).isEqualTo("✦");
        assertThat(gems.defaultBalance()).isEqualByComparingTo("0.00");
        assertThat(gems.transferable()).isFalse();
    }

    @Test
    @DisplayName("Should initialize wallet with default balance")
    void shouldInitializeWalletWithDefaultBalance() throws SQLException {
        UUID playerId = UUID.randomUUID();
        Wallet wallet = repository.getOrCreateWallet(playerId, coins);

        assertThat(wallet).isNotNull();
        assertThat(wallet.ownerId()).isEqualTo(playerId);
        assertThat(wallet.currencyId()).isEqualTo("coins");
        assertThat(wallet.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Should execute atomic deposit and update ledger")
    void shouldExecuteDeposit() throws SQLException {
        UUID playerId = UUID.randomUUID();
        TransactionResult result = repository.deposit(playerId, coins, new BigDecimal("50.00"), "Quest reward", null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.balanceAfter()).isEqualByComparingTo("150.00");

        Wallet wallet = repository.getOrCreateWallet(playerId, coins);
        assertThat(wallet.balance()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Should execute atomic withdrawal when funds are sufficient")
    void shouldExecuteWithdrawal() throws SQLException {
        UUID playerId = UUID.randomUUID();
        TransactionResult result = repository.withdraw(playerId, coins, new BigDecimal("40.00"), "Item purchase", null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.balanceAfter()).isEqualByComparingTo("60.00");

        Wallet wallet = repository.getOrCreateWallet(playerId, coins);
        assertThat(wallet.balance()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("Should reject withdrawal when balance is insufficient")
    void shouldRejectWithdrawalOnInsufficientFunds() throws SQLException {
        UUID playerId = UUID.randomUUID();
        TransactionResult result = repository.withdraw(playerId, coins, new BigDecimal("150.00"), "Overdraft attempt", null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.status()).isEqualTo(TransactionResult.Status.INSUFFICIENT_FUNDS);
        assertThat(result.balanceAfter()).isEqualByComparingTo("100.00");

        Wallet wallet = repository.getOrCreateWallet(playerId, coins);
        assertThat(wallet.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Should execute atomic transfer with double-entry ledger balance conservation")
    void shouldExecuteTransfer() throws SQLException {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        TransactionResult result = repository.transfer(sender, receiver, coins, new BigDecimal("25.00"), "Gift", null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.balanceAfter()).isEqualByComparingTo("75.00");

        Wallet senderWallet = repository.getOrCreateWallet(sender, coins);
        Wallet receiverWallet = repository.getOrCreateWallet(receiver, coins);

        assertThat(senderWallet.balance()).isEqualByComparingTo("75.00");
        assertThat(receiverWallet.balance()).isEqualByComparingTo("125.00");
    }

    @Test
    @DisplayName("Should reject transfer for non-transferable currency like Gems")
    void shouldRejectTransferForNonTransferableCurrency() throws SQLException {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        // Give sender some gems first
        repository.deposit(sender, gems, new BigDecimal("10.00"), "Purchased gems", null);

        TransactionResult result = repository.transfer(sender, receiver, gems, new BigDecimal("5.00"), "Illegal gem transfer", null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.status()).isEqualTo(TransactionResult.Status.NOT_TRANSFERABLE);
    }

    @Test
    @DisplayName("Should prevent double-processing with idempotency key")
    void shouldPreventDoubleProcessingWithIdempotencyKey() throws SQLException {
        UUID playerId = UUID.randomUUID();
        String key = "reward:daily:claim:" + playerId;

        // First execution
        TransactionResult first = repository.deposit(playerId, coins, new BigDecimal("50.00"), "Daily login bonus", key);
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.balanceAfter()).isEqualByComparingTo("150.00");

        // Duplicate submission with same key
        TransactionResult second = repository.deposit(playerId, coins, new BigDecimal("50.00"), "Daily login bonus duplicate", key);
        assertThat(second.status()).isEqualTo(TransactionResult.Status.DUPLICATE_IDEMPOTENCY);
        assertThat(second.balanceAfter()).isEqualByComparingTo("150.00");

        // Confirm wallet balance remained 150.00 and was not incremented twice
        Wallet wallet = repository.getOrCreateWallet(playerId, coins);
        assertThat(wallet.balance()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Should execute admin set balance and record audit trail")
    void shouldExecuteAdminSet() throws SQLException {
        UUID target = UUID.randomUUID();
        TransactionResult result = repository.adminSet(null, "CONSOLE", target, coins, new BigDecimal("999.99"), "Admin correction");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.balanceAfter()).isEqualByComparingTo("999.99");

        Wallet wallet = repository.getOrCreateWallet(target, coins);
        assertThat(wallet.balance()).isEqualByComparingTo("999.99");
    }
}
