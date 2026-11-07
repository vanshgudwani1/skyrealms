# ADR-0004: Database Driver Shading and ServiceLoader SPI Merging

## Context
In Milestone 1, `SkyRealmsDatabase` bundles external runtime libraries (`HikariCP`, `org.postgresql.Driver`, `org.h2.Driver`, and `org.flywaydb:flyway-core`).
Both PostgreSQL and H2 define `META-INF/services/java.sql.Driver`, while `flyway-core` and database extensions define `META-INF/services/org.flywaydb.core.extensibility.Plugin`.

When building fat plugin JARs with standard Gradle `jar` tasks using `DuplicatesStrategy.EXCLUDE`, SPI files in `META-INF/services/` are clobbered. This caused Flyway to fail runtime detection of the H2 fallback database (`Unsupported Database: H2 2.3`).
Furthermore, Paper plugins enable sequentially in dependency order. If `SkyRealmsCore` aggressively enabled all modules during its own `onEnable()`, downstream plugins (`SkyRealmsPlayer`) had not yet completed Paper's enable cycle, causing Bukkit event registration to throw `IllegalPluginAccessException`.

## Decision
1. **Gradle Shadow Plugin with `mergeServiceFiles()`**:
   Apply `com.gradleup.shadow:8.3.6` to shaded plugins (`SkyRealmsDatabase`). Use `mergeServiceFiles()` so that all ServiceLoader descriptor entries across shaded JARs are appended and merged rather than overwritten.
2. **ServerLoadEvent Initialization Sync**:
   `SkyRealmsCorePlugin` defers the activation of registered modules until `ServerLoadEvent`. This ensures that all Paper plugins and Bukkit services are completely enabled and ready before internal modules execute their `onEnable(ModuleContext)`.

## Consequences
- Multi-driver environments (PostgreSQL with embedded H2 fallback) resolve transparently with zero SPI descriptor collision.
- Downstream plugins can safely register Bukkit listeners and commands with their own `Plugin` instance during module enable.
- Unit tests remain unaffected as they invoke lifecycle methods directly.
