-- =============================================================================
-- KATARIA STONE WORLD — CONSOLIDATED MySQL DATABASE CHANGES
-- =============================================================================
-- Purpose: Single script listing all migrations from src/main/resources/db/,
--          database_migration_*.sql, and scripts/ (see section headers).
--
-- WHY THIS SHOULD NOT BREAK EXISTING BEHAVIOUR
--   • Additive only: new tables, new columns (with defaults), indexes, widened
--     VARCHARs — no DROP TABLE / destructive deletes in this file.
--   • Existing rows keep working; new columns use NULL, 0, or sensible defaults.
--   • Your app should already match this schema; applying migrations *aligns* DB
--     with the code (fixes errors like payment_status truncation).
--
-- ONE-SHOT RUN (pick one)
--
--   A) PowerShell helper (backup + apply) — recommended:
--        cd katariastoneworldbackend\src\main\resources\db
--        .\run-consolidated-migration.ps1 -Database katariastoneworld -User root
--
--   B) MySQL client — select DB with -D (do not rely on USE inside file):
--        mysqldump -u USER -p DATABASE > backup_before_migrate.sql
--        mysql -u USER -p -D DATABASE --default-character-set=utf8mb4 ^
--          < ALL_MIGRATIONS_CONSOLIDATED.mysql.sql
--
--   C) MySQL Workbench: File → Open SQL Script → choose this file → set default
--      schema in toolbar → Execute lightning bolt.
--
-- IF THE SCRIPT STOPS MIDWAY
--   "Duplicate column" / "Duplicate key" / "already exists" means that part was
--   applied earlier. Remove or comment out only those statements, then run again
--   (or run the remaining sections manually). Do not use mysql --force in
--   production unless you review every skipped error.
--
-- SPECIAL CASE
--   CREATE UNIQUE INDEX uk_customers_phone_location — fails if duplicate
--   (phone, location) rows exist. Fix duplicates first, or comment out that line.
--
-- MySQL 8.0+ recommended.
-- =============================================================================

SET NAMES utf8mb4;

-- Helpers to make this script re-runnable safely.
DROP PROCEDURE IF EXISTS ensure_column;
DROP PROCEDURE IF EXISTS ensure_index;
DELIMITER $$
CREATE PROCEDURE ensure_column(
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
END$$

CREATE PROCEDURE ensure_index(
    IN p_table_name VARCHAR(128),
    IN p_index_name VARCHAR(128),
    IN p_index_columns TEXT,
    IN p_unique TINYINT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM INFORMATION_SCHEMA.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        IF p_unique = 1 THEN
            SET @sql = CONCAT('CREATE UNIQUE INDEX `', p_index_name, '` ON `', p_table_name, '` (', p_index_columns, ')');
        ELSE
            SET @sql = CONCAT('CREATE INDEX `', p_index_name, '` ON `', p_table_name, '` (', p_index_columns, ')');
        END IF;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- =============================================================================
-- SECTION A — Legacy / base (database_migration_add_location.sql)
-- =============================================================================
-- Adds location to employees, expenses, products, customers. Skip if columns exist.

-- ALTER TABLE employees
--     ADD COLUMN location VARCHAR(50) NOT NULL DEFAULT 'Bhondsi' AFTER joining_date;
-- ALTER TABLE expenses
--     ADD COLUMN location VARCHAR(50) NOT NULL DEFAULT 'Bhondsi' AFTER settled;
-- ALTER TABLE products
--     ADD COLUMN location VARCHAR(50) NOT NULL DEFAULT 'Bhondsi' AFTER meta_keywords;
-- ALTER TABLE customers
--     ADD COLUMN location VARCHAR(50) NULL AFTER email;

-- UPDATE employees SET location = 'Bhondsi' WHERE location IS NULL OR location = '';
-- UPDATE expenses SET location = 'Bhondsi' WHERE location IS NULL OR location = '';
-- UPDATE products SET location = 'Bhondsi' WHERE location IS NULL OR location = '';
-- UPDATE customers SET location = 'Bhondsi' WHERE location IS NULL OR location = '';

-- CREATE INDEX idx_employees_location ON employees(location);
-- CREATE INDEX idx_expenses_location ON expenses(location);
-- CREATE INDEX idx_products_location ON products(location);
-- CREATE INDEX idx_customers_location ON customers(location);

-- =============================================================================
-- SECTION B — Product extra fields (database_migration_add_inventory_fields.sql)
-- =============================================================================
-- Skip if columns already exist.

-- ALTER TABLE products
--     ADD COLUMN labour_charges DECIMAL(10, 2) NULL COMMENT 'Labour charges for the product';
-- ALTER TABLE products
--     ADD COLUMN rto_fees DECIMAL(10, 2) NULL COMMENT 'RTO fees for the product';
-- ALTER TABLE products
--     ADD COLUMN damage_expenses DECIMAL(10, 2) NULL COMMENT 'Damage expenses for the product';
-- ALTER TABLE products
--     ADD COLUMN others_expenses DECIMAL(10, 2) NULL COMMENT 'Other expenses for the product';
-- ALTER TABLE products
--     ADD COLUMN price_per_sqft_after DECIMAL(10, 2) NULL COMMENT 'Price per sqft after all expenses';

-- =============================================================================
-- SECTION C — Data backfill (database_migration_update_existing_data.sql)
-- =============================================================================
-- Optional; often redundant if SECTION A UPDATEs were run.

-- UPDATE employees SET location = 'Bhondsi' WHERE location IS NULL OR location = '';
-- UPDATE expenses SET location = 'Bhondsi' WHERE location IS NULL OR location = '';
-- UPDATE products SET location = 'Bhondsi' WHERE location IS NULL OR location = '';
-- UPDATE customers SET location = 'Bhondsi' WHERE location IS NULL OR location = '';

-- =============================================================================
-- SECTION D — suppliers_dealers.mysql.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS suppliers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    contact_number VARCHAR(50) NULL,
    address VARCHAR(500) NULL,
    location VARCHAR(50) NOT NULL COMMENT 'Branch scope — matches JWT / products.location',
    created_at DATETIME(6) NOT NULL,
    INDEX idx_suppliers_location (location),
    INDEX idx_suppliers_name (name)
);

CREATE TABLE IF NOT EXISTS dealers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    contact_number VARCHAR(50) NULL,
    address VARCHAR(500) NULL,
    location VARCHAR(50) NOT NULL COMMENT 'Branch scope — matches JWT / products.location',
    created_at DATETIME(6) NOT NULL,
    INDEX idx_dealers_location (location),
    INDEX idx_dealers_name (name)
);

CALL ensure_column('products', 'supplier_id', 'supplier_id BIGINT NULL');
CALL ensure_column('products', 'dealer_id', 'dealer_id BIGINT NULL');

-- Optional FKs (uncomment if your policies allow):
-- ALTER TABLE products
--     ADD CONSTRAINT fk_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
--     ADD CONSTRAINT fk_products_dealer FOREIGN KEY (dealer_id) REFERENCES dealers (id);

-- =============================================================================
-- SECTION E — bills_location_isolation.mysql.sql
-- =============================================================================

CALL ensure_column('bills_gst', 'location', 'location VARCHAR(50) NULL');
CALL ensure_column('bills_non_gst', 'location', 'location VARCHAR(50) NULL');

CALL ensure_index('bills_gst', 'idx_bills_gst_location', 'location', 0);
CALL ensure_index('bills_non_gst', 'idx_bills_non_gst_location', 'location', 0);

-- Optional backfill:
-- UPDATE bills_gst b JOIN customers c ON c.id = b.customer_id SET b.location = c.location WHERE b.location IS NULL;
-- UPDATE bills_non_gst b JOIN customers c ON c.id = b.customer_id SET b.location = c.location WHERE b.location IS NULL;

-- =============================================================================
-- SECTION F — customers_notes.mysql.sql
-- =============================================================================

CALL ensure_column('customers', 'notes', 'notes TEXT NULL');

-- =============================================================================
-- SECTION G — bills_payment_method_varchar512.mysql.sql
-- =============================================================================

ALTER TABLE bills_non_gst
    MODIFY COLUMN payment_method VARCHAR(512) NULL;

ALTER TABLE bills_gst
    MODIFY COLUMN payment_method VARCHAR(512) NULL;

-- =============================================================================
-- SECTION H — bill_payments.mysql.sql (table + legacy backfill)
-- =============================================================================

CREATE TABLE IF NOT EXISTS bill_payments (
    payment_id BIGINT NOT NULL AUTO_INCREMENT,
    bill_kind VARCHAR(16) NOT NULL,
    bill_id BIGINT NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    payment_mode VARCHAR(32) NOT NULL,
    payment_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (payment_id),
    KEY idx_bill_payments_bill (bill_kind, bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO bill_payments (bill_kind, bill_id, amount, payment_mode, payment_date, created_at, is_deleted)
SELECT
    'GST' AS bill_kind,
    g.id AS bill_id,
    g.total_amount AS amount,
    CASE
        WHEN UPPER(REPLACE(REPLACE(TRIM(g.payment_method), '-', ''), ' ', '_')) LIKE '%CASH%' THEN 'CASH'
        WHEN UPPER(TRIM(g.payment_method)) LIKE '%UPI%' THEN 'UPI'
        WHEN UPPER(TRIM(g.payment_method)) LIKE '%CHEQUE%' OR UPPER(TRIM(g.payment_method)) LIKE '%CHECK%' THEN 'CHEQUE'
        ELSE 'BANK_TRANSFER'
    END AS payment_mode,
    g.bill_date AS payment_date,
    g.created_at AS created_at,
    0 AS is_deleted
FROM bills_gst g
WHERE g.payment_method IS NOT NULL AND TRIM(g.payment_method) <> ''
  AND NOT EXISTS (
        SELECT 1 FROM bill_payments p
        WHERE p.bill_kind = 'GST' AND p.bill_id = g.id
    );

INSERT INTO bill_payments (bill_kind, bill_id, amount, payment_mode, payment_date, created_at, is_deleted)
SELECT
    'NON_GST' AS bill_kind,
    n.id AS bill_id,
    n.total_amount AS amount,
    CASE
        WHEN UPPER(REPLACE(REPLACE(TRIM(n.payment_method), '-', ''), ' ', '_')) LIKE '%CASH%' THEN 'CASH'
        WHEN UPPER(TRIM(n.payment_method)) LIKE '%UPI%' THEN 'UPI'
        WHEN UPPER(TRIM(n.payment_method)) LIKE '%CHEQUE%' OR UPPER(TRIM(n.payment_method)) LIKE '%CHECK%' THEN 'CHEQUE'
        ELSE 'BANK_TRANSFER'
    END AS payment_mode,
    n.bill_date AS payment_date,
    n.created_at AS created_at,
    0 AS is_deleted
FROM bills_non_gst n
WHERE n.payment_method IS NOT NULL AND TRIM(n.payment_method) <> ''
  AND NOT EXISTS (
        SELECT 1 FROM bill_payments p
        WHERE p.bill_kind = 'NON_GST' AND p.bill_id = n.id
    );

-- =============================================================================
-- SECTION I — bills_payment_status_expand.mysql.sql
-- =============================================================================
-- Fixes: Data truncated for column 'payment_status'

ALTER TABLE bills_gst
  MODIFY COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE bills_non_gst
  MODIFY COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

UPDATE bills_gst
SET payment_status = 'PENDING'
WHERE payment_status IS NULL OR payment_status = '' OR payment_status NOT IN ('DUE','PENDING','PARTIAL','PAID','CANCELLED');

UPDATE bills_non_gst
SET payment_status = 'PENDING'
WHERE payment_status IS NULL OR payment_status = '' OR payment_status NOT IN ('DUE','PENDING','PARTIAL','PAID','CANCELLED');

-- =============================================================================
-- SECTION J — bills_paid_amount.mysql.sql (requires bill_payments for backfill)
-- =============================================================================

CALL ensure_column('bills_gst', 'paid_amount', 'paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER total_amount');
CALL ensure_column('bills_non_gst', 'paid_amount', 'paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER total_amount');

UPDATE bills_gst b
LEFT JOIN (
  SELECT bill_id, SUM(amount) AS paid
  FROM bill_payments
  WHERE bill_kind = 'GST'
  GROUP BY bill_id
) p ON p.bill_id = b.id
SET b.paid_amount = COALESCE(p.paid, 0.00);

UPDATE bills_non_gst b
LEFT JOIN (
  SELECT bill_id, SUM(amount) AS paid
  FROM bill_payments
  WHERE bill_kind = 'NON_GST'
  GROUP BY bill_id
) p ON p.bill_id = b.id
SET b.paid_amount = COALESCE(p.paid, 0.00);

-- =============================================================================
-- SECTION K — customer_advance.mysql.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS customer_advance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    remaining_amount DECIMAL(14, 2) NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_customer_advance_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CALL ensure_index('customer_advance', 'idx_customer_advance_customer', 'customer_id', 0);
CALL ensure_index('customer_advance', 'idx_customer_advance_remaining', 'customer_id, remaining_amount', 0);

CREATE TABLE IF NOT EXISTS customer_advance_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    advance_id BIGINT NOT NULL,
    bill_kind VARCHAR(16) NOT NULL COMMENT 'GST or NON_GST — matches bill_payments.bill_kind',
    bill_id BIGINT NOT NULL,
    amount_used DECIMAL(14, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_advance_usage_advance FOREIGN KEY (advance_id) REFERENCES customer_advance (id)
);

CALL ensure_index('customer_advance_usage', 'idx_advance_usage_bill', 'bill_kind, bill_id', 0);
CALL ensure_index('customer_advance_usage', 'idx_advance_usage_advance', 'advance_id', 0);

-- =============================================================================
-- SECTION L — customer_advance_payment_mode.mysql.sql
-- =============================================================================

CALL ensure_column('customer_advance', 'payment_mode', 'payment_mode VARCHAR(32) NULL AFTER description');

UPDATE customer_advance
SET payment_mode = 'CASH'
WHERE payment_mode IS NULL OR TRIM(payment_mode) = '';

-- =============================================================================
-- SECTION M — financial_ledger.mysql.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS financial_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    location VARCHAR(50) NOT NULL,
    bill_kind VARCHAR(16) NULL,
    bill_id BIGINT NULL,
    customer_id BIGINT NULL,
    payment_mode VARCHAR(32) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    in_hand_amount DECIMAL(14,2) NOT NULL DEFAULT 0.00,
    event_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_fin_ledger_source UNIQUE (source_type, source_id)
);

CALL ensure_index('financial_ledger', 'idx_fin_ledger_loc_date', 'location, event_date', 0);
CALL ensure_index('financial_ledger', 'idx_fin_ledger_mode_date', 'payment_mode, event_date', 0);

-- =============================================================================
-- SECTION N — audit_softdelete_indexes.mysql.sql
-- =============================================================================
-- bill_payments.updated_at is NULLABLE (avoids strict-mode issues on backfill).

CALL ensure_column('bills_gst', 'updated_by_user_id', 'updated_by_user_id BIGINT NULL');
CALL ensure_column('bills_gst', 'is_deleted', 'is_deleted TINYINT(1) NOT NULL DEFAULT 0');
CALL ensure_column('bills_non_gst', 'updated_by_user_id', 'updated_by_user_id BIGINT NULL');
CALL ensure_column('bills_non_gst', 'is_deleted', 'is_deleted TINYINT(1) NOT NULL DEFAULT 0');

CALL ensure_column('bill_payments', 'updated_at', 'updated_at DATETIME(6) NULL');
CALL ensure_column('bill_payments', 'created_by', 'created_by BIGINT NULL');
CALL ensure_column('bill_payments', 'updated_by', 'updated_by BIGINT NULL');
CALL ensure_column('bill_payments', 'is_deleted', 'is_deleted TINYINT(1) NOT NULL DEFAULT 0');

UPDATE bill_payments
SET updated_at = COALESCE(updated_at, created_at, NOW(6))
WHERE updated_at IS NULL;

CALL ensure_column('customer_advance', 'created_by', 'created_by BIGINT NULL');
CALL ensure_column('customer_advance', 'updated_by', 'updated_by BIGINT NULL');
CALL ensure_column('customer_advance', 'updated_at', 'updated_at DATETIME(6) NULL');

UPDATE customer_advance
SET updated_at = COALESCE(updated_at, created_at, NOW(6))
WHERE updated_at IS NULL;

CALL ensure_column('expenses', 'created_by', 'created_by BIGINT NULL');
CALL ensure_column('expenses', 'updated_by', 'updated_by BIGINT NULL');
CALL ensure_column('expenses', 'is_deleted', 'is_deleted TINYINT(1) NOT NULL DEFAULT 0');

-- May fail if duplicate (phone, location) — fix data first:
CALL ensure_index('customers', 'uk_customers_phone_location', 'phone, location', 1);

CALL ensure_index('bill_payments', 'idx_bill_payments_payment_date_mode', 'payment_date, payment_mode', 0);
CALL ensure_index('bills_gst', 'idx_bills_gst_created_at_location', 'created_at, location', 0);
CALL ensure_index('bills_non_gst', 'idx_bills_non_gst_created_at_location', 'created_at, location', 0);
CALL ensure_index('customers', 'idx_customers_phone_location', 'phone, location', 0);

-- =============================================================================
-- SECTION O — product_change_history.mysql.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS product_change_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    previous_snapshot LONGTEXT NULL COMMENT 'JSON: ProductResponseDTO before update',
    new_snapshot LONGTEXT NULL COMMENT 'JSON: ProductResponseDTO after update',
    notes VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_change_history_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON DELETE CASCADE,
    KEY idx_product_change_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- SECTION P — inventory_history.mysql.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS inventory_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL COMMENT 'ADD, SALE, UPDATE, ADJUST',
    quantity_changed DECIMAL(12, 2) NOT NULL,
    previous_quantity DECIMAL(12, 2) NOT NULL,
    new_quantity DECIMAL(12, 2) NOT NULL,
    reference_id BIGINT NULL COMMENT 'e.g. bill id when applicable',
    notes VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_inventory_history_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON DELETE CASCADE,
    KEY idx_inventory_history_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- SECTION P2 — employee_payroll_ledger.mysql.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS employee_payroll_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    location VARCHAR(50) NOT NULL,
    event_type VARCHAR(32) NOT NULL COMMENT 'ADVANCE_GIVEN, ADVANCE_APPLIED, SALARY_CASH_PAID',
    amount DECIMAL(14,2) NOT NULL,
    payment_mode VARCHAR(32) NULL COMMENT 'CASH, UPI, BANK_TRANSFER, CHEQUE, OTHER',
    event_date DATE NOT NULL,
    month VARCHAR(7) NOT NULL COMMENT 'YYYY-MM derived from event_date',
    notes VARCHAR(512) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    INDEX idx_emp_payroll_emp_month (employee_id, month),
    INDEX idx_emp_payroll_loc_date (location, event_date),
    INDEX idx_emp_payroll_emp_date (employee_id, event_date)
);

-- =============================================================================
-- SECTION Q — scripts/add-hsn-number-to-bill-items-gst.sql (idempotent)
-- =============================================================================

SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bill_items_gst' AND COLUMN_NAME = 'hsn_number'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE bill_items_gst ADD COLUMN hsn_number VARCHAR(20) NULL COMMENT ''from product inventory at billing'' AFTER item_total_price',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =============================================================================
-- SECTION R — scripts/fix-customers-unique-constraint.sql (OPTIONAL)
-- =============================================================================
-- Only if you must drop a legacy UNIQUE(phone) and rely on (location, phone).
-- Inspect SHOW INDEX FROM customers; before running.

-- ALTER TABLE customers DROP INDEX UK_m3iom37efaxd5eucmxjqqcbe9;
-- ALTER TABLE customers ADD UNIQUE KEY uk_customer_location_phone (location, phone);

-- =============================================================================
-- SECTION S — financial_domains_refactor.mysql.sql
-- =============================================================================

CALL ensure_column('expenses', 'expense_category', 'expense_category VARCHAR(32) NULL');
CALL ensure_column('expenses', 'reference_type', 'reference_type VARCHAR(32) NULL');
CALL ensure_column('expenses', 'reference_id', 'reference_id VARCHAR(64) NULL');
CALL ensure_index('expenses', 'idx_expenses_ref_type_id', 'reference_type, reference_id', 0);
CALL ensure_index('expenses', 'idx_expenses_category_date', 'expense_category, date', 0);

UPDATE expenses
SET expense_category = CASE
  WHEN LOWER(COALESCE(type, '')) IN ('salary', 'advance') THEN 'SALARY'
  WHEN LOWER(COALESCE(category, '')) IN ('inventory', 'stock', 'purchase', 'client') THEN 'INVENTORY'
  WHEN LOWER(COALESCE(type, '')) = 'daily' THEN 'DAILY'
  ELSE 'MISC'
END
WHERE expense_category IS NULL;

UPDATE expenses
SET reference_type = 'DIRECT'
WHERE reference_type IS NULL;

CREATE TABLE IF NOT EXISTS client_transactions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  client_id VARCHAR(128) NOT NULL,
  transaction_type VARCHAR(32) NOT NULL COMMENT 'PAYMENT_IN, PAYMENT_OUT, PURCHASE',
  amount DECIMAL(14,2) NOT NULL,
  payment_mode VARCHAR(32) NOT NULL COMMENT 'CASH, UPI, BANK_TRANSFER, CHEQUE, OTHER',
  transaction_date DATE NOT NULL,
  notes VARCHAR(512) NULL,
  location VARCHAR(50) NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL ensure_index('client_transactions', 'idx_client_txn_location_date', 'location, transaction_date', 0);
CALL ensure_index('client_transactions', 'idx_client_txn_client_date', 'client_id, transaction_date', 0);
CALL ensure_index('client_transactions', 'idx_client_txn_type_date', 'transaction_type, transaction_date', 0);

DROP PROCEDURE IF EXISTS ensure_column;
DROP PROCEDURE IF EXISTS ensure_index;

-- =============================================================================
-- END OF CONSOLIDATED SCRIPT
-- =============================================================================
