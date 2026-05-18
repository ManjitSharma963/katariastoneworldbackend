-- Bill revision workflow: performance indexes (additive, idempotent).
-- Run after: billing_append_only_phase1.mysql.sql, bill_events_and_version_lifecycle.mysql.sql

SET @db := DATABASE();

SET @idx := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bills_non_gst' AND INDEX_NAME = 'idx_bills_non_gst_status_date');
SET @sql := IF(@idx = 0,
    'CREATE INDEX idx_bills_non_gst_status_date ON bills_non_gst (bill_status, bill_date)',
    'SELECT ''skip idx_bills_non_gst_status_date'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bill_payments' AND INDEX_NAME = 'idx_bill_payments_bill_active');
SET @sql := IF(@idx = 0,
    'CREATE INDEX idx_bill_payments_bill_active ON bill_payments (bill_kind, bill_id, is_deleted, payment_status)',
    'SELECT ''skip idx_bill_payments_bill_active'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bill_versions' AND INDEX_NAME = 'idx_bill_versions_bill_lifecycle');
SET @sql := IF(@idx = 0,
    'CREATE INDEX idx_bill_versions_bill_lifecycle ON bill_versions (bill_id, lifecycle_status, version_no)',
    'SELECT ''skip idx_bill_versions_bill_lifecycle'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
