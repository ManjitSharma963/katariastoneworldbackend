package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.DailyClosingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyClosingSnapshotRepository extends JpaRepository<DailyClosingSnapshot, Long> {
    Optional<DailyClosingSnapshot> findByLocationAndSnapshotDate(String location, LocalDate snapshotDate);
    Optional<DailyClosingSnapshot> findFirstByLocationAndSnapshotDateBeforeOrderBySnapshotDateDesc(String location, LocalDate snapshotDate);

    List<DailyClosingSnapshot> findByLocationAndSnapshotDateBetweenOrderBySnapshotDateDesc(
            String location,
            LocalDate from,
            LocalDate to);
}
