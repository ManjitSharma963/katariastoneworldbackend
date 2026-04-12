package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.LoanLender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoanLenderRepository extends JpaRepository<LoanLender, Long> {

    Optional<LoanLender> findByLocationAndNameKey(String location, String nameKey);

    List<LoanLender> findByLocationOrderByDisplayNameAsc(String location);

    /** Matches location case-insensitively and ignores leading/trailing spaces (JWT vs DB drift). */
    @Query("SELECT l FROM LoanLender l WHERE LOWER(TRIM(l.location)) = LOWER(TRIM(:loc)) ORDER BY l.displayName ASC")
    List<LoanLender> findByLocationNormalizedOrderByDisplayNameAsc(@Param("loc") String location);

    @Query("SELECT l FROM LoanLender l WHERE LOWER(TRIM(l.location)) = LOWER(TRIM(:loc)) AND l.nameKey = :nameKey")
    Optional<LoanLender> findByLocationNormalizedAndNameKey(@Param("loc") String location, @Param("nameKey") String nameKey);
}
