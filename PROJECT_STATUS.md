# SkyRealms Project Status

**Current Milestone:** M1 — Core, Database and Players  
**Status:** COMPLETED & VERIFIED  
**Server Target:** Paper 1.21.1 (Build 133) on Java 21 LTS  
**Last Verified:** 2026-11-07  

---

## 1. Completed Tasks (M0 & M1)

| Task | Scope | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Gradle Multi-Project** | Root & Subprojects | ✅ Completed | Configured Gradle 8.12 with Kotlin DSL, parallel builds, build cache, and automated deployment. |
| **Java 21 Environment** | Build & Runtime | ✅ Completed | Integrated portable Eclipse Adoptium Temurin 21.0.13 JDK. |
| **Shared Platform APIs** | `platform:api` | ✅ Completed | Created `SkyModule`, `ModuleMetadata`, `ModuleContext`, `ModuleState`, `EventBus`, and `ServiceRegistry`. |
| **Core Implementations** | `platform:common` | ✅ Completed | Built DAG `DirectedGraphDependencyResolver` (Kahn's algo + cycle detection), `ConcurrentServiceRegistry`, `DefaultEventBus`, and `DefaultModuleLifecycleManager`. |
| **Database & Integrations API** | `database-api`, `integrations-api` | ✅ Completed | Defined contracts for `DatabaseService`, `TransactionCallback`, `EconomyProvider`, and `PermissionsProvider`. |
| **Testing Harness** | `platform:testing` | ✅ Completed | Built `TestModuleContext` and `DummyTestModule`. 27 automated unit tests with 100% pass rate. |
| **SkyRealmsCore Plugin** | `plugins:SkyRealmsCore` | ✅ Completed | Paper plugin with `ServerLoadEvent` module boot synchronization and `/skyrealms` commands. |
| **SkyRealmsDatabase Plugin** | `plugins:SkyRealmsDatabase` | ✅ Completed | HikariCP pool, PostgreSQL driver, embedded H2 fallback, Flyway migration runner (`V1__create_player_tables.sql`), shadowJar with SPI descriptor merging. |
| **SkyRealmsPlayer Plugin** | `plugins:SkyRealmsPlayer` | ✅ Completed | `PlayerProfile`, `PlayerSettings`, `PlayerStats`, `PlayerSession` models, async pre-login cache preloading, quit saving, and `/profile [player]` command. |
| **Paper Server Runtime Verification** | Minecraft Runtime | ✅ Completed | Deployed all 3 JARs alongside EssentialsX, Vault, LuckPerms, and ViaVersion. Clean boot and reverse-order shutdown verified. |
| **Documentation & ADRs** | Docs & Governance | ✅ Completed | Created `README.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `CONTRIBUTING.md`, and ADRs 0001–0004. |

---

## 2. Incomplete / Upcoming Tasks (M2)

* [ ] Implement `SkyRealmsEconomy` multi-currency core (Coins, Gems).
* [ ] Create Flyway migration `V2__create_economy_tables.sql` (`sr_currencies`, `sr_wallets`, `sr_transactions`, `sr_transaction_entries`, `sr_economy_audit`, `sr_idempotency_keys`).
* [ ] Atomic balance modifications with idempotency keys and ledger audit history.
* [ ] Implement Vault economy bridge adapter.
* [ ] Implement player economy commands (`/balance`, `/pay`, `/eco`).

---

## 3. Blocked Tasks

* None. Database connectivity, migrations, player persistence, and Paper runtime lifecycle are 100% verified.
