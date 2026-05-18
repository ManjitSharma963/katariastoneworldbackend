-- =============================================================================
-- ARCHIVED — one-time migration (tables daily_budget* removed; do not run on new DBs).
-- Migrate legacy daily_budget_events → transactions (MySQL 8.0+)
-- Goal: stop depending on daily_budget_events / daily_budget for history.
--
-- Prereq: transactions table exists (see transactions_ledger.mysql.sql).
--
-- Legacy model (DailyBudgetEvent):
--   delta = closing_balance - opening_balance (cash/UPI "in hand" pool change)
--   Positive delta → money IN ; negative → money OUT
--
-- payment_mode: legacy rows do not split CASH vs UPI; we store 'cash' for all
--   in-hand-style events. Rows with event_type containing BANK use 'bank'.
--
-- Run in a transaction; verify counts; then drop/rename old tables only after
-- app code reads `transactions` instead of daily_budget_*.
-- =============================================================================

SET NAMES utf8mb4;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- 1) Idempotent guard: skip rows already migrated (re-run safe)
-- ---------------------------------------------------------------------------
-- Expect empty result before first run:
--   SELECT COUNT(*) FROM transactions WHERE notes LIKE 'migrated:daily_budget_events#%';

-- Idempotency key in notes: migrated:daily_budget_events#<id> <rest>
-- Pattern ends with space after id so #12 does not match legacy id 123.

INSERT INTO transactions (
  date_time,
  amount,
  direction,
  category,
  payment_mode,
  location,
  owner_user_id,
  party_id,
  reference_id,
  reference_type,
  status,
  notes,
  created_at,
  updated_at
)
SELECT
  COALESCE(e.created_at, TIMESTAMP(e.event_date, '00:00:00')) AS date_time,
  ROUND(ABS(COALESCE(e.delta, e.closing_balance - e.opening_balance)), 2) AS amount,
  CASE
    WHEN (COALESCE(e.delta, e.closing_balance - e.opening_balance)) > 0 THEN 'in'
    WHEN (COALESCE(e.delta, e.closing_balance - e.opening_balance)) < 0 THEN 'out'
    ELSE NULL
  END AS direction,
  CASE e.event_type
    WHEN 'LOAN_RECEIVED' THEN 'loan_taken'
    WHEN 'EXPENSE_DEBIT' THEN 'expense'
    WHEN 'EXPENSE_CREDIT' THEN 'expense'
    ELSE 'other'
  END AS category,
  CASE
    WHEN e.event_type LIKE '%BANK%' THEN 'bank'
    WHEN e.event_type IN ('BUDGET_BANK_TRANSFER_INCREASE') THEN 'bank'
    ELSE 'cash'
  END AS payment_mode,
  TRIM(e.location) AS location,
  NULL AS owner_user_id,
  NULL AS party_id,
  e.id AS reference_id,
  'other' AS reference_type,
  'active' AS status,
  CONCAT(
    'migrated:daily_budget_events#', e.id, ' ',
    'event_type=', e.event_type,
    ' event_date=', e.event_date,
    ' opening=', e.opening_balance,
    ' closing=', e.closing_balance,
    ' spent=', e.spent_amount,
    ' delta=', IFNULL(e.delta, e.closing_balance - e.opening_balance)
  ) AS notes,
  COALESCE(e.created_at, CURRENT_TIMESTAMP(3)) AS created_at,
  COALESCE(e.created_at, CURRENT_TIMESTAMP(3)) AS updated_at
FROM daily_budget_events e
WHERE
  -- Atomic money movement only; skip zero-delta snapshots (CHECK amount > 0)
  ROUND(ABS(COALESCE(e.delta, e.closing_balance - e.opening_balance)), 2) > 0
  AND NOT EXISTS (
    SELECT 1
    FROM transactions t
    WHERE t.notes LIKE CONCAT('migrated:daily_budget_events#', e.id, ' %')
  );

-- ---------------------------------------------------------------------------
-- 2) Optional: daily_budget — NOT a pure event log
-- ---------------------------------------------------------------------------
-- `daily_budget` holds current caps (amount, remaining_budget, bank_opening_balance).
-- Those are NOT equivalent to single cash flows. Recommended:
--   A) Do NOT insert synthetic rows from daily_budget (avoids double-count with events).
--   B) After cutover, set initial "opening" for new installs via one manual
--      transactions row, OR derive UI from SUM(transactions) only.
--
-- If you still want a one-time **marker** row per location (zero amount NOT allowed),
-- you could insert category='other' with amount=0.01 and notes only — NOT recommended.
--
-- If you must capture **remaining_budget** as a one-off opening balance seed
-- (only when events do not reflect full truth), uncomment and adjust carefully:
--
-- INSERT INTO transactions (date_time, amount, direction, category, payment_mode,
--   location, owner_user_id, party_id, reference_id, reference_type, status, notes, created_at, updated_at)
-- SELECT
--   b.updated_at,
--   ROUND(GREATEST(COALESCE(b.remaining_budget, 0), 0.01), 2),
--   'in',
--   'other',
--   'cash',
--   TRIM(b.location),
--   b.user_id,
--   NULL,
--   b.id,
--   'other',
--   'active',
--   CONCAT('migrated:daily_budget#', b.id, ' ONE-TIME remaining_budget seed — verify no double count'),
--   b.updated_at,
--   b.updated_at
-- FROM daily_budget b
-- WHERE COALESCE(b.remaining_budget, 0) <> 0
--   AND NOT EXISTS (
--     SELECT 1 FROM transactions t WHERE t.notes LIKE CONCAT('migrated:daily_budget#', b.id, '%')
--   );

COMMIT;

-- ---------------------------------------------------------------------------
-- 3) Verification queries (run after commit)
-- ---------------------------------------------------------------------------
-- SELECT COUNT(*) FROM daily_budget_events;
-- SELECT COUNT(*) FROM transactions WHERE notes LIKE 'migrated:daily_budget_events#%';
--
-- Compare net by day (legacy vs new) for one location:
--   -- Legacy (use delta):
--   SELECT event_date, SUM(delta) FROM daily_budget_events WHERE location='Bhondsi' GROUP BY event_date;
--   -- New:
--   SELECT DATE(date_time) d,
--          SUM(CASE WHEN direction='in' THEN amount ELSE -amount END)
--   FROM transactions WHERE location='Bhondsi' AND status='active'
--     AND notes LIKE 'migrated:daily_budget_events#%'
--   GROUP BY DATE(date_time);

-- ---------------------------------------------------------------------------
-- 4) After app deploy reads `transactions` only — drop legacy tables
-- ---------------------------------------------------------------------------
-- BACK UP FIRST (mysqldump).
--
-- SET FOREIGN_KEY_CHECKS = 0;
-- DROP TABLE IF EXISTS daily_budget_events;
-- DROP TABLE IF EXISTS daily_budget;
-- SET FOREIGN_KEY_CHECKS = 1;
