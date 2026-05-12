-- Backfill missing bill transaction party names.
UPDATE transactions t
JOIN bills_non_gst b ON t.reference_id = b.id
SET t.party_name = CONCAT('Customer_', b.customer_id)
WHERE t.reference_type = 'bill'
  AND (t.party_name IS NULL OR TRIM(t.party_name) = '');
