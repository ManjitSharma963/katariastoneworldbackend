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
import com.katariastoneworld.apis.repository.CustomerAdvanceRepository;
import com.katariastoneworld.apis.repository.CustomerAdvanceUsageRepository;
import com.katariastoneworld.apis.repository.CustomerRepository;
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
        financialLedgerService.recordAdvanceDeposit(location, customer.getId(), saved.getId(),
                saved.getPaymentMode() != null ? saved.getPaymentMode() : BillPaymentMode.CASH,
                saved.getAmount(), saved.getCreatedAt() != null ? saved.getCreatedAt().toLocalDate() : null);
        return toResponseDTO(saved);
    }

    /**
     * FIFO apply advance to a bill; persists usage rows and decrements remaining_amount.
     *
     * @return total amount applied from advance toward this bill
     */
    public BigDecimal applyAdvanceFifo(Long customerId, BillKind billKind, Long billId, BigDecimal billTotal) {
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(billKind);
        Objects.requireNonNull(billId);
        BigDecimal gross = billTotal.setScale(2, RoundingMode.HALF_UP);
        if (gross.compareTo(ZERO) <= 0) {
            return ZERO;
        }
        BigDecimal stillNeed = gross;
        BigDecimal totalApplied = ZERO;
        List<CustomerAdvance> rows = customerAdvanceRepository.findEligibleForApplyLocked(customerId, ZERO);
        for (CustomerAdvance adv : rows) {
            if (stillNeed.compareTo(ZERO) <= 0) {
                break;
            }
            BigDecimal rem = adv.getRemainingAmount().setScale(2, RoundingMode.HALF_UP);
            if (rem.compareTo(ZERO) <= 0) {
                continue;
            }
            BigDecimal take = rem.min(stillNeed);
            CustomerAdvanceUsage u = new CustomerAdvanceUsage();
            u.setAdvance(adv);
            u.setBillKind(billKind);
            u.setBillId(billId);
            u.setAmountUsed(take);
            customerAdvanceUsageRepository.save(u);
            adv.setRemainingAmount(rem.subtract(take).setScale(2, RoundingMode.HALF_UP));
            customerAdvanceRepository.save(adv);
            stillNeed = stillNeed.subtract(take).setScale(2, RoundingMode.HALF_UP);
            totalApplied = totalApplied.add(take).setScale(2, RoundingMode.HALF_UP);
        }
        return totalApplied;
    }

    public BigDecimal sumAdvanceUsedForBill(BillKind billKind, Long billId) {
        return customerAdvanceUsageRepository.findByBillKindAndBillId(billKind, billId).stream()
                .map(CustomerAdvanceUsage::getAmountUsed)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Map<String, BigDecimal> sumAdvanceUsedGrouped(Collection<Long> gstBillIds, Collection<Long> nonGstBillIds) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (gstBillIds != null && !gstBillIds.isEmpty()) {
            for (CustomerAdvanceUsage u : customerAdvanceUsageRepository.findByBillKindAndBillIdIn(BillKind.GST,
                    gstBillIds)) {
                String k = "GST:" + u.getBillId();
                map.merge(k, u.getAmountUsed().setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
            }
        }
        if (nonGstBillIds != null && !nonGstBillIds.isEmpty()) {
            for (CustomerAdvanceUsage u : customerAdvanceUsageRepository.findByBillKindAndBillIdIn(BillKind.NON_GST,
                    nonGstBillIds)) {
                String k = "NON_GST:" + u.getBillId();
                map.merge(k, u.getAmountUsed().setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
            }
        }
        return map;
    }

    public CustomerAdvanceSummaryDTO getSummary(Long customerId, String location) {
        customerRepository.findByIdAndLocation(customerId, location)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));
        List<CustomerAdvance> rows = customerAdvanceRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId);
        BigDecimal totalAdvance = BigDecimal.ZERO;
        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (CustomerAdvance a : rows) {
            totalAdvance = totalAdvance.add(a.getAmount());
            totalRemaining = totalRemaining.add(a.getRemainingAmount());
        }
        totalAdvance = totalAdvance.setScale(2, RoundingMode.HALF_UP);
        totalRemaining = totalRemaining.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalUsed = totalAdvance.subtract(totalRemaining).max(ZERO).setScale(2, RoundingMode.HALF_UP);
        CustomerAdvanceSummaryDTO dto = new CustomerAdvanceSummaryDTO();
        dto.setTotalAdvance(totalAdvance.doubleValue());
        dto.setTotalUsed(totalUsed.doubleValue());
        dto.setRemaining(totalRemaining.doubleValue());
        return dto;
    }

    public List<CustomerAdvanceHistoryEntryDTO> getHistory(Long customerId, String location) {
        customerRepository.findByIdAndLocation(customerId, location)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + customerId));
        List<CustomerAdvance> advances = customerAdvanceRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId);
        List<CustomerAdvanceHistoryEntryDTO> entries = new ArrayList<>();
        for (CustomerAdvance a : advances) {
            entries.add(new CustomerAdvanceHistoryEntryDTO(
                    "DEPOSIT",
                    a.getCreatedAt(),
                    a.getAmount().doubleValue(),
                    a.getDescription() != null ? a.getDescription() : "Advance / token",
                    a.getPaymentMode() != null ? a.getPaymentMode().name() : null,
                    null,
                    null,
                    a.getId()));
        }
        for (CustomerAdvance a : advances) {
            List<CustomerAdvanceUsage> usages = customerAdvanceUsageRepository.findByAdvanceIdOrderByCreatedAtDesc(a.getId());
            for (CustomerAdvanceUsage u : usages) {
                entries.add(new CustomerAdvanceHistoryEntryDTO(
                        "USAGE",
                        u.getCreatedAt(),
                        u.getAmountUsed().doubleValue(),
                        "Applied to bill",
                        null,
                        u.getBillId(),
                        u.getBillKind() != null ? u.getBillKind().name() : null,
                        a.getId()));
            }
        }
        entries.sort(Comparator.comparing(CustomerAdvanceHistoryEntryDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return entries;
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
            throw new IllegalArgumentException("Invalid payment mode. Use CASH, UPI, BANK_TRANSFER, or CHEQUE.");
        }
    }
}
