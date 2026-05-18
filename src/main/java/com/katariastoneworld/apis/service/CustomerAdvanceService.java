package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.CustomerAdvanceCreateRequestDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceHistoryEntryDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceResponseDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceRefundRequestDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceRefundResponseDTO;
import com.katariastoneworld.apis.dto.CustomerAdvanceSummaryDTO;
import com.katariastoneworld.apis.entity.BillGST;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillNonGST;
import com.katariastoneworld.apis.entity.BillPayment;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.Customer;
import com.katariastoneworld.apis.entity.CustomerAdvance;
import com.katariastoneworld.apis.entity.CustomerAdvanceUsage;
import com.katariastoneworld.apis.entity.CustomerWalletTransaction;
import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
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

/**
 * Customer wallet and advance application for bills. Primary ledger: {@code customer_wallet_transactions}
 * (credits {@code ADVANCE_DEPOSIT}, debits {@code BILL_PAYMENT} against reference {@code NON_GST:billId} / {@code GST:billId}).
 * {@code customer_advance} rows support legacy deposits; {@code customer_advance_usage} is legacy/read-side in some reports —
 * new bill application flows use wallet debits + {@code bill_payments} rows with {@code sourceType=ADVANCE} as the mirror for UI.
 */
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
    private BillGSTRepository billGSTRepository;

    @Autowired
    private BillNonGSTRepository billNonGSTRepository;

    @Autowired
    private BillPaymentRepository billPaymentRepository;

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

    public CustomerAdvanceRefundResponseDTO refundAdvance(CustomerAdvanceRefundRequestDTO dto, String location) {
        Customer customer = customerRepository.findByIdAndLocation(dto.getCustomerId(), location)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + dto.getCustomerId()));
        BigDecimal amt = BigDecimal.valueOf(dto.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        BigDecimal walletBalance = getWalletBalance(customer.getId());
        if (amt.compareTo(walletBalance) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount exceeds available wallet balance: " + walletBalance.toPlainString());
        }
        BillPaymentMode mode = parseAdvancePaymentMode(dto.getPaymentMode());

        CustomerWalletTransaction walletTxn = new CustomerWalletTransaction();
        walletTxn.setCustomer(customer);
        walletTxn.setTxnType(CustomerWalletTransaction.TxnType.DEBIT);
        walletTxn.setAmount(amt);
        walletTxn.setSource("ADVANCE_REFUND");
        walletTxn.setPaymentMode(mode);
        walletTxn.setNotes(dto.getDescription() != null ? dto.getDescription().trim() : null);
        walletTxn.setStatus(CustomerWalletTransaction.Status.ACTIVE);
        CustomerWalletTransaction saved = customerWalletTransactionRepository.save(walletTxn);

        financialLedgerService.recordAdvanceRefund(location, customer.getId(), saved.getId(), mode, saved.getAmount(),
                saved.getCreatedAt() != null ? saved.getCreatedAt().toLocalDate() : null);

        CustomerAdvanceRefundResponseDTO response = new CustomerAdvanceRefundResponseDTO();
        response.setCustomerId(customer.getId());
        response.setRefundedAmount(saved.getAmount().doubleValue());
        response.setRemainingWalletBalance(getWalletBalance(customer.getId()).doubleValue());
        response.setPaymentMode(saved.getPaymentMode() != null ? saved.getPaymentMode().name() : null);
        response.setDescription(saved.getNotes());
        response.setWalletTransactionId(saved.getId());
        return response;
    }

    /** Wallet-ledger apply: debit from active balance for this bill reference. */
    public BigDecimal applyAdvanceFifo(Long customerId, BillKind billKind, Long billId, BigDecimal billTotal) {
        return applyAdvanceFifo(customerId, billKind, billId, billTotal, null, null);
    }

    /** Wallet-ledger apply with optional mutation-group metadata. */
    public BigDecimal applyAdvanceFifo(Long customerId, BillKind billKind, Long billId, BigDecimal billTotal,
            Long billVersionId, String linkedGroupId) {
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
            walletTxn.setBillVersionId(billVersionId);
            walletTxn.setLinkedGroupId(linkedGroupId);
            customerWalletTransactionRepository.save(walletTxn);

            remainingNeeded = remainingNeeded.subtract(use).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal used = targetUse.subtract(remainingNeeded).setScale(2, RoundingMode.HALF_UP);
        return used.compareTo(ZERO) > 0 ? used : ZERO;
    }

    /**
     * When a bill total drops and prior cash + advance on the bill exceeded the new total, credit the excess
     * to the customer wallet (store credit). Idempotent per {@code linkedGroupId} for one replace operation.
     *
     * @return new wallet transaction id, or {@code null} if skipped
     */
    public Long creditBillEditExcessToWalletIfAbsent(Customer customer, BigDecimal amount, BillKind billKind, Long billId,
            String billNumber, Long billVersionId, String linkedGroupId) {
        if (customer == null || customer.getId() == null || amount == null || linkedGroupId == null) {
            return null;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(ZERO) <= 0) {
            return null;
        }
        if (customerWalletTransactionRepository.existsByCustomer_IdAndLinkedGroupIdAndSource(
                customer.getId(), linkedGroupId, "BILL_EDIT_ADJUSTMENT")) {
            return null;
        }
        String ref = walletReferenceForBill(billKind, billId);
        CustomerWalletTransaction w = new CustomerWalletTransaction();
        w.setCustomer(customer);
        w.setTxnType(CustomerWalletTransaction.TxnType.CREDIT);
        w.setAmount(amt);
        w.setSource("BILL_EDIT_ADJUSTMENT");
        w.setPaymentMode(BillPaymentMode.OTHER);
        w.setReferenceId(ref);
        w.setNotes("Excess paid over new bill total after bill edit"
                + (billNumber != null && !billNumber.isBlank() ? " (" + billNumber + ")" : ""));
        w.setStatus(CustomerWalletTransaction.Status.ACTIVE);
        w.setBillVersionId(billVersionId);
        w.setLinkedGroupId(linkedGroupId);
        CustomerWalletTransaction saved = customerWalletTransactionRepository.save(w);
        return saved.getId();
    }

    /**
     * Physical stock return: credit return value to customer wallet once per {@code billInventoryReturnId}.
     *
     * @return new wallet transaction id, or {@code null} if skipped / duplicate linked group
     */
    public Long creditBillReturnToWalletIfAbsent(Customer customer, BigDecimal amount, BillKind billKind, Long billId,
            String billNumber, Long billInventoryReturnId) {
        if (customer == null || customer.getId() == null || amount == null || billInventoryReturnId == null) {
            return null;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(ZERO) <= 0) {
            return null;
        }
        String linkedGroupId = "BILL_INV_RET_" + billInventoryReturnId;
        if (customerWalletTransactionRepository.existsByCustomer_IdAndLinkedGroupIdAndSource(
                customer.getId(), linkedGroupId, "BILL_RETURN_CREDIT")) {
            return null;
        }
        String ref = walletReferenceForBill(billKind, billId);
        CustomerWalletTransaction w = new CustomerWalletTransaction();
        w.setCustomer(customer);
        w.setTxnType(CustomerWalletTransaction.TxnType.CREDIT);
        w.setAmount(amt);
        w.setSource("BILL_RETURN_CREDIT");
        w.setPaymentMode(BillPaymentMode.OTHER);
        w.setReferenceId(ref);
        w.setNotes("Return value credited to wallet"
                + (billNumber != null && !billNumber.isBlank() ? " (" + billNumber + ")" : ""));
        w.setStatus(CustomerWalletTransaction.Status.ACTIVE);
        w.setLinkedGroupId(linkedGroupId);
        CustomerWalletTransaction saved = customerWalletTransactionRepository.save(w);
        return saved.getId();
    }

    /**
     * After returns, restore customer overpayment vs effective obligation to wallet (adjustment row).
     * Idempotent per {@code billInventoryReturnId} via linked group {@code BILL_SURPLUS_RET_{id}}.
     */
    public Long creditAdvanceSurplusRestoreIfAbsent(Customer customer, BigDecimal amount, BillKind billKind, Long billId,
            String billNumber, Long billInventoryReturnId) {
        if (customer == null || customer.getId() == null || amount == null || billInventoryReturnId == null) {
            return null;
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(ZERO) <= 0) {
            return null;
        }
        String linkedGroupId = "BILL_SURPLUS_RET_" + billInventoryReturnId;
        if (customerWalletTransactionRepository.existsByCustomer_IdAndLinkedGroupIdAndSource(
                customer.getId(), linkedGroupId, "ADVANCE_RESTORE")) {
            return null;
        }
        String ref = walletReferenceForBill(billKind, billId);
        CustomerWalletTransaction w = new CustomerWalletTransaction();
        w.setCustomer(customer);
        w.setTxnType(CustomerWalletTransaction.TxnType.CREDIT);
        w.setAmount(amt);
        w.setSource("ADVANCE_RESTORE");
        w.setPaymentMode(BillPaymentMode.OTHER);
        w.setReferenceId(ref);
        w.setNotes("Advance surplus restored after return"
                + (billNumber != null && !billNumber.isBlank() ? " (" + billNumber + ")" : ""));
        w.setStatus(CustomerWalletTransaction.Status.ACTIVE);
        w.setLinkedGroupId(linkedGroupId);
        CustomerWalletTransaction saved = customerWalletTransactionRepository.save(w);
        return saved.getId();
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
        reverseAdvanceUsageForBill(billKind, billId, null, null);
    }

    /** Reverse by inserting CREDIT refund wallet transaction rows linked to original debits. */
    public void reverseAdvanceUsageForBill(BillKind billKind, Long billId, Long billVersionId, String linkedGroupId) {
        String ref = walletReferenceForBill(billKind, billId);
        List<CustomerWalletTransaction> txns = customerWalletTransactionRepository
                .findBySourceAndReferenceIdAndTxnTypeAndStatus(
                        "BILL_PAYMENT",
                        ref,
                        CustomerWalletTransaction.TxnType.DEBIT,
                        CustomerWalletTransaction.Status.ACTIVE);
        if (txns.isEmpty()) {
            return;
        }
        for (CustomerWalletTransaction debit : txns) {
            BigDecimal used = nvl(debit.getAmount()).setScale(2, RoundingMode.HALF_UP);
            if (used.compareTo(ZERO) <= 0) {
                continue;
            }
            if (customerWalletTransactionRepository.existsByReversalOfIdAndStatusAndTxnTypeAndSource(
                    debit.getId(),
                    CustomerWalletTransaction.Status.ACTIVE,
                    CustomerWalletTransaction.TxnType.CREDIT,
                    "REFUND")) {
                continue;
            }
            CustomerWalletTransaction refund = new CustomerWalletTransaction();
            refund.setCustomer(debit.getCustomer());
            refund.setTxnType(CustomerWalletTransaction.TxnType.CREDIT);
            refund.setAmount(used);
            refund.setSource("REFUND");
            refund.setReferenceId(ref);
            refund.setNotes("Bill cancelled/replaced reversal");
            refund.setStatus(CustomerWalletTransaction.Status.ACTIVE);
            refund.setReversalOfId(debit.getId());
            refund.setBillVersionId(billVersionId);
            refund.setLinkedGroupId(linkedGroupId);
            customerWalletTransactionRepository.save(refund);
        }
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
        Customer customer = customerRepository.findByIdAndLocation(customerId, location)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));
        // Deposits only — do not count REFUND credits (bill cancel) as "new advance"; those restore wallet balance.
        BigDecimal totalAdvance = nvl(customerWalletTransactionRepository.sumByCustomerIdAndStatusAndTxnTypeAndSource(
                customerId,
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.CREDIT,
                "ADVANCE_DEPOSIT"));
        // Net usage on bills:
        // BILL_PAYMENT debits minus REFUND credits (bill cancellation reversal).
        BigDecimal grossUsedOnBills = nvl(customerWalletTransactionRepository.sumByCustomerIdAndStatusAndTxnTypeAndSource(
                customerId,
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.DEBIT,
                "BILL_PAYMENT"));
        BigDecimal reversedFromCancelledBills = nvl(customerWalletTransactionRepository.sumByCustomerIdAndStatusAndTxnTypeAndSource(
                customerId,
                CustomerWalletTransaction.Status.ACTIVE,
                CustomerWalletTransaction.TxnType.CREDIT,
                "REFUND"));
        BigDecimal totalUsed = grossUsedOnBills.subtract(reversedFromCancelledBills).setScale(2, RoundingMode.HALF_UP);
        if (totalUsed.compareTo(ZERO) < 0) {
            totalUsed = ZERO;
        }
        BigDecimal totalRemaining = getWalletBalance(customerId).setScale(2, RoundingMode.HALF_UP);
        BigDecimal oldBillPendingAmount = getOldBillPendingAmount(customer);
        CustomerAdvanceSummaryDTO dto = new CustomerAdvanceSummaryDTO();
        dto.setTotalAdvance(totalAdvance.doubleValue());
        dto.setTotalUsed(totalUsed.doubleValue());
        dto.setRemaining(totalRemaining.doubleValue());
        dto.setOldBillPendingAmount(oldBillPendingAmount.doubleValue());
        return dto;
    }

    public List<CustomerAdvanceHistoryEntryDTO> getHistory(Long customerId, String location) {
        customerRepository.findByIdAndLocation(customerId, location)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));
        List<CustomerWalletTransaction> txns = customerWalletTransactionRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId);
        List<CustomerAdvanceHistoryEntryDTO> entries = new ArrayList<>();
        for (CustomerWalletTransaction t : txns) {
            String kind = resolveHistoryKind(t);
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

    /** Active wallet balance (CREDIT − DEBIT) for cancellation preview. */
    public BigDecimal getAvailableWalletBalance(Long customerId) {
        return getWalletBalance(customerId);
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

    private static String resolveHistoryKind(CustomerWalletTransaction txn) {
        if (txn == null) {
            return "UNKNOWN";
        }
        String source = txn.getSource() != null ? txn.getSource().trim().toUpperCase() : "";
        if (txn.getTxnType() == CustomerWalletTransaction.TxnType.CREDIT) {
            if ("ADVANCE_DEPOSIT".equals(source)) {
                return "DEPOSIT";
            }
            if ("BILL_EDIT_ADJUSTMENT".equals(source)) {
                return "STORE_CREDIT";
            }
            if ("BILL_RETURN_CREDIT".equals(source)) {
                return "RETURN_CREDIT";
            }
            if ("REFUND".equals(source)) {
                return "REVERSAL_CREDIT";
            }
            return "CREDIT";
        }
        if ("BILL_PAYMENT".equals(source)) {
            return "USAGE";
        }
        if ("ADVANCE_REFUND".equals(source)) {
            return "REFUND";
        }
        return "DEBIT";
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

    private BigDecimal getOldBillPendingAmount(Customer customer) {
        if (customer == null || customer.getId() == null) {
            return ZERO;
        }

        List<BillGST> gstBills = billGSTRepository.findByCustomer(customer).stream()
                .filter(this::isCountableBill)
                .toList();
        List<BillNonGST> nonGstBills = billNonGSTRepository.findByCustomer(customer).stream()
                .filter(this::isCountableBill)
                .toList();

        Map<String, List<BillPayment>> paymentMap = new HashMap<>();
        List<Long> gstIds = gstBills.stream().map(BillGST::getId).toList();
        if (!gstIds.isEmpty()) {
            for (BillPayment payment : billPaymentRepository.findByBillKindAndBillIdIn(BillKind.GST, gstIds)) {
                paymentMap.computeIfAbsent("GST:" + payment.getBillId(), ignored -> new ArrayList<>()).add(payment);
            }
        }

        List<Long> nonGstIds = nonGstBills.stream().map(BillNonGST::getId).toList();
        if (!nonGstIds.isEmpty()) {
            for (BillPayment payment : billPaymentRepository.findByBillKindAndBillIdIn(BillKind.NON_GST, nonGstIds)) {
                paymentMap.computeIfAbsent("NON_GST:" + payment.getBillId(), ignored -> new ArrayList<>()).add(payment);
            }
        }

        Map<String, BigDecimal> advanceMap = sumAdvanceUsedGrouped(gstIds, nonGstIds);
        BigDecimal totalPending = ZERO;

        for (BillGST bill : gstBills) {
            totalPending = totalPending.add(computeBillDue(
                    bill.getTotalAmount(),
                    paymentMap.get("GST:" + bill.getId()),
                    advanceMap.get("GST:" + bill.getId())));
        }
        for (BillNonGST bill : nonGstBills) {
            totalPending = totalPending.add(computeBillDue(
                    bill.getTotalAmount(),
                    paymentMap.get("NON_GST:" + bill.getId()),
                    advanceMap.get("NON_GST:" + bill.getId())));
        }

        return totalPending.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isCountableBill(BillGST bill) {
        return bill != null
                && Boolean.TRUE.equals(bill.getLatestVersion())
                && !"CANCELLED".equalsIgnoreCase(bill.getBillStatus())
                && bill.getPaymentStatus() != BillGST.PaymentStatus.CANCELLED;
    }

    private boolean isCountableBill(BillNonGST bill) {
        return bill != null
                && Boolean.TRUE.equals(bill.getLatestVersion())
                && !"CANCELLED".equalsIgnoreCase(bill.getBillStatus())
                && bill.getPaymentStatus() != BillNonGST.PaymentStatus.CANCELLED;
    }

    private BigDecimal computeBillDue(BigDecimal totalAmount, List<BillPayment> paymentRows, BigDecimal advanceUsed) {
        BigDecimal paid = ZERO;
        if (paymentRows != null) {
            for (BillPayment paymentRow : paymentRows) {
                if (paymentRow == null || Boolean.TRUE.equals(paymentRow.getIsDeleted())) {
                    continue;
                }
                paid = paid.add(nvl(paymentRow.getAmount()));
            }
        }
        return nvl(totalAmount)
                .subtract(nvl(advanceUsed))
                .subtract(paid)
                .max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
