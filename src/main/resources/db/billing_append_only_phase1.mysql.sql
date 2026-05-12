-- Phase 1 (additive): bill versioning + append-only linkage metadata.
-- Safe to run before service logic switch. No destructive changes.

ALTER TABLE bills_gst
    ADD COLUMN current_version_no INT NOT NULL DEFAULT 1,
    ADD COLUMN original_bill_id BIGINT NULL,
    ADD COLUMN is_latest_version BIT NOT NULL DEFAULT b'1',
    ADD COLUMN bill_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE bills_non_gst
    ADD COLUMN current_version_no INT NOT NULL DEFAULT 1,
    ADD COLUMN original_bill_id BIGINT NULL,
    ADD COLUMN is_latest_version BIT NOT NULL DEFAULT b'1',
    ADD COLUMN bill_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

CREATE TABLE IF NOT EXISTS bill_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    bill_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    action_type VARCHAR(50),
    previous_version_id BIGINT NULL,
    snapshot_json JSON,
    change_summary JSON,
    edit_reason VARCHAR(500),
    created_by BIGINT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_bill_versions_bill_ver (bill_id, version_no),
    INDEX idx_bill_versions_prev (previous_version_id),
    INDEX idx_bill_versions_created (created_at)
);

ALTER TABLE bill_items_gst
    ADD COLUMN version_no INT NOT NULL DEFAULT 1,
    ADD COLUMN is_deleted BIT NOT NULL DEFAULT b'0';

ALTER TABLE bill_items_non_gst
    ADD COLUMN version_no INT NOT NULL DEFAULT 1,
    ADD COLUMN is_deleted BIT NOT NULL DEFAULT b'0';

ALTER TABLE bill_payments
    ADD COLUMN bill_version_id BIGINT NULL,
    ADD COLUMN reversal_of_id BIGINT NULL,
    ADD COLUMN payment_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    ADD INDEX idx_bill_payments_version (bill_version_id),
    ADD INDEX idx_bill_payments_reversal (reversal_of_id);

ALTER TABLE inventory_transactions
    ADD COLUMN bill_version_id BIGINT NULL,
    ADD COLUMN reversal_of_id BIGINT NULL,
    ADD COLUMN linked_group_id VARCHAR(64) NULL,
    ADD COLUMN source_action VARCHAR(50) NULL,
    ADD INDEX idx_inv_txn_version (bill_version_id),
    ADD INDEX idx_inv_txn_reversal (reversal_of_id),
    ADD INDEX idx_inv_txn_group (linked_group_id);

ALTER TABLE transactions
    ADD COLUMN txn_type VARCHAR(50) NULL,
    ADD COLUMN bill_version_id BIGINT NULL,
    ADD COLUMN reversal_of_id BIGINT NULL,
    ADD COLUMN linked_group_id VARCHAR(64) NULL,
    ADD COLUMN metadata_json JSON NULL,
    ADD INDEX idx_txn_version (bill_version_id),
    ADD INDEX idx_txn_reversal (reversal_of_id),
    ADD INDEX idx_txn_group (linked_group_id);

ALTER TABLE customer_wallet_transactions
    ADD COLUMN reversal_of_id BIGINT NULL,
    ADD COLUMN bill_version_id BIGINT NULL,
    ADD COLUMN linked_group_id VARCHAR(64) NULL,
    ADD INDEX idx_wallet_reversal (reversal_of_id),
    ADD INDEX idx_wallet_version (bill_version_id),
    ADD INDEX idx_wallet_group (linked_group_id);

