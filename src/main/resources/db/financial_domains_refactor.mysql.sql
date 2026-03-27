-- Financial domains refactor (safe, backward-compatible)
-- Phase 1 schema: add expense metadata + create client_transactions
-- Re-runnable in MySQL 8+

DELIMITER $$
DROP PROCEDURE IF EXISTS ensure_column $$
CREATE PROCEDURE ensure_column(IN p_table_name VARCHAR(128), IN p_column_name VARCHAR(128), IN p_column_def TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
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

DROP PROCEDURE IF EXISTS ensure_index $$
CREATE PROCEDURE ensure_index(IN p_table_name VARCHAR(128), IN p_index_name VARCHAR(128), IN p_index_columns TEXT, IN p_unique TINYINT)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name
  ) THEN
    SET @sql = CONCAT(
      'CREATE ',
      IF(p_unique = 1, 'UNIQUE ', ''),
      'INDEX `', p_index_name, '` ON `', p_table_name, '` (', p_index_columns, ')'
    );
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$
DELIMITER ;

-- expenses: new financial-domain metadata (non-destructive)
CALL ensure_column('expenses', 'expense_category', 'expense_category VARCHAR(32) NULL');
CALL ensure_column('expenses', 'reference_type', 'reference_type VARCHAR(32) NULL');
CALL ensure_column('expenses', 'reference_id', 'reference_id VARCHAR(64) NULL');

-- optional performance indexes for new columns
CALL ensure_index('expenses', 'idx_expenses_ref_type_id', 'reference_type, reference_id', 0);
CALL ensure_index('expenses', 'idx_expenses_category_date', 'expense_category, date', 0);

-- backfill defaults for old rows where metadata absent
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

-- new table: client_transactions
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

