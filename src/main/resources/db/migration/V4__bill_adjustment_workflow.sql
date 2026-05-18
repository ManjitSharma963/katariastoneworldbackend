-- NON-GST bill adjustment / exchange workflow (additive, traceable)

ALTER TABLE transactions
    ADD COLUMN adjustment_group_id VARCHAR(64) NULL;

CREATE INDEX idx_transactions_adjustment_group ON transactions (adjustment_group_id);

ALTER TABLE bill_payments
    ADD COLUMN is_reversed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reversed_at DATETIME NULL;

ALTER TABLE bill_inventory_returns
    ADD COLUMN refund_mode VARCHAR(32) NULL,
    ADD COLUMN refund_amount DECIMAL(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN settled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN settled_at DATETIME NULL,
    ADD COLUMN adjustment_group_id VARCHAR(64) NULL;

ALTER TABLE bill_inventory_return_lines
    ADD COLUMN unit_price_at_return DECIMAL(14, 2) NULL,
    ADD COLUMN line_return_value DECIMAL(14, 2) NULL;

ALTER TABLE bills_non_gst
    ADD COLUMN adjustment_reason VARCHAR(500) NULL,
    ADD COLUMN adjustment_type VARCHAR(32) NULL;
