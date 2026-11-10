package com.skyrealms.placeholders;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.service.IslandService;
import com.skyrealms.platform.integrations.CustomItemStatProvider;
import com.skyrealms.platform.integrations.PetStatProvider;
import com.skyrealms.platform.integrations.SkyItemStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves all {@code %skyrealms_*%} placeholders from live cached SkyRealms services.
 * Never performs synchronous database writes or blocking network lookups.
 */
public final class SkyRealmsPlaceholderResolver {

    public static final int DEFAULT_GARDEN_PLOTS_TOTAL = 16;

    public static final List<String> SUPPORTED_KEYS = List.of(
            "farming_level",
            "farming_xp",
            "farming_xp_next",
            "farming_xp_percent",
            "farming_xp_bar",
            "farming_fortune",
            "current_tool",
            "garden_level",
            "garden_unlocked",
            "garden_visitors",
            "garden_plots_unlocked",
            "garden_plots_total",
            "garden_event",
            "combat_level",
            "mining_level",
            "island_level",
            "island_rank",
            "island_members",
            "island_visitors",
            "island_owner",
            "island_name",
            "coins",
            "coins_short",
            "gems",
            "gems_short",
            "rank",
            "profile",
            "server_event",
            "location",
            "max_players"
    );

    private static final int MAX_TOOL_NAME_LEN = 22;
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();
    private static final DecimalFormatSymbols US_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);

    private final SkyRealmsServiceAccess services;

    public SkyRealmsPlaceholderResolver(@NotNull SkyRealmsServiceAccess services) {
        this.services = Objects.requireNonNull(services, "services cannot be null");
    }

    @Nullable
    public String resolve(@Nullable OfflinePlayer offlinePlayer, @NotNull String identifier) {
        String key = identifier.toLowerCase(Locale.ROOT);
        Player onlinePlayer = offlinePlayer instanceof Player p ? p : null;

        return switch (key) {
            case "farming_level" -> String.valueOf(getFarmingLevel(onlinePlayer));
            case "farming_xp" -> formatWholeNumber(getFarmingXp(onlinePlayer));
            case "farming_xp_next" -> formatWholeNumber(getFarmingXpNext(onlinePlayer));
            case "farming_xp_percent" -> getFarmingXpPercent(onlinePlayer) + "%";
            case "farming_xp_bar" -> renderProgressBar(getFarmingProgressRatio(onlinePlayer));
            case "farming_fortune" -> formatWholeNumber(Math.round(getFarmingFortune(onlinePlayer)));
            case "current_tool" -> getCurrentToolName(onlinePlayer);

            case "garden_level" -> "N/A";
            case "garden_unlocked" -> resolveGardenUnlocked(offlinePlayer, onlinePlayer);
            case "garden_visitors" -> String.valueOf(countGardenVisitors(offlinePlayer, onlinePlayer));
            case "garden_plots_unlocked" -> String.valueOf(countUnlockedGardenPlots(offlinePlayer, onlinePlayer));
            case "garden_plots_total" -> String.valueOf(getTotalGardenPlots(offlinePlayer, onlinePlayer));
            case "garden_event", "server_event" -> resolveActiveEvent(offlinePlayer);

            case "combat_level" -> String.valueOf(getCombatLevel(onlinePlayer));
            case "mining_level" -> String.valueOf(getMiningLevel(onlinePlayer));

            case "island_level" -> resolveIslandLevel(offlinePlayer, onlinePlayer);
            case "island_rank" -> resolveIslandRank(offlinePlayer, onlinePlayer);
            case "island_members" -> resolveIslandMembers(offlinePlayer, onlinePlayer);
            case "island_visitors" -> String.valueOf(countIslandVisitors(offlinePlayer, onlinePlayer));
            case "island_owner" -> resolveIslandOwner(offlinePlayer, onlinePlayer);
            case "island_name" -> resolveIslandName(offlinePlayer, onlinePlayer);

            case "coins" -> resolveCurrency(offlinePlayer, onlinePlayer, "coins", false);
            case "coins_short" -> resolveCurrency(offlinePlayer, onlinePlayer, "coins", true);
            case "gems" -> resolveCurrency(offlinePlayer, onlinePlayer, "gems", false);
            case "gems_short" -> resolveCurrency(offlinePlayer, onlinePlayer, "gems", true);

            case "rank" -> resolveRank(offlinePlayer, onlinePlayer);
            case "profile" -> resolveProfile(offlinePlayer);
            case "location" -> resolveLocationName(onlinePlayer);
            case "max_players" -> String.valueOf(services.maxPlayers());
            default -> null;
        };
    }

    private int getFarmingLevel(@Nullable Player player) {
        if (player == null) return 0;
        return services.mcMmoFarmingService()
                .map(svc -> svc.getFarmingLevel(player))
                .orElse(0);
    }

    private int getFarmingXp(@Nullable Player player) {
        if (player == null) return 0;
        return services.mcMmoFarmingService()
                .map(svc -> svc.getFarmingXp(player))
                .orElse(0);
    }

    private int getFarmingXpNext(@Nullable Player player) {
        if (player == null) return 0;
        return services.mcMmoFarmingService()
                .map(svc -> svc.getFarmingXpToNextLevel(player))
                .orElse(0);
    }

    private double getFarmingProgressRatio(@Nullable Player player) {
        if (player == null) return 0.0;
        return services.mcMmoFarmingService()
                .map(svc -> clamp01(svc.getProgressToNextLevel(player)))
                .orElse(0.0);
    }

    private long getFarmingXpPercent(@Nullable Player player) {
        return Math.round(getFarmingProgressRatio(player) * 100.0);
    }

    private double getFarmingFortune(@Nullable Player player) {
        if (player == null) return 0.0;
        double gearFortune = services.itemStatProvider()
                .map(provider -> provider.getPlayerEquippedStats(player))
                .map(SkyItemStats::farmingFortune)
                .orElse(0.0);
        double petFortune = services.petStatProvider()
                .map(provider -> provider.getActivePetStats(player))
                .map(SkyItemStats::farmingFortune)
                .orElse(0.0);
        return Math.max(0.0, gearFortune + petFortune);
    }

    @NotNull
    private String getCurrentToolName(@Nullable Player player) {
        if (player == null) return "None";
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            return "None";
        }
        ItemMeta meta = hand.hasItemMeta() ? hand.getItemMeta() : null;
        String rawName = null;
        if (meta != null && meta.hasDisplayName()) {
            Component display = meta.displayName();
            if (display != null) {
                rawName = PLAIN_SERIALIZER.serialize(display);
            }
        }
        if ((rawName == null || rawName.isBlank()) && services.itemStatProvider().isPresent()) {
            CustomItemStatProvider provider = services.itemStatProvider().get();
            Optional<String> customId = provider.getCustomItemId(hand);
            if (customId.isPresent()) {
                rawName = humanizeEnumName(customId.get());
            }
        }
        if (rawName == null || rawName.isBlank()) {
            rawName = humanizeEnumName(hand.getType().name());
        }
        rawName = rawName.trim();
        if (rawName.length() > MAX_TOOL_NAME_LEN) {
            return rawName.substring(0, MAX_TOOL_NAME_LEN - 3) + "...";
        }
        return rawName;
    }

    @NotNull
    private Optional<Garden> resolveGarden(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        Optional<GardenService> optGardenService = services.gardenService();
        if (optGardenService.isEmpty()) return Optional.empty();
        GardenService gardenService = optGardenService.get();

        if (onlinePlayer != null) {
            Location loc = onlinePlayer.getLocation();
            Optional<Garden> atLoc = gardenService.getGardenAt(loc);
            if (atLoc.isPresent()) return atLoc;
        }
        if (offlinePlayer != null) {
            return gardenService.getGardenByPlayer(offlinePlayer.getUniqueId());
        }
        return Optional.empty();
    }

    @NotNull
    private String resolveGardenUnlocked(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        Optional<Garden> garden = resolveGarden(offlinePlayer, onlinePlayer);
        if (garden.isEmpty()) return "None";
        return garden.get().unlocked() ? "Unlocked" : "Locked";
    }

    private int countGardenVisitors(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        Optional<Garden> optGarden = resolveGarden(offlinePlayer, onlinePlayer);
        if (optGarden.isEmpty()) return 0;
        Garden garden = optGarden.get();

        Optional<Island> ownerIsland = services.islandService()
                .flatMap(svc -> svc.getIsland(garden.islandId()));

        int visitors = 0;
        for (Player candidate : services.onlinePlayers()) {
            if (!garden.isInRegion(candidate.getLocation())) continue;
            UUID candidateId = candidate.getUniqueId();
            boolean isMember = ownerIsland.map(island -> island.isMember(candidateId))
                    .orElse(offlinePlayer != null && candidateId.equals(offlinePlayer.getUniqueId()));
            if (!isMember) {
                visitors++;
            }
        }
        return visitors;
    }

    private int countUnlockedGardenPlots(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        Optional<Garden> optGarden = resolveGarden(offlinePlayer, onlinePlayer);
        Optional<GardenService> optGardenService = services.gardenService();
        if (optGarden.isEmpty() || optGardenService.isEmpty()) return 0;

        int unlocked = 0;
        for (GardenPlot plot : optGardenService.get().getPlots(optGarden.get().islandId())) {
            if (plot.unlocked()) unlocked++;
        }
        return unlocked;
    }

    private int getTotalGardenPlots(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        Optional<Garden> optGarden = resolveGarden(offlinePlayer, onlinePlayer);
        Optional<GardenService> optGardenService = services.gardenService();
        if (optGarden.isEmpty() || optGardenService.isEmpty()) {
            return DEFAULT_GARDEN_PLOTS_TOTAL;
        }
        int total = optGardenService.get().getPlots(optGarden.get().islandId()).size();
        return total > 0 ? total : DEFAULT_GARDEN_PLOTS_TOTAL;
    }

    @NotNull
    private String resolveActiveEvent(@Nullable OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) return "None";
        boolean surgeActive = services.gardenAbilityService()
                .map(svc -> svc.isSurgeActive(offlinePlayer.getUniqueId()))
                .orElse(false);
        return surgeActive ? "Sakura Surge" : "None";
    }

    private int getMiningLevel(@Nullable Player player) {
        if (player == null) return 0;
        return services.getMcMmoSkillLevel(player, "MINING");
    }

    private int getCombatLevel(@Nullable Player player) {
        if (player == null) return 0;
        int swords = services.getMcMmoSkillLevel(player, "SWORDS");
        int axes = services.getMcMmoSkillLevel(player, "AXES");
        int archery = services.getMcMmoSkillLevel(player, "ARCHERY");
        int unarmed = services.getMcMmoSkillLevel(player, "UNARMED");
        return Math.max(Math.max(swords, axes), Math.max(archery, unarmed));
    }

    @NotNull
    private Optional<Island> resolveIsland(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        Optional<IslandService> optIslandService = services.islandService();
        if (optIslandService.isEmpty()) return Optional.empty();
        IslandService islandService = optIslandService.get();

        if (onlinePlayer != null) {
            Optional<Island> atLoc = islandService.getIslandAt(onlinePlayer.getLocation());
            if (atLoc.isPresent()) return atLoc;
        }
        if (offlinePlayer != null) {
            return islandService.getIslandByPlayer(offlinePlayer.getUniqueId());
        }
        return Optional.empty();
    }

    @NotNull
    private String resolveIslandLevel(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        return resolveIsland(offlinePlayer, onlinePlayer)
                .map(island -> formatWholeNumber(island.level()))
                .orElse("0");
    }

    @NotNull
    private String resolveIslandRank(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        Optional<Island> optIsland = resolveIsland(offlinePlayer, onlinePlayer);
        Optional<IslandService> optIslandService = services.islandService();
        if (optIsland.isEmpty() || optIslandService.isEmpty()) {
            return "Unranked";
        }
        Island target = optIsland.get();
        long targetLevel = target.level();
        int higherCount = 0;
        for (Island other : optIslandService.get().getAllActiveIslands()) {
            if (!other.id().equals(target.id()) && other.level() > targetLevel) {
                higherCount++;
            }
        }
        return "#" + (higherCount + 1);
    }

    @NotNull
    private String resolveIslandMembers(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        return resolveIsland(offlinePlayer, onlinePlayer)
                .map(island -> String.valueOf(island.members().size()))
                .orElse("0");
    }

    private int countIslandVisitors(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        Optional<Island> optIsland = resolveIsland(offlinePlayer, onlinePlayer);
        if (optIsland.isEmpty()) return 0;
        Island island = optIsland.get();

        int visitors = 0;
        for (Player candidate : services.onlinePlayers()) {
            Location loc = candidate.getLocation();
            World world = loc.getWorld();
            if (world == null) continue;
            if (island.location().contains(world.getName(), loc.getBlockX(), loc.getBlockZ(), island.size())
                    && !island.isMember(candidate.getUniqueId())) {
                visitors++;
            }
        }
        return visitors;
    }

    @NotNull
    private String resolveIslandOwner(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        Optional<Island> optIsland = resolveIsland(offlinePlayer, onlinePlayer);
        if (optIsland.isEmpty()) return "None";
        String ownerName = services.resolvePlayerName(optIsland.get().ownerUuid());
        return ownerName != null && !ownerName.isBlank() ? ownerName : "Unknown";
    }

    @NotNull
    private String resolveIslandName(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        return resolveIsland(offlinePlayer, onlinePlayer)
                .map(Island::name)
                .filter(name -> !name.isBlank())
                .orElse("None");
    }

    @NotNull
    private String resolveCurrency(
            @Nullable OfflinePlayer offlinePlayer,
            @Nullable Player onlinePlayer,
            @NotNull String currencyId,
            boolean compact
    ) {
        if (offlinePlayer == null) {
            return "0";
        }
        // Query live EconomyService cache only when the player is online to avoid synchronous DB reads off-cache.
        if (onlinePlayer == null && !offlinePlayer.isOnline()) {
            return "0";
        }
        BigDecimal balance = BigDecimal.ZERO;
        Optional<EconomyService> economyService = services.economyService();
        if (economyService.isPresent()) {
            balance = economyService.get().getCachedBalance(offlinePlayer.getUniqueId(), currencyId);
        } else if (services.economyProvider().isPresent()) {
            balance = services.economyProvider().get().getBalance(offlinePlayer.getUniqueId(), currencyId);
        }
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        return compact ? formatCompact(balance.doubleValue()) : formatBalance(balance);
    }

    @NotNull
    private String resolveRank(@Nullable OfflinePlayer offlinePlayer, @Nullable Player onlinePlayer) {
        String rawGroup = null;
        if (onlinePlayer != null && services.vaultPermission().isPresent()) {
            try {
                rawGroup = services.vaultPermission().get().getPrimaryGroup(onlinePlayer);
            } catch (RuntimeException ignored) {
                rawGroup = null;
            }
        }
        if ((rawGroup == null || rawGroup.isBlank()) && offlinePlayer != null && services.permissionsProvider().isPresent()) {
            rawGroup = services.permissionsProvider().get()
                    .getPrimaryGroup(offlinePlayer.getUniqueId())
                    .orElse(null);
        }
        if (rawGroup == null || rawGroup.isBlank()) {
            return "Default";
        }
        return humanizeRankName(rawGroup);
    }

    @NotNull
    private String resolveProfile(@Nullable OfflinePlayer offlinePlayer) {
        if (offlinePlayer == null) return "Solo";
        return services.playerService()
                .flatMap(svc -> svc.getCached(offlinePlayer.getUniqueId()))
                .map(profile -> "Solo")
                .orElse("Loading...");
    }

    @NotNull
    private String resolveLocationName(@Nullable Player player) {
        if (player == null) return "SkyRealms";
        String worldName = player.getWorld().getName().toLowerCase(Locale.ROOT);
        return switch (worldName) {
            case "world", "skyrealms_arrival" -> "Adventure Hub";
            case "skyrealms_garden" -> "The Garden";
            case "skyrealms_islands" -> "Your Island";
            default -> humanizeEnumName(worldName);
        };
    }

    @NotNull
    static String formatWholeNumber(long value) {
        return new DecimalFormat("#,##0", US_SYMBOLS).format(value);
    }

    @NotNull
    static String formatBalance(@NotNull BigDecimal value) {
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        if (scaled.scale() <= 0) {
            return new DecimalFormat("#,##0", US_SYMBOLS).format(scaled);
        }
        return new DecimalFormat("#,##0.##", US_SYMBOLS).format(scaled);
    }

    @NotNull
    static String formatCompact(double value) {
        if (value < 1_000.0) {
            return formatWholeNumber(Math.round(value));
        }
        if (value < 1_000_000.0) {
            return new DecimalFormat("0.#", US_SYMBOLS).format(value / 1_000.0) + "K";
        }
        if (value < 1_000_000_000.0) {
            return new DecimalFormat("0.##", US_SYMBOLS).format(value / 1_000_000.0) + "M";
        }
        return new DecimalFormat("0.##", US_SYMBOLS).format(value / 1_000_000_000.0) + "B";
    }

    @NotNull
    static String renderProgressBar(double ratio) {
        double clamped = clamp01(ratio);
        int filled = (int) Math.round(clamped * 10.0);
        StringBuilder sb = new StringBuilder(24);
        sb.append("&a");
        for (int i = 0; i < filled; i++) {
            sb.append('■');
        }
        if (filled < 10) {
            sb.append("&7");
            for (int i = filled; i < 10; i++) {
                sb.append('□');
            }
        }
        return sb.toString();
    }

    @NotNull
    private static String humanizeEnumName(@NotNull String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("[_-]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.isEmpty() ? raw : sb.toString();
    }

    @NotNull
    private static String humanizeRankName(@NotNull String rawGroup) {
        String trimmed = rawGroup.trim();
        if (trimmed.equalsIgnoreCase("mvp+")) return "MVP+";
        if (trimmed.equalsIgnoreCase("mvp++")) return "MVP++";
        if (trimmed.equalsIgnoreCase("vip+")) return "VIP+";
        if (trimmed.equalsIgnoreCase("mvp") || trimmed.equalsIgnoreCase("vip") || trimmed.equalsIgnoreCase("mod")) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return humanizeEnumName(trimmed);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || value <= 0.0) return 0.0;
        return Math.min(1.0, value);
    }
}
