package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.InventoryHistoryResponseDTO;
import com.katariastoneworld.apis.entity.InventoryActionType;
import com.katariastoneworld.apis.entity.InventoryHistory;
import com.katariastoneworld.apis.repository.InventoryHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class InventoryHistoryService {

    @Autowired
    private InventoryHistoryRepository inventoryHistoryRepository;

    public InventoryHistory saveInventoryHistory(
            Long productId,
            InventoryActionType actionType,
            BigDecimal quantityChanged,
            BigDecimal previousQty,
            BigDecimal newQty,
            Long referenceId,
            String notes) {
        InventoryHistory row = new InventoryHistory();
        row.setProductId(productId);
        row.setActionType(actionType);
        row.setQuantityChanged(scale(quantityChanged));
        row.setPreviousQuantity(scale(previousQty));
        row.setNewQuantity(scale(newQty));
        row.setReferenceId(referenceId);
        row.setNotes(notes);
        return inventoryHistoryRepository.save(row);
    }

    private static BigDecimal scale(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<InventoryHistoryResponseDTO> getHistoryForProduct(Long productId) {
        return inventoryHistoryRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryHistoryResponseDTO> getHistoryForLocation(
            String location,
            LocalDate from,
            LocalDate to,
            InventoryActionType actionType,
            Integer limit) {
        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toExclusive = to != null ? to.plusDays(1).atStartOfDay() : null;
        int lim = limit != null ? Math.max(1, Math.min(5000, limit)) : 1000;
        return inventoryHistoryRepository
                .findAllForLocationWithFilters(location, actionType, fromTs, toExclusive)
                .stream()
                .limit(lim)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private InventoryHistoryResponseDTO toDto(InventoryHistory h) {
        InventoryHistoryResponseDTO dto = new InventoryHistoryResponseDTO();
        dto.setId(h.getId());
        dto.setProductId(h.getProductId());
        dto.setActionType(h.getActionType() != null ? h.getActionType().name() : null);
        dto.setQuantityChanged(h.getQuantityChanged() != null ? h.getQuantityChanged().doubleValue() : null);
        dto.setPreviousQuantity(h.getPreviousQuantity() != null ? h.getPreviousQuantity().doubleValue() : null);
        dto.setNewQuantity(h.getNewQuantity() != null ? h.getNewQuantity().doubleValue() : null);
        dto.setReferenceId(h.getReferenceId());
        dto.setNotes(h.getNotes());
        dto.setCreatedAt(h.getCreatedAt());
        return dto;
    }
}
