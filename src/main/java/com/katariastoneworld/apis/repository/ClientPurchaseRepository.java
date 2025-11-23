package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.ClientPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientPurchaseRepository extends JpaRepository<ClientPurchase, Long> {
    
    @Query("SELECT cp FROM ClientPurchase cp WHERE cp.location = :location ORDER BY cp.purchaseDate DESC, cp.createdAt DESC")
    List<ClientPurchase> findByLocationOrderByPurchaseDateDesc(@Param("location") String location);
    
    @Query("SELECT cp FROM ClientPurchase cp WHERE cp.id = :id AND cp.location = :location")
    Optional<ClientPurchase> findByIdAndLocation(@Param("id") Long id, @Param("location") String location);
}

