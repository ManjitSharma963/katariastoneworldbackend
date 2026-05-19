package com.katariastoneworld.apis.accounting.support;

import com.katariastoneworld.apis.accounting.api.AccountingTransactionService;
import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.accounting.config.AccountingFeatureFlags;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import com.katariastoneworld.apis.service.MoneyTransactionLegacySync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingMigrationBridgesTest {

    @Mock
    private AccountingFeatureFlags flags;
    @Mock
    private AccountingTransactionService accountingTransactionService;
    @Mock
    private MoneyTransactionLegacySync legacySync;
    @Mock
    private MoneyTransactionRepository moneyTransactionRepository;

    private AccountingPostingSupport posting;
    private CustomerAdvanceAccountingBridge advanceBridge;
    private LoanAccountingBridge loanBridge;
    private PayrollAccountingBridge payrollBridge;

    @BeforeEach
    void setUp() {
        posting = new AccountingPostingSupport(accountingTransactionService);
        advanceBridge = new CustomerAdvanceAccountingBridge(flags, posting, legacySync);
        loanBridge = new LoanAccountingBridge(flags, posting, legacySync);
        payrollBridge = new PayrollAccountingBridge(flags, posting, legacySync);
    }

    @Test
    void advanceDeposit_postsThroughEngineWithBalancedMetadata() {
        when(flags.isAdvanceEnabled()).thenReturn(true);
        MoneyTransaction posted = new MoneyTransaction();
        posted.setId(1L);
        when(accountingTransactionService.postIn(any())).thenReturn(
                com.katariastoneworld.apis.accounting.dto.PostedTransactionResult.from(posted, false));

        advanceBridge.postAdvanceDeposit(
                "Bhondsi", 10L, 99L, BillPaymentMode.CASH, new BigDecimal("500.00"), LocalDate.now(), "test");

        ArgumentCaptor<PostMoneyCommand> cap = ArgumentCaptor.forClass(PostMoneyCommand.class);
        verify(accountingTransactionService).postIn(cap.capture());
        PostMoneyCommand cmd = cap.getValue();
        assertThat(cmd.requestId()).isEqualTo("ADVANCE:DEPOSIT:99");
        assertThat(cmd.direction()).isEqualTo(MoneyDirection.IN);
        assertThat(cmd.metadataJson()).contains("CASH").contains("CUSTOMER_ADVANCE_LIABILITY");
        verify(legacySync, never()).syncFromUnified(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void advanceDeposit_legacyWhenFlagOff() {
        when(flags.isAdvanceEnabled()).thenReturn(false);

        advanceBridge.postAdvanceDeposit(
                "Bhondsi", 10L, 99L, BillPaymentMode.CASH, new BigDecimal("100.00"), LocalDate.now(), "test");

        verify(legacySync).syncFromUnified(
                eq("Bhondsi"), any(), eq(new BigDecimal("100.00")), any(), any(), eq("ADVANCE"), eq(99L), any());
        verify(accountingTransactionService, never()).postIn(any());
    }

    @Test
    void loanReceived_postsThroughEngine() {
        when(flags.isLoanEnabled()).thenReturn(true);
        MoneyTransaction posted = new MoneyTransaction();
        posted.setId(2L);
        when(accountingTransactionService.postIn(any())).thenReturn(
                com.katariastoneworld.apis.accounting.dto.PostedTransactionResult.from(posted, false));

        loanBridge.postLoanReceived(
                "Bhondsi", 5L, 1L, "Lender A", new BigDecimal("1000"), null, LocalDate.now(), "loan");

        ArgumentCaptor<PostMoneyCommand> cap = ArgumentCaptor.forClass(PostMoneyCommand.class);
        verify(accountingTransactionService).postIn(cap.capture());
        assertThat(cap.getValue().requestId()).isEqualTo("LOAN:RECEIVED:5");
        assertThat(cap.getValue().metadataJson()).contains("MARKET_LOAN_LIABILITY");
    }

    @Test
    void payrollAdvance_postsThroughEngine() {
        when(flags.isPayrollEnabled()).thenReturn(true);
        MoneyTransaction posted = new MoneyTransaction();
        posted.setId(3L);
        when(accountingTransactionService.postOut(any())).thenReturn(
                com.katariastoneworld.apis.accounting.dto.PostedTransactionResult.from(posted, false));

        payrollBridge.postSalaryAdvance(
                "Bhondsi", 7L, 2L, "Emp", new BigDecimal("200"), BillPaymentMode.CASH, LocalDate.now(), "adv");

        ArgumentCaptor<PostMoneyCommand> cap = ArgumentCaptor.forClass(PostMoneyCommand.class);
        verify(accountingTransactionService).postOut(cap.capture());
        assertThat(cap.getValue().requestId()).isEqualTo("PAYROLL:SALARY_ADVANCE:7");
        assertThat(cap.getValue().metadataJson()).contains("EMPLOYEE_ADVANCE");
    }
}
