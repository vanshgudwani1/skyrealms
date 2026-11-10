package com.skyrealms.items;

import com.skyrealms.items.catalog.ItemCatalog;
import com.skyrealms.items.factory.SkyRealmsItemFactory;
import com.skyrealms.items.listener.CustomEnchantEffectListener;
import com.skyrealms.items.listener.VanillaEnchantBlockListener;
import com.skyrealms.items.model.CustomItemDefinition;
import com.skyrealms.items.service.EliteEnchantsService;
import com.skyrealms.items.service.HoeUpgradeService;
import com.skyrealms.items.service.ItemRegistryService;
import com.skyrealms.platform.integrations.CustomEnchantProvider;
import com.skyrealms.platform.integrations.SkyItemStats;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemCatalogAndStatsTest {

    @Test
    @DisplayName("Should contain only the 3 upgradable Farming Hoes and zero armor sets")
    void shouldContainOnlyThreeFarmingHoes() {
        ItemCatalog catalog = new ItemCatalog();

        assertThat(catalog.all()).hasSize(3);
        assertThat(catalog.all().stream().map(CustomItemDefinition::id))
                .containsExactly(ItemCatalog.SPROUT_HOE, ItemCatalog.SAKURA_SCYTHE, ItemCatalog.VERDANT_REAPER);
        assertThat(catalog.allArmorSets()).isEmpty();
    }

    @Test
    @DisplayName("Should step through all hoe upgrade levels and tier evolutions cleanly")
    void shouldProgressThroughLevelsAndEvolutions() {
        ItemCatalog catalog = new ItemCatalog();
        SkyRealmsItemFactory factory = new SkyRealmsItemFactory(catalog);
        HoeUpgradeService upgrades = new HoeUpgradeService(catalog, factory);

        CustomItemDefinition sprout = catalog.find(ItemCatalog.SPROUT_HOE);
        assertThat(sprout).isNotNull();

        Optional<HoeUpgradeService.UpgradeStep> step1 = upgrades.nextUpgrade(sprout, 1);
        assertThat(step1).isPresent();
        assertThat(step1.get().isEvolution()).isFalse();
        assertThat(step1.get().targetLevel()).isEqualTo(2);
        assertThat(step1.get().targetDefinition().id()).isEqualTo(ItemCatalog.SPROUT_HOE);

        Optional<HoeUpgradeService.UpgradeStep> evolve1 = upgrades.nextUpgrade(sprout, 5);
        assertThat(evolve1).isPresent();
        assertThat(evolve1.get().isEvolution()).isTrue();
        assertThat(evolve1.get().targetDefinition().id()).isEqualTo(ItemCatalog.SAKURA_SCYTHE);
        assertThat(evolve1.get().targetLevel()).isEqualTo(1);

        CustomItemDefinition sakura = catalog.find(ItemCatalog.SAKURA_SCYTHE);
        assertThat(sakura).isNotNull();
        Optional<HoeUpgradeService.UpgradeStep> evolve2 = upgrades.nextUpgrade(sakura, 5);
        assertThat(evolve2).isPresent();
        assertThat(evolve2.get().isEvolution()).isTrue();
        assertThat(evolve2.get().targetDefinition().id()).isEqualTo(ItemCatalog.VERDANT_REAPER);
        assertThat(evolve2.get().targetLevel()).isEqualTo(1);

        CustomItemDefinition verdant = catalog.find(ItemCatalog.VERDANT_REAPER);
        assertThat(verdant).isNotNull();
        assertThat(upgrades.nextUpgrade(verdant, 5)).isEmpty();
    }

    @Test
    @DisplayName("Should scale Farming Fortune and XP stats accurately by held hoe upgrade level")
    void shouldScaleEquippedStatsByHoeLevel() {
        ItemCatalog catalog = new ItemCatalog();
        SkyRealmsItemFactory factory = new SkyRealmsItemFactory(catalog);
        ItemRegistryService service = new ItemRegistryService(catalog, factory);

        ItemStack sproutLv1 = mockTaggedHoe(ItemCatalog.SPROUT_HOE, Material.IRON_HOE, 1);
        ItemStack sproutLv5 = mockTaggedHoe(ItemCatalog.SPROUT_HOE, Material.IRON_HOE, 5);
        ItemStack reaperLv5 = mockTaggedHoe(ItemCatalog.VERDANT_REAPER, Material.NETHERITE_HOE, 5);

        SkyItemStats s1 = service.calculateEquippedStats(sproutLv1, null, null);
        assertThat(s1.farmingFortune()).isCloseTo(0.08, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(s1.farmingXpBonus()).isCloseTo(0.05, org.assertj.core.data.Offset.offset(0.0001));

        SkyItemStats s5 = service.calculateEquippedStats(sproutLv5, null, null);
        assertThat(s5.farmingFortune()).isCloseTo(0.16, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(s5.farmingXpBonus()).isCloseTo(0.10, org.assertj.core.data.Offset.offset(0.0001));

        SkyItemStats r5 = service.calculateEquippedStats(reaperLv5, null, null);
        assertThat(r5.farmingFortune()).isCloseTo(0.70, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(r5.farmingXpBonus()).isCloseTo(0.40, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(r5.speedBonus()).isCloseTo(0.08, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("Should block vanilla enchants on custom items in enchanting tables and anvils")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldBlockVanillaEnchantsOnCustomItems() {
        ItemCatalog catalog = new ItemCatalog();
        SkyRealmsItemFactory factory = new SkyRealmsItemFactory(catalog);
        VanillaEnchantBlockListener listener = new VanillaEnchantBlockListener(factory, true);

        ItemStack customHoe = mockTaggedHoe(ItemCatalog.SPROUT_HOE, Material.IRON_HOE, 1);
        ItemStack vanillaPick = mockPlainItem(Material.DIAMOND_PICKAXE);

        PrepareItemEnchantEvent prepCustom = mock(PrepareItemEnchantEvent.class);
        when(prepCustom.getItem()).thenReturn(customHoe);
        listener.onPrepareItemEnchant(prepCustom);
        verify(prepCustom).setCancelled(true);

        PrepareItemEnchantEvent prepVanilla = mock(PrepareItemEnchantEvent.class);
        when(prepVanilla.getItem()).thenReturn(vanillaPick);
        listener.onPrepareItemEnchant(prepVanilla);
        verify(prepVanilla, never()).setCancelled(true);

        EnchantItemEvent enchantCustom = mock(EnchantItemEvent.class);
        when(enchantCustom.getItem()).thenReturn(customHoe);
        when(enchantCustom.getEnchanter()).thenReturn(mock(Player.class));
        listener.onEnchantItem(enchantCustom);
        verify(enchantCustom).setCancelled(true);

        Map nonEmptyStoredEnchants = mock(Map.class);
        when(nonEmptyStoredEnchants.isEmpty()).thenReturn(false);

        ItemStack vanillaBook = mock(ItemStack.class);
        EnchantmentStorageMeta bookMeta = mock(EnchantmentStorageMeta.class);
        when(vanillaBook.getType()).thenReturn(Material.ENCHANTED_BOOK);
        when(vanillaBook.hasItemMeta()).thenReturn(true);
        when(vanillaBook.getEnchantments()).thenReturn(Map.of());
        when(vanillaBook.getItemMeta()).thenReturn(bookMeta);
        when(bookMeta.getStoredEnchants()).thenReturn(nonEmptyStoredEnchants);

        AnvilInventory anvilInv = mock(AnvilInventory.class);
        when(anvilInv.getFirstItem()).thenReturn(customHoe);
        when(anvilInv.getSecondItem()).thenReturn(vanillaBook);
        PrepareAnvilEvent anvilEvent = mock(PrepareAnvilEvent.class);
        when(anvilEvent.getInventory()).thenReturn(anvilInv);

        listener.onPrepareAnvil(anvilEvent);
        verify(anvilEvent).setResult(null);
    }

    @Test
    @DisplayName("Should strip vanilla enchants and preserve EliteEnchantments lore across upgrades")
    @SuppressWarnings("unchecked")
    void shouldSanitizeVanillaEnchantsAndPreserveCustomLoreOnUpgrade() {
        ItemCatalog catalog = new ItemCatalog();
        SkyRealmsItemFactory factory = new SkyRealmsItemFactory(catalog);

        ItemStack hoe = mock(ItemStack.class);
        ItemStack evolvedDiamond = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(hoe.getType()).thenReturn(Material.IRON_HOE);
        when(hoe.hasItemMeta()).thenReturn(true);
        when(hoe.getItemMeta()).thenReturn(meta);
        when(hoe.withType(Material.DIAMOND_HOE)).thenReturn(evolvedDiamond);

        when(evolvedDiamond.getType()).thenReturn(Material.DIAMOND_HOE);
        when(evolvedDiamond.hasItemMeta()).thenReturn(true);
        when(evolvedDiamond.getItemMeta()).thenReturn(meta);

        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(SkyRealmsItemFactory.CUSTOM_ITEM_KEY, PersistentDataType.STRING)).thenReturn(ItemCatalog.SPROUT_HOE);
        when(pdc.get(SkyRealmsItemFactory.ITEM_LEVEL_KEY, PersistentDataType.INTEGER)).thenReturn(5);
        when(meta.hasEnchants()).thenReturn(true, false);

        Component customEnchantLine = Component.text("Harvester III");
        when(meta.hasLore()).thenReturn(true);
        when(meta.lore()).thenReturn(List.of(
                customEnchantLine,
                Component.text("Farming Hoe • Tier Level 5/5"),
                Component.text("UNCOMMON HOE")
        ));

        // 1) Sanitize vanilla enchant
        assertThat(factory.sanitizeVanillaEnchants(hoe)).isTrue();
        verify(meta).removeEnchantments();
        verify(hoe).setItemMeta(meta);

        // 2) Evolve to Sakura Scythe Lv.1 while preserving external custom enchant line
        CustomItemDefinition sakura = catalog.find(ItemCatalog.SAKURA_SCYTHE);
        assertThat(sakura).isNotNull();
        ItemStack result = factory.upgradeItem(hoe, sakura, 1);

        assertThat(result).isSameAs(evolvedDiamond);
        verify(meta, org.mockito.Mockito.atLeastOnce()).setEnchantmentGlintOverride(Boolean.TRUE);
        verify(pdc).set(SkyRealmsItemFactory.CUSTOM_ITEM_KEY, PersistentDataType.STRING, ItemCatalog.SAKURA_SCYTHE);
        verify(pdc).set(SkyRealmsItemFactory.ITEM_LEVEL_KEY, PersistentDataType.INTEGER, 1);

        ArgumentCaptor<List<Component>> loreCaptor = ArgumentCaptor.forClass(List.class);
        verify(meta).lore(loreCaptor.capture());
        assertThat(loreCaptor.getValue().get(0)).isEqualTo(customEnchantLine);
    }

    @Test
    @DisplayName("Should protect immature crops and stems when Replenish is active")
    void shouldProtectImmatureSeedsAndStemsWithReplenish() {
        ItemStack hoeWithReplenish = mockTaggedHoe(ItemCatalog.SPROUT_HOE, Material.IRON_HOE, 1);
        ItemStack plainHoe = mockPlainItem(Material.IRON_HOE);

        EliteEnchantsService enchants = new EliteEnchantsService(
                (item, id) -> item == hoeWithReplenish && CustomEnchantProvider.ENCHANT_REPLENISH.equals(id)
        );
        CustomEnchantEffectListener listener = new CustomEnchantEffectListener(mock(Plugin.class), enchants);

        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);

        Block immatureWheat = mock(Block.class);
        Ageable ageable = mock(Ageable.class);
        when(immatureWheat.getType()).thenReturn(Material.WHEAT);
        when(immatureWheat.getBlockData()).thenReturn(ageable);
        when(ageable.getAge()).thenReturn(3);
        when(ageable.getMaximumAge()).thenReturn(7);

        BlockBreakEvent breakImmature = new BlockBreakEvent(immatureWheat, player);
        when(inv.getItemInMainHand()).thenReturn(hoeWithReplenish);
        listener.onProtectImmatureSeed(breakImmature);
        assertThat(breakImmature.isCancelled()).isTrue();

        // Melon stem should also be protected
        Block melonStem = mock(Block.class);
        when(melonStem.getType()).thenReturn(Material.MELON_STEM);
        BlockBreakEvent breakStem = new BlockBreakEvent(melonStem, player);
        listener.onProtectImmatureSeed(breakStem);
        assertThat(breakStem.isCancelled()).isTrue();

        // Sugar cane root (block below != SUGAR_CANE) should be protected
        Block caneRoot = mock(Block.class);
        Block dirtBelow = mock(Block.class);
        when(caneRoot.getType()).thenReturn(Material.SUGAR_CANE);
        when(caneRoot.getRelative(BlockFace.DOWN)).thenReturn(dirtBelow);
        when(dirtBelow.getType()).thenReturn(Material.SAND);
        BlockBreakEvent breakRoot = new BlockBreakEvent(caneRoot, player);
        listener.onProtectImmatureSeed(breakRoot);
        assertThat(breakRoot.isCancelled()).isTrue();

        // Tool without Replenish breaks normally
        when(inv.getItemInMainHand()).thenReturn(plainHoe);
        BlockBreakEvent breakWithoutEnchant = new BlockBreakEvent(immatureWheat, player);
        listener.onProtectImmatureSeed(breakWithoutEnchant);
        assertThat(breakWithoutEnchant.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Should deliver items straight to inventory for Telekinesis and consume 1 seed for Replenish")
    void shouldDeliverTelekinesisAndConsumeSeedForReplenish() {
        EliteEnchantsService enchants = new EliteEnchantsService((item, id) -> true);

        Player player = mock(Player.class);
        PlayerInventory inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        World world = mock(World.class);
        Location fallback = new Location(world, 10, 64, 10);

        ItemStack wheat = mock(ItemStack.class);
        ItemStack wheatClone = mock(ItemStack.class);
        when(wheat.getType()).thenReturn(Material.WHEAT);
        when(wheat.getAmount()).thenReturn(2);
        when(wheat.clone()).thenReturn(wheatClone);
        when(wheatClone.getAmount()).thenReturn(2);
        when(inv.addItem(wheatClone)).thenReturn(new HashMap<>());

        enchants.deliverOrDrop(player, fallback, List.of(wheat), true);
        verify(inv).addItem(wheatClone);
        verify(world, never()).dropItemNaturally(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        // Seed consumption from drops list
        ItemStack seedDrop = mock(ItemStack.class);
        when(seedDrop.getType()).thenReturn(Material.WHEAT_SEEDS);
        when(seedDrop.getAmount()).thenReturn(2);
        List<ItemStack> drops = new ArrayList<>(List.of(seedDrop));
        assertThat(EliteEnchantsService.consumeOneSeed(player, Material.WHEAT_SEEDS, drops)).isTrue();
        verify(seedDrop).setAmount(1);
    }

    private static ItemStack mockTaggedHoe(String customId, Material material, int level) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.getType()).thenReturn(material);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(stack.getEnchantments()).thenReturn(Map.of());
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(SkyRealmsItemFactory.CUSTOM_ITEM_KEY, PersistentDataType.STRING)).thenReturn(customId);
        when(pdc.get(SkyRealmsItemFactory.ITEM_LEVEL_KEY, PersistentDataType.INTEGER)).thenReturn(level);
        return stack;
    }

    private static ItemStack mockPlainItem(Material material) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.hasItemMeta()).thenReturn(false);
        return stack;
    }
}
