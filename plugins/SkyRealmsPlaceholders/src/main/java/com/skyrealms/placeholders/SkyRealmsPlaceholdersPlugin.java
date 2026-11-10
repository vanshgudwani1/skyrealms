package com.skyrealms.placeholders;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper plugin entrypoint registering SkyRealms PlaceholderAPI expansions on startup.
 */
public final class SkyRealmsPlaceholdersPlugin extends JavaPlugin {

    private SkyRealmsPlaceholderExpansion expansion;

    @Override
    public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getSLF4JLogger().warn("PlaceholderAPI is not enabled; SkyRealms placeholders will remain inactive.");
            return;
        }

        SkyRealmsServiceAccess services = new LiveSkyRealmsServiceAccess(Bukkit.getServicesManager());
        SkyRealmsPlaceholderResolver resolver = new SkyRealmsPlaceholderResolver(services);
        this.expansion = new SkyRealmsPlaceholderExpansion(getPluginMeta().getVersion(), resolver);

        if (expansion.register()) {
            getSLF4JLogger().info("Registered %skyrealms_*% PlaceholderAPI expansion ({} placeholders).",
                    expansion.getPlaceholders().size());
        } else {
            getSLF4JLogger().error("Failed to register %skyrealms_*% PlaceholderAPI expansion.");
        }
    }

    @Override
    public void onDisable() {
        if (expansion != null && expansion.isRegistered()) {
            expansion.unregister();
        }
        getSLF4JLogger().info("SkyRealmsPlaceholders disabled.");
    }
}

