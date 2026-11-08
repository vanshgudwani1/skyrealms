# 🌌 SkyRealms — Master Memory File & Architecture Blueprint

> **Authoritative Technical Specification & State Archive**  
> **Server Target:** Minecraft Paper 1.21.1 (Build 133) on Java 21 LTS  
> **Repository Root:** `C:\servermc\skyrealms`  
> **Runtime Server Root:** `C:\servermc`  
> **Last Updated:** 2026-11-08  
> **Status:** Milestones 0, 1, 2, 3 + Dragon Nest Void Lobby Overhaul COMPLETED & VERIFIED (47/47 Tests Passing, 20.0 TPS)

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
        ├── plugins:SkyRealmsDatabase    -> HikariCP pool, PostgreSQL driver, H2 fallback, Flyway V1/V2/V3
        ├── plugins:SkyRealmsPlayer      -> Authoritative player profiles, async pre-login warming, /profile
        ├── plugins:SkyRealmsEconomy     -> Multi-currency ledger, idempotency deduplication, Vault bridge, /balance, /pay, /eco
        └── plugins:SkyRealmsIslands     -> Void generator, O(1) spiral allocator, protection, GUI, /is, lobby & holograms
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
* [`DefaultModuleLifecycleManager`](file:///C:/servermc/skyrealms/platform/common/src/main/java/com/skyrealms/common/lifecycle/DefaultModuleLifecycleManager.java): Drives modules through `UNLOADED -> LOADED -> ENABLING -> ENABLED -> DISABLING -> DISABLED` states.

### `platform:database-api`
* [`DatabaseService`](file:///C:/servermc/skyrealms/platform/database-api/src/main/java/com/skyrealms/database/api/DatabaseService.java): Contract providing connection retrieval, query execution, and atomic transactions.
* [`TransactionCallback`](file:///C:/servermc/skyrealms/platform/database-api/src/main/java/com/skyrealms/database/api/TransactionCallback.java): Functional interface for executing multi-statement ACID transactions with automatic rollback on failure.

### `platform:integrations-api`
* [`EconomyProvider`](file:///C:/servermc/skyrealms/platform/integrations-api/src/main/java/com/skyrealms/integrations/economy/EconomyProvider.java): Clean abstraction for currency transactions, balance lookups, and transfers.
* [`PermissionsProvider`](file:///C:/servermc/skyrealms/platform/integrations-api/src/main/java/com/skyrealms/integrations/permissions/PermissionsProvider.java): Abstraction for permission queries, group resolution, and rank checks.

### `platform:testing`
* In-memory test context, `DummyTestModule`, and JUnit 5 harnesses allowing 100% of platform logic to be tested without running a live Minecraft server instance.

---

## 3. Implemented Plugins & Modules

### A. `SkyRealmsCore` (`plugins:SkyRealmsCore`)
* **Role:** Central Paper plugin orchestrator.
* **Boot Synchronization:** Defers module initialization until Bukkit's `ServerLoadEvent` fires, ensuring all third-party plugins (Vault, LuckPerms, FAWE) are completely loaded before SkyRealms modules start.
* **Shutdown:** Orchestrates clean, reverse-dependency shutdown across all registered modules upon server stop.
* **Commands:**
  * `/skyrealms status` — Displays runtime state, memory consumption, active modules, and database health.
  * `/skyrealms modules` — Lists loaded modules in topological order.
  * `/skyrealms reload` — Safely reloads module configurations.

### B. `SkyRealmsDatabase` (`plugins:SkyRealmsDatabase`)
* **Role:** Authoritative persistence and schema management engine.
* **Connection Pooling:** HikariCP with tuned production parameters (max pool size: 10, min idle: 2, connection timeout: 10,000ms, idle timeout: 600,000ms, max lifetime: 1,800,000ms).
* **Database Compatibility:** Primary target is PostgreSQL 16+. Includes automatic fallback to embedded H2 for isolated offline testing or development environments.
* **Automated Migration Engine:** Embedded Flyway migration runner executes migrations at module enable time.
* **ServiceLoader SPI Merging (ADR-0004):** Shaded JAR merges `META-INF/services/` entries so PostgreSQL and H2 drivers load correctly within Paper's isolated plugin classloaders.

### C. `SkyRealmsPlayer` (`plugins:SkyRealmsPlayer`)
* **Role:** Player profile, settings, stats, and session tracking.
* **Models:**
  * `PlayerProfile`: UUID, username, first joined timestamp, last seen timestamp, active state.
  * `PlayerSettings`: Language, chat preferences, scoreboard toggles, privacy visibility.
  * `PlayerStats`: Playtime, blocks broken, blocks placed, deaths, kills, island level cache.
  * `PlayerSession`: Session start, IP address, client version, session duration.
* **Caching & Preloading:** Uses Paper's `AsyncPlayerPreLoginEvent` to preload player data from PostgreSQL into memory asynchronously before the player spawns.
* **Persistence:** Flushes profiles, stats, and session records asynchronously on `PlayerQuitEvent` and server shutdown.
* **Commands:**
  * `/profile [player]` — Displays player statistics, rank, join date, playtime, and settings.

### D. `SkyRealmsEconomy` (`plugins:SkyRealmsEconomy`)
* **Role:** Authoritative double-entry general ledger economy.
* **Currencies:**
  * `COINS` (Primary economy currency, decimal 18,2 precision).
  * `GEMS` (Premium currency, integer precision).
* **Double-Entry Ledger Architecture:** Every balance change creates an atomic `sr_ledger_transactions` journal record and two or more `sr_ledger_entries` (credit/debit), ensuring zero currency duplication or desync.
* **Idempotency Guarantee:** Deduplication engine uses unique idempotency keys to ensure network retries or command double-clicks never double-charge players.
* **Vault Integration:** Registers [`VaultEconomyBridge`](file:///C:/servermc/skyrealms/plugins/SkyRealmsEconomy/src/main/java/com/skyrealms/economy/vault/VaultEconomyBridge.java) into Bukkit's `ServicesManager` at `ServicePriority.Highest`.
* **Commands:**
  * `/balance` (`/bal`, `/money`) — View player's Coin and Gem balances.
  * `/pay <player> <amount>` — Transfer Coins with validation, limits, and double-entry ledger logging.
  * `/eco <give|take|set> <player> <amount> [currency]` — Administrator balance adjustments with audit log tracking.

---

## 4. Milestone 3: Custom Island Management Engine (`SkyRealmsIslands`)

### A. Dedicated Void World Dimension
* **Dimension Name:** `skyrealms_islands`
* **Generator:** [`VoidChunkGenerator`](file:///C:/servermc/skyrealms/plugins/SkyRealmsIslands/src/main/java/com/skyrealms/islands/world/VoidChunkGenerator.java) generates 100% empty chunks with no vanilla terrain noise or structures.
* **Biome Provider:** [`VoidBiomeProvider`](file:///C:/servermc/skyrealms/plugins/SkyRealmsIslands/src/main/java/com/skyrealms/islands/world/VoidBiomeProvider.java) assigns `Biome.CHERRY_GROVE` to all coordinates, giving every player's island floating pink cherry petals.

### B. Bijective $O(1)$ Spatial Spiral Allocator
* [`SpatialGridAllocator`](file:///C:/servermc/skyrealms/plugins/SkyRealmsIslands/src/main/java/com/skyrealms/islands/world/SpatialGridAllocator.java) maps sequential island IDs ($0, 1, 2, \dots$) to a 2D square spiral on a regular grid:
  $$\text{Grid Anchor: } (X \cdot \text{SPACING}, Z \cdot \text{SPACING}) \quad \text{where } \text{SPACING} = 1000 \text{ blocks}$$
* **Mathematical Properties:**
  * **Bijective & Collision-Free:** Every index maps to a unique $(X, Z)$ coordinate; two islands can never occupy the same grid cell.
  * **$O(1)$ Spatial Hash Lookup:** Any world location $(X, Z)$ resolves to its owning grid anchor via:
    $$\text{gridX} = \lfloor (X + \text{HALF\_SPACING}) / \text{SPACING} \rfloor, \quad \text{gridZ} = \lfloor (Z + \text{HALF\_SPACING}) / \text{SPACING} \rfloor$$
    Combined into a 64-bit spatial hash key: `(gridX << 32) ^ (gridZ & 0xFFFFFFFFL)`.

### C. Cherry Blossom Starter Island Platform
* [`StarterIslandGenerator`](file:///C:/servermc/skyrealms/plugins/SkyRealmsIslands/src/main/java/com/skyrealms/islands/world/StarterIslandGenerator.java) programmatically creates an island centered at $(X, 72, Z)$:
  * **Bedrock Anchor:** Single bedrock block at $Y=68$ for coordinate reference.
  * **Platform Layers:** 3-block deep inverted cone of Dirt with a vibrant surface of Grass blocks and Pink Petals.
  * **Cherry Tree:** 5-block tall Cherry Log trunk with a hand-placed spherical canopy of `CHERRY_LEAVES` ($R=2.6$).
  * **Starter Chest:** Facing south, pre-populated with: Ice $\times 2$, Lava Bucket $\times 1$, Sugar Cane $\times 1$, Cactus $\times 1$, Melon Slice $\times 1$, Pumpkin Seeds $\times 1$, Bone Meal $\times 4$, Bread $\times 16$, Oak Sapling $\times 2$, Cherry Sapling $\times 2$.
  * **Safe Spawn:** Clear air column at $(X + 0.5, 73.0, Z + 2.5)$.

### D. Security, Roles & Boundary Protection Engine
* **Role Hierarchy:**
  * `LEADER`: Full ownership, member invites/kicks, promotions/demotions, settings, upgrades, reset, delete.
  * `OFFICER`: Invite members, toggle permitted settings, build, interact.
  * `MEMBER`: Build, break, open permitted containers, use island amenities.
  * `VISITOR`: Visit island, interact only with explicitly unlocked containers or doors.
* **Centralized Protection Service:** [`IslandProtectionService`](file:///C:/servermc/skyrealms/plugins/SkyRealmsIslands/src/main/java/com/skyrealms/islands/protection/IslandProtectionService.java):
  * Intercepts `BlockBreakEvent`, `BlockPlaceEvent`, `PlayerInteractEvent` (doors, trapdoors, buttons, levers), `InventoryOpenEvent` (chests, hoppers, barrels).
  * Intercepts `EntityDamageByEntityEvent` (protects passive mobs, armor stands, item frames, paintings).
  * Blocks environmental bypasses: `BlockPistonExtendEvent`, `BlockPistonRetractEvent`, `BlockFromToEvent` (fluid griefing), `BlockExplodeEvent`, `EntityExplodeEvent`, `BlockIgniteEvent`, `BlockBurnEvent`.
  * Allows staff bypass with `skyrealms.island.admin.bypass` permission.

### E. Island Upgrades & Economy Integration
* **Size Radius Upgrades:** Level 1: 100 blocks ($200 \times 200$) $\to$ Level 2: 150 blocks ($300 \times 300$, cost: 10,000 Coins) $\to$ Level 3: 200 blocks ($400 \times 400$, cost: 50,000 Coins).
* **Co-op Member Limits:** Level 1: 4 members $\to$ Level 2: 8 members (cost: 5,000 Coins) $\to$ Level 3: 12 members (cost: 25,000 Coins).
* **ACID Transactions:** Upgrade purchases atomically deduct funds from the player's account via `EconomyService` and persist the upgrade record; on any database failure, coins are automatically rolled back.

### F. Interactive GUI & Command Suite
* **54-Slot Cherry GUI:** [`IslandGui`](file:///C:/servermc/skyrealms/plugins/SkyRealmsIslands/src/main/java/com/skyrealms/islands/gui/IslandGui.java) provides an interactive chest interface with sound effects and animated lore for Island Info, Teleport Home, Members, Upgrades, Settings Toggles, and Island Reset.
* **Command Suite (`/island`, `/is`):**
  * `/is create` — Allocate coordinates, save to PostgreSQL, paste starter island, teleport player.
  * `/is home` — Teleport to island spawn with 3-second warmup and helix particle aura.
  * `/is invite <player>` / `/is accept <player>` — Secure co-op invitation handshake with 60-second expiration.
  * `/is kick <player>` / `/is leave` — Manage island roster.
  * `/is visit <player>` — Visit public islands with visit logging in `sr_island_visits`.
  * `/is settings` — Toggle visitor permissions, container access, and PvP.
  * `/is upgrades` — Browse and purchase size expansions and member slots.
  * `/is name <name>` — Set a custom island display name.
  * `/is top` — View highest-level islands.
  * `/is reset` — Clear island blocks, reset upgrades, respawn fresh starter platform.
  * `/is delete` — Kick all members, wipe island records, delete blocks, and teleport members safely to server spawn.

---

## 5. Celestial Dragon Nest Void Lobby & Castle Engine

### A. 100% Pure Void Cherry Grove Dimension
* **Configuration:** `server.properties` configured with native flat air generation:
  ```properties
  level-type=minecraft\:flat
  generator-settings={"biome"\:"minecraft\:cherry_grove","layers"\:[{"block"\:"minecraft\:air","height"\:1}],"structures"\:{"structures"\:{}}}
  generate-structures=false
  ```
* **Result:** Zero vanilla terrain noise, caves, oceans, or bedrock clipping. The floating dragon nest sits in an endless sky with falling pink cherry petals.
* **Physics & Break Shield:** [`LobbyListener`](file:///C:/servermc/skyrealms/plugins/SkyRealmsIslands/src/main/java/com/skyrealms/islands/lobby/LobbyListener.java) freezes physics in `world`, completely cancelling:
  * `BlockPhysicsEvent`, `LeavesDecayEvent`, `EntityChangeBlockEvent` (falling concrete powder/sand/gravel).
  * `BlockBurnEvent`, `BlockSpreadEvent`, `ItemSpawnEvent` (zero loose ground items).

### B. Schematic Sanitization & Palette Transformation
* **Source:** `dragonnest (1).schematic` ($590 \times 246 \times 607$ blocks).
* **Elimination of Falling Physics:** All 12,464 concrete powder blocks converted to solid Pink Concrete (`#251:6`). All 40 falling anvils converted to Iron blocks (`#42`).
* **Hazard Removal:** 27 fire blocks eliminated; 19 hostile entities stripped.
* **Dragon Scale Lighting Overhaul:** Replaced 53,813 blinding sea lanterns and prismarine blocks with velvety Pink (`#251:6`) and Magenta (`#251:2`) concrete scales.
* **Foliage & Timber Swap:** All oak/spruce wood and leaves transformed to Cherry Planks, Logs, Fences, and Cherry Leaves.

### C. Multi-Tiered Sunken Dais & 3D Orbital Core
* **Sunken Dais:** Outer rim of Polished Deepslate and chime-emitting Amethyst; sunken trench ($Y=69$) with an underlit radiant floor (hidden sea lanterns beneath Pink and Magenta stained glass); inner dais of Cherry planks, Gilded Blackstone, and Pink Petal mats.
* **Floating Celestial Core Star $(0, 73, 0)$:** Radiant Beacon core surrounded by pink/magenta stained glass with radiating End Rod spikes anchored over Lodestone and Crying Obsidian.
* **Live 3D Orbital Particle Engine (`CoreParticleTask`):**
  * Orbit 1: Horizontal ring of `CHERRY_LEAVES` ($R=4.2$).
  * Orbit 2: Tilted diagonal ring of `END_ROD` stars ($R=3.2$, $\text{tilt} = 45^\circ$).
  * Core cloud of `DRAGON_BREATH` stardust and periodic Amethyst chime sound resonances.

### D. 3-Second Teleport Warmup Engine (`TeleportWarmupService`)
* **3-Second (60-Tick) Warmup:** Integrated into `/spawn`, `/lobby`, `/hub`, `/is home`, `/is visit`, and GUI teleports.
* **Particle Aura:** Upward swirling double-helix of `CHERRY_LEAVES` and `END_ROD` particles around the player.
* **Audio-Visual Feedback:** Action bar countdown, title countdown, and pitch-rising note ticks (`BLOCK_NOTE_BLOCK_BELL`).
* **Movement Cancellation:** Moving more than 0.35 blocks cancels the teleport with an error sound and smoke puff.
* **Arrival Fanfare:** Plays `BLOCK_AMETHYST_BLOCK_CHIME` and `ENTITY_PLAYER_TELEPORT` upon arrival.
* **Cooldown:** 5-second anti-spam cooldown between teleport requests.

### E. Modern `TextDisplay` Hologram Engine (`HologramService`)
Paper 1.21.1 billboarding (`Billboard.CENTER`) `TextDisplay` entities with RGB colors, drop shadows, and dark alpha backing:
1. `[Spawn Dais]` $(0.5, 73.2, 0.5)$: **SKYREALMS — DRAGON NEST** welcome hologram.
2. `[Portal Gateway]` $(13.0, 73.2, 0.5)$: **SKYBLOCK REALMS** portal hologram.
3. `[Castle Gatehouse]` $(0.5, 80.5, 30.5)$: **IMPERIAL CITADEL** grand entrance.
4. `[Treasury / Bank]` $(-20.5, 79.8, 35.5)$: **ROYAL TREASURY & BANK** hologram.
5. `[Arcane Spire]` $(20.5, 79.8, 35.5)$: **ARCANE UPGRADES SPIRE** hologram.
6. `[Hall of Fame]` $(0.5, 84.8, 55.5)$: **CITADEL THRONE & LEADERBOARDS** hologram.
7. `[Royal Armory]` $(-45.5, 70.8, -25.5)$: **ROYAL ARMORY & FORGE** hologram.
8. `[Rewards Vault]` $(0.5, 73.2, 13.5)$: **CELESTIAL REWARDS** hologram.

### F. Castle Aspects & Fast-Travel Warps
* **Grand Sweeping Bridge:** Connects Spawn Dais $(0, 70, 8)$ directly across the chasm into Castle Gatehouse $(0, 78, 30)$.
* **Warps:**
  * `/warp spawn` — Central Dais
  * `/warp castle` — Castle Gatehouse entrance
  * `/warp bank` — Royal Treasury (Banker NPC with `/balance`)
  * `/warp upgrades` — Arcane Spire (Upgrades Master NPC)
  * `/warp halloffame` — Citadel Throne room (`/is top`)
  * `/warp forge` — Royal Armory & Forge

---

## 6. Database Schema & Flyway Migrations

All migrations live in `plugins/SkyRealmsDatabase/src/main/resources/db/migration/`:

### Migration V1: `V1__create_player_tables.sql`
```sql
CREATE TABLE IF NOT EXISTS sr_players (
    player_uuid UUID PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    first_joined TIMESTAMPTZ NOT NULL,
    last_seen TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS sr_player_settings (
    player_uuid UUID PRIMARY KEY REFERENCES sr_players(player_uuid) ON DELETE CASCADE,
    language VARCHAR(8) NOT NULL DEFAULT 'en_US',
    chat_enabled BOOLEAN NOT NULL DEFAULT true,
    scoreboard_enabled BOOLEAN NOT NULL DEFAULT true,
    teleport_requests_enabled BOOLEAN NOT NULL DEFAULT true,
    settings_json JSONB
);
CREATE TABLE IF NOT EXISTS sr_player_stats (
    player_uuid UUID PRIMARY KEY REFERENCES sr_players(player_uuid) ON DELETE CASCADE,
    playtime_seconds BIGINT NOT NULL DEFAULT 0,
    blocks_broken BIGINT NOT NULL DEFAULT 0,
    blocks_placed BIGINT NOT NULL DEFAULT 0,
    deaths INTEGER NOT NULL DEFAULT 0,
    kills INTEGER NOT NULL DEFAULT 0,
    island_level BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS sr_player_sessions (
    session_id UUID PRIMARY KEY,
    player_uuid UUID NOT NULL REFERENCES sr_players(player_uuid) ON DELETE CASCADE,
    ip_address VARCHAR(45) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    left_at TIMESTAMPTZ,
    client_version VARCHAR(32)
);
```

### Migration V2: `V2__create_economy_tables.sql`
```sql
CREATE TABLE IF NOT EXISTS sr_currencies (
    currency_id VARCHAR(32) PRIMARY KEY,
    symbol VARCHAR(8) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    decimals INTEGER NOT NULL DEFAULT 2,
    default_currency BOOLEAN NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS sr_accounts (
    account_id UUID PRIMARY KEY,
    owner_uuid UUID NOT NULL,
    currency_id VARCHAR(32) NOT NULL REFERENCES sr_currencies(currency_id),
    balance NUMERIC(28, 6) NOT NULL DEFAULT 0.000000,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_sr_account_owner_currency UNIQUE (owner_uuid, currency_id)
);
CREATE TABLE IF NOT EXISTS sr_ledger_transactions (
    transaction_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(64) UNIQUE,
    reference_type VARCHAR(32) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS sr_ledger_entries (
    entry_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES sr_ledger_transactions(transaction_id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES sr_accounts(account_id),
    amount NUMERIC(28, 6) NOT NULL,
    balance_after NUMERIC(28, 6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS sr_admin_audit_log (
    audit_id UUID PRIMARY KEY,
    actor_uuid UUID NOT NULL,
    target_uuid UUID NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    amount NUMERIC(28, 6) NOT NULL,
    currency_id VARCHAR(32) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
```

### Migration V3: `V3__create_island_tables.sql`
```sql
CREATE TABLE IF NOT EXISTS sr_islands (
    island_id UUID PRIMARY KEY,
    owner_uuid UUID NOT NULL,
    world_name VARCHAR(64) NOT NULL,
    center_x INTEGER NOT NULL,
    center_y INTEGER NOT NULL,
    center_z INTEGER NOT NULL,
    island_size INTEGER NOT NULL DEFAULT 100,
    island_level BIGINT NOT NULL DEFAULT 0,
    island_name VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS sr_island_members (
    island_id UUID NOT NULL REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    player_uuid UUID NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (island_id, player_uuid)
);
CREATE TABLE IF NOT EXISTS sr_island_upgrades (
    island_id UUID NOT NULL REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    upgrade_key VARCHAR(64) NOT NULL,
    upgrade_level INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (island_id, upgrade_key)
);
CREATE TABLE IF NOT EXISTS sr_island_settings (
    island_id UUID PRIMARY KEY REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    public_visits BOOLEAN NOT NULL DEFAULT true,
    visitor_block_break BOOLEAN NOT NULL DEFAULT false,
    visitor_block_place BOOLEAN NOT NULL DEFAULT false,
    visitor_container_access BOOLEAN NOT NULL DEFAULT false,
    pvp_enabled BOOLEAN NOT NULL DEFAULT false,
    settings_json JSONB,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS sr_island_visits (
    visit_id UUID PRIMARY KEY,
    island_id UUID NOT NULL REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    visitor_uuid UUID NOT NULL,
    visited_at TIMESTAMPTZ NOT NULL,
    visit_duration_seconds BIGINT NOT NULL DEFAULT 0
);
```

---

## 7. Master Command & Permission Registry

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
| `/spawn` | `/lobby`, `/hub` | Teleport to Dragon Nest Central Dais with 3s warmup | None (Default) |
| `/spawn paste` | | Re-paste schematic and regenerate lobby interior | `skyrealms.lobby.admin` |
| `/warp <location>` | | Teleport to castle stations (`spawn`, `castle`, `bank`, etc.) | Essentials default |

---

## 8. Automated Test Suite (47 / 47 Passing)

Run via: `cd C:\servermc\skyrealms; .\gradlew test`
* **`SpatialGridAllocatorTest` (4 tests):**
  * Origin mapping at index 0.
  * Minimum distance enforcement between adjacent islands ($\ge 1000$ blocks).
  * Bijective uniqueness across 10,000 sequential index allocations.
  * $O(1)$ nearest grid anchor spatial resolution.
* **`IslandModelTest` (2 tests):**
  * 4-tier role permission matrix verification (`LEADER`, `OFFICER`, `MEMBER`, `VISITOR`).
  * Spatial coordinate bounding box containment checks.
* **`IslandRepositoryTest` (3 tests):**
  * Atomic island creation and spatial index query.
  * Member role updates and name mutations.
  * Upgrade record tracking and visit duration logging.
* **Platform, Database, Player & Economy Suites (38 tests):**
  * Topological sorting & cycle detection in DAG resolver.
  * Service registry concurrent type resolution.
  * Event bus asynchronous priority ordering.
  * HikariCP pool creation and Flyway migration execution.
  * Player profile serialization and cache warming.
  * Double-entry ledger debits/credits and idempotency key deduplication.

---

## 9. Next Milestones on the Roadmap

* **Milestone 4 — Custom Progression Engine:**
  * Custom Skills: Farming, Mining, Combat, Foraging, Fishing, Enchanting, Alchemy.
  * Tiered Collection Engine for all gathered resources.
  * Research Tree and Recipe Unlocking System.
  * Milestone Rewards, Player Prestige, and Stat Boosters.
  * Flyway Migration `V4__create_progression_tables.sql`.
* **Milestone 5 — Custom Cobblestone Generators, Machines & Minions:**
  * Island cobblestone generator tiering (ore generation probabilities based on island level).
  * Minion / Automation Engine for farming, mining, and mob collection.
  * Machine network and energy/fluid conduits.
* **Milestone 6 — Custom Crops & Industrial Agriculture:**
  * Custom crops, greenhouse biomes, and growth rate boosters.
  * Automated harvesters, sorting pipes, and industrial processors.
* **Milestone 7 — Dungeons, Bosses & Combat Engine:**
  * Custom instanced dungeons, mob scaling, boss arenas, and rare loot tables.
