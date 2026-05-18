-- Reclassify customer refunds: NOT expenses — sales returns / bill cancellations.
-- Safe to run multiple times.

-- Full bill cancellation refunds
UPDATE transactions
SET category = 'BILL_REVERSAL',
    sub_category = 'BILL_CANCELLATION',
    txn_type = 'BILL_REVERSAL'
WHERE is_deleted = 0
  AND status = 'ACTIVE'
  AND direction = 'OUT'
  AND category IN ('BILL', 'BILL_REVERSAL')
  AND (
    txn_type LIKE '%REVERSAL%'
    OR sub_category LIKE '%REVERSAL%'
    OR sub_category = 'BILL_CANCELLATION'
    OR notes LIKE '%cancellation refund%'
    OR notes LIKE '%Bill cancellation refund%'
  );

-- Partial return refunds
UPDATE transactions
SET category = 'BILL_RETURN',
    sub_category = 'CUSTOMER_REFUND',
    txn_type = COALESCE(NULLIF(txn_type, ''), 'BILL_RETURN')
WHERE is_deleted = 0
  AND status = 'ACTIVE'
  AND direction = 'OUT'
  AND category = 'BILL'
  AND (
    notes LIKE '%Partial return refund%'
    OR notes LIKE '%partial return%'
    OR txn_type LIKE 'STOCK_RETURN_%'
  );
