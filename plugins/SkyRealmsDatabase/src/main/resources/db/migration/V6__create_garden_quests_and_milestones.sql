-- ==============================================================================
-- SkyRealms Garden Phase G3 & G4 Schema Migration (V6)
-- Garden Quests, Offering Submissions, Elarion Intro, and Crop Milestones
-- ==============================================================================

-- 1. Garden Offering Audit & History
CREATE TABLE IF NOT EXISTS sr_garden_offerings (
    island_id UUID PRIMARY KEY REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    submitted_by UUID NOT NULL REFERENCES sr_players(id) ON DELETE CASCADE,
    hay_bales_count INTEGER NOT NULL DEFAULT 3,
    carrots_count INTEGER NOT NULL DEFAULT 3,
    sugar_cane_count INTEGER NOT NULL DEFAULT 3,
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_garden_offerings_player ON sr_garden_offerings(submitted_by);

-- 2. Farming Quests Progression (First Harvest Quest & Future Quests)
CREATE TABLE IF NOT EXISTS sr_farming_quests (
    player_uuid UUID NOT NULL REFERENCES sr_players(id) ON DELETE CASCADE,
    quest_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    tilled_count INTEGER NOT NULL DEFAULT 0,
    planted_count INTEGER NOT NULL DEFAULT 0,
    harvested_count INTEGER NOT NULL DEFAULT 0,
    completed_at TIMESTAMP WITH TIME ZONE,
    claimed_rewards BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, quest_id)
);

CREATE INDEX IF NOT EXISTS idx_farming_quests_player ON sr_farming_quests(player_uuid);

-- 3. Add Claimed Milestones to Crop Mastery for Milestone Duplication Prevention
ALTER TABLE sr_crop_mastery ADD COLUMN IF NOT EXISTS claimed_milestones VARCHAR(256) NOT NULL DEFAULT '';

-- 4. Add Elarion Introduction Completed Flag to Player Research
ALTER TABLE sr_player_research ADD COLUMN IF NOT EXISTS elarion_intro_completed BOOLEAN NOT NULL DEFAULT FALSE;
