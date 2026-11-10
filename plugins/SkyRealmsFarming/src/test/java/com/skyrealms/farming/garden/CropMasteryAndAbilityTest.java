package com.skyrealms.farming.garden;

import com.skyrealms.farming.garden.ability.GardenAbility;
import com.skyrealms.farming.garden.ability.GardenAbilityService;
import com.skyrealms.farming.garden.harvest.GardenBalanceConfig;
import com.skyrealms.farming.garden.mastery.CropMastery;
import com.skyrealms.farming.garden.mastery.CropType;
import com.skyrealms.farming.garden.mastery.MasteryMilestone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CropMasteryAndAbilityTest {

    @Test
    @DisplayName("Crop Mastery tier requires both harvest count and mcMMO Farming level")
    void cropMasteryRequiresBothHarvestsAndMcMmoLevel() {
        CropMastery mastery = new CropMastery(UUID.randomUUID(), CropType.WHEAT, 0L, 0, new HashSet<>());

        mastery.recordHarvest(150L, 0);
        assertThat(mastery.harvestCount()).isEqualTo(150L);
        assertThat(mastery.tier()).isEqualTo(0);

        mastery.recalculateTier(1);
        assertThat(mastery.tier()).isEqualTo(1);
        assertThat(mastery.getCurrentTierTitle()).isEqualTo("Thresher Initiate");
        assertThat(mastery.getTotalDoubleDropBonus()).isCloseTo(0.05, within(0.0001));
        assertThat(mastery.getTotalMcMmoXpMultiplier()).isCloseTo(0.05, within(0.0001));

        mastery.recalculateTier(5);
        assertThat(mastery.tier()).isEqualTo(2);
        assertThat(mastery.getTotalDoubleDropBonus()).isCloseTo(0.10, within(0.0001));
        assertThat(mastery.getTotalMcMmoXpMultiplier()).isCloseTo(0.10, within(0.0001));
    }

    @Test
    @DisplayName("All 10 crops define unique identities, discoveries, cosmetics, and equipment hooks")
    void allCropsHaveDistinctIdentitiesAndRewards() {
        for (CropType crop : CropType.values()) {
            List<MasteryMilestone> tiers = MasteryMilestone.getMilestonesFor(crop);
            assertThat(tiers).hasSize(7);
            assertThat(tiers.get(2).discoveryUnlock()).isNotBlank();
            assertThat(tiers.get(4).cosmeticUnlock()).isNotBlank();
            assertThat(tiers.get(5).equipmentHook()).isNotBlank();
        }

        CropMastery nether = new CropMastery(UUID.randomUUID(), CropType.NETHER_WART, 0L, 0, new HashSet<>());
        nether.recordHarvest(7500L, 75);

        assertThat(nether.tier()).isEqualTo(7);
        assertThat(nether.getCurrentTierTitle()).isEqualTo("Overlord of Nether Spores");
        assertThat(nether.getTotalDoubleDropBonus()).isCloseTo(0.40, within(0.0001));
        assertThat(nether.getTotalMcMmoXpMultiplier()).isCloseTo(0.35, within(0.0001));
        assertThat(nether.getUnlockedDiscoveries()).contains("Arcane Alchemical Wart");
        assertThat(nether.getUnlockedCosmetics()).contains("Crimson Soul Embers");
        assertThat(nether.getUnlockedEquipmentHooks()).contains("Alembic Infusion Core");
        assertThat(nether.getNextMilestone()).isNull();
    }

    @Test
    @DisplayName("GardenAbilityService scales probabilities, enforces caps, and retains cooldowns across relog")
    void gardenAbilityScalingAndCooldownRetention() {
        GardenBalanceConfig cfg = GardenBalanceConfig.defaults();
        GardenAbilityService service = new GardenAbilityService(cfg);
        UUID player = UUID.randomUUID();

        assertThat(service.isUnlocked(GardenAbility.BOUNTIFUL_HARVEST, 1)).isTrue();
        assertThat(service.isUnlocked(GardenAbility.VERDANT_AURA, 9)).isFalse();
        assertThat(service.isUnlocked(GardenAbility.VERDANT_AURA, 10)).isTrue();
        assertThat(service.isUnlocked(GardenAbility.SAKURA_HARVEST_SURGE, 14)).isFalse();
        assertThat(service.isUnlocked(GardenAbility.SAKURA_HARVEST_SURGE, 15)).isTrue();
        assertThat(service.isUnlocked(GardenAbility.GOLDEN_SPROUT, 29)).isFalse();
        assertThat(service.isUnlocked(GardenAbility.GOLDEN_SPROUT, 30)).isTrue();

        assertThat(service.getBountifulHarvestChance(0)).isEqualTo(0.0);
        assertThat(service.getBountifulHarvestChance(1)).isCloseTo(0.05, within(0.0001));
        assertThat(service.getBountifulHarvestChance(100)).isCloseTo(0.50, within(0.0001));
        assertThat(service.getBountifulHarvestChance(500)).isCloseTo(0.50, within(0.0001));

        assertThat(service.getGoldenSproutChance(30)).isCloseTo(0.01, within(0.0001));
        assertThat(service.getGoldenSproutChance(100)).isCloseTo(0.15, within(0.0001));
        assertThat(service.calculateGoldenSproutCoins(CropType.WHEAT, 30, 0)).isCloseTo(12.0, within(0.01));
        assertThat(service.calculateGoldenSproutCoins(CropType.NETHER_WART, 1000, 7))
                .isLessThanOrEqualTo(cfg.goldenSproutMaxCoinsPerHarvest());

        // Surge activation and cooldown persistence across disconnect/world leave
        assertThat(service.tryActivateSurge(player, 10)).isFalse();
        assertThat(service.tryActivateSurge(player, 15)).isTrue();
        assertThat(service.isSurgeActive(player)).isTrue();

        service.deactivateSurgeWindowOnly(player);
        assertThat(service.isSurgeActive(player)).isFalse();
        assertThat(service.getSurgeRemainingCooldownSeconds(player)).isGreaterThan(0L);
        assertThat(service.tryActivateSurge(player, 15)).isFalse();
    }
}
