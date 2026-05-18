-- Customer refunds are sales returns / cancellations, not expenses (idempotent).

UPDATE transactions
SET category = 'BILL_REVERSAL',
    sub_category = 'BILL_CANCELLATION',
    txn_type = 'BILL_REVERSAL'
WHERE is_deleted = 0
  AND status = 'ACTIVE'
  AND direction IN ('OUT', 'out')
  AND category IN ('BILL', 'BILL_REVERSAL', 'bill_payment')
  AND (
    txn_type LIKE '%REVERSAL%'
    OR sub_category LIKE '%REVERSAL%'
    OR sub_category = 'BILL_CANCELLATION'
    OR notes LIKE '%cancellation refund%'
    OR notes LIKE '%Bill cancellation refund%'
  );

UPDATE transactions
SET category = 'BILL_RETURN',
    sub_category = 'CUSTOMER_REFUND',
    txn_type = COALESCE(NULLIF(txn_type, ''), 'BILL_RETURN')
WHERE is_deleted = 0
  AND status = 'ACTIVE'
  AND direction IN ('OUT', 'out')
  AND category IN ('BILL', 'bill_payment')
  AND (
    notes LIKE '%Partial return refund%'
    OR notes LIKE '%partial return%'
    OR txn_type LIKE 'STOCK_RETURN_%'
  );
