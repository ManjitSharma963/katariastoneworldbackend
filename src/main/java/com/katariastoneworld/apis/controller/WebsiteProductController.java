package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.WebsiteProductRequestDTO;
import com.katariastoneworld.apis.dto.WebsiteProductResponseDTO;
import com.katariastoneworld.apis.service.WebsiteProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Website products – for public website display only (not inventory).
 * GET endpoints are public (no authentication). POST/PUT/DELETE require admin.
 */
@RestController
@RequestMapping(value = {"/api/website-products", "/website-products"}, produces = "application/json")
@Tag(name = "Website Products", description = "Products for website display only (not inventory). GET is public; create/update/delete require admin.")
public class WebsiteProductController {

    @Autowired
    private WebsiteProductService websiteProductService;

    @Operation(summary = "Get all website products", description = "Public – no authentication required. Returns active-only by default.")
    @GetMapping
    public ResponseEntity<List<WebsiteProductResponseDTO>> getAllProducts(
            @Parameter(description = "If true, return only active products. Default true.")
            @RequestParam(defaultValue = "true") Boolean activeOnly) {
        return ResponseEntity.ok(websiteProductService.getAllProducts(activeOnly));
    }

    @Operation(summary = "Get website product by ID", description = "Public – no authentication required.")
    @GetMapping("/{id}")
    public ResponseEntity<WebsiteProductResponseDTO> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(websiteProductService.getProductById(id));
    }

    @Operation(summary = "Get website product by slug", description = "Public – no authentication required.")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<WebsiteProductResponseDTO> getProductBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(websiteProductService.getProductBySlug(slug));
    }

    @Operation(summary = "Create website product", description = "Admin only. Any admin can add.")
    @PostMapping
    @RequiresRole("admin")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WebsiteProductResponseDTO> createProduct(@Valid @RequestBody WebsiteProductRequestDTO requestDTO) {
        WebsiteProductResponseDTO created = websiteProductService.createProduct(requestDTO);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @Operation(summary = "Update website product", description = "Admin only.")
    @PutMapping("/{id}")
    @RequiresRole("admin")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WebsiteProductResponseDTO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody WebsiteProductRequestDTO requestDTO) {
        return ResponseEntity.ok(websiteProductService.updateProduct(id, requestDTO));
    }

    @Operation(summary = "Delete website product", description = "Admin only.")
    @DeleteMapping("/{id}")
    @RequiresRole("admin")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        websiteProductService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
