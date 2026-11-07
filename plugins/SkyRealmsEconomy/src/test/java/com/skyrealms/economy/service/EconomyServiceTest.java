package com.skyrealms.economy.service;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.economy.event.TransactionCompletedEvent;
import com.skyrealms.economy.model.TransactionResult;
import com.skyrealms.economy.repository.EconomyRepository;
import com.skyrealms.platform.api.event.EventPriority;
import com.skyrealms.platform.common.event.DefaultEventBus;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class EconomyServiceTest {

    private HikariDatabaseService databaseService;
    private DefaultEventBus eventBus;
    private EconomyService economyService;

    @BeforeEach
    void setUp() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("EcoService-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:ecoservicedb_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(config);

        DatabaseMigrationService migrationService = new DatabaseMigrationService(
                databaseService.getDataSource(),
                HikariDatabaseService.class.getClassLoader()
        );
        migrationService.runMigrations();

        EconomyRepository repository = new EconomyRepository(databaseService);
        eventBus = new DefaultEventBus();
        economyService = new EconomyService(repository, eventBus);
    }

    @AfterEach
    void tearDown() {
        economyService.close();
        eventBus.close();
        databaseService.close();
    }

    @Test
    @DisplayName("Should retrieve cached balance and update on deposit")
    void shouldCacheAndDeposit() {
        UUID playerId = UUID.randomUUID();

        BigDecimal initial = economyService.getBalanceAsync(playerId, "coins").join();
        assertThat(initial).isEqualByComparingTo("100.00");

        // Verify synchronous cached read
        BigDecimal cached = economyService.getCachedBalance(playerId, "coins");
        assertThat(cached).isEqualByComparingTo("100.00");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        eventBus.subscribe(TransactionCompletedEvent.class, EventPriority.NORMAL, event -> {
            if (event.ownerId().equals(playerId)) {
                eventFired.set(true);
            }
        });

        TransactionResult result = economyService.deposit(playerId, "coins", new BigDecimal("75.50"), "Reward", null).join();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.balanceAfter()).isEqualByComparingTo("175.50");

        // Cache must reflect new balance immediately
        assertThat(economyService.getCachedBalance(playerId, "coins")).isEqualByComparingTo("175.50");

        // Wait for async event bus delivery
        long deadline = System.currentTimeMillis() + 1000;
        while (!eventFired.get() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {}
        }
        assertThat(eventFired.get()).isTrue();
    }

    @Test
    @DisplayName("Should execute transfer and update cached states")
    void shouldTransferAndSyncCaches() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        // Preload balances
        economyService.preloadBalances(sender).join();
        economyService.preloadBalances(receiver).join();

        TransactionResult result = economyService.transfer(sender, receiver, "coins", new BigDecimal("30.00"), "Pay", null).join();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.balanceAfter()).isEqualByComparingTo("70.00");

        assertThat(economyService.getCachedBalance(sender, "coins")).isEqualByComparingTo("70.00");
        assertThat(economyService.getCachedBalance(receiver, "coins")).isEqualByComparingTo("130.00");
    }
}
