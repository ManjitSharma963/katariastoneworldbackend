package com.katariastoneworld.apis.event;

import com.katariastoneworld.apis.service.FinancialLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies {@link BillPaymentLedgerSyncEvent} to {@link FinancialLedgerService} in the publisher's transaction
 * (synchronous listener — no {@code AFTER_COMMIT}). {@link Propagation#MANDATORY} fails if published outside a transaction.
 */
@Component
public class BillPaymentLedgerEventListener {

    @Autowired
    private FinancialLedgerService financialLedgerService;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onBillPaymentLedgerSync(BillPaymentLedgerSyncEvent event) {
        financialLedgerService.syncBillPayment(
                event.location(),
                event.billKind(),
                event.billId(),
                event.paymentId(),
                event.paymentMode(),
                event.amount(),
                event.paymentDate(),
                event.active());
    }
}
