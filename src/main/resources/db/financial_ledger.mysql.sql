-- Canonical financial ledger (idempotent by source_type + source_id).
CREATE TABLE IF NOT EXISTS financial_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    location VARCHAR(50) NOT NULL,
    bill_kind VARCHAR(16) NULL,
    bill_id BIGINT NULL,
    customer_id BIGINT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    payment_mode VARCHAR(32) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    in_hand_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    event_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_fin_ledger_source UNIQUE (source_type, source_id)
);

CREATE INDEX idx_fin_ledger_loc_date ON financial_ledger (location, event_date);
CREATE INDEX idx_fin_ledger_mode_date ON financial_ledger (payment_mode, event_date);
