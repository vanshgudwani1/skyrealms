# 🌌 SkyRealms — Master Memory File & Architecture Blueprint

> **Authoritative Technical Specification & State Archive**  
> **Server Target:** Minecraft Paper 1.21.1 (Build 133) on Java 21 LTS  
> **Repository Root:** `C:\servermc\skyrealms`  
> **Runtime Server Root:** `C:\servermc`  
> **Last Updated:** 2026-11-10  
> **Status:** Milestones M0–M4, Garden F1–F6 & Customizable Plot System P1–P5 COMPLETED & VERIFIED (97/97 Automated Tests Passing)

---

## 1. System Overview & Core Philosophy

SkyRealms is an enterprise-grade, modular RPG and industrial progression SkyBlock server network built from the ground up for Paper 1.21.1. It replaces traditional monolithic plugin design with a **clean micro-kernel / plugin DAG architecture**, where services are decoupled, dependency-injected, and coordinated via a topological dependency graph (Kahn's algorithm) with reverse-order graceful shutdowns.

### High-Level Topology
```
Minecraft Paper 1.21.1 Runtime (Java 21 LTS)
  ├── Third-Party Infrastructure:
  │     ├── FastAsyncWorldEdit (FAWE 2.16.2) — High-throughput async schematic & world edits
  │     ├── Vault (1.7.3) — Universal economy bridging
  │     ├── LuckPerms (5.5.71) — Authoritative permissions
  │     ├── EssentialsX Suite (2.22.0) — Server utility, warps, and administration
  │     ├── ViaVersion / ViaBackwards / ViaRewind — Cross-version protocol translation
  │     ├── GUIPlus (3.9.0) & WarpSystem (5.1.14) — Menu and warp fallbacks
  │
  └── Custom SkyRealms Platform Architecture:
        ├── platform:api                 -> Core interfaces, module contracts, event bus, service registry
        ├── platform:common              -> DAG dependency resolver, event bus implementation, lifecycle
        ├── platform:database-api        -> Database pool abstraction, transaction management, connection pooling
        ├── platform:integrations-api    -> Provider contracts for Economy and Permissions
        ├── platform:testing             -> JUnit 5 test harness and mock contexts
        │
        ├── plugins:SkyRealmsCore        -> DAG module host, Bukkit ServerLoadEvent boot coordinator, /skyrealms
        ├── plugins:SkyRealmsDatabase    -> HikariCP pool, PostgreSQL driver, H2 fallback, Flyway V1/V2/V3/V4
        ├── plugins:SkyRealmsPlayer      -> Authoritative player profiles, async pre-login warming, /profile
        ├── plugins:SkyRealmsEconomy     -> Multi-currency ledger, idempotency deduplication, Vault bridge, /balance, /pay, /eco
        ├── plugins:SkyRealmsIslands     -> Void generator, O(1) spiral allocator, protection, GUI, /is, lobby & holograms
        └── plugins:SkyRealmsFarming     -> Physical farming generators, crop mutations, progression & collections, /farm
```

---

## 2. Platform Modules (`platform:*`)

### `platform:api`
* [`SkyModule`](file:///C:/servermc/skyrealms/platform/api/src/main/java/com/skyrealms/api/module/SkyModule.java): The foundational lifecycle interface (`onLoad`, `onEnable`, `onDisable`). Defines module identity (`id`, `name`, `version`, `dependencies`).
* [`ModuleMetadata`](file:///C:/servermc/skyrealms/platform/api/src/main/java/com/skyrealms/api/module/ModuleMetadata.java): Immutable descriptor with semantic versioning and topological dependency declarations.
* [`ServiceRegistry`](file:///C:/servermc/skyrealms/platform/api/src/main/java/com/skyrealms/api/service/ServiceRegistry.java): Thread-safe DI service locator enabling loose coupling across modules without direct class coupling.
* [`EventBus`](file:///C:/servermc/skyrealms/platform/api/src/main/java/com/skyrealms/api/event/EventBus.java): Asynchronous publish-subscribe messaging pipeline supporting strongly-typed versioned events.

### `platform:common`
* [`DirectedGraphDependencyResolver`](file:///C:/servermc/skyrealms/platform/common/src/main/java/com/skyrealms/common/dependency/DirectedGraphDependencyResolver.java): Implements Kahn's topological sort algorithm to determine the exact execution order of modules and detects circular dependency deadlocks at compile/startup time.
* [`ConcurrentServiceRegistry`](file:///C:/servermc/skyrealms/platform/common/src/main/java/com/skyrealms/common/service/ConcurrentServiceRegistry.java): Lock-free, concurrent registry supporting typed interface resolution and runtime unregistration.
* [`DefaultEventBus`](file:///C:/servermc/skyrealms/platform/common/src/main/java/com/skyrealms/common/event/DefaultEventBus.java): High-concurrency event dispatcher executing subscriber callbacks asynchronously with priority ordering.
* [`DefaultModuleLifecycleManager`](file:///C:/servermc/skyrealms/platform/common/src/main/java/com/skyrealms/common/lifecycle/DefaultModuleLifecycleManager.java): Drives modules through `UNLOADED -> LOADED -> INITIALIZING -> ENABLED -> DISABLED` states.

---

## 3. Implemented Plugins & Modules

### A. `SkyRealmsCore` (`plugins:SkyRealmsCore`)
* **Role:** Central Paper plugin orchestrator.
* **Boot Synchronization:** Defers module initialization until Bukkit's `ServerLoadEvent` fires, ensuring all third-party plugins (Vault, LuckPerms, FAWE) are completely loaded before SkyRealms modules start.
* **Shutdown:** Orchestrates clean, reverse-dependency shutdown across all registered modules upon server stop.
* **Commands:** `/skyrealms status`, `/skyrealms modules`, `/skyrealms reload`.

### B. `SkyRealmsDatabase` (`plugins:SkyRealmsDatabase`)
* **Role:** Authoritative persistence and schema management engine.
* **Connection Pooling:** HikariCP with tuned production parameters.
* **Database Compatibility:** Primary target is PostgreSQL 16+ with embedded H2 fallback for isolated offline testing or development environments.
* **Automated Migration Engine:** Embedded Flyway migration runner executes migrations V1, V2, V3, and V4 automatically.
* **ServiceLoader SPI Merging (ADR-0004):** Shaded JAR merges `META-INF/services/` entries so PostgreSQL and H2 drivers load correctly within Paper's isolated plugin classloaders.

### C. `SkyRealmsPlayer` (`plugins:SkyRealmsPlayer`)
* **Role:** Player profile, settings, stats, and session tracking.
* **Caching & Preloading:** Uses Paper's `AsyncPlayerPreLoginEvent` to preload player data from PostgreSQL into memory asynchronously before the player spawns.
* **Persistence:** Flushes profiles, stats, and session records asynchronously on `PlayerQuitEvent` and server shutdown.
* **Commands:** `/profile [player]`.

### D. `SkyRealmsEconomy` (`plugins:SkyRealmsEconomy`)
* **Role:** Authoritative double-entry general ledger economy.
* **Currencies:** `COINS` (primary decimal currency) and `GEMS` (premium integer currency).
* **Double-Entry Ledger Architecture:** Every balance change creates an atomic `sr_ledger_transactions` journal record and balanced `sr_ledger_entries`.
* **Idempotency Guarantee:** Deduplication engine uses unique idempotency keys to ensure network retries or command double-clicks never double-charge players.
* **Vault Integration:** Registers [`VaultEconomyBridge`](file:///C:/servermc/skyrealms/plugins/SkyRealmsEconomy/src/main/java/com/skyrealms/economy/vault/VaultEconomyBridge.java) at `ServicePriority.Highest`.
* **Commands:** `/balance`, `/pay`, `/eco`.

### E. `SkyRealmsIslands` (`plugins:SkyRealmsIslands`)
* **Role:** Custom void world generation, spatial spiral island allocation, centralized boundary protection, role hierarchy (`LEADER`, `OFFICER`, `MEMBER`, `VISITOR`), island upgrades, and interactive Cherry GUI.
* **Commands:** `/island` (`/is`), `/spawn`, `/lobby`, `/hub`.

### F. `SkyRealmsFarming` (`plugins:SkyRealmsFarming`)
* **Role:** mcMMO-integrated SkyRealms Garden engine (`skyrealms_garden`), $768 \times 768$ deterministic spatial region allocator (1024 spacing), Sakura Glass Garden conservatory domes, 16 crop plots ($4 \times 4$ chunks each), centralized `HarvestRewardPipeline`, 4 Garden-only abilities (`Bountiful Harvest`, `Verdant Aura`, `Sakura Harvest Surge`, `Golden Sprout`), mcMMO-driven 10-crop Crop Mastery, Botanical Journal, and complete retirement of legacy generators, Natural Fast-Harvest, and Elarion/quests.
* **Commands:** `/garden` (`/garden home`, `/garden leave`, `/garden mastery`, `/garden abilities`, `/garden journal`, `/garden info`, `/garden plots`, `/garden admin`), `/is farm`, `/farm stats`, `/farm help`.

---

## 4. SkyRealms Garden Engine Architecture (Milestones G1 & G2)

### A. Legacy Farming Generator Retirement & Data Preservation
* **Retirement Policy:**
  * All automatic generator crop spawning loops (`FarmingGeneratorProcessor`) and auto-terraforming sequences (`FarmingTerraformService`) have been retired.
  * Attempting to place a custom Farming Core item (`FarmingCoreItemFactory.isFarmingCore`) is intercepted and cancelled with a helpful notification directing players to `/garden home` or `/is farm`.
  * Right-clicking or breaking generator nodes has been disconnected from generator mechanics; crops in gardens grow and harvest naturally according to vanilla rules.
  * **Zero Data Loss Guarantee:** Legacy tables (`sr_farming_generators`, `sr_farming_generator_upgrades`, `sr_farming_player_progress`, `sr_farming_crop_collections`, `sr_farming_mutation_discoveries`) remain intact in PostgreSQL / H2 for auditability, and player Farming XP, levels, and collections carry forward seamlessly into the new Garden system.

### B. Shared Garden World (`skyrealms_garden`) & Spatial Region Allocation
* **Dedicated World Architecture:** Single shared Paper world (`skyrealms_garden`) using `VoidChunkGenerator` and `VoidBiomeProvider(Biome.PLAINS)`. World rules locked at noon (6000 ticks), daylight cycle disabled, clear weather, and mob spawning disabled.
* **Deterministic Spiral Allocator (`GardenSpatialAllocator`):**
  * Spacing: 1024 blocks center-to-center.
  * Region Size: $768 \times 768$ blocks ($[-384, +384]$ from anchor), with 256-block void gap buffer between adjacent gardens.
  * $O(1)$ coordinate bijection mapping sequential island indexes to unique spiral coordinates and reversing block coordinates to nearest grid anchors.
  * `DefaultGardenService.normalizeRegionSize` automatically upgrades any legacy `256`-sized database rows to `768`.

### C. Greenhouse Dome Procedural Generation (`GardenDomeGenerator`)
* **Conservatory Architecture:**
  * Radius: 34 blocks with vaulted tinted glass canopy and dark oak framing ribs reaching up to 23 blocks high.
  * Perimeter stone brick base walls and decorative landscaping (grass, moss, flowering azalea).
  * Central Grand Boulevard (East-West): 7-block wide polished andesite and smooth stone thoroughfare with recessed sea lanterns for warm, ambient lighting.
  * Entrance Plaza (West end): Arrival spawn dais, lanterns, dark oak portico facing East down the boulevard.
  * Garden Keeper Shrine (East end): Raised quartz dais with four quartz pillars, sea lantern beacons, and central lectern altar for Elarion and divine offerings.
  * 4 Specialized Corner Alcoves: Research Library (NW), Equipment Workshop (SW), Botanical Showcase Dais (NE), Divine Altar (SE).
  * **16 Crop Plots (2 Rows $\times$ 8 Columns):**
    * North Row (Plots 1–8) at $Z = -8$; South Row (Plots 9–16) at $Z = +8$.
    * Columns 1–8 spaced along $X$ at $\{-24, -17, -10, -3, 4, 11, 18, 25\}$.
    * Each plot features a $7 \times 7$ footprint with polished andesite borders, facing entrance opening, and internal $5 \times 5$ fertile farmland with sub-surface water irrigation.
    * Plot 1 (starter plot) generates pre-planted with mature wheat for immediate first harvest onboarding; Plots 2–16 unlock progressively.

### D. mcMMO Farming Integration, Reward Pipeline, Garden Abilities & Botanical Journal
* **Authoritative mcMMO Bridge (`McMMOFarmingService` & `McMMOIntegrationListener`):**
  * mcMMO `HERBALISM` (rebranded as **Farming**) is the single authoritative overall Farming XP and level source.
  * All 18 non-Herbalism mcMMO skills, all built-in mcMMO active SuperAbilities/passive SubSkills, and all 18 non-Farming skill commands are blocked at runtime.
  * Herbalism XP is strictly restricted to authorized, unlocked Garden regions inside `skyrealms_garden`.
* **Centralized Harvest Reward Pipeline (`HarvestRewardPipeline` & `GardenBalanceConfig`):**
  * Normal left-click mature crop breaks and Sakura Harvest Surge 3×3 AoE breaks route through one calculation & commit pipeline with per-block harvest locks and idempotent `harvestId` tracking.
  * Unified bonus drop chance (`Bountiful Harvest + Crop Mastery`, clamped to `75%` and max 3 bonus drops), unified mcMMO XP multiplier (clamped to `2.0x`), single collection increment (`sr_farming_crop_collections`), and single Golden Sprout payout with per-harvest (`250`) and daily (`15,000`) caps.
* **Custom Garden Abilities (`GardenAbilityService`, `VerdantAuraManager`, `GardenAbilitiesGui`):**
  * `Bountiful Harvest` (Lv.1+: 5%–50%), `Verdant Aura` (Lv.10+ Haste I, Lv.25+ Speed I, Lv.50+ Haste II; removed on Garden/world exit), `Sakura Harvest Surge` (Lv.15+: Sneak + Right-Click Hoe, 3×3 AoE, persistent cooldowns), `Golden Sprout` (Lv.30+: 1%–15% Coins).
* **mcMMO-Driven Crop Mastery & Botanical Journal (`CropMastery`, `DefaultBotanicalJournalService`, `BotanicalJournalGui`):**
  * 10 crops × 7 Tiers gated by both mcMMO Farming level (`1, 5, 10, 20, 35, 50, 75`) and crop harvest counts (`25`–`7,500`), with distinct crop identities, discoveries, cosmetics, and equipment hooks.
  * Botanical Journal (`sr_botanical_journal`) connects lifetime collections, mutation discoveries, harvest streaks, best single harvest, and 8 permanent botanical achievements.

### E. Region Protection & Boundary Containment (`GardenProtectionListener`)
* **Boundary Enforcement:** Players cannot walk, fly, ender pearl, or chorus-fruit outside their $256 \times 256$ garden region. Attempting to cross pushes the player gently back towards the center with warning sound and message.
* **Anti-Grief Architecture:** Conservatory glass, structural framing, pathways, and shrine blocks cannot be broken by regular island members.
* **Plot Permissions:** Breaking or planting in locked plots is blocked with an action bar prompt directing the player to `/garden plots`.
* **Farmland Shield:** Trampling farmland by players or mobs is cancelled, and farmland moisture loss is prevented.

### F. Navigation & Warmup Integration
* **Commands:**
  * `/garden home` (or `/is farm`): Teleports to Garden entrance dais with 3-second particle warmup aura.
  * `/garden leave`: Teleports back to Island Home with 3-second warmup aura.
  * `/garden info`: Displays island name, region index, center coordinates, and unlocked plots.
  * `/garden plots`: Displays interactive ASCII overview of all 16 plots across both rows.
  * `/garden admin tp <player>` & `/garden admin unlockplot <player> <1-16>`: Administrative management.
  * Accessible directly from the 54-slot `/farm` GUI or by sneak-breaking the core.
  * Evolving upgrades crops in-place to higher tiers when meeting level and coin requirements.
  * Dismantling refunds the custom core item with all upgrades preserved.

### D. Harvest & Automatic Regeneration Loop
* **Maturity Check**: Only fully mature crops can be harvested (`Ageable.getAge() == Ageable.getMaximumAge()`).
* **Concurrency Lock**: Atomic node lock ensures a crop position cannot be harvested multiple times within the same tick or during regeneration.
* **Drop Delivery & Progression**:
  * Delivers crop items directly to inventory (or drops naturally if full).
  * Awards Farming XP and displays progress bar in action bar.
  * Updates crop collection milestones.
* **Regeneration Scheduling**:
  * Sets block to young seedling (`Ageable.setAge(0)`).
  * Schedules regeneration timer:
    $$\text{Delay} = \max(5, 30 - (\text{Speed Level} \times 2)) \text{ ticks}$$
  * When timer elapses, grows back to mature state with green particles and soft audio chime.

### C. Crop Mutation Framework
Harvested crops have a chance to mutate into rare variants with custom Adventure RGB lore, glint, and PDC tags:
* `GOLDEN`: +200% Sell Value at market.
* `GIANT`: +200% Farming XP.
* `ANCIENT`: Rare relic material for research and high-tier crafting.
* `FERTILE`: +100% Crop Drop Quantity.
* `MAGICAL`: Rare glowing mystic crafting ingredient.

### D. Generator Upgrades & Economy
Upgrades purchased atomically using `EconomyService.withdraw(...)`:
* `SPEED` (Levels 1–10): Reduces regeneration delay down to 10 ticks.
* `YIELD` (Levels 1–5): +15% chance per level for double drops.
* `MUTATION` (Levels 1–5): +0.5% bonus mutation chance per level.
* `XP_BOOST` (Levels 1–5): +20% Farming XP multiplier per level.
* `CAPACITY` (Levels 1–3): Expands maximum supported crop nodes.

### E. Anti-Exploit Shield
* Cancels vanilla drop items to prevent duplication.
* Prevents water flushing, piston pushing, or explosions from generating drops or XP.
* Enforces island boundary checks and visitor permissions.

---

## 5. Database Schema & Flyway Migrations

### Migrations List
- `V1__create_player_tables.sql`: `sr_players`, `sr_player_settings`, `sr_player_stats`, `sr_player_sessions`
- `V2__create_economy_tables.sql`: `sr_currencies`, `sr_accounts`, `sr_ledger_transactions`, `sr_ledger_entries`, `sr_admin_audit_log`
- `V3__create_island_tables.sql`: `sr_islands`, `sr_island_members`, `sr_island_upgrades`, `sr_island_settings`, `sr_island_visits`
- `V4__create_farming_tables.sql`: `sr_farming_generators`, `sr_farming_generator_upgrades`, `sr_farming_player_progress`, `sr_farming_crop_collections`, `sr_farming_mutation_discoveries`
- `V5__create_garden_tables.sql`: `sr_gardens`, `sr_garden_plots`, `sr_garden_unlocks`, `sr_crop_mastery`, `sr_player_research`
- `V6__create_garden_quests_and_milestones.sql`: `sr_garden_offerings`, `sr_farming_quests`, `sr_crop_mastery.claimed_milestones`, `sr_player_research.elarion_intro_completed`
- `V7__create_botanical_journal.sql`: `sr_botanical_journal` (highest harvest streak, best single harvest, achievements, discovered varieties)

### Migration V4 Schema
```sql
CREATE TABLE IF NOT EXISTS sr_farming_generators (
    generator_id UUID PRIMARY KEY,
    island_id UUID NOT NULL REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    owner_uuid UUID NOT NULL REFERENCES sr_players(id) ON DELETE CASCADE,
    generator_tier VARCHAR(32) NOT NULL DEFAULT 'TIER_1_WHEAT',
    world_name VARCHAR(64) NOT NULL,
    core_x INTEGER NOT NULL,
    core_y INTEGER NOT NULL,
    core_z INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    total_harvests BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_farming_gen_core UNIQUE (world_name, core_x, core_y, core_z)
);

CREATE TABLE IF NOT EXISTS sr_farming_generator_upgrades (
    generator_id UUID NOT NULL REFERENCES sr_farming_generators(generator_id) ON DELETE CASCADE,
    upgrade_key VARCHAR(64) NOT NULL,
    upgrade_level INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (generator_id, upgrade_key)
);

CREATE TABLE IF NOT EXISTS sr_farming_player_progress (
    player_uuid UUID PRIMARY KEY REFERENCES sr_players(id) ON DELETE CASCADE,
    farming_xp BIGINT NOT NULL DEFAULT 0,
    farming_level INTEGER NOT NULL DEFAULT 1,
    total_harvests BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sr_farming_crop_collections (
    player_uuid UUID NOT NULL REFERENCES sr_players(id) ON DELETE CASCADE,
    crop_type VARCHAR(32) NOT NULL,
    amount_collected BIGINT NOT NULL DEFAULT 0,
    tier_unlocked INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, crop_type)
);

CREATE TABLE IF NOT EXISTS sr_farming_mutation_discoveries (
    discovery_id UUID PRIMARY KEY,
    player_uuid UUID NOT NULL REFERENCES sr_players(id) ON DELETE CASCADE,
    mutation_type VARCHAR(32) NOT NULL,
    crop_type VARCHAR(32) NOT NULL,
    discovered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    count BIGINT NOT NULL DEFAULT 1
);
```

---

## 6. Master Command & Permission Registry

| Command | Aliases | Description | Permission |
| :--- | :--- | :--- | :--- |
| `/skyrealms status` | `/sr status` | View system health, RAM, module DAG, and database state | `skyrealms.admin` |
| `/skyrealms modules` | `/sr modules` | View loaded modules in topological order | `skyrealms.admin` |
| `/skyrealms reload` | `/sr reload` | Reload configuration files | `skyrealms.admin` |
| `/profile [player]` | `/p` | View player profile, playtime, stats, and settings | `skyrealms.player.profile` |
| `/balance [player]` | `/bal`, `/money` | View personal or target player Coin/Gem balance | `skyrealms.economy.balance` |
| `/pay <player> <amount>` | | Send Coins securely via double-entry ledger | `skyrealms.economy.pay` |
| `/eco <give\|take\|set>` | | Administrative balance modification with audit logging | `skyrealms.economy.admin` |
| `/island` | `/is` | Open 54-slot Cherry Island Management GUI | `skyrealms.island.base` |
| `/island create` | `/is create` | Generate a new Cherry Blossom void island | `skyrealms.island.base` |
| `/island home` | `/is home` | Teleport to island home with 3s warmup | `skyrealms.island.base` |
| `/island invite <player>` | `/is invite` | Invite player to co-op island | `skyrealms.island.invite` |
| `/island accept <player>` | `/is accept` | Accept co-op invitation | `skyrealms.island.base` |
| `/island kick <player>` | `/is kick` | Kick member from island | `skyrealms.island.kick` |
| `/island leave` | `/is leave` | Leave current co-op island | `skyrealms.island.base` |
| `/island visit <player>` | `/is visit` | Visit public player island with 3s warmup | `skyrealms.island.visit` |
| `/island settings` | `/is settings` | Configure privacy and visitor permissions | `skyrealms.island.settings` |
| `/island upgrades` | `/is upgrades` | Purchase size and member capacity expansions | `skyrealms.island.upgrades` |
| `/island name <name>` | `/is name` | Rename island display name | `skyrealms.island.name` |
| `/island info` | `/is info` | View island level, bounds, and member count | `skyrealms.island.base` |
| `/island members` | `/is members` | List all members and their roles | `skyrealms.island.base` |
| `/island top` | `/is top` | View top island leaderboards | `skyrealms.island.base` |
| `/island reset` | `/is reset` | Clear island blocks and respawn fresh starter | `skyrealms.island.reset` |
| `/island delete` | `/is delete` | Permanently delete island and warp members to spawn | `skyrealms.island.delete` |
| `/farm` | `/farming` | Open 54-slot Cherry Farming Menu | `skyrealms.farming.base` |
| `/farm help` | | Display farming command guide (supports console) | `skyrealms.farming.base` |
| `/farm core [tier]` | | Obtain custom Farming Generator Core item with PDC metadata | `skyrealms.farming.base` |
| `/farm stats [player]` | | View farming level, XP progress, and total harvests (supports console) | `skyrealms.farming.base` |
| `/farm generators` | | List active generators on player's island | `skyrealms.farming.base` |
| `/farm generator info` | | Inspect generator details at targeted block | `skyrealms.farming.base` |
| `/farm generator upgrade` | | Open upgrade purchase GUI for targeted generator | `skyrealms.farming.base` |
| `/farm generator remove` | | Deactivate and refund targeted generator with upgrades preserved | `skyrealms.farming.base` |
| `/farm admin givecore <p> [t]` | | Admin command to grant generator cores to players (console supported) | `skyrealms.farming.admin` |
| `/spawn` | `/lobby`, `/hub` | Teleport to Dragon Nest Central Dais with 3s warmup | None (Default) |
| `/spawn paste` | | Re-paste schematic and regenerate lobby interior | `skyrealms.lobby.admin` |
| `/warp <location>` | | Teleport to castle stations (`spawn`, `castle`, `bank`, etc.) | Essentials default |

---

## 7. Automated Test Suite (97 / 97 Passing)

Run via: `cd C:\servermc\skyrealms; .\gradlew test`
* **`PlotCustomizationAndBedTest` & `PlotRepositoryTest` (7 tests):** CropBed interior bounds/overlap/radius validation, all preset layouts bounded inside plot active zone, Rich Soil/Specialized Bed integration through `HarvestRewardPipeline`, `SavedPlotLayout` serialization, planting material mapping, and Flyway V8 (`sr_garden_plots` extensions, `sr_garden_plot_beds`, `sr_garden_saved_layouts`) round-trip persistence.
* **`HarvestRewardPipelineTest` (4 tests):** Primary vs AoE XP deduplication, combined bonus drop cap, Golden Sprout per-harvest & daily caps, per-block claim locking.
* **`CropMasteryAndAbilityTest` (3 tests):** Dual mcMMO level + harvest gates, all 10 crop identities/discoveries/cosmetics/equipment hooks, probability scaling and cooldown persistence across relog.
* **`BotanicalJournalRepositoryTest` (1 test):** V7 `sr_botanical_journal` persistence, updates, and `sr_farming_mutation_discoveries` integration.
* **`GardenPlotTest` & `GardenSpatialAllocatorTest` (11 tests):** 4×4 chunk plot bounds, non-overlapping 2×8 grid, 5,000-region spiral bijection & nearest-anchor reversal.
* **`SpatialGridAllocatorTest`, `IslandModelTest`, `IslandRepositoryTest` (9 tests):** Island spatial allocation, 4-tier role permissions, island persistence.
* **`FarmingRepositoryTest`, `GardenOfferingAndQuestRepositoryTest`, `FarmingProgressionTest`, `FarmingUpgradeTest`, `CropMutationEngineTest`, `LegacyGeneratorRetirementTest` (21 tests):** Garden & offering repository persistence, legacy retirement checks, progression & mutation math.
* **Platform, Database, Player & Economy Suites (41 tests):** Topological DAG sort & cycle detection, service registry, event bus, HikariCP, Flyway V1–V8 migration validation, double-entry ledger, idempotency, Vault bridge.

---

## 8. Next Milestones on the Roadmap

* **Milestone 5 — Custom Cobblestone Generators, Machines & Minions:**
  * Island cobblestone generator tiering (ore generation probabilities based on island level).
  * Minion / Automation Engine for farming, mining, and mob collection.
  * Machine network and energy/fluid conduits.
* **Milestone 6 — Industrial Agriculture & Custom Processing:**
  * Custom crops, greenhouse biomes, and growth rate boosters.
  * Automated harvesters, sorting pipes, and industrial processors.
* **Milestone 7 — Dungeons, Bosses & Combat Engine:**
  * Custom instanced dungeons, mob scaling, boss arenas, and rare loot tables.
