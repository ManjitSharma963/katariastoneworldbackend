-- =============================================================================
-- Clean all business/transaction data — KEEP users + products only
-- Database: katariadb (see application.properties)
-- =============================================================================
--
-- BEFORE RUNNING:
--   1. Stop Spring Boot API
--   2. Backup:
--        mysqldump -u appuser -p katariadb > backup_katariadb_before_clean.sql
--   3. Run in MySQL Workbench or:
--        mysql -u appuser -p katariadb < scripts/clean_data_keep_users_products.mysql.sql
--
-- KEEPS:  users, products, categories, state_gst_master, hero_slides, website_product
-- CLEARS: bills, transactions, inventory movements, expenses, payroll, loans,
--          customers, suppliers, dealers, employees, client accounts, advances, etc.
-- =============================================================================

USE katariadb;

DELIMITER $$

DROP PROCEDURE IF EXISTS _truncate_if_exists $$
CREATE PROCEDURE _truncate_if_exists(IN p_table VARCHAR(128))
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

DROP PROCEDURE IF EXISTS _clean_keep_users_products $$
CREATE PROCEDURE _clean_keep_users_products()
BEGIN
  SET FOREIGN_KEY_CHECKS = 0;

  CALL _truncate_if_exists('bill_inventory_return_lines');
  CALL _truncate_if_exists('bill_inventory_returns');
  CALL _truncate_if_exists('bill_events');
  CALL _truncate_if_exists('bill_versions');
  CALL _truncate_if_exists('bill_cancellation_logs');
  CALL _truncate_if_exists('bill_items_gst');
  CALL _truncate_if_exists('bill_items_non_gst');
  CALL _truncate_if_exists('bill_payments');
  CALL _truncate_if_exists('bills_gst');
  CALL _truncate_if_exists('bills_non_gst');

  CALL _truncate_if_exists('customer_advance_usage');
  CALL _truncate_if_exists('customer_advance');
  CALL _truncate_if_exists('customer_wallet_transactions');
  CALL _truncate_if_exists('receivable_ledger_entries');
  CALL _truncate_if_exists('transactions');

  CALL _truncate_if_exists('inventory_reservations');
  CALL _truncate_if_exists('inventory_transactions');
  CALL _truncate_if_exists('inventory_history');
  CALL _truncate_if_exists('product_change_history');

  CALL _truncate_if_exists('expenses');
  CALL _truncate_if_exists('daily_closing_snapshot');
  CALL _truncate_if_exists('client_purchase_payments');
  CALL _truncate_if_exists('client_purchases');
  CALL _truncate_if_exists('client_transactions');
  CALL _truncate_if_exists('client_supplier_accounts');
  CALL _truncate_if_exists('employee_payroll_ledger');
  CALL _truncate_if_exists('loan_ledger_entries');
  CALL _truncate_if_exists('loan_borrowers');
  CALL _truncate_if_exists('loan_lenders');

  CALL _truncate_if_exists('customers');
  CALL _truncate_if_exists('suppliers');
  CALL _truncate_if_exists('dealers');
  CALL _truncate_if_exists('sellers');
  CALL _truncate_if_exists('employees');

  SET FOREIGN_KEY_CHECKS = 1;
END $$

DELIMITER ;

CALL _clean_keep_users_products();
DROP PROCEDURE IF EXISTS _clean_keep_users_products;
DROP PROCEDURE IF EXISTS _truncate_if_exists;

-- Optional: zero on-hand stock on product rows (movements were cleared)
UPDATE products SET total_sqft_stock = 0 WHERE 1 = 1;

-- Sanity check
SELECT 'users' AS tbl, COUNT(*) AS cnt FROM users
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'customers', COUNT(*) FROM customers
UNION ALL SELECT 'transactions', COUNT(*) FROM transactions
UNION ALL SELECT 'bills_non_gst', COUNT(*) FROM bills_non_gst
UNION ALL SELECT 'bills_gst', COUNT(*) FROM bills_gst;
