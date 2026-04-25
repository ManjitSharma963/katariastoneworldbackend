package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.InventoryHistoryResponseDTO;
import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.repository.InventoryTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class InventoryTransactionService {

    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;

    /**
     * Append ledger row then caller updates {@code products.quantity}. Quantity here is always positive; direction defines sign.
     */
    public InventoryTransaction append(
            Long productId,
            InventoryTxnType txnType,
            InventoryDirection direction,
            BigDecimal quantity,
            InventoryReferenceType referenceType,
            Long referenceId,
            String billKind,
            String notes,
            Long locationId) {
        return append(productId, txnType, direction, quantity, referenceType, referenceId, billKind, notes, locationId, null);
    }

    public InventoryTransaction append(
            Long productId,
            InventoryTxnType txnType,
            InventoryDirection direction,
            BigDecimal quantity,
            InventoryReferenceType referenceType,
            Long referenceId,
            String billKind,
            String notes,
            Long locationId,
            LocalDate businessDate) {
        InventoryTransaction row = new InventoryTransaction();
        row.setProductId(productId);
        row.setTxnType(txnType);
        row.setDirection(direction);
        row.setQuantity(scale(quantity));
        row.setReferenceType(referenceType);
        row.setReferenceId(referenceId);
        row.setBillKind(billKind);
        row.setNotes(truncateNotes(notes));
        row.setLocationId(locationId);
        row.setBusinessDate(businessDate);
        return inventoryTransactionRepository.save(row);
    }

    @Transactional(readOnly = true)
    public List<InventoryHistoryResponseDTO> getHistoryForProduct(Long productId) {
        return inventoryTransactionRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryHistoryResponseDTO> getHistoryForLocation(
            String location,
            LocalDate from,
            LocalDate to,
            InventoryTxnType txnTypeFilter,
            int limit) {
        LocalDate fromD = from;
        LocalDate toD = to;
        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toExclusive = to != null ? to.plusDays(1).atStartOfDay() : null;
        return inventoryTransactionRepository
                .findAllForLocationWithFilters(location, txnTypeFilter, fromD, toD, fromTs, toExclusive)
                .stream()
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private InventoryHistoryResponseDTO toDto(InventoryTransaction t) {
        InventoryHistoryResponseDTO dto = new InventoryHistoryResponseDTO();
        dto.setId(t.getId());
        dto.setProductId(t.getProductId());
        dto.setActionType(t.getTxnType() != null ? t.getTxnType().name() : null);
        dto.setQuantityChanged(t.getQuantity() != null
                ? (t.getDirection() == InventoryDirection.IN ? t.getQuantity().doubleValue() : -t.getQuantity().doubleValue())
                : null);
        dto.setPreviousQuantity(null);
        dto.setNewQuantity(null);
        dto.setReferenceId(t.getReferenceId());
        dto.setNotes(t.getNotes());
        dto.setCreatedAt(t.getCreatedAt());
        dto.setTxnType(t.getTxnType() != null ? t.getTxnType().name() : null);
        dto.setDirection(t.getDirection() != null ? t.getDirection().name() : null);
        dto.setReferenceType(t.getReferenceType() != null ? t.getReferenceType().name() : null);
        dto.setBusinessDate(t.getBusinessDate());
        return dto;
    }

    private static BigDecimal scale(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncateNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String t = notes.trim();
        if (t.length() <= 255) {
            return t;
        }
        return t.substring(0, 252) + "...";
    }

    @Transactional(readOnly = true)
    public java.util.List<Object[]> sumSignedQuantityByProductAfterInstant(String location, LocalDateTime afterInstant) {
        return inventoryTransactionRepository.sumSignedQuantityByProductAfterInstant(location, afterInstant);
    }
}
