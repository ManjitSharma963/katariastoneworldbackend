package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.CustomerAdvanceCreateRequestDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceResponseDTO;
import com.katariastoneworld.apis.entity.CustomerAdvance;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.Customer;
import com.katariastoneworld.apis.entity.CustomerWalletTransaction;
import com.katariastoneworld.apis.repository.CustomerAdvanceRepository;
import com.katariastoneworld.apis.repository.CustomerAdvanceUsageRepository;
import com.katariastoneworld.apis.repository.CustomerRepository;
import com.katariastoneworld.apis.repository.CustomerWalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAdvanceServiceTest {

    @Mock
    private CustomerAdvanceRepository customerAdvanceRepository;
    @Mock
    private CustomerAdvanceUsageRepository customerAdvanceUsageRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerWalletTransactionRepository customerWalletTransactionRepository;
    @Mock
    private FinancialLedgerService financialLedgerService;

    private CustomerAdvanceService service;

    @BeforeEach
    void setUp() {
        service = new CustomerAdvanceService();
        ReflectionTestUtils.setField(service, "customerAdvanceRepository", customerAdvanceRepository);
        ReflectionTestUtils.setField(service, "customerAdvanceUsageRepository", customerAdvanceUsageRepository);
        ReflectionTestUtils.setField(service, "customerRepository", customerRepository);
        ReflectionTestUtils.setField(service, "customerWalletTransactionRepository", customerWalletTransactionRepository);
        ReflectionTestUtils.setField(service, "financialLedgerService", financialLedgerService);
    }

    @Test
    void createAdvance_persistsWalletCreditAndLedger() {
        Customer c = new Customer();
        c.setId(11L);
        when(customerRepository.findByIdAndLocation(11L, "Loc")).thenReturn(Optional.of(c));
        when(customerAdvanceRepository.save(any())).thenAnswer(inv -> {
            CustomerAdvance a = inv.getArgument(0);
            a.setId(101L);
            return a;
        });

        CustomerAdvanceCreateRequestDTO dto = new CustomerAdvanceCreateRequestDTO();
        dto.setCustomerId(11L);
        dto.setAmount(50.0);
        dto.setPaymentMode("UPI");
        dto.setDescription("token");

        CustomerAdvanceResponseDTO resp = service.createAdvance(dto, "Loc");
        assertThat(resp.getId()).isEqualTo(101L);
        verify(customerWalletTransactionRepository).save(any());
        verify(financialLedgerService).recordAdvanceDeposit(
                eq("Loc"), eq(11L), eq(101L), eq(BillPaymentMode.UPI), any(), any());
    }

    @Test
    void createAdvance_rejectsNonPositiveAmount() {
        Customer c = new Customer();
        c.setId(1L);
        when(customerRepository.findByIdAndLocation(1L, "L1")).thenReturn(java.util.Optional.of(c));

        CustomerAdvanceCreateRequestDTO dto = new CustomerAdvanceCreateRequestDTO();
        dto.setCustomerId(1L);
        dto.setAmount(0.0);
        dto.setPaymentMode("CASH");

        assertThatThrownBy(() -> service.createAdvance(dto, "L1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        verify(customerAdvanceRepository, never()).save(any());
        verify(customerWalletTransactionRepository, never()).save(any());
    }

    @Test
    void applyAdvanceFifo_returnsZero_whenBillTotalZero() {
        BigDecimal applied = service.applyAdvanceFifo(9L, BillKind.GST, 3L, BigDecimal.ZERO);
        assertThat(applied).isEqualByComparingTo("0.00");
        verify(customerWalletTransactionRepository, never()).save(any());
    }

    @Test
    void applyAdvanceFifo_capsAtWalletBalance() {
        when(customerWalletTransactionRepository.getActiveWalletBalance(
                7L,
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.CREDIT))
                .thenReturn(new BigDecimal("40.00"));
        Customer cust = new Customer();
        cust.setId(7L);
        when(customerRepository.findById(7L)).thenReturn(java.util.Optional.of(cust));

        BigDecimal applied = service.applyAdvanceFifo(7L, BillKind.NON_GST, 99L, new BigDecimal("100.00"));

        assertThat(applied).isEqualByComparingTo("40.00");
        ArgumentCaptor<CustomerWalletTransaction> cap = ArgumentCaptor.forClass(CustomerWalletTransaction.class);
        verify(customerWalletTransactionRepository).save(cap.capture());
        assertThat(cap.getValue().getTxnType()).isEqualTo(CustomerWalletTransaction.TxnType.DEBIT);
        assertThat(cap.getValue().getAmount()).isEqualByComparingTo("40.00");
        assertThat(cap.getValue().getReferenceId()).isEqualTo("NON_GST:99");
    }

    @Test
    void applyAdvanceFifo_usesFullBillWhenWalletCovers() {
        when(customerWalletTransactionRepository.getActiveWalletBalance(
                2L,
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.CREDIT))
                .thenReturn(new BigDecimal("500.00"));
        Customer cust = new Customer();
        cust.setId(2L);
        when(customerRepository.findById(2L)).thenReturn(java.util.Optional.of(cust));

        BigDecimal applied = service.applyAdvanceFifo(2L, BillKind.GST, 1L, new BigDecimal("123.45"));

        assertThat(applied).isEqualByComparingTo("123.45");
        ArgumentCaptor<CustomerWalletTransaction> cap = ArgumentCaptor.forClass(CustomerWalletTransaction.class);
        verify(customerWalletTransactionRepository).save(cap.capture());
        assertThat(cap.getValue().getAmount()).isEqualByComparingTo("123.45");
        assertThat(cap.getValue().getReferenceId()).isEqualTo("GST:1");
    }

    @Test
    void reverseAdvanceUsageForBill_insertsRefundCredit_whenDebitsExist() {
        when(customerWalletTransactionRepository.sumDebitByBillReference(
                "GST:5",
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.DEBIT)).thenReturn(new BigDecimal("80.00"));
        Customer c = new Customer();
        c.setId(3L);
        CustomerWalletTransaction debit = new CustomerWalletTransaction();
        debit.setCustomer(c);
        when(customerWalletTransactionRepository.findBySourceAndReferenceIdAndTxnTypeAndStatus(
                eq("BILL_PAYMENT"),
                eq("GST:5"),
                eq(CustomerWalletTransaction.TxnType.DEBIT),
                eq(CustomerWalletTransaction.Status.ACTIVE)))
                .thenReturn(List.of(debit));

        service.reverseAdvanceUsageForBill(BillKind.GST, 5L);

        ArgumentCaptor<CustomerWalletTransaction> cap = ArgumentCaptor.forClass(CustomerWalletTransaction.class);
        verify(customerWalletTransactionRepository).save(cap.capture());
        CustomerWalletTransaction refund = cap.getValue();
        assertThat(refund.getTxnType()).isEqualTo(CustomerWalletTransaction.TxnType.CREDIT);
        assertThat(refund.getAmount()).isEqualByComparingTo("80.00");
        assertThat(refund.getSource()).isEqualTo("REFUND");
        assertThat(refund.getReferenceId()).isEqualTo("GST:5");
        assertThat(refund.getNotes()).isEqualTo("Bill cancelled");
    }

    @Test
    void reverseAdvanceUsageForBill_noOpWhenNothingUsed() {
        when(customerWalletTransactionRepository.sumDebitByBillReference(
                "NON_GST:8",
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.DEBIT)).thenReturn(BigDecimal.ZERO);

        service.reverseAdvanceUsageForBill(BillKind.NON_GST, 8L);

        verify(customerWalletTransactionRepository, never()).save(any());
    }

    @Test
    void sumAdvanceUsedForBill_delegatesToRepository() {
        when(customerWalletTransactionRepository.sumDebitByBillReference(
                "GST:2",
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.DEBIT)).thenReturn(new BigDecimal("15.25"));
        assertThat(service.sumAdvanceUsedForBill(BillKind.GST, 2L)).isEqualByComparingTo("15.25");
    }

    @Test
    void getSummary_computesRemainingFromCreditMinusDebit() {
        when(customerRepository.findByIdAndLocation(4L, "X")).thenReturn(java.util.Optional.of(new Customer()));
        when(customerWalletTransactionRepository.sumByTxnType(
                4L,
                CustomerWalletTransaction.TxnType.CREDIT,
                CustomerWalletTransaction.Status.ACTIVE))
                .thenReturn(new BigDecimal("200.00"));
        when(customerWalletTransactionRepository.sumByTxnType(
                4L,
                CustomerWalletTransaction.TxnType.DEBIT,
                CustomerWalletTransaction.Status.ACTIVE))
                .thenReturn(new BigDecimal("45.50"));

        var summary = service.getSummary(4L, "X");
        assertThat(summary.getTotalAdvance()).isEqualTo(200.0);
        assertThat(summary.getTotalUsed()).isEqualTo(45.5);
        assertThat(summary.getRemaining()).isEqualTo(154.5);
    }
}
