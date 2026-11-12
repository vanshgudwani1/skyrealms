package com.skyrealms.islands.lobby;

import org.bukkit.Color;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Immutable definition for an interactive 3D display pedestal in the Sakura Hub.
 */
public record LobbyStation(
        @NotNull String id,
        @NotNull String title,
        @NotNull String subtitle,
        @NotNull String command,
        @NotNull Material icon,
        @NotNull Color glowColor,
        double x,
        double y,
        double z
) {
    /**
     * All interactive stations positioned on verified open courtyard slabs around Sakura Spawn.
     */
    public static final List<LobbyStation> DEFAULT_STATIONS = List.of(
            new LobbyStation(
                    "islands",
                    "☁ SKYBLOCK ISLANDS",
                    "Create, expand & visit realms",
                    "island",
                    Material.GRASS_BLOCK,
                    Color.fromRGB(85, 255, 85),
                    -3.5, 89.0, -3.5
            ),
            new LobbyStation(
                    "garden",
                    "✿ SAKURA GARDEN",
                    "16 plots, Crop Mastery & abilities",
                    "garden",
                    Material.GOLDEN_HOE,
                    Color.fromRGB(255, 158, 187),
                    4.5, 89.0, -3.5
            ),
            new LobbyStation(
                    "pets",
                    "✦ CUBEES PETS",
                    "Animated 3D ModelEngine companions",
                    "pets",
                    Material.AMETHYST_CLUSTER,
                    Color.fromRGB(224, 170, 255),
                    -3.5, 89.0, 4.5
            ),
            new LobbyStation(
                    "menu",
                    "★ MASTER HUB MENU",
                    "All warps, skills, gear & guides",
                    "menu",
                    Material.NETHER_STAR,
                    Color.fromRGB(255, 209, 102),
                    4.5, 89.0, 4.5
            ),
            new LobbyStation(
                    "items",
                    "⚔ CUSTOM HOES & GEAR",
                    "Upgradable tools & custom enchants",
                    "items",
                    Material.ENCHANTED_BOOK,
                    Color.fromRGB(0, 229, 255),
                    -2.5, 90.0, 14.5
            ),
            new LobbyStation(
                    "treasury",
                    "⛃ REALM TREASURY",
                    "Coins, Gems & Player Profile",
                    "balance",
                    Material.GOLD_INGOT,
                    Color.fromRGB(255, 190, 60),
                    3.5, 90.0, 14.5
            )
    );
}

