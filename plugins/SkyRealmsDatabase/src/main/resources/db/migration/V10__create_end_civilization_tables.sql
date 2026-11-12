-- V10: SkyRealms End Civilization progression, discipline mastery, quests, bestiary, and Dragon qualification license

CREATE TABLE IF NOT EXISTS sr_end_player_profile (
    player_uuid                VARCHAR(36)  NOT NULL PRIMARY KEY,
    reputation                 INTEGER      NOT NULL DEFAULT 0,
    discovered_districts       TEXT         NOT NULL DEFAULT '',
    dragon_license_unlocked    BOOLEAN      NOT NULL DEFAULT FALSE,
    dragon_license_unlocked_at BIGINT       NULL,
    active_discipline          VARCHAR(16)  NOT NULL DEFAULT 'MELEE',
    updated_at                 BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sr_end_discipline_progress (
    player_uuid         VARCHAR(36) NOT NULL,
    discipline          VARCHAR(16) NOT NULL,
    level               INTEGER     NOT NULL DEFAULT 1,
    total_xp            BIGINT      NOT NULL DEFAULT 0,
    mastery_completed   BOOLEAN     NOT NULL DEFAULT FALSE,
    training_best_score INTEGER     NOT NULL DEFAULT 0,
    updated_at          BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, discipline)
);

CREATE TABLE IF NOT EXISTS sr_end_quest_progress (
    player_uuid     VARCHAR(36)  NOT NULL,
    quest_id        VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'AVAILABLE',
    progress_value  INTEGER      NOT NULL DEFAULT 0,
    target_value    INTEGER      NOT NULL DEFAULT 1,
    cycle_key       VARCHAR(32)  NOT NULL DEFAULT 'PERMANENT',
    completed_at    BIGINT       NULL,
    claimed_at      BIGINT       NULL,
    PRIMARY KEY (player_uuid, quest_id, cycle_key)
);

CREATE INDEX IF NOT EXISTS idx_sr_end_quest_status ON sr_end_quest_progress (player_uuid, status);

CREATE TABLE IF NOT EXISTS sr_end_bestiary (
    player_uuid         VARCHAR(36) NOT NULL,
    mob_id              VARCHAR(64) NOT NULL,
    kills               INTEGER     NOT NULL DEFAULT 0,
    elites_killed       INTEGER     NOT NULL DEFAULT 0,
    first_discovered_at BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (player_uuid, mob_id)
);

