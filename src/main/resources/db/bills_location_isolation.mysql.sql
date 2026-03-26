-- Bill location isolation + per-location series support.
-- Adds location column on bills tables so queries do NOT depend on customer.location.
-- Safe: columns are nullable for legacy rows; new rows should set location from JWT.

ALTER TABLE bills_gst ADD COLUMN location VARCHAR(50) NULL;
ALTER TABLE bills_non_gst ADD COLUMN location VARCHAR(50) NULL;

CREATE INDEX idx_bills_gst_location ON bills_gst(location);
CREATE INDEX idx_bills_non_gst_location ON bills_non_gst(location);

-- Optional one-time backfill (uncomment if you want to persist legacy location snapshot):
-- UPDATE bills_gst b JOIN customers c ON c.id = b.customer_id SET b.location = c.location WHERE b.location IS NULL;
-- UPDATE bills_non_gst b JOIN customers c ON c.id = b.customer_id SET b.location = c.location WHERE b.location IS NULL;
