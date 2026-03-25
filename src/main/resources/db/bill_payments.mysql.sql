-- Bill payments (split / partial). Run once if not using spring.jpa.hibernate.ddl-auto=update.
-- Or rely on Hibernate ddl-auto=update to create bill_payments.

CREATE TABLE IF NOT EXISTS bill_payments (
    payment_id BIGINT NOT NULL AUTO_INCREMENT,
    bill_kind VARCHAR(16) NOT NULL,
    bill_id BIGINT NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    payment_mode VARCHAR(32) NOT NULL,
    payment_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (payment_id),
    KEY idx_bill_payments_bill (bill_kind, bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Backfill: one row per existing bill that has payment_method set (adjust enum mapping as needed).
INSERT INTO bill_payments (bill_kind, bill_id, amount, payment_mode, payment_date, created_at)
SELECT
    'GST' AS bill_kind,
    g.id AS bill_id,
    g.total_amount AS amount,
    CASE
        WHEN UPPER(REPLACE(REPLACE(TRIM(g.payment_method), '-', ''), ' ', '_')) LIKE '%CASH%' THEN 'CASH'
        WHEN UPPER(TRIM(g.payment_method)) LIKE '%UPI%' THEN 'UPI'
        WHEN UPPER(TRIM(g.payment_method)) LIKE '%CHEQUE%' OR UPPER(TRIM(g.payment_method)) LIKE '%CHECK%' THEN 'CHEQUE'
        ELSE 'BANK_TRANSFER'
    END AS payment_mode,
    g.bill_date AS payment_date,
    g.created_at AS created_at
FROM bills_gst g
WHERE g.payment_method IS NOT NULL AND TRIM(g.payment_method) <> ''
  AND NOT EXISTS (
        SELECT 1 FROM bill_payments p
        WHERE p.bill_kind = 'GST' AND p.bill_id = g.id
    );

INSERT INTO bill_payments (bill_kind, bill_id, amount, payment_mode, payment_date, created_at)
SELECT
    'NON_GST' AS bill_kind,
    n.id AS bill_id,
    n.total_amount AS amount,
    CASE
        WHEN UPPER(REPLACE(REPLACE(TRIM(n.payment_method), '-', ''), ' ', '_')) LIKE '%CASH%' THEN 'CASH'
        WHEN UPPER(TRIM(n.payment_method)) LIKE '%UPI%' THEN 'UPI'
        WHEN UPPER(TRIM(n.payment_method)) LIKE '%CHEQUE%' OR UPPER(TRIM(n.payment_method)) LIKE '%CHECK%' THEN 'CHEQUE'
        ELSE 'BANK_TRANSFER'
    END AS payment_mode,
    n.bill_date AS payment_date,
    n.created_at AS created_at
FROM bills_non_gst n
WHERE n.payment_method IS NOT NULL AND TRIM(n.payment_method) <> ''
  AND NOT EXISTS (
        SELECT 1 FROM bill_payments p
        WHERE p.bill_kind = 'NON_GST' AND p.bill_id = n.id
    );

-- Optional: fix payment_status for bills with no payments row yet (full credit sale = DUE).
-- UPDATE bills_gst SET payment_status = 'DUE' WHERE total_amount > 0 AND payment_status = 'PAID'
--   AND id NOT IN (SELECT bill_id FROM bill_payments WHERE bill_kind = 'GST');
-- (Run only after validating your data.)

-- --- Reporting examples ---
-- A) Total collected by mode:
-- SELECT payment_mode, SUM(amount) FROM bill_payments GROUP BY payment_mode;

-- B) Total still due (requires computed paid per bill):
-- SELECT SUM(GREATEST(0,
--   COALESCE(b.total_amount,0) - COALESCE((SELECT SUM(p.amount) FROM bill_payments p
--     WHERE p.bill_kind = 'GST' AND p.bill_id = b.id), 0)))
-- FROM bills_gst b
-- UNION ALL ... (same for bills_non_gst);

-- C) Cash collected (for reconciliation / cash in hand from sales):
-- SELECT COALESCE(SUM(amount),0) FROM bill_payments WHERE payment_mode = 'CASH';

-- --- Daily closing (MySQL) — adapt :loc and :d ---
-- Bills issued on date (count + sales) — union GST + non-GST:
-- SELECT COUNT(*), COALESCE(SUM(total_amount),0) FROM (
--   SELECT id, total_amount, bill_date FROM bills_gst g JOIN customers c ON c.id = g.customer_id
--     WHERE c.location = :loc AND DATE(g.bill_date) = :d
--   UNION ALL
--   SELECT id, total_amount, bill_date FROM bills_non_gst n JOIN customers c ON c.id = n.customer_id
--     WHERE c.location = :loc AND DATE(n.bill_date) = :d
-- ) x;

-- Payments received on date (location via bill join):
-- SELECT COALESCE(SUM(p.amount),0) FROM bill_payments p
-- WHERE DATE(p.payment_date) = :d
--   AND (
--     (p.bill_kind = 'GST' AND EXISTS (SELECT 1 FROM bills_gst g JOIN customers c ON c.id = g.customer_id
--        WHERE g.id = p.bill_id AND c.location = :loc))
--     OR (p.bill_kind = 'NON_GST' AND EXISTS (SELECT 1 FROM bills_non_gst n JOIN customers c ON c.id = n.customer_id
--        WHERE n.id = p.bill_id AND c.location = :loc))
--   );

-- Payment mode breakdown (same filter as above):
-- SELECT p.payment_mode, SUM(p.amount) FROM bill_payments p
-- WHERE DATE(p.payment_date) = :d AND ( ... same EXISTS as above ... )
-- GROUP BY p.payment_mode;

-- Expenses: table `expenses`, column `date` (not expense_date):
-- SELECT COALESCE(SUM(amount),0) FROM expenses WHERE location = :loc AND DATE(`date`) = :d;
