package com.katariastoneworld.apis.constants;

import com.katariastoneworld.apis.entity.MoneyCategory;

import java.util.List;

/**
 * Ledger categories that represent customer refunds / revenue reversals — never operating expenses.
 */
public final class MoneyLedgerCategories {

    public static final String SUB_CUSTOMER_REFUND = "CUSTOMER_REFUND";
    public static final String SUB_BILL_CANCELLATION = "BILL_CANCELLATION";
    /** Supplementary / exchange: customer pays additional amount on linked bill. */
    public static final String SUB_ADJUSTMENT_PAYMENT = "ADJUSTMENT_PAYMENT";
    /** Supplementary / exchange: refund when return value exceeds new items. */
    public static final String SUB_ADJUSTMENT_REFUND = "ADJUSTMENT_REFUND";
    /** Overpayment vs effective obligation restored to customer wallet (adjustment row, not silent edit). */
    public static final String SUB_ADVANCE_RESTORE = "ADVANCE_RESTORE";
    /** Bill settlement from customer wallet — liability release, not new cash/UPI receipt. See {@link com.katariastoneworld.apis.service.BillService#createTransactionFromBillPayment}. */
    public static final String SUB_ADVANCE_APPLICATION = "ADVANCE_APPLICATION";

    public static final List<MoneyCategory> NON_EXPENSE_OUT = List.of(
            MoneyCategory.BILL_REVERSAL,
            MoneyCategory.BILL_RETURN);

    private MoneyLedgerCategories() {
    }
}
