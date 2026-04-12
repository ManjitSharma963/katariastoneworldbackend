package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.InventoryHistoryResponseDTO;
import com.katariastoneworld.apis.dto.ProductChangeHistoryResponseDTO;
import com.katariastoneworld.apis.dto.ProductRequestDTO;
import com.katariastoneworld.apis.dto.ProductResponseDTO;
import com.katariastoneworld.apis.dto.ProductStockAsOfRowDTO;
import com.katariastoneworld.apis.dto.StockAsOfResponseDTO;
import com.katariastoneworld.apis.entity.InventoryActionType;
import com.katariastoneworld.apis.entity.Product;
import com.katariastoneworld.apis.repository.InventoryHistoryRepository;
import com.katariastoneworld.apis.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {
    
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryHistoryRepository inventoryHistoryRepository;

    @Autowired
    private InventoryHistoryService inventoryHistoryService;

    @Autowired
    private ProductChangeHistoryService productChangeHistoryService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private DealerService dealerService;
    
    public ProductResponseDTO createProduct(ProductRequestDTO productRequestDTO, String location) {
        if (productRepository.existsBySlugAndLocation(productRequestDTO.getSlug(), location)) {
            throw new RuntimeException("Product with slug '" + productRequestDTO.getSlug() + "' already exists for location: " + location);
        }
        Product product = new Product();
        product.setName(productRequestDTO.getName());
        product.setSlug(productRequestDTO.getSlug());
        product.setProductType(productRequestDTO.getProductType()); // Now accepts any string value
        product.setPricePerUnit(BigDecimal.valueOf(productRequestDTO.getPricePerUnit())
                .setScale(2, RoundingMode.HALF_UP));
        product.setQuantity(BigDecimal.valueOf(productRequestDTO.getQuantity())
                .setScale(2, RoundingMode.HALF_UP));
        product.setUnit(productRequestDTO.getUnit().trim()); // Unit is now required, so this should always have a value
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
        if (productRequestDTO.getLabourCharges() != null) {
            product.setLabourCharges(BigDecimal.valueOf(productRequestDTO.getLabourCharges())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getRtoFees() != null) {
            product.setRtoFees(BigDecimal.valueOf(productRequestDTO.getRtoFees())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getDamageExpenses() != null) {
            product.setDamageExpenses(BigDecimal.valueOf(productRequestDTO.getDamageExpenses())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getOthersExpenses() != null) {
            product.setOthersExpenses(BigDecimal.valueOf(productRequestDTO.getOthersExpenses())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getPricePerSqftAfter() != null) {
            product.setPricePerSqftAfter(BigDecimal.valueOf(productRequestDTO.getPricePerSqftAfter())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getTransportationCharge() != null) {
            product.setTransportationCharge(BigDecimal.valueOf(productRequestDTO.getTransportationCharge())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getGstCharges() != null) {
            product.setGstCharges(BigDecimal.valueOf(productRequestDTO.getGstCharges())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getHsnNumber() != null && !productRequestDTO.getHsnNumber().trim().isEmpty()) {
            product.setHsnNumber(productRequestDTO.getHsnNumber().trim());
        }

        applySupplierDealerOnCreate(product, productRequestDTO, location);
        
        product.setLocation(location);
        // Save product
        Product savedProduct = productRepository.save(product);

        // Store an initial product snapshot so history details (price/GST/supplier/dealer) are visible from day 1.
        // We also include a "before" snapshot with quantity=0 so the UI can compute Stock Δ and show one clean row.
        ProductResponseDTO createdSnapshot = convertToResponseDTO(savedProduct);
        ProductResponseDTO beforeSnapshot = new ProductResponseDTO();
        beforeSnapshot.setId(createdSnapshot.getId());
        beforeSnapshot.setName(createdSnapshot.getName());
        beforeSnapshot.setSlug(createdSnapshot.getSlug());
        beforeSnapshot.setCategoryId(createdSnapshot.getCategoryId());
        beforeSnapshot.setProductType(createdSnapshot.getProductType());
        beforeSnapshot.setColor(createdSnapshot.getColor());
        beforeSnapshot.setPricePerUnit(createdSnapshot.getPricePerUnit());
        beforeSnapshot.setQuantity(0.0);
        beforeSnapshot.setUnit(createdSnapshot.getUnit());
        beforeSnapshot.setPrimaryImageUrl(createdSnapshot.getPrimaryImageUrl());
        beforeSnapshot.setDescription(createdSnapshot.getDescription());
        beforeSnapshot.setIsFeatured(createdSnapshot.getIsFeatured());
        beforeSnapshot.setIsActive(createdSnapshot.getIsActive());
        beforeSnapshot.setMetaKeywords(createdSnapshot.getMetaKeywords());
        beforeSnapshot.setLabourCharges(createdSnapshot.getLabourCharges());
        beforeSnapshot.setRtoFees(createdSnapshot.getRtoFees());
        beforeSnapshot.setDamageExpenses(createdSnapshot.getDamageExpenses());
        beforeSnapshot.setOthersExpenses(createdSnapshot.getOthersExpenses());
        beforeSnapshot.setPricePerSqftAfter(createdSnapshot.getPricePerSqftAfter());
        beforeSnapshot.setTransportationCharge(createdSnapshot.getTransportationCharge());
        beforeSnapshot.setGstCharges(createdSnapshot.getGstCharges());
        beforeSnapshot.setHsnNumber(createdSnapshot.getHsnNumber());
        beforeSnapshot.setSupplierId(createdSnapshot.getSupplierId());
        beforeSnapshot.setDealerId(createdSnapshot.getDealerId());
        beforeSnapshot.setSupplierName(createdSnapshot.getSupplierName());
        beforeSnapshot.setDealerName(createdSnapshot.getDealerName());
        beforeSnapshot.setCreatedAt(createdSnapshot.getCreatedAt());
        beforeSnapshot.setUpdatedAt(createdSnapshot.getUpdatedAt());

        productChangeHistoryService.recordChange(
                savedProduct.getId(),
                beforeSnapshot,
                createdSnapshot,
                "Initial product details on creation");

        return createdSnapshot;
    }
    
    public ProductResponseDTO getProductById(Long id, String location) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
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
        if (location == null || location.trim().isEmpty()) {
            throw new RuntimeException("Location is required to fetch products.");
        }
        List<Product> products = productRepository.findByLocation(location);
        return products.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
    
    public ProductResponseDTO updateProduct(Long id, ProductRequestDTO productRequestDTO, String location) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        if (!location.equals(product.getLocation())) {
            throw new RuntimeException("Product not found with id: " + id);
        }
        if (!product.getSlug().equals(productRequestDTO.getSlug()) && 
            productRepository.existsBySlugAndLocation(productRequestDTO.getSlug(), location)) {
            throw new RuntimeException("Product with slug '" + productRequestDTO.getSlug() + "' already exists for location: " + location);
        }

        ProductResponseDTO beforeSnapshot = convertToResponseDTO(product);
        
        product.setName(productRequestDTO.getName());
        product.setSlug(productRequestDTO.getSlug());
        product.setProductType(productRequestDTO.getProductType());
        
        if (productRequestDTO.getPricePerUnit() != null) {
            product.setPricePerUnit(BigDecimal.valueOf(productRequestDTO.getPricePerUnit())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getQuantity() != null) {
            product.setQuantity(BigDecimal.valueOf(productRequestDTO.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getUnit() != null && !productRequestDTO.getUnit().trim().isEmpty()) {
            product.setUnit(productRequestDTO.getUnit());
        }
        
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
        if (productRequestDTO.getLabourCharges() != null) {
            product.setLabourCharges(BigDecimal.valueOf(productRequestDTO.getLabourCharges())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getRtoFees() != null) {
            product.setRtoFees(BigDecimal.valueOf(productRequestDTO.getRtoFees())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getDamageExpenses() != null) {
            product.setDamageExpenses(BigDecimal.valueOf(productRequestDTO.getDamageExpenses())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getOthersExpenses() != null) {
            product.setOthersExpenses(BigDecimal.valueOf(productRequestDTO.getOthersExpenses())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getPricePerSqftAfter() != null) {
            product.setPricePerSqftAfter(BigDecimal.valueOf(productRequestDTO.getPricePerSqftAfter())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getTransportationCharge() != null) {
            product.setTransportationCharge(BigDecimal.valueOf(productRequestDTO.getTransportationCharge())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getGstCharges() != null) {
            product.setGstCharges(BigDecimal.valueOf(productRequestDTO.getGstCharges())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (productRequestDTO.getHsnNumber() != null) {
            product.setHsnNumber(productRequestDTO.getHsnNumber().trim().isEmpty()
                    ? null : productRequestDTO.getHsnNumber().trim());
        }

        applySupplierDealerOnUpdate(product, productRequestDTO, location);
        
        Product updatedProduct = productRepository.save(product);
        ProductResponseDTO afterSnapshot = convertToResponseDTO(updatedProduct);

        productChangeHistoryService.recordChange(
                id,
                beforeSnapshot,
                afterSnapshot,
                productRequestDTO.getUpdateNotes());

        recordQuantityDeltaIfChanged(id, beforeSnapshot, afterSnapshot, productRequestDTO.getUpdateNotes());

        return afterSnapshot;
    }

    /** Create: only positive ids link; null, omitted, or non-positive leaves association unset. */
    private void applySupplierDealerOnCreate(Product product, ProductRequestDTO dto, String location) {
        Long sid = dto.getSupplierId();
        if (sid != null && sid > 0) {
            product.setSupplier(supplierService.requireForProduct(sid, location));
        } else {
            product.setSupplier(null);
        }
        Long did = dto.getDealerId();
        if (did != null && did > 0) {
            product.setDealer(dealerService.requireForProduct(did, location));
        } else {
            product.setDealer(null);
        }
    }

    /**
     * Update: null/omitted on DTO = do not change; non-positive = clear; positive = set (validated for location).
     */
    private void applySupplierDealerOnUpdate(Product product, ProductRequestDTO dto, String location) {
        Long sid = dto.getSupplierId();
        if (sid != null) {
            if (sid <= 0) {
                product.setSupplier(null);
            } else {
                product.setSupplier(supplierService.requireForProduct(sid, location));
            }
        }
        Long did = dto.getDealerId();
        if (did != null) {
            if (did <= 0) {
                product.setDealer(null);
            } else {
                product.setDealer(dealerService.requireForProduct(did, location));
            }
        }
    }

    private void recordQuantityDeltaIfChanged(Long productId, ProductResponseDTO before, ProductResponseDTO after, String updateNotes) {
        if (before.getQuantity() == null || after.getQuantity() == null) {
            return;
        }
        BigDecimal prev = BigDecimal.valueOf(before.getQuantity()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal next = BigDecimal.valueOf(after.getQuantity()).setScale(2, RoundingMode.HALF_UP);
        if (prev.compareTo(next) == 0) {
            return;
        }
        String note = "Quantity adjusted via product update";
        if (updateNotes != null && !updateNotes.isBlank()) {
            note = note + ". " + updateNotes.trim();
        }
        inventoryHistoryService.saveInventoryHistory(
                productId,
                InventoryActionType.UPDATE,
                next.subtract(prev),
                prev,
                next,
                null,
                note);
    }

    @Transactional(readOnly = true)
    public List<ProductChangeHistoryResponseDTO> getProductChangeHistory(Long productId, String location) {
        requireProductForLocation(productId, location);
        return productChangeHistoryService.listForProduct(productId);
    }
    
    public void deleteProduct(Long id, String location) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
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
        
        BigDecimal currentStock = product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO;
        String unit = product.getUnit() != null ? product.getUnit() : "sqft";
        BigDecimal newStock = currentStock.subtract(quantityToDeduct);
        
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Insufficient stock for product: " + product.getName() + 
                    ". Available: " + currentStock + " " + unit + ", Requested: " + quantityToDeduct + " " + unit);
        }
    }
    
    /**
     * Deduct stock from a product by product ID (same behavior as before; audit row is appended when history is enabled).
     */
    public void deductStock(Long productId, BigDecimal quantityToDeduct) {
        deductStock(productId, quantityToDeduct, null, "Stock deducted via bill");
    }

    /**
     * Deduct stock and record a SALE line in inventory history.
     *
     * @param referenceId optional bill id (GST or Non-GST — disambiguate using notes in UI if needed)
     */
    public void deductStock(Long productId, BigDecimal quantityToDeduct, Long referenceId, String notes) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        BigDecimal currentStock = product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO;
        String unit = product.getUnit() != null ? product.getUnit() : "sqft";
        BigDecimal newStock = currentStock.subtract(quantityToDeduct);

        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Insufficient stock for product: " + product.getName() +
                    ". Available: " + currentStock + " " + unit + ", Requested: " + quantityToDeduct + " " + unit);
        }

        BigDecimal previousQty = currentStock.setScale(2, RoundingMode.HALF_UP);
        BigDecimal scaledNew = newStock.setScale(2, RoundingMode.HALF_UP);
        product.setQuantity(scaledNew);
        productRepository.save(product);

        inventoryHistoryService.saveInventoryHistory(
                productId,
                InventoryActionType.SALE,
                quantityToDeduct.negate(),
                previousQty,
                scaledNew,
                referenceId,
                notes != null ? notes : "Stock deducted via bill");
    }

    /**
     * Manual stock increase (admin). Persists product quantity and an ADD history row.
     */
    public ProductResponseDTO addStock(Long productId, BigDecimal quantityToAdd, String notes, String location) {
        Product product = requireProductForLocation(productId, location);
        BigDecimal previous = product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO;
        BigDecimal newQty = previous.add(quantityToAdd).setScale(2, RoundingMode.HALF_UP);
        product.setQuantity(newQty);
        Product saved = productRepository.save(product);
        inventoryHistoryService.saveInventoryHistory(
                productId,
                InventoryActionType.ADD,
                quantityToAdd.setScale(2, RoundingMode.HALF_UP),
                previous.setScale(2, RoundingMode.HALF_UP),
                newQty,
                null,
                notes != null && !notes.isBlank() ? notes.trim() : "Manual stock add");
        return convertToResponseDTO(saved);
    }

    /**
     * Manual stock set to an absolute quantity (admin). Persists and records UPDATE with delta.
     */
    public ProductResponseDTO updateStockToNewQuantity(Long productId, BigDecimal newQuantity, String notes, String location) {
        Product product = requireProductForLocation(productId, location);
        BigDecimal previous = product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO;
        BigDecimal newQty = newQuantity.setScale(2, RoundingMode.HALF_UP);
        BigDecimal delta = newQty.subtract(previous).setScale(2, RoundingMode.HALF_UP);
        product.setQuantity(newQty);
        Product saved = productRepository.save(product);
        inventoryHistoryService.saveInventoryHistory(
                productId,
                InventoryActionType.UPDATE,
                delta,
                previous.setScale(2, RoundingMode.HALF_UP),
                newQty,
                null,
                notes != null && !notes.isBlank() ? notes.trim() : "Manual stock update");
        return convertToResponseDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<InventoryHistoryResponseDTO> getStockHistoryForProduct(Long productId, String location) {
        requireProductForLocation(productId, location);
        return inventoryHistoryService.getHistoryForProduct(productId);
    }

    @Transactional(readOnly = true)
    public List<InventoryHistoryResponseDTO> getStockHistoryForLocation(
            String location,
            LocalDate from,
            LocalDate to,
            InventoryActionType actionType,
            Integer limit) {
        if (location == null || location.isBlank()) {
            throw new RuntimeException("Location is required");
        }
        return inventoryHistoryService.getHistoryForLocation(location, from, to, actionType, limit);
    }

    /**
     * Quantity at end of {@code rangeEnd} (and optionally {@code rangeStart}) from current stock minus summed
     * {@code inventory_history.quantity_changed} for rows with {@code created_at} on or after the start of the day after each date.
     */
    @Transactional(readOnly = true)
    public StockAsOfResponseDTO getStockAsOf(String location, LocalDate rangeEnd, LocalDate rangeStart) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Location is required");
        }
        if (rangeEnd == null) {
            throw new IllegalArgumentException("endDate is required");
        }
        if (rangeStart != null && rangeStart.isAfter(rangeEnd)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }
        String loc = location.trim();
        LocalDateTime afterEnd = rangeEnd.plusDays(1).atStartOfDay();
        LocalDateTime afterStart = rangeStart != null ? rangeStart.plusDays(1).atStartOfDay() : null;

        Map<Long, BigDecimal> sumAfterEnd = toProductSumMap(
                inventoryHistoryRepository.sumQuantityChangedByProductAfterInstant(loc, afterEnd));
        Map<Long, BigDecimal> sumAfterStart = new HashMap<>();
        if (afterStart != null) {
            sumAfterStart.putAll(toProductSumMap(
                    inventoryHistoryRepository.sumQuantityChangedByProductAfterInstant(loc, afterStart)));
        }

        List<Product> products = productRepository.findByLocation(loc);
        List<ProductStockAsOfRowDTO> rows = new ArrayList<>();
        for (Product p : products) {
            boolean existedEnd = !p.getCreatedAt().toLocalDate().isAfter(rangeEnd);
            boolean existedStart = rangeStart == null || !p.getCreatedAt().toLocalDate().isAfter(rangeStart);

            BigDecimal current = p.getQuantity() != null ? p.getQuantity() : BigDecimal.ZERO;
            current = current.setScale(2, RoundingMode.HALF_UP);

            BigDecimal qEnd = null;
            if (existedEnd) {
                BigDecimal sumE = sumAfterEnd.getOrDefault(p.getId(), BigDecimal.ZERO);
                qEnd = current.subtract(sumE).setScale(2, RoundingMode.HALF_UP);
                if (qEnd.compareTo(BigDecimal.ZERO) < 0) {
                    qEnd = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
            }

            BigDecimal qStart = null;
            if (rangeStart != null && existedStart) {
                BigDecimal sumS = sumAfterStart.getOrDefault(p.getId(), BigDecimal.ZERO);
                qStart = current.subtract(sumS).setScale(2, RoundingMode.HALF_UP);
                if (qStart.compareTo(BigDecimal.ZERO) < 0) {
                    qStart = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
            }

            rows.add(new ProductStockAsOfRowDTO(p.getId(), qEnd, qStart));
        }

        String explanation =
                "Quantities are derived from current stock minus every inventory movement logged after each selected date "
                        + "(sales, stock adds, manual quantity updates). Products created after a date show no figure for that date. "
                        + "If some past movements were never recorded in inventory history, older dates may be inaccurate.";

        return new StockAsOfResponseDTO(rangeEnd, rangeStart, rows, explanation);
    }

    private static Map<Long, BigDecimal> toProductSumMap(List<Object[]> sumRows) {
        Map<Long, BigDecimal> m = new HashMap<>();
        if (sumRows == null) {
            return m;
        }
        for (Object[] row : sumRows) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            Long pid = ((Number) row[0]).longValue();
            m.put(pid, toScaledBd(row[1]));
        }
        return m;
    }

    private static BigDecimal toScaledBd(Object v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (v instanceof BigDecimal) {
            return ((BigDecimal) v).setScale(2, RoundingMode.HALF_UP);
        }
        if (v instanceof Number) {
            return BigDecimal.valueOf(((Number) v).doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private Product requireProductForLocation(Long productId, String location) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        if (location == null || !location.equals(product.getLocation())) {
            throw new RuntimeException("Product not found with id: " + productId);
        }
        return product;
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
        deductStockByName(productName, quantityToDeduct, null, "Stock deducted via bill");
    }

    public void deductStockByName(String productName, BigDecimal quantityToDeduct, Long referenceId, String notes) {
        Product product = getProductEntityByName(productName);
        deductStock(product.getId(), quantityToDeduct, referenceId, notes);
    }

    /**
     * Increase stock by product name (used when reversing a deleted bill).
     * Records an ADD history row via {@link #addStock(Long, BigDecimal, String, String)}.
     */
    public void addStockByName(String productName, BigDecimal quantityToAdd, String notes, String location) {
        Product product = getProductEntityByName(productName);
        if (location == null || !location.equals(product.getLocation())) {
            throw new RuntimeException("Product not found with name: " + productName);
        }
        addStock(product.getId(), quantityToAdd, notes, location);
    }
    
    private ProductResponseDTO convertToResponseDTO(Product product) {
        ProductResponseDTO responseDTO = new ProductResponseDTO();
        responseDTO.setId(product.getId());
        responseDTO.setName(product.getName());
        responseDTO.setSlug(product.getSlug());
        responseDTO.setCategoryId(product.getCategoryId());
        responseDTO.setProductType(product.getProductType());
        responseDTO.setColor(product.getColor());
        responseDTO.setPricePerUnit(product.getPricePerUnit() != null ? product.getPricePerUnit().doubleValue() : null);
        responseDTO.setQuantity(product.getQuantity() != null ? product.getQuantity().doubleValue() : null);
        responseDTO.setUnit(product.getUnit() != null ? product.getUnit() : "sqft"); // Default for backward compatibility
        responseDTO.setPrimaryImageUrl(product.getPrimaryImageUrl());
        responseDTO.setDescription(product.getDescription());
        responseDTO.setIsFeatured(product.getIsFeatured());
        responseDTO.setIsActive(product.getIsActive());
        responseDTO.setMetaKeywords(product.getMetaKeywords());
        responseDTO.setLabourCharges(product.getLabourCharges() != null ? product.getLabourCharges().doubleValue() : null);
        responseDTO.setRtoFees(product.getRtoFees() != null ? product.getRtoFees().doubleValue() : null);
        responseDTO.setDamageExpenses(product.getDamageExpenses() != null ? product.getDamageExpenses().doubleValue() : null);
        responseDTO.setOthersExpenses(product.getOthersExpenses() != null ? product.getOthersExpenses().doubleValue() : null);
        responseDTO.setPricePerSqftAfter(product.getPricePerSqftAfter() != null ? product.getPricePerSqftAfter().doubleValue() : null);
        responseDTO.setTransportationCharge(product.getTransportationCharge() != null ? product.getTransportationCharge().doubleValue() : null);
        responseDTO.setGstCharges(product.getGstCharges() != null ? product.getGstCharges().doubleValue() : null);
        responseDTO.setHsnNumber(product.getHsnNumber());
        if (product.getSupplier() != null) {
            responseDTO.setSupplierId(product.getSupplier().getId());
            responseDTO.setSupplierName(product.getSupplier().getName());
        } else {
            responseDTO.setSupplierId(null);
            responseDTO.setSupplierName(null);
        }
        if (product.getDealer() != null) {
            responseDTO.setDealerId(product.getDealer().getId());
            responseDTO.setDealerName(product.getDealer().getName());
        } else {
            responseDTO.setDealerId(null);
            responseDTO.setDealerName(null);
        }
        responseDTO.setCreatedAt(product.getCreatedAt());
        responseDTO.setUpdatedAt(product.getUpdatedAt());
        
        return responseDTO;
    }
}

