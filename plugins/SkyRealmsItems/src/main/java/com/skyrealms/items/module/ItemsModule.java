package com.skyrealms.items.module;

import com.skyrealms.items.catalog.ItemCatalog;
import com.skyrealms.items.command.ItemsCommand;
import com.skyrealms.items.factory.SkyRealmsItemFactory;
import com.skyrealms.items.gui.ItemsCatalogGuiListener;
import com.skyrealms.items.listener.ArmorAttributeTask;
import com.skyrealms.items.listener.CustomEnchantEffectListener;
import com.skyrealms.items.listener.GearEffectListener;
import com.skyrealms.items.listener.VanillaEnchantBlockListener;
import com.skyrealms.items.recipe.ItemRecipeRegistry;
import com.skyrealms.items.service.EliteEnchantsService;
import com.skyrealms.items.service.HoeUpgradeService;
import com.skyrealms.items.service.ItemRegistryService;
import com.skyrealms.platform.api.module.ModuleContext;
import com.skyrealms.platform.api.module.ModuleId;
import com.skyrealms.platform.api.module.ModuleMetadata;
import com.skyrealms.platform.api.module.ModuleState;
import com.skyrealms.platform.api.module.SkyModule;
import com.skyrealms.platform.common.command.DynamicCommandRegistrar;
import com.skyrealms.platform.integrations.CustomEnchantProvider;
import com.skyrealms.platform.integrations.CustomItemStatProvider;
import org.bukkit.Bukkit;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * SkyRealms Items module managing the 3 upgradable Farming Hoes,
 * vanilla enchantment blocking (while allowing EliteEnchantments), and stat provisioning.
 */
public final class ItemsModule implements SkyModule {

    private static final ModuleId ID = ModuleId.of("items");

    private final Plugin plugin;
    private final ModuleMetadata metadata = ModuleMetadata.builder(ID)
            .name("SkyRealms Items")
            .version("1.0.0")
            .dependsOn(ModuleId.of("core"))
            .build();

    private ModuleState state = ModuleState.UNLOADED;
    private ItemRegistryService itemService;
    private ItemRecipeRegistry recipeRegistry;
    private ArmorAttributeTask attributeTask;

    public ItemsModule(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
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

        ItemCatalog catalog = new ItemCatalog();
        SkyRealmsItemFactory factory = new SkyRealmsItemFactory(catalog);
        HoeUpgradeService upgradeService = new HoeUpgradeService(catalog, factory);
        this.itemService = new ItemRegistryService(catalog, factory, upgradeService);
        EliteEnchantsService enchantService = new EliteEnchantsService();

        context.services().register(ItemRegistryService.class, itemService);
        context.services().register(CustomItemStatProvider.class, itemService);
        context.services().register(CustomEnchantProvider.class, enchantService);
        Bukkit.getServicesManager().register(ItemRegistryService.class, itemService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(CustomItemStatProvider.class, itemService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(CustomEnchantProvider.class, enchantService, plugin, ServicePriority.High);

        long syncInterval = plugin.getConfig().getLong("stats.speed-sync-interval-ticks", 20L);
        boolean registerRecipes = plugin.getConfig().getBoolean("recipes.register-survival-recipes", true);
        boolean blockVanillaEnchants = plugin.getConfig().getBoolean("enchantments.block-vanilla-on-custom-items", true);

        this.attributeTask = new ArmorAttributeTask(plugin, itemService);
        this.attributeTask.start(syncInterval);

        if (registerRecipes) {
            this.recipeRegistry = new ItemRecipeRegistry(itemService);
            this.recipeRegistry.registerAll();
        }

        Bukkit.getPluginManager().registerEvents(new GearEffectListener(itemService, attributeTask), plugin);
        Bukkit.getPluginManager().registerEvents(new VanillaEnchantBlockListener(factory, blockVanillaEnchants), plugin);
        Bukkit.getPluginManager().registerEvents(new CustomEnchantEffectListener(plugin, enchantService), plugin);
        Bukkit.getPluginManager().registerEvents(new ItemsCatalogGuiListener(itemService), plugin);

        ItemsCommand commandHandler = new ItemsCommand(itemService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "items",
                "Browse SkyRealms Farming Hoes, upgrade your held hoe, or inspect gear stats",
                "/items [upgrade|list|stats|inspect]",
                List.of("sritems", "gear", "catalog", "hoe"),
                "skyrealms.items.base",
                PermissionDefault.TRUE,
                "§cYou do not have permission to use /items.",
                commandHandler
        );

        state = ModuleState.ENABLED;
        context.logger().info("SkyRealms Items module enabled ({} upgradable hoes; vanilla enchants blocked={}).",
                catalog.all().size(), blockVanillaEnchants);
    }

    @Override
    public void onDisable(@NotNull ModuleContext context) throws Exception {
        context.logger().info("Disabling SkyRealms Items module...");
        if (attributeTask != null) {
            attributeTask.stop();
            attributeTask = null;
        }
        if (recipeRegistry != null) {
            recipeRegistry.unregisterAll();
            recipeRegistry = null;
        }
        Bukkit.getServicesManager().unregister(CustomEnchantProvider.class, plugin);
        Bukkit.getServicesManager().unregister(CustomItemStatProvider.class, plugin);
        Bukkit.getServicesManager().unregister(ItemRegistryService.class, plugin);
        itemService = null;
        state = ModuleState.DISABLED;
    }
}
