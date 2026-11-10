package com.skyrealms.platform.integrations;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable stat snapshot aggregated from SkyRealms custom gear, armor sets, and relics.
 */
public record SkyItemStats(
        double farmingFortune,
        double farmingXpBonus,
        double miningFortune,
        double miningSpeed,
        double foragingFortune,
        double fishingLuck,
        double combatDamage,
        double slayerDamageBonus,
        double maxHealthBonus,
        double defense,
        double speedBonus
) {
    public static final SkyItemStats EMPTY = new SkyItemStats(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0
    );

    @NotNull
    public SkyItemStats add(@NotNull SkyItemStats other) {
        return new SkyItemStats(
                farmingFortune + other.farmingFortune,
                farmingXpBonus + other.farmingXpBonus,
                miningFortune + other.miningFortune,
                miningSpeed + other.miningSpeed,
                foragingFortune + other.foragingFortune,
                fishingLuck + other.fishingLuck,
                combatDamage + other.combatDamage,
                slayerDamageBonus + other.slayerDamageBonus,
                maxHealthBonus + other.maxHealthBonus,
                defense + other.defense,
                speedBonus + other.speedBonus
        );
    }

    public boolean isEmpty() {
        return farmingFortune == 0.0
                && farmingXpBonus == 0.0
                && miningFortune == 0.0
                && miningSpeed == 0.0
                && foragingFortune == 0.0
                && fishingLuck == 0.0
                && combatDamage == 0.0
                && slayerDamageBonus == 0.0
                && maxHealthBonus == 0.0
                && defense == 0.0
                && speedBonus == 0.0;
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double farmingFortune;
        private double farmingXpBonus;
        private double miningFortune;
        private double miningSpeed;
        private double foragingFortune;
        private double fishingLuck;
        private double combatDamage;
        private double slayerDamageBonus;
        private double maxHealthBonus;
        private double defense;
        private double speedBonus;

        public Builder farmingFortune(double value) { this.farmingFortune = value; return this; }
        public Builder farmingXpBonus(double value) { this.farmingXpBonus = value; return this; }
        public Builder miningFortune(double value) { this.miningFortune = value; return this; }
        public Builder miningSpeed(double value) { this.miningSpeed = value; return this; }
        public Builder foragingFortune(double value) { this.foragingFortune = value; return this; }
        public Builder fishingLuck(double value) { this.fishingLuck = value; return this; }
        public Builder combatDamage(double value) { this.combatDamage = value; return this; }
        public Builder slayerDamageBonus(double value) { this.slayerDamageBonus = value; return this; }
        public Builder maxHealthBonus(double value) { this.maxHealthBonus = value; return this; }
        public Builder defense(double value) { this.defense = value; return this; }
        public Builder speedBonus(double value) { this.speedBonus = value; return this; }

        @NotNull
        public SkyItemStats build() {
            return new SkyItemStats(
                    farmingFortune,
                    farmingXpBonus,
                    miningFortune,
                    miningSpeed,
                    foragingFortune,
                    fishingLuck,
                    combatDamage,
                    slayerDamageBonus,
                    maxHealthBonus,
                    defense,
                    speedBonus
            );
        }
    }
}

