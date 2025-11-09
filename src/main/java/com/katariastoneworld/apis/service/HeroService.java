package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.HeroResponseDTO;
import com.katariastoneworld.apis.entity.Hero;
import com.katariastoneworld.apis.repository.HeroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HeroService {
    
    @Autowired
    private HeroRepository heroRepository;
    
    public List<HeroResponseDTO> getAllHeroes() {
        List<Hero> heroes = heroRepository.findAllByOrderByDisplayOrderAsc();
        return heroes.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public List<HeroResponseDTO> getActiveHeroes() {
        List<Hero> heroes = heroRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        return heroes.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public HeroResponseDTO getHeroById(Long id) {
        Hero hero = heroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hero not found with id: " + id));
        return convertToDTO(hero);
    }
    
    private HeroResponseDTO convertToDTO(Hero hero) {
        HeroResponseDTO dto = new HeroResponseDTO();
        dto.setId(hero.getId());
        dto.setTitle(hero.getTitle());
        dto.setImageUrl(hero.getImageUrl());
        dto.setDisplayOrder(hero.getDisplayOrder());
        dto.setIsActive(hero.getIsActive());
        dto.setSubtitle(hero.getSubtitle());
        dto.setCreatedAt(hero.getCreatedAt());
        dto.setUpdatedAt(hero.getUpdatedAt());
        return dto;
    }
}

