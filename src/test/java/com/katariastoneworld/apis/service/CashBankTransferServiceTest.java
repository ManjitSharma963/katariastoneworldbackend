package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.accounting.support.CashBankTransferAccountingBridge;
import com.katariastoneworld.apis.dto.BalanceSummaryDTO;
import com.katariastoneworld.apis.dto.CashBankTransferHistoryDTO;
import com.katariastoneworld.apis.dto.CashBankTransferRequestDTO;
import com.katariastoneworld.apis.entity.CashBankTransferDirection;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashBankTransferServiceTest {

    @Mock
    private CashBankTransferAccountingBridge cashBankTransferAccountingBridge;

    @Mock
    private BalanceSummaryService balanceSummaryService;

    @Mock
    private MoneyTransactionRepository moneyTransactionRepository;

    @InjectMocks
    private CashBankTransferService service;

    @Test
    void listHistory_groupsPairedLegs() {
        MoneyTransaction out = new MoneyTransaction();
        out.setId(1L);
        out.setLinkedGroupId("grp-1");
        out.setDirection(MoneyDirection.OUT);
        out.setPaymentMode(MoneyPaymentMode.CASH);
        out.setAmount(new BigDecimal("5000.00"));
        out.setTransactionDate(LocalDate.of(2026, 6, 27));
        out.setNotes("HDFC deposit");

        MoneyTransaction in = new MoneyTransaction();
        in.setId(2L);
        in.setLinkedGroupId("grp-1");
        in.setDirection(MoneyDirection.IN);
        in.setPaymentMode(MoneyPaymentMode.BANK);
        in.setAmount(new BigDecimal("5000.00"));
        in.setTransactionDate(LocalDate.of(2026, 6, 27));

        when(moneyTransactionRepository.findCashBankTransferRows(
                eq("LOC"), any(), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(List.of(out, in));

        List<CashBankTransferHistoryDTO> history = service.listHistory("LOC", null, null, 50);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getDirection()).isEqualTo("CASH_TO_BANK");
        assertThat(history.get(0).getAmount()).isEqualByComparingTo("5000.00");
        assertThat(history.get(0).getNotes()).contains("HDFC");
    }

    @Test
    void transfer_cashToBank_postsWhenBalanceSufficient() {
        BalanceSummaryDTO before = new BalanceSummaryDTO();
        before.setInHand(new BigDecimal("5000.00"));
        before.setBank(new BigDecimal("1000.00"));
        BalanceSummaryDTO after = new BalanceSummaryDTO();
        after.setInHand(new BigDecimal("3000.00"));
        after.setBank(new BigDecimal("3000.00"));

        when(balanceSummaryService.getSummary("LOC")).thenReturn(before, after);

        CashBankTransferRequestDTO req = new CashBankTransferRequestDTO();
        req.setAmount(new BigDecimal("2000"));
        req.setDirection("CASH_TO_BANK");

        BalanceSummaryDTO result = service.transfer("LOC", req);

        verify(cashBankTransferAccountingBridge).postTransfer(
                eq("LOC"),
                eq(new BigDecimal("2000.00")),
                eq(CashBankTransferDirection.CASH_TO_BANK),
                any(),
                eq(null));
        assertThat(result.getInHand()).isEqualByComparingTo("3000.00");
    }

    @Test
    void transfer_rejectsInsufficientCash() {
        BalanceSummaryDTO before = new BalanceSummaryDTO();
        before.setInHand(new BigDecimal("100.00"));
        before.setBank(BigDecimal.ZERO);
        when(balanceSummaryService.getSummary("LOC")).thenReturn(before);

        CashBankTransferRequestDTO req = new CashBankTransferRequestDTO();
        req.setAmount(new BigDecimal("500"));
        req.setDirection("CASH_TO_BANK");

        assertThatThrownBy(() -> service.transfer("LOC", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient cash+UPI");
    }
}
