# SkyRealms Garden — Farming Progression Improvements & Balancing Walkthrough (F1–F6)

> **Target Platform:** Paper 1.21.1 (Java 21 LTS)  
> **mcMMO Version:** `mcMMO 2.3.002` (`HERBALISM` rebranded as **Farming**)  
> **Last Updated:** 2026-11-10

---

## 1. Authoritative Architecture Overview

1. **Single Overall Farming XP Source (mcMMO)**:
   - `McMMOFarmingService` wraps `ExperienceAPI` (`HERBALISM`).
   - `McMMOIntegrationListener` cancels all non-Herbalism XP, enforces authorized unlocked Garden region + `IslandPermission.BLOCK_BREAK` checks on `McMMOPlayerPreXpGainEvent`, cancels all built-in `McMMOPlayerAbilityActivateEvent` & `SubSkillEvent` triggers, and blocks all 18 non-Farming skill commands.
2. **Centralized Harvest Reward Pipeline (`HarvestRewardPipeline`)**:
   - All Garden harvests (both primary left-click crop breaks and Sakura Harvest Surge 3×3 AoE neighbor breaks) route through `HarvestRewardPipeline.calculatePlan(...)` and `commitHarvest(...)`.
   - Per-block coordinate locking (`tryClaimBlock` / `releaseBlock`) and `harvestId` idempotency keys prevent duplicate drops, double XP, double collection increments, or double coin payouts.
3. **Reward Formulas & Caps (`GardenBalanceConfig` in `config.yml`)**:
   - **Combined Bonus Drop Chance**: `clamp(bountifulChance + masteryDoubleDropBonus + extraModifiers, 0.0, 0.75)` with `maxBonusDropsPerHarvest = 3`.
   - **mcMMO Farming XP**: Primary `BlockBreakEvent` lets mcMMO award base XP natively (`baseCustomMcMmoXp = 0`) and adds only the Crop Mastery bonus XP once via `ExperienceAPI.addRawXP(..., true)`; AoE neighbor blocks award `baseMcMmoXp + bonusMcMmoXp` once, clamped at `maxXpMultiplier = 2.0`.
   - **Golden Sprout**: `clamp(cropBaseCoins + levelsAbove30 * 1.25 + masteryTier * 5.0, 0.0, 250.0)` with a `15,000` Coins UTC daily cap per player.
4. **Garden-Only Abilities**:
   - **Bountiful Harvest** (Lv.1+): `5%` to `50%` smooth linear scaling across Lv.1–100.
   - **Verdant Aura** (Lv.10+): Bounded Haste I (Haste II at Lv.50+) + Speed I (Lv.25+), managed by `VerdantAuraManager` without per-tick polling and automatically removed when leaving the Garden or changing worlds.
   - **Sakura Harvest Surge** (Lv.15+, Sneak + Right-Click Hoe): 3×3 mature crop harvest (`10s`–`20s` duration, `60s`–`30s` cooldown) with per-block permission & plot validation and persistent cooldowns across relog/world changes.
   - **Golden Sprout** (Lv.30+): `1%` to `15%` chance to deposit Coins via `EconomyService` with transaction verification and threshold logging (`>= 100` Coins).
5. **Crop Mastery & Botanical Journal**:
   - 10 crops × 7 Tiers gated by both mcMMO Farming Level (`1, 5, 10, 20, 35, 50, 75`) and crop harvests (`25`–`7,500`), each with distinct crop-specific identities, discoveries, cosmetics, and equipment attunement hooks (`MasteryMilestone`).
   - Flyway `V7__create_botanical_journal.sql` adds `sr_botanical_journal`, connecting `sr_farming_crop_collections`, `sr_farming_mutation_discoveries`, `sr_crop_mastery`, harvest streaks, best single harvest, and 8 permanent achievements.

---

## 2. In-Game Commands & GUIs

* `/garden plot` — Open the 54-slot **Plot Studio GUI** (`GardenPlotGui`) to switch layout presets (with destructive-change confirmation), toggle Irrigation/Decoration styles, inspect/cycle Crop Beds (`Basic`, `Rich Soil`, `Specialized`), preview boundaries, upgrade plot expansion tiers (Lv.1–4), or manage Saved Layouts.
* `/garden plot edit` — Toggle vanilla-compatible **Plot Edit Mode** (Left-Click Corner 1, Right-Click Corner 2, Sneak+Right-Click to create a Crop Bed).
* `/garden plot layout [preset] [confirm]` — Preview or apply `clean_canvas`, `traditional_rows`, `japanese_garden`, `greenhouse`, or `terraced_garden`.
* `/garden plot save <name>` / `/garden plot reset [confirm]` — Save custom bed/layout templates to `sr_garden_saved_layouts` or safely reset back to Clean Canvas.
* `/garden mastery` — 54-slot **Crop Mastery** overview & 7-tier detail GUI with navigation bar.
* `/garden abilities` — 54-slot **Garden Farming Abilities** GUI showing live mcMMO level, chances, cooldowns, caps, and Verdant Aura status.
* `/garden journal` — 54-slot read-only **Botanical Journal** GUI showing lifetime collections, mutation discoveries, streaks, strains, and 8 achievements.
* `/garden home | leave | info | plots | help` — Garden navigation and status commands.

---

## 3. Verification Commands

* **Automated Tests**: `cd C:\servermc\skyrealms; .\gradlew test --rerun-tasks` (`97 / 97` tests passing across all modules; `45` tests in `SkyRealmsFarming`).
* **Deployed Artifacts**: `C:\servermc\plugins\SkyRealmsDatabase-1.0.0.jar` and `C:\servermc\plugins\SkyRealmsFarming-1.0.0.jar`.

