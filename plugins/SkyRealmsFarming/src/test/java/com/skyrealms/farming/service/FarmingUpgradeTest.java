package com.skyrealms.farming.service;

import com.skyrealms.economy.service.EconomyService;
import com.skyrealms.farming.model.*;
import com.skyrealms.farming.repository.FarmingRepository;
import com.skyrealms.platform.api.event.EventBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FarmingUpgradeTest {

    @Test
    @DisplayName("Should correctly scale upgrade costs across levels")
    void shouldCalculateUpgradeCosts() {
        FarmingRepository repo = Mockito.mock(FarmingRepository.class);
        EconomyService eco = Mockito.mock(EconomyService.class);
        EventBus bus = Mockito.mock(EventBus.class);

        FarmingUpgradeService service = new FarmingUpgradeService(repo, eco, bus);

        // Speed base cost = 1000, mult = 1.8
        BigDecimal speedLvl0 = service.calculateUpgradeCost(GeneratorUpgrade.KEY_SPEED, 0);
        assertThat(speedLvl0).isEqualTo(BigDecimal.valueOf(1000));

        BigDecimal speedLvl1 = service.calculateUpgradeCost(GeneratorUpgrade.KEY_SPEED, 1);
        assertThat(speedLvl1).isEqualTo(BigDecimal.valueOf(1800));

        // Capacity base cost = 10000, mult = 3.0
        BigDecimal capLvl0 = service.calculateUpgradeCost(GeneratorUpgrade.KEY_CAPACITY, 0);
        assertThat(capLvl0).isEqualTo(BigDecimal.valueOf(10000));

        BigDecimal capLvl1 = service.calculateUpgradeCost(GeneratorUpgrade.KEY_CAPACITY, 1);
        assertThat(capLvl1).isEqualTo(BigDecimal.valueOf(30000));
    }

    @Test
    @DisplayName("Should enforce max upgrade level boundaries")
    void shouldEnforceMaxLevels() {
        FarmingRepository repo = Mockito.mock(FarmingRepository.class);
        EconomyService eco = Mockito.mock(EconomyService.class);
        EventBus bus = Mockito.mock(EventBus.class);

        FarmingUpgradeService service = new FarmingUpgradeService(repo, eco, bus);

        assertThat(service.getMaxUpgradeLevel(GeneratorUpgrade.KEY_SPEED)).isEqualTo(10);
        assertThat(service.getMaxUpgradeLevel(GeneratorUpgrade.KEY_YIELD)).isEqualTo(5);
        assertThat(service.getMaxUpgradeLevel(GeneratorUpgrade.KEY_CAPACITY)).isEqualTo(3);
    }
}
