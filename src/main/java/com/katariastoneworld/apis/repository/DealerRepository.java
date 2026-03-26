package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.Dealer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DealerRepository extends JpaRepository<Dealer, Long> {

    List<Dealer> findByLocationOrderByNameAsc(String location);

    Optional<Dealer> findByIdAndLocation(Long id, String location);
}
