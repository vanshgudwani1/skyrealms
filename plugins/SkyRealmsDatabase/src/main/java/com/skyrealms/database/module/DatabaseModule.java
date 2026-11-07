package com.skyrealms.database.module;

import com.skyrealms.database.HikariDatabaseService;
import com.skyrealms.database.migration.DatabaseMigrationService;
import com.skyrealms.platform.api.module.*;
import com.skyrealms.platform.database.DatabaseService;
import com.zaxxer.hikari.HikariConfig;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.util.Objects;

/**
 * Lifecycle module responsible for initializing connection pool and executing migrations.
 */
public final class DatabaseModule implements SkyModule {

    private static final ModuleId ID = ModuleId.of("database");

    private final Plugin plugin;
    private final ModuleMetadata metadata = ModuleMetadata.builder(ID)
            .name("SkyRealms Database")
            .version("1.0.0")
            .dependsOn(ModuleId.of("core"))
            .build();

    private ModuleState state = ModuleState.UNLOADED;
    private HikariDatabaseService databaseService;

    public DatabaseModule(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    @NotNull
    public ModuleId id() {
        return ID;
    }

    @Override
    @NotNull
    public ModuleMetadata metadata() {
        return metadata;
    }

    @Override
    @NotNull
    public ModuleState state() {
        return state;
    }

    @Override
    public void onEnable(@NotNull ModuleContext context) throws Exception {
        state = ModuleState.INITIALIZING;
        FileConfiguration config = plugin.getConfig();

        String mode = config.getString("database.mode", "auto");
        String host = config.getString("database.host", "127.0.0.1");
        int port = config.getInt("database.port", 5432);
        String dbName = config.getString("database.name", "skyrealms");
        String username = config.getString("database.username", "skyrealms");
        String password = config.getString("database.password", "password");
        boolean ssl = config.getBoolean("database.ssl", false);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("SkyRealms-HikariPool");
        hikariConfig.setMaximumPoolSize(config.getInt("pool.maximum-pool-size", 10));
        hikariConfig.setMinimumIdle(config.getInt("pool.minimum-idle", 2));
        hikariConfig.setConnectionTimeout(config.getLong("pool.connection-timeout-ms", 3000));
        hikariConfig.setIdleTimeout(config.getLong("pool.idle-timeout-ms", 600000));
        hikariConfig.setMaxLifetime(config.getLong("pool.max-lifetime-ms", 1800000));
        hikariConfig.setLeakDetectionThreshold(config.getLong("pool.leak-detection-threshold-ms", 10000));

        boolean usePostgres = !mode.equalsIgnoreCase("h2");
        if (usePostgres) {
            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s?ssl=%b", host, port, dbName, ssl);
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName("org.postgresql.Driver");

            context.logger().info("Testing PostgreSQL connection at {}:{}...", host, port);
            try {
                this.databaseService = new HikariDatabaseService(hikariConfig);
                try (Connection conn = databaseService.getConnection()) {
                    if (conn.isValid(1)) {
                        context.logger().info("Successfully connected to authoritative PostgreSQL database '{}'.", dbName);
                    }
                }
            } catch (Exception e) {
                if (mode.equalsIgnoreCase("auto")) {
                    context.logger().warn("PostgreSQL connection to {}:{} failed: {}. Falling back to embedded PostgreSQL-mode H2 for local development.",
                            host, port, e.getMessage());
                    if (databaseService != null) {
                        databaseService.close();
                    }
                    this.databaseService = createH2Fallback(context, config);
                } else {
                    state = ModuleState.FAILED;
                    throw e;
                }
            }
        } else {
            this.databaseService = createH2Fallback(context, config);
        }

        // Run Flyway Migrations
        if (config.getBoolean("migrations.auto-run", true)) {
            DatabaseMigrationService migrationService = new DatabaseMigrationService(
                    databaseService.getDataSource(),
                    plugin.getClass().getClassLoader()
            );
            migrationService.runMigrations();
        }

        // Register in internal ServiceRegistry & Bukkit ServicesManager
        context.services().register(DatabaseService.class, databaseService);
        Bukkit.getServicesManager().register(DatabaseService.class, databaseService, plugin, ServicePriority.High);

        state = ModuleState.ENABLED;
        context.logger().info("SkyRealms Database module is active and serving connections.");
    }

    private HikariDatabaseService createH2Fallback(ModuleContext context, FileConfiguration config) {
        File dataDir = context.dataDirectory().toFile();
        File h2Db = new File(dataDir, "skyrealms_dev");
        String h2Url = "jdbc:h2:" + h2Db.getAbsolutePath().replace('\\', '/') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";

        context.logger().info("Initializing embedded H2 database at: {}", h2Db.getAbsolutePath());
        HikariConfig h2Config = new HikariConfig();
        h2Config.setPoolName("SkyRealms-H2DevPool");
        h2Config.setJdbcUrl(h2Url);
        h2Config.setUsername("sa");
        h2Config.setPassword("");
        h2Config.setDriverClassName("org.h2.Driver");
        h2Config.setMaximumPoolSize(5);
        h2Config.setConnectionTimeout(3000);

        return new HikariDatabaseService(h2Config);
    }

    @Override
    public void onDisable(@NotNull ModuleContext context) throws Exception {
        context.logger().info("Disabling SkyRealms Database module...");
        if (databaseService != null) {
            databaseService.close();
            databaseService = null;
        }
        Bukkit.getServicesManager().unregister(DatabaseService.class, plugin);
        state = ModuleState.DISABLED;
    }

    public DatabaseService databaseService() {
        return databaseService;
    }
}
