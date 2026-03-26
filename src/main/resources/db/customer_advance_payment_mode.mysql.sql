-- Add payment mode to customer advance deposits so reports can bucket advance receipts by mode.
ALTER TABLE customer_advance
    ADD COLUMN payment_mode VARCHAR(32) NULL AFTER description;

-- Backfill existing rows to CASH to preserve current behavior.
UPDATE customer_advance
SET payment_mode = 'CASH'
WHERE payment_mode IS NULL OR TRIM(payment_mode) = '';
