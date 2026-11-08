-- ==============================================================================
-- SkyRealms Garden Schema Migration (V5)
-- Gardens, 16 Plots, Garden Unlocks, Crop Mastery, and Player Research
-- ==============================================================================

-- 1. Island Gardens
CREATE TABLE IF NOT EXISTS sr_gardens (
    garden_id UUID PRIMARY KEY,
    island_id UUID NOT NULL UNIQUE REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    region_index INTEGER NOT NULL UNIQUE,
    world_name VARCHAR(64) NOT NULL DEFAULT 'skyrealms_garden',
    center_x INTEGER NOT NULL,
    center_y INTEGER NOT NULL DEFAULT 100,
    center_z INTEGER NOT NULL,
    size INTEGER NOT NULL DEFAULT 256,
    unlocked BOOLEAN NOT NULL DEFAULT TRUE,
    unlocked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gardens_island ON sr_gardens(island_id);
CREATE INDEX IF NOT EXISTS idx_gardens_region_index ON sr_gardens(region_index);
CREATE INDEX IF NOT EXISTS idx_gardens_location ON sr_gardens(world_name, center_x, center_z);

-- 2. 16 Plots per Garden (2 rows of 8)
CREATE TABLE IF NOT EXISTS sr_garden_plots (
    island_id UUID NOT NULL REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    plot_index INTEGER NOT NULL,
    unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    crop_type VARCHAR(32),
    soil_type VARCHAR(32) NOT NULL DEFAULT 'BALANCED',
    fertility DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    moisture DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    nutrient_level DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (island_id, plot_index)
);

CREATE INDEX IF NOT EXISTS idx_garden_plots_island ON sr_garden_plots(island_id);

-- 3. Garden Offering & Unlock Progression
CREATE TABLE IF NOT EXISTS sr_garden_unlocks (
    island_id UUID PRIMARY KEY REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    offering_completed BOOLEAN NOT NULL DEFAULT FALSE,
    cinematic_completed BOOLEAN NOT NULL DEFAULT FALSE,
    unlocked_at TIMESTAMP WITH TIME ZONE
);

-- 4. Crop Mastery Progression (belongs to player)
CREATE TABLE IF NOT EXISTS sr_crop_mastery (
    player_uuid UUID NOT NULL REFERENCES sr_players(id) ON DELETE CASCADE,
    crop_type VARCHAR(32) NOT NULL,
    mastery_xp BIGINT NOT NULL DEFAULT 0,
    mastery_level INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, crop_type)
);

CREATE INDEX IF NOT EXISTS idx_crop_mastery_player ON sr_crop_mastery(player_uuid);

-- 5. Player Research Progression
CREATE TABLE IF NOT EXISTS sr_player_research (
    player_uuid UUID PRIMARY KEY REFERENCES sr_players(id) ON DELETE CASCADE,
    research_points BIGINT NOT NULL DEFAULT 0,
    unlocked_branches VARCHAR(512) NOT NULL DEFAULT '',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
