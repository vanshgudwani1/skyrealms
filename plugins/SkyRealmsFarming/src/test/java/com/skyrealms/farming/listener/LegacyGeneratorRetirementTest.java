package com.skyrealms.farming.listener;

import com.skyrealms.farming.model.FarmingCoreItemFactory;
import com.skyrealms.farming.model.FarmingGeneratorTier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyGeneratorRetirementTest {

    @Test
    @DisplayName("Null or plain item without meta must NOT be identified as legacy farming core")
    void nullOrPlainItemsAreNotCores() {
        assertThat(FarmingCoreItemFactory.isFarmingCore(null)).isFalse();

        ItemStack plainItem = mock(ItemStack.class);
        when(plainItem.hasItemMeta()).thenReturn(false);
        assertThat(FarmingCoreItemFactory.isFarmingCore(plainItem)).isFalse();
    }

    @Test
    @DisplayName("Items without CORE_KEY persistent data tag must NOT be identified as farming cores")
    void itemsWithoutCoreTagAreNotCores() {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(FarmingCoreItemFactory.CORE_KEY, PersistentDataType.STRING)).thenReturn(false);

        assertThat(FarmingCoreItemFactory.isFarmingCore(item)).isFalse();
    }

    @Test
    @DisplayName("Items with authentic CORE_KEY tag must be recognized and tier correctly extracted")
    void authenticCoresAreRecognized() {
        ItemStack coreItem = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(coreItem.hasItemMeta()).thenReturn(true);
        when(coreItem.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(FarmingCoreItemFactory.CORE_KEY, PersistentDataType.STRING)).thenReturn(true);
        when(pdc.get(FarmingCoreItemFactory.TIER_KEY, PersistentDataType.STRING)).thenReturn("TIER_2_ROOT");

        assertThat(FarmingCoreItemFactory.isFarmingCore(coreItem)).isTrue();
        assertThat(FarmingCoreItemFactory.extractTier(coreItem)).isEqualTo(FarmingGeneratorTier.TIER_2_ROOT);
    }
}
