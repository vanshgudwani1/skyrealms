package com.skyrealms.farming.service;

import com.skyrealms.farming.model.FarmingPlayerProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FarmingProgressionTest {

    @Test
    @DisplayName("Should correctly calculate farming levels according to power curve")
    void shouldCalculateFarmingLevels() {
        // Level 1 at 0 XP
        assertThat(FarmingProgressionService.calculateLevel(0)).isEqualTo(1);
        assertThat(FarmingProgressionService.calculateLevel(50)).isEqualTo(1);

        // Level 2 requires 100 XP (100 * 1^1.8)
        assertThat(FarmingProgressionService.calculateLevel(100)).isEqualTo(2);

        // Level 3 requires ~348 XP (100 * 2^1.8)
        assertThat(FarmingProgressionService.calculateLevel(350)).isEqualTo(3);

        // Level 10 requires ~5,200 XP
        int lvl10 = FarmingProgressionService.calculateLevel(6000);
        assertThat(lvl10).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("Should increment XP and track player progress model")
    void shouldIncrementXpAndProgress() {
        UUID playerUuid = UUID.randomUUID();
        FarmingPlayerProgress progress = new FarmingPlayerProgress(playerUuid, 0L, 1, 0L, Instant.now());

        progress.addXp(150);
        progress.incrementHarvests();

        assertThat(progress.farmingXp()).isEqualTo(150);
        assertThat(progress.totalHarvests()).isEqualTo(1);
        assertThat(FarmingProgressionService.calculateLevel(progress.farmingXp())).isEqualTo(2);
    }
}
