package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.LedgerAuditEntryDTO;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.FinancialLedgerEntry;
import com.katariastoneworld.apis.entity.User;
import com.katariastoneworld.apis.repository.FinancialLedgerRepository;
import com.katariastoneworld.apis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AuditLedgerService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    @Autowired
    private FinancialLedgerRepository financialLedgerRepository;

    @Autowired
    private UserRepository userRepository;

    public List<LedgerAuditEntryDTO> listLedgerAudit(String location, LocalDate eventDate, String sourceType,
            Long createdByUserId, int limit) {
        final String loc = location == null ? "" : location.trim();
        int lim = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        Specification<FinancialLedgerEntry> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("location"), loc));
            if (eventDate != null) {
                p.add(cb.equal(root.get("eventDate"), eventDate));
            }
            if (sourceType != null && !sourceType.isBlank()) {
                p.add(cb.equal(root.get("sourceType"), sourceType.trim()));
            }
            if (createdByUserId != null) {
                p.add(cb.equal(root.get("createdBy"), createdByUserId));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        var page = financialLedgerRepository.findAll(spec,
                PageRequest.of(0, lim, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<FinancialLedgerEntry> rows = page.getContent();
        Map<Long, String> names = loadUserNames(rows);
        List<LedgerAuditEntryDTO> out = new ArrayList<>();
        for (FinancialLedgerEntry l : rows) {
            out.add(toDto(l, names));
        }
        return out;
    }

    public List<LedgerAuditEntryDTO> listBillLedger(String location, Long billId, BillKind billKind) {
        final String loc = location == null ? "" : location.trim();
        if (billId == null) {
            return List.of();
        }
        String billIdStr = String.valueOf(billId);
        Specification<FinancialLedgerEntry> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("location"), loc));
            p.add(cb.equal(root.get("referenceType"), "BILL"));
            p.add(cb.equal(root.get("referenceId"), billIdStr));
            if (billKind != null) {
                p.add(cb.equal(root.get("billKind"), billKind.name()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        var rows = financialLedgerRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<Long, String> names = loadUserNames(rows);
        List<LedgerAuditEntryDTO> out = new ArrayList<>();
        for (FinancialLedgerEntry l : rows) {
            out.add(toDto(l, names));
        }
        return out;
    }

    private Map<Long, String> loadUserNames(List<FinancialLedgerEntry> rows) {
        Set<Long> ids = new HashSet<>();
        for (FinancialLedgerEntry l : rows) {
            if (l.getCreatedBy() != null) {
                ids.add(l.getCreatedBy());
            }
            if (l.getUpdatedBy() != null) {
                ids.add(l.getUpdatedBy());
            }
        }
        Map<Long, String> map = new HashMap<>();
        for (Long id : ids) {
            userRepository.findById(id).map(User::getName).ifPresent(n -> map.put(id, n));
        }
        return map;
    }

    private static LedgerAuditEntryDTO toDto(FinancialLedgerEntry l, Map<Long, String> names) {
        BigDecimal amt = l.getAmount();
        return LedgerAuditEntryDTO.builder()
                .id(l.getId())
                .sourceType(l.getSourceType())
                .sourceId(l.getSourceId())
                .entryType(l.getEntryType() != null ? l.getEntryType().name() : null)
                .amount(amt != null ? amt.doubleValue() : null)
                .paymentMode(l.getPaymentMode() != null ? l.getPaymentMode().name() : null)
                .eventDate(l.getEventDate())
                .referenceType(l.getReferenceType())
                .referenceId(l.getReferenceId())
                .billKind(l.getBillKind())
                .billId(l.getBillId())
                .createdAt(l.getCreatedAt())
                .createdBy(l.getCreatedBy())
                .createdByName(l.getCreatedBy() != null ? names.get(l.getCreatedBy()) : null)
                .updatedAt(l.getUpdatedAt())
                .updatedBy(l.getUpdatedBy())
                .updatedByName(l.getUpdatedBy() != null ? names.get(l.getUpdatedBy()) : null)
                .deleted(Boolean.TRUE.equals(l.getIsDeleted()))
                .build();
    }
}
