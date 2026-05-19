package com.katariastoneworld.apis.accounting.support;

import org.springframework.stereotype.Component;

/**
 * Central void routing for legacy {@code removeTransaction(source, referenceId)} calls.
 */
@Component
public class UnifiedLedgerVoidRouter {

    private final RefundAccountingBridge refundAccountingBridge;
    private final CustomerAdvanceAccountingBridge customerAdvanceAccountingBridge;
    private final LoanAccountingBridge loanAccountingBridge;
    private final PayrollAccountingBridge payrollAccountingBridge;
    private final ClientTransactionAccountingBridge clientTransactionAccountingBridge;
    private final BudgetAccountingBridge budgetAccountingBridge;
    private final ReceivableAccountingBridge receivableAccountingBridge;

    public UnifiedLedgerVoidRouter(
            RefundAccountingBridge refundAccountingBridge,
            CustomerAdvanceAccountingBridge customerAdvanceAccountingBridge,
            LoanAccountingBridge loanAccountingBridge,
            PayrollAccountingBridge payrollAccountingBridge,
            ClientTransactionAccountingBridge clientTransactionAccountingBridge,
            BudgetAccountingBridge budgetAccountingBridge,
            ReceivableAccountingBridge receivableAccountingBridge) {
        this.refundAccountingBridge = refundAccountingBridge;
        this.customerAdvanceAccountingBridge = customerAdvanceAccountingBridge;
        this.loanAccountingBridge = loanAccountingBridge;
        this.payrollAccountingBridge = payrollAccountingBridge;
        this.clientTransactionAccountingBridge = clientTransactionAccountingBridge;
        this.budgetAccountingBridge = budgetAccountingBridge;
        this.receivableAccountingBridge = receivableAccountingBridge;
    }

    public boolean tryVoid(String location, String source, Long referenceId, String reason) {
        if (refundAccountingBridge.tryVoidByLedgerSource(location, source, referenceId, reason)) {
            return true;
        }
        if (customerAdvanceAccountingBridge.tryVoidByLedgerSource(location, source, referenceId, reason)) {
            return true;
        }
        if (loanAccountingBridge.tryVoidByLedgerSource(location, source, referenceId, reason)) {
            return true;
        }
        if (receivableAccountingBridge.tryVoidByLedgerSource(location, source, referenceId, reason)) {
            return true;
        }
        if (payrollAccountingBridge.tryVoidByLedgerSource(location, source, referenceId, reason)) {
            return true;
        }
        if (clientTransactionAccountingBridge.tryVoidByLedgerSource(location, source, referenceId, reason)) {
            return true;
        }
        return budgetAccountingBridge.tryVoidByLedgerSource(location, source, referenceId, reason);
    }
}
