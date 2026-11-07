package com.skyrealms.economy.vault;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.economy.repository.EconomyRepository;
import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.platform.common.event.DefaultEventBus;
import com.zaxxer.hikari.HikariConfig;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class VaultEconomyBridgeTest {

    private HikariDatabaseService databaseService;
    private DefaultEventBus eventBus;
    private EconomyService economyService;
    private VaultEconomyBridge vaultBridge;

    @BeforeEach
    void setUp() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("VaultBridge-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:vaultdb_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
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
        vaultBridge = new VaultEconomyBridge(economyService, "coins");
    }

    @AfterEach
    void tearDown() {
        economyService.close();
        eventBus.close();
        databaseService.close();
    }

    @Test
    @DisplayName("Should return correct Vault currency metadata")
    void shouldReturnCurrencyMetadata() {
        assertThat(vaultBridge.isEnabled()).isTrue();
        assertThat(vaultBridge.getName()).isEqualTo("SkyRealms Economy");
        assertThat(vaultBridge.currencyNamePlural()).isEqualTo("Coins");
        assertThat(vaultBridge.fractionalDigits()).isEqualTo(2);
        assertThat(vaultBridge.format(125.50)).contains("125.50");
    }

    @Test
    @DisplayName("Should query balance, deposit, and withdraw through Vault OfflinePlayer API")
    void shouldDepositAndWithdrawViaVault() {
        UUID playerId = UUID.randomUUID();
        OfflinePlayer mockPlayer = Mockito.mock(OfflinePlayer.class);
        when(mockPlayer.getUniqueId()).thenReturn(playerId);
        when(mockPlayer.hasPlayedBefore()).thenReturn(true);

        // Default balance is 100.00
        assertThat(vaultBridge.getBalance(mockPlayer)).isEqualTo(100.00);
        assertThat(vaultBridge.has(mockPlayer, 50.00)).isTrue();
        assertThat(vaultBridge.has(mockPlayer, 200.00)).isFalse();

        // Deposit via Vault
        EconomyResponse depResponse = vaultBridge.depositPlayer(mockPlayer, 50.00);
        assertThat(depResponse.type).isEqualTo(EconomyResponse.ResponseType.SUCCESS);
        assertThat(depResponse.balance).isEqualTo(150.00);
        assertThat(vaultBridge.getBalance(mockPlayer)).isEqualTo(150.00);

        // Withdraw via Vault
        EconomyResponse withResponse = vaultBridge.withdrawPlayer(mockPlayer, 60.00);
        assertThat(withResponse.type).isEqualTo(EconomyResponse.ResponseType.SUCCESS);
        assertThat(withResponse.balance).isEqualTo(90.00);
        assertThat(vaultBridge.getBalance(mockPlayer)).isEqualTo(90.00);

        // Attempt overdraft
        EconomyResponse overResponse = vaultBridge.withdrawPlayer(mockPlayer, 200.00);
        assertThat(overResponse.type).isEqualTo(EconomyResponse.ResponseType.FAILURE);
        assertThat(vaultBridge.getBalance(mockPlayer)).isEqualTo(90.00);
    }
}
