package com.skyrealms.database;

import com.skyrealms.database.migration.DatabaseMigrationService;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {

    private HikariDatabaseService databaseService;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("Flyway-TestPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:flywaydb_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");

        databaseService = new HikariDatabaseService(config);
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    @DisplayName("Should apply V1 and V2 migrations and create all player and economy tables")
    void shouldApplyMigrations() throws SQLException {
        DatabaseMigrationService migrationService = new DatabaseMigrationService(
                databaseService.getDataSource(),
                getClass().getClassLoader()
        );

        MigrateResult result = migrationService.runMigrations();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(2);

        Set<String> tables = new HashSet<>();
        try (Connection conn = databaseService.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, "sr_%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
        }

        assertThat(tables).contains(
                "sr_players",
                "sr_player_settings",
                "sr_player_stats",
                "sr_player_sessions",
                "sr_currencies",
                "sr_wallets",
                "sr_idempotency_keys",
                "sr_transactions",
                "sr_transaction_entries",
                "sr_economy_audit"
        );
    }
}
