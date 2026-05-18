package com.katariastoneworld.apis.entity;

/**
 * {@code source} values passed into money sync (uppercase, stable for APIs and reports).
 */
public final class LedgerSources {

    private LedgerSources() {
    }

    public static final String EXPENSE = "EXPENSE";
    public static final String BILL = "BILL";
    /** Customer advance deposit (financial ledger ADVANCE_DEPOSIT). */
    public static final String ADVANCE = "ADVANCE";
    /** Customer advance refunded to customer (financial ledger ADVANCE_REFUND). */
    public static final String ADVANCE_REFUND = "ADVANCE_REFUND";
    /** Non-GST bill replace: excess paid over new total → customer wallet store credit (see {@code BILL_EDIT_ADJUSTMENT}). */
    public static final String BILL_EDIT_ADJUSTMENT = "BILL_EDIT_ADJUSTMENT";
    /** Inventory bill return credited to wallet (paired with wallet txn id as reference key). */
    public static final String BILL_RETURN_WALLET_CREDIT = "BILL_RETURN_WALLET_CREDIT";
    /** Client module payment in (unified ledger; Phase 2 name). */
    public static final String CLIENT_PAYMENT = "CLIENT_PAYMENT";
    /** Client module payment out / purchase settlement (DEBIT). */
    public static final String CLIENT_OUT = "CLIENT_OUT";
    /** @deprecated use {@link #CLIENT_PAYMENT}; retained for existing DB rows. */
    @Deprecated
    public static final String CLIENT = "CLIENT";
    /** Loan draw / receipt (loan_ledger RECEIPT). Phase 2 canonical name. */
    public static final String LOAN = "LOAN";
    /** @deprecated use {@link #LOAN}; retained for existing DB rows. */
    @Deprecated
    public static final String LOAN_RECEIVED = "LOAN_RECEIVED";
    /** Loan repayment outflow (daily loan expense; keyed by expense id). */
    public static final String LOAN_REPAY = "LOAN_REPAY";
    /** Money lent out to a person/org (receivable_ledger DISBURSEMENT); keyed by receivable_ledger_entries.id. */
    public static final String LOAN_GIVEN = "LOAN_GIVEN";
    /** Repayment received from a borrower; keyed by receivable_ledger_entries.id. */
    public static final String LOAN_GIVEN_REPAY = "LOAN_GIVEN_REPAY";
    /** Employee advance paid out (payroll ledger). */
    public static final String SALARY_ADVANCE = "SALARY_ADVANCE";
    /** Net salary paid in cash/UPI/bank per payroll settlement. */
    public static final String SALARY_PAY = "SALARY_PAY";
}
