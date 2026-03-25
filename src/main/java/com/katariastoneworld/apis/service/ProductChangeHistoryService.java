package com.katariastoneworld.apis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katariastoneworld.apis.dto.ProductChangeHistoryResponseDTO;
import com.katariastoneworld.apis.dto.ProductResponseDTO;
import com.katariastoneworld.apis.entity.ProductChangeHistory;
import com.katariastoneworld.apis.repository.ProductChangeHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductChangeHistoryService {

    @Autowired
    private ProductChangeHistoryRepository productChangeHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public void recordChange(Long productId, ProductResponseDTO before, ProductResponseDTO after, String notes) {
        ProductChangeHistory row = new ProductChangeHistory();
        row.setProductId(productId);
        row.setPreviousSnapshotJson(writeJson(before));
        row.setNewSnapshotJson(writeJson(after));
        row.setNotes(notes != null && !notes.isBlank() ? notes.trim() : null);
        productChangeHistoryRepository.save(row);
    }

    private String writeJson(ProductResponseDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize product snapshot", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductChangeHistoryResponseDTO> listForProduct(Long productId) {
        return productChangeHistoryRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ProductChangeHistoryResponseDTO toDto(ProductChangeHistory e) {
        ProductChangeHistoryResponseDTO dto = new ProductChangeHistoryResponseDTO();
        dto.setId(e.getId());
        dto.setProductId(e.getProductId());
        dto.setNotes(e.getNotes());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setPreviousSnapshot(parseJson(e.getPreviousSnapshotJson()));
        dto.setNewSnapshot(parseJson(e.getNewSnapshotJson()));
        return dto;
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode().put("raw", json);
        }
    }
}
