# SkyRealms Islands Engine Documentation (Milestone 3)

The **SkyRealmsIslands** plugin (`plugins/SkyRealmsIslands`) implements the authoritative SkyBlock island management engine, void world generator, spatial grid allocator, boundary protection listener, upgrade pipeline, and player command/GUI interface for SkyRealms.

---

## Architecture Overview

```
                      ┌────────────────────────┐
                      │    SkyRealmsCore       │
                      │ (ModuleRegistry / Bus) │
                      └───────────┬────────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         │                        │                        │
┌────────▼────────┐      ┌────────▼────────┐      ┌────────▼────────┐
│SkyRealmsDatabase│      │ SkyRealmsPlayer │      │SkyRealmsEconomy │
│  (Flyway V3)    │      │  (Profiles)     │      │(Coins / Upgrades)
└────────┬────────┘      └────────┬────────┘      └────────┬────────┘
         │                        │                        │
         └────────────────────────┼────────────────────────┘
                                  │
                      ┌───────────▼────────────┐
                      │    SkyRealmsIslands    │
                      │                        │
                      │ ├─ SpatialGridAllocator│
                      │ ├─ VoidChunkGenerator  │
                      │ ├─ StarterGenerator    │
                      │ ├─ ProtectionService   │
                      │ ├─ IslandManager       │
                      │ └─ IslandCommand & GUI │
                      └────────────────────────┘
```

---

## 1. Void World Generation & Dedicated Dimension

- **World Name:** `skyrealms_islands` (configurable in `config.yml`).
- **Generator:** [`VoidChunkGenerator`](file:///C:/servermc/skyrealms/plugins/SkyRealmsIslands/src/main/java/com/skyrealms/islands/world/VoidChunkGenerator.java).
  - Generates zero vanilla terrain blocks, structures, or caves.
  - Overrides biome generation to use `PLAINS` via [`VoidBiomeProvider`](file:///C:/servermc/skyrealms/plugins/SkyRealmsIslands/src/main/java/com/skyrealms/islands/world/VoidBiomeProvider.java).
  - Automatically loads and verifies the dedicated void world on module startup.

---

## 2. Spatial Grid Allocation Engine

The spatial allocator uses a square spiral bijection mapping each sequential island index $n \in [0, \infty)$ to a unique integer grid coordinate $(gx, gz)$:

```
           (-1, 1)   (0, 1)   (1, 1)
              ▲        ▲        ▲
              │        │        │
           (-1, 0) ── (0, 0) ── (1, 0)
                       │        │
                       ▼        ▼
           (-1,-1) ── (0,-1) ── (1,-1)
```

- **Collision-free bijection:** $n \to (gx, gz)$ guarantees that no two islands will ever occupy the same anchor.
- **Spacing:** Default spacing is 500 blocks center-to-center.
- **Anchor Center:** $(centerX, centerZ) = (gx \times 500, gz \times 500)$.
- **Spatial Hash Key:** $O(1)$ fast lookup without scanning the database or Bukkit entities:
  $$\text{Key} = (anchorX \ll 32) \mid (anchorZ \ \& \ \text{0xFFFFFFFFL})$$
- **Nearest Anchor Resolution:** Any coordinate $(x, z)$ maps to its bounding island anchor in $O(1)$ time via:
  $$gx = \text{round}(x / 500), \quad gz = \text{round}(z / 500)$$

---

## 3. Starter Island Generation

When a player executes `/is create`:
1. Island anchor coordinates are generated from `SpatialGridAllocator`.
2. Starter island platform is generated natively on the server main thread:
   - Bedrock anchor block placed at island center $(X, Y-2, Z)$.
   - Dirt layers (diameter 7 blocks, 3 layers deep) with a top layer of lush grass.
   - An Oak Tree is grown at $(X-1, Y+1, Z-1)$.
   - A Starter Chest is placed at $(X+1, Y+1, Z)$ containing basic starter supplies (Ice, Lava Bucket, Water Bucket, Bread, Saplings, Bone Meal, Seeds).
   - Island safe spawn point is calibrated at $(X+0.5, Y+1.0, Z+0.5)$.

---

## 4. Role Hierarchy & Permissions

| Role | Description | Permissions |
|---|---|---|
| **`LEADER`** | Absolute owner | Full permissions, invite/kick members, toggle settings, upgrade, reset/delete island. |
| **`OFFICER`** | Trusted administrator | Block break/place, container access, entity damage, invite/kick members, upgrade island. Cannot delete/reset island. |
| **`MEMBER`** | Standard team member | Block break/place, open containers, interact with redstone/doors/animals. No management or destruction rights. |
| **`VISITOR`** | Non-member player | Read-only by default (can interact and walk around). Block break/place and chest access are controlled dynamically by `IslandSettings`. |

---

## 5. Centralized Protection Engine

[`IslandProtectionService`](file:///C:/servermc/skyrealms/plugins/SkyRealmsIslands/src/main/java/com/skyrealms/islands/protection/IslandProtectionService.java) enforces spatial boundary rules:
- **Block Protection:** Intercepts `BlockBreakEvent`, `BlockPlaceEvent`, `SignChangeEvent`.
- **Container Protection:** Intercepts `PlayerInteractEvent` for chests, barrels, hoppers, dispensers, shulker boxes, furnaces, brewing stands, and anvils.
- **Entity Protection:** Intercepts `EntityDamageByEntityEvent` and `PlayerArmorStandManipulateEvent` for friendly animals, item frames, and armor stands.
- **Physical Environmental Protections:**
  - `BlockFromToEvent`: Prevents water and lava from flowing across island boundaries into adjacent airspace.
  - `BlockPistonExtendEvent` / `BlockPistonRetractEvent`: Prevents pistons from pushing/pulling blocks beyond the island boundary.
  - `EntityExplodeEvent` / `BlockExplodeEvent`: Strips exploded blocks located outside the originating island boundary or on visitor-protected terrain.
- **Admin Bypass:** Operators or players with `skyrealms.island.admin` can freely build and bypass restrictions.

---

## 6. Island Upgrades & Economy Integration

- **Size Upgrade:** Expands island boundary radius (Tier 1: 100 blocks, Tier 2: 120 blocks, Tier 3: 140 blocks, Tier 4: 160 blocks, Tier 5: 200 blocks).
- **Member Limit Upgrade:** Increases maximum island co-op roster (Tier 1: 4 members up to Tier 5: 12 members).
- **Currency:** Upgrades cost Coins, deducting funds atomically via `EconomyService.withdraw()` with double-entry ledger tracking.

---

## 7. Command Suite & Interactive GUI

Command root: `/island` (aliases: `/is`)
- `/is` or `/is gui` — Opens the SkyRealms interactive Island Control Panel GUI.
- `/is create` — Creates and allocates a new floating island.
- `/is home` or `/is go` — Teleports the player to their island center.
- `/is invite <player>` — Sends an invite to join the island team.
- `/is accept` — Accepts a pending island invitation.
- `/is kick <player>` — Removes a team member.
- `/is leave` — Voluntarily departs from a co-op island.
- `/is visit <player>` — Visits a public player island.
- `/is settings` — Opens or views island privacy and visitor permissions.
- `/is info` — Displays island stats (level, size, members, status).
- `/is members` — Lists all current island members and roles.
- `/is name <new_name>` — Renames the island.
- `/is reset` — Clears and regenerates the starter island.
- `/is delete` — Permanently removes the island.

---

## 8. Database Schema (Flyway V3)

Migration file: `V3__create_island_tables.sql`
- `sr_islands`: Primary metadata, anchor coordinates, size, level, name, status.
- `sr_island_members`: Island members, UUIDs, roles (`LEADER`, `OFFICER`, `MEMBER`), join timestamps.
- `sr_island_upgrades`: Per-island upgrade keys and tiers.
- `sr_island_settings`: Visitor flags (`public_visits`, `visitor_block_break`, `visitor_block_place`, `visitor_container_access`, `pvp_enabled`, `settings_json`).
- `sr_island_visits`: Teleport visit tracking, timestamps, visit durations.
