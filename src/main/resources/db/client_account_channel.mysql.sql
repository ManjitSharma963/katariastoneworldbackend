-- GST vs Non-GST account channel for client/supplier purchases, payments, and ledger.
-- Safe to re-run: checks information_schema before altering.

SET @db := DATABASE();

-- client_purchases
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'client_purchases' AND COLUMN_NAME = 'account_channel'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE client_purchases ADD COLUMN account_channel VARCHAR(16) NOT NULL DEFAULT ''NON_GST'' AFTER client_name',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- client_purchase_payments
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'client_purchase_payments' AND COLUMN_NAME = 'account_channel'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE client_purchase_payments ADD COLUMN account_channel VARCHAR(16) NOT NULL DEFAULT ''NON_GST'' AFTER client_id',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- client_transactions
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'client_transactions' AND COLUMN_NAME = 'account_channel'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE client_transactions ADD COLUMN account_channel VARCHAR(16) NOT NULL DEFAULT ''NON_GST'' AFTER client_id',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- client_supplier_accounts: allow GST + Non-GST rows per client
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'client_supplier_accounts' AND COLUMN_NAME = 'account_channel'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE client_supplier_accounts ADD COLUMN account_channel VARCHAR(16) NOT NULL DEFAULT ''NON_GST'' AFTER client_key',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'client_supplier_accounts' AND INDEX_NAME = 'uk_client_supplier_loc_key'
);
SET @sql := IF(@idx_exists > 0,
  'ALTER TABLE client_supplier_accounts DROP INDEX uk_client_supplier_loc_key',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'client_supplier_accounts' AND INDEX_NAME = 'uk_client_supplier_loc_key_channel'
);
SET @sql := IF(@idx_exists = 0,
  'ALTER TABLE client_supplier_accounts ADD UNIQUE KEY uk_client_supplier_loc_key_channel (location, client_key, account_channel)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Legacy data: everything before GST/Non-GST split is Non-GST (run once; idempotent).
UPDATE client_purchases SET account_channel = 'NON_GST';
UPDATE client_purchase_payments p
INNER JOIN client_purchases cp ON cp.id = p.client_purchase_id
SET p.account_channel = cp.account_channel;
UPDATE client_transactions SET account_channel = 'NON_GST';
UPDATE client_transactions SET notes = REPLACE(notes, '[GST]', '[Non-GST]') WHERE notes LIKE '[GST]%';
DELETE g FROM client_supplier_accounts g
INNER JOIN client_supplier_accounts n
  ON g.location = n.location AND g.client_key = n.client_key
 AND n.account_channel = 'NON_GST' AND g.account_channel = 'GST';
UPDATE client_supplier_accounts SET account_channel = 'NON_GST'
WHERE account_channel IS NULL OR account_channel <> 'NON_GST';
