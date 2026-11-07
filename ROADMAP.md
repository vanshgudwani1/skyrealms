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

### [ ] M1 — Core, Database and Players
* [ ] Implement `SkyRealmsDatabase` plugin.
* [ ] Configure HikariCP connection pool with PostgreSQL.
* [ ] Set up Flyway migrations (`V1__initial_player_schema.sql`).
* [ ] Implement `SkyRealmsPlayer` plugin:
  * Persistent player profile loading on join (`AsyncPlayerPreLoginEvent`).
  * Offline data caching and clean unload on quit.
  * Session duration and basic player settings.

---

### [ ] M2 — Authoritative Economy
* [ ] Implement `SkyRealmsEconomy` plugin.
* [ ] Multi-currency support (Coins, Gems).
* [ ] Atomic transactions with idempotency keys.
* [ ] Append-only transaction ledger and administrative audit trail.
* [ ] Bridge Vault economy calls to internal authoritative balances.
* [ ] Economy commands (`/balance`, `/pay`, `/eco`).

---

### [ ] M3 — Islands System
* [ ] Implement `SkyRealmsIslands` plugin.
* [ ] Custom void world generator with Paper World generation API.
* [ ] Island template schematic pasting and boundary protection.
* [ ] Member management, permissions, and roles.
* [ ] Island upgrades (size, generator limit, minion limit).
* [ ] Safe island reset and visiting mechanics.

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
