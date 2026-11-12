package com.skyrealms.end;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.district.EndDistrict;
import com.skyrealms.end.npc.AnimationProfile;
import com.skyrealms.end.npc.EndNpcDefinition;
import com.skyrealms.end.npc.EndNpcRegistry;
import com.skyrealms.end.npc.NpcActionType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EndNpcRegistryTest {

    private static EndNpcRegistry registry;

    @BeforeAll
    static void init() throws Exception {
        try (InputStream is = EndNpcRegistryTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertThat(is).isNotNull();
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
            EndVillageConfig config = EndVillageConfig.load(yaml);
            registry = new EndNpcRegistry(config);
        }
    }

    @Test
    void assignsAllThirtyFiveRequiredNpcsToProperDistricts() {
        assertThat(registry.size()).isEqualTo(35);
        assertThat(registry.inDistrict(EndDistrict.CENTRAL_PLAZA)).hasSize(4);
        assertThat(registry.inDistrict(EndDistrict.WARRIOR_QUARTER)).hasSize(5);
        assertThat(registry.inDistrict(EndDistrict.ARCHER_QUARTER)).hasSize(5);
        assertThat(registry.inDistrict(EndDistrict.ARCANE_QUARTER)).hasSize(5);
        assertThat(registry.inDistrict(EndDistrict.MERCHANT_DISTRICT)).hasSize(7);
        assertThat(registry.inDistrict(EndDistrict.GRAND_QUEST_HALL)).hasSize(4);
        assertThat(registry.inDistrict(EndDistrict.DRAGON_SANCTUARY)).hasSize(5);
    }

    @Test
    void parsesDialogueActionsAnimationProfilesAndGuardPatrols() {
        for (EndNpcDefinition def : registry.all()) {
            assertThat(def.id()).isNotBlank();
            assertThat(def.displayName()).isNotBlank();
            assertThat(def.titleTag()).isNotBlank();
            assertThat(def.dialogue()).isNotEmpty();
            assertThat(def.proximityRadius()).isGreaterThan(0.0);
        }

        assertThat(registry.get("weaponsmith_doran").animationProfile()).isEqualTo(AnimationProfile.BLACKSMITH);
        assertThat(registry.get("weaponsmith_doran").actionType()).isEqualTo(NpcActionType.CRAFTING);
        assertThat(registry.get("dragon_historian").actionType()).isEqualTo(NpcActionType.DRAGON_LICENSE);

        EndNpcDefinition varek = registry.get("hunter_varek");
        assertThat(varek).isNotNull();
        assertThat(varek.hasPatrol()).isTrue();
        assertThat(varek.patrolRoute()).hasSize(4);

        EndNpcDefinition keeper = registry.get("dungeon_keeper");
        assertThat(keeper).isNotNull();
        assertThat(keeper.hasPatrol()).isTrue();
    }
}

