package com.skyrealms.end;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.end.config.EndVillageConfig;
import com.skyrealms.end.gear.EndGearCatalog;
import com.skyrealms.end.gear.EndGearService;
import com.skyrealms.end.progression.CombatContributionTracker;
import com.skyrealms.end.progression.CombatProgressionService;
import com.skyrealms.end.progression.DisciplineProgressRepository;
import com.skyrealms.end.wilderness.BestiaryRepository;
import com.skyrealms.end.wilderness.BestiaryRepository.KillRecordOutcome;
import com.skyrealms.end.wilderness.EndLootTable;
import com.skyrealms.end.wilderness.EndLootTable.RolledLootEntry;
import com.skyrealms.end.wilderness.EndMobArchetype;
import com.skyrealms.end.wilderness.WildernessRegion;
import com.skyrealms.items.catalog.ItemCatalog;
import com.skyrealms.items.factory.SkyRealmsItemFactory;
import com.skyrealms.items.service.HoeUpgradeService;
import com.skyrealms.items.service.ItemRegistryService;
import com.zaxxer.hikari.HikariConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class EndWildernessAndBestiaryTest {

    private HikariDatabaseService databaseService;
    private BestiaryRepository bestiaryRepository;
    private EndLootTable lootTable;

    @BeforeEach
    void setUp() {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("EndWildTest-" + UUID.randomUUID());
        cfg.setJdbcUrl("jdbc:h2:mem:endwild_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
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
        Plugin mockPlugin = mock(Plugin.class);
        when(mockPlugin.isEnabled()).thenReturn(false);

        ItemCatalog itemCatalog = new ItemCatalog();
        SkyRealmsItemFactory factory = new SkyRealmsItemFactory(itemCatalog);
        ItemRegistryService itemService = new ItemRegistryService(itemCatalog, factory, new HoeUpgradeService(itemCatalog, factory));
        EndGearCatalog gearCatalog = new EndGearCatalog();
        gearCatalog.registerAllInto(itemService);
        CombatProgressionService prog = new CombatProgressionService(
                mockPlugin, config, new DisciplineProgressRepository(databaseService),
                new CombatContributionTracker(), LoggerFactory.getLogger("EndWildTest")
        );
        EndGearService gearService = new EndGearService(gearCatalog, itemService, prog);

        bestiaryRepository = new BestiaryRepository(databaseService);
        lootTable = new EndLootTable(itemService, gearService);
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    void definesFourHuntingRegionsThreeNonDragonBossesAndRegionalResolution() {
        assertThat(WildernessRegion.values()).hasSize(4);
        assertThat(WildernessRegion.SHATTERED_FIELDS.minLevel()).isEqualTo(1);
        assertThat(WildernessRegion.ABYSSAL_GROUNDS.maxLevel()).isEqualTo(30);

        assertThat(WildernessRegion.resolveAt(0.0, 0.0)).isEqualTo(WildernessRegion.SHATTERED_FIELDS);
        assertThat(WildernessRegion.resolveAt(320.0, 5.0)).isEqualTo(WildernessRegion.CRYSTAL_WILDS);
        assertThat(WildernessRegion.resolveAt(5.0, 320.0)).isEqualTo(WildernessRegion.FORGOTTEN_RUINS);
        assertThat(WildernessRegion.resolveAt(-320.0, -320.0)).isEqualTo(WildernessRegion.ABYSSAL_GROUNDS);

        long bossCount = java.util.Arrays.stream(EndMobArchetype.values()).filter(EndMobArchetype::isFieldBoss).count();
        assertThat(bossCount).isEqualTo(3);
        for (EndMobArchetype mob : EndMobArchetype.values()) {
            assertThat(mob.entityType()).isNotEqualTo(EntityType.ENDER_DRAGON);
        }
    }

    @Test
    void rollsRegionalAndBossLootAndPersistsBestiaryDiscoveryAndEliteKills() throws Exception {
        Random rng = new Random(1337L);
        List<RolledLootEntry> bossDrops = lootTable.rollEntries(EndMobArchetype.BOSS_FORGOTTEN_HARVESTER, false, rng);
        assertThat(bossDrops).isNotEmpty();
        assertThat(bossDrops.stream().anyMatch(e -> e.itemId().equals(EndGearCatalog.MAT_FORGOTTEN_CORE))).isTrue();

        UUID hunter = UUID.randomUUID();
        KillRecordOutcome first = bestiaryRepository.recordKill(hunter, EndMobArchetype.CRYSTAL_SENTINEL.id(), false);
        assertThat(first.newlyDiscovered()).isTrue();
        assertThat(first.entry().kills()).isEqualTo(1);
        assertThat(first.entry().elitesKilled()).isZero();

        KillRecordOutcome second = bestiaryRepository.recordKill(hunter, EndMobArchetype.CRYSTAL_SENTINEL.id(), true);
        assertThat(second.newlyDiscovered()).isFalse();
        assertThat(second.entry().kills()).isEqualTo(2);
        assertThat(second.entry().elitesKilled()).isEqualTo(1);

        var loaded = bestiaryRepository.loadAll(hunter);
        assertThat(loaded).containsKey(EndMobArchetype.CRYSTAL_SENTINEL.id());
        assertThat(loaded.get(EndMobArchetype.CRYSTAL_SENTINEL.id()).elitesKilled()).isEqualTo(1);
    }
}

