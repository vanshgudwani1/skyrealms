# SkyRealms Project Status

**Current Milestone:** M2 — Authoritative Economy  
**Status:** COMPLETED & VERIFIED  
**Server Target:** Paper 1.21.1 (Build 133) on Java 21 LTS  
**Last Verified:** 2026-11-07  

---

## 1. Completed Tasks (M0, M1, M2)

| Task | Scope | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Gradle Multi-Project** | Root & Subprojects | ✅ Completed | Configured Gradle 8.12 with Kotlin DSL, parallel builds, build cache, and automated deployment. |
| **Java 21 Environment** | Build & Runtime | ✅ Completed | Integrated portable Eclipse Adoptium Temurin 21.0.13 JDK. |
| **Shared Platform APIs** | `platform:api` | ✅ Completed | Created `SkyModule`, `ModuleMetadata`, `ModuleContext`, `ModuleState`, `EventBus`, and `ServiceRegistry`. |
| **Core Implementations** | `platform:common` | ✅ Completed | Built DAG `DirectedGraphDependencyResolver` (Kahn's algo + cycle detection), `ConcurrentServiceRegistry`, `DefaultEventBus`, and `DefaultModuleLifecycleManager`. |
| **Database & Integrations API** | `database-api`, `integrations-api` | ✅ Completed | Defined contracts for `DatabaseService`, `TransactionCallback`, `EconomyProvider`, and `PermissionsProvider`. |
| **Testing Harness** | `platform:testing` | ✅ Completed | Built `TestModuleContext` and `DummyTestModule`. 38 automated unit tests passing across all suites. |
| **SkyRealmsCore Plugin** | `plugins:SkyRealmsCore` | ✅ Completed | Paper plugin with `ServerLoadEvent` module boot synchronization and `/skyrealms` commands. |
| **SkyRealmsDatabase Plugin** | `plugins:SkyRealmsDatabase` | ✅ Completed | HikariCP pool, PostgreSQL driver, embedded H2 fallback, Flyway migration runner (`V1`, `V2`), shadowJar with SPI descriptor merging. |
| **SkyRealmsPlayer Plugin** | `plugins:SkyRealmsPlayer` | ✅ Completed | `PlayerProfile`, `PlayerSettings`, `PlayerStats`, `PlayerSession` models, async pre-login cache preloading, quit saving, and `/profile [player]` command. |
| **SkyRealmsEconomy Plugin** | `plugins:SkyRealmsEconomy` | ✅ Completed | Multi-currency (Coins & Gems), append-only double-entry ledger, idempotency keys, Vault economy bridge adapter, and `/balance`, `/pay`, `/eco` commands. |
| **Paper Server Runtime Verification** | Minecraft Runtime | ✅ Completed | Deployed all 4 JARs alongside EssentialsX, Vault, LuckPerms, and ViaVersion. Clean boot and reverse-order shutdown verified across 4 active modules. |
| **Documentation & ADRs** | Docs & Governance | ✅ Completed | Created `README.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `CONTRIBUTING.md`, and ADRs 0001–0004. |

---

## 2. Incomplete / Upcoming Tasks (M3)

* [ ] Implement `SkyRealmsIslands` plugin.
* [ ] Custom void world generator with Paper World generation API.
* [ ] Island template schematic pasting and boundary protection.
* [ ] Member management, permissions, and roles.
* [ ] Island upgrades (size, generator limit, minion limit).
* [ ] Safe island reset and visiting mechanics.

---

## 3. Blocked Tasks

* None. Multi-currency economy, ledger journals, Vault bridging, player persistence, and Paper runtime lifecycle are 100% verified.
