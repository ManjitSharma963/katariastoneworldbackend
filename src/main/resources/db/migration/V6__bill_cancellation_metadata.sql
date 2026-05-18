-- Bill cancellation audit columns (FINALIZED cancel flow; history preserved).

ALTER TABLE bills_non_gst
    ADD COLUMN cancelled_at DATETIME NULL,
    ADD COLUMN cancelled_by_user_id BIGINT NULL,
    ADD COLUMN cancellation_reason TEXT NULL;
