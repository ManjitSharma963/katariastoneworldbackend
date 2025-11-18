package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ProductRequestDTO;
import com.katariastoneworld.apis.dto.ProductResponseDTO;
import com.katariastoneworld.apis.entity.Product;
import com.katariastoneworld.apis.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    public ProductResponseDTO createProduct(ProductRequestDTO productRequestDTO, String location) {
        // Check if slug already exists for this location
        if (productRepository.existsBySlugAndLocation(productRequestDTO.getSlug(), location)) {
            throw new RuntimeException("Product with slug '" + productRequestDTO.getSlug() + "' already exists for location: " + location);
        }
        
        // Create Product entity
        Product product = new Product();
        product.setName(productRequestDTO.getName());
        product.setSlug(productRequestDTO.getSlug());
        product.setProductType(productRequestDTO.getProductType());
        product.setPricePerSqft(BigDecimal.valueOf(productRequestDTO.getPricePerSqft())
                .setScale(2, RoundingMode.HALF_UP));
        product.setTotalSqftStock(BigDecimal.valueOf(productRequestDTO.getTotalSqftStock())
                .setScale(2, RoundingMode.HALF_UP));
        product.setPrimaryImageUrl(productRequestDTO.getPrimaryImageUrl());
        
        // Optional fields
        if (productRequestDTO.getColor() != null) {
            product.setColor(productRequestDTO.getColor());
        }
        if (productRequestDTO.getDescription() != null) {
            product.setDescription(productRequestDTO.getDescription());
        }
        if (productRequestDTO.getCategoryId() != null) {
            product.setCategoryId(productRequestDTO.getCategoryId());
        }
        if (productRequestDTO.getIsFeatured() != null) {
            product.setIsFeatured(productRequestDTO.getIsFeatured());
        }
        if (productRequestDTO.getIsActive() != null) {
            product.setIsActive(productRequestDTO.getIsActive());
        }
        if (productRequestDTO.getMetaKeywords() != null) {
            product.setMetaKeywords(productRequestDTO.getMetaKeywords());
        }
        
        // Set location
        product.setLocation(location);
        
        // Save product
        Product savedProduct = productRepository.save(product);
        
        // Convert to response DTO
        return convertToResponseDTO(savedProduct);
    }
    
    public ProductResponseDTO getProductById(Long id, String location) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        
        // Verify location matches
        if (!location.equals(product.getLocation())) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        
        return convertToResponseDTO(product);
    }
    
    public ProductResponseDTO getProductBySlug(String slug, String location) {
        Product product = productRepository.findBySlugAndLocation(slug, location)
                .orElseThrow(() -> new RuntimeException("Product not found with slug: " + slug + " for location: " + location));
        return convertToResponseDTO(product);
    }
    
    public List<ProductResponseDTO> getAllProducts(String location) {
        return productRepository.findByLocation(location).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
    
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO productRequestDTO, String location) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        
        // Verify location matches
        if (!location.equals(product.getLocation())) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        
        // Check if slug is being changed and if new slug already exists for this location
        if (!product.getSlug().equals(productRequestDTO.getSlug()) && 
            productRepository.existsBySlugAndLocation(productRequestDTO.getSlug(), location)) {
            throw new RuntimeException("Product with slug '" + productRequestDTO.getSlug() + "' already exists for location: " + location);
        }
        
        product.setName(productRequestDTO.getName());
        product.setSlug(productRequestDTO.getSlug());
        product.setProductType(productRequestDTO.getProductType());
        product.setPricePerSqft(BigDecimal.valueOf(productRequestDTO.getPricePerSqft())
                .setScale(2, RoundingMode.HALF_UP));
        product.setTotalSqftStock(BigDecimal.valueOf(productRequestDTO.getTotalSqftStock())
                .setScale(2, RoundingMode.HALF_UP));
        product.setPrimaryImageUrl(productRequestDTO.getPrimaryImageUrl());
        
        if (productRequestDTO.getColor() != null) {
            product.setColor(productRequestDTO.getColor());
        }
        if (productRequestDTO.getDescription() != null) {
            product.setDescription(productRequestDTO.getDescription());
        }
        if (productRequestDTO.getCategoryId() != null) {
            product.setCategoryId(productRequestDTO.getCategoryId());
        }
        if (productRequestDTO.getIsFeatured() != null) {
            product.setIsFeatured(productRequestDTO.getIsFeatured());
        }
        if (productRequestDTO.getIsActive() != null) {
            product.setIsActive(productRequestDTO.getIsActive());
        }
        if (productRequestDTO.getMetaKeywords() != null) {
            product.setMetaKeywords(productRequestDTO.getMetaKeywords());
        }
        
        Product updatedProduct = productRepository.save(product);
        return convertToResponseDTO(updatedProduct);
    }
    
    public void deleteProduct(Long id, String location) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        
        // Verify location matches
        if (!location.equals(product.getLocation())) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        
        productRepository.deleteById(id);
    }
    
    /**
     * Validate stock availability without deducting
     * @param productId The ID of the product
     * @param quantityToDeduct The quantity (in sqft) to validate
     * @throws RuntimeException if product not found or insufficient stock
     */
    public void validateStockAvailability(Long productId, BigDecimal quantityToDeduct) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        
        BigDecimal currentStock = product.getTotalSqftStock();
        BigDecimal newStock = currentStock.subtract(quantityToDeduct);
        
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Insufficient stock for product: " + product.getName() + 
                    ". Available: " + currentStock + " sqft, Requested: " + quantityToDeduct + " sqft");
        }
    }
    
    /**
     * Deduct stock from a product by product ID
     * @param productId The ID of the product
     * @param quantityToDeduct The quantity (in sqft) to deduct from stock
     * @throws RuntimeException if product not found or insufficient stock
     */
    public void deductStock(Long productId, BigDecimal quantityToDeduct) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        
        BigDecimal currentStock = product.getTotalSqftStock();
        BigDecimal newStock = currentStock.subtract(quantityToDeduct);
        
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Insufficient stock for product: " + product.getName() + 
                    ". Available: " + currentStock + " sqft, Requested: " + quantityToDeduct + " sqft");
        }
        
        product.setTotalSqftStock(newStock.setScale(2, RoundingMode.HALF_UP));
        productRepository.save(product);
    }
    
    /**
     * Get product entity by ID (for internal use)
     */
    public Product getProductEntityById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
    }
    
    /**
     * Get product entity by name (for internal use)
     */
    public Product getProductEntityByName(String name) {
        return productRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Product not found with name: " + name));
    }
    
    /**
     * Validate stock availability by product name
     */
    public void validateStockAvailabilityByName(String productName, BigDecimal quantityToDeduct) {
        Product product = getProductEntityByName(productName);
        validateStockAvailability(product.getId(), quantityToDeduct);
    }
    
    /**
     * Deduct stock from a product by product name
     */
    public void deductStockByName(String productName, BigDecimal quantityToDeduct) {
        Product product = getProductEntityByName(productName);
        deductStock(product.getId(), quantityToDeduct);
    }
    
    private ProductResponseDTO convertToResponseDTO(Product product) {
        ProductResponseDTO responseDTO = new ProductResponseDTO();
        responseDTO.setId(product.getId());
        responseDTO.setName(product.getName());
        responseDTO.setSlug(product.getSlug());
        responseDTO.setCategoryId(product.getCategoryId());
        responseDTO.setProductType(product.getProductType());
        responseDTO.setColor(product.getColor());
        responseDTO.setPricePerSqft(product.getPricePerSqft().doubleValue());
        responseDTO.setTotalSqftStock(product.getTotalSqftStock().doubleValue());
        responseDTO.setPrimaryImageUrl(product.getPrimaryImageUrl());
        responseDTO.setDescription(product.getDescription());
        responseDTO.setIsFeatured(product.getIsFeatured());
        responseDTO.setIsActive(product.getIsActive());
        responseDTO.setMetaKeywords(product.getMetaKeywords());
        responseDTO.setCreatedAt(product.getCreatedAt());
        responseDTO.setUpdatedAt(product.getUpdatedAt());
        
        return responseDTO;
    }
}

