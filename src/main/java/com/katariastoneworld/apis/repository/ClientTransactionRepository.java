package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.ClientTransaction;
import com.katariastoneworld.apis.entity.ClientTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ClientTransactionRepository extends JpaRepository<ClientTransaction, Long> {
    List<ClientTransaction> findByLocationOrderByTransactionDateDescIdDesc(String location);
    List<ClientTransaction> findByLocationAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(String location, LocalDate from, LocalDate to);
    List<ClientTransaction> findByLocationAndTransactionTypeAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
            String location, ClientTransactionType type, LocalDate from, LocalDate to);
}

