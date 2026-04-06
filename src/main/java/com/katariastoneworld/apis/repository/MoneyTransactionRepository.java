package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.MoneyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MoneyTransactionRepository extends JpaRepository<MoneyTransaction, Long> {

    Optional<MoneyTransaction> findByReferenceTypeAndReferenceIdAndIsDeletedFalse(String referenceType, String referenceId);

    @Query("SELECT t FROM MoneyTransaction t WHERE t.isDeleted = false AND t.location = :loc "
            + "AND t.eventDate = :d ORDER BY t.createdAt ASC, t.id ASC")
    List<MoneyTransaction> listForLocationAndDay(@Param("loc") String location, @Param("d") LocalDate d);

    @Query("SELECT t FROM MoneyTransaction t WHERE t.isDeleted = false AND t.location = :loc "
            + "AND t.eventDate >= :from AND t.eventDate <= :to ORDER BY t.eventDate ASC, t.createdAt ASC, t.id ASC")
    List<MoneyTransaction> listForLocationAndRange(@Param("loc") String location,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
