-- Link ledger rows to the originating bill_payments row (1:1 for bill collection lines).
-- Safe additive migration; no new tables.

SET @db := DATABASE();

SET @col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE table_schema = @db
      AND table_name = 'transactions'
      AND column_name = 'bill_payment_id'
);

SET @sql := IF(
    @col = 0,
    'ALTER TABLE transactions ADD COLUMN bill_payment_id BIGINT UNSIGNED NULL COMMENT ''Logical FK to bill_payments.id'' AFTER reference_id',
    'SELECT ''transactions.bill_payment_id already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE table_schema = @db
      AND table_name = 'transactions'
      AND index_name = 'idx_transactions_bill_payment_id'
);

SET @sql2 := IF(
    @idx = 0,
    'CREATE INDEX idx_transactions_bill_payment_id ON transactions (bill_payment_id)',
    'SELECT ''idx_transactions_bill_payment_id already exists'' AS msg2'
);
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
