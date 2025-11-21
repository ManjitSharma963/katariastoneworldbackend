package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.HeroRequestDTO;
import com.katariastoneworld.apis.dto.HeroResponseDTO;
import com.katariastoneworld.apis.service.HeroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/heroes", "/heroes"})
@Tag(name = "Heroes", description = "Hero section content management endpoints")
public class HeroController {
    
    @Autowired
    private HeroService heroService;
    
    @GetMapping
    public ResponseEntity<List<HeroResponseDTO>> getAllHeroes() {
        List<HeroResponseDTO> heroes = heroService.getAllHeroes();
        return ResponseEntity.ok(heroes);
    }
    
    @GetMapping("/active")
    public ResponseEntity<List<HeroResponseDTO>> getActiveHeroes() {
        List<HeroResponseDTO> heroes = heroService.getActiveHeroes();
        return ResponseEntity.ok(heroes);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<HeroResponseDTO> getHeroById(@PathVariable Long id) {
        HeroResponseDTO hero = heroService.getHeroById(id);
        return ResponseEntity.ok(hero);
    }
    
    @PostMapping
    @RequiresRole("admin")
    public ResponseEntity<HeroResponseDTO> createHero(@Valid @RequestBody HeroRequestDTO heroRequestDTO) {
        HeroResponseDTO response = heroService.createHero(heroRequestDTO);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @PutMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<HeroResponseDTO> updateHero(@PathVariable Long id, @Valid @RequestBody HeroRequestDTO heroRequestDTO) {
        try {
            HeroResponseDTO response = heroService.updateHero(id, heroRequestDTO);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    @DeleteMapping("/{id}")
    @RequiresRole("admin")
    public ResponseEntity<Void> deleteHero(@PathVariable Long id) {
        try {
            heroService.deleteHero(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

