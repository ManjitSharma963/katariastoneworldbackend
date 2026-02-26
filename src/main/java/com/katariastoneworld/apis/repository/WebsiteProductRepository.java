package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.WebsiteProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebsiteProductRepository extends JpaRepository<WebsiteProduct, Long> {

    List<WebsiteProduct> findByIsActiveTrueOrderByCreatedAtDesc();

    List<WebsiteProduct> findAllByOrderByCreatedAtDesc();

    Optional<WebsiteProduct> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
