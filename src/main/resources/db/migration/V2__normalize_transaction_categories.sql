-- Map legacy ENUM values to Java MoneyCategory names (idempotent).
UPDATE transactions SET category = 'BILL' WHERE category = 'bill_payment';
UPDATE transactions SET category = 'ADVANCE' WHERE category = 'advance';
UPDATE transactions SET category = 'EXPENSE' WHERE category = 'expense';
UPDATE transactions SET category = 'SALARY' WHERE category = 'salary';
UPDATE transactions SET category = 'LOAN' WHERE category IN ('loan_taken', 'loan_given', 'loan_repayment');
UPDATE transactions SET category = 'CLIENT_PAYMENT' WHERE category = 'client_payment';
UPDATE transactions SET category = 'OTHER' WHERE category = 'other';
