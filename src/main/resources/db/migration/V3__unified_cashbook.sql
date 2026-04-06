-- Unified money movement + per-day balance (coexists with legacy expenses / daily_budget / financial_ledger).
-- Backfill from legacy tables can be a separate data migration; old tables remain for reference.

CREATE TABLE IF NOT EXISTS location_day_budget (
    id BIGINT NOT NULL AUTO_INCREMENT,
    budget_date DATE NOT NULL,
    location VARCHAR(50) NOT NULL,
    opening_balance DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    manual_adjustment_total DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    current_balance DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    closing_balance DECIMAL(14, 2) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_location_day_budget_date_loc (budget_date, location),
    KEY idx_location_day_budget_loc (location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS money_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tx_type VARCHAR(16) NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    category VARCHAR(100) NOT NULL,
    payment_mode VARCHAR(32) NULL,
    reference_type VARCHAR(32) NULL,
    reference_id VARCHAR(64) NULL,
    location VARCHAR(50) NOT NULL,
    event_date DATE NOT NULL,
    description TEXT NULL,
    balance_after DECIMAL(14, 2) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_money_tx_loc_date (location, event_date, is_deleted),
    KEY idx_money_tx_created (created_at),
    KEY idx_money_tx_ref (reference_type, reference_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS budget_manual_adjustment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    budget_date DATE NOT NULL,
    location VARCHAR(50) NOT NULL,
    adjustment_kind VARCHAR(24) NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    balance_after DECIMAL(14, 2) NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_budget_adj_loc_date (location, budget_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
