package com.skyrealms.end;

import com.skyrealms.end.progression.CombatContributionTracker;
import com.skyrealms.end.progression.CombatContributionTracker.AntiAfkVerdict;
import com.skyrealms.end.progression.CombatContributionTracker.AttributedShare;
import com.skyrealms.end.progression.CombatDiscipline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CombatContributionTrackerTest {

    @Test
    void attributesXpProportionallyAcrossPlayersAndDisciplinesAndPreventsDuplicateKillPayout() {
        CombatContributionTracker tracker = new CombatContributionTracker();
        UUID mob = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        // Alice switches mid-fight: 50 Bow + 25 Melee; Bob: 25 Mage => total 100 dmg
        tracker.recordDamage(mob, alice, CombatDiscipline.ARCHERY, 50.0, 1000L);
        tracker.recordDamage(mob, alice, CombatDiscipline.MELEE, 25.0, 2000L);
        tracker.recordDamage(mob, bob, CombatDiscipline.MAGE, 25.0, 3000L);

        List<AttributedShare> shares = tracker.consumeKillShares(mob, 100L, true);
        assertThat(shares).hasSize(3);
        assertThat(shares.stream().mapToLong(AttributedShare::xpAward).sum()).isEqualTo(100L);
        assertThat(shares.get(0).discipline()).isEqualTo(CombatDiscipline.ARCHERY);
        assertThat(shares.get(0).xpAward()).isEqualTo(50L);

        // Second death event for same mob UUID yields 0 XP (no XP duplication!)
        assertThat(tracker.consumeKillShares(mob, 100L, true)).isEmpty();
    }

    @Test
    void detectsStationaryAfkMacroAndResetsWhenPlayerMoves() {
        CombatContributionTracker tracker = new CombatContributionTracker();
        UUID player = UUID.randomUUID();
        long t = 1_000_000L;

        // 6 stationary kills with zero movement or aim change
        for (int i = 0; i < 6; i++) {
            AntiAfkVerdict v = tracker.evaluateKillActivity(player, 10.0, 65.0, 10.0, 90.0f, 0.0f, t + i * 2_000L);
            assertThat(v.allowed()).isTrue();
        }
        // 7th stationary kill inside 60s window triggers AFK block
        AntiAfkVerdict blocked = tracker.evaluateKillActivity(player, 10.0, 65.0, 10.0, 90.0f, 0.0f, t + 14_000L);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.multiplier()).isZero();

        // Advancing time beyond 60s window restores full XP rate
        AntiAfkVerdict recovered = tracker.evaluateKillActivity(player, 25.0, 65.0, 18.0, 15.0f, -10.0f, t + 80_000L);
        assertThat(recovered.allowed()).isTrue();
        assertThat(recovered.multiplier()).isEqualTo(1.0);
    }

    @Test
    void purgesExpiredLedgers() {
        CombatContributionTracker tracker = new CombatContributionTracker();
        tracker.recordDamage(UUID.randomUUID(), UUID.randomUUID(), CombatDiscipline.MELEE, 15.0, 1000L);
        tracker.recordDamage(UUID.randomUUID(), UUID.randomUUID(), CombatDiscipline.ARCHERY, 15.0, 400_000L);

        tracker.purgeExpiredLedgers(401_000L);
        assertThat(tracker.trackedMobCount()).isEqualTo(1);
    }
}

