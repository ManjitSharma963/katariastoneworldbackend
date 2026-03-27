-- Mid-risk consistency improvements: audit fields, soft delete flags, and performance indexes.

-- Bills audit + soft delete
ALTER TABLE bills_gst
    ADD COLUMN updated_by_user_id BIGINT NULL,
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE bills_non_gst
    ADD COLUMN updated_by_user_id BIGINT NULL,
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;

-- Bill payments audit + soft delete
ALTER TABLE bill_payments
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL,
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;

UPDATE bill_payments
SET updated_at = COALESCE(updated_at, created_at, NOW(6))
WHERE updated_at IS NULL;

-- Customer advance audit
ALTER TABLE customer_advance
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL,
    ADD COLUMN updated_at DATETIME(6) NULL;

UPDATE customer_advance
SET updated_at = COALESCE(updated_at, created_at, NOW(6))
WHERE updated_at IS NULL;

-- Expenses audit + soft delete
ALTER TABLE expenses
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL,
    ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;

-- Customer dedup key (phone per location)
CREATE UNIQUE INDEX uk_customers_phone_location ON customers (phone, location);

-- Performance indexes
CREATE INDEX idx_bill_payments_payment_date_mode ON bill_payments (payment_date, payment_mode);
CREATE INDEX idx_bills_gst_created_at_location ON bills_gst (created_at, location);
CREATE INDEX idx_bills_non_gst_created_at_location ON bills_non_gst (created_at, location);
CREATE INDEX idx_customers_phone_location ON customers (phone, location);
