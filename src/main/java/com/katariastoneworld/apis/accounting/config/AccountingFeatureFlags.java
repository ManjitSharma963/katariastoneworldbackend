package com.katariastoneworld.apis.accounting.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Feature flags for gradual migration to {@link com.katariastoneworld.apis.accounting.api.AccountingTransactionService}.
 */
@Component
public class AccountingFeatureFlags {

    @Value("${accounting.engine.expense.enabled:false}")
    private boolean expenseEnabled;

    @Value("${accounting.engine.bill.enabled:false}")
    private boolean billEnabled;

    @Value("${accounting.engine.refund.enabled:false}")
    private boolean refundEnabled;

    @Value("${accounting.engine.advance.enabled:false}")
    private boolean advanceEnabled;

    @Value("${accounting.engine.loan.enabled:false}")
    private boolean loanEnabled;

    @Value("${accounting.engine.receivable.enabled:false}")
    private boolean receivableEnabled;

    @Value("${accounting.engine.payroll.enabled:false}")
    private boolean payrollEnabled;

    @Value("${accounting.engine.client.enabled:false}")
    private boolean clientEnabled;

    @Value("${accounting.engine.budget.enabled:false}")
    private boolean budgetEnabled;

    @Value("${accounting.engine.adjustment.enabled:false}")
    private boolean adjustmentEnabled;

    public boolean isExpenseEnabled() {
        return expenseEnabled;
    }

    public boolean isBillEnabled() {
        return billEnabled;
    }

    public boolean isRefundEnabled() {
        return refundEnabled;
    }

    public boolean isAdvanceEnabled() {
        return advanceEnabled;
    }

    public boolean isLoanEnabled() {
        return loanEnabled;
    }

    public boolean isReceivableEnabled() {
        return receivableEnabled;
    }

    public boolean isPayrollEnabled() {
        return payrollEnabled;
    }

    public boolean isClientEnabled() {
        return clientEnabled;
    }

    public boolean isBudgetEnabled() {
        return budgetEnabled;
    }

    public boolean isAdjustmentEnabled() {
        return adjustmentEnabled;
    }
}
