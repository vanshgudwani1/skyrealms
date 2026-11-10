-- ==============================================================================
-- SkyRealms Garden Botanical Journal Schema Migration (V7)
-- Tracks player botanical journal achievements, streaks, and discovery extension state
-- without duplicating sr_farming_crop_collections or sr_farming_mutation_discoveries.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS sr_botanical_journal (
    player_uuid UUID PRIMARY KEY REFERENCES sr_players(id) ON DELETE CASCADE,
    highest_harvest_streak INTEGER NOT NULL DEFAULT 0,
    best_single_harvest INTEGER NOT NULL DEFAULT 1,
    unlocked_achievements VARCHAR(1024) NOT NULL DEFAULT '',
    discovered_varieties VARCHAR(1024) NOT NULL DEFAULT '',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

