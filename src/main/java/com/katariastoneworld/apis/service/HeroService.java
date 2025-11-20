package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.HeroRequestDTO;
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
    
    public HeroResponseDTO createHero(HeroRequestDTO heroRequestDTO) {
        Hero hero = new Hero();
        hero.setTitle(heroRequestDTO.getTitle());
        hero.setImageUrl(heroRequestDTO.getImageUrl());
        hero.setSubtitle(heroRequestDTO.getSubtitle());
        hero.setDisplayOrder(heroRequestDTO.getDisplayOrder() != null ? heroRequestDTO.getDisplayOrder() : 0);
        hero.setIsActive(heroRequestDTO.getIsActive() != null ? heroRequestDTO.getIsActive() : true);
        
        Hero savedHero = heroRepository.save(hero);
        return convertToDTO(savedHero);
    }
    
    public HeroResponseDTO updateHero(Long id, HeroRequestDTO heroRequestDTO) {
        Hero hero = heroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hero not found with id: " + id));
        
        hero.setTitle(heroRequestDTO.getTitle());
        hero.setImageUrl(heroRequestDTO.getImageUrl());
        if (heroRequestDTO.getSubtitle() != null) {
            hero.setSubtitle(heroRequestDTO.getSubtitle());
        }
        if (heroRequestDTO.getDisplayOrder() != null) {
            hero.setDisplayOrder(heroRequestDTO.getDisplayOrder());
        }
        if (heroRequestDTO.getIsActive() != null) {
            hero.setIsActive(heroRequestDTO.getIsActive());
        }
        
        Hero updatedHero = heroRepository.save(hero);
        return convertToDTO(updatedHero);
    }
    
    public void deleteHero(Long id) {
        Hero hero = heroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hero not found with id: " + id));
        heroRepository.delete(hero);
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

