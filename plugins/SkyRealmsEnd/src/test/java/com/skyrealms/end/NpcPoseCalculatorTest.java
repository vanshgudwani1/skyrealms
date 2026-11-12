package com.skyrealms.end;

import com.skyrealms.end.config.EndVillageConfig.AnchorPoint;
import com.skyrealms.end.npc.AnimationProfile;
import com.skyrealms.end.npc.NpcPoseCalculator;
import com.skyrealms.end.npc.NpcPoseCalculator.PatrolStep;
import com.skyrealms.end.npc.NpcPoseCalculator.PoseFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NpcPoseCalculatorTest {

    @Test
    void computesDistinctAnimatedPosesAndThrottledAccentEventsForEveryProfile() {
        for (AnimationProfile profile : AnimationProfile.values()) {
            int emitCount = 0;
            for (int phase = 0; phase < 40; phase++) {
                PoseFrame frame = NpcPoseCalculator.computePose(profile, phase, true, false);
                assertThat(frame.head()).isNotNull();
                assertThat(frame.rightArm()).isNotNull();
                assertThat(frame.leftArm()).isNotNull();
                if (frame.emitAccentEffect()) {
                    emitCount++;
                }
            }
            if (profile != AnimationProfile.IDLE) {
                assertThat(emitCount)
                        .as("Bounded particle/sound accent count for %s over 40 frames", profile)
                        .isBetween(1, 6);
            } else {
                assertThat(emitCount).isZero();
            }
        }
    }

    @Test
    void triggersBlacksmithStrikeAndInteractionPoseCleanly() {
        PoseFrame strike = NpcPoseCalculator.computePose(AnimationProfile.BLACKSMITH, 3, true, false);
        PoseFrame lift = NpcPoseCalculator.computePose(AnimationProfile.BLACKSMITH, 5, true, false);
        assertThat(strike.emitAccentEffect()).isTrue();
        assertThat(strike.rightArm().getX()).isGreaterThan(lift.rightArm().getX());

        PoseFrame interact = NpcPoseCalculator.computePose(AnimationProfile.MAGE, 0, true, true);
        assertThat(interact.emitAccentEffect()).isTrue();
        assertThat(interact.rightArm().getX()).isCloseTo(-0.95, within(0.01));
    }

    @Test
    void interpolatesGuardPatrolRouteSmoothlyAndWrapsIndex() {
        List<AnchorPoint> route = List.of(
                new AnchorPoint(100.0, 80.0, 50.0, 0f, 0f),
                new AnchorPoint(101.0, 80.0, 50.0, 90f, 0f)
        );

        PatrolStep mid = NpcPoseCalculator.stepPatrol(route, 1, 100.0, 80.0, 50.0, 0.25);
        assertThat(mid.x()).isCloseTo(100.25, within(1e-6));
        assertThat(mid.nextIndex()).isEqualTo(1);
        assertThat(mid.yaw()).isCloseTo(-90.0f, within(0.5f)); // moving +X -> Minecraft yaw -90

        PatrolStep arrive = NpcPoseCalculator.stepPatrol(route, 1, 100.9, 80.0, 50.0, 0.25);
        assertThat(arrive.x()).isCloseTo(101.0, within(1e-6));
        assertThat(arrive.nextIndex()).isEqualTo(0);
    }
}

