-- V9: SkyRealms Pets persistence (Cubees companion unlocks, active selection, level, XP, nickname)
CREATE TABLE IF NOT EXISTS sr_player_pets (
    player_uuid VARCHAR(36) NOT NULL,
    pet_id      VARCHAR(64) NOT NULL,
    level       INTEGER     NOT NULL DEFAULT 1,
    xp          DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    nickname    VARCHAR(64) NULL,
    active      BOOLEAN     NOT NULL DEFAULT FALSE,
    unlocked_at BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, pet_id)
);

CREATE INDEX IF NOT EXISTS idx_sr_player_pets_active ON sr_player_pets (player_uuid, active);

