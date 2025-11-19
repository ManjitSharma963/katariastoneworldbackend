package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.dto.CategoryResponseDTO;
import com.katariastoneworld.apis.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    
    @Autowired
    private CategoryService categoryService;
    
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

