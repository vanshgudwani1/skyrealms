package com.skyrealms.farming.garden.plot;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.garden.harvest.GardenBalanceConfig;
import com.skyrealms.farming.garden.harvest.HarvestRewardContext;
import com.skyrealms.farming.garden.harvest.HarvestRewardPipeline;
import com.skyrealms.farming.garden.harvest.HarvestRewardPlan;
import com.skyrealms.farming.garden.mastery.CropMastery;
import com.skyrealms.farming.garden.mastery.CropType;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PlotCustomizationAndBedTest {

    @Test
    @DisplayName("CropBed enforces interior [-31, 30] bounds, normalized corners, overlap & radius checks")
    void cropBedBoundsAndOverlapValidation() {
        UUID island = UUID.randomUUID();
        CropBed bed1 = new CropBed(UUID.randomUUID(), island, 1, BedType.BASIC, null, 5, 5, -5, -5);
        assertThat(bed1.minLocalX()).isEqualTo(-5);
        assertThat(bed1.maxLocalX()).isEqualTo(5);
        assertThat(bed1.width()).isEqualTo(11);
        assertThat(bed1.depth()).isEqualTo(11);
        assertThat(bed1.containsLocal(0, 0)).isTrue();
        assertThat(bed1.containsLocal(6, 0)).isFalse();
        assertThat(bed1.isWithinRadius(PlotUpgradeTier.TIER_1.expandedRadius())).isTrue();

        // Full 4x4 chunk plot interior [-31, 30] is editable from Tier 1 without radius gating
        CropBed fullCorner = new CropBed(UUID.randomUUID(), island, 1, BedType.RICH_SOIL, null, -31, -31, -10, -10);
        assertThat(fullCorner.isWithinRadius(PlotUpgradeTier.TIER_1.expandedRadius())).isTrue();
        assertThat(fullCorner.area()).isEqualTo(484);
        assertThat(bed1.overlaps(fullCorner)).isFalse();

        CropBed overlapping = new CropBed(UUID.randomUUID(), island, 1, BedType.BASIC, null, 4, 4, 8, 8);
        assertThat(bed1.overlaps(overlapping)).isTrue();

        assertThatThrownBy(() -> new CropBed(UUID.randomUUID(), island, 1, BedType.BASIC, null, -32, 0, -25, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CropBed(UUID.randomUUID(), island, 1, BedType.BASIC, null, 0, 0, 31, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("All premade layouts span the full 4x4 chunk plot interior without overlapping beds")
    void presetLayoutsStayIsolatedWithinPlotRadius() {
        UUID island = UUID.randomUUID();
        for (PlotLayoutPreset preset : PlotLayoutPreset.values()) {
            PlotLayoutEngine.GeneratedLayout layout = PlotLayoutEngine.generatePresetDefinition(
                    island, 1, preset, PlotUpgradeTier.TIER_1.expandedRadius(), PlotUpgradeTier.TIER_1.maxBeds()
            );
            assertThat(layout.beds()).isNotEmpty();
            int globalMinX = Integer.MAX_VALUE;
            int globalMaxX = Integer.MIN_VALUE;
            int globalMinZ = Integer.MAX_VALUE;
            int globalMaxZ = Integer.MIN_VALUE;
            for (int i = 0; i < layout.beds().size(); i++) {
                CropBed a = layout.beds().get(i);
                assertThat(a.isWithinRadius(PlotUpgradeTier.TIER_1.expandedRadius())).isTrue();
                globalMinX = Math.min(globalMinX, a.minLocalX());
                globalMaxX = Math.max(globalMaxX, a.maxLocalX());
                globalMinZ = Math.min(globalMinZ, a.minLocalZ());
                globalMaxZ = Math.max(globalMaxZ, a.maxLocalZ());
                for (int j = i + 1; j < layout.beds().size(); j++) {
                    CropBed b = layout.beds().get(j);
                    assertThat(a.overlaps(b)).isFalse();
                }
            }
            if (preset != PlotLayoutPreset.CLEAN_CANVAS) {
                assertThat(globalMinX).isLessThanOrEqualTo(-30);
                assertThat(globalMaxX).isGreaterThanOrEqualTo(29);
                assertThat(globalMinZ).isLessThanOrEqualTo(-30);
                assertThat(globalMaxZ).isGreaterThanOrEqualTo(29);
            }
        }
    }

    @Test
    @DisplayName("Rich Soil and Specialized Bed modifiers flow through single HarvestRewardPipeline")
    void bedModifiersIntegrateWithHarvestRewardPipeline() {
        GardenBalanceConfig cfg = GardenBalanceConfig.defaults();
        GardenAbilityService abilities = new GardenAbilityService(cfg);
        HarvestRewardPipeline pipeline = HarvestRewardPipeline.forCalculationOnly(cfg, abilities);

        UUID player = UUID.randomUUID();
        UUID island = UUID.randomUUID();
        Garden garden = new Garden(UUID.randomUUID(), island, 0, "skyrealms_garden", 0, 100, 0, 768, true, Instant.now(), Instant.now());
        GardenPlot plot = new GardenPlot(island, 1, true, "CARROT", "BALANCED", 1.0, 1.0, 1.0);
        CropMastery starterMastery = new CropMastery(player, CropType.CARROT, 0L, 0, new HashSet<>());

        CropBed richBed = new CropBed(UUID.randomUUID(), island, 1, BedType.RICH_SOIL, null, -4, -4, 4, 4);
        assertThat(richBed.getBonusDropModifier(CropType.CARROT)).isCloseTo(0.08, within(0.0001));
        assertThat(richBed.getXpMultiplierModifier(CropType.CARROT)).isCloseTo(0.10, within(0.0001));

        CropBed carrotSpecialized = new CropBed(UUID.randomUUID(), island, 1, BedType.SPECIALIZED, CropType.CARROT, -4, -4, 4, 4);
        assertThat(carrotSpecialized.getBonusDropModifier(CropType.CARROT)).isCloseTo(0.12, within(0.0001));
        assertThat(carrotSpecialized.getXpMultiplierModifier(CropType.CARROT)).isCloseTo(0.15, within(0.0001));
        // Wrong crop inside a Carrot-specialized bed gets no specialization bonus
        assertThat(carrotSpecialized.getBonusDropModifier(CropType.WHEAT)).isEqualTo(0.0);
        assertThat(carrotSpecialized.getXpMultiplierModifier(CropType.WHEAT)).isEqualTo(0.0);

        HarvestRewardContext ctx = new HarvestRewardContext(
                UUID.randomUUID(),
                player,
                garden,
                plot,
                CropType.CARROT,
                1, // Bountiful 5%
                starterMastery,
                List.of(),
                false,
                true, // AoE so base XP = 12 (Carrot baseMcMmoXp)
                carrotSpecialized.getBonusDropModifier(CropType.CARROT),
                carrotSpecialized.getXpMultiplierModifier(CropType.CARROT),
                0.16, // 0.05 (Bountiful) + 0.12 (Specialized) = 0.17 -> roll 0.16 triggers bonus drop
                0.99
        );
        HarvestRewardPlan plan = pipeline.calculatePlan(ctx);
        assertThat(plan.combinedBonusChance()).isCloseTo(0.17, within(0.0001));
        assertThat(plan.bonusDropTriggered()).isTrue();
        // Base Carrot XP = 12 + (12 * 0.15) = 13.8f
        assertThat(plan.totalCustomMcMmoXp()).isCloseTo(13.8f, within(0.01f));
    }

    @Test
    @DisplayName("SavedPlotLayout serializes and instantiates crop beds accurately")
    void savedPlotLayoutRoundTrip() {
        UUID island = UUID.randomUUID();
        List<CropBed> original = List.of(
                new CropBed(UUID.randomUUID(), island, 1, BedType.BASIC, null, -8, -8, -2, -2),
                new CropBed(UUID.randomUUID(), island, 1, BedType.SPECIALIZED, CropType.SUGAR_CANE, 2, 2, 8, 8)
        );
        String serialized = SavedPlotLayout.serializeBeds(original);
        SavedPlotLayout saved = new SavedPlotLayout(
                UUID.randomUUID(), island, GardenPlotCustomizationService.CUSTOM_LAYOUT_NAME, PlotLayoutPreset.NETHER_WART_OPTIMAL,
                IrrigationType.BAMBOO_AQUEDUCT, DecorationStyle.ZEN_STONES, serialized, Instant.now()
        );
        List<CropBed> instantiated = saved.instantiateBeds(island, 4);
        assertThat(instantiated).hasSize(2);
        assertThat(instantiated.get(0).bedType()).isEqualTo(BedType.BASIC);
        assertThat(instantiated.get(0).targetCrop()).isNull();
        assertThat(instantiated.get(0).plotIndex()).isEqualTo(4);
        assertThat(instantiated.get(1).bedType()).isEqualTo(BedType.SPECIALIZED);
        assertThat(instantiated.get(1).targetCrop()).isEqualTo(CropType.SUGAR_CANE);
        assertThat(instantiated.get(1).minLocalX()).isEqualTo(2);
        assertThat(instantiated.get(1).maxLocalZ()).isEqualTo(8);
    }

    @Test
    @DisplayName("All 10 crops have seed item mappings, optimal full-plot presets, and valid planting material recognition")
    void allCropPresetsAndSeedMappings() {
        assertThat(PlotLayoutPreset.cropPresets()).hasSize(10);
        for (CropType crop : CropType.values()) {
            PlotLayoutPreset preset = PlotLayoutPreset.forCrop(crop);
            assertThat(preset.targetCrop()).isEqualTo(crop);
            assertThat(CropType.fromSeedItemMaterial(crop.seedItemMaterial())).isEqualTo(crop);
            assertThat(PlotEditorAndPlantListener.fromPlantingMaterial(crop.plantedBlockMaterial())).isEqualTo(crop);
        }
        assertThat(PlotEditorAndPlantListener.fromPlantingMaterial(Material.WHEAT)).isEqualTo(CropType.WHEAT);
        assertThat(PlotEditorAndPlantListener.fromPlantingMaterial(Material.CARROTS)).isEqualTo(CropType.CARROT);
        assertThat(PlotEditorAndPlantListener.fromPlantingMaterial(Material.MELON_STEM)).isEqualTo(CropType.MELON);
        assertThat(PlotEditorAndPlantListener.fromPlantingMaterial(Material.PUMPKIN_STEM)).isEqualTo(CropType.PUMPKIN);
        assertThat(PlotEditorAndPlantListener.fromPlantingMaterial(Material.NETHER_WART)).isEqualTo(CropType.NETHER_WART);
        assertThat(PlotEditorAndPlantListener.fromPlantingMaterial(Material.OAK_PLANKS)).isNull();

        assertThat(PlotEditorAndPlantListener.isEditorSelectionItem(Material.AIR)).isTrue();
        assertThat(PlotEditorAndPlantListener.isEditorSelectionItem(Material.STICK)).isTrue();
        assertThat(PlotEditorAndPlantListener.isEditorSelectionItem(Material.BLAZE_ROD)).isTrue();
        assertThat(PlotEditorAndPlantListener.isEditorSelectionItem(Material.DIAMOND_PICKAXE)).isFalse();
        assertThat(PlotEditorAndPlantListener.isEditorSelectionItem(Material.OAK_PLANKS)).isFalse();
        assertThat(PlotEditorAndPlantListener.isEditorSelectionItem(Material.WHEAT_SEEDS)).isFalse();
        assertThat(PlotEditorAndPlantListener.isEditorSelectionItem(Material.DIAMOND_HOE)).isFalse();
    }
}

