-- Persist exact paid amount on bills for deterministic DUE/PARTIAL/PAID.
ALTER TABLE bills_gst
    ADD COLUMN paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER total_amount;

ALTER TABLE bills_non_gst
    ADD COLUMN paid_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER total_amount;

-- Backfill from bill_payments where available.
UPDATE bills_gst b
LEFT JOIN (
  SELECT bill_id, SUM(amount) AS paid
  FROM bill_payments
  WHERE bill_kind = 'GST'
  GROUP BY bill_id
) p ON p.bill_id = b.id
SET b.paid_amount = COALESCE(p.paid, 0.00);

UPDATE bills_non_gst b
LEFT JOIN (
  SELECT bill_id, SUM(amount) AS paid
  FROM bill_payments
  WHERE bill_kind = 'NON_GST'
  GROUP BY bill_id
) p ON p.bill_id = b.id
SET b.paid_amount = COALESCE(p.paid, 0.00);
