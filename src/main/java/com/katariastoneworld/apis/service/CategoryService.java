package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.CategoryRequestDTO;
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
        // Normalize the category type (case-insensitive for querying)
        String normalizedType = categoryType != null ? categoryType.toLowerCase().trim() : null;
        
        if (normalizedType == null || normalizedType.isEmpty()) {
            return getAllCategories();
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
        // Normalize the category type (case-insensitive for querying)
        String normalizedType = categoryType != null ? categoryType.toLowerCase().trim() : null;
        
        if (normalizedType == null || normalizedType.isEmpty()) {
            return getActiveCategories();
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
    
    public CategoryResponseDTO createCategory(CategoryRequestDTO requestDTO) {
        // Validate category type is provided
        String categoryType = requestDTO.getCategoryType() != null 
            ? requestDTO.getCategoryType().trim() 
            : null;
        
        if (categoryType == null || categoryType.isEmpty()) {
            throw new RuntimeException("Category type is required");
        }
        
        // Normalize to lowercase for storage and duplicate checking (case-insensitive)
        String normalizedType = categoryType.toLowerCase();
        
        // Check if category with same name and type already exists (case-insensitive)
        Category existingCategory = categoryRepository.findByNameAndCategoryType(
            requestDTO.getName(), normalizedType);
        if (existingCategory != null) {
            throw new RuntimeException("Category with name '" + requestDTO.getName() + "' and type '" + categoryType + "' already exists");
        }
        
        // Create new category - store normalized type for consistency
        Category category = new Category();
        category.setName(requestDTO.getName());
        category.setImageUrl(requestDTO.getImageUrl());
        category.setCategoryType(normalizedType);
        category.setDescription(requestDTO.getDescription());
        category.setDisplayOrder(requestDTO.getDisplayOrder() != null ? requestDTO.getDisplayOrder() : 0);
        category.setIsActive(requestDTO.getIsActive() != null ? requestDTO.getIsActive() : true);
        
        Category savedCategory = categoryRepository.save(category);
        return convertToDTO(savedCategory);
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

