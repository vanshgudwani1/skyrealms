package com.skyrealms.end;

import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.district.EndDistrict;
import com.skyrealms.end.district.EndVillageBounds;
import com.skyrealms.end.npc.EndNpcDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EndVillageConfigAndBoundsTest {

    private static EndVillageConfig config;
    private static EndVillageBounds bounds;

    @BeforeAll
    static void loadConfig() throws Exception {
        try (InputStream is = EndVillageConfigAndBoundsTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertThat(is).isNotNull();
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
            config = EndVillageConfig.load(yaml);
            bounds = new EndVillageBounds(config);
        }
    }

    @Test
    void loadsAllDistrictsNpcsAndTrainingArenasFromConfig() {
        assertThat(config.worldName()).isEqualTo("fullya_end");
        assertThat(config.districts()).hasSize(7);
        assertThat(config.npcs()).hasSize(35);
        assertThat(config.trainingArenas()).containsKeys("melee", "archery", "arcane");
    }

    @Test
    void resolvesEverySurveyedNpcAnchorToItsAssignedDistrictInsideSafeZone() {
        for (EndNpcDefinition npc : config.npcs().values()) {
            double x = npc.anchor().x();
            double y = npc.anchor().y();
            double z = npc.anchor().z();
            assertThat(bounds.isInSafeZone("fullya_end", x, y, z))
                    .as("Safe-zone check for NPC %s (%s, %s, %s)", npc.id(), x, y, z)
                    .isTrue();
            assertThat(bounds.resolveDistrict(x, y, z))
                    .as("District resolution for NPC %s", npc.id())
                    .isEqualTo(npc.district());
        }
    }

    @Test
    void resolvesOutdoorTrainingArenasToTheirDistricts() {
        assertThat(bounds.resolveDistrict(
                config.trainingArenas().get("melee").center().x(),
                config.trainingArenas().get("melee").center().y(),
                config.trainingArenas().get("melee").center().z()
        )).isEqualTo(EndDistrict.WARRIOR_QUARTER);

        assertThat(bounds.resolveDistrict(
                config.trainingArenas().get("archery").center().x(),
                config.trainingArenas().get("archery").center().y(),
                config.trainingArenas().get("archery").center().z()
        )).isEqualTo(EndDistrict.ARCHER_QUARTER);

        assertThat(bounds.resolveDistrict(
                config.trainingArenas().get("arcane").center().x(),
                config.trainingArenas().get("arcane").center().y(),
                config.trainingArenas().get("arcane").center().z()
        )).isEqualTo(EndDistrict.ARCANE_QUARTER);
    }

    @Test
    void protectsFlatHubBoundsAndDetectsVoidFall() {
        // Outer wilderness / other worlds remain outside village safe-zone
        assertThat(bounds.isInSafeZone("fullya_end", 600.0, 64.0, 600.0)).isFalse();
        assertThat(bounds.isInSafeZone("skyrealms_islands", 0.5, 64.0, 0.5)).isFalse();

        // Void rescue triggers cleanly below threshold in fullya_end only
        assertThat(bounds.isVoidFall("fullya_end", 29.5)).isTrue();
        assertThat(bounds.isVoidFall("fullya_end", 64.0)).isFalse();
        assertThat(bounds.isVoidFall("fullya", 10.0)).isFalse();
    }
}

