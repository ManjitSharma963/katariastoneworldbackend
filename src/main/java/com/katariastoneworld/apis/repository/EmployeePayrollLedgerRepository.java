package com.katariastoneworld.apis.repository;

import com.katariastoneworld.apis.entity.EmployeePayrollLedgerEntry;
import com.katariastoneworld.apis.entity.EmployeePayrollLedgerEntry.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.katariastoneworld.apis.entity.BillPaymentMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface EmployeePayrollLedgerRepository extends JpaRepository<EmployeePayrollLedgerEntry, Long> {

    List<EmployeePayrollLedgerEntry> findByEmployeeIdAndEventDateBetweenOrderByEventDateAscIdAsc(Long employeeId, LocalDate from,
            LocalDate to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM EmployeePayrollLedgerEntry e " +
            "WHERE e.location = :loc AND e.employeeId = :empId AND e.eventType = :type AND e.eventDate < :before")
    BigDecimal sumAmountByEmployeeTypeBefore(@Param("loc") String location, @Param("empId") Long employeeId,
            @Param("type") EventType type, @Param("before") LocalDate before);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM EmployeePayrollLedgerEntry e " +
            "WHERE e.location = :loc AND e.employeeId = :empId AND e.eventType = :type AND e.month = :month")
    BigDecimal sumAmountByEmployeeTypeInMonth(@Param("loc") String location, @Param("empId") Long employeeId,
            @Param("type") EventType type, @Param("month") String month);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM EmployeePayrollLedgerEntry e " +
            "WHERE e.location = :loc AND e.employeeId = :empId AND e.eventType IN :types AND e.month = :month")
    BigDecimal sumAmountByEmployeeTypesInMonth(@Param("loc") String location, @Param("empId") Long employeeId,
            @Param("types") List<EventType> types, @Param("month") String month);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM EmployeePayrollLedgerEntry e " +
            "WHERE e.location = :loc AND e.employeeId = :empId AND e.eventType = :type")
    BigDecimal sumAmountByEmployeeTypeAllTime(@Param("loc") String location, @Param("empId") Long employeeId,
            @Param("type") EventType type);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM EmployeePayrollLedgerEntry e WHERE LOWER(TRIM(e.location)) = LOWER(TRIM(:loc)) "
            + "AND e.eventDate >= :from AND e.eventDate <= :to "
            + "AND e.eventType IN :eventTypes AND e.paymentMode IN :modes")
    BigDecimal sumAmountByLocationDateRangeTypesAndModes(
            @Param("loc") String location,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("eventTypes") Collection<EmployeePayrollLedgerEntry.EventType> eventTypes,
            @Param("modes") Collection<BillPaymentMode> modes);
}

