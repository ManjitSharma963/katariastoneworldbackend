-- Backfill inventory_transactions.bill_kind for BILL rows where it was never set.
-- Enables reversal_of_id resolution (latest SALE lookup) for legacy data.
-- Safe to run multiple times: only touches rows with NULL/empty bill_kind.
--
-- Collision note: bills_gst.id and bills_non_gst.id are independent sequences.
-- If the same numeric id existed in BOTH tables (rare), GST is applied first;
-- NON_GST is applied only when the id is not present in bills_gst.

UPDATE inventory_transactions t
INNER JOIN bills_gst g ON g.id = t.reference_id
SET t.bill_kind = 'GST'
WHERE t.reference_type = 'BILL'
  AND (t.bill_kind IS NULL OR TRIM(t.bill_kind) = '');

UPDATE inventory_transactions t
INNER JOIN bills_non_gst n ON n.id = t.reference_id
SET t.bill_kind = 'NON_GST'
WHERE t.reference_type = 'BILL'
  AND (t.bill_kind IS NULL OR TRIM(t.bill_kind) = '')
  AND NOT EXISTS (SELECT 1 FROM bills_gst g WHERE g.id = t.reference_id);
