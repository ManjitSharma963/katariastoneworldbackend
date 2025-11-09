package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Hero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HeroRepository extends JpaRepository<Hero, Long> {
    
    List<Hero> findByIsActiveTrueOrderByDisplayOrderAsc();
    
    List<Hero> findAllByOrderByDisplayOrderAsc();
    
    Optional<Hero> findByIdAndIsActiveTrue(Long id);
}

