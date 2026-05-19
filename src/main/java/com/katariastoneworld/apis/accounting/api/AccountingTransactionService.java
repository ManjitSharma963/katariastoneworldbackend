package com.katariastoneworld.apis.accounting.api;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.command.ReverseMoneyCommand;
import com.katariastoneworld.apis.accounting.command.VoidByBillPaymentCommand;
import com.katariastoneworld.apis.accounting.command.VoidByReferenceCommand;
import com.katariastoneworld.apis.accounting.command.VoidByRequestIdCommand;
import com.katariastoneworld.apis.accounting.dto.PostedTransactionResult;

/**
 * Central accounting engine for {@code transactions} (append-only; void instead of delete).
 */
public interface AccountingTransactionService {

    PostedTransactionResult postIn(PostMoneyCommand command);

    PostedTransactionResult postOut(PostMoneyCommand command);

    void voidByReference(VoidByReferenceCommand command);

    void voidByBillPayment(VoidByBillPaymentCommand command);

    void voidByRequestId(VoidByRequestIdCommand command);

    PostedTransactionResult reverseTransaction(ReverseMoneyCommand command);
}
