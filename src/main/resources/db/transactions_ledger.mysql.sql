-- =============================================================================
-- transactions — single source of truth for money movements (append-only rows)
-- =============================================================================
-- Business rules (application-enforced; DB supports via status + CHECK):
--   • Never hard-delete: set status = 'cancelled' (optionally notes = reason).
--   • Never change amount in place: cancel row + insert a new active row.
--   • Reports / balances: WHERE status = 'active' only.
--   • One row = one atomic economic event (no rolled-up summaries in this table).
--
-- Primary money ledger (replaces removed daily_budget* and financial_ledger tables):
--   • Use location + date_time + direction/amount + payment_mode to rebuild
--     per-day cash/UPI/bank flows and running balances.
--   • Migrate existing ledger-like rows into this table, then retire old tables
--     after dual-write validation (separate migration project).
--
-- MySQL 8.0+ recommended (CHECK constraints, functional indexes optional).
-- =============================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS transactions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

  -- When the money actually moved (business time; use app timezone consistently)
  date_time DATETIME(3) NOT NULL,

  -- Always strictly positive; sign comes from direction
  amount DECIMAL(14, 2) NOT NULL,

  direction ENUM('in', 'out') NOT NULL,

  category ENUM(
    'bill_payment',
    'advance',
    'expense',
    'salary',
    'loan_taken',
    'loan_given',
    'loan_repayment',
    'client_payment',
    'other'
  ) NOT NULL,

  -- Physical / rail channel. 'bank' covers NEFT/RTGS/card/cheque unless you split later.
  payment_mode ENUM('cash', 'upi', 'bank') NOT NULL,

  -- Branch / store scope
  location VARCHAR(64) NOT NULL DEFAULT '',

  -- Optional tenant / owner scope for multi-user deployments (nullable = global/system)
  owner_user_id BIGINT UNSIGNED NULL,

  -- Polymorphic party (customer id, employee id, supplier id, lender id — meaning by category)
  party_id BIGINT UNSIGNED NULL,

  -- Polymorphic link to originating document / entity
  reference_id BIGINT UNSIGNED NULL,
  reference_type ENUM(
    'bill',
    'expense',
    'salary',
    'loan',
    'other'
  ) NOT NULL DEFAULT 'other',

  status ENUM('active', 'cancelled', 'adjusted') NOT NULL DEFAULT 'active',

  notes VARCHAR(2000) NULL,

  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  PRIMARY KEY (id),

  CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Append-only money events; cancel+reinsert for corrections.';

-- -----------------------------------------------------------------------------
-- Indexes (reporting + filters + party lookups)
-- -----------------------------------------------------------------------------

CREATE INDEX idx_transactions_date_time ON transactions (date_time);

CREATE INDEX idx_transactions_payment_mode ON transactions (payment_mode);

CREATE INDEX idx_transactions_status ON transactions (status);

CREATE INDEX idx_transactions_party_id ON transactions (party_id);

CREATE INDEX idx_transactions_reference ON transactions (reference_id, reference_type);

-- Common report slice: active rows in a time window (optionally add location first if always filtered)
CREATE INDEX idx_transactions_status_date_time ON transactions (status, date_time);

CREATE INDEX idx_transactions_location_status_date ON transactions (location, status, date_time);

-- Optional: filter by category in dashboards
CREATE INDEX idx_transactions_category_status_date ON transactions (category, status, date_time);

-- -----------------------------------------------------------------------------
-- Example: daily net and running balance (application or SQL view)
-- -----------------------------------------------------------------------------
-- Daily net (active only):
--   SELECT DATE(date_time) AS d,
--          SUM(CASE WHEN direction = 'in'  THEN amount ELSE 0 END) AS cash_in,
--          SUM(CASE WHEN direction = 'out' THEN amount ELSE 0 END) AS cash_out,
--          SUM(CASE WHEN direction = 'in' THEN amount ELSE -amount END) AS net
--   FROM transactions
--   WHERE status = 'active' AND location = :loc
--   GROUP BY DATE(date_time);
--
-- Running balance over ordered events:
--   SELECT id, date_time,
--          SUM(CASE WHEN direction = 'in' THEN amount ELSE -amount END)
--            OVER (PARTITION BY location ORDER BY date_time, id
--                  ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance
--   FROM transactions
--   WHERE status = 'active' AND payment_mode = 'cash' AND location = :loc;
-- -----------------------------------------------------------------------------
