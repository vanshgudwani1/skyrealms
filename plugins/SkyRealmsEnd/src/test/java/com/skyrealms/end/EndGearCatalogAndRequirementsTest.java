package com.skyrealms.end;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.gear.EndGearCatalog;
import com.skyrealms.end.gear.EndGearService;
import com.skyrealms.end.gear.EndGearService.RequirementCheckResult;
import com.skyrealms.end.progression.CombatContributionTracker;
import com.skyrealms.end.progression.CombatDiscipline;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.DisciplineProgressRepository;
import com.skyrealms.items.catalog.ItemCatalog;
import com.skyrealms.items.factory.SkyRealmsItemFactory;
import com.skyrealms.items.service.HoeUpgradeService;
import com.skyrealms.items.service.ItemRegistryService;
import com.skyrealms.platform.integrations.SkyItemStats;
import com.zaxxer.hikari.HikariConfig;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EndGearCatalogAndRequirementsTest {

    private HikariDatabaseService databaseService;
    private ItemCatalog universalCatalog;
    private ItemRegistryService itemService;
    private CombatProgressionService progressionService;
    private EndGearCatalog gearCatalog;
    private EndGearService gearService;

    @BeforeEach
    void setUp() {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("EndGearTest-" + UUID.randomUUID());
        cfg.setJdbcUrl("jdbc:h2:mem:endgear_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(cfg);
        MigrateResult res = new DatabaseMigrationService(databaseService.getDataSource(), getClass().getClassLoader()).runMigrations();
        assertThat(res.success).isTrue();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("config.yml")),
                StandardCharsets.UTF_8
        ));
        EndVillageConfig config = EndVillageConfig.load(yaml);
        Plugin mockPlugin = Mockito.mock(Plugin.class);
        when(mockPlugin.isEnabled()).thenReturn(false);

        universalCatalog = new ItemCatalog();
        SkyRealmsItemFactory factory = new SkyRealmsItemFactory(universalCatalog);
        itemService = new ItemRegistryService(universalCatalog, factory, new HoeUpgradeService(universalCatalog, factory));

        progressionService = new CombatProgressionService(
                mockPlugin, config, new DisciplineProgressRepository(databaseService),
                new CombatContributionTracker(), LoggerFactory.getLogger("EndGearTest")
        );

        gearCatalog = new EndGearCatalog();
        gearCatalog.registerAllInto(itemService);
        gearService = new EndGearService(gearCatalog, itemService, progressionService);
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    void registersAllEndWeaponsArmorSetsAndMaterialsIntoUniversalCatalog() {
        // 3 Farming Hoes + 8 End Materials + 1 Ward + 21 Weapons + 28 Armor pieces = 61 items, 7 Armor sets
        assertThat(universalCatalog.all()).hasSize(61);
        assertThat(universalCatalog.allArmorSets()).hasSize(7);
        assertThat(gearCatalog.allSpecs()).hasSize(50);

        List<String> requiredWeapons = List.of(
                "end_apprentice_bow", "end_hunter_bow", "end_endwood_longbow",
                "end_voidpiercer", "end_rift_hunter_bow", "end_astral_longbow",
                "end_apprentice_sword", "end_endsteel_blade", "end_void_cleaver",
                "end_riftbreaker", "end_ancient_greatblade", "end_astral_executioner",
                "end_apprentice_staff", "end_crystal_staff", "end_voidcaller",
                "end_rift_conduit", "end_ancient_arcane_staff", "end_astral_archmage_staff"
        );
        for (String id : requiredWeapons) {
            assertThat(universalCatalog.find(id)).isNotNull();
        }
    }

    @Test
    void aggregatesMultiSlotArmorAndSetBonusAndEnforcesDisciplineLevelGates() throws Exception {
        UUID player = UUID.randomUUID();
        ItemStack astralBow = mockCustomStack(Material.BOW, "end_astral_longbow", 1);

        RequirementCheckResult blocked = gearService.checkRequirement(player, astralBow);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.discipline()).isEqualTo(CombatDiscipline.ARCHERY);
        assertThat(blocked.requiredLevel()).isEqualTo(30);

        progressionService.setDisciplineLevel(player, CombatDiscipline.ARCHERY, 30);
        RequirementCheckResult unlocked = gearService.checkRequirement(player, astralBow);
        assertThat(unlocked.allowed()).isTrue();

        ItemStack[] fullScoutSet = new ItemStack[]{
                mockCustomStack(Material.LEATHER_BOOTS, "end_scout_boots", 1),
                mockCustomStack(Material.LEATHER_LEGGINGS, "end_scout_leggings", 1),
                mockCustomStack(Material.LEATHER_CHESTPLATE, "end_scout_chestplate", 1),
                mockCustomStack(Material.LEATHER_HELMET, "end_scout_helmet", 1)
        };
        assertThat(itemService.getActiveArmorSet(fullScoutSet))
                .isPresent()
                .get()
                .satisfies(bonus -> assertThat(bonus.setId()).isEqualTo("end_scout"));

        SkyItemStats total = itemService.calculateEquippedStats(astralBow, null, fullScoutSet);
        assertThat(total.combatDamage()).isGreaterThanOrEqualTo(24.0);
        assertThat(total.defense()).isGreaterThanOrEqualTo(8.0);
        assertThat(total.speedBonus()).isGreaterThan(0.20); // bow + 4 pieces + 4pc set bonus

        Random rng = new Random(42L);
        for (int i = 0; i < 50; i++) {
            assertThat(EndGearCatalog.rollQualityLevel(rng, false)).isBetween(1, 4);
            assertThat(EndGearCatalog.rollQualityLevel(rng, true)).isBetween(2, 5);
        }
    }

    private static ItemStack mockCustomStack(Material material, String customId, int qualityLevel) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(stack.getType()).thenReturn(material);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(SkyRealmsItemFactory.CUSTOM_ITEM_KEY, PersistentDataType.STRING)).thenReturn(customId);
        when(pdc.get(SkyRealmsItemFactory.ITEM_LEVEL_KEY, PersistentDataType.INTEGER)).thenReturn(qualityLevel);
        return stack;
    }
}

