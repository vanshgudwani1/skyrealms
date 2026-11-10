package com.skyrealms.pets.catalog;

import com.skyrealms.pets.model.PetDefinition;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Authoritative registry of the 8 Cubees Rework Currets 3D ModelEngine pets.
 */
public final class PetCatalog {

    public static final String STARTER_PET_ID = "baby_ghast";

    private final Map<String, PetDefinition> pets;

    public PetCatalog() {
        Map<String, PetDefinition> map = new LinkedHashMap<>();

        register(map, new PetDefinition(
                "baby_ghast",
                "cubee-baby_ghast",
                "Baby Ghast Cubee",
                TextColor.color(0xE8E4F0),
                Material.GHAST_TEAR,
                "STARTER • EPIC",
                "Harvester Companion",
                "Fire",
                "Floats beside you channeling fiery crop fortune.",
                0L,
                0.03, 0.20,
                0.00, 0.00,
                0.02, 0.10
        ));

        register(map, new PetDefinition(
                "hoody_fox",
                "cubee-hoody_fox",
                "Hoody Fox Cubee",
                TextColor.color(0x4F8A3F),
                Material.SWEET_BERRIES,
                "LEGENDARY",
                "Nature Companion",
                "Nature",
                "Forest scout boosting Garden crop drops and XP.",
                75_000L,
                0.05, 0.25,
                0.03, 0.15,
                0.00, 0.00
        ));

        register(map, new PetDefinition(
                "twinkle_witch",
                "cubee-twinkle_witch",
                "Twinkle Witch Cubee",
                TextColor.color(0xC48AE8),
                Material.BREWING_STAND,
                "EPIC",
                "Arcane Scholar",
                "Magic",
                "Brews rapid Farming XP gains while harvesting.",
                50_000L,
                0.02, 0.10,
                0.04, 0.25,
                0.00, 0.00
        ));

        register(map, new PetDefinition(
                "llama",
                "cubee-llama",
                "Llama Cubee",
                TextColor.color(0xC4A882),
                Material.WHITE_WOOL,
                "RARE",
                "Trail Runner",
                "Poison",
                "Nimble pack companion adding speed and fortune.",
                20_000L,
                0.02, 0.12,
                0.00, 0.00,
                0.03, 0.15
        ));

        register(map, new PetDefinition(
                "slingshot",
                "cubee-slingshot",
                "Slingshot Cubee",
                TextColor.color(0xA87F4F),
                Material.BOW,
                "RARE",
                "Swift Scout",
                "Light",
                "Keeps your stride fast across wide Garden rows.",
                25_000L,
                0.00, 0.00,
                0.02, 0.10,
                0.04, 0.18
        ));

        register(map, new PetDefinition(
                "bombardier_creeper",
                "cubee-bombardier_creeper",
                "Bombardier Creeper Cubee",
                TextColor.color(0x5CB04F),
                Material.CREEPER_HEAD,
                "EPIC",
                "Burst Harvester",
                "Kinetic",
                "Explosive energy granting extra crop yields.",
                45_000L,
                0.03, 0.18,
                0.00, 0.00,
                0.02, 0.10
        ));

        register(map, new PetDefinition(
                "cannon",
                "cubee-cannon",
                "Cannon Cubee",
                TextColor.color(0x6B6B7F),
                Material.TNT,
                "RARE",
                "Heavy Loader",
                "Earth",
                "Steady earth spirit boosting fortune and XP.",
                30_000L,
                0.02, 0.15,
                0.02, 0.12,
                0.00, 0.00
        ));

        register(map, new PetDefinition(
                "swag_pillager",
                "cubee-swag_pillager",
                "Swag Pillager Cubee",
                TextColor.color(0x7F7F5C),
                Material.CROSSBOW,
                "EPIC",
                "Treasure Raider",
                "Darkness",
                "Sharp-eyed raider boosting Farming XP and speed.",
                60_000L,
                0.00, 0.00,
                0.03, 0.18,
                0.03, 0.12
        ));

        this.pets = Collections.unmodifiableMap(map);
    }

    private static void register(Map<String, PetDefinition> map, PetDefinition def) {
        map.put(def.id().toLowerCase(Locale.ROOT), def);
    }

    @Nullable
    public PetDefinition find(@NotNull String id) {
        Objects.requireNonNull(id, "id cannot be null");
        String key = id.toLowerCase(Locale.ROOT);
        if (key.startsWith("cubee-")) {
            key = key.substring("cubee-".length());
        }
        return pets.get(key);
    }

    @NotNull
    public Collection<PetDefinition> all() {
        return pets.values();
    }
}

