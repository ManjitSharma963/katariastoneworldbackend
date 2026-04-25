package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Optional<Product> findByName(String name);
    List<Product> findByLocation(String location);
    List<Product> findByOwnerUserId(Long ownerUserId);
    Optional<Product> findBySlugAndLocation(String slug, String location);
    Optional<Product> findBySlugAndOwnerUserId(String slug, Long ownerUserId);
    boolean existsBySlugAndLocation(String slug, String location);
    boolean existsBySlugAndOwnerUserId(String slug, Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}

