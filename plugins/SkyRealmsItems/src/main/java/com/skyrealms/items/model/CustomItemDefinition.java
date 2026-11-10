package com.skyrealms.items.model;

import com.skyrealms.platform.integrations.SkyItemStats;
import org.bukkit.Color;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of a SkyRealms custom item, tool, weapon, armor piece, or relic.
 */
public record CustomItemDefinition(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull Material material,
        @NotNull ItemRarity rarity,
        @NotNull ItemDomain domain,
        @NotNull GearSlot slot,
        @NotNull SkyItemStats stats,
        @Nullable String armorSetId,
        @Nullable String abilityId,
        @Nullable String abilityName,
        @Nullable String abilityDescription,
        int abilityCooldownSeconds,
        @NotNull List<String> loreLines,
        @Nullable Color leatherColor,
        int customModelData,
        boolean glint,
        boolean unbreakable
) {
    public CustomItemDefinition {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(material, "material cannot be null");
        Objects.requireNonNull(rarity, "rarity cannot be null");
        Objects.requireNonNull(domain, "domain cannot be null");
        Objects.requireNonNull(slot, "slot cannot be null");
        Objects.requireNonNull(stats, "stats cannot be null");
        loreLines = List.copyOf(Objects.requireNonNull(loreLines, "loreLines cannot be null"));
    }

    public boolean hasAbility() {
        return abilityId != null && !abilityId.isBlank();
    }

    @NotNull
    public static Builder builder(@NotNull String id, @NotNull String displayName, @NotNull Material material) {
        return new Builder(id, displayName, material);
    }

    public static final class Builder {
        private final String id;
        private final String displayName;
        private final Material material;
        private ItemRarity rarity = ItemRarity.COMMON;
        private ItemDomain domain = ItemDomain.UTILITY;
        private GearSlot slot = GearSlot.MAIN_HAND;
        private SkyItemStats stats = SkyItemStats.EMPTY;
        private String armorSetId;
        private String abilityId;
        private String abilityName;
        private String abilityDescription;
        private int abilityCooldownSeconds;
        private List<String> loreLines = List.of();
        private Color leatherColor;
        private int customModelData;
        private boolean glint = true;
        private boolean unbreakable = true;

        private Builder(String id, String displayName, Material material) {
            this.id = id;
            this.displayName = displayName;
            this.material = material;
        }

        public Builder rarity(@NotNull ItemRarity rarity) { this.rarity = rarity; return this; }
        public Builder domain(@NotNull ItemDomain domain) { this.domain = domain; return this; }
        public Builder slot(@NotNull GearSlot slot) { this.slot = slot; return this; }
        public Builder stats(@NotNull SkyItemStats stats) { this.stats = stats; return this; }
        public Builder armorSet(@NotNull String setId) { this.armorSetId = setId; return this; }
        public Builder ability(@NotNull String id, @NotNull String name, @NotNull String description, int cooldownSeconds) {
            this.abilityId = id;
            this.abilityName = name;
            this.abilityDescription = description;
            this.abilityCooldownSeconds = cooldownSeconds;
            return this;
        }
        public Builder lore(@NotNull List<String> loreLines) { this.loreLines = loreLines; return this; }
        public Builder leatherColor(@Nullable Color leatherColor) { this.leatherColor = leatherColor; return this; }
        public Builder customModelData(int cmd) { this.customModelData = cmd; return this; }
        public Builder glint(boolean glint) { this.glint = glint; return this; }
        public Builder unbreakable(boolean unbreakable) { this.unbreakable = unbreakable; return this; }

        @NotNull
        public CustomItemDefinition build() {
            return new CustomItemDefinition(
                    id,
                    displayName,
                    material,
                    rarity,
                    domain,
                    slot,
                    stats,
                    armorSetId,
                    abilityId,
                    abilityName,
                    abilityDescription,
                    abilityCooldownSeconds,
                    loreLines,
                    leatherColor,
                    customModelData,
                    glint,
                    unbreakable
            );
        }
    }
}

