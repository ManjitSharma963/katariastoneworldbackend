-- =============================================================================
-- bills_non_gst.bill_status = ENTERPRISE LIFECYCLE (not payment state)
-- =============================================================================
-- payment_status / paid_amount / bill_payments remain the source of truth
-- for money owed vs collected. This column tracks document lifecycle only:
--   DRAFT, COMPLETED, PARTIALLY_RETURNED, FULLY_RETURNED, EXCHANGED,
--   CANCELLED, SUPERSEDED
--
-- Legacy values from billing_append_only_phase1 (version-oriented):
--   ACTIVE, UPDATED, SUPPLEMENTARY  →  COMPLETED
--   CANCELLED  →  CANCELLED
--
-- Safe to run on MySQL 8+. If bill_status does not exist yet, run
-- billing_append_only_phase1.mysql.sql first.
-- =============================================================================

-- Map legacy version-oriented flags into lifecycle vocabulary
UPDATE bills_non_gst
SET bill_status = 'COMPLETED'
WHERE bill_status IN ('ACTIVE', 'UPDATED', 'SUPPLEMENTARY');

-- Normalise any unexpected value to COMPLETED (audit can still use bill_versions)
UPDATE bills_non_gst
SET bill_status = 'COMPLETED'
WHERE bill_status NOT IN (
    'DRAFT',
    'COMPLETED',
    'PARTIALLY_RETURNED',
    'FULLY_RETURNED',
    'EXCHANGED',
    'CANCELLED',
    'SUPERSEDED'
);

ALTER TABLE bills_non_gst
    MODIFY COLUMN bill_status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED'
        COMMENT 'Lifecycle: DRAFT,COMPLETED,PARTIALLY_RETURNED,FULLY_RETURNED,EXCHANGED,CANCELLED,SUPERSEDED';
