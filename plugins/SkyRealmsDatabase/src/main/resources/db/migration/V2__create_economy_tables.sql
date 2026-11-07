-- ==============================================================================
-- SkyRealms Economy Schema Migration (V2)
-- Currencies, Wallets, Transactions, Double-Entry Ledger, Idempotency, and Audit
-- ==============================================================================

-- 1. Currencies Configuration Table
CREATE TABLE IF NOT EXISTS sr_currencies (
    id VARCHAR(32) PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL,
    symbol VARCHAR(8) NOT NULL,
    decimal_places INT NOT NULL DEFAULT 2,
    default_balance NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    transferable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Player Currency Wallets
CREATE TABLE IF NOT EXISTS sr_wallets (
    owner_id UUID NOT NULL,
    currency_id VARCHAR(32) NOT NULL REFERENCES sr_currencies(id) ON DELETE RESTRICT,
    balance NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_id, currency_id),
    CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX IF NOT EXISTS idx_wallets_owner ON sr_wallets(owner_id);

-- 3. Idempotency Keys (Deduplication Gate)
CREATE TABLE IF NOT EXISTS sr_idempotency_keys (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    transaction_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Transactions Ledger Header
CREATE TABLE IF NOT EXISTS sr_transactions (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) UNIQUE,
    transaction_type VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transactions_created ON sr_transactions(created_at);

-- 5. Transaction Entries (Double-Entry Bookkeeping)
CREATE TABLE IF NOT EXISTS sr_transaction_entries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES sr_transactions(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL,
    currency_id VARCHAR(32) NOT NULL REFERENCES sr_currencies(id) ON DELETE RESTRICT,
    amount NUMERIC(18, 2) NOT NULL,
    balance_after NUMERIC(18, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_entries_owner_currency ON sr_transaction_entries(owner_id, currency_id, created_at);

-- 6. Administrative Economy Audit Trail
CREATE TABLE IF NOT EXISTS sr_economy_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id UUID,
    actor_name VARCHAR(64) NOT NULL,
    target_id UUID NOT NULL,
    currency_id VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed Initial Currencies
INSERT INTO sr_currencies (id, display_name, symbol, decimal_places, default_balance, transferable)
VALUES 
    ('coins', 'Coins', '⛃', 2, 100.00, TRUE),
    ('gems', 'Gems', '✦', 0, 0.00, FALSE);
