package com.skyrealms.economy.module;

import com.skyrealms.economy.command.BalanceCommand;
import com.skyrealms.economy.command.EcoCommand;
import com.skyrealms.economy.command.PayCommand;
import com.skyrealms.economy.listener.EconomyConnectionListener;
import com.skyrealms.economy.repository.EconomyRepository;
import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.economy.vault.VaultEconomyBridge;
import com.skyrealms.platform.api.module.*;
import com.skyrealms.platform.common.command.DynamicCommandRegistrar;
import com.skyrealms.platform.database.DatabaseService;
import com.skyrealms.platform.integrations.EconomyProvider;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * SkyRealms Economy Module.
 * Provides multi-currency double-entry ledger, transaction safety, and Vault bridging.
 */
public final class EconomyModule implements SkyModule {

    public static final ModuleId ID = ModuleId.of("economy");

    private final JavaPlugin plugin;
    private final ModuleMetadata metadata;
    private ModuleState state = ModuleState.UNLOADED;

    private EconomyService economyService;
    private VaultEconomyBridge vaultBridge;

    public EconomyModule(@NotNull JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.metadata = ModuleMetadata.builder(ID)
                .name("SkyRealms Economy")
                .version("1.0.0")
                .dependsOn(ModuleId.of("database"))
                .dependsOn(ModuleId.of("player"))
                .build();
    }

    @Override
    @NotNull
    public ModuleId id() {
        return ID;
    }

    @Override
    @NotNull
    public ModuleMetadata metadata() {
        return metadata;
    }

    @Override
    @NotNull
    public ModuleState state() {
        return state;
    }

    @Override
    public void onEnable(@NotNull ModuleContext context) throws Exception {
        state = ModuleState.INITIALIZING;

        DatabaseService databaseService = context.services().get(DatabaseService.class);
        EconomyRepository repository = new EconomyRepository(databaseService);
        this.economyService = new EconomyService(repository, context.eventBus());

        // Register in internal ServiceRegistry
        context.services().register(EconomyService.class, economyService);
        context.services().register(EconomyProvider.class, economyService);

        // Register in Bukkit ServicesManager
        Bukkit.getServicesManager().register(EconomyService.class, economyService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(EconomyProvider.class, economyService, plugin, ServicePriority.High);

        // Vault Bridge Integration
        String primaryCurrency = plugin.getConfig().getString("vault-bridge.primary-currency", "coins");
        if (plugin.getServer().getPluginManager().isPluginEnabled("Vault") && plugin.getConfig().getBoolean("vault-bridge.enabled", true)) {
            this.vaultBridge = new VaultEconomyBridge(economyService, primaryCurrency);
            Bukkit.getServicesManager().register(Economy.class, vaultBridge, plugin, ServicePriority.Highest);
            context.logger().info("Vault Economy hooked using primary currency: '{}'", primaryCurrency);
        }

        // Register Bukkit Listener
        EconomyConnectionListener listener = new EconomyConnectionListener(economyService);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        // Register Commands
        BigDecimal minPay = BigDecimal.valueOf(plugin.getConfig().getDouble("rules.min-pay-amount", 1.00));

        BalanceCommand balHandler = new BalanceCommand(economyService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "balance",
                "Check currency balance",
                "/balance [player]",
                java.util.List.of("bal", "money"),
                "skyrealms.economy.balance",
                org.bukkit.permissions.PermissionDefault.TRUE,
                "§cYou do not have permission to use /balance.",
                balHandler
        );

        PayCommand payHandler = new PayCommand(economyService, primaryCurrency, minPay);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "pay",
                "Pay currency to another online player",
                "/pay <player> <amount>",
                java.util.Collections.emptyList(),
                "skyrealms.economy.pay",
                org.bukkit.permissions.PermissionDefault.TRUE,
                "§cYou do not have permission to use /pay.",
                payHandler
        );

        EcoCommand ecoHandler = new EcoCommand(economyService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "eco",
                "Administrative economy management",
                "/eco <give|take|set> <player> <currency> <amount> [reason]",
                java.util.Collections.emptyList(),
                "skyrealms.economy.admin",
                org.bukkit.permissions.PermissionDefault.OP,
                "§cYou do not have permission to use /eco.",
                ecoHandler
        );

        state = ModuleState.ENABLED;
        context.logger().info("SkyRealms Economy module successfully enabled.");
    }

    @Override
    public void onDisable(@NotNull ModuleContext context) throws Exception {
        context.logger().info("Disabling SkyRealms Economy module...");

        if (vaultBridge != null) {
            Bukkit.getServicesManager().unregister(Economy.class, vaultBridge);
            vaultBridge = null;
        }

        if (economyService != null) {
            economyService.close();
            economyService = null;
        }

        state = ModuleState.DISABLED;
        context.logger().info("SkyRealms Economy module disabled.");
    }

    public EconomyService economyService() {
        return economyService;
    }
}
