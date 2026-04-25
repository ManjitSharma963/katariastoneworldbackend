package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DailyClosingReportDTO;
import com.katariastoneworld.apis.dto.DailyClosingSnapshotDTO;
import com.katariastoneworld.apis.entity.DailyClosingSnapshot;
import com.katariastoneworld.apis.repository.DailyClosingSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DailyClosingSnapshotService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Autowired
    private DailyClosingSnapshotRepository dailyClosingSnapshotRepository;

    @Autowired
    private DailyClosingReportService dailyClosingReportService;

    public DailyClosingSnapshotDTO createOrUpdateSnapshot(LocalDate date, String location) {
        LocalDate d = date != null ? date : LocalDate.now();
        String loc = location == null ? "" : location.trim();
        if (loc.isBlank()) {
            throw new IllegalArgumentException("Location is required");
        }

        DailyClosingReportDTO report = dailyClosingReportService.buildReportForPeriod(d, d, loc);
        DailyClosingSnapshot snapshot = dailyClosingSnapshotRepository
                .findByLocationAndSnapshotDate(loc, d)
                .orElseGet(DailyClosingSnapshot::new);

        snapshot.setLocation(loc);
        snapshot.setSnapshotDate(d);
        snapshot.setTotalSales(scale(report.getTotalSales()));
        snapshot.setTotalExpenses(scale(report.getTotalExpenses()));
        snapshot.setTotalCollections(scale(report.getTotalCollected()));
        snapshot.setInHand(scale(report.getInHandAmount()));
        snapshot.setReconciliationStatus(resolveReconciliationStatus(report.getCollectionsReconciliationOk()));

        DailyClosingSnapshot saved = dailyClosingSnapshotRepository.save(snapshot);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public DailyClosingSnapshotDTO getSnapshot(LocalDate date, String location) {
        LocalDate d = date != null ? date : LocalDate.now();
        String loc = location == null ? "" : location.trim();
        DailyClosingSnapshot snapshot = dailyClosingSnapshotRepository
                .findByLocationAndSnapshotDate(loc, d)
                .orElseThrow(() -> new RuntimeException("Snapshot not found for date: " + d));
        return toDto(snapshot);
    }

    @Transactional(readOnly = true)
    public List<DailyClosingSnapshotDTO> getSnapshots(LocalDate from, LocalDate to, String location) {
        String loc = location == null ? "" : location.trim();
        LocalDate start = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? to : LocalDate.now();
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
        return dailyClosingSnapshotRepository
                .findByLocationAndSnapshotDateBetweenOrderBySnapshotDateDesc(loc, start, end)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private static BigDecimal scale(Double value) {
        if (value == null) {
            return ZERO;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String resolveReconciliationStatus(Boolean reconciliationOk) {
        if (reconciliationOk == null) {
            return "UNKNOWN";
        }
        return reconciliationOk ? "RECONCILED" : "MISMATCH";
    }

    private DailyClosingSnapshotDTO toDto(DailyClosingSnapshot snapshot) {
        return DailyClosingSnapshotDTO.builder()
                .id(snapshot.getId())
                .location(snapshot.getLocation())
                .snapshotDate(snapshot.getSnapshotDate())
                .totalSales(snapshot.getTotalSales() != null ? snapshot.getTotalSales().doubleValue() : 0.0)
                .totalExpenses(snapshot.getTotalExpenses() != null ? snapshot.getTotalExpenses().doubleValue() : 0.0)
                .totalCollections(snapshot.getTotalCollections() != null ? snapshot.getTotalCollections().doubleValue() : 0.0)
                .inHand(snapshot.getInHand() != null ? snapshot.getInHand().doubleValue() : 0.0)
                .reconciliationStatus(snapshot.getReconciliationStatus())
                .createdAt(snapshot.getCreatedAt())
                .updatedAt(snapshot.getUpdatedAt())
                .build();
    }
}
