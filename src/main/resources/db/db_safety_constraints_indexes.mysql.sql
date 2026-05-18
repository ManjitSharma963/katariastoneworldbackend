-- Database safety hardening for billing + ledgers
-- Goals:
-- 1) Add/normalize audit columns (created_at, updated_at)
-- 2) Add operational indexes for hot query paths
-- 3) Keep migration idempotent and safe to re-run

SET @db := DATABASE();

DROP PROCEDURE IF EXISTS sp_db_safety_hardening;
DELIMITER $$
CREATE PROCEDURE sp_db_safety_hardening()
BEGIN

    -- ------------------------------------------------------------------
    -- bill_payments: constraints + audit + indexes
    -- ------------------------------------------------------------------

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = @db AND table_name = 'bill_payments'
    ) THEN

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bill_payments' AND column_name = 'created_at'
        ) THEN
            ALTER TABLE bill_payments
                ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bill_payments' AND column_name = 'updated_at'
        ) THEN
            ALTER TABLE bill_payments
                ADD COLUMN updated_at DATETIME(6) NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bill_payments' AND column_name = 'is_deleted'
        ) THEN
            ALTER TABLE bill_payments
                ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bill_payments' AND column_name = 'source_type'
        ) THEN
            ALTER TABLE bill_payments
                ADD COLUMN source_type VARCHAR(32) NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bill_payments' AND column_name = 'created_by'
        ) THEN
            ALTER TABLE bill_payments
                ADD COLUMN created_by BIGINT NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bill_payments' AND column_name = 'updated_by'
        ) THEN
            ALTER TABLE bill_payments
                ADD COLUMN updated_by BIGINT NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = @db AND table_name = 'bill_payments' AND index_name = 'idx_bill_payments_bill'
        ) THEN
            CREATE INDEX idx_bill_payments_bill ON bill_payments (bill_kind, bill_id);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = @db AND table_name = 'bill_payments' AND index_name = 'idx_bill_payments_bill_active'
        ) THEN
            CREATE INDEX idx_bill_payments_bill_active ON bill_payments (bill_kind, bill_id, is_deleted);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = @db AND table_name = 'bill_payments' AND index_name = 'idx_bill_payments_date_mode'
        ) THEN
            CREATE INDEX idx_bill_payments_date_mode ON bill_payments (payment_date, payment_mode);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = @db AND table_name = 'bill_payments' AND index_name = 'idx_bill_payments_source_type'
        ) THEN
            CREATE INDEX idx_bill_payments_source_type ON bill_payments (source_type);
        END IF;

    END IF;

    -- ------------------------------------------------------------------
    -- bills_gst / bills_non_gst: audit + list/report indexes
    -- ------------------------------------------------------------------

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = @db AND table_name = 'bills_gst'
    ) THEN

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bills_gst' AND column_name = 'created_at'
        ) THEN
            ALTER TABLE bills_gst
                ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bills_gst' AND column_name = 'updated_at'
        ) THEN
            ALTER TABLE bills_gst
                ADD COLUMN updated_at DATETIME(6) NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bills_gst' AND column_name = 'is_deleted'
        ) THEN
            ALTER TABLE bills_gst
                ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bills_gst' AND column_name = 'location'
        ) THEN
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @db AND table_name = 'bills_gst' AND index_name = 'idx_bills_gst_loc_date_del'
            ) THEN
                CREATE INDEX idx_bills_gst_loc_date_del ON bills_gst (location, bill_date, is_deleted);
            END IF;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = @db AND table_name = 'bills_gst' AND index_name = 'idx_bills_gst_customer_date'
        ) THEN
            CREATE INDEX idx_bills_gst_customer_date ON bills_gst (customer_id, bill_date);
        END IF;

    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = @db AND table_name = 'bills_non_gst'
    ) THEN

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bills_non_gst' AND column_name = 'created_at'
        ) THEN
            ALTER TABLE bills_non_gst
                ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bills_non_gst' AND column_name = 'updated_at'
        ) THEN
            ALTER TABLE bills_non_gst
                ADD COLUMN updated_at DATETIME(6) NULL;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bills_non_gst' AND column_name = 'is_deleted'
        ) THEN
            ALTER TABLE bills_non_gst
                ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = @db AND table_name = 'bills_non_gst' AND column_name = 'location'
        ) THEN
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.statistics
                WHERE table_schema = @db AND table_name = 'bills_non_gst' AND index_name = 'idx_bills_non_gst_loc_date_del'
            ) THEN
                CREATE INDEX idx_bills_non_gst_loc_date_del ON bills_non_gst (location, bill_date, is_deleted);
            END IF;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.statistics
            WHERE table_schema = @db AND table_name = 'bills_non_gst' AND index_name = 'idx_bills_non_gst_customer_date'
        ) THEN
            CREATE INDEX idx_bills_non_gst_customer_date ON bills_non_gst (customer_id, bill_date);
        END IF;

    END IF;

END$$
DELIMITER ;

CALL sp_db_safety_hardening();
DROP PROCEDURE IF EXISTS sp_db_safety_hardening;
