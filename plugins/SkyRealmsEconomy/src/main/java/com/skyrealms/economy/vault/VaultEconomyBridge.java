package com.skyrealms.economy.vault;

import com.skyrealms.economy.model.Currency;
import com.skyrealms.economy.model.TransactionResult;
import com.skyrealms.economy.service.EconomyService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Vault economy bridge adapter.
 * Bridges Vault API calls to SkyRealms authoritative primary currency ('coins').
 */
public final class VaultEconomyBridge implements Economy {

    private final EconomyService economyService;
    private final String primaryCurrencyId;

    public VaultEconomyBridge(@NotNull EconomyService economyService, @NotNull String primaryCurrencyId) {
        this.economyService = Objects.requireNonNull(economyService, "economyService cannot be null");
        this.primaryCurrencyId = primaryCurrencyId.toLowerCase();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "SkyRealms Economy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return economyService.getCurrency(primaryCurrencyId).map(Currency::decimalPlaces).orElse(2);
    }

    @Override
    public String format(double amount) {
        BigDecimal bd = BigDecimal.valueOf(amount);
        return economyService.getCurrency(primaryCurrencyId)
                .map(c -> c.format(bd))
                .orElse(String.format("⛃%,.2f", amount));
    }

    @Override
    public String currencyNamePlural() {
        return economyService.getCurrency(primaryCurrencyId).map(Currency::displayName).orElse("Coins");
    }

    @Override
    public String currencyNameSingular() {
        return currencyNamePlural();
    }

    @Override
    public boolean hasAccount(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null && (player.hasPlayedBefore() || player.isOnline());
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null) return 0.0;
        return economyService.getCachedBalance(player.getUniqueId(), primaryCurrencyId).doubleValue();
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player cannot be null");
        }
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount");
        }

        UUID uuid = player.getUniqueId();
        BigDecimal bdAmount = BigDecimal.valueOf(amount);

        TransactionResult result = economyService.withdraw(uuid, primaryCurrencyId, bdAmount, "Vault: Withdraw", null).join();
        if (result.isSuccess()) {
            return new EconomyResponse(amount, result.balanceAfter().doubleValue(), EconomyResponse.ResponseType.SUCCESS, result.message());
        } else if (result.status() == TransactionResult.Status.INSUFFICIENT_FUNDS) {
            return new EconomyResponse(0, result.balanceAfter().doubleValue(), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        } else {
            return new EconomyResponse(0, result.balanceAfter().doubleValue(), EconomyResponse.ResponseType.FAILURE, result.message());
        }
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Player cannot be null");
        }
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount");
        }

        UUID uuid = player.getUniqueId();
        BigDecimal bdAmount = BigDecimal.valueOf(amount);

        TransactionResult result = economyService.deposit(uuid, primaryCurrencyId, bdAmount, "Vault: Deposit", null).join();
        if (result.isSuccess()) {
            return new EconomyResponse(amount, result.balanceAfter().doubleValue(), EconomyResponse.ResponseType.SUCCESS, result.message());
        } else {
            return new EconomyResponse(0, result.balanceAfter().doubleValue(), EconomyResponse.ResponseType.FAILURE, result.message());
        }
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) return false;
        economyService.preloadBalances(player.getUniqueId()).join();
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    // ---------------------------------------------------------------------------------------------
    // Bank Operations (Unused in SkyBlock)
    // ---------------------------------------------------------------------------------------------

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported by SkyRealms Economy");
    }
}
