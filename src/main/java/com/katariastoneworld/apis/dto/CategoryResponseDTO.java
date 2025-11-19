package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponseDTO {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("image_url")
    private String imageUrl;
    
    @JsonProperty("category_type")
    private String categoryType;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("display_order")
    private Integer displayOrder;
    
    @JsonProperty("is_active")
    private Boolean isActive;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}

