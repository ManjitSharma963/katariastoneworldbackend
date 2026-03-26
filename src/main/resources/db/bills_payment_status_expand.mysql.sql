-- Fix payment_status mismatch with current backend enums.
-- Backend now uses: DUE, PENDING, PARTIAL, PAID, CANCELLED
-- If DB column is old ENUM (e.g. only PAID/PENDING), inserts fail with:
-- "Data truncated for column 'payment_status'".

-- Recommended: use VARCHAR(20) so future enum additions don't require DB enum changes.
ALTER TABLE bills_gst
  MODIFY COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE bills_non_gst
  MODIFY COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- Optional data cleanup for unexpected legacy values:
UPDATE bills_gst
SET payment_status = 'PENDING'
WHERE payment_status IS NULL OR payment_status = '' OR payment_status NOT IN ('DUE','PENDING','PARTIAL','PAID','CANCELLED');

UPDATE bills_non_gst
SET payment_status = 'PENDING'
WHERE payment_status IS NULL OR payment_status = '' OR payment_status NOT IN ('DUE','PENDING','PARTIAL','PAID','CANCELLED');
