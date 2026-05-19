package com.katariastoneworld.apis.accounting.internal;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.command.VoidByReferenceCommand;
import com.katariastoneworld.apis.accounting.domain.AccountingEventType;
import com.katariastoneworld.apis.accounting.dto.AccountingReference;
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
class AccountingTransactionServiceExpenseTest {

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
    void postOut_isIdempotentByRequestId() {
        MoneyTransaction existing = new MoneyTransaction();
        existing.setId(99L);
        when(moneyTransactionRepository.findByLocationAndRequestId("Bhondsi", "EXPENSE:OUT:7"))
                .thenReturn(Optional.of(existing));

        var result = service.postOut(expensePost(7L));

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.transactionId()).isEqualTo(99L);
        verify(moneyTransactionRepository, never()).save(any());
    }

    @Test
    void voidByReference_cancelsActiveRowWithoutDelete() {
        MoneyTransaction row = new MoneyTransaction();
        row.setId(12L);
        row.setLocation("Bhondsi");
        row.setStatus(MoneyTxnStatus.ACTIVE);
        row.setIsDeleted(false);
        when(moneyTransactionRepository.findByLocationAndReferenceTypeAndReferenceIdAndCategoryAndStatusAndIsDeletedFalse(
                eq("Bhondsi"),
                eq(MoneyReferenceType.expense),
                eq(7L),
                eq(MoneyCategory.EXPENSE),
                eq(MoneyTxnStatus.ACTIVE)))
                .thenReturn(List.of(row));
        when(moneyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.voidByReference(VoidByReferenceCommand.of(
                "Bhondsi",
                AccountingReference.of(MoneyReferenceType.expense, 7L, MoneyCategory.EXPENSE),
                "expense deleted"));

        ArgumentCaptor<MoneyTransaction> captor = ArgumentCaptor.forClass(MoneyTransaction.class);
        verify(moneyTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MoneyTxnStatus.CANCELLED);
        assertThat(captor.getValue().getVoidReason()).isEqualTo("expense deleted");
        verify(moneyTransactionRepository, never()).delete(any());
    }

    private static PostMoneyCommand expensePost(Long expenseId) {
        return PostMoneyCommand.builder()
                .location("Bhondsi")
                .transactionDate(LocalDate.of(2026, 5, 18))
                .amount(new BigDecimal("1500.00"))
                .direction(MoneyDirection.OUT)
                .category(MoneyCategory.EXPENSE)
                .referenceType(MoneyReferenceType.expense)
                .referenceId(expenseId)
                .paymentMode(MoneyPaymentMode.CASH)
                .requestId("EXPENSE:OUT:" + expenseId)
                .eventType(AccountingEventType.EXPENSE_OUT)
                .build();
    }
}
