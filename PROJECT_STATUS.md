# SkyRealms Project Status

**Current Milestone:** Garden Plot System Redesign — Customizable Plots, Crop Beds & Layout Studio (Phases P1–P5)  
**Status:** COMPLETED & VERIFIED (97/97 Automated Tests Passing)  
**Server Target:** Paper 1.21.1 (Build 133) on Java 21 LTS  
**Last Verified:** 2026-11-10  
**Master Memory Document:** [`MEMORY.md`](file:///C:/servermc/skyrealms/MEMORY.md)

---

## 1. Completed Tasks (M0–M4, Void Lobby, Garden G1–G3 & Farming Overhaul F1–F6)

| Task | Scope | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Gradle Multi-Project** | Root & Subprojects | ✅ Completed | Configured Gradle 8.12 with Kotlin DSL, parallel builds, build cache, and automated deployment. |
| **Java 21 Environment** | Build & Runtime | ✅ Completed | Integrated portable Eclipse Adoptium Temurin 21.0.13 JDK (`C:\servermc\jdk-21`). |
| **Shared Platform APIs** | `platform:api` | ✅ Completed | Created `SkyModule`, `ModuleMetadata`, `ModuleContext`, `ModuleState`, `EventBus`, and `ServiceRegistry`. |
| **Core Implementations** | `platform:common` | ✅ Completed | Built DAG `DirectedGraphDependencyResolver` (Kahn's algo + cycle detection), `ConcurrentServiceRegistry`, `DefaultEventBus`, and `DefaultModuleLifecycleManager`. |
| **Database & Integrations API** | `database-api`, `integrations-api` | ✅ Completed | Defined contracts for `DatabaseService`, `TransactionCallback`, `EconomyProvider`, and `PermissionsProvider`. |
| **Testing Harness** | `platform:testing` | ✅ Completed | Built `TestModuleContext` and `DummyTestModule`. Automated unit tests passing across all suites (70+ tests). |
| **SkyRealmsCore Plugin** | `plugins:SkyRealmsCore` | ✅ Completed | Paper plugin with `ServerLoadEvent` module boot synchronization and `/skyrealms` commands. |
| **SkyRealmsDatabase Plugin** | `plugins:SkyRealmsDatabase` | ✅ Completed | HikariCP pool, PostgreSQL driver, embedded H2 fallback, Flyway migration runner (`V1`, `V2`, `V3`, `V4`, `V5`, `V6`), shadowJar with SPI descriptor merging (ADR-0004). |
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
| **Garden Spatial Region Allocator** | `GardenSpatialAllocator` | ✅ Completed | Deterministic 2D spiral allocator mapping sequential island indexes to collision-free $768 \times 768$ regions with 1024-block spacing (256-block void gap buffer). |
| **Grand Cathedral Conservatory Dome Generator** | `GardenDomeGenerator` | ✅ Completed | Procedural generator building a monumental $640 \times 180$ block Victorian/Gothic cathedral glass conservatory: soaring central crossing cupola (Y=124), 512-block barrel vaults with polished deepslate ribs & copper spines, 32-block wide Grand Boulevard with sunken reflecting canal, illuminated bridges, West Arrival Pavilion, and East Elarion Botanical Sanctuary. |
| **16-Plot 4x4 Chunks Layout & Irrigation** | `GardenPlot` | ✅ Completed | Symmetrical 2 rows $\times$ 8 columns layout where each plot is at least 4x4 chunks ($64 \times 64$ blocks = 4,096 blocks), complete with ornate gatehouse portico, perimeter stone curb, and 100% moisture irrigation grid with sea lanterns. Plot 1 pre-planted with 4,000+ mature golden wheat. |
| **Legacy Feature Removal & mcMMO Switch** | `SkyRealmsFarming` & `mcMMO` | ✅ Completed | Deleted Natural Fast-Harvest, Elarion/First Harvest quests, and standalone Crop Mastery XP curve. Configured mcMMO (`HERBALISM` -> Farming only) with disabled stock abilities/subskills and runtime command/region guards. |
| **F1: Centralized Harvest Reward Pipeline** | `HarvestRewardPipeline` & `GardenBalanceConfig` | ✅ Completed | Unified bonus drops (75% max chance, 3 bonus item cap), mcMMO XP (preventing double base XP), collections (`sr_farming_crop_collections`), and per-block harvest claim locking. |
| **F2: mcMMO Integration Hardening** | `McMMOIntegrationListener` & `McMMOFarmingService` | ✅ Completed | Verified mcMMO 2.3.002 `ExperienceAPI`; enforced authorized Garden region + `BLOCK_BREAK` permission check on `McMMOPlayerPreXpGainEvent`; blocked 18 non-farming skills & commands. |
| **F3: Garden Abilities Balancing & Security** | `GardenAbilityService` & `VerdantAuraManager` | ✅ Completed | Balanced `Bountiful Harvest` (5%–50%), bounded `Verdant Aura` (removed on Garden/world exit), secured `Sakura Harvest Surge` (per-block validation, cooldown retained across relog), and capped `Golden Sprout` (per-harvest 250 / daily 15,000 Coins). |
| **F4: Crop Mastery Identities & GUI** | `MasteryMilestone`, `CropMastery`, `CropMasteryGui` | ✅ Completed | Distinct identities & reward separation (drops, XP, coins, discoveries, cosmetics, equipment hooks) across all 10 crops × 7 mcMMO-gated tiers. |
| **F5: Botanical Journal & Flyway V7** | `BotanicalJournalService`, `BotanicalJournalGui`, `V7` | ✅ Completed | Added Flyway `V7__create_botanical_journal.sql` (`sr_botanical_journal`), connected collections & mutation discoveries, 8 achievements, and unified navigation across `/garden abilities`, `/garden mastery`, and `/garden journal`. |
| **F6: Automated Suite & JAR Deployment** | All Modules | ✅ Completed | 90/90 unit tests passing (`.\gradlew test --rerun-tasks`); deployed `SkyRealmsDatabase-1.0.0.jar` and `SkyRealmsFarming-1.0.0.jar` to `C:\servermc\plugins\`. |
| **P1–P5: Customizable Plot System & V8** | `garden.plot.*`, `GardenDomeGenerator`, `V8` | ✅ Completed | Replaced fixed pre-planted plot interiors with Clean Canvas + starter 9×9 Basic Bed; added 5 layout presets (`Traditional Rows`, `Japanese Garden`, `Greenhouse`, `Terraced Garden`, `Clean Canvas`), `CropBed` (`Basic`, `Rich Soil`, `Specialized`) routed through `HarvestRewardPipeline`, `IrrigationType`, `DecorationStyle`, `PlotUpgradeTier` Lv.1–4, `PlotEditorService`, 54-slot `GardenPlotGui`, `/garden plot [edit|layout|save|reset]`, perimeter curb/Torii shield, Flyway `V8__create_customizable_plot_tables.sql`, and 7 new tests (`97/97` passing total). |

---

## 2. Incomplete / Upcoming Tasks (Future Garden & Platform Milestones)

* [ ] **Phase G5: Garden Equipment System** (Specialized Hoes, Harvesting Scythe, Watering Can, Fertilizer Tools, Farmer Armor).
* [ ] **Phase G6: Garden Soil & Research Progression** (soil profiles, fertilizers, research points, 5 research branches).
* [ ] **Phase G7: Crop Mutation & Divine Blessings** (mutation engine, divine altar, blessings).
* [ ] **Phase G8: Garden Competitions & Endgame** (Harvest Festival, rotating weekly categories, leaderboards).

---

## 3. Blocked Tasks

* None. Phase G1 has passed all acceptance tests, unit tests, and live Paper server runtime verification.
