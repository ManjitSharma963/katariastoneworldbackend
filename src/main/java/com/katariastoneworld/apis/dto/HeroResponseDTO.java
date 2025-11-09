package com.katariastoneworld.apis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeroResponseDTO {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("image_url")
    private String imageUrl;
    
    @JsonProperty("display_order")
    private Integer displayOrder;
    
    @JsonProperty("is_active")
    private Boolean isActive;
    
    @JsonProperty("subtitle")
    private String subtitle;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}

