package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.SalesAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesAgentRepository extends JpaRepository<SalesAgent, Long> {

    List<SalesAgent> findByLocationOrderByNameAsc(String location);

    List<SalesAgent> findByLocationAndActiveTrueOrderByNameAsc(String location);

    Optional<SalesAgent> findByIdAndLocation(Long id, String location);
}
