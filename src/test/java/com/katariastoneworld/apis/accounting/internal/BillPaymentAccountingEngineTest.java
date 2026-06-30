package com.katariastoneworld.apis.accounting.internal;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.command.VoidByBillPaymentCommand;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillPaymentAccountingEngineTest {

    @Mock
    private MoneyTransactionRepository moneyTransactionRepository;

    private AccountingTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        AccountingTransactionWriter writer = new AccountingTransactionWriter(moneyTransactionRepository);
        AccountingTransactionVoider voider = new AccountingTransactionVoider(moneyTransactionRepository);
        AccountingTransactionReverser reverser = new AccountingTransactionReverser(moneyTransactionRepository);
        service = new AccountingTransactionServiceImpl(writer, voider, reverser);
    }

    @Test
    void postIn_billPayment_usesBillPaymentIdForUpsert() {
        when(moneyTransactionRepository.findByLocationAndRequestId("Bhondsi", "BILL_PAYMENT:IN:42"))
                .thenReturn(Optional.empty());
        when(moneyTransactionRepository.findFirstByBillPaymentIdAndStatusAndIsDeletedFalseOrderByIdAsc(42L, MoneyTxnStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(moneyTransactionRepository.save(any())).thenAnswer(inv -> {
            MoneyTransaction row = inv.getArgument(0);
            row.setId(100L);
            return row;
        });

        var result = service.postIn(billPaymentIn(42L));

        assertThat(result.idempotentReplay()).isFalse();
        ArgumentCaptor<MoneyTransaction> captor = ArgumentCaptor.forClass(MoneyTransaction.class);
        verify(moneyTransactionRepository).save(captor.capture());
        MoneyTransaction saved = captor.getValue();
        assertThat(saved.getBillPaymentId()).isEqualTo(42L);
        assertThat(saved.getReferenceId()).isEqualTo(900L);
        assertThat(saved.getCategory()).isEqualTo(MoneyCategory.BILL);
        assertThat(saved.getDirection()).isEqualTo(MoneyDirection.IN);
        assertThat(saved.getRequestId()).isEqualTo("BILL_PAYMENT:IN:42");
    }

    @Test
    void voidByBillPayment_cancelsWithoutHardDelete() {
        MoneyTransaction row = new MoneyTransaction();
        row.setId(5L);
        row.setBillPaymentId(42L);
        row.setStatus(MoneyTxnStatus.ACTIVE);
        row.setIsDeleted(false);
        when(moneyTransactionRepository.findByBillPaymentIdAndIsDeletedFalseOrderByIdAsc(42L))
                .thenReturn(List.of(row));
        when(moneyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.voidByBillPayment(VoidByBillPaymentCommand.of(42L, "bill payment voided"));

        ArgumentCaptor<MoneyTransaction> captor = ArgumentCaptor.forClass(MoneyTransaction.class);
        verify(moneyTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MoneyTxnStatus.CANCELLED);
        assertThat(captor.getValue().getIsDeleted()).isFalse();
        verify(moneyTransactionRepository, never()).delete(any());
    }

    @Test
    void postIn_afterVoidedBillPayment_reactivatesRowWithUpdatedFields() {
        MoneyTransaction cancelled = new MoneyTransaction();
        cancelled.setId(5L);
        cancelled.setBillPaymentId(42L);
        cancelled.setStatus(MoneyTxnStatus.CANCELLED);
        cancelled.setIsDeleted(false);
        cancelled.setPaymentMode(MoneyPaymentMode.CASH);
        cancelled.setAmount(new BigDecimal("5000.00"));
        when(moneyTransactionRepository.findByLocationAndRequestId("Bhondsi", "BILL_PAYMENT:IN:42"))
                .thenReturn(Optional.of(cancelled));
        when(moneyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.postIn(billPaymentIn(42L, MoneyPaymentMode.UPI));

        assertThat(result.idempotentReplay()).isFalse();
        ArgumentCaptor<MoneyTransaction> captor = ArgumentCaptor.forClass(MoneyTransaction.class);
        verify(moneyTransactionRepository).save(captor.capture());
        MoneyTransaction saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(MoneyTxnStatus.ACTIVE);
        assertThat(saved.getPaymentMode()).isEqualTo(MoneyPaymentMode.UPI);
    }

    private static PostMoneyCommand billPaymentIn(Long billPaymentId) {
        return billPaymentIn(billPaymentId, MoneyPaymentMode.CASH);
    }

    private static PostMoneyCommand billPaymentIn(Long billPaymentId, MoneyPaymentMode paymentMode) {
        return PostMoneyCommand.builder()
                .location("Bhondsi")
                .transactionDate(LocalDate.of(2026, 5, 18))
                .amount(new BigDecimal("5000.00"))
                .direction(MoneyDirection.IN)
                .category(MoneyCategory.BILL)
                .subCategory("BILL_PAYMENT")
                .referenceType(MoneyReferenceType.bill)
                .referenceId(900L)
                .paymentMode(paymentMode)
                .partyName("Test Customer")
                .requestId("BILL_PAYMENT:IN:" + billPaymentId)
                .billPaymentId(billPaymentId)
                .ledgerTxnType("BILL_PAYMENT")
                .eventType(AccountingEventType.BILL_PAYMENT_IN)
                .build();
    }
}
