-- ==============================================================================
-- SkyRealms Farming Schema Migration (V4)
-- Farming Generators, Upgrades, Player Progress, Collections, and Mutations
-- ==============================================================================

-- 1. Farming Generators
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

CREATE INDEX IF NOT EXISTS idx_farming_gen_island ON sr_farming_generators(island_id);
CREATE INDEX IF NOT EXISTS idx_farming_gen_owner ON sr_farming_generators(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_farming_gen_location ON sr_farming_generators(world_name, core_x, core_y, core_z);

-- 2. Generator Upgrades
CREATE TABLE IF NOT EXISTS sr_farming_generator_upgrades (
    generator_id UUID NOT NULL REFERENCES sr_farming_generators(generator_id) ON DELETE CASCADE,
    upgrade_key VARCHAR(64) NOT NULL,
    upgrade_level INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (generator_id, upgrade_key)
);

-- 3. Player Farming Progression (XP & Level)
CREATE TABLE IF NOT EXISTS sr_farming_player_progress (
    player_uuid UUID PRIMARY KEY REFERENCES sr_players(id) ON DELETE CASCADE,
    farming_xp BIGINT NOT NULL DEFAULT 0,
    farming_level INTEGER NOT NULL DEFAULT 1,
    total_harvests BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Crop Collections Tracking
CREATE TABLE IF NOT EXISTS sr_farming_crop_collections (
    player_uuid UUID NOT NULL REFERENCES sr_players(id) ON DELETE CASCADE,
    crop_type VARCHAR(32) NOT NULL,
    amount_collected BIGINT NOT NULL DEFAULT 0,
    tier_unlocked INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, crop_type)
);

CREATE INDEX IF NOT EXISTS idx_farming_collections_player ON sr_farming_crop_collections(player_uuid);

-- 5. Rare Mutation Discoveries
CREATE TABLE IF NOT EXISTS sr_farming_mutation_discoveries (
    discovery_id UUID PRIMARY KEY,
    player_uuid UUID NOT NULL REFERENCES sr_players(id) ON DELETE CASCADE,
    mutation_type VARCHAR(32) NOT NULL,
    crop_type VARCHAR(32) NOT NULL,
    discovered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    count BIGINT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_farming_mutations_player ON sr_farming_mutation_discoveries(player_uuid);
