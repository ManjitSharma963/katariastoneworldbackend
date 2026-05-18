-- Drop legacy tables superseded by `transactions` (safe to re-run).
-- Run once per environment after backup.

DROP TABLE IF EXISTS daily_budget_events;
DROP TABLE IF EXISTS daily_budget;
DROP TABLE IF EXISTS financial_ledger;
DROP TABLE IF EXISTS unified_financial_ledger;
