ALTER TABLE receivable_ledger_entries
    ADD COLUMN expense_id BIGINT NULL;

CREATE INDEX idx_receivable_ledger_expense_id ON receivable_ledger_entries (expense_id);
