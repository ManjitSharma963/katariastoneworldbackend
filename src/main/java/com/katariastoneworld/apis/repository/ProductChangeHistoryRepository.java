package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.ProductChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductChangeHistoryRepository extends JpaRepository<ProductChangeHistory, Long> {

    List<ProductChangeHistory> findByProductIdOrderByCreatedAtDesc(Long productId);
}
