package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Optional<Product> findByName(String name);
    List<Product> findByLocation(String location);
    Optional<Product> findBySlugAndLocation(String slug, String location);
    boolean existsBySlugAndLocation(String slug, String location);
}

