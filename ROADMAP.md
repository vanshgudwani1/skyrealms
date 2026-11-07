# SkyRealms Project Roadmap

---

### [X] M0 — Project Foundation (Current)
* [x] Establish Gradle multi-project layout with Kotlin DSL.
* [x] Configure Java 21 LTS and Paper API 1.21.1.
* [x] Implement shared platform modules (`platform:api`, `platform:common`, `platform:database-api`, `platform:integrations-api`, `platform:testing`).
* [x] Implement `SkyModule`, `ModuleMetadata`, `ModuleContext`, and `ModuleState`.
* [x] Implement DAG-based `DependencyResolver` with cycle detection and missing dependency handling.
* [x] Implement `DefaultModuleLifecycleManager` with fault isolation.
* [x] Implement thread-safe `ConcurrentServiceRegistry` and prioritized `DefaultEventBus`.
* [x] Implement and build `SkyRealmsCore` Paper plugin.
* [x] Verify runtime loading and clean shutdown on Paper 1.21.1 server.
* [x] Write architectural documentation and ADRs.

---

### [x] M1 — Core, Database and Players
* [x] Implement `SkyRealmsDatabase` plugin.
* [x] Configure HikariCP connection pool with PostgreSQL (and embedded H2 PostgreSQL-mode fallback).
* [x] Set up Flyway migrations (`V1__create_player_tables.sql`).
* [x] Implement `SkyRealmsPlayer` plugin:
  * Persistent player profile loading on join (`AsyncPlayerPreLoginEvent`).
  * Offline data caching and clean unload on quit.
  * Session duration and basic player settings.
  * In-game command `/profile [player]`.
* [x] Shadow plugin integration with SPI `mergeServiceFiles()`.
* [x] ServerLoadEvent boot synchronization.

---

### [x] M2 — Authoritative Economy
* [x] Implement `SkyRealmsEconomy` plugin.
* [x] Multi-currency support (Coins, Gems).
* [x] Atomic transactions with idempotency keys.
* [x] Append-only transaction ledger (`sr_transactions`, `sr_transaction_entries`) and administrative audit trail (`sr_economy_audit`).
* [x] Bridge Vault economy calls to internal authoritative balances (`VaultEconomyBridge`).
* [x] Economy commands (`/balance`, `/pay`, `/eco`).

---

### [x] M3 — Islands System
* [x] Implement `SkyRealmsIslands` plugin.
* [x] Custom void world generator with Paper World generation API (`VoidChunkGenerator`, `VoidBiomeProvider`).
* [x] Deterministic spatial spiral grid allocator ($O(1)$ bijection and hash indexing).
* [x] Native starter island platform and schematic builder.
* [x] Centralized boundary protection (`IslandProtectionService` for blocks, containers, redstone, entities, pistons, fluids, explosions).
* [x] Member management, permissions, and 4-tier roles (`LEADER`, `OFFICER`, `MEMBER`, `VISITOR`).
* [x] Island upgrades (size, members) with Coin economy deduction.
* [x] Safe island reset, rename, visit tracking, interactive GUI, and `/island` suite.
* [x] Database migration V3 (`V3__create_island_tables.sql`).

---

### [ ] M4 — Progression Engine
* [ ] Implement `SkyRealmsProgression` plugin.
* [ ] Skills: Farming, Mining, Combat, Foraging, Fishing.
* [ ] XP curves, leveling rewards, and stat boosts.
* [ ] Collections tracker and milestones.
* [ ] Research tree integration.

---

### [ ] M5 — Production: Generators, Farming & Mining
* [ ] Implement `SkyRealmsGenerators`: Tiered block generators with fuel/efficiency.
* [ ] Implement `SkyRealmsFarming`: Custom crops, growth ticks, and mutations.
* [ ] Implement `SkyRealmsMining`: Custom ores, pickaxe abilities, and regeneration zones.

---

### [ ] M6 — Automation: Minions & Factories
* [ ] Implement `SkyRealmsMachines`:
  * Autonomous Minions (batched processing, no repeating task per minion).
  * Industrial processing factories (input/output inventories, recipes).
  * Offline production with bounded calculation caps.

---

### [ ] M7 — RPG Systems
* [ ] Implement `SkyRealmsCombat` & `SkyRealmsMagic`:
  * Custom weapons, armor, mana pool, and ability casting.
  * Damage calculations and custom item attributes.

---

### [ ] M8 — Dungeons & Boss Encounters
* [ ] Implement `SkyRealmsDungeons` & `SkyRealmsBosses`:
  * Dungeon instances and party matchmaking.
  * Multi-phase boss AI with telegraphed attacks.
  * Loot tables and reward distribution.

---

### [ ] M9 — Economy Marketplace & Social
* [ ] Implement `SkyRealmsMarket`: Auction House, Bazaar, and secure P2P trading.
* [ ] Implement `SkyRealmsSocial`: Parties, Guilds, and Friends.

---

### [ ] M10 — Events & Competitions
* [ ] Implement `SkyRealmsEvents`: Meteor showers, Blood Moons, and scheduled contests.
* [ ] Leaderboards and seasonal awards.

---

### [ ] M11 — Public Launch Preparation
* [ ] Comprehensive end-to-end integration testing.
* [ ] Spark performance profiling and memory leak audits.
* [ ] Security penetration review (economy exploits, item duplication).
* [ ] Disaster recovery and backup automation.
