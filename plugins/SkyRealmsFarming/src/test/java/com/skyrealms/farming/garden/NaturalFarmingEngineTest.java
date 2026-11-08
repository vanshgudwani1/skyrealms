package com.skyrealms.farming.garden;

import com.skyrealms.farming.service.FarmingProgressionService;
import com.skyrealms.platform.api.event.EventBus;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NaturalFarmingEngineTest {

    private Plugin plugin;
    private GardenService gardenService;
    private FarmingProgressionService progressionService;
    private EventBus eventBus;
    private NaturalFarmingEngine engine;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        gardenService = mock(GardenService.class);
        progressionService = mock(FarmingProgressionService.class);
        eventBus = mock(EventBus.class);

        FileConfiguration config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);

        engine = new NaturalFarmingEngine(plugin, gardenService, progressionService, eventBus);
    }

    @Test
    @DisplayName("Should accurately identify supported vanilla crop materials")
    void shouldIdentifyCropMaterials() {
        assertThat(engine.isCropMaterial(Material.WHEAT)).isTrue();
        assertThat(engine.isCropMaterial(Material.CARROTS)).isTrue();
        assertThat(engine.isCropMaterial(Material.POTATOES)).isTrue();
        assertThat(engine.isCropMaterial(Material.BEETROOTS)).isTrue();
        assertThat(engine.isCropMaterial(Material.MELON)).isTrue();
        assertThat(engine.isCropMaterial(Material.PUMPKIN)).isTrue();
        assertThat(engine.isCropMaterial(Material.SUGAR_CANE)).isTrue();
        assertThat(engine.isCropMaterial(Material.CACTUS)).isTrue();
        assertThat(engine.isCropMaterial(Material.COCOA)).isTrue();
        assertThat(engine.isCropMaterial(Material.NETHER_WART)).isTrue();

        assertThat(engine.isCropMaterial(Material.DIRT)).isFalse();
        assertThat(engine.isCropMaterial(Material.STONE)).isFalse();
        assertThat(engine.isCropMaterial(Material.OAK_LOG)).isFalse();
    }

    @Test
    @DisplayName("Should return canonical crop IDs")
    void shouldReturnCanonicalCropIds() {
        assertThat(engine.getCropId(Material.WHEAT)).isEqualTo("WHEAT");
        assertThat(engine.getCropId(Material.CARROTS)).isEqualTo("CARROTS");
        assertThat(engine.getCropId(Material.POTATOES)).isEqualTo("POTATOES");
        assertThat(engine.getCropId(Material.BEETROOTS)).isEqualTo("BEETROOTS");
        assertThat(engine.getCropId(Material.MELON)).isEqualTo("MELON");
        assertThat(engine.getCropId(Material.PUMPKIN)).isEqualTo("PUMPKIN");
        assertThat(engine.getCropId(Material.SUGAR_CANE)).isEqualTo("SUGAR_CANE");
        assertThat(engine.getCropId(Material.NETHER_WART)).isEqualTo("NETHER_WART");
    }

    @Test
    @DisplayName("Should identify replant materials for ageable crops")
    void shouldIdentifyReplantMaterials() {
        assertThat(engine.getReplantCrop(Material.WHEAT)).isEqualTo(Material.WHEAT);
        assertThat(engine.getReplantCrop(Material.CARROTS)).isEqualTo(Material.CARROTS);
        assertThat(engine.getReplantCrop(Material.POTATOES)).isEqualTo(Material.POTATOES);
        assertThat(engine.getReplantCrop(Material.BEETROOTS)).isEqualTo(Material.BEETROOTS);
        assertThat(engine.getReplantCrop(Material.NETHER_WART)).isEqualTo(Material.NETHER_WART);

        // Melon & Pumpkin do not replant fruit blocks (the stem produces fruit)
        assertThat(engine.getReplantCrop(Material.MELON)).isNull();
        assertThat(engine.getReplantCrop(Material.PUMPKIN)).isNull();
    }

    @Test
    @DisplayName("Anti-exploit should track and clear player placed blocks")
    void antiExploitShouldTrackPlayerPlacedBlocks() {
        Block block = mock(Block.class);
        when(block.getX()).thenReturn(10);
        when(block.getY()).thenReturn(100);
        when(block.getZ()).thenReturn(-20);

        assertThat(engine.isPlayerPlaced(block)).isFalse();

        engine.markPlayerPlaced(block);
        assertThat(engine.isPlayerPlaced(block)).isTrue();

        engine.clearPlayerPlaced(block);
        assertThat(engine.isPlayerPlaced(block)).isFalse();
    }

    @Test
    @DisplayName("Maturity check should require maximum age for Ageable crops")
    void maturityCheckShouldVerifyAgeableMaximumAge() {
        Block matureBlock = mock(Block.class);
        Ageable matureData = mock(Ageable.class);
        when(matureData.getAge()).thenReturn(7);
        when(matureData.getMaximumAge()).thenReturn(7);
        when(matureBlock.getBlockData()).thenReturn(matureData);
        when(matureBlock.getType()).thenReturn(Material.WHEAT);

        assertThat(engine.isMature(matureBlock)).isTrue();

        Block immatureBlock = mock(Block.class);
        Ageable immatureData = mock(Ageable.class);
        when(immatureData.getAge()).thenReturn(3);
        when(immatureData.getMaximumAge()).thenReturn(7);
        when(immatureBlock.getBlockData()).thenReturn(immatureData);
        when(immatureBlock.getType()).thenReturn(Material.WHEAT);

        assertThat(engine.isMature(immatureBlock)).isFalse();
    }

    @Test
    @DisplayName("Player-placed melons and pumpkins must NOT be recognized as mature")
    void playerPlacedMelonsMustNotBeMature() {
        Block melonBlock = mock(Block.class);
        when(melonBlock.getX()).thenReturn(5);
        when(melonBlock.getY()).thenReturn(100);
        when(melonBlock.getZ()).thenReturn(5);
        when(melonBlock.getType()).thenReturn(Material.MELON);
        when(melonBlock.getBlockData()).thenReturn(mock(org.bukkit.block.data.BlockData.class));

        // Naturally grown (not player placed) -> mature
        assertThat(engine.isMature(melonBlock)).isTrue();

        // Player placed -> NOT mature (blocked from XP/collections)
        engine.markPlayerPlaced(melonBlock);
        assertThat(engine.isMature(melonBlock)).isFalse();
    }
}
