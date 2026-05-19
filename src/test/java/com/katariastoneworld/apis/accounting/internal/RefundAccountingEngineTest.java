package com.katariastoneworld.apis.accounting.internal;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.command.VoidByRequestIdCommand;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.entity.MoneyTxnStatus;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundAccountingEngineTest {

    @Mock
    private MoneyTransactionRepository moneyTransactionRepository;

    private AccountingTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountingTransactionServiceImpl(
                new AccountingTransactionWriter(moneyTransactionRepository),
                new AccountingTransactionVoider(moneyTransactionRepository),
                new AccountingTransactionReverser(moneyTransactionRepository));
    }

    @Test
    void postOut_stockReturnRefund_isIdempotentByTxnType() {
        MoneyTransaction existing = new MoneyTransaction();
        existing.setId(77L);
        when(moneyTransactionRepository.findByLocationAndRequestId("Bhondsi", "REFUND:STOCK_RETURN:9"))
                .thenReturn(Optional.empty());
        when(moneyTransactionRepository.findFirstByReferenceTypeAndReferenceIdAndTxnTypeAndStatusAndIsDeletedFalseOrderByIdAsc(
                eq(MoneyReferenceType.bill),
                eq(100L),
                eq("STOCK_RETURN_9"),
                eq(MoneyTxnStatus.ACTIVE)))
                .thenReturn(Optional.of(existing));

        var result = service.postOut(stockReturnRefund());

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.transactionId()).isEqualTo(77L);
        verify(moneyTransactionRepository, never()).save(any());
    }

    @Test
    void voidByRequestId_cancelsWithoutHardDelete() {
        MoneyTransaction row = new MoneyTransaction();
        row.setId(88L);
        row.setLocation("Bhondsi");
        row.setRequestId("REFUND:STOCK_RETURN:9");
        row.setStatus(MoneyTxnStatus.ACTIVE);
        row.setIsDeleted(false);
        when(moneyTransactionRepository.findByLocationAndRequestId("Bhondsi", "REFUND:STOCK_RETURN:9"))
                .thenReturn(Optional.of(row));
        when(moneyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.voidByRequestId(VoidByRequestIdCommand.of("Bhondsi", "REFUND:STOCK_RETURN:9", "stock return reversed"));

        ArgumentCaptor<MoneyTransaction> captor = ArgumentCaptor.forClass(MoneyTransaction.class);
        verify(moneyTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MoneyTxnStatus.CANCELLED);
        assertThat(captor.getValue().getIsDeleted()).isFalse();
        verify(moneyTransactionRepository, never()).delete(any());
    }

    private static PostMoneyCommand stockReturnRefund() {
        return PostMoneyCommand.builder()
                .location("Bhondsi")
                .transactionDate(LocalDate.of(2026, 5, 18))
                .amount(new BigDecimal("1200.00"))
                .direction(MoneyDirection.OUT)
                .category(MoneyCategory.BILL_RETURN)
                .subCategory(MoneyLedgerCategories.SUB_CUSTOMER_REFUND)
                .referenceType(MoneyReferenceType.bill)
                .referenceId(100L)
                .paymentMode(MoneyPaymentMode.CASH)
                .requestId("REFUND:STOCK_RETURN:9")
                .ledgerTxnType("STOCK_RETURN_9")
                .eventType(AccountingEventType.REFUND_STOCK_RETURN)
                .build();
    }
}
