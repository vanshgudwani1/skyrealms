# SkyRealms — Master Architecture & Platform Monorepo

[![Platform](https://img.shields.io/badge/Minecraft-Paper%201.21.1-blue.svg)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://adoptium.net)
[![Build](https://img.shields.io/badge/Gradle-8.12%20Kotlin%20DSL-green.svg)](https://gradle.org)
[![Database](https://img.shields.io/badge/Database-PostgreSQL%20%7C%20H2-blue.svg)](https://www.postgresql.org/)
[![Client](https://img.shields.io/badge/Client-100%25%20Vanilla%20Compatible-brightgreen.svg)]()
[![Tests](https://img.shields.io/badge/Unit%20Tests-65%2B%20Passing-success.svg)]()

> **SkyRealms** is an enterprise-grade, RPG and progression-driven Minecraft SkyBlock server platform built from the ground up for **Paper 1.21.1 on Java 21 LTS**.
> Completely compatible with **100% vanilla Java Minecraft clients** (zero client-side mods, launchers, or mandatory resource packs).

---

## 📖 Complete Technical Manual
For exhaustive class-by-class, method-by-method, and database schema documentation, see:
* **[SKYREALMS_COMPLETE_SYSTEM_DOCUMENTATION.txt](SKYREALMS_COMPLETE_SYSTEM_DOCUMENTATION.txt)** — Master Platform Manual
* **[GARDEN_SYSTEM_OVERVIEW.txt](GARDEN_SYSTEM_OVERVIEW.txt)** — Garden & Botanical Conservatory Manual

---

## 🏛 Architecture & Modular DAG Ecosystem

SkyRealms is built as a **Modular Monolith** using decoupled Paper plugin JARs coordinated by an in-house Directed Acyclic Graph (DAG) module lifecycle engine.

```
                  ┌──────────────────────┐
                  │     platform:api     │
                  └──────────┬───────────┘
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ platform:common │ │platform:database│ │platform:integ.  │
└────────┬────────┘ └────────┬────────┘ └─────────────────┘
         └───────────────────┼───────────────────┘
                             ▼
                    ┌─────────────────┐
                    │  SkyRealmsCore  │ (Runtime Kernel & DAG Lifecycle)
                    └────────┬────────┘
                             ▼
                    ┌─────────────────┐
                    │SkyRealmsDatabase│ (HikariCP / Flyway V1-V6 / H2 Fallback)
                    └────────┬────────┘
                             ▼
                    ┌─────────────────┐
                    │ SkyRealmsPlayer │ (Profiles, Preloaded Caching, Sessions)
                    └────────┬────────┘
                             ▼
                    ┌─────────────────┐
                    │SkyRealmsEconomy │ (Multi-Currency: Coins & Gems, Vault)
                    └────────┬────────┘
                             ▼
                    ┌─────────────────┐
                    │SkyRealmsIslands │ (Spatial Allocation, Protection, Roles)
                    └────────┬────────┘
                             ▼
                    ┌─────────────────┐
                    │SkyRealmsFarming │ (Sakura Conservatory, Mastery, Quests)
                    └─────────────────┘
```

---

## 📦 Monorepo Module Catalog

### 1. Platform Modules (`/platform`)
| Module | Role & Key Responsibilities |
| :--- | :--- |
| **`platform:api`** | Public interfaces, `EventBus`, `SkyEvent`, `CancellableEvent`, `SkyModule`, `ModuleContext`, `ServiceRegistry`. |
| **`platform:common`** | `DefaultEventBus`, `DirectedGraphDependencyResolver` (DFS topological sorter), `DynamicCommandRegistrar`. |
| **`platform:database-api`** | `DatabaseService`, `TransactionCallback`, `VoidTransactionCallback` abstractions. |
| **`platform:integrations-api`**| Provider abstractions for Vault Economy and LuckPerms Permissions. |
| **`platform:testing`** | JUnit 5 test harness, `DummyTestModule`, and in-memory test contexts. |

### 2. Runtime Plugins (`/plugins`)
| Plugin JAR | Description & Systems |
| :--- | :--- |
| **`SkyRealmsCore`** | **Platform Kernel**: Bootstraps the platform runtime, coordinates the DAG lifecycle, manages dynamic commands, and provides `/tpworld` & `extra-worlds` void-world loading. |
| **`SkyRealmsDatabase`** | **Data Engine**: HikariCP connection pool, Flyway database migrations (V1 to V10), automatic failover from PostgreSQL to local H2. |
| **`SkyRealmsPlayer`** | **Identity & Profiles**: Prelogin asynchronous profile caching (zero main-thread hitches), playtime statistics, personal settings. |
| **`SkyRealmsEconomy`** | **Banking & Ledger**: Multi-currency engine (Coins & Gems), transactional double-entry ledger, full Vault economy provider. |
| **`SkyRealmsIslands`** | **SkyBlock Islands**: Deterministic 2D spiral allocator (500-block spacing), dedicated void world `skyrealms_islands`, cooperative roles & permissions, boundary protection, Sakura Hub spawn & island void rescue. |
| **`SkyRealmsFarming`** | **Agricultural Overhaul**: Project Sakura Glass Garden (16 customizable 4×4 chunk plots), mcMMO Herbalism progression, Harvest Reward Pipeline, Botanical Journal, Crop Mastery, and Garden abilities. |
| **`SkyRealmsItems`** | **Custom Gear & Enchants**: Upgradable farming hoes, custom enchant controller (`EliteEnchantments`), vanilla enchant blocker, and stat providers. |
| **`SkyRealmsPets`** | **3D Companions**: Cubees ModelEngine v4 pets, custom `/pets` GUI, pet XP progression, and Farming Fortune bonuses. |
| **`SkyRealmsPlaceholders`** | **TAB & PAPI Bridge**: Registers `%skyrealms_*%` PlaceholderAPI expansion backed by cached SkyRealms/LuckPerms/mcMMO metrics. |
| **`SkyRealmsEnd`** | **End Civilization**: 7 End districts, 35 interactive NPCs, Melee/Archery/Mage combat training & leaderboards, quest chains & reputation, Vaelkor boss encounter, End gear catalog, and Dragon qualification. |

---

## 🌸 Highlight System: Project Sakura Glass Garden

The SkyRealms farming experience has been completely overhauled from physical generators to a breathtaking, natural conservatory:

* **Unified Botanical Envelope**: 520×168 block greenhouse with curved transparent glass canopy, Torii arches, koi pond, moon bridge, tea house, waterfall, and 12 massive Sakura blossom trees.
* **16 Agricultural Plots**: Each plot measures **4×4 chunks (64×64 blocks = 4,096 blocks)** with a built-in 100% moisture irrigation grid (sea lanterns + waterlogged stone slabs).
* **Divine Offering Ritual**: Players craft 3 Enchanted Hay Bales, 3 Enchanted Carrots, and 3 Enchanted Sugar Cane, placing them into a renamed chest `"Offering to the Gods"`. Triggers an in-world Allay emissary cinematic and unlocks the Garden.
* **Natural Farming Engine**: Instant right-click fast harvesting with auto-replant, age validation, anti-exploit placed-crop tracking, and real-time action bar feedback.
* **Crop Mastery**: 10 crops, 30 levels with power-law XP curve $\lfloor 50 \times \text{level}^{2.1} \rfloor$, 7 claimed milestones awarding double drops, potion buffs (Night Vision, Speed, Haste, Fire Resistance), and coin rewards.

---

## 🗄 Database Schema & Migrations

Flyway manages schema migrations across PostgreSQL and embedded H2:

* **`V1__create_player_tables.sql`**: `sr_players`, `sr_player_sessions`.
* **`V2__create_economy_tables.sql`**: `sr_currencies`, `sr_balances`, `sr_transactions`.
* **`V3__create_island_tables.sql`**: `sr_islands`, `sr_island_members`, `sr_island_upgrades`.
* **`V4__create_farming_tables.sql`**: `sr_farming_progress`, `sr_crop_collections`, `sr_farming_generators` *(retired)*.
* **`V5__create_garden_tables.sql`**: `sr_gardens`, `sr_garden_plots`, `sr_garden_unlocks`, `sr_crop_mastery`, `sr_player_research`.
* **`V6__create_garden_quests_and_milestones.sql`**: `sr_garden_offerings`, `sr_farming_quests`, milestone claims, Elarion intro flags.
* **`V7__create_botanical_journal.sql`**: Botanical Journal crop discovery & harvest tracking.
* **`V8__create_customizable_plot_tables.sql`**: Plot presets, crop beds, and plot layout customization.
* **`V9__create_pet_tables.sql`**: Persistent unlocked pets, XP, levels, and active companion selection.

---

## 🎮 Command & Permission Reference

### Player Commands
| Command | Permission | Description |
| :--- | :--- | :--- |
| `/garden` (or `/garden home`) | `skyrealms.garden.base` | Teleport to your Greenhouse Dome (checks offering unlock). |
| `/garden leave` | `skyrealms.garden.base` | Teleport back to your Island Home spawn. |
| `/garden mastery` | `skyrealms.garden.base` | Open the interactive 10-crop Crop Mastery GUI. |
| `/garden quest` | `skyrealms.garden.base` | View First Harvest quest objectives and live progress. |
| `/garden plots` | `skyrealms.garden.base` | View unlocked/locked status of all 16 agricultural plots. |
| `/garden info` | `skyrealms.garden.base` | View Garden coordinates, center anchor, and region bounds. |
| `/garden help` | `skyrealms.garden.base` | View the clean in-game Garden command guide. |
| `/is` (or `/island`) | `skyrealms.islands.base` | Create, manage, invite members, upgrade, or visit islands. |
| `/balance` (or `/bal`) | `skyrealms.economy.balance` | View your personal Coin and Gem balances. |
| `/pay <player> <amount>` | `skyrealms.economy.pay` | Transfer Coins to another player with verification. |
| `/profile` | `skyrealms.player.profile` | View player profile, join date, statistics, and rank. |

### Administrative Commands
| Command | Permission | Description |
| :--- | :--- | :--- |
| `/garden admin reset <player>` | `skyrealms.garden.admin` (OP) | Completely resets target player's offering, unlock, Elarion intro, and quests. Teleports them out of the garden back to their island. |
| `/garden admin tp <player>` | `skyrealms.garden.admin` (OP) | Teleport directly into target player's island garden. |
| `/garden admin unlockplot <player> <1-16>`| `skyrealms.garden.admin` (OP) | Manually unlocks a specific 4×4 chunk plot for an island. |
| `/garden admin giveitem <player> <item> [amt]` | `skyrealms.garden.admin` (OP) | Gives custom items (`hay`, `carrot`, `cane`, `hoe`, `seeds`, `bonemeal`). |
| `/eco <give\|take\|set\|reset> <player> <amt>` | `skyrealms.economy.admin` (OP) | Administrative bank management. |
| `/skyrealms status` | `skyrealms.admin` (OP) | Displays runtime DAG module health and registered services. |

---

## 🛠 Compilation, Testing & Deployment

### Requirements
* **Java 21 LTS** (Temurin 21 recommended)
* **Gradle 8.12** (included via wrapper)
* **Paper 1.21.1 Server**

### Build All Plugins
```bash
./gradlew build
```
*(Every plugin automatically executes unit tests, packages production JARs, and copies them to `../plugins/` ready for server deployment).*

### Execute Unit Test Suite
```bash
./gradlew test
```
*Current test suite: **65+ passing unit tests** verifying DAG topological resolution, cycle detection, Flyway migrations, HikariCP rollback, multi-currency ledger arithmetic, spatial allocation grids, 4×4 chunk plot boundaries, natural farming maturity, and crop mastery power curves.*

---

## 📄 License & Credits
Developed for the **SkyRealms Network**. All rights reserved.
Repository: [https://github.com/vanshgudwani1/skyrealms.git](https://github.com/vanshgudwani1/skyrealms.git)
