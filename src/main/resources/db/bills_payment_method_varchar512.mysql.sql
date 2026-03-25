-- Widen payment_method: split-payment summaries look like
--   CASH ₹5000.00, UPI ₹2000.00 | Due: ₹1000.00
-- which exceeds VARCHAR(50) and causes "Data too long for column 'payment_method'".
-- Run once on your MySQL database (adjust schema/db name if needed).

ALTER TABLE bills_non_gst
    MODIFY COLUMN payment_method VARCHAR(512) NULL;

ALTER TABLE bills_gst
    MODIFY COLUMN payment_method VARCHAR(512) NULL;
