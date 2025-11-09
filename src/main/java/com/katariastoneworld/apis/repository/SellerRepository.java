package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Seller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findFirstByOrderByIdAsc();
}

