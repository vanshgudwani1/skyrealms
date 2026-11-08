package com.skyrealms.farming.garden.mastery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class CropMasteryTest {

    @Test
    @DisplayName("Should correctly calculate power curve level requirements")
    void shouldCalculatePowerCurveLevelRequirements() {
        // XP(L) = floor(50 * L^2.1)
        assertThat(CropMastery.calculateXpForLevel(0)).isEqualTo(0);
        assertThat(CropMastery.calculateXpForLevel(1)).isEqualTo(50);
        assertThat(CropMastery.calculateXpForLevel(2)).isEqualTo(214);
        assertThat(CropMastery.calculateXpForLevel(5)).isEqualTo(1468);
        assertThat(CropMastery.calculateXpForLevel(10)).isEqualTo(6294);
        assertThat(CropMastery.calculateXpForLevel(30)).isEqualTo(63230);
    }

    @Test
    @DisplayName("Should accurately level up and progress to next level")
    void shouldAccuratelyLevelUpAndProgress() {
        UUID playerUuid = UUID.randomUUID();
        CropMastery mastery = new CropMastery(playerUuid, CropType.WHEAT, 0, 0, new HashSet<>());

        assertThat(mastery.level()).isEqualTo(0);
        assertThat(mastery.progressToNextLevel()).isEqualTo(0.0);

        // Add 50 XP -> Level 1
        mastery.addXp(50);
        assertThat(mastery.level()).isEqualTo(1);
        assertThat(mastery.masteryXp()).isEqualTo(50);

        // Add enough to reach Level 2 (214 XP)
        mastery.addXp(164);
        assertThat(mastery.level()).isEqualTo(2);

        // Add enough to reach Level 30
        mastery.addXp(100_000);
        assertThat(mastery.level()).isEqualTo(30);
        assertThat(mastery.progressToNextLevel()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should track and claim milestone rewards correctly")
    void shouldTrackAndClaimMilestones() {
        UUID playerUuid = UUID.randomUUID();
        CropMastery mastery = new CropMastery(playerUuid, CropType.WHEAT, 0, 0, new HashSet<>());

        // Cannot claim Level 5 milestone when level is 0
        assertThat(mastery.claimMilestone(5)).isFalse();

        // Level up to 5
        mastery.addXp(CropMastery.calculateXpForLevel(5));
        assertThat(mastery.level()).isGreaterThanOrEqualTo(5);

        // Claim Level 1 and Level 5 milestones
        assertThat(mastery.claimMilestone(1)).isTrue();
        assertThat(mastery.claimMilestone(5)).isTrue();

        // Cannot claim twice
        assertThat(mastery.claimMilestone(5)).isFalse();

        // Cannot claim Level 10 yet
        assertThat(mastery.claimMilestone(10)).isFalse();
    }

    @Test
    @DisplayName("Should accumulate double drop bonuses from all achieved milestones")
    void shouldAccumulateDoubleDropBonuses() {
        UUID playerUuid = UUID.randomUUID();
        CropMastery mastery = new CropMastery(playerUuid, CropType.WHEAT, 0, 0, new HashSet<>());

        assertThat(mastery.getTotalDoubleDropBonus()).isEqualTo(0.0);

        // Reach Level 1 (+5%)
        mastery.addXp(CropMastery.calculateXpForLevel(1));
        assertThat(mastery.getTotalDoubleDropBonus()).isEqualTo(0.05);

        // Reach Level 5 (+5% + 10% = 15%)
        mastery.addXp(CropMastery.calculateXpForLevel(5) - mastery.masteryXp());
        assertThat(mastery.getTotalDoubleDropBonus()).isCloseTo(0.15, offset(0.0001));
    }

    @Test
    @DisplayName("Should encode and decode claimed milestone sets accurately")
    void shouldEncodeAndDecodeClaimedMilestones() {
        UUID playerUuid = UUID.randomUUID();
        Set<Integer> claimed = Set.of(1, 5, 10);
        CropMastery mastery = new CropMastery(playerUuid, CropType.CARROT, 1000, 5, claimed);

        String encoded = mastery.encodeClaimedMilestones();
        assertThat(encoded).isEqualTo("1,5,10");

        Set<Integer> decoded = CropMastery.decodeClaimedMilestones(encoded);
        assertThat(decoded).containsExactlyInAnyOrder(1, 5, 10);
    }

    @Test
    @DisplayName("All 10 crops must have exactly 7 defined milestones at levels 1, 5, 10, 15, 20, 25, 30")
    void allCropsMustHave7Milestones() {
        for (CropType type : CropType.values()) {
            List<MasteryMilestone> list = MasteryMilestone.getMilestonesFor(type);
            assertThat(list).hasSize(7);
            List<Integer> levels = list.stream().map(MasteryMilestone::level).toList();
            assertThat(levels).containsExactly(1, 5, 10, 15, 20, 25, 30);
        }
    }
}
