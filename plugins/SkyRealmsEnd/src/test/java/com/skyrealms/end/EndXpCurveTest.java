package com.skyrealms.end;

import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.EndXpCurve;
import com.skyrealms.end.progression.EndXpCurve.DisciplineAttributes;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndXpCurveTest {

    @Test
    void exactFormulaMatchesLevel30Requirement46110() {
        assertThat(EndXpCurve.xpRequiredForNextLevel(1)).isEqualTo(80L + 22L + 4L);      // 106
        assertThat(EndXpCurve.xpRequiredForNextLevel(10)).isEqualTo(80L + 220L + 400L); // 700
        assertThat(EndXpCurve.xpRequiredForNextLevel(29)).isEqualTo(80L + 638L + 3364L);// 4082
        assertThat(EndXpCurve.xpRequiredForNextLevel(30)).isZero();

        assertThat(EndXpCurve.totalXpForLevel(1)).isZero();
        assertThat(EndXpCurve.totalXpForLevel(2)).isEqualTo(106L);
        assertThat(EndXpCurve.totalXpForLevel(30)).isEqualTo(46_110L);
        assertThat(EndXpCurve.TOTAL_XP_FOR_MAX_LEVEL).isEqualTo(46_110L);
    }

    @Test
    void levelForTotalXpHandlesBoundariesAndClamping() {
        assertThat(EndXpCurve.levelForTotalXp(-100L)).isEqualTo(1);
        assertThat(EndXpCurve.levelForTotalXp(0L)).isEqualTo(1);
        assertThat(EndXpCurve.levelForTotalXp(105L)).isEqualTo(1);
        assertThat(EndXpCurve.levelForTotalXp(106L)).isEqualTo(2);
        assertThat(EndXpCurve.levelForTotalXp(46_109L)).isEqualTo(29);
        assertThat(EndXpCurve.levelForTotalXp(46_110L)).isEqualTo(30);
        assertThat(EndXpCurve.levelForTotalXp(999_999L)).isEqualTo(30);

        for (int lv = 1; lv <= 30; lv++) {
            long threshold = EndXpCurve.totalXpForLevel(lv);
            assertThat(EndXpCurve.levelForTotalXp(threshold)).isEqualTo(lv);
        }
    }

    @Test
    void independentAttributesAndUnlocksScaleProperly() {
        DisciplineAttributes archery30 = EndXpCurve.attributesFor(CombatDiscipline.ARCHERY, 30);
        DisciplineAttributes melee30 = EndXpCurve.attributesFor(CombatDiscipline.MELEE, 30);
        DisciplineAttributes mage30 = EndXpCurve.attributesFor(CombatDiscipline.MAGE, 30);

        assertThat(archery30.bonusRangedDamagePercent()).isEqualTo(58.0);
        assertThat(archery30.bonusArrowVelocityPercent()).isEqualTo(43.5);
        assertThat(melee30.bonusMeleeDamage()).isCloseTo(10.15, org.assertj.core.data.Offset.offset(0.001));
        assertThat(melee30.bonusMaxHealth()).isEqualTo(14.0);
        assertThat(mage30.maxMana()).isEqualTo(332.0);
        assertThat(mage30.spellPowerMultiplier()).isCloseTo(1.87, org.assertj.core.data.Offset.offset(0.001));

        assertThat(EndXpCurve.unlockedFeaturesAt(CombatDiscipline.ARCHERY, 1)).hasSize(1);
        assertThat(EndXpCurve.unlockedFeaturesAt(CombatDiscipline.ARCHERY, 30)).hasSize(7);
        assertThat(EndXpCurve.unlockedFeaturesAt(CombatDiscipline.MAGE, 25)).hasSize(6);
    }

    @Test
    void detectsFreeWeaponSwitchingForVanillaAndCustomIds() {
        assertThat(CombatDiscipline.detectFromWeapon(Material.BOW, null, CombatDiscipline.MELEE))
                .isEqualTo(CombatDiscipline.ARCHERY);
        assertThat(CombatDiscipline.detectFromWeapon(Material.DIAMOND_SWORD, null, CombatDiscipline.MAGE))
                .isEqualTo(CombatDiscipline.MELEE);
        assertThat(CombatDiscipline.detectFromWeapon(Material.BLAZE_ROD, null, CombatDiscipline.ARCHERY))
                .isEqualTo(CombatDiscipline.MAGE);

        assertThat(CombatDiscipline.detectFromWeapon(Material.STICK, "rift_conduit", CombatDiscipline.MELEE))
                .isEqualTo(CombatDiscipline.MAGE);
        assertThat(CombatDiscipline.detectFromWeapon(Material.IRON_SWORD, "astral_executioner", CombatDiscipline.ARCHERY))
                .isEqualTo(CombatDiscipline.MELEE);
        assertThat(CombatDiscipline.detectFromWeapon(Material.BOW, "voidpiercer_bow", CombatDiscipline.MAGE))
                .isEqualTo(CombatDiscipline.ARCHERY);
    }
}

