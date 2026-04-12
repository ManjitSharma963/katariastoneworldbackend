package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.AddStockRequestDTO;
import com.katariastoneworld.apis.dto.InventoryHistoryResponseDTO;
import com.katariastoneworld.apis.dto.ProductChangeHistoryResponseDTO;
import com.katariastoneworld.apis.dto.ProductRequestDTO;
import com.katariastoneworld.apis.dto.ProductResponseDTO;
import com.katariastoneworld.apis.dto.StockAsOfResponseDTO;
import com.katariastoneworld.apis.dto.UpdateStockRequestDTO;
import com.katariastoneworld.apis.entity.InventoryActionType;
import com.katariastoneworld.apis.service.ProductService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/inventory", "/inventory"})
@Tag(name = "Products/Inventory", description = "Product and inventory management endpoints")
public class ProductController {
    
    @Autowired
    private ProductService productService;
    
    @Operation(summary = "Create a new product", description = "Create a new product in inventory. Requires admin role.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @RequiresRole("admin")
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO productRequestDTO, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        ProductResponseDTO response = productService.createProduct(productRequestDTO, location);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @Operation(summary = "Get all products", description = "Returns all inventory for the authenticated user's location. Location-scoped.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getAllProducts(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        List<ProductResponseDTO> products = productService.getAllProducts(location);
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Add stock manually", description = "Increases quantity on hand. Admin only. Records an ADD row in inventory history.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/add-stock")
    @RequiresRole("admin")
    public ResponseEntity<ProductResponseDTO> addStock(@Valid @RequestBody AddStockRequestDTO body, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        ProductResponseDTO dto = productService.addStock(
                body.getProductId(),
                BigDecimal.valueOf(body.getQuantity()),
                body.getNotes(),
                location);
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Set stock to a new quantity", description = "Manual override of on-hand quantity. Admin only. Records an UPDATE row in inventory history.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/update-stock")
    @RequiresRole("admin")
    public ResponseEntity<ProductResponseDTO> updateStock(@Valid @RequestBody UpdateStockRequestDTO body, HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        ProductResponseDTO dto = productService.updateStockToNewQuantity(
                body.getProductId(),
                BigDecimal.valueOf(body.getNewQuantity()),
                body.getNotes(),
                location);
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Stock history for a product", description = "Audit trail for the product, newest first. Empty if no movements were recorded yet.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/history/{productId}")
    public ResponseEntity<List<InventoryHistoryResponseDTO>> getStockHistory(
            @PathVariable Long productId,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(productService.getStockHistoryForProduct(productId, location));
    }

    @Operation(summary = "Stock history for all products",
            description = "Returns inventory movement rows across all products for your location, newest first. Supports optional date and action filters.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/history")
    public ResponseEntity<List<InventoryHistoryResponseDTO>> getStockHistoryForAllProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false, defaultValue = "1000") Integer limit,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        InventoryActionType parsedActionType = null;
        if (actionType != null && !actionType.isBlank()) {
            try {
                parsedActionType = InventoryActionType.valueOf(actionType.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.ok(productService.getStockHistoryForLocation(location, from, to, parsedActionType, limit));
    }

    @Operation(
            summary = "Stock as of date(s)",
            description = "Reconstructs quantity at the end of endDate (and optionally startDate) from current stock minus inventory_history movements after those dates. "
                    + "Products added after a date show null for that date. See `explanation` in the response.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/stock-as-of")
    public ResponseEntity<StockAsOfResponseDTO> getStockAsOf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(productService.getStockAsOf(location, endDate, startDate));
    }

    @Operation(summary = "Full product edit history", description = "Snapshots before/after each PUT update (prices, GST, stock, etc.). Newest first.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/product-changes/{productId}")
    public ResponseEntity<List<ProductChangeHistoryResponseDTO>> getProductChangeHistory(
            @PathVariable Long productId,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(productService.getProductChangeHistory(productId, location));
    }
    
    @Operation(summary = "Get product by ID", description = "Returns the product if it belongs to your location.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(
            @PathVariable Long id,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        ProductResponseDTO response = productService.getProductById(id, location);
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "Get product by slug", description = "Returns the product if it belongs to your location.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductResponseDTO> getProductBySlug(
            @PathVariable String slug,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        ProductResponseDTO response = productService.getProductBySlug(slug, location);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<ProductResponseDTO> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequestDTO productRequestDTO, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            ProductResponseDTO response = productService.updateProduct(id, productRequestDTO, location);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @DeleteMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id, HttpServletRequest request) {
        try {
            String location = RequestUtil.getLocationFromRequest(request);
            productService.deleteProduct(id, location);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

