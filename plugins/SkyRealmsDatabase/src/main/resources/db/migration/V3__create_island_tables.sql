-- ==============================================================================
-- SkyRealms Islands Schema Migration (V3)
-- Islands, Members, Roles, Upgrades, Settings, and Visit History
-- ==============================================================================

-- 1. Islands Primary Table
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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_islands_owner ON sr_islands(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_islands_location ON sr_islands(world_name, center_x, center_z);

-- 2. Island Members and Roles
CREATE TABLE IF NOT EXISTS sr_island_members (
    island_id UUID NOT NULL REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    player_uuid UUID NOT NULL,
    role VARCHAR(16) NOT NULL, -- LEADER, OFFICER, MEMBER, VISITOR
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (island_id, player_uuid)
);

CREATE INDEX IF NOT EXISTS idx_island_members_player ON sr_island_members(player_uuid);

-- 3. Island Upgrades
CREATE TABLE IF NOT EXISTS sr_island_upgrades (
    island_id UUID NOT NULL REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    upgrade_key VARCHAR(64) NOT NULL,
    upgrade_level INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (island_id, upgrade_key)
);

-- 4. Island Protection & Visitor Settings
CREATE TABLE IF NOT EXISTS sr_island_settings (
    island_id UUID PRIMARY KEY REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    public_visits BOOLEAN NOT NULL DEFAULT TRUE,
    visitor_block_break BOOLEAN NOT NULL DEFAULT FALSE,
    visitor_block_place BOOLEAN NOT NULL DEFAULT FALSE,
    visitor_container_access BOOLEAN NOT NULL DEFAULT FALSE,
    pvp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    settings_json TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Island Visit History & Metrics
CREATE TABLE IF NOT EXISTS sr_island_visits (
    visit_id UUID PRIMARY KEY,
    island_id UUID NOT NULL REFERENCES sr_islands(island_id) ON DELETE CASCADE,
    visitor_uuid UUID NOT NULL,
    visited_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    visit_duration_seconds BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_visits_island_date ON sr_island_visits(island_id, visited_at);
CREATE INDEX IF NOT EXISTS idx_visits_visitor ON sr_island_visits(visitor_uuid);
