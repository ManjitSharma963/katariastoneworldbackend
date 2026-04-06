package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.LedgerRequest;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.LedgerEntryType;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialLedgerServiceTest {

    @Mock
    private FinancialLedgerRepository financialLedgerRepository;

    @InjectMocks
    private FinancialLedgerService financialLedgerService;

    @Test
    void createEntry_rejectsNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> financialLedgerService.createEntry(null));
    }

    @Test
    void createEntry_rejectsBlankLocation() {
        LedgerRequest req = LedgerRequest.builder()
                .location("  ")
                .sourceType("BILL_PAYMENT")
                .sourceId("1")
                .entryType(LedgerEntryType.CREDIT)
                .amount(new BigDecimal("10.00"))
                .paymentMode(BillPaymentMode.CASH)
                .build();
        assertThrows(IllegalArgumentException.class, () -> financialLedgerService.createEntry(req));
    }

    @Test
    void createEntry_idempotentWhenActiveRowExists() {
        when(financialLedgerRepository.findBySourceTypeAndSourceIdAndIsDeletedFalse("BILL_PAYMENT", "99"))
                .thenReturn(Optional.of(new com.katariastoneworld.apis.entity.FinancialLedgerEntry()));

        LedgerRequest req = LedgerRequest.builder()
                .location("Shop")
                .sourceType("BILL_PAYMENT")
                .sourceId("99")
                .entryType(LedgerEntryType.CREDIT)
                .amount(new BigDecimal("50.00"))
                .paymentMode(BillPaymentMode.UPI)
                .build();

        financialLedgerService.createEntry(req);

        verify(financialLedgerRepository, never()).save(any());
    }

    @Test
    void createEntry_persistsWhenNoRows() {
        when(financialLedgerRepository.findBySourceTypeAndSourceIdAndIsDeletedFalse("EXPENSE", "e1"))
                .thenReturn(Optional.empty());
        when(financialLedgerRepository.findBySourceTypeAndSourceId("EXPENSE", "e1"))
                .thenReturn(Collections.emptyList());

        LedgerRequest req = LedgerRequest.builder()
                .location("Shop")
                .sourceType("EXPENSE")
                .sourceId("e1")
                .entryType(LedgerEntryType.DEBIT)
                .amount(new BigDecimal("25.00"))
                .paymentMode(BillPaymentMode.CASH)
                .build();

        financialLedgerService.createEntry(req);

        verify(financialLedgerRepository).save(any());
    }
}
