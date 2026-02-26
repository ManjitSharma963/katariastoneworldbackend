package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.WebsiteProductRequestDTO;
import com.katariastoneworld.apis.dto.WebsiteProductResponseDTO;
import com.katariastoneworld.apis.entity.WebsiteProduct;
import com.katariastoneworld.apis.repository.WebsiteProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for website products (display only, not inventory).
 * Does not use or modify Product/Inventory.
 */
@Service
@Transactional
public class WebsiteProductService {

    @Autowired
    private WebsiteProductRepository websiteProductRepository;

    public List<WebsiteProductResponseDTO> getAllProducts(Boolean activeOnly) {
        List<WebsiteProduct> products = Boolean.TRUE.equals(activeOnly)
                ? websiteProductRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                : websiteProductRepository.findAllByOrderByCreatedAtDesc();
        return products.stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    public WebsiteProductResponseDTO getProductById(Long id) {
        WebsiteProduct product = websiteProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Website product not found with id: " + id));
        return toResponseDTO(product);
    }

    public WebsiteProductResponseDTO getProductBySlug(String slug) {
        WebsiteProduct product = websiteProductRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Website product not found with slug: " + slug));
        return toResponseDTO(product);
    }

    public WebsiteProductResponseDTO createProduct(WebsiteProductRequestDTO dto) {
        if (websiteProductRepository.existsBySlug(dto.getSlug())) {
            throw new RuntimeException("Website product with slug '" + dto.getSlug() + "' already exists.");
        }
        WebsiteProduct product = new WebsiteProduct();
        product.setName(dto.getName());
        product.setSlug(dto.getSlug());
        product.setDescription(dto.getDescription());
        product.setPrimaryImageUrl(dto.getPrimaryImageUrl());
        product.setPrice(dto.getPrice() != null ? dto.getPrice().setScale(2, RoundingMode.HALF_UP) : null);
        product.setCategoryId(dto.getCategoryId());
        product.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        WebsiteProduct saved = websiteProductRepository.save(product);
        return toResponseDTO(saved);
    }

    public WebsiteProductResponseDTO updateProduct(Long id, WebsiteProductRequestDTO dto) {
        WebsiteProduct product = websiteProductRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Website product not found with id: " + id));
        if (!product.getSlug().equals(dto.getSlug()) && websiteProductRepository.existsBySlug(dto.getSlug())) {
            throw new RuntimeException("Website product with slug '" + dto.getSlug() + "' already exists.");
        }
        product.setName(dto.getName());
        product.setSlug(dto.getSlug());
        product.setDescription(dto.getDescription());
        product.setPrimaryImageUrl(dto.getPrimaryImageUrl());
        if (dto.getPrice() != null) {
            product.setPrice(dto.getPrice().setScale(2, RoundingMode.HALF_UP));
        }
        product.setCategoryId(dto.getCategoryId());
        if (dto.getIsActive() != null) {
            product.setIsActive(dto.getIsActive());
        }
        WebsiteProduct updated = websiteProductRepository.save(product);
        return toResponseDTO(updated);
    }

    public void deleteProduct(Long id) {
        if (!websiteProductRepository.existsById(id)) {
            throw new RuntimeException("Website product not found with id: " + id);
        }
        websiteProductRepository.deleteById(id);
    }

    private WebsiteProductResponseDTO toResponseDTO(WebsiteProduct p) {
        WebsiteProductResponseDTO dto = new WebsiteProductResponseDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setSlug(p.getSlug());
        dto.setDescription(p.getDescription());
        dto.setPrimaryImageUrl(p.getPrimaryImageUrl());
        dto.setPrice(p.getPrice());
        dto.setCategoryId(p.getCategoryId());
        dto.setIsActive(p.getIsActive());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }
}
