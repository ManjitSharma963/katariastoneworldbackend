package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.dto.ProductRequestDTO;
import com.katariastoneworld.apis.dto.ProductResponseDTO;
import com.katariastoneworld.apis.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/inventory", "/inventory"})
@CrossOrigin(origins = {"http://localhost:3001", "http://localhost:3000"}, allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.PATCH}, maxAge = 3600)
public class ProductController {
    
    @Autowired
    private ProductService productService;
    
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO productRequestDTO) {
        ProductResponseDTO response = productService.createProduct(productRequestDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> getAllProducts() {
        List<ProductResponseDTO> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable Long id) {
        ProductResponseDTO response = productService.getProductById(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProductResponseDTO> getProductBySlug(@PathVariable String slug) {
        ProductResponseDTO response = productService.getProductBySlug(slug);
        return ResponseEntity.ok(response);
    }
}

