-- Standalone bootstrap for financial_ledger (new DBs / manual runs).
-- Full upgrades: use ALL_MIGRATIONS_CONSOLIDATED.mysql.sql (ensure_column / ensure_index).
CREATE TABLE IF NOT EXISTS financial_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    entry_type VARCHAR(16) NOT NULL DEFAULT 'CREDIT',
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    reference_type VARCHAR(32) NULL,
    reference_id VARCHAR(64) NULL,
    location VARCHAR(50) NOT NULL,
    bill_kind VARCHAR(16) NULL,
    bill_id BIGINT NULL,
    customer_id BIGINT NULL,
    payment_mode VARCHAR(32) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    in_hand_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    event_date DATE NOT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(6) NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uk_fin_ledger_source_del UNIQUE (source_type, source_id, is_deleted)
);

CREATE INDEX idx_fin_ledger_loc_date ON financial_ledger (location, event_date);
CREATE INDEX idx_fin_ledger_mode_date ON financial_ledger (payment_mode, event_date);
CREATE INDEX idx_fin_ledger_event_date_location ON financial_ledger (event_date, location);
CREATE INDEX idx_fin_ledger_entry_type ON financial_ledger (location, event_date, entry_type);
CREATE INDEX idx_fin_ledger_entry_type_only ON financial_ledger (entry_type);
CREATE INDEX idx_event_date ON financial_ledger (event_date);
CREATE INDEX idx_source_type ON financial_ledger (source_type);
