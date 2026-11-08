package com.skyrealms.farming.mutation;

import com.skyrealms.farming.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class CropMutationEngineTest {

    @Test
    @DisplayName("Should return empty when random roll exceeds mutation chance")
    void shouldReturnEmptyWhenRollExceedsChance() {
        CropMutationEngine engine = new CropMutationEngine(0.015, 0.005);
        FarmingGenerator generator = new FarmingGenerator(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                FarmingGeneratorTier.TIER_1_WHEAT, "skyrealms_islands",
                0, 100, 0, GeneratorStatus.ACTIVE, 0L, Instant.now(), Instant.now(),
                Collections.emptyList()
        );

        // Random returning 0.5 (greater than 0.015)
        Random predictableRandom = new Random() {
            @Override
            public double nextDouble() {
                return 0.5;
            }
        };

        Optional<CropMutationType> result = engine.evaluateMutation(generator, predictableRandom);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should trigger mutation when random roll is below mutation chance")
    void shouldTriggerMutationWhenRollIsBelowChance() {
        CropMutationEngine engine = new CropMutationEngine(0.05, 0.01);
        FarmingGenerator generator = new FarmingGenerator(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                FarmingGeneratorTier.TIER_1_WHEAT, "skyrealms_islands",
                0, 100, 0, GeneratorStatus.ACTIVE, 0L, Instant.now(), Instant.now(),
                Collections.emptyList()
        );

        // Random returning 0.001 (less than 0.05) and nextInt returning 10
        Random predictableRandom = new Random() {
            @Override
            public double nextDouble() {
                return 0.001;
            }

            @Override
            public int nextInt(int bound) {
                return 5; // Falls within first weight bracket (GOLDEN has weight 40)
            }
        };

        Optional<CropMutationType> result = engine.evaluateMutation(generator, predictableRandom);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(CropMutationType.GOLDEN);
    }
}
