package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.CategoryRequestDTO;
import com.katariastoneworld.apis.dto.CategoryResponseDTO;
import com.katariastoneworld.apis.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    
    @Autowired
    private CategoryService categoryService;
    
    @PostMapping
    @RequiresRole("admin")
    public ResponseEntity<CategoryResponseDTO> createCategory(@Valid @RequestBody CategoryRequestDTO categoryRequestDTO) {
        CategoryResponseDTO response = categoryService.createCategory(categoryRequestDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getAllCategories(
            @RequestParam(required = false) String category_type) {
        
        List<CategoryResponseDTO> categories;
        
        if (category_type != null && !category_type.trim().isEmpty()) {
            categories = categoryService.getCategoriesByType(category_type);
        } else {
            categories = categoryService.getAllCategories();
        }
        
        return ResponseEntity.ok(categories);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> getCategoryById(@PathVariable Long id) {
        CategoryResponseDTO category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(category);
    }
}

