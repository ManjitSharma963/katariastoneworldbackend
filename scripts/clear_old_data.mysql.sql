-- =============================================================================
-- Kataria Stone World — clear old / test data (MySQL 8+)
-- =============================================================================
--
-- ⚠️  READ BEFORE RUNNING
-- 1. BACK UP FIRST (replace user/password/database):
--      mysqldump -u root -p katariadb_dev > backup_katariadb_dev_%DATE%.sql
-- 2. Stop the Spring Boot API while this script runs.
-- 3. Pick ONE mode below (A, B, or C) by uncommenting the CALL at the bottom.
-- 4. Default target DB is katariadb_dev (local). Change @target_db if needed.
--
-- Databases in this project:
--   katariadb_dev  → application-local.properties (typical dev)
--   katariadb      → application.properties (production-like)
--
-- =============================================================================

SET @target_db = 'katariadb_dev';
-- SET @target_db = 'katariadb';

SET @cutoff_date = '2025-01-01';  -- used only in MODE D (date-based delete)

USE katariadb_dev;  -- change manually if your client does not honor @target_db

-- -----------------------------------------------------------------------------
-- Helpers: truncate only if table exists (safe across schema versions)
-- -----------------------------------------------------------------------------
DELIMITER $$

DROP PROCEDURE IF EXISTS truncate_if_exists $$
CREATE PROCEDURE truncate_if_exists(IN p_table VARCHAR(128))
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = p_table
  ) THEN
    SET @sql = CONCAT('TRUNCATE TABLE `', p_table, '`');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END $$

DROP PROCEDURE IF EXISTS delete_bills_before $$
CREATE PROCEDURE delete_bills_before(IN p_cutoff DATE)
BEGIN
  -- GST bills + children
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'bill_items_gst') THEN
    DELETE bi FROM bill_items_gst bi
    INNER JOIN bills_gst b ON b.id = bi.bill_id
    WHERE DATE(b.created_at) < p_cutoff;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'bills_gst') THEN
    DELETE FROM bills_gst WHERE DATE(created_at) < p_cutoff;
  END IF;

  -- Non-GST bills + children
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'bill_items_non_gst') THEN
    DELETE bi FROM bill_items_non_gst bi
    INNER JOIN bills_non_gst b ON b.id = bi.bill_id
    WHERE DATE(b.created_at) < p_cutoff;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'bills_non_gst') THEN
    DELETE FROM bills_non_gst WHERE DATE(created_at) < p_cutoff;
  END IF;
END $$

DROP PROCEDURE IF EXISTS clear_transactional_data $$
CREATE PROCEDURE clear_transactional_data()
BEGIN
  SET FOREIGN_KEY_CHECKS = 0;

  -- Bill audit / revision
  CALL truncate_if_exists('bill_inventory_return_lines');
  CALL truncate_if_exists('bill_inventory_returns');
  CALL truncate_if_exists('bill_events');
  CALL truncate_if_exists('bill_versions');
  CALL truncate_if_exists('bill_cancellation_logs');

  -- Bills
  CALL truncate_if_exists('bill_items_gst');
  CALL truncate_if_exists('bill_items_non_gst');
  CALL truncate_if_exists('bill_payments');
  CALL truncate_if_exists('bills_gst');
  CALL truncate_if_exists('bills_non_gst');

  -- Customer money
  CALL truncate_if_exists('customer_advance_usage');
  CALL truncate_if_exists('customer_advance');
  CALL truncate_if_exists('customer_wallet_transactions');

  -- Ledgers & cash flow
  CALL truncate_if_exists('receivable_ledger_entries');
  CALL truncate_if_exists('transactions');

  -- Inventory movement (keeps product rows; stock may need reset — see end)
  CALL truncate_if_exists('inventory_reservations');
  CALL truncate_if_exists('inventory_transactions');
  CALL truncate_if_exists('inventory_history');
  CALL truncate_if_exists('product_change_history');

  -- Ops / finance
  CALL truncate_if_exists('expenses');
  CALL truncate_if_exists('daily_closing_snapshot');
  CALL truncate_if_exists('client_purchase_payments');
  CALL truncate_if_exists('client_purchases');
  CALL truncate_if_exists('client_transactions');
  CALL truncate_if_exists('client_supplier_accounts');
  CALL truncate_if_exists('employee_payroll_ledger');

  -- Loans
  CALL truncate_if_exists('loan_ledger_entries');
  CALL truncate_if_exists('loan_borrowers');
  CALL truncate_if_exists('loan_lenders');

  SET FOREIGN_KEY_CHECKS = 1;
END $$

DROP PROCEDURE IF EXISTS clear_master_and_catalog $$
CREATE PROCEDURE clear_master_and_catalog()
BEGIN
  SET FOREIGN_KEY_CHECKS = 0;

  CALL truncate_if_exists('customers');
  CALL truncate_if_exists('products');
  CALL truncate_if_exists('suppliers');
  CALL truncate_if_exists('dealers');
  CALL truncate_if_exists('sellers');
  CALL truncate_if_exists('employees');
  -- categories, users, state_gst_master, hero_slides, website_product kept intentionally

  SET FOREIGN_KEY_CHECKS = 1;
END $$

DROP PROCEDURE IF EXISTS reset_product_stock_cache $$
CREATE PROCEDURE reset_product_stock_cache()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'products'
  ) THEN
    UPDATE products SET total_sqft_stock = 0;
  END IF;
END $$

DELIMITER ;

-- =============================================================================
-- MODE A — Transactional data only (RECOMMENDED for dev reset)
-- Clears: bills, payments, ledgers, inventory movements, expenses, loans, etc.
-- Keeps: users, products, customers, categories, website content
-- =============================================================================
-- CALL clear_transactional_data();
-- CALL reset_product_stock_cache();   -- optional: zero all on-hand stock after clearing movements

-- =============================================================================
-- MODE B — MODE A + customers & catalog
-- Clears transactional data AND customers/products/suppliers/dealers/employees
-- Keeps: users, categories, state_gst_master, hero_slides, website_product
-- =============================================================================
-- CALL clear_transactional_data();
-- CALL clear_master_and_catalog();

-- =============================================================================
-- MODE C — Full dev wipe (almost everything except login + reference)
-- Uncomment BOTH blocks below if you want a near-empty dev DB
-- =============================================================================
-- CALL clear_transactional_data();
-- CALL clear_master_and_catalog();
-- TRUNCATE TABLE categories;        -- only if you want zero categories too
-- TRUNCATE TABLE hero_slides;
-- TRUNCATE TABLE website_product;
-- TRUNCATE TABLE state_gst_master;

-- =============================================================================
-- MODE D — Delete only bills OLDER than @cutoff_date (partial cleanup)
-- Does NOT clear transactions/ledger automatically — use only if you know
-- orphaned ledger rows are acceptable, or clean those tables separately.
-- =============================================================================
-- CALL delete_bills_before(@cutoff_date);

-- =============================================================================
-- After run: row counts (sanity check)
-- =============================================================================
SELECT 'bills_gst' AS tbl, COUNT(*) AS cnt FROM bills_gst
UNION ALL SELECT 'bills_non_gst', COUNT(*) FROM bills_non_gst
UNION ALL SELECT 'transactions', COUNT(*) FROM transactions
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'customers', COUNT(*) FROM customers
UNION ALL SELECT 'users', COUNT(*) FROM users;

-- Cleanup procedures (optional)
-- DROP PROCEDURE IF EXISTS truncate_if_exists;
-- DROP PROCEDURE IF EXISTS delete_bills_before;
-- DROP PROCEDURE IF EXISTS clear_transactional_data;
-- DROP PROCEDURE IF EXISTS clear_master_and_catalog;
-- DROP PROCEDURE IF EXISTS reset_product_stock_cache;
