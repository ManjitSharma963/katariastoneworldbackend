-- Unblocks BILL_REVERSAL / BILL_RETURN inserts on bill cancel (legacy ENUM cannot store them).
ALTER TABLE transactions
  MODIFY COLUMN category VARCHAR(32) NOT NULL;
