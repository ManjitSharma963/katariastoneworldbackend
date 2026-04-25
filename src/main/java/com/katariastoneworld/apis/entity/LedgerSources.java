package com.katariastoneworld.apis.entity;

/**
 * {@code source} values for {@link UnifiedFinancialLedgerEntry} (uppercase, stable for APIs and reports).
 */
public final class LedgerSources {

    private LedgerSources() {
    }

    public static final String EXPENSE = "EXPENSE";
    public static final String BILL = "BILL";
    /** Customer advance deposit (financial ledger ADVANCE_DEPOSIT). */
    public static final String ADVANCE = "ADVANCE";
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
    /** Employee advance paid out (payroll ledger). */
    public static final String SALARY_ADVANCE = "SALARY_ADVANCE";
    /** Net salary paid in cash/UPI/bank per payroll settlement. */
    public static final String SALARY_PAY = "SALARY_PAY";
}
