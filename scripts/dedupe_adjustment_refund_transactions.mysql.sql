-- One-time cleanup: adjustment finalize previously posted TWO BILL_RETURN rows
-- (STOCK_RETURN_{id} + ADJ_SETTLE_REFUND_{group}). Keep the adjustment settlement row;
-- soft-delete the duplicate stock-return payout for the same bill + adjustment group.
--
-- Preview:
-- SELECT id, txn_type, amount, transaction_date, notes, adjustment_group_id, linked_group_id
-- FROM transactions
-- WHERE category = 'BILL_RETURN' AND is_deleted = 0
--   AND txn_type LIKE 'STOCK_RETURN_%'
--   AND adjustment_group_id IS NOT NULL
--   AND EXISTS (
--     SELECT 1 FROM transactions t2
--     WHERE t2.reference_id = transactions.reference_id
--       AND t2.category = 'BILL_RETURN' AND t2.is_deleted = 0
--       AND t2.txn_type LIKE 'ADJ_SETTLE_REFUND_%'
--       AND (t2.adjustment_group_id = transactions.adjustment_group_id
--            OR t2.linked_group_id = transactions.linked_group_id)
--   );

UPDATE transactions t
JOIN transactions adj
  ON adj.reference_id = t.reference_id
 AND adj.category = 'BILL_RETURN'
 AND adj.is_deleted = 0
 AND adj.txn_type LIKE 'ADJ_SETTLE_REFUND_%'
 AND (adj.adjustment_group_id = t.adjustment_group_id OR adj.linked_group_id = t.linked_group_id)
SET t.is_deleted = 1,
    t.updated_at = NOW()
WHERE t.category = 'BILL_RETURN'
  AND t.is_deleted = 0
  AND t.txn_type LIKE 'STOCK_RETURN_%'
  AND t.adjustment_group_id IS NOT NULL;
