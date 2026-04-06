package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyBudgetStatusDTO;
import com.katariastoneworld.apis.entity.LedgerEntryType;
import com.katariastoneworld.apis.repository.DailyBudgetEventRepository;
import com.katariastoneworld.apis.repository.DailyBudgetRepository;
import com.katariastoneworld.apis.repository.ExpenseRepository;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyBudgetServiceTest {

    @Mock
    private DailyBudgetRepository dailyBudgetRepository;
    @Mock
    private FinancialLedgerRepository financialLedgerRepository;
    @Mock
    private DailyBudgetEventRepository dailyBudgetEventRepository;
    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private DailyBudgetService dailyBudgetService;

    @Test
    void getBudgetStatus_noBudget_returnsZerosAndLedgerTotals() {
        LocalDate day = LocalDate.of(2026, 3, 15);
        when(dailyBudgetRepository.findFirstByLocationAndUserIdIsNull("Tapugada")).thenReturn(Optional.empty());
        when(dailyBudgetRepository.findByLocation("Tapugada")).thenReturn(Optional.empty());
        when(expenseRepository.findByLocationAndDate("Tapugada", day)).thenReturn(Collections.emptyList());
        when(financialLedgerRepository.sumAmountByLocationAndDateAndEntryType(eq("Tapugada"), eq(day), eq(LedgerEntryType.CREDIT)))
                .thenReturn(new BigDecimal("100.00"));
        when(financialLedgerRepository.sumAmountByLocationAndDateAndEntryType(eq("Tapugada"), eq(day), eq(LedgerEntryType.DEBIT)))
                .thenReturn(new BigDecimal("40.00"));
        when(financialLedgerRepository.sumBillPaymentCreditsCashUpiByLocationAndDate(eq("Tapugada"), eq(day)))
                .thenReturn(BigDecimal.ZERO);

        DailyBudgetStatusDTO dto = dailyBudgetService.getBudgetStatus("Tapugada", day);

        assertNotNull(dto);
        assertEquals(0, dto.getBudgetAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, dto.getSpentAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, dto.getRemainingAmount().compareTo(BigDecimal.ZERO.setScale(2)));
        assertEquals(0, dto.getLedgerCreditTotal().compareTo(new BigDecimal("100.00")));
        assertEquals(0, dto.getLedgerDebitTotal().compareTo(new BigDecimal("40.00")));
        assertEquals(0, dto.getLedgerNetAmount().compareTo(new BigDecimal("60.00")));
        assertEquals(day, dto.getDate());
        assertEquals("Tapugada", dto.getLocation());
    }
}
