-- Fix: Data truncated for column 'category' when inserting BILL_REVERSAL / BILL_RETURN on bill cancel.
-- Java uses VARCHAR-style category names (BILL, BILL_REVERSAL, BILL_RETURN, EXPENSE, …).
-- Legacy ENUM('bill_payment', 'expense', …) cannot store those values.

ALTER TABLE transactions
  MODIFY COLUMN category VARCHAR(32) NOT NULL;
