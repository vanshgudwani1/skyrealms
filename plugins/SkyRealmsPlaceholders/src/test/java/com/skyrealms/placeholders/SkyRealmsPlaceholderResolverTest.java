package com.skyrealms.placeholders;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.GardenService;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.mcmmo.McMMOFarmingService;
import com.skyrealms.islands.model.Island;
import com.skyrealms.islands.model.IslandLocation;
import com.skyrealms.islands.model.IslandMember;
import com.skyrealms.islands.service.IslandService;
import com.skyrealms.platform.integrations.CustomItemStatProvider;
import com.skyrealms.platform.integrations.EconomyProvider;
import com.skyrealms.platform.integrations.PermissionsProvider;
import com.skyrealms.platform.integrations.PetStatProvider;
import com.skyrealms.platform.integrations.SkyItemStats;
import com.skyrealms.player.model.PlayerProfile;
import com.skyrealms.player.service.PlayerService;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkyRealmsPlaceholderResolverTest {

    private SkyRealmsServiceAccess services;
    private SkyRealmsPlaceholderResolver resolver;
    private Player player;
    private UUID playerId;
    private Location gardenLoc;
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() {
        services = mock(SkyRealmsServiceAccess.class);
        when(services.playerService()).thenReturn(Optional.empty());
        when(services.economyService()).thenReturn(Optional.empty());
        when(services.economyProvider()).thenReturn(Optional.empty());
        when(services.islandService()).thenReturn(Optional.empty());
        when(services.gardenService()).thenReturn(Optional.empty());
        when(services.mcMmoFarmingService()).thenReturn(Optional.empty());
        when(services.gardenAbilityService()).thenReturn(Optional.empty());
        when(services.itemStatProvider()).thenReturn(Optional.empty());
        when(services.petStatProvider()).thenReturn(Optional.empty());
        when(services.permissionsProvider()).thenReturn(Optional.empty());
        when(services.vaultPermission()).thenReturn(Optional.empty());
        when(services.onlinePlayers()).thenReturn(List.of());
        when(services.maxPlayers()).thenReturn(20);

        resolver = new SkyRealmsPlaceholderResolver(services);

        playerId = UUID.randomUUID();
        player = mock(Player.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("skyrealms_garden");
        gardenLoc = new Location(world, 10, 80, 10);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("DJHeaveen");
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(gardenLoc);

        inventory = mock(PlayerInventory.class);
        ItemStack emptyHand = mock(ItemStack.class);
        when(emptyHand.getType()).thenReturn(Material.AIR);
        when(inventory.getItemInMainHand()).thenReturn(emptyHand);
        when(player.getInventory()).thenReturn(inventory);
    }

    @Test
    void resolvesFarmingGardenAndToolMetricsFromLiveServices() {
        McMMOFarmingService mcMmo = mock(McMMOFarmingService.class);
        when(mcMmo.getFarmingLevel(player)).thenReturn(24);
        when(mcMmo.getFarmingXp(player)).thenReturn(1350);
        when(mcMmo.getFarmingXpToNextLevel(player)).thenReturn(3000);
        when(mcMmo.getProgressToNextLevel(player)).thenReturn(0.45);
        when(services.mcMmoFarmingService()).thenReturn(Optional.of(mcMmo));

        CustomItemStatProvider itemStats = mock(CustomItemStatProvider.class);
        when(itemStats.getPlayerEquippedStats(player))
                .thenReturn(SkyItemStats.builder().farmingFortune(85.0).build());
        when(services.itemStatProvider()).thenReturn(Optional.of(itemStats));

        PetStatProvider petStats = mock(PetStatProvider.class);
        when(petStats.getActivePetStats(player))
                .thenReturn(SkyItemStats.builder().farmingFortune(35.0).build());
        when(services.petStatProvider()).thenReturn(Optional.of(petStats));

        ItemStack hoe = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(hoe.getType()).thenReturn(Material.DIAMOND_HOE);
        when(hoe.getAmount()).thenReturn(1);
        when(hoe.hasItemMeta()).thenReturn(true);
        when(hoe.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.displayName()).thenReturn(Component.text("Emerald Hoe [Lv.3]"));
        when(inventory.getItemInMainHand()).thenReturn(hoe);

        GardenService gardenService = mock(GardenService.class);
        UUID islandId = UUID.randomUUID();
        Garden garden = mock(Garden.class);
        when(garden.islandId()).thenReturn(islandId);
        when(garden.unlocked()).thenReturn(true);
        when(garden.isInRegion(any(Location.class))).thenReturn(true);
        when(gardenService.getGardenAt(any(Location.class))).thenReturn(Optional.of(garden));

        GardenPlot p1 = mock(GardenPlot.class);
        when(p1.unlocked()).thenReturn(true);
        GardenPlot p2 = mock(GardenPlot.class);
        when(p2.unlocked()).thenReturn(true);
        GardenPlot p3 = mock(GardenPlot.class);
        when(p3.unlocked()).thenReturn(false);
        when(gardenService.getPlots(islandId)).thenReturn(List.of(p1, p2, p3));
        when(services.gardenService()).thenReturn(Optional.of(gardenService));

        Player visitor = mock(Player.class);
        UUID visitorId = UUID.randomUUID();
        when(visitor.getUniqueId()).thenReturn(visitorId);
        when(visitor.getLocation()).thenReturn(gardenLoc);
        Mockito.<Collection<? extends Player>>when(services.onlinePlayers()).thenReturn(List.of(player, visitor));

        GardenAbilityService abilityService = mock(GardenAbilityService.class);
        when(abilityService.isSurgeActive(playerId)).thenReturn(true);
        when(services.gardenAbilityService()).thenReturn(Optional.of(abilityService));

        assertThat(resolver.resolve(player, "farming_level")).isEqualTo("24");
        assertThat(resolver.resolve(player, "farming_xp")).isEqualTo("1,350");
        assertThat(resolver.resolve(player, "farming_xp_next")).isEqualTo("3,000");
        assertThat(resolver.resolve(player, "farming_xp_percent")).isEqualTo("45%");
        assertThat(resolver.resolve(player, "farming_fortune")).isEqualTo("120");
        assertThat(resolver.resolve(player, "current_tool")).isEqualTo("Emerald Hoe [Lv.3]");
        assertThat(resolver.resolve(player, "garden_level")).isEqualTo("N/A");
        assertThat(resolver.resolve(player, "garden_unlocked")).isEqualTo("Unlocked");
        assertThat(resolver.resolve(player, "garden_plots_unlocked")).isEqualTo("2");
        assertThat(resolver.resolve(player, "garden_plots_total")).isEqualTo("3");
        assertThat(resolver.resolve(player, "garden_visitors")).isEqualTo("1");
        assertThat(resolver.resolve(player, "garden_event")).isEqualTo("Sakura Surge");
        assertThat(resolver.resolve(player, "location")).isEqualTo("The Garden");
    }

    @Test
    void resolvesIslandEconomyRankAndCombatMiningMetrics() {
        IslandService islandService = mock(IslandService.class);
        UUID islandId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Island playerIsland = mock(Island.class);
        when(playerIsland.id()).thenReturn(islandId);
        when(playerIsland.ownerUuid()).thenReturn(ownerId);
        when(playerIsland.name()).thenReturn("Sakura Isle");
        when(playerIsland.level()).thenReturn(420L);
        when(playerIsland.size()).thenReturn(150);
        when(playerIsland.location()).thenReturn(new IslandLocation("skyrealms_islands", 0, 100, 0));
        UUID coMemberId = UUID.randomUUID();
        when(playerIsland.members()).thenReturn(java.util.Map.of(
                playerId, new IslandMember(islandId, playerId, com.skyrealms.islands.model.IslandRole.LEADER, java.time.Instant.now()),
                coMemberId, new IslandMember(islandId, coMemberId, com.skyrealms.islands.model.IslandRole.MEMBER, java.time.Instant.now())
        ));
        when(playerIsland.isMember(playerId)).thenReturn(true);

        Island higherIsland = mock(Island.class);
        when(higherIsland.id()).thenReturn(UUID.randomUUID());
        when(higherIsland.level()).thenReturn(900L);

        when(islandService.getIslandAt(any(Location.class))).thenReturn(Optional.empty());
        when(islandService.getIslandByPlayer(playerId)).thenReturn(Optional.of(playerIsland));
        when(islandService.getAllActiveIslands()).thenReturn(List.of(playerIsland, higherIsland));
        when(services.islandService()).thenReturn(Optional.of(islandService));
        when(services.resolvePlayerName(ownerId)).thenReturn("Vansh");

        Player outsider = mock(Player.class);
        World islandWorld = mock(World.class);
        when(islandWorld.getName()).thenReturn("skyrealms_islands");
        when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());
        when(outsider.getLocation()).thenReturn(new Location(islandWorld, 10, 100, 10));
        Mockito.<Collection<? extends Player>>when(services.onlinePlayers()).thenReturn(List.of(player, outsider));

        EconomyProvider eco = mock(EconomyProvider.class);
        when(eco.getBalance(playerId, "coins")).thenReturn(new BigDecimal("12500.50"));
        when(eco.getBalance(playerId, "gems")).thenReturn(new BigDecimal("350"));
        when(services.economyProvider()).thenReturn(Optional.of(eco));

        Permission vaultPerm = mock(Permission.class);
        when(vaultPerm.getPrimaryGroup(player)).thenReturn("admin");
        when(services.vaultPermission()).thenReturn(Optional.of(vaultPerm));

        PlayerService playerService = mock(PlayerService.class);
        when(playerService.getCached(playerId)).thenReturn(Optional.of(mock(PlayerProfile.class)));
        when(services.playerService()).thenReturn(Optional.of(playerService));

        when(services.getMcMmoSkillLevel(player, "MINING")).thenReturn(12);
        when(services.getMcMmoSkillLevel(player, "SWORDS")).thenReturn(19);
        when(services.getMcMmoSkillLevel(player, "AXES")).thenReturn(5);

        assertThat(resolver.resolve(player, "island_level")).isEqualTo("420");
        assertThat(resolver.resolve(player, "island_rank")).isEqualTo("#2");
        assertThat(resolver.resolve(player, "island_members")).isEqualTo("2");
        assertThat(resolver.resolve(player, "island_visitors")).isEqualTo("1");
        assertThat(resolver.resolve(player, "island_owner")).isEqualTo("Vansh");
        assertThat(resolver.resolve(player, "island_name")).isEqualTo("Sakura Isle");
        assertThat(resolver.resolve(player, "coins")).isEqualTo("12,500.5");
        assertThat(resolver.resolve(player, "coins_short")).isEqualTo("12.5K");
        assertThat(resolver.resolve(player, "gems")).isEqualTo("350");
        assertThat(resolver.resolve(player, "rank")).isEqualTo("Admin");
        assertThat(resolver.resolve(player, "profile")).isEqualTo("Solo");
        assertThat(resolver.resolve(player, "mining_level")).isEqualTo("12");
        assertThat(resolver.resolve(player, "combat_level")).isEqualTo("19");
        assertThat(resolver.resolve(player, "max_players")).isEqualTo("20");
    }

    @Test
    void returnsSafeFallbacksWhenPlayerOfflineOrServicesMissing() {
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getUniqueId()).thenReturn(UUID.randomUUID());
        when(offline.isOnline()).thenReturn(false);

        PermissionsProvider permissions = mock(PermissionsProvider.class);
        when(permissions.getPrimaryGroup(offline.getUniqueId())).thenReturn(Optional.of("vip+"));
        when(services.permissionsProvider()).thenReturn(Optional.of(permissions));

        assertThat(resolver.resolve(offline, "farming_level")).isEqualTo("0");
        assertThat(resolver.resolve(offline, "farming_fortune")).isEqualTo("0");
        assertThat(resolver.resolve(offline, "current_tool")).isEqualTo("None");
        assertThat(resolver.resolve(offline, "island_rank")).isEqualTo("Unranked");
        assertThat(resolver.resolve(offline, "island_owner")).isEqualTo("None");
        assertThat(resolver.resolve(offline, "coins")).isEqualTo("0");
        assertThat(resolver.resolve(offline, "gems")).isEqualTo("0");
        assertThat(resolver.resolve(offline, "rank")).isEqualTo("VIP+");
        assertThat(resolver.resolve(offline, "profile")).isEqualTo("Loading...");
        assertThat(resolver.resolve(offline, eqOrUnknown())).isNull();
    }

    private String eqOrUnknown() {
        return "does_not_exist";
    }
}
