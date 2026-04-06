package com.katariastoneworld.apis.event;

import com.katariastoneworld.apis.service.UnifiedCashbookService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors bill payments (cash/UPI) into the unified cashbook in the same transaction as {@code bill_payments}.
 */
@Component
public class BillPaymentUnifiedCashbookListener {

    private final UnifiedCashbookService unifiedCashbookService;

    public BillPaymentUnifiedCashbookListener(UnifiedCashbookService unifiedCashbookService) {
        this.unifiedCashbookService = unifiedCashbookService;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onBillPayment(BillPaymentLedgerSyncEvent event) {
        unifiedCashbookService.syncBillPayment(event);
    }
}
