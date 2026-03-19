package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.ProductRequestDTO;
import com.katariastoneworld.apis.dto.ProductResponseDTO;
import com.katariastoneworld.apis.service.ProductService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

