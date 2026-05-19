-- Accounting engine foundation: idempotency + reversal link (non-breaking, nullable columns).

ALTER TABLE transactions
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT 'Idempotency key; unique per location when set' AFTER id;

ALTER TABLE transactions
    ADD COLUMN reverses_transaction_id BIGINT UNSIGNED NULL
        COMMENT 'Accounting reversal: points to transactions.id being offset' AFTER reversal_of_id;

ALTER TABLE transactions
    ADD COLUMN void_reason VARCHAR(500) NULL COMMENT 'Why row was cancelled/voided' AFTER notes;

CREATE UNIQUE INDEX uk_transactions_location_request_id
    ON transactions (location, request_id);

CREATE INDEX idx_transactions_reverses_txn
    ON transactions (reverses_transaction_id);

CREATE INDEX idx_transactions_location_date_active
    ON transactions (location, transaction_date, status, is_deleted);
