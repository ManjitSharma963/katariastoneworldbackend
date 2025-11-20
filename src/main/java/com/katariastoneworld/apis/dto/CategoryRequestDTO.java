package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategoryRequestDTO {
    
    @NotBlank(message = "Category name is required")
    private String name;
    
    @JsonAlias({"image_url", "imageUrl"})
    private String imageUrl;
    
    @NotBlank(message = "Category type is required")
    @JsonAlias({"category_type", "categoryType"})
    private String categoryType; // "room" or "material"
    
    private String description;
    
    @JsonAlias({"display_order", "displayOrder"})
    @PositiveOrZero(message = "Display order must be positive or zero")
    private Integer displayOrder = 0;
    
    @JsonAlias({"is_active", "isActive"})
    private Boolean isActive = true;
}

