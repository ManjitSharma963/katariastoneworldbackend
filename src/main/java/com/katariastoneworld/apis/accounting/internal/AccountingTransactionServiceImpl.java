package com.katariastoneworld.apis.accounting.internal;

import com.katariastoneworld.apis.accounting.api.AccountingTransactionService;
import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.command.ReverseMoneyCommand;
import com.katariastoneworld.apis.accounting.command.VoidByBillPaymentCommand;
import com.katariastoneworld.apis.accounting.command.VoidByReferenceCommand;
import com.katariastoneworld.apis.accounting.command.VoidByRequestIdCommand;
import com.katariastoneworld.apis.accounting.dto.PostedTransactionResult;
import com.katariastoneworld.apis.entity.MoneyDirection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountingTransactionServiceImpl implements AccountingTransactionService {

    private final AccountingTransactionWriter writer;
    private final AccountingTransactionVoider voider;
    private final AccountingTransactionReverser reverser;

    public AccountingTransactionServiceImpl(
            AccountingTransactionWriter writer,
            AccountingTransactionVoider voider,
            AccountingTransactionReverser reverser) {
        this.writer = writer;
        this.voider = voider;
        this.reverser = reverser;
    }

    @Override
    public PostedTransactionResult postIn(PostMoneyCommand command) {
        PostingValidator.validatePost(command, MoneyDirection.IN);
        return writer.post(command);
    }

    @Override
    public PostedTransactionResult postOut(PostMoneyCommand command) {
        PostingValidator.validatePost(command, MoneyDirection.OUT);
        return writer.post(command);
    }

    @Override
    public void voidByReference(VoidByReferenceCommand command) {
        voider.voidByReference(command);
    }

    @Override
    public void voidByBillPayment(VoidByBillPaymentCommand command) {
        voider.voidByBillPayment(command);
    }

    @Override
    public void voidByRequestId(VoidByRequestIdCommand command) {
        voider.voidByRequestId(command);
    }

    @Override
    public PostedTransactionResult reverseTransaction(ReverseMoneyCommand command) {
        return reverser.reverse(command);
    }
}
