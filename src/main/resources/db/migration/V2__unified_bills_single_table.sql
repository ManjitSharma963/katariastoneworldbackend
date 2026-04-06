-- =============================================================================
-- V2: Unified bills + bill_line_items; DROP bills_gst, bills_non_gst, old items.
-- BACK UP FIRST. Maintenance window recommended.
-- Requires MySQL 8+ (window functions). Test on a copy of production.
--
-- financial_ledger: Section A adds any missing columns (idempotent) so remaps
-- and the JPA entity match; widen/modify steps are one-shot (safe if already OK).
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- A) financial_ledger — align with FinancialLedgerEntry (idempotent adds)
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS v2_ensure_column;
DELIMITER $$
CREATE PROCEDURE v2_ensure_column(
    IN p_table_name VARCHAR(128),
    IN p_column_name VARCHAR(128),
    IN p_column_def TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END $$
DELIMITER ;

CALL v2_ensure_column('financial_ledger', 'event_type', 'event_type VARCHAR(32) NOT NULL DEFAULT ''BILL_PAYMENT''');
CALL v2_ensure_column('financial_ledger', 'entry_type', 'entry_type VARCHAR(16) NOT NULL DEFAULT ''CREDIT''');
CALL v2_ensure_column('financial_ledger', 'reference_type', 'reference_type VARCHAR(32) NULL');
CALL v2_ensure_column('financial_ledger', 'reference_id', 'reference_id VARCHAR(64) NULL');
CALL v2_ensure_column('financial_ledger', 'bill_kind', 'bill_kind VARCHAR(16) NULL');
CALL v2_ensure_column('financial_ledger', 'bill_id', 'bill_id BIGINT NULL');
CALL v2_ensure_column('financial_ledger', 'customer_id', 'customer_id BIGINT NULL');
CALL v2_ensure_column('financial_ledger', 'in_hand_amount', 'in_hand_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00');
CALL v2_ensure_column('financial_ledger', 'created_by', 'created_by BIGINT NULL');
CALL v2_ensure_column('financial_ledger', 'updated_at', 'updated_at DATETIME(6) NULL');
CALL v2_ensure_column('financial_ledger', 'updated_by', 'updated_by BIGINT NULL');
CALL v2_ensure_column('financial_ledger', 'is_deleted', 'is_deleted TINYINT(1) NOT NULL DEFAULT 0');

DROP PROCEDURE IF EXISTS v2_ensure_column;

UPDATE financial_ledger SET entry_type = 'CREDIT' WHERE entry_type IS NULL OR TRIM(entry_type) = '';
UPDATE financial_ledger SET is_deleted = 0 WHERE is_deleted IS NULL;
UPDATE financial_ledger SET in_hand_amount = 0.00 WHERE in_hand_amount IS NULL;

ALTER TABLE financial_ledger MODIFY COLUMN source_id VARCHAR(64) NOT NULL;
ALTER TABLE financial_ledger MODIFY COLUMN payment_mode VARCHAR(32) NOT NULL;
ALTER TABLE financial_ledger MODIFY COLUMN amount DECIMAL(14,2) NOT NULL;

-- ---------------------------------------------------------------------------
-- B) New unified tables
-- ---------------------------------------------------------------------------
CREATE TABLE bills (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bill_kind ENUM('GST','NON_GST') NOT NULL,
    bill_number VARCHAR(50) NOT NULL,
    location VARCHAR(50) NULL,
    customer_id INT NOT NULL,
    bill_date DATE NOT NULL,
    total_sqft DECIMAL(14,2) NOT NULL,
    subtotal DECIMAL(14,2) NOT NULL,
    tax_rate DECIMAL(5,2) NULL,
    tax_amount DECIMAL(14,2) NULL,
    service_charge DECIMAL(14,2) NOT NULL,
    labour_charge DECIMAL(14,2) NOT NULL,
    transportation_charge DECIMAL(14,2) NOT NULL,
    other_expenses DECIMAL(14,2) NOT NULL,
    discount_amount DECIMAL(14,2) NOT NULL,
    total_amount DECIMAL(14,2) NOT NULL,
    paid_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    payment_status ENUM('DUE','PENDING','PARTIAL','PAID','CANCELLED') NOT NULL,
    payment_method VARCHAR(512) NULL,
    notes TEXT NULL,
    hsn_code VARCHAR(20) NULL,
    vehicle_no VARCHAR(50) NULL,
    delivery_address TEXT NULL,
    created_by_user_id BIGINT NULL,
    updated_by_user_id BIGINT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    _mig_kind ENUM('GST','NON_GST') NULL,
    _mig_old_id BIGINT NULL,
    UNIQUE KEY uk_bills_mig (_mig_kind, _mig_old_id),
    UNIQUE KEY uq_bills_loc_num (location, bill_number),
    KEY ix_bills_customer (customer_id),
    KEY ix_bills_loc_date (location, bill_date),
    KEY ix_bills_kind (bill_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE bill_line_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    bill_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    product_id INT NULL,
    product_name VARCHAR(200) NOT NULL,
    product_image_url VARCHAR(500) NULL,
    product_type VARCHAR(50) NULL,
    price_per_sqft DECIMAL(14,2) NOT NULL,
    sqft_ordered DECIMAL(14,2) NOT NULL,
    unit VARCHAR(50) NULL,
    item_total_price DECIMAL(14,2) NOT NULL,
    hsn_number VARCHAR(20) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_line_bill_no (bill_id, line_no),
    KEY ix_line_product (product_id),
    CONSTRAINT fk_line_bill FOREIGN KEY (bill_id) REFERENCES bills (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- C) Migrate GST bills
-- ---------------------------------------------------------------------------
INSERT INTO bills (
    bill_kind, bill_number, location, customer_id, bill_date, total_sqft, subtotal,
    tax_rate, tax_amount, service_charge, labour_charge, transportation_charge, other_expenses,
    discount_amount, total_amount, paid_amount, payment_status, payment_method, notes,
    hsn_code, vehicle_no, delivery_address, created_by_user_id, updated_by_user_id,
    is_deleted, created_at, updated_at, _mig_kind, _mig_old_id
)
SELECT
    'GST',
    g.bill_number,
    g.location,
    g.customer_id,
    g.bill_date,
    g.total_sqft,
    g.subtotal,
    g.tax_rate,
    g.tax_amount,
    g.service_charge,
    g.labour_charge,
    g.transportation_charge,
    g.other_expenses,
    g.discount_amount,
    g.total_amount,
    g.paid_amount,
    g.payment_status,
    g.payment_method,
    g.notes,
    g.hsn_code,
    g.vehicle_no,
    g.delivery_address,
    g.created_by_user_id,
    g.updated_by_user_id,
    IF(g.is_deleted IS NULL OR g.is_deleted = 0, 0, 1),
    g.created_at,
    g.updated_at,
    'GST',
    g.id
FROM bills_gst g;

-- ---------------------------------------------------------------------------
-- D) Migrate NON_GST bills
-- ---------------------------------------------------------------------------
INSERT INTO bills (
    bill_kind, bill_number, location, customer_id, bill_date, total_sqft, subtotal,
    tax_rate, tax_amount, service_charge, labour_charge, transportation_charge, other_expenses,
    discount_amount, total_amount, paid_amount, payment_status, payment_method, notes,
    hsn_code, vehicle_no, delivery_address, created_by_user_id, updated_by_user_id,
    is_deleted, created_at, updated_at, _mig_kind, _mig_old_id
)
SELECT
    'NON_GST',
    n.bill_number,
    n.location,
    n.customer_id,
    n.bill_date,
    n.total_sqft,
    n.subtotal,
    0.00,
    0.00,
    n.service_charge,
    n.labour_charge,
    n.transportation_charge,
    n.other_expenses,
    n.discount_amount,
    n.total_amount,
    n.paid_amount,
    n.payment_status,
    n.payment_method,
    n.notes,
    NULL,
    NULL,
    NULL,
    n.created_by_user_id,
    n.updated_by_user_id,
    IF(n.is_deleted IS NULL OR n.is_deleted = 0, 0, 1),
    n.created_at,
    n.updated_at,
    'NON_GST',
    n.id
FROM bills_non_gst n;

-- ---------------------------------------------------------------------------
-- E) Line items
-- ---------------------------------------------------------------------------
INSERT INTO bill_line_items (
    bill_id, line_no, product_id, product_name, product_image_url, product_type,
    price_per_sqft, sqft_ordered, unit, item_total_price, hsn_number, created_at
)
SELECT
    b.id,
    ROW_NUMBER() OVER (PARTITION BY i.bill_id ORDER BY i.id),
    i.product_id,
    i.product_name,
    i.product_image_url,
    i.product_type,
    i.price_per_sqft,
    i.sqft_ordered,
    i.unit,
    i.item_total_price,
    i.hsn_number,
    i.created_at
FROM bill_items_gst i
INNER JOIN bills b ON b._mig_kind = 'GST' AND b._mig_old_id = i.bill_id;

INSERT INTO bill_line_items (
    bill_id, line_no, product_id, product_name, product_image_url, product_type,
    price_per_sqft, sqft_ordered, unit, item_total_price, hsn_number, created_at
)
SELECT
    b.id,
    ROW_NUMBER() OVER (PARTITION BY i.bill_id ORDER BY i.id),
    i.product_id,
    i.product_name,
    i.product_image_url,
    i.product_type,
    i.price_per_sqft,
    i.sqft_ordered,
    i.unit,
    i.item_total_price,
    NULL,
    i.created_at
FROM bill_items_non_gst i
INNER JOIN bills b ON b._mig_kind = 'NON_GST' AND b._mig_old_id = i.bill_id;

-- ---------------------------------------------------------------------------
-- F) Remap child tables
-- ---------------------------------------------------------------------------
UPDATE bill_payments p
INNER JOIN bills b ON b._mig_kind = p.bill_kind AND b._mig_old_id = p.bill_id
SET p.bill_id = b.id;

UPDATE financial_ledger fl
INNER JOIN bills b ON b._mig_kind = fl.bill_kind AND b._mig_old_id = fl.bill_id
SET fl.bill_id = b.id,
    fl.reference_id = CAST(b.id AS CHAR)
WHERE fl.bill_id IS NOT NULL AND fl.bill_kind IS NOT NULL;

UPDATE customer_advance_usage u
INNER JOIN bills b ON b._mig_kind = u.bill_kind AND b._mig_old_id = u.bill_id
SET u.bill_id = b.id;

-- ---------------------------------------------------------------------------
-- G) Remove migration columns
-- ---------------------------------------------------------------------------
ALTER TABLE bills
    DROP INDEX uk_bills_mig,
    DROP COLUMN _mig_kind,
    DROP COLUMN _mig_old_id;

-- ---------------------------------------------------------------------------
-- H) Drop legacy tables
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS bill_items_gst;
DROP TABLE IF EXISTS bill_items_non_gst;
DROP TABLE IF EXISTS bills_gst;
DROP TABLE IF EXISTS bills_non_gst;

SET FOREIGN_KEY_CHECKS = 1;
