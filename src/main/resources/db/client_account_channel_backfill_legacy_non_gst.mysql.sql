-- ONE-TIME: Reclassify all existing client purchases/payments/ledger as Non-GST.
-- Run after client_account_channel.mysql.sql if old data still shows under GST.
-- Safe to re-run: sets NON_GST on all rows (new GST entries created after this stay GST).

SET @db := DATABASE();

-- ── Purchases ──
UPDATE client_purchases SET account_channel = 'NON_GST';

-- ── Payments ──
UPDATE client_purchase_payments SET account_channel = 'NON_GST';

-- ── Client ledger transactions ──
UPDATE client_transactions SET account_channel = 'NON_GST';

UPDATE client_transactions
SET notes = REPLACE(notes, '[GST]', '[Non-GST]')
WHERE notes LIKE '[GST]%';

-- ── Supplier account settings ──
-- If both GST and Non-GST rows exist for same client, drop empty GST duplicate first.
DELETE g FROM client_supplier_accounts g
INNER JOIN client_supplier_accounts n
  ON g.location = n.location
 AND g.client_key = n.client_key
 AND n.account_channel = 'NON_GST'
 AND g.account_channel = 'GST';

UPDATE client_supplier_accounts SET account_channel = 'NON_GST'
WHERE account_channel <> 'NON_GST' OR account_channel IS NULL;

-- Verify (optional):
-- SELECT account_channel, COUNT(*) FROM client_purchases GROUP BY account_channel;
-- SELECT account_channel, COUNT(*) FROM client_purchase_payments GROUP BY account_channel;
-- SELECT account_channel, COUNT(*) FROM client_transactions GROUP BY account_channel;
