package com.skyrealms.farming.garden.harvest;

import com.skyrealms.farming.garden.Garden;
import com.skyrealms.farming.garden.GardenPlot;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.garden.mastery.CropMastery;
import com.skyrealms.farming.garden.mastery.CropType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HarvestRewardPipelineTest {

    private Garden dummyGarden(UUID islandId) {
        return new Garden(UUID.randomUUID(), islandId, 0, "skyrealms_garden", 0, 100, 0, 768, true, Instant.now(), Instant.now());
    }

    private GardenPlot dummyPlot(UUID islandId) {
        return new GardenPlot(islandId, 1, true, "WHEAT", "BALANCED", 1.0, 1.0, 1.0);
    }

    @Test
    @DisplayName("Primary vs AoE harvest XP prevents double base XP and caps multiplier")
    void primaryVsAoeXpAndCaps() {
        GardenBalanceConfig cfg = GardenBalanceConfig.defaults();
        GardenAbilityService abilities = new GardenAbilityService(cfg);
        HarvestRewardPipeline pipeline = HarvestRewardPipeline.forCalculationOnly(cfg, abilities);

        UUID player = UUID.randomUUID();
        UUID island = UUID.randomUUID();
        CropMastery tier7 = new CropMastery(player, CropType.WHEAT, 0L, 0, new HashSet<>());
        tier7.recordHarvest(7500L, 75); // +40% drop, +35% XP

        // Primary block break: baseCustomMcMmoXp must be 0 (mcMMO handles primary block XP)
        HarvestRewardContext primaryCtx = new HarvestRewardContext(
                UUID.randomUUID(), player, dummyGarden(island), dummyPlot(island),
                CropType.WHEAT, 100, tier7, List.of(), true, false,
                0.0, 0.0, 0.99, 0.99
        );
        HarvestRewardPlan primaryPlan = pipeline.calculatePlan(primaryCtx);
        assertThat(primaryPlan.baseCustomMcMmoXp()).isEqualTo(0.0f);
        assertThat(primaryPlan.bonusCustomMcMmoXp()).isCloseTo(3.5f, within(0.01f)); // 10 * 0.35

        // AoE neighbor block: baseCustomMcMmoXp = 10 + 3.5 bonus = 13.5
        HarvestRewardContext aoeCtx = new HarvestRewardContext(
                UUID.randomUUID(), player, dummyGarden(island), dummyPlot(island),
                CropType.WHEAT, 100, tier7, List.of(), false, true,
                0.0, 0.0, 0.99, 0.99
        );
        HarvestRewardPlan aoePlan = pipeline.calculatePlan(aoeCtx);
        assertThat(aoePlan.baseCustomMcMmoXp()).isEqualTo(10.0f);
        assertThat(aoePlan.bonusCustomMcMmoXp()).isCloseTo(3.5f, within(0.01f));
        assertThat(aoePlan.totalCustomMcMmoXp()).isCloseTo(13.5f, within(0.01f));
    }

    @Test
    @DisplayName("Combined bonus drop chance is clamped to maxCombinedBonusDropChance")
    void combinedBonusDropChanceCapped() {
        GardenBalanceConfig cfg = GardenBalanceConfig.defaults();
        GardenAbilityService abilities = new GardenAbilityService(cfg);
        HarvestRewardPipeline pipeline = HarvestRewardPipeline.forCalculationOnly(cfg, abilities);

        UUID player = UUID.randomUUID();
        UUID island = UUID.randomUUID();
        CropMastery tier7 = new CropMastery(player, CropType.WHEAT, 0L, 0, new HashSet<>());
        tier7.recordHarvest(7500L, 75); // 0.40 mastery + 0.50 bountiful = 0.90 -> clamped to 0.75

        HarvestRewardContext ctx = new HarvestRewardContext(
                UUID.randomUUID(), player, dummyGarden(island), dummyPlot(island),
                CropType.WHEAT, 100, tier7, List.of(), true, false,
                0.0, 0.0, 0.74, 0.99
        );
        HarvestRewardPlan plan = pipeline.calculatePlan(ctx);
        assertThat(plan.combinedBonusChance()).isCloseTo(0.75, within(0.0001));
        assertThat(plan.bonusDropTriggered()).isTrue();
    }

    @Test
    @DisplayName("Golden Sprout respects per-harvest max and daily coin cap")
    void goldenSproutRespectsDailyCap() {
        GardenBalanceConfig cfg = GardenBalanceConfig.defaults();
        GardenAbilityService abilities = new GardenAbilityService(cfg);
        HarvestRewardPipeline pipeline = HarvestRewardPipeline.forCalculationOnly(cfg, abilities);

        UUID player = UUID.randomUUID();
        UUID island = UUID.randomUUID();
        CropMastery tier7 = new CropMastery(player, CropType.NETHER_WART, 0L, 0, new HashSet<>());
        tier7.recordHarvest(7500L, 75);

        // Drain almost all of daily 15,000 cap
        double first = abilities.reserveDailyGoldenSproutCoins(player, 14950.0);
        assertThat(first).isEqualTo(14950.0);

        HarvestRewardContext ctx = new HarvestRewardContext(
                UUID.randomUUID(), player, dummyGarden(island), dummyPlot(island),
                CropType.NETHER_WART, 100, tier7, List.of(), true, false,
                0.0, 0.0, 0.99, 0.0
        );
        HarvestRewardPlan plan = pipeline.calculatePlan(ctx);
        assertThat(plan.goldenSproutTriggered()).isTrue();
        assertThat(plan.goldenSproutCoins()).isCloseTo(50.0, within(0.01));

        // Subsequent proc yields 0 coins
        HarvestRewardPlan cappedPlan = pipeline.calculatePlan(ctx);
        assertThat(cappedPlan.goldenSproutTriggered()).isFalse();
        assertThat(cappedPlan.goldenSproutCoins()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Block harvest claim lock prevents duplicate concurrent harvest on same coordinates")
    void blockClaimPreventsDuplicates() {
        HarvestRewardPipeline pipeline = HarvestRewardPipeline.forCalculationOnly(
                GardenBalanceConfig.defaults(), new GardenAbilityService()
        );
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        assertThat(pipeline.tryClaimBlock("skyrealms_garden", -245, 100, -35, id1)).isTrue();
        assertThat(pipeline.tryClaimBlock("skyrealms_garden", -245, 100, -35, id2)).isFalse();

        // Wrong owner cannot release
        pipeline.releaseBlock("skyrealms_garden", -245, 100, -35, id2);
        assertThat(pipeline.tryClaimBlock("skyrealms_garden", -245, 100, -35, id2)).isFalse();

        // Owner release allows subsequent harvest
        pipeline.releaseBlock("skyrealms_garden", -245, 100, -35, id1);
        assertThat(pipeline.tryClaimBlock("skyrealms_garden", -245, 100, -35, id2)).isTrue();
    }
}

