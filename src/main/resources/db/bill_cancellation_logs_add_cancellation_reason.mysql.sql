-- Run once on existing databases that already have bill_cancellation_logs without cancellation_reason.
ALTER TABLE bill_cancellation_logs
    ADD COLUMN cancellation_reason TEXT NULL
    AFTER cancelled_by_user_id;
