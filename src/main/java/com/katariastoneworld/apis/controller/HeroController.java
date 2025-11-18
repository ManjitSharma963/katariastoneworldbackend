package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.dto.HeroResponseDTO;
import com.katariastoneworld.apis.service.HeroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/heroes", "/heroes"})
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
}

