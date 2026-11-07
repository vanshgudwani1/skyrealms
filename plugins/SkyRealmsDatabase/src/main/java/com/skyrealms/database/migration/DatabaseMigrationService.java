package com.skyrealms.database.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Service orchestrating Flyway schema migrations.
 */
public final class DatabaseMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationService.class);

    private final DataSource dataSource;
    private final ClassLoader classLoader;

    public DatabaseMigrationService(@NotNull DataSource dataSource, @NotNull ClassLoader classLoader) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader cannot be null");
    }

    @NotNull
    public MigrateResult runMigrations() {
        log.info("Running Flyway database migrations from classpath:db/migration...");
        Flyway flyway = Flyway.configure(classLoader)
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load();

        MigrateResult result = flyway.migrate();
        log.info("Flyway migration complete! Target schema version: {}, Migrations executed: {}",
                result.targetSchemaVersion, result.migrationsExecuted);
        return result;
    }
}
