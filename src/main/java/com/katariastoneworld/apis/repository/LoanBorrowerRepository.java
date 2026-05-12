package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.LoanBorrower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoanBorrowerRepository extends JpaRepository<LoanBorrower, Long> {

    Optional<LoanBorrower> findByLocationAndNameKey(String location, String nameKey);

    List<LoanBorrower> findByLocationOrderByDisplayNameAsc(String location);

    @Query("SELECT b FROM LoanBorrower b WHERE LOWER(TRIM(b.location)) = LOWER(TRIM(:loc)) ORDER BY b.displayName ASC")
    List<LoanBorrower> findByLocationNormalizedOrderByDisplayNameAsc(@Param("loc") String location);

    @Query("SELECT b FROM LoanBorrower b WHERE LOWER(TRIM(b.location)) = LOWER(TRIM(:loc)) AND b.nameKey = :nameKey")
    Optional<LoanBorrower> findByLocationNormalizedAndNameKey(@Param("loc") String location, @Param("nameKey") String nameKey);
}
