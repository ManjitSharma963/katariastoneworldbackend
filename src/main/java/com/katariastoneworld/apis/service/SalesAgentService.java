package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.constants.AgentCommissionStatus;
import com.katariastoneworld.apis.constants.AgentCommissionType;
import com.katariastoneworld.apis.dto.AgentCommissionHistoryDTO;
import com.katariastoneworld.apis.dto.AgentCommissionStatusUpdateResponseDTO;
import com.katariastoneworld.apis.dto.AssignBillAgentCommissionDTO;
import com.katariastoneworld.apis.dto.BillAgentAssignmentResponseDTO;
import com.katariastoneworld.apis.dto.ExpenseResponseDTO;
import com.katariastoneworld.apis.dto.SalesAgentRequestDTO;
import com.katariastoneworld.apis.dto.SalesAgentResponseDTO;
import com.katariastoneworld.apis.entity.BillGST;
import com.katariastoneworld.apis.entity.BillNonGST;
import com.katariastoneworld.apis.entity.SalesAgent;
import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.repository.SalesAgentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SalesAgentService {

    private final SalesAgentRepository salesAgentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ExpenseService expenseService;
    private final BillGSTRepository billGSTRepository;
    private final BillNonGSTRepository billNonGSTRepository;

    public SalesAgentService(SalesAgentRepository salesAgentRepository,
                             JdbcTemplate jdbcTemplate,
                             ExpenseService expenseService,
                             BillGSTRepository billGSTRepository,
                             BillNonGSTRepository billNonGSTRepository) {
        this.salesAgentRepository = salesAgentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.expenseService = expenseService;
        this.billGSTRepository = billGSTRepository;
        this.billNonGSTRepository = billNonGSTRepository;
    }

    public List<SalesAgentResponseDTO> list(String location, boolean activeOnly) {
        List<SalesAgent> agents = activeOnly
                ? salesAgentRepository.findByLocationAndActiveTrueOrderByNameAsc(location)
                : salesAgentRepository.findByLocationOrderByNameAsc(location);
        Map<Long, AgentStats> stats = loadStatsForLocation(location);
        return agents.stream()
                .map(a -> toResponse(a, stats.get(a.getId())))
                .collect(Collectors.toList());
    }

    public SalesAgentResponseDTO getById(Long id, String location) {
        SalesAgent agent = salesAgentRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        Map<Long, AgentStats> stats = loadStatsForLocation(location);
        return toResponse(agent, stats.get(agent.getId()));
    }

    @Transactional
    public SalesAgentResponseDTO create(SalesAgentRequestDTO dto, String location) {
        SalesAgent agent = new SalesAgent();
        applyRequest(agent, dto);
        agent.setLocation(location);
        SalesAgent saved = salesAgentRepository.save(agent);
        return toResponse(saved, null);
    }

    @Transactional
    public SalesAgentResponseDTO update(Long id, SalesAgentRequestDTO dto, String location) {
        SalesAgent agent = salesAgentRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        applyRequest(agent, dto);
        SalesAgent saved = salesAgentRepository.save(agent);
        Map<Long, AgentStats> stats = loadStatsForLocation(location);
        return toResponse(saved, stats.get(saved.getId()));
    }

    @Transactional
    public void delete(Long id, String location) {
        SalesAgent agent = salesAgentRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Agent not found"));
        Map<Long, AgentStats> stats = loadStatsForLocation(location);
        AgentStats agentStats = stats.get(id);
        if (agentStats != null && agentStats.deals() > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete agent with linked bills. Remove bill assignments first or set the agent inactive.");
        }
        salesAgentRepository.delete(agent);
    }

    public List<AgentCommissionHistoryDTO> commissionHistory(Long agentId, String location) {
        salesAgentRepository.findByIdAndLocation(agentId, location)
                .orElseThrow(() -> new RuntimeException("Agent not found"));

        List<AgentCommissionHistoryDTO> rows = new ArrayList<>();
        rows.addAll(queryGstHistory(agentId, location));
        rows.addAll(queryNonGstHistory(agentId, location));
        rows.sort(Comparator
                .comparing(AgentCommissionHistoryDTO::getBillDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AgentCommissionHistoryDTO::getBillId, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    @Transactional
    public AgentCommissionStatusUpdateResponseDTO updateCommissionStatus(
            String billType, Long billId, String status, String location) {
        String normalized = AgentCommissionStatus.normalize(status);
        if (!AgentCommissionStatus.PAID.equals(normalized) && !AgentCommissionStatus.PENDING.equals(normalized)) {
            throw new IllegalArgumentException("Status must be PENDING or PAID");
        }

        CommissionBillRow billRow = loadCommissionBill(billType, billId, location);
        if (billRow == null) {
            throw new RuntimeException("Bill commission record not found for this agent");
        }

        String currentStatus = billRow.commissionStatus() != null
                ? billRow.commissionStatus().trim().toUpperCase(Locale.ROOT)
                : AgentCommissionStatus.PENDING;
        if (AgentCommissionStatus.CANCELLED.equals(currentStatus)) {
            throw new IllegalArgumentException("Cannot update commission on a cancelled bill");
        }

        boolean expenseCreated = false;
        Long expenseId = null;
        if (AgentCommissionStatus.PAID.equals(normalized) && !AgentCommissionStatus.PAID.equals(currentStatus)) {
            SalesAgent agent = salesAgentRepository.findById(billRow.agentId())
                    .orElseThrow(() -> new RuntimeException("Agent not found"));
            String referenceKey = commissionExpenseReferenceKey(billType, billId);
            boolean alreadyPosted = expenseService.findAgentCommissionExpense(location, referenceKey).isPresent();
            ExpenseResponseDTO expense = expenseService.createAgentCommissionExpenseIfAbsent(
                    location,
                    referenceKey,
                    agent.getName(),
                    billRow.billNumber(),
                    billRow.commissionAmount(),
                    LocalDate.now(),
                    billRow.commissionNotes());
            expenseId = expense.getId();
            expenseCreated = !alreadyPosted;
        }

        String table = billTable(billType);
        int updated = jdbcTemplate.update(
                "UPDATE " + table + " SET agent_commission_status = ? WHERE id = ? AND agent_id IS NOT NULL AND location = ?",
                normalized, billId, location);
        if (updated == 0) {
            throw new RuntimeException("Bill commission record not found for this agent");
        }

        AgentCommissionStatusUpdateResponseDTO response = new AgentCommissionStatusUpdateResponseDTO();
        response.setBillType(normalizeBillTypeForResponse(billType));
        response.setBillId(billId);
        response.setStatus(normalized);
        response.setExpenseCreated(expenseCreated);
        response.setExpenseId(expenseId);
        return response;
    }

    @Transactional
    public BillAgentAssignmentResponseDTO assignAgentToBill(AssignBillAgentCommissionDTO dto, String location) {
        if (Boolean.TRUE.equals(dto.getClearAgent())) {
            Long billId = resolveBillId(dto, location);
            return clearAgentFromBill(dto.getBillType(), billId, location);
        }
        if (dto.getAgentId() == null) {
            throw new IllegalArgumentException("agentId is required unless clearAgent is true");
        }
        Long billId = resolveBillId(dto, location);
        SalesAgent agent = requireAgentForLocation(dto.getAgentId(), location);
        String type = dto.getAgentCommissionType() != null && !dto.getAgentCommissionType().isBlank()
                ? AgentCommissionType.normalize(dto.getAgentCommissionType())
                : agent.getDefaultCommissionType();
        BigDecimal value = dto.getAgentCommissionValue() != null
                ? BigDecimal.valueOf(dto.getAgentCommissionValue()).setScale(2, RoundingMode.HALF_UP)
                : agent.getDefaultCommissionValue();
        String notes = trimOrNull(dto.getAgentCommissionNotes());

        if (isGstBillType(dto.getBillType())) {
            BillGST bill = billGSTRepository.findById(billId)
                    .orElseThrow(() -> new RuntimeException("Bill not found"));
            assertBillLocation(bill.getLocation(), bill.getCustomer() != null ? bill.getCustomer().getLocation() : null, location);
            assertBillActiveGst(bill);
            assertCommissionEditable(bill.getAgentCommissionStatus());
            BigDecimal amount = SalesAgentService.calculateCommissionAmount(bill.getTotalAmount(), type, value);
            bill.setAgentId(agent.getId());
            bill.setAgentCommissionType(type);
            bill.setAgentCommissionValue(value);
            bill.setAgentCommissionAmount(amount);
            bill.setAgentCommissionStatus(AgentCommissionStatus.PENDING);
            bill.setAgentCommissionNotes(notes);
            billGSTRepository.save(bill);
            return toAssignmentResponse(bill.getId(), "GST", bill.getBillNumber(), agent, bill.getAgentCommissionType(),
                    bill.getAgentCommissionValue(), bill.getAgentCommissionAmount(), bill.getAgentCommissionStatus(),
                    bill.getAgentCommissionNotes(), false);
        }

        BillNonGST bill = billNonGSTRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
        assertBillLocation(bill.getLocation(), bill.getCustomer() != null ? bill.getCustomer().getLocation() : null, location);
        assertBillActiveNonGst(bill);
        assertCommissionEditable(bill.getAgentCommissionStatus());
        BigDecimal amount = SalesAgentService.calculateCommissionAmount(bill.getTotalAmount(), type, value);
        bill.setAgentId(agent.getId());
        bill.setAgentCommissionType(type);
        bill.setAgentCommissionValue(value);
        bill.setAgentCommissionAmount(amount);
        bill.setAgentCommissionStatus(AgentCommissionStatus.PENDING);
        bill.setAgentCommissionNotes(notes);
        billNonGSTRepository.save(bill);
        return toAssignmentResponse(bill.getId(), "NON_GST", bill.getBillNumber(), agent, bill.getAgentCommissionType(),
                bill.getAgentCommissionValue(), bill.getAgentCommissionAmount(), bill.getAgentCommissionStatus(),
                bill.getAgentCommissionNotes(), false);
    }

    @Transactional
    public BillAgentAssignmentResponseDTO clearAgentFromBill(String billType, Long billId, String location) {
        if (isGstBillType(billType)) {
            BillGST bill = billGSTRepository.findById(billId)
                    .orElseThrow(() -> new RuntimeException("Bill not found"));
            assertBillLocation(bill.getLocation(), bill.getCustomer() != null ? bill.getCustomer().getLocation() : null, location);
            assertCommissionEditable(bill.getAgentCommissionStatus());
            if (bill.getAgentId() == null) {
                return toAssignmentResponse(bill.getId(), "GST", bill.getBillNumber(), null, null, null, null, null, null, true);
            }
            bill.setAgentId(null);
            bill.setAgentCommissionType(null);
            bill.setAgentCommissionValue(null);
            bill.setAgentCommissionAmount(null);
            bill.setAgentCommissionStatus(null);
            bill.setAgentCommissionNotes(null);
            billGSTRepository.save(bill);
            return toAssignmentResponse(bill.getId(), "GST", bill.getBillNumber(), null, null, null, null, null, null, true);
        }

        BillNonGST bill = billNonGSTRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
        assertBillLocation(bill.getLocation(), bill.getCustomer() != null ? bill.getCustomer().getLocation() : null, location);
        assertCommissionEditable(bill.getAgentCommissionStatus());
        if (bill.getAgentId() == null) {
            return toAssignmentResponse(bill.getId(), "NON_GST", bill.getBillNumber(), null, null, null, null, null, null, true);
        }
        bill.setAgentId(null);
        bill.setAgentCommissionType(null);
        bill.setAgentCommissionValue(null);
        bill.setAgentCommissionAmount(null);
        bill.setAgentCommissionStatus(null);
        bill.setAgentCommissionNotes(null);
        billNonGSTRepository.save(bill);
        return toAssignmentResponse(bill.getId(), "NON_GST", bill.getBillNumber(), null, null, null, null, null, null, true);
    }

    private Long resolveBillId(AssignBillAgentCommissionDTO dto, String location) {
        if (dto.getBillId() != null) {
            return dto.getBillId();
        }
        String billNumber = trimOrNull(dto.getBillNumber());
        if (billNumber == null) {
            throw new IllegalArgumentException("billId or billNumber is required");
        }
        if (isGstBillType(dto.getBillType())) {
            return billGSTRepository.findByBillNumberAndBillLocation(billNumber, location)
                    .map(BillGST::getId)
                    .orElseThrow(() -> new RuntimeException("GST bill not found: " + billNumber));
        }
        return billNonGSTRepository.findByBillNumberAndBillLocation(billNumber, location)
                .map(BillNonGST::getId)
                .orElseThrow(() -> new RuntimeException("Non-GST bill not found: " + billNumber));
    }

    private static boolean isGstBillType(String billType) {
        return "GST".equals(normalizeBillTypeKey(billType));
    }

    private static void assertBillLocation(String billLocation, String customerLocation, String requestLocation) {
        String effective = billLocation != null && !billLocation.isBlank()
                ? billLocation.trim()
                : (customerLocation != null ? customerLocation.trim() : "");
        if (!effective.equals(requestLocation != null ? requestLocation.trim() : "")) {
            throw new RuntimeException("Bill not found");
        }
    }

    private static void assertBillActiveGst(BillGST bill) {
        if (Boolean.TRUE.equals(bill.getIsDeleted())) {
            throw new IllegalArgumentException("Cannot assign agent to a deleted bill");
        }
        if (BillGST.PaymentStatus.CANCELLED.equals(bill.getPaymentStatus())
                || "CANCELLED".equalsIgnoreCase(String.valueOf(bill.getBillStatus()))) {
            throw new IllegalArgumentException("Cannot assign agent to a cancelled bill");
        }
    }

    private static void assertBillActiveNonGst(BillNonGST bill) {
        if (Boolean.TRUE.equals(bill.getIsDeleted())) {
            throw new IllegalArgumentException("Cannot assign agent to a deleted bill");
        }
        if (BillNonGST.PaymentStatus.CANCELLED.equals(bill.getPaymentStatus())
                || "CANCELLED".equalsIgnoreCase(String.valueOf(bill.getBillStatus()))) {
            throw new IllegalArgumentException("Cannot assign agent to a cancelled bill");
        }
    }

    private static void assertCommissionEditable(String commissionStatus) {
        if (commissionStatus == null || commissionStatus.isBlank()) {
            return;
        }
        String s = commissionStatus.trim().toUpperCase(Locale.ROOT);
        if (AgentCommissionStatus.PAID.equals(s)) {
            throw new IllegalArgumentException(
                    "Commission is already paid — remove the expense manually before changing agent assignment");
        }
        if (AgentCommissionStatus.CANCELLED.equals(s)) {
            throw new IllegalArgumentException("Cannot assign agent on a cancelled commission");
        }
    }

    private static BillAgentAssignmentResponseDTO toAssignmentResponse(
            Long billId,
            String billType,
            String billNumber,
            SalesAgent agent,
            String commissionType,
            BigDecimal commissionValue,
            BigDecimal commissionAmount,
            String commissionStatus,
            String commissionNotes,
            boolean cleared) {
        BillAgentAssignmentResponseDTO dto = new BillAgentAssignmentResponseDTO();
        dto.setBillId(billId);
        dto.setBillType(billType);
        dto.setBillNumber(billNumber);
        dto.setAgentId(agent != null ? agent.getId() : null);
        dto.setAgentName(agent != null ? agent.getName() : null);
        dto.setAgentCommissionType(commissionType);
        dto.setAgentCommissionValue(commissionValue != null ? commissionValue.doubleValue() : null);
        dto.setAgentCommissionAmount(commissionAmount != null ? commissionAmount.doubleValue() : null);
        dto.setAgentCommissionStatus(commissionStatus);
        dto.setAgentCommissionNotes(commissionNotes);
        dto.setCleared(cleared);
        return dto;
    }

    public static String commissionExpenseReferenceKey(String billType, Long billId) {
        return "AGENT_COMMISSION:" + normalizeBillTypeKey(billType) + ":" + billId;
    }

    private static String normalizeBillTypeKey(String billType) {
        String t = billType != null ? billType.trim().toUpperCase(Locale.ROOT).replace("-", "_") : "";
        if ("NON_GST".equals(t) || "NONGST".equals(t)) {
            return "NON_GST";
        }
        return "GST";
    }

    private static String normalizeBillTypeForResponse(String billType) {
        return "NON_GST".equals(normalizeBillTypeKey(billType)) ? "NON_GST" : "GST";
    }

    private CommissionBillRow loadCommissionBill(String billType, Long billId, String location) {
        String table = billTable(billType);
        String sql = """
                SELECT agent_id, bill_number, agent_commission_amount, agent_commission_status, agent_commission_notes
                FROM %s
                WHERE id = ? AND location = ? AND agent_id IS NOT NULL AND is_deleted = 0
                """.formatted(table);
        List<CommissionBillRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new CommissionBillRow(
                rs.getLong("agent_id"),
                rs.getString("bill_number"),
                rs.getBigDecimal("agent_commission_amount"),
                rs.getString("agent_commission_status"),
                rs.getString("agent_commission_notes")), billId, location);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record CommissionBillRow(
            Long agentId,
            String billNumber,
            BigDecimal commissionAmount,
            String commissionStatus,
            String commissionNotes) {}

    public SalesAgent requireAgentForLocation(Long agentId, String location) {
        return salesAgentRepository.findByIdAndLocation(agentId, location)
                .orElseThrow(() -> new RuntimeException("Agent not found for your location"));
    }

    public static BigDecimal calculateCommissionAmount(BigDecimal billTotal, String type, BigDecimal value) {
        if (billTotal == null || value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        String normalized = AgentCommissionType.normalize(type);
        BigDecimal amount;
        if (AgentCommissionType.FIXED.equals(normalized)) {
            amount = value;
        } else {
            amount = billTotal.multiply(value)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public static void cancelCommissionOnBill(Object billEntity) {
        if (billEntity instanceof com.katariastoneworld.apis.entity.BillGST gst) {
            if (gst.getAgentId() != null) {
                gst.setAgentCommissionStatus(AgentCommissionStatus.CANCELLED);
            }
        } else if (billEntity instanceof com.katariastoneworld.apis.entity.BillNonGST nonGst) {
            if (nonGst.getAgentId() != null) {
                nonGst.setAgentCommissionStatus(AgentCommissionStatus.CANCELLED);
            }
        }
    }

    private List<AgentCommissionHistoryDTO> queryGstHistory(Long agentId, String location) {
        String sql = """
                SELECT b.id, b.bill_number, b.bill_date, b.total_amount, b.agent_commission_type,
                       b.agent_commission_value, b.agent_commission_amount, b.agent_commission_status,
                       b.agent_commission_notes, b.payment_status, b.bill_status,
                       c.customer_name, c.phone
                FROM bills_gst b
                JOIN customers c ON c.id = b.customer_id
                WHERE b.agent_id = ? AND b.location = ? AND b.is_deleted = 0
                ORDER BY b.bill_date DESC, b.id DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapHistoryRow(rs, "GST"), agentId, location);
    }

    private List<AgentCommissionHistoryDTO> queryNonGstHistory(Long agentId, String location) {
        String sql = """
                SELECT b.id, b.bill_number, b.bill_date, b.total_amount, b.agent_commission_type,
                       b.agent_commission_value, b.agent_commission_amount, b.agent_commission_status,
                       b.agent_commission_notes, b.payment_status, b.bill_status,
                       c.customer_name, c.phone
                FROM bills_non_gst b
                JOIN customers c ON c.id = b.customer_id
                WHERE b.agent_id = ? AND b.location = ? AND b.is_deleted = 0
                ORDER BY b.bill_date DESC, b.id DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapHistoryRow(rs, "NON_GST"), agentId, location);
    }

    private AgentCommissionHistoryDTO mapHistoryRow(java.sql.ResultSet rs, String billType) throws java.sql.SQLException {
        AgentCommissionHistoryDTO dto = new AgentCommissionHistoryDTO();
        dto.setBillId(rs.getLong("id"));
        dto.setBillType(billType);
        dto.setBillNumber(rs.getString("bill_number"));
        java.sql.Date d = rs.getDate("bill_date");
        dto.setBillDate(d != null ? d.toLocalDate() : null);
        dto.setBillTotalAmount(rs.getBigDecimal("total_amount") != null
                ? rs.getBigDecimal("total_amount").doubleValue() : 0.0);
        dto.setCommissionType(rs.getString("agent_commission_type"));
        dto.setCommissionValue(rs.getBigDecimal("agent_commission_value") != null
                ? rs.getBigDecimal("agent_commission_value").doubleValue() : null);
        dto.setCommissionAmount(rs.getBigDecimal("agent_commission_amount") != null
                ? rs.getBigDecimal("agent_commission_amount").doubleValue() : null);
        dto.setCommissionStatus(rs.getString("agent_commission_status"));
        dto.setCommissionNotes(rs.getString("agent_commission_notes"));
        dto.setBillPaymentStatus(rs.getString("payment_status"));
        dto.setBillLifecycleStatus(rs.getString("bill_status"));
        dto.setCustomerName(rs.getString("customer_name"));
        dto.setCustomerMobileNumber(rs.getString("phone"));
        return dto;
    }

    private Map<Long, AgentStats> loadStatsForLocation(String location) {
        String sql = """
                SELECT agent_id,
                       COUNT(*) AS deals,
                       COALESCE(SUM(CASE WHEN agent_commission_status = 'PENDING'
                           THEN agent_commission_amount ELSE 0 END), 0) AS pending_amt,
                       COALESCE(SUM(CASE WHEN agent_commission_status = 'PAID'
                           THEN agent_commission_amount ELSE 0 END), 0) AS paid_amt
                FROM (
                    SELECT agent_id, agent_commission_status, agent_commission_amount
                    FROM bills_gst WHERE agent_id IS NOT NULL AND location = ? AND is_deleted = 0
                    UNION ALL
                    SELECT agent_id, agent_commission_status, agent_commission_amount
                    FROM bills_non_gst WHERE agent_id IS NOT NULL AND location = ? AND is_deleted = 0
                ) combined
                GROUP BY agent_id
                """;
        List<AgentStats> list = jdbcTemplate.query(sql, (rs, rowNum) -> new AgentStats(
                rs.getLong("agent_id"),
                rs.getLong("deals"),
                rs.getBigDecimal("pending_amt"),
                rs.getBigDecimal("paid_amt")), location, location);
        return list.stream().collect(Collectors.toMap(AgentStats::agentId, s -> s));
    }

    private static String billTable(String billType) {
        if (billType == null) {
            throw new IllegalArgumentException("billType is required");
        }
        String t = billType.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        if ("GST".equals(t)) {
            return "bills_gst";
        }
        if ("NON_GST".equals(t) || "NONGST".equals(t)) {
            return "bills_non_gst";
        }
        throw new IllegalArgumentException("Invalid billType: " + billType);
    }

    private void applyRequest(SalesAgent agent, SalesAgentRequestDTO dto) {
        agent.setName(dto.getName().trim());
        agent.setPhone(normalizePhone(dto.getPhone()));
        if (dto.getEmail() != null) {
            agent.setEmail(trimOrNull(dto.getEmail()));
        }
        if (dto.getDefaultCommissionType() != null && !dto.getDefaultCommissionType().isBlank()) {
            agent.setDefaultCommissionType(AgentCommissionType.normalize(dto.getDefaultCommissionType()));
        }
        if (dto.getDefaultCommissionValue() != null) {
            agent.setDefaultCommissionValue(BigDecimal.valueOf(dto.getDefaultCommissionValue())
                    .setScale(2, RoundingMode.HALF_UP));
        }
        agent.setNotes(trimOrNull(dto.getNotes()));
        if (dto.getActive() != null) {
            agent.setActive(dto.getActive());
        }
    }

    private SalesAgentResponseDTO toResponse(SalesAgent agent, AgentStats stats) {
        SalesAgentResponseDTO dto = new SalesAgentResponseDTO();
        dto.setId(agent.getId());
        dto.setName(agent.getName());
        dto.setPhone(agent.getPhone());
        dto.setEmail(agent.getEmail());
        dto.setDefaultCommissionType(agent.getDefaultCommissionType());
        dto.setDefaultCommissionValue(agent.getDefaultCommissionValue().doubleValue());
        dto.setNotes(agent.getNotes());
        dto.setLocation(agent.getLocation());
        dto.setActive(agent.getActive());
        dto.setCreatedAt(agent.getCreatedAt());
        dto.setUpdatedAt(agent.getUpdatedAt());
        if (stats != null) {
            dto.setTotalDeals(stats.deals());
            dto.setTotalCommissionPending(stats.pending().doubleValue());
            dto.setTotalCommissionPaid(stats.paid().doubleValue());
        } else {
            dto.setTotalDeals(0L);
            dto.setTotalCommissionPending(0.0);
            dto.setTotalCommissionPaid(0.0);
        }
        return dto;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    /** Store digits only, max 10. */
    private static String normalizePhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() > 10) {
            throw new IllegalArgumentException("Phone must be at most 10 digits");
        }
        return digits;
    }

    private record AgentStats(long agentId, long deals, BigDecimal pending, BigDecimal paid) {}
}
