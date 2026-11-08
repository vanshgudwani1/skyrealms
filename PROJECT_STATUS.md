# SkyRealms Project Status

**Current Milestone:** Garden Redesign — Phase G1: Legacy Farming Generator Retirement & Garden Foundation  
**Status:** COMPLETED & VERIFIED  
**Server Target:** Paper 1.21.1 (Build 133) on Java 21 LTS  
**Last Verified:** 2026-11-08  
**Master Memory Document:** [`MEMORY.md`](file:///C:/servermc/skyrealms/MEMORY.md)

---

## 1. Completed Tasks (M0–M4, Void Lobby & Garden G1)

| Task | Scope | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Gradle Multi-Project** | Root & Subprojects | ✅ Completed | Configured Gradle 8.12 with Kotlin DSL, parallel builds, build cache, and automated deployment. |
| **Java 21 Environment** | Build & Runtime | ✅ Completed | Integrated portable Eclipse Adoptium Temurin 21.0.13 JDK (`C:\servermc\jdk-21`). |
| **Shared Platform APIs** | `platform:api` | ✅ Completed | Created `SkyModule`, `ModuleMetadata`, `ModuleContext`, `ModuleState`, `EventBus`, and `ServiceRegistry`. |
| **Core Implementations** | `platform:common` | ✅ Completed | Built DAG `DirectedGraphDependencyResolver` (Kahn's algo + cycle detection), `ConcurrentServiceRegistry`, `DefaultEventBus`, and `DefaultModuleLifecycleManager`. |
| **Database & Integrations API** | `database-api`, `integrations-api` | ✅ Completed | Defined contracts for `DatabaseService`, `TransactionCallback`, `EconomyProvider`, and `PermissionsProvider`. |
| **Testing Harness** | `platform:testing` | ✅ Completed | Built `TestModuleContext` and `DummyTestModule`. Automated unit tests passing across all suites (65+ tests). |
| **SkyRealmsCore Plugin** | `plugins:SkyRealmsCore` | ✅ Completed | Paper plugin with `ServerLoadEvent` module boot synchronization and `/skyrealms` commands. |
| **SkyRealmsDatabase Plugin** | `plugins:SkyRealmsDatabase` | ✅ Completed | HikariCP pool, PostgreSQL driver, embedded H2 fallback, Flyway migration runner (`V1`, `V2`, `V3`, `V4`, `V5`), shadowJar with SPI descriptor merging (ADR-0004). |
| **SkyRealmsPlayer Plugin** | `plugins:SkyRealmsPlayer` | ✅ Completed | `PlayerProfile`, `PlayerSettings`, `PlayerStats`, `PlayerSession` models, async pre-login cache preloading, quit saving, and `/profile [player]` command. |
| **SkyRealmsEconomy Plugin** | `plugins:SkyRealmsEconomy` | ✅ Completed | Multi-currency (Coins & Gems), append-only double-entry ledger, idempotency keys, Vault economy bridge adapter, and `/balance`, `/pay`, `/eco` commands. |
| **SkyRealmsIslands Plugin** | `plugins:SkyRealmsIslands` | ✅ Completed | Void world generator, $O(1)$ spatial spiral grid allocator, native starter island generator, centralized boundary protection, role hierarchy (`LEADER`, `OFFICER`, `MEMBER`, `VISITOR`), upgrade pipeline, GUI, `/island` suite, and `/is farm` alias. |
| **Pure Void Lobby Dimension** | Server World Config | ✅ Completed | Flat air world generation (`minecraft:air` height 1, structures false, `minecraft:cherry_grove` biome). Zero terrain noise, bedrock, or ocean clipping. |
| **Physics Anti-Break Shield** | `LobbyListener` | ✅ Completed | Cancels block physics, leaf decay, falling blocks, fire spread, and item spawns in `world`. |
| **Dragon Nest Overhaul** | FAWE Schematic Remap | ✅ Completed | Eliminated 12,464 concrete powder blocks to solid Pink Concrete, removed 27 fire blocks, converted 40 anvils to iron, and remapped 53,813 sea lanterns to pink/magenta velvet dragon scales. |
| **3-Second Teleport Warmup** | `TeleportWarmupService` | ✅ Completed | Dual-helix particle aura (`CHERRY_LEAVES` + `END_ROD`), action bar/title countdown, pitch-rising ticks, movement cancellation, arrival sound fanfare, and 5s cooldown across all teleports. |
| **Central Spawn Dais & Core** | `LobbyService` | ✅ Completed | Sunken deepslate/amethyst dais with underlit pink/magenta glass floor, floating Celestial Star Diamond at $(0, 73, 0)$, and live 3D orbital particle engine loop. |
| **Modern TextDisplay Holograms** | `HologramService` | ✅ Completed | 8 active RGB billboarding holograms with dark celestial glass backgrounds across spawn and castle. |
| **Castle Gameplay Integration** | `LobbyService` & Essentials | ✅ Completed | Grand sweeping bridge into Imperial Citadel, Royal Treasury & Bank, Arcane Upgrades Spire, Citadel Throne Room, and Royal Armory with `/warp` suite. |
| **Legacy Farming Generator Retirement** | `SkyRealmsFarming` | ✅ Completed | Completely disabled automatic generator crop spawning, removed generator node harvest intercepts, blocked legacy core placement with friendly retirement notice, and preserved all legacy tables (`sr_farming_generators`, `sr_farming_generator_upgrades`, `sr_farming_player_progress`, `sr_farming_crop_collections`, `sr_farming_mutation_discoveries`). |
| **Shared Garden World (`skyrealms_garden`)** | `GardenWorldManager` | ✅ Completed | Dedicated Paper void world with fixed noon daylight, clear weather, disabled mob spawning, and Plains biome. |
| **Garden Spatial Region Allocator** | `GardenSpatialAllocator` | ✅ Completed | Deterministic 2D spiral allocator mapping sequential island indexes to collision-free $256 \times 256$ regions with 512-block spacing. |
| **Greenhouse Dome Generator** | `GardenDomeGenerator` | ✅ Completed | Procedural generator building vaulted glass conservatory dome, central grand boulevard, entrance plaza, Elarion shrine, 4 specialized alcoves, and 16 crop plots (2 rows $\times$ 8 columns) with sub-surface irrigation and fertile farmland. |
| **16-Plot Layout & Progression** | `GardenPlot` | ✅ Completed | Symmetrical 2 rows $\times$ 8 columns arrangement with starter Plot 1 unlocked (mature wheat) and Plots 2–16 unlockable through `/garden plots`. |
| **Garden Region Protection** | `GardenProtectionListener` | ✅ Completed | Strict boundary containment preventing players/projectiles from leaving their 256x256 region, plot permissions, dome architectural grief protection, and farmland trample prevention. |
| **Garden Commands & Navigation** | `GardenCommand` & `IslandCommand` | ✅ Completed | Implemented `/garden home`, `/garden leave`, `/garden info`, `/garden plots`, `/garden admin`, and `/is farm` with 3-second teleport warmup aura. |
| **Database Migration V5** | `SkyRealmsDatabase` | ✅ Completed | Flyway V5 migration creating `sr_gardens`, `sr_garden_plots`, `sr_garden_unlocks`, `sr_crop_mastery`, and `sr_player_research`. |
| **Paper Server Runtime Verification** | Minecraft Runtime | ✅ Completed | Deployed updated JARs to Paper 1.21.1 server. All 6 modules enabled without errors, running at 20.0 TPS with `skyrealms_garden` loaded. |

---

## 2. Incomplete / Upcoming Tasks (Garden Phased Roadmap)

* [ ] **Phase G2: Natural Farming Engine** (crop planting, vanilla growth, natural harvesting, crop XP, collections, plot permissions).
* [ ] **Phase G3: Garden Unlock & Introduction** (offering chest workflow, divine cinematic, Elarion introduction, First Harvest quest).
* [ ] **Phase G4: Crop Mastery System** (crop-specific XP, 30 levels per crop, milestone rewards, mastery GUI).
* [ ] **Phase G5: Garden Equipment System** (Harvesting Scythe, Watering Can, Cultivator Hoe, Farmer Backpack, Farmer Armor).
* [ ] **Phase G6: Garden Soil & Research Progression** (soil profiles, fertilizers, research points, 5 research branches).
* [ ] **Phase G7: Crop Mutation & Divine Blessings** (mutation engine, Botanical Journal, divine altar, blessings).
* [ ] **Phase G8: Garden Competitions & Endgame** (Harvest Festival, rotating weekly categories, leaderboards).

---

## 3. Blocked Tasks

* None. Phase G1 has passed all acceptance tests, unit tests, and live Paper server runtime verification.
