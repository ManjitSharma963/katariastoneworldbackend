-- Bill audit events + version row lifecycle (additive, idempotent where possible).
-- bills_non_gst already has current_version_no, parent_bill_id, is_latest_version (see billing_append_only_phase1.mysql.sql).

SET @db := DATABASE();

-- bill_versions.lifecycle_status: ACTIVE = current head snapshot; SUPERSEDED = older row after a newer version was opened.
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bill_versions' AND COLUMN_NAME = 'lifecycle_status');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE bill_versions ADD COLUMN lifecycle_status VARCHAR(20) NULL COMMENT ''ACTIVE|SUPERSEDED''',
    'SELECT ''skip bill_versions.lifecycle_status'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS bill_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bill_kind VARCHAR(16) NOT NULL COMMENT 'GST or NON_GST',
    bill_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    bill_version_id BIGINT NULL,
    linked_group_id VARCHAR(64) NULL,
    payload_json JSON NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_bill_events_bill (bill_kind, bill_id, created_at),
    KEY idx_bill_events_version (bill_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
