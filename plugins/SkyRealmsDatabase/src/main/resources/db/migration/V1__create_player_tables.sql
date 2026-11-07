-- ========================================================
-- SkyRealms V1: Player Identity, Settings, Stats & Sessions
-- ========================================================

-- Authoritative Player Profile
CREATE TABLE IF NOT EXISTS sr_players (
    id UUID PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    first_joined TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_ip VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sr_players_username ON sr_players(username);

-- Player Visual & Gameplay Settings
CREATE TABLE IF NOT EXISTS sr_player_settings (
    player_id UUID PRIMARY KEY REFERENCES sr_players(id) ON DELETE CASCADE,
    scoreboard_visible BOOLEAN NOT NULL DEFAULT TRUE,
    chat_visible BOOLEAN NOT NULL DEFAULT TRUE,
    private_messages_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    island_fly_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Player Gameplay Counters & Statistics
CREATE TABLE IF NOT EXISTS sr_player_stats (
    player_id UUID PRIMARY KEY REFERENCES sr_players(id) ON DELETE CASCADE,
    playtime_seconds BIGINT NOT NULL DEFAULT 0,
    joins_count INT NOT NULL DEFAULT 1,
    deaths INT NOT NULL DEFAULT 0,
    kills INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Player Login Session Audit History
CREATE TABLE IF NOT EXISTS sr_player_sessions (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES sr_players(id) ON DELETE CASCADE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP WITH TIME ZONE,
    duration_seconds BIGINT NOT NULL DEFAULT 0,
    ip_address VARCHAR(45)
);

CREATE INDEX IF NOT EXISTS idx_sr_player_sessions_player ON sr_player_sessions(player_id, started_at);
