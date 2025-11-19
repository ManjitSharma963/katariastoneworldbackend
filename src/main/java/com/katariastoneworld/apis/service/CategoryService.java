package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.CategoryResponseDTO;
import com.katariastoneworld.apis.entity.Category;
import com.katariastoneworld.apis.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    
    @Autowired
    private CategoryRepository categoryRepository;
    
    public List<CategoryResponseDTO> getAllCategories() {
        List<Category> categories = categoryRepository.findAllByOrderByDisplayOrderAsc();
        return categories.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public List<CategoryResponseDTO> getCategoriesByType(String categoryType) {
        // Normalize the category type (case-insensitive)
        String normalizedType = categoryType != null ? categoryType.toLowerCase().trim() : null;
        
        // Validate that it's a valid type (optional validation)
        if (normalizedType != null && !normalizedType.isEmpty() && 
            !normalizedType.equals("room") && !normalizedType.equals("material")) {
            throw new RuntimeException("Invalid category type: " + categoryType + ". Must be 'room' or 'material'");
        }
        
        List<Category> categories = categoryRepository.findByCategoryTypeOrderByDisplayOrderAsc(normalizedType);
        return categories.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public List<CategoryResponseDTO> getActiveCategories() {
        List<Category> categories = categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        return categories.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public List<CategoryResponseDTO> getActiveCategoriesByType(String categoryType) {
        // Normalize the category type (case-insensitive)
        String normalizedType = categoryType != null ? categoryType.toLowerCase().trim() : null;
        
        // Validate that it's a valid type (optional validation)
        if (normalizedType != null && !normalizedType.isEmpty() && 
            !normalizedType.equals("room") && !normalizedType.equals("material")) {
            throw new RuntimeException("Invalid category type: " + categoryType + ". Must be 'room' or 'material'");
        }
        
        List<Category> categories = categoryRepository.findByCategoryTypeAndIsActiveTrueOrderByDisplayOrderAsc(normalizedType);
        return categories.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public CategoryResponseDTO getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
        return convertToDTO(category);
    }
    
    private CategoryResponseDTO convertToDTO(Category category) {
        CategoryResponseDTO dto = new CategoryResponseDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setImageUrl(category.getImageUrl());
        dto.setCategoryType(category.getCategoryType()); // Already a String
        dto.setDescription(category.getDescription());
        dto.setDisplayOrder(category.getDisplayOrder());
        dto.setIsActive(category.getIsActive());
        dto.setCreatedAt(category.getCreatedAt());
        dto.setUpdatedAt(category.getUpdatedAt());
        return dto;
    }
}

