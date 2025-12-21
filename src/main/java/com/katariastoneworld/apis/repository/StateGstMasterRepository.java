package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.StateGstMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StateGstMasterRepository extends JpaRepository<StateGstMaster, Long> {
    
    Optional<StateGstMaster> findByStateNameIgnoreCase(String stateName);
}

