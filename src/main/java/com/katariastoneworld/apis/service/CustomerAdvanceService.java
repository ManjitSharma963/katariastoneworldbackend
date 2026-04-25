package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.CustomerAdvanceCreateRequestDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceHistoryEntryDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceResponseDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceSummaryDTO;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.Customer;
import com.katariastoneworld.apis.entity.CustomerAdvance;
import com.katariastoneworld.apis.entity.CustomerAdvanceUsage;
import com.katariastoneworld.apis.entity.CustomerWalletTransaction;
import com.katariastoneworld.apis.repository.CustomerAdvanceRepository;
import com.katariastoneworld.apis.repository.CustomerAdvanceUsageRepository;
import com.katariastoneworld.apis.repository.CustomerRepository;
import com.katariastoneworld.apis.repository.CustomerWalletTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class CustomerAdvanceService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Autowired
    private CustomerAdvanceRepository customerAdvanceRepository;

    @Autowired
    private CustomerAdvanceUsageRepository customerAdvanceUsageRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerWalletTransactionRepository customerWalletTransactionRepository;

    @Autowired
    private FinancialLedgerService financialLedgerService;

    public CustomerAdvanceResponseDTO createAdvance(CustomerAdvanceCreateRequestDTO dto, String location) {
        Customer customer = customerRepository.findByIdAndLocation(dto.getCustomerId(), location)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + dto.getCustomerId()));
        BigDecimal amt = BigDecimal.valueOf(dto.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Advance amount must be positive");
        }
        CustomerAdvance row = new CustomerAdvance();
        row.setCustomer(customer);
        row.setAmount(amt);
        row.setRemainingAmount(amt);
        row.setPaymentMode(parseAdvancePaymentMode(dto.getPaymentMode()));
        row.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        CustomerAdvance saved = customerAdvanceRepository.save(row);

        // New wallet-ledger insert (while keeping legacy customer_advance insert for safety).
        CustomerWalletTransaction walletTxn = new CustomerWalletTransaction();
        walletTxn.setCustomer(customer);
        walletTxn.setTxnType(CustomerWalletTransaction.TxnType.CREDIT);
        walletTxn.setAmount(amt);
        walletTxn.setSource("ADVANCE_DEPOSIT");
        walletTxn.setPaymentMode(saved.getPaymentMode());
        walletTxn.setNotes(saved.getDescription());
        walletTxn.setStatus(CustomerWalletTransaction.Status.ACTIVE);
        customerWalletTransactionRepository.save(walletTxn);

        financialLedgerService.recordAdvanceDeposit(location, customer.getId(), saved.getId(),
                saved.getPaymentMode() != null ? saved.getPaymentMode() : BillPaymentMode.CASH,
                saved.getAmount(), saved.getCreatedAt() != null ? saved.getCreatedAt().toLocalDate() : null);
        return toResponseDTO(saved);
    }

    /** Wallet-ledger apply: debit from active balance for this bill reference. */
    public BigDecimal applyAdvanceFifo(Long customerId, BillKind billKind, Long billId, BigDecimal billTotal) {
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(billKind);
        Objects.requireNonNull(billId);
        BigDecimal gross = billTotal.setScale(2, RoundingMode.HALF_UP);
        if (gross.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        BigDecimal walletBalance = getWalletBalance(customerId);
        BigDecimal targetUse = walletBalance.min(gross).setScale(2, RoundingMode.HALF_UP);
        if (targetUse.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));
        String ref = walletReferenceForBill(billKind, billId);
        BigDecimal remainingNeeded = targetUse;

        // Reconstruct per-credit remaining using FIFO:
        // first absorb all prior debits, then consume remaining credits for this bill.
        List<CustomerWalletTransaction> txns = customerWalletTransactionRepository
                .findByCustomer_IdAndStatusOrderByCreatedAtAscIdAsc(
                        customerId,
                        CustomerWalletTransaction.Status.ACTIVE);
        BigDecimal priorDebits = ZERO;
        for (CustomerWalletTransaction t : txns) {
            if (t.getTxnType() == CustomerWalletTransaction.TxnType.DEBIT) {
                priorDebits = priorDebits.add(nvl(t.getAmount()));
            }
        }

        BigDecimal debitToAllocate = priorDebits;
        for (CustomerWalletTransaction t : txns) {
            if (remainingNeeded.compareTo(ZERO) <= 0) break;
            if (t.getTxnType() != CustomerWalletTransaction.TxnType.CREDIT) continue;
            BigDecimal creditRemaining = nvl(t.getAmount());
            if (creditRemaining.compareTo(ZERO) <= 0) continue;

            if (debitToAllocate.compareTo(ZERO) > 0) {
                BigDecimal absorb = creditRemaining.min(debitToAllocate);
                creditRemaining = creditRemaining.subtract(absorb).setScale(2, RoundingMode.HALF_UP);
                debitToAllocate = debitToAllocate.subtract(absorb).setScale(2, RoundingMode.HALF_UP);
            }

            if (creditRemaining.compareTo(ZERO) <= 0) continue;

            BigDecimal use = creditRemaining.min(remainingNeeded).setScale(2, RoundingMode.HALF_UP);
            if (use.compareTo(ZERO) <= 0) continue;

            CustomerWalletTransaction walletTxn = new CustomerWalletTransaction();
            walletTxn.setCustomer(customer);
            walletTxn.setTxnType(CustomerWalletTransaction.TxnType.DEBIT);
            walletTxn.setAmount(use);
            walletTxn.setSource("BILL_PAYMENT");
            walletTxn.setReferenceId(ref);
            walletTxn.setNotes("Used in bill");
            // Carry forward original deposit mode so sales mode totals can bucket advance correctly.
            walletTxn.setPaymentMode(t.getPaymentMode() != null ? t.getPaymentMode() : BillPaymentMode.OTHER);
            walletTxn.setStatus(CustomerWalletTransaction.Status.ACTIVE);
            customerWalletTransactionRepository.save(walletTxn);

            remainingNeeded = remainingNeeded.subtract(use).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal used = targetUse.subtract(remainingNeeded).setScale(2, RoundingMode.HALF_UP);
        return used.compareTo(ZERO) > 0 ? used : ZERO;
    }

    public BigDecimal sumAdvanceUsedForBill(BillKind billKind, Long billId) {
        return nvl(customerWalletTransactionRepository
                .sumDebitByBillReference(
                        walletReferenceForBill(billKind, billId),
                        CustomerWalletTransaction.Status.ACTIVE,
                        CustomerWalletTransaction.TxnType.DEBIT));
    }

    /** Reverse by inserting a CREDIT refund wallet transaction. */
    public void reverseAdvanceUsageForBill(BillKind billKind, Long billId) {
        String ref = walletReferenceForBill(billKind, billId);
        BigDecimal used = nvl(customerWalletTransactionRepository.sumDebitByBillReference(
                ref,
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.DEBIT));
        if (used.compareTo(ZERO) <= 0) {
            return;
        }
        List<CustomerWalletTransaction> txns = customerWalletTransactionRepository
                .findBySourceAndReferenceIdAndTxnTypeAndStatus(
                        "BILL_PAYMENT",
                        ref,
                        CustomerWalletTransaction.TxnType.DEBIT,
                        CustomerWalletTransaction.Status.ACTIVE);
        if (txns.isEmpty()) {
            return;
        }
        Customer customer = txns.get(0).getCustomer();
        CustomerWalletTransaction refund = new CustomerWalletTransaction();
        refund.setCustomer(customer);
        refund.setTxnType(CustomerWalletTransaction.TxnType.CREDIT);
        refund.setAmount(used);
        refund.setSource("REFUND");
        refund.setReferenceId(ref);
        refund.setNotes("Bill cancelled");
        refund.setStatus(CustomerWalletTransaction.Status.ACTIVE);
        customerWalletTransactionRepository.save(refund);
    }

    public Map<String, BigDecimal> sumAdvanceUsedGrouped(Collection<Long> gstBillIds, Collection<Long> nonGstBillIds) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (gstBillIds != null && !gstBillIds.isEmpty()) {
            for (Long id : gstBillIds) {
                String k = "GST:" + id;
                map.put(k, sumAdvanceUsedForBill(BillKind.GST, id));
            }
        }
        if (nonGstBillIds != null && !nonGstBillIds.isEmpty()) {
            for (Long id : nonGstBillIds) {
                String k = "NON_GST:" + id;
                map.put(k, sumAdvanceUsedForBill(BillKind.NON_GST, id));
            }
        }
        return map;
    }

    public CustomerAdvanceSummaryDTO getSummary(Long customerId, String location) {
        customerRepository.findByIdAndLocation(customerId, location)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));
        // Deposits only — do not count REFUND credits (bill cancel) as "new advance"; those restore wallet balance.
        BigDecimal totalAdvance = nvl(customerWalletTransactionRepository.sumByCustomerIdAndStatusAndTxnTypeAndSource(
                customerId,
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.CREDIT,
                "ADVANCE_DEPOSIT"));
        // Usage on bills only (wallet debits from advance application).
        BigDecimal totalUsed = nvl(customerWalletTransactionRepository.sumByCustomerIdAndStatusAndTxnTypeAndSource(
                customerId,
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.DEBIT,
                "BILL_PAYMENT"));
        BigDecimal totalRemaining = getWalletBalance(customerId).setScale(2, RoundingMode.HALF_UP);
        CustomerAdvanceSummaryDTO dto = new CustomerAdvanceSummaryDTO();
        dto.setTotalAdvance(totalAdvance.doubleValue());
        dto.setTotalUsed(totalUsed.doubleValue());
        dto.setRemaining(totalRemaining.doubleValue());
        return dto;
    }

    public List<CustomerAdvanceHistoryEntryDTO> getHistory(Long customerId, String location) {
        customerRepository.findByIdAndLocation(customerId, location)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));
        List<CustomerWalletTransaction> txns = customerWalletTransactionRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId);
        List<CustomerAdvanceHistoryEntryDTO> entries = new ArrayList<>();
        for (CustomerWalletTransaction t : txns) {
            String kind = t.getTxnType() == CustomerWalletTransaction.TxnType.CREDIT ? "DEPOSIT" : "USAGE";
            Long billId = parseBillIdFromReference(t.getReferenceId());
            String billKind = parseBillKindFromReference(t.getReferenceId());
            entries.add(new CustomerAdvanceHistoryEntryDTO(
                    kind,
                    t.getCreatedAt(),
                    t.getAmount() != null ? t.getAmount().doubleValue() : 0.0,
                    t.getNotes() != null ? t.getNotes() : t.getSource(),
                    t.getPaymentMode() != null ? t.getPaymentMode().name() : null,
                    billId,
                    billKind,
                    t.getId()));
        }
        entries.sort(Comparator.comparing(CustomerAdvanceHistoryEntryDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return entries;
    }

    private BigDecimal getWalletBalance(Long customerId) {
        return nvl(customerWalletTransactionRepository.getActiveWalletBalance(
                customerId,
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.CREDIT));
    }

    private static BigDecimal nvl(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String walletReferenceForBill(BillKind billKind, Long billId) {
        return billKind.name() + ":" + billId;
    }

    private static Long parseBillIdFromReference(String referenceId) {
        if (referenceId == null || !referenceId.contains(":")) {
            return null;
        }
        String[] parts = referenceId.split(":", 2);
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String parseBillKindFromReference(String referenceId) {
        if (referenceId == null || !referenceId.contains(":")) {
            return null;
        }
        return referenceId.split(":", 2)[0];
    }

    private static CustomerAdvanceResponseDTO toResponseDTO(CustomerAdvance a) {
        CustomerAdvanceResponseDTO dto = new CustomerAdvanceResponseDTO();
        dto.setId(a.getId());
        dto.setCustomerId(a.getCustomer() != null ? a.getCustomer().getId() : null);
        dto.setAmount(a.getAmount().doubleValue());
        dto.setRemainingAmount(a.getRemainingAmount().doubleValue());
        dto.setPaymentMode(a.getPaymentMode() != null ? a.getPaymentMode().name() : null);
        dto.setDescription(a.getDescription());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }

    private static BillPaymentMode parseAdvancePaymentMode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return BillPaymentMode.CASH;
        }
        try {
            return BillPaymentMode.parseFlexible(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid payment mode. Use CASH, UPI, BANK_TRANSFER, CHEQUE, or WALLET.");
        }
    }
}
