package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.BillItemDTO;
import com.katariastoneworld.apis.dto.BillLineQuantitiesPatchRequestDTO;
import com.katariastoneworld.apis.dto.BillLineQuantityPatchLineDTO;
import com.katariastoneworld.apis.dto.BillPaymentRequestDTO;
import com.katariastoneworld.apis.dto.BillPaymentResponseDTO;
import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.dto.BillReturnSummaryDTO;
import com.katariastoneworld.apis.dto.BillStockReturnLineRequestDTO;
import com.katariastoneworld.apis.dto.BillStockReturnRequestDTO;
import com.katariastoneworld.apis.dto.BillStockReturnResponseDTO;
import com.katariastoneworld.apis.dto.BillStockReturnHistoryDTO;
import com.katariastoneworld.apis.dto.BillSupplementarySummaryDTO;
import com.katariastoneworld.apis.dto.BillReturnRefundMode;
import com.katariastoneworld.apis.dto.BillCancellationLogDTO;
import com.katariastoneworld.apis.dto.BillEventResponseDTO;
import com.katariastoneworld.apis.constants.BillLifecycleStatus;
import com.katariastoneworld.apis.constants.MoneyLedgerCategories;
import com.katariastoneworld.apis.entity.BillEvent;
import com.katariastoneworld.apis.entity.BillGST;
import com.katariastoneworld.apis.entity.BillEventType;
import com.katariastoneworld.apis.entity.BillCancellationLog;
import com.katariastoneworld.apis.entity.BillInventoryReturn;
import com.katariastoneworld.apis.entity.BillInventoryReturnLine;
import com.katariastoneworld.apis.entity.BillItemGST;
import com.katariastoneworld.apis.entity.BillItemNonGST;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillNonGST;
import com.katariastoneworld.apis.entity.BillPayment;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.BillVersion;
import com.katariastoneworld.apis.entity.Customer;
import com.katariastoneworld.apis.entity.MoneyCategory;
import com.katariastoneworld.apis.entity.MoneyDirection;
import com.katariastoneworld.apis.entity.MoneyPaymentMode;
import com.katariastoneworld.apis.entity.MoneyReferenceType;
import com.katariastoneworld.apis.entity.MoneyTransaction;
import com.katariastoneworld.apis.entity.MoneyTxnStatus;
import com.katariastoneworld.apis.entity.Product;
import com.katariastoneworld.apis.repository.BillCancellationLogRepository;
import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillInventoryReturnLineRepository;
import com.katariastoneworld.apis.repository.BillInventoryReturnRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
import com.katariastoneworld.apis.repository.BillVersionRepository;
import com.katariastoneworld.apis.repository.MoneyTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.temporal.ChronoUnit;

/**
 * Bill lifecycle, payments, and (when enabled) inventory coupling for GST/Non-GST bills.
 *
 * <p><b>Inventory rules — bill create, full replace (edit), line-quantity patch, partial stock return, cancel</b>:
 * <ul>
 *   <li><b>Ledger:</b> every stock movement is recorded in {@code inventory_transactions}
 *       ({@link com.katariastoneworld.apis.entity.InventoryTransaction}).</li>
 *   <li><b>On-hand cache:</b> {@link com.katariastoneworld.apis.entity.Product#getQuantity()} persists to
 *       {@code products.total_sqft_stock} and must stay in sync with the sum of signed ledger rows — never update it
 *       &quot;silently&quot; from bill line DTOs or this service alone.</li>
 *   <li><b>Allowed path:</b> bill-related stock effects go only through {@link ProductService} (e.g.
 *       {@link ProductService#deductStock}, {@link ProductService#recordBillStockReturn}, {@link ProductService#deductStockByName},
 *       {@link ProductService#recordBillStockReturnByName}), which always append a ledger row before saving the product.</li>
 *   <li><b>Bill line quantities</b> ({@code bill_items_*}) are commercial line state; changing them does not move stock unless
 *       the corresponding {@code ProductService} / reservation flow runs for that edit.</li>
 *   <li><b>Payment totals on edit:</b> full financial + advance recompute uses {@link #replaceBill} (PUT). Line-only edits use
 *       {@link #patchBillLineQuantities}, which recomputes subtotal/charges/tax/total from lines then
 *       {@link #resyncAdvanceApplicationAfterLineQuantityEditNonGst} / {@link #resyncAdvanceApplicationAfterLineQuantityEditGst}
 *       to re-apply wallet against the new total (cash rows unchanged).</li>
 *   <li><b>Non-GST replace — excess paid vs new total:</b> when prior cash + advance on the bill exceeded the new total,
 *       default behaviour credits the excess to customer wallet ({@code BILL_EDIT_ADJUSTMENT}) and appends a matching
 *       {@code transactions} row; set {@code excessPaymentHandling} to {@code NONE} on the replace request to skip
 *       automatic store credit (cash refund handled offline).</li>
 *   <li><b>Central revision entry:</b> HTTP/API orchestration may go through {@link BillRevisionService} so edits stay
 *       aligned with append-only audit rules.</li>
 * </ul>
 */
@Service
@Transactional
public class BillService {
    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    private static final BigDecimal PAY_ROUND_EPS = new BigDecimal("0.01");

    /** {@code inventory_transactions.source_action} — extra sale from bill line quantity increase (SALE / OUT). */
    private static final String INV_SRC_BILL_LINE_QTY_INCREASE_SALE = "BILL_LINE_QTY_INCREASE_SALE";

    /** {@code inventory_transactions.source_action} — stock back from billed qty decrease (RETURN / IN). */
    private static final String INV_SRC_BILL_LINE_QTY_DECREASE_RETURN = "BILL_LINE_QTY_DECREASE_RETURN";

    /** {@code inventory_transactions.source_action} — full line removed from bill (RETURN / IN). */
    private static final String INV_SRC_BILL_LINE_REMOVED_RETURN = "BILL_LINE_REMOVED_RETURN";

    /** {@code inventory_transactions.source_action} — new bill line appended via patch (SALE / OUT). */
    private static final String INV_SRC_BILL_LINE_NEW_ITEM_SALE = "BILL_LINE_NEW_ITEM_SALE";

    /** Partial stock entry from bill return UI — ledger RETURN / IN; original bill lines untouched. */
    private static final String INV_SRC_BILL_STOCK_RETURN = "BILL_STOCK_RETURN";

    /**
     * Legacy DBs often use VARCHAR(50) for {@code payment_method}. Summaries must stay within this;
     * full split details remain in {@code bill_payments}.
     */
    private static final int BILL_PAYMENT_METHOD_MAX_LEN = 50;
    private static final long MAX_BACKDATE_DAYS = 7;

    /**
     * GST bills are B2B invoices that are NOT real sales for this business
     * (we just pass goods through). When this flag is {@code false} we skip:
     *   • stock validation / reservation / deduction / consumption for new
     *     and re-applied GST bill lines, and
     *   • customer wallet/advance application on GST bills.
     * We intentionally still run the matching {@code revert} / {@code reverse}
     * operations on edit/cancel paths so any legacy GST bills that had stock
     * deducted or advance consumed before this flag was introduced clean
     * themselves up the next time they are touched.
     */
    private static final boolean GST_BILLS_AFFECT_STOCK_AND_WALLET = false;

    private record ResolvedLine(BigDecimal amount, BillPaymentMode mode, LocalDate paymentDate) {
    }

    @Autowired
    private BillGSTRepository billGSTRepository;

    @Autowired
    private BillNonGSTRepository billNonGSTRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private BillNumberGeneratorService billNumberGeneratorService;

    @Autowired
    private ProductService productService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BillPaymentRepository billPaymentRepository;

    @Autowired
    private CustomerAdvanceService customerAdvanceService;

    @Autowired
    private InventoryReservationService inventoryReservationService;

    @Autowired
    private BillInventoryReturnRepository billInventoryReturnRepository;

    @Autowired
    private BillInventoryReturnLineRepository billInventoryReturnLineRepository;

    @Autowired
    private DailyClosingSnapshotService dailyClosingSnapshotService;

    @Autowired
    private BillCancellationLogRepository billCancellationLogRepository;

    @Autowired
    private MoneyTransactionRepository moneyTransactionRepository;

    @Autowired
    private FinancialLedgerService financialLedgerService;

    @Autowired
    private BillEventService billEventService;

    @Autowired
    private BillVersionRepository billVersionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public BillResponseDTO createBill(BillRequestDTO billRequestDTO, String location, Long createdByUserId, String userRole) {
        // Get or create customer with details
        log.info("bill_create_start location={} createdBy={} items={}",
                location, createdByUserId, billRequestDTO.getItems() != null ? billRequestDTO.getItems().size() : 0);
        Customer customer = customerService.getOrCreateCustomer(
                billRequestDTO.getCustomerMobileNumber(),
                billRequestDTO.getCustomerName(),
                billRequestDTO.getAddress(),
                billRequestDTO.getGstin(),
                billRequestDTO.getCustomerEmail(),
                location,
                createdByUserId);

        // Calculate total sqft from items (quantity represents sqft)
        // Calculate total quantity (sum of all item quantities regardless of unit)
        // Note: This is called "totalSqft" for backward compatibility, but it's
        // actually
        // a sum of quantities which could be sqft, pieces, packets, etc.
        BigDecimal totalSqft = billRequestDTO.getItems().stream()
                .map(item -> BigDecimal.valueOf(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate subtotal from items (pricePerUnit * quantity)
        BigDecimal subtotal = billRequestDTO.getItems().stream()
                .map(item -> BigDecimal.valueOf(item.getPricePerUnit())
                        .multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Service charge (default to 0)
        BigDecimal serviceCharge = BigDecimal.ZERO;

        // Labour charge (default to 0 if null)
        BigDecimal labourCharge = billRequestDTO.getLabourCharge() != null
                ? BigDecimal.valueOf(billRequestDTO.getLabourCharge()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Transportation charge (default to 0 if null)
        BigDecimal transportationCharge = billRequestDTO.getTransportationCharge() != null
                ? BigDecimal.valueOf(billRequestDTO.getTransportationCharge()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Other expenses (default to 0 if null)
        BigDecimal otherExpenses = billRequestDTO.getOtherExpenses() != null
                ? BigDecimal.valueOf(billRequestDTO.getOtherExpenses()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        log.info("[Bill] Other expenses from request: raw={}, applied={}", billRequestDTO.getOtherExpenses(), otherExpenses);

        // Discount amount
        BigDecimal discountAmount = BigDecimal.valueOf(billRequestDTO.getDiscountAmount())
                .setScale(2, RoundingMode.HALF_UP);

        // Determine if GST or NonGST based on tax percentage
        BigDecimal taxPercentage = BigDecimal.valueOf(billRequestDTO.getTaxPercentage());
        boolean isGST = taxPercentage.compareTo(BigDecimal.ZERO) > 0;

        // Generate bill number per location (separate series per branch).
        String billNumber;
        if (isGST) {
            billNumber = billNumberGeneratorService.generateGSTBillNumber(location, createdByUserId);
        } else {
            billNumber = billNumberGeneratorService.generateNonGSTBillNumber(location, createdByUserId);
        }

        LocalDate effectiveBillDate = resolveRequestedBillDate(billRequestDTO, userRole);
        boolean backdated = effectiveBillDate.isBefore(LocalDate.now());
        if (isGST) {
            return createGSTBill(billRequestDTO, customer, location, billNumber, totalSqft, subtotal,
                    taxPercentage, serviceCharge, labourCharge, transportationCharge, otherExpenses, discountAmount,
                    createdByUserId, effectiveBillDate, backdated, userRole);
        } else {
            return createNonGSTBill(billRequestDTO, customer, location, billNumber, totalSqft, subtotal,
                    serviceCharge, labourCharge, transportationCharge, otherExpenses, discountAmount,
                    createdByUserId, effectiveBillDate, backdated, userRole);
        }
    }

    public BillResponseDTO createSupplementaryBill(Long parentBillId, String parentBillType, BillRequestDTO billRequestDTO,
            String location, Long createdByUserId, String userRole) {
        if (parentBillId == null || parentBillType == null || parentBillType.isBlank()) {
            throw new IllegalArgumentException("Parent bill id and type are required for supplementary bill");
        }
        BillKind parentKind = parseBillKind(parentBillType);
        Customer parentCustomer;
        if (parentKind == BillKind.GST) {
            BillGST parent = billGSTRepository.findByIdWithItemsAndProducts(parentBillId)
                    .orElseThrow(() -> new RuntimeException("Parent GST bill not found with id: " + parentBillId));
            String parentLoc = resolveBillLocation(parent, parent.getCustomer());
            if (!Objects.equals(parentLoc, location)) {
                throw new RuntimeException("Parent GST bill not found with id: " + parentBillId);
            }
            assertParentGstBillAllowsSupplementary(parent);
            parentCustomer = parent.getCustomer();
        } else {
            BillNonGST parent = billNonGSTRepository.findByIdWithItemsAndProducts(parentBillId)
                    .orElseThrow(() -> new RuntimeException("Parent Non-GST bill not found with id: " + parentBillId));
            String parentLoc = resolveBillLocation(parent, parent.getCustomer());
            if (!Objects.equals(parentLoc, location)) {
                throw new RuntimeException("Parent Non-GST bill not found with id: " + parentBillId);
            }
            assertParentNonGstBillAllowsSupplementary(parent);
            parentCustomer = parent.getCustomer();
        }
        billRequestDTO.setCustomerMobileNumber(parentCustomer.getPhone());
        if (billRequestDTO.getCustomerName() == null || billRequestDTO.getCustomerName().isBlank()) {
            billRequestDTO.setCustomerName(parentCustomer.getCustomerName());
        }
        billRequestDTO.setParentBillId(parentBillId);
        billRequestDTO.setParentBillType(parentKind.name());
        return createBill(billRequestDTO, location, createdByUserId, userRole);
    }

    /**
     * Replace an existing bill atomically (same bill id/number) with new header, items, and payment split.
     * Stock, ledger, wallet-advance application, and daily closing snapshots are all recalculated from
     * canonical transaction rows (single source of truth).
     * <p>
     * <b>Old bills:</b> omit {@link BillRequestDTO#getBillDate()} to keep the stored bill date (avoids backdate
     * validation that blocked edits older than {@link #MAX_BACKDATE_DAYS}). If {@code payments} / {@code paymentMethod}
     * are omitted, prior non-advance payment rows are reused and scaled down when the new payable total is lower.
     * Bills with inventory returns still cannot be replaced here — use the quantity patch / return flows first.
     */
    public BillResponseDTO replaceBill(Long billId, String billType, BillRequestDTO billRequestDTO,
            String location, Long actorUserId, String userRole) {
        BillKind kind = parseBillKind(billType);
        if (kind == BillKind.GST) {
            return replaceGstBill(billId, billRequestDTO, location, actorUserId, userRole);
        }
        return replaceNonGstBill(billId, billRequestDTO, location, actorUserId, userRole);
    }

    private BillResponseDTO replaceGstBill(Long billId, BillRequestDTO req, String location, Long actorUserId, String userRole) {
        BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("GST Bill not found with id: " + billId);
        }
        assertGstBillMutableForEdits(bill);
        Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.GST, billId);
        if (!returnedByLine.isEmpty()) {
            throw new IllegalArgumentException("Cannot fully edit bill with stock returns. Use quantity patch/stock return flow.");
        }

        LocalDate oldBillDate = bill.getBillDate();
        List<ResolvedLine> oldPayLines = resolvedNonAdvanceLinesForSnapshot(BillKind.GST, billId);
        Customer oldCustomer = bill.getCustomer();
        Long previousVersionId = latestBillVersionId(bill.getId());
        int nextVersionNo = nextBillVersionNo(bill.getId());
        String replaceNote = trimToNull(req.getNotes());
        String opLinkedGroupId = newLinkedGroupId();
        Long currentBillVersionRowId = beginBillVersion(bill.getId(), nextVersionNo, "UPDATE", previousVersionId,
                Map.of("kind", "GST", "linkedGroupId", opLinkedGroupId), truncateBillVersionEditReason(replaceNote),
                actorUserId).getId();

        // Undo existing effects first.
        inventoryReservationService.releaseForBill(billId, BillKind.GST);
        revertStockForGstBill(bill, billLocation, opLinkedGroupId, currentBillVersionRowId);
        deactivateBillPayments(BillKind.GST, billId, billLocation, actorUserId, currentBillVersionRowId, opLinkedGroupId);
        customerAdvanceService.reverseAdvanceUsageForBill(BillKind.GST, billId, currentBillVersionRowId, opLinkedGroupId);

        Customer customer = customerService.getOrCreateCustomer(
                req.getCustomerMobileNumber(),
                req.getCustomerName(),
                req.getAddress(),
                req.getGstin(),
                req.getCustomerEmail(),
                billLocation,
                actorUserId);
        bill.setCustomer(customer);

        BigDecimal totalSqft = req.getItems().stream()
                .map(item -> BigDecimal.valueOf(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = req.getItems().stream()
                .map(item -> BigDecimal.valueOf(item.getPricePerUnit()).multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxRate = BigDecimal.valueOf(req.getTaxPercentage()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = subtotal.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal labourCharge = req.getLabourCharge() != null
                ? BigDecimal.valueOf(req.getLabourCharge()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal transportationCharge = req.getTransportationCharge() != null
                ? BigDecimal.valueOf(req.getTransportationCharge()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal otherExpenses = req.getOtherExpenses() != null
                ? BigDecimal.valueOf(req.getOtherExpenses()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = BigDecimal.valueOf(req.getDiscountAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(taxAmount)
                .add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .add(labourCharge)
                .add(transportationCharge)
                .add(otherExpenses)
                .subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);

        LocalDate effectiveBillDate = resolveRequestedBillDateForReplace(req, userRole, oldBillDate);
        boolean backdated = effectiveBillDate.isBefore(LocalDate.now());
        bill.setBillDate(effectiveBillDate);
        bill.setBackdated(backdated);
        bill.setBackdateReason(trimToNull(req.getBackdateReason()));
        bill.setBackdateApprovedBy(trimToNull(req.getBackdateApprovedBy()));
        bill.setSupplementaryBill(req.getParentBillId() != null);
        bill.setParentBillId(req.getParentBillId());
        bill.setParentBillType(normalizeParentBillType(req.getParentBillType()));
        bill.setSupplementaryReason(trimToNull(req.getSupplementaryReason()));
        bill.setTotalSqft(totalSqft);
        bill.setSubtotal(subtotal);
        bill.setTaxRate(taxRate);
        bill.setTaxAmount(taxAmount);
        bill.setServiceCharge(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        bill.setLabourCharge(labourCharge);
        bill.setTransportationCharge(transportationCharge);
        bill.setOtherExpenses(otherExpenses);
        bill.setDiscountAmount(discountAmount);
        bill.setTotalAmount(totalAmount);
        bill.setVehicleNo(trimToNull(req.getVehicleNo()));
        bill.setDeliveryAddress(trimToNull(req.getDeliveryAddress()));
        bill.setUpdatedByUserId(actorUserId);
        BigDecimal headerPaidBeforeReplace = bill.getPaidAmount() != null
                ? bill.getPaidAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String headerPaymentMethodBeforeReplace = bill.getPaymentMethod();
        bill.setPaymentStatus(BillGST.PaymentStatus.DUE);
        bill.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        bill.setPaymentMethod("-");

        Map<Long, BigDecimal> productQuantitiesById = new HashMap<>();
        Map<String, BigDecimal> productQuantitiesByName = new HashMap<>();
        for (BillItemDTO itemDTO : req.getItems()) {
            BigDecimal quantity = BigDecimal.valueOf(itemDTO.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            if (itemDTO.getProductId() != null) {
                productQuantitiesById.merge(itemDTO.getProductId(), quantity, BigDecimal::add);
            } else if (itemDTO.getItemName() != null) {
                productQuantitiesByName.merge(itemDTO.getItemName(), quantity, BigDecimal::add);
            }
        }
        if (GST_BILLS_AFFECT_STOCK_AND_WALLET) {
            for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
                productService.validateStockAvailability(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
                productService.validateStockAvailabilityByName(entry.getKey(), entry.getValue());
            }
        }

        bill.getItems().clear();
        String firstInventoryHsn = null;
        for (BillItemDTO itemDTO : req.getItems()) {
            BillItemGST item = new BillItemGST();
            item.setProductName(itemDTO.getItemName());
            item.setProductType(itemDTO.getCategory() != null && !itemDTO.getCategory().isBlank() ? itemDTO.getCategory().trim() : "General");
            item.setPricePerUnit(BigDecimal.valueOf(itemDTO.getPricePerUnit()).setScale(2, RoundingMode.HALF_UP));
            item.setQuantity(BigDecimal.valueOf(itemDTO.getQuantity()).setScale(2, RoundingMode.HALF_UP));
            item.setItemTotalPrice(BigDecimal.valueOf(itemDTO.getPricePerUnit())
                    .multiply(BigDecimal.valueOf(itemDTO.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));
            if (itemDTO.getProductImageUrl() != null) {
                item.setProductImageUrl(itemDTO.getProductImageUrl());
            }
            Product product = null;
            if (itemDTO.getProductId() != null) {
                product = productService.getProductEntityById(itemDTO.getProductId());
                item.setProduct(product);
            } else if (itemDTO.getItemName() != null) {
                try {
                    product = productService.getProductEntityByName(itemDTO.getItemName());
                    item.setProduct(product);
                } catch (RuntimeException ignored) {
                }
            }
            if (product != null && product.getHsnNumber() != null && !product.getHsnNumber().isBlank()) {
                String invHsn = product.getHsnNumber().trim();
                item.setHsnNumber(invHsn);
                if (firstInventoryHsn == null) {
                    firstInventoryHsn = invHsn;
                }
            }
            if (product != null && product.getUnit() != null && !product.getUnit().trim().isEmpty()) {
                item.setUnit(product.getUnit());
            } else if (itemDTO.getUnit() != null && !itemDTO.getUnit().trim().isEmpty()) {
                item.setUnit(itemDTO.getUnit());
            } else {
                item.setUnit("sqft");
            }
            bill.addItem(item);
        }
        String billHsnFromRequest = trimToNull(req.getHsnCode());
        bill.setHsnCode(billHsnFromRequest != null ? billHsnFromRequest : firstInventoryHsn);
        billGSTRepository.save(bill);

        // GST bills are B2B pass-through invoices: by default we do NOT
        // apply customer wallet/advance on them, and we do NOT reserve or
        // deduct inventory. We still call the matching revert/reverse
        // operations earlier in this method so any legacy GST bills that had
        // wallet or stock effects clean themselves up on the next edit.
        BigDecimal advanceApplied;
        if (GST_BILLS_AFFECT_STOCK_AND_WALLET) {
            advanceApplied = customerAdvanceService.applyAdvanceFifo(
                    customer.getId(), BillKind.GST, bill.getId(), totalAmount, currentBillVersionRowId, opLinkedGroupId);
            assertAppliedAdvanceNotExceedingBillTotal(advanceApplied, totalAmount);
            persistWalletAdvancePayment(BillKind.GST, bill.getId(), advanceApplied, bill.getBillDate(), actorUserId,
                    currentBillVersionRowId, opLinkedGroupId);
        } else {
            advanceApplied = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal netForPayments = totalAmount.subtract(advanceApplied).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        List<ResolvedLine> payLines = resolvePaymentLinesForReplace(
                req,
                netForPayments,
                bill.getBillDate(),
                userRole,
                oldPayLines,
                headerPaidBeforeReplace,
                headerPaymentMethodBeforeReplace);
        BigDecimal totalPaid = sumResolvedLines(payLines);
        bill.setPaidAmount(totalPaid);
        bill.setPaymentMethod(buildPaymentMethodSummary(payLines, totalAmount, totalPaid, advanceApplied));
        billGSTRepository.save(bill);

        inventoryReservationService.releaseForBill(bill.getId(), BillKind.GST);
        if (GST_BILLS_AFFECT_STOCK_AND_WALLET) {
            inventoryReservationService.reserveForBill(
                    bill.getId(), BillKind.GST, productQuantitiesById, productQuantitiesByName, billLocation);
            try {
                String note = "Stock deducted via edited GST bill " + bill.getBillNumber() + " (id=" + bill.getId() + ")";
                LocalDate stockDate = bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now();
                for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
                    productService.deductStock(entry.getKey(), entry.getValue(), bill.getId(), note, BillKind.GST, stockDate,
                            currentBillVersionRowId, null, opLinkedGroupId, "BILL_REPLACE_APPLY");
                }
                for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
                    productService.deductStockByName(entry.getKey(), entry.getValue(), bill.getId(), note, BillKind.GST, stockDate,
                            currentBillVersionRowId, null, opLinkedGroupId, "BILL_REPLACE_APPLY");
                }
                inventoryReservationService.consumeForBill(bill.getId(), BillKind.GST);
            } catch (RuntimeException ex) {
                inventoryReservationService.releaseForBill(bill.getId(), BillKind.GST);
                throw ex;
            }
        }

        persistResolvedPayments(BillKind.GST, bill.getId(), payLines, billLocation, currentBillVersionRowId, opLinkedGroupId);
        refreshBillFinancialsGST(bill, billLocation);
        Set<LocalDate> extraImpacted = new HashSet<>();
        if (oldBillDate != null) {
            extraImpacted.add(oldBillDate);
        }
        if (bill.getBillDate() != null) {
            extraImpacted.add(bill.getBillDate());
        }
        List<ResolvedLine> allImpactedLines = new ArrayList<>(oldPayLines);
        allImpactedLines.addAll(payLines);
        recomputeSnapshotsForBillMutation(billLocation, bill.getBillDate(), allImpactedLines, extraImpacted);

        if (replaceNote != null) {
            appendBillLifecycleNoteGst(bill, replaceNote, "UPDATE");
        }

        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, bill.getId()).setScale(2, RoundingMode.HALF_UP);
        BillResponseDTO response = convertGSTToResponseDTO(
                bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, bill.getId()),
                adv);
        boolean isSimpleBill = (req.getSimpleBill() != null && req.getSimpleBill())
                || (req.getTaxPercentage() != null && req.getTaxPercentage() == 0);
        response.setSimpleBill(isSimpleBill);
        bill.setCurrentVersionNo(nextVersionNo);
        bill.setLatestVersion(true);
        bill.setBillStatus("UPDATED");
        billGSTRepository.save(bill);
        finalizeBillVersionSnapshot(currentBillVersionRowId, response);
        recordBillEvent(BillKind.GST, bill.getId(), BillEventType.BILL_EDITED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                java.util.Map.of("versionNo", nextVersionNo, "totalAmount", totalAmount.toPlainString()));
        recordBillEvent(BillKind.GST, bill.getId(), BillEventType.ADVANCE_RECALCULATED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                java.util.Map.of("advanceApplied", advanceApplied.toPlainString()));
        recordBillEvent(BillKind.GST, bill.getId(), BillEventType.PAYMENT_ADJUSTED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                java.util.Map.of("totalPaid", totalPaid.toPlainString()));
        if (oldCustomer != null && oldCustomer.getEmail() != null && !oldCustomer.getEmail().isBlank()) {
            emailService.sendBillEmail(response, oldCustomer.getEmail());
        } else if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            emailService.sendBillEmail(response, customer.getEmail());
        }
        return response;
    }

    private BillResponseDTO replaceNonGstBill(Long billId, BillRequestDTO req, String location, Long actorUserId, String userRole) {
        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        assertNonGstBillMutableForEdits(bill);
        Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.NON_GST, billId);
        if (!returnedByLine.isEmpty()) {
            throw new IllegalArgumentException("Cannot fully edit bill with stock returns. Use quantity patch/stock return flow.");
        }

        LocalDate oldBillDate = bill.getBillDate();
        List<ResolvedLine> oldPayLines = resolvedNonAdvanceLinesForSnapshot(BillKind.NON_GST, billId);
        BigDecimal priorAdvanceBeforeReplace = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal headerPaidSnapshot = bill.getPaidAmount() != null
                ? bill.getPaidAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal priorCashBeforeReplace = !oldPayLines.isEmpty()
                ? sumResolvedLines(oldPayLines).setScale(2, RoundingMode.HALF_UP)
                : headerPaidSnapshot;
        BigDecimal priorCoveredBeforeReplace = priorAdvanceBeforeReplace.add(priorCashBeforeReplace)
                .setScale(2, RoundingMode.HALF_UP);
        Long anchorOrigBillPaymentId = null;
        for (BillPayment bp : billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId)) {
            if (Boolean.TRUE.equals(bp.getIsDeleted()) || isAdvancePayment(bp)) {
                continue;
            }
            anchorOrigBillPaymentId = bp.getId();
            break;
        }
        Long anchorOrigMoneyTxnId = null;
        if (anchorOrigBillPaymentId != null) {
            anchorOrigMoneyTxnId = moneyTransactionRepository
                    .findFirstByBillPaymentIdAndIsDeletedFalseOrderByIdAsc(anchorOrigBillPaymentId)
                    .map(MoneyTransaction::getId)
                    .orElse(null);
        }
        Customer oldCustomer = bill.getCustomer();
        Long previousVersionId = latestBillVersionId(bill.getId());
        int nextVersionNo = nextBillVersionNo(bill.getId());
        String replaceNote = trimToNull(req.getNotes());
        String opLinkedGroupId = newLinkedGroupId();
        Long currentBillVersionRowId = beginBillVersion(bill.getId(), nextVersionNo, "UPDATE", previousVersionId,
                Map.of("kind", "NON_GST", "linkedGroupId", opLinkedGroupId), truncateBillVersionEditReason(replaceNote),
                actorUserId).getId();

        inventoryReservationService.releaseForBill(billId, BillKind.NON_GST);
        revertStockForNonGstBill(bill, billLocation, opLinkedGroupId, currentBillVersionRowId);
        deactivateBillPayments(BillKind.NON_GST, billId, billLocation, actorUserId, currentBillVersionRowId, opLinkedGroupId);
        customerAdvanceService.reverseAdvanceUsageForBill(BillKind.NON_GST, billId, currentBillVersionRowId, opLinkedGroupId);

        Customer customer = customerService.getOrCreateCustomer(
                req.getCustomerMobileNumber(),
                req.getCustomerName(),
                req.getAddress(),
                req.getGstin(),
                req.getCustomerEmail(),
                billLocation,
                actorUserId);
        bill.setCustomer(customer);

        BigDecimal totalSqft = req.getItems().stream()
                .map(item -> BigDecimal.valueOf(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = req.getItems().stream()
                .map(item -> BigDecimal.valueOf(item.getPricePerUnit()).multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal labourCharge = req.getLabourCharge() != null
                ? BigDecimal.valueOf(req.getLabourCharge()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal transportationCharge = req.getTransportationCharge() != null
                ? BigDecimal.valueOf(req.getTransportationCharge()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal otherExpenses = req.getOtherExpenses() != null
                ? BigDecimal.valueOf(req.getOtherExpenses()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = BigDecimal.valueOf(req.getDiscountAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal
                .add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .add(labourCharge)
                .add(transportationCharge)
                .add(otherExpenses)
                .subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);

        LocalDate effectiveBillDate = resolveRequestedBillDateForReplace(req, userRole, oldBillDate);
        boolean backdated = effectiveBillDate.isBefore(LocalDate.now());
        bill.setBillDate(effectiveBillDate);
        bill.setBackdated(backdated);
        bill.setBackdateReason(trimToNull(req.getBackdateReason()));
        bill.setBackdateApprovedBy(trimToNull(req.getBackdateApprovedBy()));
        bill.setSupplementaryBill(req.getParentBillId() != null);
        bill.setParentBillId(req.getParentBillId());
        bill.setParentBillType(normalizeParentBillType(req.getParentBillType()));
        bill.setSupplementaryReason(trimToNull(req.getSupplementaryReason()));
        bill.setTotalSqft(totalSqft);
        bill.setSubtotal(subtotal);
        bill.setServiceCharge(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        bill.setLabourCharge(labourCharge);
        bill.setTransportationCharge(transportationCharge);
        bill.setOtherExpenses(otherExpenses);
        bill.setDiscountAmount(discountAmount);
        bill.setTotalAmount(totalAmount);
        bill.setUpdatedByUserId(actorUserId);
        BigDecimal headerPaidBeforeReplace = bill.getPaidAmount() != null
                ? bill.getPaidAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String headerPaymentMethodBeforeReplace = bill.getPaymentMethod();
        bill.setPaymentStatus(BillNonGST.PaymentStatus.DUE);
        bill.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        bill.setPaymentMethod("-");

        Map<Long, BigDecimal> productQuantitiesById = new HashMap<>();
        Map<String, BigDecimal> productQuantitiesByName = new HashMap<>();
        for (BillItemDTO itemDTO : req.getItems()) {
            BigDecimal quantity = BigDecimal.valueOf(itemDTO.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            if (itemDTO.getProductId() != null) {
                productQuantitiesById.merge(itemDTO.getProductId(), quantity, BigDecimal::add);
            } else if (itemDTO.getItemName() != null) {
                productQuantitiesByName.merge(itemDTO.getItemName(), quantity, BigDecimal::add);
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
            productService.validateStockAvailability(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
            productService.validateStockAvailabilityByName(entry.getKey(), entry.getValue());
        }

        bill.getItems().clear();
        for (BillItemDTO itemDTO : req.getItems()) {
            BillItemNonGST item = new BillItemNonGST();
            item.setProductName(itemDTO.getItemName());
            item.setProductType(itemDTO.getCategory() != null && !itemDTO.getCategory().isBlank() ? itemDTO.getCategory().trim() : "General");
            item.setPricePerUnit(BigDecimal.valueOf(itemDTO.getPricePerUnit()).setScale(2, RoundingMode.HALF_UP));
            item.setQuantity(BigDecimal.valueOf(itemDTO.getQuantity()).setScale(2, RoundingMode.HALF_UP));
            item.setItemTotalPrice(BigDecimal.valueOf(itemDTO.getPricePerUnit())
                    .multiply(BigDecimal.valueOf(itemDTO.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));
            if (itemDTO.getProductImageUrl() != null) {
                item.setProductImageUrl(itemDTO.getProductImageUrl());
            }
            Product product = null;
            if (itemDTO.getProductId() != null) {
                product = productService.getProductEntityById(itemDTO.getProductId());
                item.setProduct(product);
            } else if (itemDTO.getItemName() != null) {
                try {
                    product = productService.getProductEntityByName(itemDTO.getItemName());
                    item.setProduct(product);
                } catch (RuntimeException ignored) {
                }
            }
            if (product != null && product.getUnit() != null && !product.getUnit().trim().isEmpty()) {
                item.setUnit(product.getUnit());
            } else if (itemDTO.getUnit() != null && !itemDTO.getUnit().trim().isEmpty()) {
                item.setUnit(itemDTO.getUnit());
            } else {
                item.setUnit("sqft");
            }
            bill.addItem(item);
        }
        billNonGSTRepository.save(bill);

        BigDecimal advanceApplied = customerAdvanceService.applyAdvanceFifo(
                customer.getId(), BillKind.NON_GST, bill.getId(), totalAmount, currentBillVersionRowId, opLinkedGroupId);
        assertAppliedAdvanceNotExceedingBillTotal(advanceApplied, totalAmount);
        persistWalletAdvancePayment(BillKind.NON_GST, bill.getId(), advanceApplied, bill.getBillDate(), actorUserId,
                currentBillVersionRowId, opLinkedGroupId);
        BigDecimal netForPayments = totalAmount.subtract(advanceApplied).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        List<ResolvedLine> payLines = resolvePaymentLinesForReplace(
                req,
                netForPayments,
                bill.getBillDate(),
                userRole,
                oldPayLines,
                headerPaidBeforeReplace,
                headerPaymentMethodBeforeReplace);
        BigDecimal totalPaid = sumResolvedLines(payLines);
        bill.setPaidAmount(totalPaid);
        bill.setPaymentMethod(buildPaymentMethodSummary(payLines, totalAmount, totalPaid, advanceApplied));
        billNonGSTRepository.save(bill);

        inventoryReservationService.releaseForBill(bill.getId(), BillKind.NON_GST);
        inventoryReservationService.reserveForBill(
                bill.getId(), BillKind.NON_GST, productQuantitiesById, productQuantitiesByName, billLocation);
        try {
            String note = "Stock deducted via edited Non-GST bill " + bill.getBillNumber() + " (id=" + bill.getId() + ")";
            LocalDate stockDate = bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now();
            for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
                productService.deductStock(entry.getKey(), entry.getValue(), bill.getId(), note, BillKind.NON_GST, stockDate,
                        currentBillVersionRowId, null, opLinkedGroupId, "BILL_REPLACE_APPLY");
            }
            for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
                productService.deductStockByName(entry.getKey(), entry.getValue(), bill.getId(), note, BillKind.NON_GST, stockDate,
                        currentBillVersionRowId, null, opLinkedGroupId, "BILL_REPLACE_APPLY");
            }
            inventoryReservationService.consumeForBill(bill.getId(), BillKind.NON_GST);
        } catch (RuntimeException ex) {
            inventoryReservationService.releaseForBill(bill.getId(), BillKind.NON_GST);
            throw ex;
        }

        persistResolvedPayments(BillKind.NON_GST, bill.getId(), payLines, billLocation, currentBillVersionRowId,
                opLinkedGroupId);
        BigDecimal storeCredited = maybeApplyBillEditExcessStoreCredit(
                req,
                customer,
                billLocation,
                bill,
                totalAmount,
                priorCoveredBeforeReplace,
                currentBillVersionRowId,
                opLinkedGroupId,
                anchorOrigMoneyTxnId);
        refreshBillFinancialsNonGST(bill, billLocation);
        Set<LocalDate> extraImpacted = new HashSet<>();
        if (oldBillDate != null) {
            extraImpacted.add(oldBillDate);
        }
        if (bill.getBillDate() != null) {
            extraImpacted.add(bill.getBillDate());
        }
        List<ResolvedLine> allImpactedLines = new ArrayList<>(oldPayLines);
        allImpactedLines.addAll(payLines);
        recomputeSnapshotsForBillMutation(billLocation, bill.getBillDate(), allImpactedLines, extraImpacted);

        if (replaceNote != null) {
            appendBillLifecycleNoteNonGst(bill, replaceNote, "UPDATE");
        }

        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, bill.getId()).setScale(2, RoundingMode.HALF_UP);
        BillResponseDTO response = convertNonGSTToResponseDTO(
                bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, bill.getId()),
                adv);
        boolean isSimpleBill = (req.getSimpleBill() != null && req.getSimpleBill())
                || (req.getTaxPercentage() != null && req.getTaxPercentage() == 0);
        response.setSimpleBill(isSimpleBill);
        bill.setCurrentVersionNo(nextVersionNo);
        bill.setLatestVersion(true);
        bill.setBillStatus(BillLifecycleStatus.COMPLETED);
        billNonGSTRepository.save(bill);
        finalizeBillVersionSnapshot(currentBillVersionRowId, response);
        recordBillEvent(BillKind.NON_GST, bill.getId(), BillEventType.BILL_EDITED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                java.util.Map.of("versionNo", nextVersionNo, "totalAmount", totalAmount.toPlainString()));
        recordBillEvent(BillKind.NON_GST, bill.getId(), BillEventType.ADVANCE_RECALCULATED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                java.util.Map.of("advanceApplied", advanceApplied.toPlainString()));
        recordBillEvent(BillKind.NON_GST, bill.getId(), BillEventType.PAYMENT_ADJUSTED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                java.util.Map.of(
                        "priorCash", priorCashBeforeReplace.toPlainString(),
                        "newCashPaid", totalPaid.toPlainString()));
        if (storeCredited.compareTo(PAY_ROUND_EPS) > 0) {
            recordBillEvent(BillKind.NON_GST, bill.getId(), BillEventType.STORE_CREDIT_CREATED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                    java.util.Map.of("amount", storeCredited.toPlainString()));
        }
        if (oldCustomer != null && oldCustomer.getEmail() != null && !oldCustomer.getEmail().isBlank()) {
            emailService.sendBillEmail(response, oldCustomer.getEmail());
        } else if (customer.getEmail() != null && !customer.getEmail().isBlank()) {
            emailService.sendBillEmail(response, customer.getEmail());
        }
        return response;
    }

    private BillResponseDTO createGSTBill(BillRequestDTO billRequestDTO, Customer customer, String location,
            String billNumber, BigDecimal totalSqft, BigDecimal subtotal,
            BigDecimal taxRate, BigDecimal serviceCharge, BigDecimal labourCharge,
            BigDecimal transportationCharge, BigDecimal otherExpenses, BigDecimal discountAmount, Long createdByUserId,
            LocalDate effectiveBillDate, boolean backdated, String userRole) {
        // Calculate tax amount
        BigDecimal taxAmount = subtotal.multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Always calculate total amount to include all charges: subtotal + tax +
        // serviceCharge + labourCharge + transportationCharge + otherExpenses - discountAmount
        BigDecimal totalAmount = subtotal.add(taxAmount).add(serviceCharge)
                .add(labourCharge).add(transportationCharge).add(otherExpenses).subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);

        // Create GST Bill entity
        BillGST bill = new BillGST();
        bill.setBillNumber(billNumber);
        bill.setCustomer(customer);
        bill.setLocation(location != null ? location.trim() : null);
        bill.setBillDate(effectiveBillDate);
        bill.setBackdated(backdated);
        bill.setBackdateReason(trimToNull(billRequestDTO.getBackdateReason()));
        bill.setBackdateApprovedBy(trimToNull(billRequestDTO.getBackdateApprovedBy()));
        bill.setSupplementaryBill(billRequestDTO.getParentBillId() != null);
        bill.setParentBillId(billRequestDTO.getParentBillId());
        bill.setParentBillType(normalizeParentBillType(billRequestDTO.getParentBillType()));
        bill.setSupplementaryReason(trimToNull(billRequestDTO.getSupplementaryReason()));
        bill.setTotalSqft(totalSqft.setScale(2, RoundingMode.HALF_UP));
        bill.setSubtotal(subtotal);
        bill.setTaxRate(taxRate);
        bill.setTaxAmount(taxAmount);
        bill.setServiceCharge(serviceCharge);
        bill.setLabourCharge(labourCharge);
        bill.setTransportationCharge(transportationCharge);
        bill.setOtherExpenses(otherExpenses);
        bill.setDiscountAmount(discountAmount);
        bill.setTotalAmount(totalAmount);
        bill.setPaymentStatus(BillGST.PaymentStatus.DUE);
        bill.setPaymentMethod("-");
        // bill-level hsnCode: set after items (from request or first product in inventory)
        bill.setVehicleNo(trimToNull(billRequestDTO.getVehicleNo()));
        bill.setDeliveryAddress(trimToNull(billRequestDTO.getDeliveryAddress()));
        bill.setNotes(trimToNull(billRequestDTO.getNotes()));
        bill.setCreatedByUserId(createdByUserId);
        log.info("[Bill GST] Bill {} created with otherExpenses={}, totalAmount={}", billNumber, otherExpenses, totalAmount);

        // Group items by productId and sum quantities for efficient stock validation
        Map<Long, BigDecimal> productQuantitiesById = new HashMap<>();
        Map<String, BigDecimal> productQuantitiesByName = new HashMap<>();

        for (BillItemDTO itemDTO : billRequestDTO.getItems()) {
            BigDecimal quantity = BigDecimal.valueOf(itemDTO.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP);

            if (itemDTO.getProductId() != null) {
                // Group by productId if provided
                productQuantitiesById.merge(itemDTO.getProductId(), quantity, BigDecimal::add);
            } else if (itemDTO.getItemName() != null) {
                // Group by product name if productId is not provided
                productQuantitiesByName.merge(itemDTO.getItemName(), quantity, BigDecimal::add);
            }
        }

        // Validate stock availability only when GST bills actually affect stock.
        if (GST_BILLS_AFFECT_STOCK_AND_WALLET) {
            for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
                productService.validateStockAvailability(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
                productService.validateStockAvailabilityByName(entry.getKey(), entry.getValue());
            }
        }

        // Add items to bill (per-line HSN from inventory product when linked)
        String firstInventoryHsn = null;
        for (BillItemDTO itemDTO : billRequestDTO.getItems()) {
            BillItemGST item = new BillItemGST();
            item.setProductName(itemDTO.getItemName());
            item.setProductType(itemDTO.getCategory() != null && !itemDTO.getCategory().isBlank() ? itemDTO.getCategory().trim() : "General");
            item.setPricePerUnit(BigDecimal.valueOf(itemDTO.getPricePerUnit())
                    .setScale(2, RoundingMode.HALF_UP));
            item.setQuantity(BigDecimal.valueOf(itemDTO.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP));
            item.setItemTotalPrice(BigDecimal.valueOf(itemDTO.getPricePerUnit())
                    .multiply(BigDecimal.valueOf(itemDTO.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));

            if (itemDTO.getProductImageUrl() != null) {
                item.setProductImageUrl(itemDTO.getProductImageUrl());
            }

            // Link product if productId is provided, otherwise try to find by name
            Product product = null;
            if (itemDTO.getProductId() != null) {
                product = productService.getProductEntityById(itemDTO.getProductId());
                item.setProduct(product);
            } else if (itemDTO.getItemName() != null) {
                try {
                    product = productService.getProductEntityByName(itemDTO.getItemName());
                    item.setProduct(product);
                } catch (RuntimeException e) {
                    // Product not found by name - this is OK, item will be saved without product
                    // link
                    // Stock deduction will still happen if product exists
                }
            }

            if (product != null && product.getHsnNumber() != null && !product.getHsnNumber().isBlank()) {
                String invHsn = product.getHsnNumber().trim();
                item.setHsnNumber(invHsn);
                if (firstInventoryHsn == null) {
                    firstInventoryHsn = invHsn;
                }
            }

            // Set unit from product if available, otherwise use from DTO or default to
            // "sqft"
            if (product != null && product.getUnit() != null && !product.getUnit().trim().isEmpty()) {
                item.setUnit(product.getUnit());
            } else if (itemDTO.getUnit() != null && !itemDTO.getUnit().trim().isEmpty()) {
                item.setUnit(itemDTO.getUnit());
            } else {
                item.setUnit("sqft"); // Default for backward compatibility
            }

            bill.addItem(item);
        }

        String billHsnFromRequest = trimToNull(billRequestDTO.getHsnCode());
        bill.setHsnCode(billHsnFromRequest != null ? billHsnFromRequest : firstInventoryHsn);

        // Save GST bill (payment fields finalized after advance + payment lines)
        BillGST savedBill = billGSTRepository.save(bill);
        int billVersionNo = 1;
        String opLinkedGroupId = newLinkedGroupId();
        savedBill.setOriginalBillId(savedBill.getId());
        savedBill.setCurrentVersionNo(billVersionNo);
        savedBill.setLatestVersion(true);
        savedBill.setBillStatus(Boolean.TRUE.equals(savedBill.getSupplementaryBill()) ? "SUPPLEMENTARY" : "ACTIVE");

        Long currentBillVersionRowId = beginBillVersion(savedBill.getId(), billVersionNo, "CREATE", null,
                Map.of("kind", "GST", "linkedGroupId", opLinkedGroupId),
                truncateBillVersionEditReason(trimToNull(billRequestDTO.getNotes())), createdByUserId).getId();

        // GST bills are B2B pass-through invoices in this business: by default
        // we do NOT consume customer wallet/advance on GST bills.
        BigDecimal advanceApplied;
        if (GST_BILLS_AFFECT_STOCK_AND_WALLET) {
            advanceApplied = customerAdvanceService.applyAdvanceFifo(
                    customer.getId(), BillKind.GST, savedBill.getId(), totalAmount, currentBillVersionRowId, opLinkedGroupId);
            persistWalletAdvancePayment(BillKind.GST, savedBill.getId(), advanceApplied, savedBill.getBillDate(), createdByUserId,
                    currentBillVersionRowId, opLinkedGroupId);
        } else {
            advanceApplied = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal netForPayments = totalAmount.subtract(advanceApplied).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        List<ResolvedLine> payLines = resolvePaymentLines(billRequestDTO, netForPayments, savedBill.getBillDate(), userRole);
        BigDecimal totalPaidCash = sumResolvedLines(payLines);
        BigDecimal covered = advanceApplied.add(totalPaidCash);
        savedBill.setPaidAmount(totalPaidCash.setScale(2, RoundingMode.HALF_UP));
        savedBill.setPaymentStatus(toGstPaymentStatus(totalAmount, covered));
        savedBill.setPaymentMethod(buildPaymentMethodSummary(payLines, totalAmount, totalPaidCash, advanceApplied));
        billGSTRepository.save(savedBill);

        // GST bills are B2B pass-through invoices: by default we do NOT
        // reserve/deduct/consume inventory for them either.
        if (GST_BILLS_AFFECT_STOCK_AND_WALLET) {
            inventoryReservationService.reserveForBill(
                    savedBill.getId(), BillKind.GST, productQuantitiesById, productQuantitiesByName, location);
            try {
                String gstBillNote = "Stock deducted via GST bill " + savedBill.getBillNumber() + " (id=" + savedBill.getId() + ")";
                LocalDate stockDate = savedBill.getBillDate() != null ? savedBill.getBillDate() : LocalDate.now();
                for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
                    productService.deductStock(entry.getKey(), entry.getValue(), savedBill.getId(), gstBillNote, BillKind.GST, stockDate,
                            currentBillVersionRowId, null, opLinkedGroupId, "BILL_CREATE_APPLY");
                }
                for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
                    productService.deductStockByName(entry.getKey(), entry.getValue(), savedBill.getId(), gstBillNote, BillKind.GST, stockDate,
                            currentBillVersionRowId, null, opLinkedGroupId, "BILL_CREATE_APPLY");
                }
                inventoryReservationService.consumeForBill(savedBill.getId(), BillKind.GST);
            } catch (RuntimeException ex) {
                inventoryReservationService.releaseForBill(savedBill.getId(), BillKind.GST);
                throw ex;
            }
        }

        persistResolvedPayments(BillKind.GST, savedBill.getId(), payLines, resolveBillLocation(savedBill, customer),
                currentBillVersionRowId, opLinkedGroupId);
        recomputeSnapshotsForBillMutation(resolveBillLocation(savedBill, customer), savedBill.getBillDate(), payLines);

        // Convert to response DTO
        BillResponseDTO responseDTO = convertGSTToResponseDTO(savedBill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, savedBill.getId()),
                advanceApplied);
        // Automatically set simpleBill to true if taxPercentage is 0, or use the flag
        // from request
        boolean isSimpleBill = (billRequestDTO.getSimpleBill() != null && billRequestDTO.getSimpleBill())
                || (billRequestDTO.getTaxPercentage() != null && billRequestDTO.getTaxPercentage() == 0);
        responseDTO.setSimpleBill(isSimpleBill);

        // Send email to customer asynchronously (non-blocking)
        emailService.sendBillEmail(responseDTO, customer.getEmail());
        finalizeBillVersionSnapshot(currentBillVersionRowId, responseDTO);
        log.info("bill_create_success kind=GST billId={} billNo={} total={} paid={} advance={}",
                savedBill.getId(), savedBill.getBillNumber(), totalAmount, totalPaidCash, advanceApplied);

        return responseDTO;
    }

    private BillResponseDTO createNonGSTBill(BillRequestDTO billRequestDTO, Customer customer, String location,
            String billNumber, BigDecimal totalSqft, BigDecimal subtotal,
            BigDecimal serviceCharge, BigDecimal labourCharge,
            BigDecimal transportationCharge, BigDecimal otherExpenses, BigDecimal discountAmount, Long createdByUserId,
            LocalDate effectiveBillDate, boolean backdated, String userRole) {
        // Always calculate total amount (no tax) to include all charges: subtotal +
        // serviceCharge + labourCharge + transportationCharge + otherExpenses - discountAmount
        BigDecimal totalAmount = subtotal.add(serviceCharge).add(labourCharge)
                .add(transportationCharge).add(otherExpenses).subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);

        // Create NonGST Bill entity
        BillNonGST bill = new BillNonGST();
        bill.setBillNumber(billNumber);
        bill.setCustomer(customer);
        bill.setLocation(location != null ? location.trim() : null);
        bill.setBillDate(effectiveBillDate);
        bill.setBackdated(backdated);
        bill.setBackdateReason(trimToNull(billRequestDTO.getBackdateReason()));
        bill.setBackdateApprovedBy(trimToNull(billRequestDTO.getBackdateApprovedBy()));
        bill.setSupplementaryBill(billRequestDTO.getParentBillId() != null);
        bill.setParentBillId(billRequestDTO.getParentBillId());
        bill.setParentBillType(normalizeParentBillType(billRequestDTO.getParentBillType()));
        bill.setSupplementaryReason(trimToNull(billRequestDTO.getSupplementaryReason()));
        bill.setTotalSqft(totalSqft.setScale(2, RoundingMode.HALF_UP));
        bill.setSubtotal(subtotal);
        bill.setServiceCharge(serviceCharge);
        bill.setLabourCharge(labourCharge);
        bill.setTransportationCharge(transportationCharge);
        bill.setOtherExpenses(otherExpenses);
        bill.setDiscountAmount(discountAmount);
        bill.setTotalAmount(totalAmount);
        bill.setPaymentStatus(BillNonGST.PaymentStatus.DUE);
        bill.setPaymentMethod("-");
        bill.setCreatedByUserId(createdByUserId);
        bill.setNotes(trimToNull(billRequestDTO.getNotes()));
        log.info("[Bill Non-GST] Bill {} created with otherExpenses={}, totalAmount={}", billNumber, otherExpenses, totalAmount);

        // Group items by productId and sum quantities for efficient stock validation
        Map<Long, BigDecimal> productQuantitiesById = new HashMap<>();
        Map<String, BigDecimal> productQuantitiesByName = new HashMap<>();

        for (BillItemDTO itemDTO : billRequestDTO.getItems()) {
            BigDecimal quantity = BigDecimal.valueOf(itemDTO.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP);

            if (itemDTO.getProductId() != null) {
                // Group by productId if provided
                productQuantitiesById.merge(itemDTO.getProductId(), quantity, BigDecimal::add);
            } else if (itemDTO.getItemName() != null) {
                // Group by product name if productId is not provided
                productQuantitiesByName.merge(itemDTO.getItemName(), quantity, BigDecimal::add);
            }
        }

        // Validate stock availability for all products (by ID)
        for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
            productService.validateStockAvailability(entry.getKey(), entry.getValue());
        }

        // Validate stock availability for all products (by name)
        for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
            productService.validateStockAvailabilityByName(entry.getKey(), entry.getValue());
        }

        // Add items to bill
        for (BillItemDTO itemDTO : billRequestDTO.getItems()) {
            BillItemNonGST item = new BillItemNonGST();
            item.setProductName(itemDTO.getItemName());
            item.setProductType(itemDTO.getCategory() != null && !itemDTO.getCategory().isBlank() ? itemDTO.getCategory().trim() : "General");
            item.setPricePerUnit(BigDecimal.valueOf(itemDTO.getPricePerUnit())
                    .setScale(2, RoundingMode.HALF_UP));
            item.setQuantity(BigDecimal.valueOf(itemDTO.getQuantity())
                    .setScale(2, RoundingMode.HALF_UP));
            item.setItemTotalPrice(BigDecimal.valueOf(itemDTO.getPricePerUnit())
                    .multiply(BigDecimal.valueOf(itemDTO.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP));

            if (itemDTO.getProductImageUrl() != null) {
                item.setProductImageUrl(itemDTO.getProductImageUrl());
            }

            // Link product if productId is provided, otherwise try to find by name
            Product product = null;
            if (itemDTO.getProductId() != null) {
                product = productService.getProductEntityById(itemDTO.getProductId());
                item.setProduct(product);
            } else if (itemDTO.getItemName() != null) {
                try {
                    product = productService.getProductEntityByName(itemDTO.getItemName());
                    item.setProduct(product);
                } catch (RuntimeException e) {
                    // Product not found by name - this is OK, item will be saved without product
                    // link
                    // Stock deduction will still happen if product exists
                }
            }

            // Set unit from product if available, otherwise use from DTO or default to
            // "sqft"
            if (product != null && product.getUnit() != null && !product.getUnit().trim().isEmpty()) {
                item.setUnit(product.getUnit());
            } else if (itemDTO.getUnit() != null && !itemDTO.getUnit().trim().isEmpty()) {
                item.setUnit(itemDTO.getUnit());
            } else {
                item.setUnit("sqft"); // Default for backward compatibility
            }

            bill.addItem(item);
        }

        // Save NonGST bill
        BillNonGST savedBill = billNonGSTRepository.save(bill);
        int billVersionNo = 1;
        String opLinkedGroupId = newLinkedGroupId();
        savedBill.setOriginalBillId(savedBill.getId());
        savedBill.setCurrentVersionNo(billVersionNo);
        savedBill.setLatestVersion(true);
        savedBill.setBillStatus(BillLifecycleStatus.COMPLETED);

        Long currentBillVersionRowId = beginBillVersion(savedBill.getId(), billVersionNo, "CREATE", null,
                Map.of("kind", "NON_GST", "linkedGroupId", opLinkedGroupId),
                truncateBillVersionEditReason(trimToNull(billRequestDTO.getNotes())), createdByUserId).getId();

        BigDecimal advanceApplied = customerAdvanceService.applyAdvanceFifo(
                customer.getId(), BillKind.NON_GST, savedBill.getId(), totalAmount, currentBillVersionRowId, opLinkedGroupId);
        persistWalletAdvancePayment(BillKind.NON_GST, savedBill.getId(), advanceApplied, savedBill.getBillDate(),
                createdByUserId, currentBillVersionRowId, opLinkedGroupId);
        BigDecimal netForPayments = totalAmount.subtract(advanceApplied).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        List<ResolvedLine> payLines = resolvePaymentLines(billRequestDTO, netForPayments, savedBill.getBillDate(), userRole);
        BigDecimal totalPaidCash = sumResolvedLines(payLines);
        BigDecimal covered = advanceApplied.add(totalPaidCash);
        savedBill.setPaidAmount(totalPaidCash.setScale(2, RoundingMode.HALF_UP));
        savedBill.setPaymentStatus(toNonGstPaymentStatus(totalAmount, covered));
        savedBill.setPaymentMethod(buildPaymentMethodSummary(payLines, totalAmount, totalPaidCash, advanceApplied));
        billNonGSTRepository.save(savedBill);

        inventoryReservationService.reserveForBill(
                savedBill.getId(), BillKind.NON_GST, productQuantitiesById, productQuantitiesByName, location);
        try {
            String nonGstBillNote = "Stock deducted via Non-GST bill " + savedBill.getBillNumber() + " (id=" + savedBill.getId() + ")";
            LocalDate stockDate = savedBill.getBillDate() != null ? savedBill.getBillDate() : LocalDate.now();
            for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
                productService.deductStock(entry.getKey(), entry.getValue(), savedBill.getId(), nonGstBillNote, BillKind.NON_GST, stockDate,
                        currentBillVersionRowId, null, opLinkedGroupId, "BILL_CREATE_APPLY");
            }
            for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
                productService.deductStockByName(entry.getKey(), entry.getValue(), savedBill.getId(), nonGstBillNote, BillKind.NON_GST, stockDate,
                        currentBillVersionRowId, null, opLinkedGroupId, "BILL_CREATE_APPLY");
            }
            inventoryReservationService.consumeForBill(savedBill.getId(), BillKind.NON_GST);
        } catch (RuntimeException ex) {
            inventoryReservationService.releaseForBill(savedBill.getId(), BillKind.NON_GST);
            throw ex;
        }

        persistResolvedPayments(BillKind.NON_GST, savedBill.getId(), payLines, resolveBillLocation(savedBill, customer),
                currentBillVersionRowId, opLinkedGroupId);
        recomputeSnapshotsForBillMutation(resolveBillLocation(savedBill, customer), savedBill.getBillDate(), payLines);

        // Convert to response DTO
        BillResponseDTO responseDTO = convertNonGSTToResponseDTO(savedBill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, savedBill.getId()),
                advanceApplied);
        // Automatically set simpleBill to true if taxPercentage is 0, or use the flag
        // from request
        boolean isSimpleBill = (billRequestDTO.getSimpleBill() != null && billRequestDTO.getSimpleBill())
                || (billRequestDTO.getTaxPercentage() != null && billRequestDTO.getTaxPercentage() == 0);
        responseDTO.setSimpleBill(isSimpleBill);

        // Send email to customer asynchronously (non-blocking)
        emailService.sendBillEmail(responseDTO, customer.getEmail());
        finalizeBillVersionSnapshot(currentBillVersionRowId, responseDTO);
        log.info("bill_create_success kind=NON_GST billId={} billNo={} total={} paid={} advance={}",
                savedBill.getId(), savedBill.getBillNumber(), totalAmount, totalPaidCash, advanceApplied);

        if (savedBill.getParentBillId() != null) {
            markNonGstParentAdjustedAfterSupplementary(savedBill.getParentBillId(), savedBill.getParentBillType(), location);
        }

        return responseDTO;
    }

    public BillResponseDTO addPaymentToBill(Long billId, String billType, BillPaymentRequestDTO paymentRequest,
            String location, Long actorUserId) {
        if (paymentRequest == null || paymentRequest.getAmount() == null || paymentRequest.getAmount() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than 0");
        }
        BillPaymentMode mode = parseBillPaymentMode(paymentRequest.getPaymentMode());
        BigDecimal amount = BigDecimal.valueOf(paymentRequest.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than 0");
        }
        if ("GST".equalsIgnoreCase(billType)) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            Customer customer = bill.getCustomer();
            String billLocation = resolveBillLocation(bill, customer);
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            assertGstBillMutableForEdits(bill);
            List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId);
            BigDecimal paid = sumNonAdvancePayments(existing);
            BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal remaining = bill.getTotalAmount().subtract(adv).subtract(paid).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.subtract(remaining).compareTo(PAY_ROUND_EPS) > 0) {
                throw new IllegalArgumentException("Payment amount cannot exceed remaining bill amount");
            }
            String opLinkedGroupId = newLinkedGroupId();
            Long previousVersionId = latestBillVersionId(billId);
            int nextVersionNo = nextBillVersionNo(billId);
            Long currentBillVersionRowId = beginBillVersion(billId, nextVersionNo, "PAYMENT_ADD", previousVersionId,
                    Map.of("kind", "GST", "linkedGroupId", opLinkedGroupId), null, actorUserId).getId();
            BillPayment row = new BillPayment();
            row.setBillKind(BillKind.GST);
            row.setBillId(billId);
            row.setSourceType("BILL_PAYMENT");
            row.setAmount(amount);
            row.setPaymentMode(mode);
            row.setPaymentDate(paymentRequest.getPaymentDate() != null ? paymentRequest.getPaymentDate() : LocalDate.now());
            row.setCreatedBy(actorUserId);
            row.setUpdatedBy(actorUserId);
            row.setBillVersionId(currentBillVersionRowId);
            BillPayment saved = billPaymentRepository.save(row);
            createTransactionFromBillPayment(saved, currentBillVersionRowId, opLinkedGroupId, "BILL_PAYMENT");
            refreshBillFinancialsGST(bill, billLocation);
            BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.GST, billId, null);
            bill.setCurrentVersionNo(nextVersionNo);
            billGSTRepository.save(bill);
            BillResponseDTO response = convertGSTToResponseDTO(bill,
                    billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId), updatedAdv);
            finalizeBillVersionSnapshot(currentBillVersionRowId, response);
            recordBillEvent(BillKind.GST, billId, BillEventType.PAYMENT_ADJUSTED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                    java.util.Map.of("amount", amount.toPlainString(), "mode", mode.name()));
            return response;
        }
        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        Customer customer = bill.getCustomer();
        String billLocation = resolveBillLocation(bill, customer);
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        assertNonGstBillMutableForEdits(bill);
        List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId);
        BigDecimal paid = sumNonAdvancePayments(existing);
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = bill.getTotalAmount().subtract(adv).subtract(paid).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.subtract(remaining).compareTo(PAY_ROUND_EPS) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed remaining bill amount");
        }
        String opLinkedGroupId = newLinkedGroupId();
        Long previousVersionId = latestBillVersionId(billId);
        int nextVersionNo = nextBillVersionNo(billId);
        Long currentBillVersionRowId = beginBillVersion(billId, nextVersionNo, "PAYMENT_ADD", previousVersionId,
                Map.of("kind", "NON_GST", "linkedGroupId", opLinkedGroupId), null, actorUserId).getId();
        BillPayment row = new BillPayment();
        row.setBillKind(BillKind.NON_GST);
        row.setBillId(billId);
        row.setSourceType("BILL_PAYMENT");
        row.setAmount(amount);
        row.setPaymentMode(mode);
        row.setPaymentDate(paymentRequest.getPaymentDate() != null ? paymentRequest.getPaymentDate() : LocalDate.now());
        row.setCreatedBy(actorUserId);
        row.setUpdatedBy(actorUserId);
        row.setBillVersionId(currentBillVersionRowId);
        BillPayment saved = billPaymentRepository.save(row);
        createTransactionFromBillPayment(saved, currentBillVersionRowId, opLinkedGroupId, "BILL_PAYMENT");
        refreshBillFinancialsNonGST(bill, billLocation);
        BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.NON_GST, billId, null);
        bill.setCurrentVersionNo(nextVersionNo);
        billNonGSTRepository.save(bill);
        BillResponseDTO response = convertNonGSTToResponseDTO(bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId), updatedAdv);
        finalizeBillVersionSnapshot(currentBillVersionRowId, response);
        recordBillEvent(BillKind.NON_GST, billId, BillEventType.PAYMENT_ADJUSTED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                java.util.Map.of("amount", amount.toPlainString(), "mode", mode.name()));
        return response;
    }

    public BillResponseDTO updatePaymentOnBill(Long billId, String billType, Long paymentId, BillPaymentRequestDTO paymentRequest,
            String location, Long actorUserId) {
        if (paymentRequest == null || paymentRequest.getAmount() == null || paymentRequest.getAmount() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than 0");
        }
        BillKind kind = "GST".equalsIgnoreCase(billType) ? BillKind.GST : BillKind.NON_GST;
        BillPayment row = billPaymentRepository.findByIdAndBillKindAndBillId(paymentId, kind, billId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));
        if (isAdvancePayment(row)) {
            throw new IllegalArgumentException("Wallet advance row cannot be edited");
        }
        LocalDate oldPaymentDate = row.getPaymentDate();
        BigDecimal oldAmount = row.getAmount() != null ? row.getAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BillPaymentMode newMode = parseBillPaymentMode(paymentRequest.getPaymentMode());
        BigDecimal newAmount = BigDecimal.valueOf(paymentRequest.getAmount()).setScale(2, RoundingMode.HALF_UP);

        if (kind == BillKind.GST) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            assertGstBillMutableForEdits(bill);
            List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId);
            BigDecimal paid = sumNonAdvancePayments(existing);
            BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId).setScale(2, RoundingMode.HALF_UP);
            BigDecimal remainingExcludingThis = bill.getTotalAmount().subtract(adv).subtract(paid).add(oldAmount)
                    .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (newAmount.subtract(remainingExcludingThis).compareTo(PAY_ROUND_EPS) > 0) {
                throw new IllegalArgumentException("Payment amount cannot exceed remaining bill amount");
            }
            row.setAmount(newAmount);
            row.setPaymentMode(newMode);
            row.setPaymentDate(paymentRequest.getPaymentDate() != null ? paymentRequest.getPaymentDate() : row.getPaymentDate());
            row.setUpdatedBy(actorUserId);
            BillPayment saved = billPaymentRepository.save(row);
            refreshBillFinancialsGST(bill, billLocation);
            BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            Set<LocalDate> extra = null;
            if (oldPaymentDate != null && saved.getPaymentDate() != null && !oldPaymentDate.equals(saved.getPaymentDate())) {
                extra = new HashSet<>();
                extra.add(oldPaymentDate);
            }
            recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.GST, billId, extra);
            String payAdjGroup = newLinkedGroupId();
            recordBillEvent(BillKind.GST, billId, BillEventType.PAYMENT_ADJUSTED, null, payAdjGroup, actorUserId,
                    java.util.Map.of(
                            "paymentId", paymentId,
                            "oldAmount", oldAmount.toPlainString(),
                            "newAmount", newAmount.toPlainString(),
                            "mode", newMode.name(),
                            "action", "UPDATE"));
            return convertGSTToResponseDTO(bill, billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId),
                    updatedAdv);
        }

        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        assertNonGstBillMutableForEdits(bill);
        List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId);
        BigDecimal paid = sumNonAdvancePayments(existing);
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingExcludingThis = bill.getTotalAmount().subtract(adv).subtract(paid).add(oldAmount)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (newAmount.subtract(remainingExcludingThis).compareTo(PAY_ROUND_EPS) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed remaining bill amount");
        }
        row.setAmount(newAmount);
        row.setPaymentMode(newMode);
        row.setPaymentDate(paymentRequest.getPaymentDate() != null ? paymentRequest.getPaymentDate() : row.getPaymentDate());
        row.setUpdatedBy(actorUserId);
        BillPayment saved = billPaymentRepository.save(row);
        refreshBillFinancialsNonGST(bill, billLocation);
        BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        Set<LocalDate> extra = null;
        if (oldPaymentDate != null && saved.getPaymentDate() != null && !oldPaymentDate.equals(saved.getPaymentDate())) {
            extra = new HashSet<>();
            extra.add(oldPaymentDate);
        }
        recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.NON_GST, billId, extra);
        String payAdjGroup = newLinkedGroupId();
        recordBillEvent(BillKind.NON_GST, billId, BillEventType.PAYMENT_ADJUSTED, null, payAdjGroup, actorUserId,
                java.util.Map.of(
                        "paymentId", paymentId,
                        "oldAmount", oldAmount.toPlainString(),
                        "newAmount", newAmount.toPlainString(),
                        "mode", newMode.name(),
                        "action", "UPDATE"));
        return convertNonGSTToResponseDTO(bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId), updatedAdv);
    }

    public BillResponseDTO deletePaymentOnBill(Long billId, String billType, Long paymentId, String location, Long actorUserId) {
        BillKind kind = "GST".equalsIgnoreCase(billType) ? BillKind.GST : BillKind.NON_GST;
        BillPayment row = billPaymentRepository.findByIdAndBillKindAndBillId(paymentId, kind, billId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));
        if (isAdvancePayment(row)) {
            throw new IllegalArgumentException("Wallet advance row cannot be deleted");
        }

        if (kind == BillKind.GST) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            assertGstBillMutableForEdits(bill);
            row.setIsDeleted(true);
            row.setUpdatedBy(actorUserId);
            BillPayment saved = billPaymentRepository.save(row);
            String payDelGroup = newLinkedGroupId();
            refundRemovedInHandPayment(BillKind.GST, billId, saved, actorUserId, null, payDelGroup);
            BigDecimal removedAmount = saved.getAmount() != null ? saved.getAmount().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            refreshBillFinancialsGST(bill, billLocation);
            BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            Set<LocalDate> extra = Set.of(LocalDate.now());
            recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.GST, billId, extra);
            recordBillEvent(BillKind.GST, billId, BillEventType.PAYMENT_ADJUSTED, null, payDelGroup, actorUserId,
                    java.util.Map.of(
                            "paymentId", paymentId,
                            "amount", removedAmount.toPlainString(),
                            "action", "DELETE"));
            return convertGSTToResponseDTO(bill, billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId),
                    updatedAdv);
        }

        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        assertNonGstBillMutableForEdits(bill);
        row.setIsDeleted(true);
        row.setUpdatedBy(actorUserId);
        BillPayment saved = billPaymentRepository.save(row);
        String payDelGroupNg = newLinkedGroupId();
        refundRemovedInHandPayment(BillKind.NON_GST, billId, saved, actorUserId, null, payDelGroupNg);
        BigDecimal removedAmountNg = saved.getAmount() != null ? saved.getAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        refreshBillFinancialsNonGST(bill, billLocation);
        BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        Set<LocalDate> extra = Set.of(LocalDate.now());
        recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.NON_GST, billId, extra);
        recordBillEvent(BillKind.NON_GST, billId, BillEventType.PAYMENT_ADJUSTED, null, payDelGroupNg, actorUserId,
                java.util.Map.of(
                        "paymentId", paymentId,
                        "amount", removedAmountNg.toPlainString(),
                        "action", "DELETE"));
        return convertNonGSTToResponseDTO(bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId), updatedAdv);
    }

    /**
     * Record a partial physical stock return against an existing bill. Persists return lines (caps per line)
     * and appends {@code RETURN} / {@code IN} inventory transactions tied to this bill id.
     */
    public BillStockReturnResponseDTO recordPartialStockReturn(Long billId, String billType,
            BillStockReturnRequestDTO request, String location, Long actorUserId) {
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("At least one return line is required");
        }
        Set<Long> seen = new HashSet<>();
        for (BillStockReturnLineRequestDTO ln : request.getLines()) {
            if (ln.getBillItemId() == null || !seen.add(ln.getBillItemId())) {
                throw new IllegalArgumentException("Each billItemId must appear once and be non-null");
            }
        }
        BillReturnRefundMode refundMode = resolveStockReturnRefundMode(request);
        if ("GST".equalsIgnoreCase(billType)) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            assertGstBillMutableForEdits(bill);
            BigDecimal computedReturn = computeGstStockReturnSettlement(bill, request.getLines());
            BigDecimal postedSettlement = resolvePostedReturnSettlement(refundMode, computedReturn, request);
            if (refundMode != BillReturnRefundMode.NO_REFUND && postedSettlement.compareTo(PAY_ROUND_EPS) <= 0) {
                throw new IllegalArgumentException(
                        "Computed return settlement is zero; choose NO_REFUND for stock-only returns.");
            }
            Map<Long, BigDecimal> returnedBefore = loadReturnedByLineId(BillKind.GST, billId);
            BillInventoryReturn header = new BillInventoryReturn();
            header.setBillKind(BillKind.GST);
            header.setBillId(billId);
            header.setLocation(location != null ? location.trim() : null);
            header.setNotes(request.getNotes());
            header.setCreatedByUserId(actorUserId);
            for (BillStockReturnLineRequestDTO ln : request.getLines()) {
                BillItemGST line = findGstLine(bill, ln.getBillItemId());
                BigDecimal sold = line.getQuantity() != null ? line.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal already = returnedBefore.getOrDefault(line.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                BigDecimal q = BigDecimal.valueOf(ln.getQuantity()).setScale(2, RoundingMode.HALF_UP);
                if (q.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Return quantity must be positive");
                }
                if (already.add(q).subtract(sold).compareTo(PAY_ROUND_EPS) > 0) {
                    throw new IllegalArgumentException(
                            "Return exceeds remaining for line item id=" + line.getId() + ": sold=" + sold + ", already returned="
                                    + already + ", this return=" + q);
                }
                BillInventoryReturnLine rl = new BillInventoryReturnLine();
                rl.setBillItemId(line.getId());
                rl.setQuantityReturned(q);
                header.addLine(rl);
            }
            BillInventoryReturn saved = billInventoryReturnRepository.save(header);
            String noteBase = "Partial stock return id=" + saved.getId() + " for GST bill " + bill.getBillNumber() + " (bill id="
                    + bill.getId() + ")";
            for (BillInventoryReturnLine rl : saved.getLines()) {
                BillItemGST line = findGstLine(bill, rl.getBillItemId());
                applyStockReturnForGstLine(line, bill.getId(), rl.getQuantityReturned(), noteBase, billLocation,
                        bill.getBillDate(), null, null, INV_SRC_BILL_STOCK_RETURN);
            }
            applyBillReturnFinancialSettlement(BillKind.GST, bill.getId(), saved.getId(), refundMode, postedSettlement,
                    request.getRefundPaymentMode(), bill.getBillNumber(), billLocation, bill.getCustomer(), actorUserId,
                    bill.getBillDate());
            persistBillLifecycleAfterStockReturnGst(bill);
            return embellishStockReturnResponse(saved, computedReturn, postedSettlement, refundMode);
        }

        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        assertNonGstBillMutableForEdits(bill);
        BigDecimal computedReturnNg = computeNonGstStockReturnSettlement(bill, request.getLines());
        BigDecimal postedSettlementNg = resolvePostedReturnSettlement(refundMode, computedReturnNg, request);
        if (refundMode != BillReturnRefundMode.NO_REFUND && postedSettlementNg.compareTo(PAY_ROUND_EPS) <= 0) {
            throw new IllegalArgumentException(
                    "Computed return settlement is zero; choose NO_REFUND for stock-only returns.");
        }
        Map<Long, BigDecimal> returnedBefore = loadReturnedByLineId(BillKind.NON_GST, billId);
        BillInventoryReturn header = new BillInventoryReturn();
        header.setBillKind(BillKind.NON_GST);
        header.setBillId(billId);
        header.setLocation(location != null ? location.trim() : null);
        header.setNotes(request.getNotes());
        header.setCreatedByUserId(actorUserId);
        if (request.getAdjustmentGroupId() != null && !request.getAdjustmentGroupId().isBlank()) {
            header.setAdjustmentGroupId(request.getAdjustmentGroupId().trim());
        }
        for (BillStockReturnLineRequestDTO ln : request.getLines()) {
            BillItemNonGST line = findNonGstLine(bill, ln.getBillItemId());
            BigDecimal sold = line.getQuantity() != null ? line.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal already = returnedBefore.getOrDefault(line.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal q = BigDecimal.valueOf(ln.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            if (q.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Return quantity must be positive");
            }
            if (already.add(q).subtract(sold).compareTo(PAY_ROUND_EPS) > 0) {
                throw new IllegalArgumentException(
                        "Return exceeds remaining for line item id=" + line.getId() + ": sold=" + sold + ", already returned="
                                + already + ", this return=" + q);
            }
            BillInventoryReturnLine rl = new BillInventoryReturnLine();
            rl.setBillItemId(line.getId());
            rl.setQuantityReturned(q);
            BigDecimal unitPrice = line.getPricePerUnit() != null
                    ? line.getPricePerUnit().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            rl.setUnitPriceAtReturn(unitPrice);
            if (sold.compareTo(PAY_ROUND_EPS) > 0) {
                BigDecimal lineTotal = nz(line.getItemTotalPrice());
                rl.setLineReturnValue(lineTotal.multiply(q).divide(sold, 2, RoundingMode.HALF_UP));
            } else {
                rl.setLineReturnValue(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            }
            header.addLine(rl);
        }
        BillInventoryReturn saved = billInventoryReturnRepository.save(header);
        saved.setRefundMode(mapReturnRefundModeToStorage(refundMode, request.getRefundPaymentMode()));
        saved.setRefundAmount(postedSettlementNg);
        saved.setSettled(refundMode == BillReturnRefundMode.NO_REFUND || postedSettlementNg.compareTo(PAY_ROUND_EPS) > 0);
        if (Boolean.TRUE.equals(saved.getSettled())) {
            saved.setSettledAt(LocalDateTime.now());
        }
        billInventoryReturnRepository.save(saved);
        String noteBase = "Partial stock return id=" + saved.getId() + " for Non-GST bill " + bill.getBillNumber() + " (bill id="
                + bill.getId() + ")";
        for (BillInventoryReturnLine rl : saved.getLines()) {
            BillItemNonGST line = findNonGstLine(bill, rl.getBillItemId());
            applyStockReturnForNonGstLine(line, bill.getId(), rl.getQuantityReturned(), noteBase, billLocation,
                    bill.getBillDate(), null, null, INV_SRC_BILL_STOCK_RETURN);
        }
        applyBillReturnFinancialSettlement(BillKind.NON_GST, bill.getId(), saved.getId(), refundMode,
                postedSettlementNg, request.getRefundPaymentMode(), bill.getBillNumber(), billLocation, bill.getCustomer(),
                actorUserId, bill.getBillDate());
        finalizeNonGstStockReturn(bill, saved, refundMode, computedReturnNg, postedSettlementNg, billLocation, actorUserId);
        return embellishStockReturnResponse(saved, computedReturnNg, postedSettlementNg, refundMode);
    }

    /**
     * Patch billed quantities and/or append new lines (difference-style inventory):
     * <ul>
     *   <li>Increase: reserve extra qty, SALE/OUT, consume reservation (when stock applies).</li>
     *   <li>Decrease: RETURN/IN for the delta, {@code reversal_of_id} tied to prior bill sale when possible.</li>
     *   <li>Remove line: {@code quantity = 0} restores remaining sold qty (sold − prior partial returns) and removes the line.</li>
     *   <li><b>Case D — new lines</b> ({@code addedItems}): merged reserve → SALE/OUT with {@code source_action}
     *       {@code BILL_LINE_NEW_ITEM_SALE} → consume.</li>
     * </ul>
     * Lines that already have partial stock returns cannot have quantity reduced below the returned amount;
     * removing a line is blocked when that line has partial returns.
     */
    public BillResponseDTO patchBillLineQuantities(Long billId, String billType, BillLineQuantitiesPatchRequestDTO request,
            String location, Long actorUserId) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        List<BillLineQuantityPatchLineDTO> linePatches = request.getLines() != null ? request.getLines() : List.of();
        List<BillItemDTO> addedItems = request.getAddedItems() != null ? request.getAddedItems() : List.of();
        if (linePatches.isEmpty() && addedItems.isEmpty()) {
            throw new IllegalArgumentException("At least one line quantity patch or at least one addedItems row is required");
        }
        if (!linePatches.isEmpty()) {
            Set<Long> seen = new HashSet<>();
            for (BillLineQuantityPatchLineDTO ln : linePatches) {
                if (ln.getBillItemId() == null || !seen.add(ln.getBillItemId())) {
                    throw new IllegalArgumentException("Each billItemId must appear once and be non-null");
                }
            }
        }
        if ("GST".equalsIgnoreCase(billType)) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            assertGstBillMutableForEdits(bill);
            String patchLinkedGroupId = newLinkedGroupId();
            Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.GST, billId);
            LocalDate stockDate = bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now();
            for (BillLineQuantityPatchLineDTO patch : linePatches) {
                BillItemGST line = findGstLine(bill, patch.getBillItemId());
                BigDecimal oldQ = line.getQuantity() != null ? line.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal newQ = BigDecimal.valueOf(patch.getQuantity()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal r = returnedByLine.getOrDefault(line.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

                if (newQ.compareTo(BigDecimal.ZERO) == 0) {
                    if (r.compareTo(BigDecimal.ZERO) > 0) {
                        throw new IllegalArgumentException(
                                "Cannot remove line id=" + line.getId() + " because partial stock returns exist for this line.");
                    }
                    if (bill.getItems() == null || bill.getItems().size() <= 1) {
                        throw new IllegalArgumentException("Cannot remove the last line from a bill");
                    }
                    BigDecimal netSale = oldQ.subtract(r).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    String removeNote = "Line removed via GST bill " + bill.getBillNumber() + " line id=" + line.getId();
                    if (GST_BILLS_AFFECT_STOCK_AND_WALLET && netSale.compareTo(PAY_ROUND_EPS) > 0) {
                        applyStockReturnForGstLine(line, bill.getId(), netSale, removeNote, billLocation, stockDate, null, null,
                                INV_SRC_BILL_LINE_REMOVED_RETURN);
                    }
                    bill.removeItem(line);
                    recordBillEvent(BillKind.GST, billId, BillEventType.ITEM_REMOVED, null, patchLinkedGroupId, actorUserId,
                            java.util.Map.of("billItemId", line.getId()));
                    continue;
                }

                if (newQ.compareTo(r) < 0) {
                    throw new IllegalArgumentException(
                            "New quantity cannot be below already-returned quantity for line id=" + line.getId() + " (returned=" + r
                                    + ")");
                }
                if (r.compareTo(BigDecimal.ZERO) > 0 && newQ.compareTo(oldQ) < 0) {
                    throw new IllegalArgumentException(
                            "Cannot reduce billed quantity on line id=" + line.getId()
                                    + " because partial stock returns exist; increase quantity or record further returns instead.");
                }
                BigDecimal delta = oldQ.subtract(newQ);
                String note = "Qty update via GST bill " + bill.getBillNumber() + " line id=" + line.getId();
                if (GST_BILLS_AFFECT_STOCK_AND_WALLET) {
                    if (delta.compareTo(PAY_ROUND_EPS) > 0) {
                        applyStockReturnForGstLine(line, bill.getId(), delta, note, billLocation, stockDate, null, null,
                                INV_SRC_BILL_LINE_QTY_DECREASE_RETURN);
                    } else if (delta.compareTo(PAY_ROUND_EPS.negate()) < 0) {
                        BigDecimal add = delta.negate();
                        Long pid = line.getProduct() != null ? line.getProduct().getId() : null;
                        applyBillLineQuantityIncreaseStock(pid, line.getProductName(), add, bill.getId(), BillKind.GST, note,
                                billLocation, stockDate);
                    }
                }
                line.setQuantity(newQ);
                BigDecimal price = line.getPricePerUnit() != null ? line.getPricePerUnit().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                line.setItemTotalPrice(price.multiply(newQ).setScale(2, RoundingMode.HALF_UP));
                if (delta.compareTo(PAY_ROUND_EPS) > 0) {
                    recordBillEvent(BillKind.GST, billId, BillEventType.QUANTITY_DECREASED, null, patchLinkedGroupId, actorUserId,
                            java.util.Map.of("billItemId", line.getId(), "oldQuantity", oldQ.toPlainString(), "newQuantity",
                                    newQ.toPlainString()));
                } else if (delta.compareTo(PAY_ROUND_EPS.negate()) < 0) {
                    recordBillEvent(BillKind.GST, billId, BillEventType.QUANTITY_INCREASED, null, patchLinkedGroupId, actorUserId,
                            java.util.Map.of("billItemId", line.getId(), "oldQuantity", oldQ.toPlainString(), "newQuantity",
                                    newQ.toPlainString()));
                }
            }
            if (!addedItems.isEmpty()) {
                Map<Long, BigDecimal> addById = new HashMap<>();
                Map<String, BigDecimal> addByName = new HashMap<>();
                for (BillItemDTO dto : addedItems) {
                    if (dto == null || dto.getItemName() == null || dto.getItemName().isBlank()) {
                        throw new IllegalArgumentException("Each addedItems row requires itemName");
                    }
                    if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
                        throw new IllegalArgumentException("Each addedItems row requires positive quantity");
                    }
                    if (dto.getPricePerUnit() == null || dto.getPricePerUnit() <= 0) {
                        throw new IllegalArgumentException("Each addedItems row requires positive pricePerUnit");
                    }
                    appendGstLineFromBillItemDto(bill, dto);
                    recordBillEvent(BillKind.GST, billId, BillEventType.ITEM_ADDED, null, patchLinkedGroupId, actorUserId,
                            java.util.Map.of(
                                    "itemName", dto.getItemName(),
                                    "quantity", String.valueOf(dto.getQuantity()),
                                    "pricePerUnit", String.valueOf(dto.getPricePerUnit())));
                    mergeBillItemDtoIntoStockMaps(dto, addById, addByName);
                }
                String newNote = "New line(s) via GST bill " + bill.getBillNumber() + " (id=" + bill.getId() + ")";
                if (GST_BILLS_AFFECT_STOCK_AND_WALLET) {
                    applyMergedNewLineSaleStock(bill.getId(), BillKind.GST, addById, addByName, billLocation, stockDate, newNote);
                }
            }
            recomputeGstAmountsFromItems(bill);
            resyncAdvanceApplicationAfterLineQuantityEditGst(bill, actorUserId);
            assertBillTotalCoversRecordedPayments(BillKind.GST, billId, bill.getTotalAmount());
            bill.setUpdatedByUserId(actorUserId);
            billGSTRepository.save(bill);
            refreshBillFinancialsGST(bill, billLocation);
            BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.GST, billId, null);
            recordBillEvent(BillKind.GST, billId, BillEventType.ADVANCE_RECALCULATED, null, patchLinkedGroupId, actorUserId,
                    java.util.Map.of("advanceApplied", adv.toPlainString()));
            recordBillEvent(BillKind.GST, billId, BillEventType.BILL_EDITED, null, patchLinkedGroupId, actorUserId,
                    java.util.Map.of("linePatches", linePatches.size(), "addedItems", addedItems.size()));
            return convertGSTToResponseDTO(bill, billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId), adv);
        }

        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        assertNonGstBillMutableForEdits(bill);
        String patchLinkedGroupId = newLinkedGroupId();
        Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.NON_GST, billId);
        LocalDate stockDate = bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now();
        for (BillLineQuantityPatchLineDTO patch : linePatches) {
            BillItemNonGST line = findNonGstLine(bill, patch.getBillItemId());
            BigDecimal oldQ = line.getQuantity() != null ? line.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal newQ = BigDecimal.valueOf(patch.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal r = returnedByLine.getOrDefault(line.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            if (newQ.compareTo(BigDecimal.ZERO) == 0) {
                if (r.compareTo(BigDecimal.ZERO) > 0) {
                    throw new IllegalArgumentException(
                            "Cannot remove line id=" + line.getId() + " because partial stock returns exist for this line.");
                }
                if (bill.getItems() == null || bill.getItems().size() <= 1) {
                    throw new IllegalArgumentException("Cannot remove the last line from a bill");
                }
                BigDecimal netSale = oldQ.subtract(r).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                String removeNote = "Line removed via Non-GST bill " + bill.getBillNumber() + " line id=" + line.getId();
                if (netSale.compareTo(PAY_ROUND_EPS) > 0) {
                    applyStockReturnForNonGstLine(line, bill.getId(), netSale, removeNote, billLocation, stockDate, null, null,
                            INV_SRC_BILL_LINE_REMOVED_RETURN);
                }
                bill.removeItem(line);
                recordBillEvent(BillKind.NON_GST, billId, BillEventType.ITEM_REMOVED, null, patchLinkedGroupId, actorUserId,
                        java.util.Map.of("billItemId", line.getId()));
                continue;
            }

            if (newQ.compareTo(r) < 0) {
                throw new IllegalArgumentException(
                        "New quantity cannot be below already-returned quantity for line id=" + line.getId() + " (returned=" + r + ")");
            }
            if (r.compareTo(BigDecimal.ZERO) > 0 && newQ.compareTo(oldQ) < 0) {
                throw new IllegalArgumentException(
                        "Cannot reduce billed quantity on line id=" + line.getId()
                                + " because partial stock returns exist; increase quantity or record further returns instead.");
            }
            BigDecimal delta = oldQ.subtract(newQ);
            String note = "Qty update via Non-GST bill " + bill.getBillNumber() + " line id=" + line.getId();
            if (delta.compareTo(PAY_ROUND_EPS) > 0) {
                applyStockReturnForNonGstLine(line, bill.getId(), delta, note, billLocation, stockDate, null, null,
                        INV_SRC_BILL_LINE_QTY_DECREASE_RETURN);
            } else if (delta.compareTo(PAY_ROUND_EPS.negate()) < 0) {
                BigDecimal add = delta.negate();
                Long pid = line.getProduct() != null ? line.getProduct().getId() : null;
                applyBillLineQuantityIncreaseStock(pid, line.getProductName(), add, bill.getId(), BillKind.NON_GST, note,
                        billLocation, stockDate);
            }
            line.setQuantity(newQ);
            BigDecimal price = line.getPricePerUnit() != null ? line.getPricePerUnit().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            line.setItemTotalPrice(price.multiply(newQ).setScale(2, RoundingMode.HALF_UP));
            if (delta.compareTo(PAY_ROUND_EPS) > 0) {
                recordBillEvent(BillKind.NON_GST, billId, BillEventType.QUANTITY_DECREASED, null, patchLinkedGroupId, actorUserId,
                        java.util.Map.of("billItemId", line.getId(), "oldQuantity", oldQ.toPlainString(), "newQuantity",
                                newQ.toPlainString()));
            } else if (delta.compareTo(PAY_ROUND_EPS.negate()) < 0) {
                recordBillEvent(BillKind.NON_GST, billId, BillEventType.QUANTITY_INCREASED, null, patchLinkedGroupId, actorUserId,
                        java.util.Map.of("billItemId", line.getId(), "oldQuantity", oldQ.toPlainString(), "newQuantity",
                                newQ.toPlainString()));
            }
        }
        if (!addedItems.isEmpty()) {
            Map<Long, BigDecimal> addById = new HashMap<>();
            Map<String, BigDecimal> addByName = new HashMap<>();
            for (BillItemDTO dto : addedItems) {
                if (dto == null || dto.getItemName() == null || dto.getItemName().isBlank()) {
                    throw new IllegalArgumentException("Each addedItems row requires itemName");
                }
                if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Each addedItems row requires positive quantity");
                }
                if (dto.getPricePerUnit() == null || dto.getPricePerUnit() <= 0) {
                    throw new IllegalArgumentException("Each addedItems row requires positive pricePerUnit");
                }
                appendNonGstLineFromBillItemDto(bill, dto);
                recordBillEvent(BillKind.NON_GST, billId, BillEventType.ITEM_ADDED, null, patchLinkedGroupId, actorUserId,
                        java.util.Map.of(
                                "itemName", dto.getItemName(),
                                "quantity", String.valueOf(dto.getQuantity()),
                                "pricePerUnit", String.valueOf(dto.getPricePerUnit())));
                mergeBillItemDtoIntoStockMaps(dto, addById, addByName);
            }
            String newNote = "New line(s) via Non-GST bill " + bill.getBillNumber() + " (id=" + bill.getId() + ")";
            applyMergedNewLineSaleStock(bill.getId(), BillKind.NON_GST, addById, addByName, billLocation, stockDate, newNote);
        }
        recomputeNonGstAmountsFromItems(bill);
        resyncAdvanceApplicationAfterLineQuantityEditNonGst(bill, actorUserId);
        assertBillTotalCoversRecordedPayments(BillKind.NON_GST, billId, bill.getTotalAmount());
        bill.setUpdatedByUserId(actorUserId);
        billNonGSTRepository.save(bill);
        refreshBillFinancialsNonGST(bill, billLocation);
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.NON_GST, billId, null);
        recordBillEvent(BillKind.NON_GST, billId, BillEventType.ADVANCE_RECALCULATED, null, patchLinkedGroupId, actorUserId,
                java.util.Map.of("advanceApplied", adv.toPlainString()));
        recordBillEvent(BillKind.NON_GST, billId, BillEventType.BILL_EDITED, null, patchLinkedGroupId, actorUserId,
                java.util.Map.of("linePatches", linePatches.size(), "addedItems", addedItems.size()));
        return convertNonGSTToResponseDTO(bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId), adv);
    }

    /**
     * Soft-delete a bill and reverse its side effects (pending, partial, or fully paid):
     * - restore inventory for remaining line quantities (sold minus prior partial returns)
     * - soft-delete every active bill payment and reverse CASH/UPI in-hand via ledger + daily budget
     * - restore customer advance amounts applied to this bill
     */
    public void deleteBill(Long billId, String billType, String location, Long actorUserId, String cancelReasonRaw) {
        String cancelReason = trimToNull(cancelReasonRaw);
        if ("GST".equalsIgnoreCase(billType)) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            if (Boolean.TRUE.equals(bill.getIsDeleted())) {
                log.info("delete_bill_skip_already_deleted kind=GST billId={}", billId);
                ensureInHandRefundsPostedForBill(BillKind.GST, billId, actorUserId);
                recomputeSnapshotsForBillFromDbPayments(bill.getLocation() != null ? bill.getLocation().trim()
                        : resolveBillLocation(bill, bill.getCustomer()), bill.getBillDate(), BillKind.GST, billId,
                        Set.of(LocalDate.now()));
                return;
            }
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            if (cancelReason != null) {
                appendBillLifecycleNoteGst(bill, cancelReason, "CANCEL");
            }
            Long previousVersionId = latestBillVersionId(billId);
            int nextVersionNo = nextBillVersionNo(billId);
            String opLinkedGroupId = newLinkedGroupId();
            String versionReason = cancelReason == null ? "Bill cancelled"
                    : truncateBillVersionEditReason("Bill cancelled: " + cancelReason);
            Long currentBillVersionRowId = beginBillVersion(billId, nextVersionNo, "CANCEL", previousVersionId,
                    Map.of("kind", "GST", "linkedGroupId", opLinkedGroupId), versionReason, actorUserId).getId();
            List<ResolvedLine> snapshotPayLines = resolvedNonAdvanceLinesForSnapshot(BillKind.GST, billId);
            BigDecimal advanceBeforeCancel = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId)
                    .setScale(2, RoundingMode.HALF_UP);
            recordBillCancellationAudit(BillKind.GST, billId, bill.getBillNumber(), bill.getBillDate(), bill.getCustomer(),
                    bill.getTotalAmount(), bill.getPaymentMethod(),
                    bill.getPaymentStatus() != null ? bill.getPaymentStatus().name() : null,
                    billLocation, actorUserId, cancelReason);
            inventoryReservationService.releaseForBill(billId, BillKind.GST);
            revertStockForGstBill(bill, billLocation, opLinkedGroupId, currentBillVersionRowId);
            deactivateBillPayments(BillKind.GST, billId, billLocation, actorUserId, currentBillVersionRowId, opLinkedGroupId);
            customerAdvanceService.reverseAdvanceUsageForBill(BillKind.GST, billId, currentBillVersionRowId, opLinkedGroupId);
            bill.setIsDeleted(true);
            bill.setUpdatedByUserId(actorUserId);
            bill.setPaymentStatus(BillGST.PaymentStatus.CANCELLED);
            bill.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            bill.setPaymentMethod("-");
            bill.setCurrentVersionNo(nextVersionNo);
            bill.setLatestVersion(true);
            bill.setBillStatus("CANCELLED");
            billGSTRepository.save(bill);
            BillResponseDTO snapshot = convertGSTToResponseDTO(
                    bill,
                    billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            finalizeBillVersionSnapshot(currentBillVersionRowId, snapshot);
            if (advanceBeforeCancel.compareTo(PAY_ROUND_EPS) > 0) {
                recordBillEvent(BillKind.GST, billId, BillEventType.REFUND_CREATED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                        java.util.Map.of("advanceRestored", advanceBeforeCancel.toPlainString(), "reason", "BILL_CANCELLED"));
            }
            recomputeSnapshotsForBillMutation(billLocation, bill.getBillDate(), snapshotPayLines);
            return;
        }

        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        if (Boolean.TRUE.equals(bill.getIsDeleted())) {
            log.info("delete_bill_skip_already_deleted kind=NON_GST billId={}", billId);
            ensureInHandRefundsPostedForBill(BillKind.NON_GST, billId, actorUserId);
            recomputeSnapshotsForBillFromDbPayments(bill.getLocation() != null ? bill.getLocation().trim()
                    : resolveBillLocation(bill, bill.getCustomer()), bill.getBillDate(), BillKind.NON_GST, billId,
                    Set.of(LocalDate.now()));
            return;
        }
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        if (cancelReason != null) {
            appendBillLifecycleNoteNonGst(bill, cancelReason, "CANCEL");
        }
        Long previousVersionId = latestBillVersionId(billId);
        int nextVersionNo = nextBillVersionNo(billId);
        String opLinkedGroupId = newLinkedGroupId();
        String versionReason = cancelReason == null ? "Bill cancelled"
                : truncateBillVersionEditReason("Bill cancelled: " + cancelReason);
        Long currentBillVersionRowId = beginBillVersion(billId, nextVersionNo, "CANCEL", previousVersionId,
                Map.of("kind", "NON_GST", "linkedGroupId", opLinkedGroupId), versionReason, actorUserId).getId();
        List<ResolvedLine> snapshotPayLines = resolvedNonAdvanceLinesForSnapshot(BillKind.NON_GST, billId);
        BigDecimal advanceBeforeCancel = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId)
                .setScale(2, RoundingMode.HALF_UP);
        recordBillCancellationAudit(BillKind.NON_GST, billId, bill.getBillNumber(), bill.getBillDate(), bill.getCustomer(),
                bill.getTotalAmount(), bill.getPaymentMethod(),
                bill.getPaymentStatus() != null ? bill.getPaymentStatus().name() : null,
                billLocation, actorUserId, cancelReason);
        inventoryReservationService.releaseForBill(billId, BillKind.NON_GST);
        revertStockForNonGstBill(bill, billLocation, opLinkedGroupId, currentBillVersionRowId);
        deactivateBillPayments(BillKind.NON_GST, billId, billLocation, actorUserId, currentBillVersionRowId, opLinkedGroupId);
        customerAdvanceService.reverseAdvanceUsageForBill(BillKind.NON_GST, billId, currentBillVersionRowId, opLinkedGroupId);
        bill.setIsDeleted(true);
        bill.setUpdatedByUserId(actorUserId);
        bill.setPaymentStatus(BillNonGST.PaymentStatus.CANCELLED);
        bill.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        bill.setPaymentMethod("-");
        bill.setCurrentVersionNo(nextVersionNo);
        bill.setLatestVersion(true);
        bill.setBillStatus(BillLifecycleStatus.CANCELLED);
        billNonGSTRepository.save(bill);
        BillResponseDTO snapshot = convertNonGSTToResponseDTO(
                bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        finalizeBillVersionSnapshot(currentBillVersionRowId, snapshot);
        if (advanceBeforeCancel.compareTo(PAY_ROUND_EPS) > 0) {
            recordBillEvent(BillKind.NON_GST, billId, BillEventType.REFUND_CREATED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                    java.util.Map.of("advanceRestored", advanceBeforeCancel.toPlainString(), "reason", "BILL_CANCELLED"));
        }
        recordBillEvent(BillKind.NON_GST, billId, BillEventType.BILL_CANCELLED, currentBillVersionRowId, opLinkedGroupId, actorUserId,
                java.util.Map.of("billNumber", bill.getBillNumber() != null ? bill.getBillNumber() : ""));
        recomputeSnapshotsForBillMutation(billLocation, bill.getBillDate(), snapshotPayLines);
    }

    /**
     * Cancelled-bill audit for the branch, filtered by bill date (inclusive).
     */
    public List<BillCancellationLogDTO> getBillCancellationLogs(String location, LocalDate billDateFrom, LocalDate billDateTo) {
        String loc = location == null ? "" : location.trim();
        if (loc.isBlank()) {
            throw new IllegalArgumentException("Location is required");
        }
        LocalDate to = billDateTo != null ? billDateTo : LocalDate.now();
        LocalDate from = billDateFrom != null ? billDateFrom : to.minusDays(30);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("billDateTo must be on or after billDateFrom");
        }
        return billCancellationLogRepository
                .findByLocationAndBillDateBetweenOrderByCancelledAtDesc(loc, from, to)
                .stream()
                .map(this::toCancellationLogDto)
                .collect(Collectors.toList());
    }

    /**
     * Best-effort delete by id without requiring caller to know bill type.
     * Tries GST first then NON_GST.
     */
    public void deleteBillById(Long billId, String location, Long actorUserId, String cancelReason) {
        try {
            deleteBill(billId, "GST", location, actorUserId, cancelReason);
            return;
        } catch (RuntimeException ignored) {
            // try non-gst
        }
        deleteBill(billId, "NON_GST", location, actorUserId, cancelReason);
    }

    public List<BillEventResponseDTO> listBillEvents(Long billId, String billType, String location) {
        BillKind kind = ("GST".equalsIgnoreCase(billType) || "gst".equalsIgnoreCase(billType))
                ? BillKind.GST
                : BillKind.NON_GST;
        assertBillAccessibleAtLocation(billId, kind, location);
        return billEventService.listEvents(kind, billId).stream()
                .map(this::toBillEventResponseDTO)
                .collect(Collectors.toList());
    }

    /** Location check only — must not call {@link #getBillById} (used from bill detail enrichment). */
    private void assertBillAccessibleAtLocation(Long billId, BillKind kind, String location) {
        if (kind == BillKind.GST) {
            BillGST bill = billGSTRepository.findById(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            if (!Objects.equals(location, resolveBillLocation(bill, bill.getCustomer()))) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            return;
        }
        BillNonGST bill = billNonGSTRepository.findById(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        if (!Objects.equals(location, resolveBillLocation(bill, bill.getCustomer()))) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
    }

    private BillEventResponseDTO toBillEventResponseDTO(BillEvent e) {
        return new BillEventResponseDTO(
                e.getId(),
                e.getBillKind(),
                e.getBillId(),
                e.getEventType(),
                e.getBillVersionId(),
                e.getLinkedGroupId(),
                e.getPayloadJson(),
                e.getCreatedBy(),
                e.getCreatedAt());
    }

    public BillResponseDTO getBillById(Long id, String billType, String location) {
        if ("GST".equalsIgnoreCase(billType) || "gst".equalsIgnoreCase(billType)) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(id)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + id));
            // Verify location matches
            if (!location.equals(resolveBillLocation(bill, bill.getCustomer()))) {
                throw new RuntimeException("GST Bill not found with id: " + id);
            }
            List<BillPayment> payments = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, id);
            BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, id);
            return convertGSTToResponseDTO(bill, payments, adv);
        } else {
            BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(id)
                    .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + id));
            // Verify location matches

            if (!location.equals(resolveBillLocation(bill, bill.getCustomer()))) {
                throw new RuntimeException("NonGST Bill not found with id: " + id);
            }
            List<BillPayment> payments = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, id);
            BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, id);
            BillResponseDTO dto = convertNonGSTToResponseDTO(bill, payments, adv);
            enrichNonGstBillDetail(dto, bill, location);
            return dto;
        }
    }

    /**
     * Stock return audit list for a NON-GST bill (does not mutate data).
     */
    public List<BillStockReturnHistoryDTO> listStockReturnsForBill(Long billId, String billType, String location) {
        BillKind kind = parseBillKind(billType);
        if (kind != BillKind.NON_GST) {
            throw new IllegalArgumentException("Stock return history is supported for NON_GST bills only in this release");
        }
        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        if (!Objects.equals(location, resolveBillLocation(bill, bill.getCustomer()))) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        return buildStockReturnHistory(BillKind.NON_GST, billId, bill);
    }

    /**
     * Rebuilds daily closing snapshots from persisted {@code bill_payments} for this bill through today.
     * Does not delete payment, inventory, or wallet ledger rows.
     */
    public void recomputeSnapshotsForBill(Long billId, String billType, String location) {
        if (billId == null || billType == null || billType.isBlank() || location == null || location.isBlank()) {
            return;
        }
        BillKind kind = "GST".equalsIgnoreCase(billType.trim()) ? BillKind.GST : BillKind.NON_GST;
        LocalDate billDate = null;
        if (kind == BillKind.GST) {
            billDate = billGSTRepository.findById(billId).map(BillGST::getBillDate).orElse(null);
        } else {
            billDate = billNonGSTRepository.findById(billId).map(BillNonGST::getBillDate).orElse(null);
        }
        if (billDate == null) {
            return;
        }
        recomputeSnapshotsForBillFromDbPayments(location, billDate, kind, billId, null);
    }

    /**
     * Lifecycle / location guard for any bill revision (replace, patch, payment add). Throws {@link IllegalArgumentException}
     * when the bill cannot be edited.
     */
    public void assertBillMutableForRevision(Long billId, String billType, String location) {
        BillKind kind = parseBillKind(billType);
        if (kind == BillKind.GST) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            assertGstBillMutableForEdits(bill);
        } else {
            BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("NonGST Bill not found with id: " + billId);
            }
            assertNonGstBillMutableForEdits(bill);
        }
    }

    /**
     * Full PUT replace only: bill must be mutable and must not have partial stock returns (use line patch / return flow).
     */
    public void assertEligibleForFullBillReplace(Long billId, String billType, String location) {
        assertBillMutableForRevision(billId, billType, location);
        BillKind kind = parseBillKind(billType);
        Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(kind, billId);
        if (!returnedByLine.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot fully edit bill with stock returns. Use quantity patch/stock return flow.");
        }
    }

    /**
     * Pre-flight for full replace: ensures net quantity <i>increases</i> per product (new bill lines minus current
     * persisted net sold, after partial returns) fit in current on-hand stock. Does not mutate inventory.
     * When GST bills do not affect stock ({@link #GST_BILLS_AFFECT_STOCK_AND_WALLET} is false), this is a no-op for GST.
     */
    public void assertNetStockSufficientForFullReplace(Long billId, String billType, BillRequestDTO req) {
        if (billId == null || req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("billId and request items are required for stock pre-flight");
        }
        BillKind kind = parseBillKind(billType);
        if (kind == BillKind.GST && !GST_BILLS_AFFECT_STOCK_AND_WALLET) {
            return;
        }
        Map<Long, BigDecimal> oldById = new HashMap<>();
        Map<String, BigDecimal> oldByName = new HashMap<>();
        if (kind == BillKind.GST) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.GST, billId);
            if (bill.getItems() != null) {
                for (BillItemGST line : bill.getItems()) {
                    BigDecimal sold = line.getQuantity() != null
                            ? line.getQuantity().setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal ret = returnedByLine.getOrDefault(line.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal netOld = sold.subtract(ret).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    if (netOld.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    Product p = line.getProduct();
                    if (p != null && p.getId() != null) {
                        oldById.merge(p.getId(), netOld, BigDecimal::add);
                    } else if (line.getProductName() != null && !line.getProductName().isBlank()) {
                        oldByName.merge(line.getProductName().trim(), netOld, BigDecimal::add);
                    }
                }
            }
        } else {
            BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
            Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.NON_GST, billId);
            if (bill.getItems() != null) {
                for (BillItemNonGST line : bill.getItems()) {
                    BigDecimal sold = line.getQuantity() != null
                            ? line.getQuantity().setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal ret = returnedByLine.getOrDefault(line.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal netOld = sold.subtract(ret).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    if (netOld.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    Product p = line.getProduct();
                    if (p != null && p.getId() != null) {
                        oldById.merge(p.getId(), netOld, BigDecimal::add);
                    } else if (line.getProductName() != null && !line.getProductName().isBlank()) {
                        oldByName.merge(line.getProductName().trim(), netOld, BigDecimal::add);
                    }
                }
            }
        }
        Map<Long, BigDecimal> newById = new HashMap<>();
        Map<String, BigDecimal> newByName = new HashMap<>();
        for (BillItemDTO itemDTO : req.getItems()) {
            BigDecimal quantity = BigDecimal.valueOf(itemDTO.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            if (itemDTO.getProductId() != null) {
                newById.merge(itemDTO.getProductId(), quantity, BigDecimal::add);
            } else if (itemDTO.getItemName() != null && !itemDTO.getItemName().isBlank()) {
                newByName.merge(itemDTO.getItemName().trim(), quantity, BigDecimal::add);
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : newById.entrySet()) {
            BigDecimal oldQ = oldById.getOrDefault(entry.getKey(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            BigDecimal inc = entry.getValue().subtract(oldQ);
            if (inc.compareTo(PAY_ROUND_EPS) > 0) {
                productService.validateStockAvailability(entry.getKey(), inc);
            }
        }
        for (Map.Entry<String, BigDecimal> entry : newByName.entrySet()) {
            BigDecimal oldQ = oldByName.getOrDefault(entry.getKey(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            BigDecimal inc = entry.getValue().subtract(oldQ);
            if (inc.compareTo(PAY_ROUND_EPS) > 0) {
                productService.validateStockAvailabilityByName(entry.getKey(), inc);
            }
        }
    }

    /**
     * Recomputes derived settlement fields ({@code paidAmount}, {@code paymentStatus}, payment summary) from
     * persisted {@code bill_payments} and wallet usage — append-only; does not delete payment rows.
     */
    public BillResponseDTO refreshOutstandingAndPaymentStatus(Long billId, String billType, String location) {
        if (billId == null || billType == null || billType.isBlank() || location == null || location.isBlank()) {
            throw new IllegalArgumentException("billId, billType, and location are required");
        }
        BillKind kind = parseBillKind(billType);
        if (kind == BillKind.GST) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            refreshBillFinancialsGST(bill, billLocation);
        } else {
            BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("NonGST Bill not found with id: " + billId);
            }
            refreshBillFinancialsNonGST(bill, billLocation);
        }
        return getBillById(billId, billType, location);
    }

    /**
     * Re-applies customer wallet advance against the current persisted bill total (same approach as line-quantity patch
     * resync for Non-GST). For GST, runs only when {@link #GST_BILLS_AFFECT_STOCK_AND_WALLET} is enabled; otherwise
     * only {@link #refreshBillFinancialsGST} runs. Requires the bill to be mutable for edits.
     */
    public BillResponseDTO resynchronizeAdvanceApplicationForBill(Long billId, String billType, Long actorUserId, String location) {
        if (billId == null || billType == null || billType.isBlank() || location == null || location.isBlank() || actorUserId == null) {
            throw new IllegalArgumentException("billId, billType, location, and actorUserId are required");
        }
        BillKind kind = parseBillKind(billType);
        if (kind == BillKind.GST) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            assertGstBillMutableForEdits(bill);
            if (GST_BILLS_AFFECT_STOCK_AND_WALLET) {
                resyncAdvanceApplicationAfterLineQuantityEditGst(bill, actorUserId);
            }
            refreshBillFinancialsGST(bill, billLocation);
        } else {
            BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("NonGST Bill not found with id: " + billId);
            }
            assertNonGstBillMutableForEdits(bill);
            resyncAdvanceApplicationAfterLineQuantityEditNonGst(bill, actorUserId);
            refreshBillFinancialsNonGST(bill, billLocation);
        }
        return getBillById(billId, billType, location);
    }

    public BillResponseDTO getBillByBillNumber(String billNumber, String location) {
        // Check GST bills first
        BillGST gstBill = billGSTRepository
                .findByBillNumberAndBillLocationWithItemsAndProducts(billNumber, location).orElse(null);
        if (gstBill != null) {
            List<BillPayment> payments = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST,
                    gstBill.getId());
            BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, gstBill.getId());
            return convertGSTToResponseDTO(gstBill, payments, adv);
        }

        // Check NonGST bills
        BillNonGST nonGstBill = billNonGSTRepository
                .findByBillNumberAndBillLocationWithItemsAndProducts(billNumber, location).orElse(null);
        log.info("Searching for bill number={} in location={}, nonGstBillFound={}", billNumber, location, nonGstBill != null);
        if (nonGstBill != null) {
            List<BillPayment> payments = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST,
                    nonGstBill.getId());
            BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, nonGstBill.getId());
            return convertNonGSTToResponseDTO(nonGstBill, payments, adv);
        }

        throw new RuntimeException("Bill not found with bill number: " + billNumber);
    }

    public List<BillResponseDTO> getAllBills(String location, Long createdByUserId) {
        return getAllBills(location, createdByUserId, null, null);
    }

    public List<BillResponseDTO> getAllBills(
            String location, Long createdByUserId, LocalDate billDateFrom, LocalDate billDateTo) {
        final boolean dateScoped = billDateFrom != null && billDateTo != null;
        List<BillGST> gstEntities;
        List<BillNonGST> nonEntities;
        if (dateScoped) {
            gstEntities = billGSTRepository.findByBillLocationWithCustomerAndBillDateBetween(
                    location, billDateFrom, billDateTo);
            nonEntities = createdByUserId != null
                    ? billNonGSTRepository.findByBillLocationWithCustomerAndCreatedByAndBillDateBetween(
                            location, createdByUserId, billDateFrom, billDateTo)
                    : billNonGSTRepository.findByBillLocationWithCustomerAndBillDateBetween(
                            location, billDateFrom, billDateTo);
        } else {
            gstEntities = createdByUserId != null
                    ? billGSTRepository.findByBillLocationAndCreatedByUserId(location, createdByUserId)
                    : billGSTRepository.findByBillLocation(location);
            nonEntities = createdByUserId != null
                    ? billNonGSTRepository.findByBillLocationWithCustomerAndCreatedBy(location, createdByUserId)
                    : billNonGSTRepository.findByBillLocationWithCustomer(location);
        }

        Map<String, List<BillPayment>> paymentMap = loadPaymentsGrouped(gstEntities, nonEntities);
        Map<String, BigDecimal> advanceMap = customerAdvanceService.sumAdvanceUsedGrouped(
                gstEntities.stream().map(BillGST::getId).toList(),
                nonEntities.stream().map(BillNonGST::getId).toList());

        List<Long> nonGstIds = nonEntities.stream().map(BillNonGST::getId).toList();
        Map<Long, Map<Long, BigDecimal>> returnedByBill =
                loadReturnedByLineIdBatch(BillKind.NON_GST, nonGstIds);
        Map<Long, BigDecimal> cumReturnValueByBill =
                loadCumulativeReturnValueByBillBatch(BillKind.NON_GST, nonGstIds);

        List<BillResponseDTO> gstBills = gstEntities.stream()
                .map(b -> convertGSTToResponseDTO(b,
                        paymentMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), List.of()),
                        advanceMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        List<BillResponseDTO> nonGstBills = nonEntities.stream()
                .map(b -> convertNonGSTToListSummaryDTO(b,
                        paymentMap.getOrDefault(paymentKey(BillKind.NON_GST, b.getId()), List.of()),
                        advanceMap.getOrDefault(paymentKey(BillKind.NON_GST, b.getId()), BigDecimal.ZERO),
                        returnedByBill.getOrDefault(b.getId(), Map.of()),
                        cumReturnValueByBill.getOrDefault(b.getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        return Stream.concat(gstBills.stream(), nonGstBills.stream())
                .sorted((a, b) -> {
                    int dateCompare = b.getBillDate().compareTo(a.getBillDate());
                    if (dateCompare != 0) {
                        return dateCompare;
                    }
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .collect(Collectors.toList());
    }

    public List<BillResponseDTO> getAllSales(String location) {
        return getAllBills(location, null);
    }

    public List<BillResponseDTO> getAllSales(String location, LocalDate billDateFrom, LocalDate billDateTo) {
        return getAllBills(location, null, billDateFrom, billDateTo);
    }

    /**
     * Lean bill payload for adjustment-session open (line items + summary; no heavy return-history rebuild).
     */
    public BillResponseDTO getNonGstBillForAdjustmentSession(Long id, String location) {
        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(id)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + id));
        if (!location.equals(resolveBillLocation(bill, bill.getCustomer()))) {
            throw new RuntimeException("NonGST Bill not found with id: " + id);
        }
        List<BillPayment> payments =
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, id);
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, id);
        BillResponseDTO dto = convertNonGSTToResponseDTO(bill, payments, adv);
        dto.setSupplementaryBills(buildSupplementarySummaries(bill.getId(), location));
        return dto;
    }

    public List<BillResponseDTO> getBillsByMobileNumber(String mobileNumber) {
        Customer customer = customerService.getCustomerByPhone(mobileNumber);

        // Combine both GST and NonGST bills for the customer
        List<BillGST> gstEntities = billGSTRepository.findByCustomer(customer);
        List<BillNonGST> nonEntities = billNonGSTRepository.findByCustomer(customer);
        Map<String, List<BillPayment>> paymentMap = loadPaymentsGrouped(gstEntities, nonEntities);
        Map<String, BigDecimal> advanceMap = customerAdvanceService.sumAdvanceUsedGrouped(
                gstEntities.stream().map(BillGST::getId).toList(),
                nonEntities.stream().map(BillNonGST::getId).toList());

        List<BillResponseDTO> gstBills = gstEntities.stream()
                .map(b -> convertGSTToResponseDTO(b,
                        paymentMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), List.of()),
                        advanceMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        List<BillResponseDTO> nonGstBills = nonEntities.stream()
                .map(b -> convertNonGSTToResponseDTO(b,
                        paymentMap.getOrDefault(paymentKey(BillKind.NON_GST, b.getId()), List.of()),
                        advanceMap.getOrDefault(paymentKey(BillKind.NON_GST, b.getId()), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        return Stream.concat(gstBills.stream(), nonGstBills.stream())
                .collect(Collectors.toList());
    }

    /**
     * Location-scoped bills by customer mobile.
     * Filters by bill.location snapshot (fallback to customer.location for legacy rows where location is null).
     */
    public List<BillResponseDTO> getBillsByMobileNumber(String mobileNumber, String location) {
        Customer customer = customerService.getCustomerByPhone(mobileNumber);

        List<BillGST> gstEntities = billGSTRepository.findByCustomer(customer);
        List<BillNonGST> nonEntities = billNonGSTRepository.findByCustomer(customer);

        Map<String, List<BillPayment>> paymentMap = loadPaymentsGrouped(gstEntities, nonEntities);
        Map<String, BigDecimal> advanceMap = customerAdvanceService.sumAdvanceUsedGrouped(
                gstEntities.stream().map(BillGST::getId).toList(),
                nonEntities.stream().map(BillNonGST::getId).toList());

        final String loc = location == null ? "" : location.trim();

        List<BillResponseDTO> gstBills = gstEntities.stream()
                .filter(b -> {
                    String bLoc = resolveBillLocation(b, b.getCustomer());
                    return bLoc != null && bLoc.equals(loc);
                })
                .map(b -> convertGSTToResponseDTO(b,
                        paymentMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), List.of()),
                        advanceMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        List<BillResponseDTO> nonGstBills = nonEntities.stream()
                .filter(b -> {
                    String bLoc = resolveBillLocation(b, b.getCustomer());
                    return bLoc != null && bLoc.equals(loc);
                })
                .map(b -> convertNonGSTToResponseDTO(b,
                        paymentMap.getOrDefault(paymentKey(BillKind.NON_GST, b.getId()), List.of()),
                        advanceMap.getOrDefault(paymentKey(BillKind.NON_GST, b.getId()), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        return Stream.concat(gstBills.stream(), nonGstBills.stream())
                .collect(Collectors.toList());
    }

    private BillResponseDTO convertGSTToResponseDTO(BillGST bill, List<BillPayment> paymentRows,
            BigDecimal advanceUsed) {
        BillResponseDTO responseDTO = new BillResponseDTO();
        responseDTO.setId(bill.getId());
        responseDTO.setBillNumber(bill.getBillNumber());
        responseDTO.setBillType("GST");
        responseDTO.setCustomerMobileNumber(bill.getCustomer().getPhone());
        responseDTO.setCustomerId(bill.getCustomer().getId());
        responseDTO.setCustomerName(bill.getCustomer().getCustomerName());
        responseDTO.setAddress(bill.getCustomer().getAddress());
        responseDTO.setGstin(bill.getCustomer().getGstin());
        responseDTO.setCustomerEmail(bill.getCustomer().getEmail());
        responseDTO.setBillDate(bill.getBillDate());
        responseDTO.setHsnCode(bill.getHsnCode());
        responseDTO.setVehicleNo(bill.getVehicleNo());
        responseDTO.setDeliveryAddress(bill.getDeliveryAddress());
        responseDTO.setTotalSqft(bill.getTotalSqft().doubleValue());
        responseDTO.setSubtotal(bill.getSubtotal().doubleValue());
        responseDTO.setTaxPercentage(bill.getTaxRate().doubleValue());
        responseDTO.setTaxAmount(bill.getTaxAmount().doubleValue());
        responseDTO.setServiceCharge(bill.getServiceCharge().doubleValue());
        responseDTO.setLabourCharge(bill.getLabourCharge().doubleValue());
        responseDTO.setTransportationCharge(bill.getTransportationCharge().doubleValue());
        responseDTO.setOtherExpenses(bill.getOtherExpenses() != null ? bill.getOtherExpenses().doubleValue() : 0.0);
        responseDTO.setDiscountAmount(bill.getDiscountAmount().doubleValue());
        responseDTO.setTotalAmount(bill.getTotalAmount().doubleValue());
        responseDTO.setPaymentStatus(bill.getPaymentStatus().name());
        responseDTO.setNotes(bill.getNotes());
        responseDTO.setCreatedAt(bill.getCreatedAt());
        responseDTO.setCreatedByUserId(bill.getCreatedByUserId());
        responseDTO.setLocation(resolveBillLocation(bill, bill.getCustomer()));
        responseDTO.setBackdated(Boolean.TRUE.equals(bill.getBackdated()));
        responseDTO.setOriginalCreatedAt(bill.getOriginalCreatedAt());
        responseDTO.setBackdateReason(bill.getBackdateReason());
        responseDTO.setBackdateApprovedBy(bill.getBackdateApprovedBy());
        responseDTO.setSupplementaryBill(Boolean.TRUE.equals(bill.getSupplementaryBill()));
        responseDTO.setParentBillId(bill.getParentBillId());
        responseDTO.setParentBillType(bill.getParentBillType());
        responseDTO.setSupplementaryReason(bill.getSupplementaryReason());
        String gstLifecycle = stripBillStatus(bill.getBillStatus());
        if ("ACTIVE".equalsIgnoreCase(gstLifecycle) || gstLifecycle.isEmpty()) {
            gstLifecycle = BillLifecycleStatus.COMPLETED;
        }
        if (!BillLifecycleStatus.isKnown(gstLifecycle)) {
            gstLifecycle = BillLifecycleStatus.COMPLETED;
        }
        responseDTO.setBillLifecycleStatus(gstLifecycle);

        Map<Long, BigDecimal> returnedByLineGst = loadReturnedByLineId(BillKind.GST, bill.getId());

        // Convert items
        List<BillItemDTO> itemDTOs = bill.getItems().stream()
                .map(item -> {
                    BillItemDTO itemDTO = new BillItemDTO();
                    itemDTO.setItemId(item.getId());
                    BigDecimal rtd = returnedByLineGst.getOrDefault(item.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    itemDTO.setQuantityReturnedToDate(rtd.doubleValue());
                    BigDecimal sold = item.getQuantity() != null ? item.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    itemDTO.setQuantityReturnable(sold.subtract(rtd).max(BigDecimal.ZERO).doubleValue());
                    itemDTO.setItemName(item.getProductName());
                    itemDTO.setCategory(item.getProductType());
                    // Use pricePerSqftAfter from product if available, otherwise use stored
                    // pricePerUnit
                    double priceToUse = item.getPricePerUnit().doubleValue();
                    double purchasePrice = 0.0;
                    try {
                        Product product = item.getProduct();
                        if (product != null && product.getPricePerSqftAfter() != null) {
                            // priceToUse = product.getPricePerSqftAfter().doubleValue();
                            purchasePrice = product.getPricePerSqftAfter().doubleValue();
                        }
                    } catch (Exception e) {
                        // If product is lazy-loaded and session is closed, use stored pricePerUnit
                        // This is expected behavior - just use the stored price
                    }
                    itemDTO.setPricePerUnit(priceToUse);
                    itemDTO.setPurchasePrice(purchasePrice);
                    itemDTO.setQuantity(item.getQuantity().doubleValue()); // Changed to doubleValue() to support
                                                                           // decimal quantities
                    itemDTO.setUnit(item.getUnit() != null ? item.getUnit() : "sqft"); // Default for backward
                                                                                       // compatibility
                    itemDTO.setProductImageUrl(item.getProductImageUrl());
                    try {
                        Product product = item.getProduct();
                        if (product != null) {
                            itemDTO.setProductId(product.getId());
                        }
                    } catch (Exception e) {
                        // If product is lazy-loaded and session is closed, skip setting productId
                    }
                    // HSN: persisted on line (from inventory at bill time), then live product, then bill default
                    if (item.getHsnNumber() != null && !item.getHsnNumber().trim().isEmpty()) {
                        itemDTO.setHsnNumber(item.getHsnNumber().trim());
                    } else {
                        try {
                            Product product = item.getProduct();
                            if (product != null && product.getHsnNumber() != null
                                    && !product.getHsnNumber().trim().isEmpty()) {
                                itemDTO.setHsnNumber(product.getHsnNumber().trim());
                            }
                        } catch (Exception e) {
                            // session closed
                        }
                    }
                    if ((itemDTO.getHsnNumber() == null || itemDTO.getHsnNumber().trim().isEmpty())
                            && bill.getHsnCode() != null && !bill.getHsnCode().trim().isEmpty()) {
                        itemDTO.setHsnNumber(bill.getHsnCode().trim());
                    }
                    return itemDTO;
                })
                .collect(Collectors.toList());

        responseDTO.setItems(itemDTOs);

        enrichBillPayments(responseDTO, paymentRows, bill.getTotalAmount(), bill.getPaymentMethod(),
                bill.getPaymentStatus().name(), advanceUsed);
        enrichBillReturnSummaryForGst(bill, responseDTO, returnedByLineGst);
        return responseDTO;
    }

    private BillResponseDTO convertNonGSTToResponseDTO(BillNonGST bill, List<BillPayment> paymentRows,
            BigDecimal advanceUsed) {
        BillResponseDTO responseDTO = new BillResponseDTO();
        responseDTO.setId(bill.getId());
        responseDTO.setBillNumber(bill.getBillNumber());
        responseDTO.setBillType("NON_GST");
        responseDTO.setCustomerMobileNumber(bill.getCustomer().getPhone());
        responseDTO.setCustomerId(bill.getCustomer().getId());
        responseDTO.setCustomerName(bill.getCustomer().getCustomerName());
        responseDTO.setAddress(bill.getCustomer().getAddress());
        responseDTO.setGstin(bill.getCustomer().getGstin());
        responseDTO.setCustomerEmail(bill.getCustomer().getEmail());
        responseDTO.setBillDate(bill.getBillDate());
        responseDTO.setTotalSqft(bill.getTotalSqft().doubleValue());
        responseDTO.setSubtotal(bill.getSubtotal().doubleValue());
        responseDTO.setTaxPercentage(0.0);
        responseDTO.setTaxAmount(0.0);
        responseDTO.setServiceCharge(bill.getServiceCharge().doubleValue());
        responseDTO.setLabourCharge(bill.getLabourCharge().doubleValue());
        responseDTO.setTransportationCharge(bill.getTransportationCharge().doubleValue());
        responseDTO.setOtherExpenses(bill.getOtherExpenses() != null ? bill.getOtherExpenses().doubleValue() : 0.0);
        responseDTO.setDiscountAmount(bill.getDiscountAmount().doubleValue());
        responseDTO.setTotalAmount(bill.getTotalAmount().doubleValue());
        responseDTO.setPaymentStatus(bill.getPaymentStatus().name());
        responseDTO.setNotes(bill.getNotes());
        responseDTO.setCreatedAt(bill.getCreatedAt());
        responseDTO.setCreatedByUserId(bill.getCreatedByUserId());
        responseDTO.setLocation(resolveBillLocation(bill, bill.getCustomer()));
        responseDTO.setBackdated(Boolean.TRUE.equals(bill.getBackdated()));
        responseDTO.setOriginalCreatedAt(bill.getOriginalCreatedAt());
        responseDTO.setBackdateReason(bill.getBackdateReason());
        responseDTO.setBackdateApprovedBy(bill.getBackdateApprovedBy());
        responseDTO.setSupplementaryBill(Boolean.TRUE.equals(bill.getSupplementaryBill()));
        responseDTO.setParentBillId(bill.getParentBillId());
        responseDTO.setParentBillType(bill.getParentBillType());
        responseDTO.setSupplementaryReason(bill.getSupplementaryReason());
        String lifecycle = bill.getBillStatus();
        if (!BillLifecycleStatus.isKnown(lifecycle)) {
            lifecycle = BillLifecycleStatus.COMPLETED;
        }
        responseDTO.setBillLifecycleStatus(lifecycle);

        Map<Long, BigDecimal> returnedByLineNon = loadReturnedByLineId(BillKind.NON_GST, bill.getId());

        // Convert items
        List<BillItemDTO> itemDTOs = bill.getItems().stream()
                .map(item -> {
                    BillItemDTO itemDTO = new BillItemDTO();
                    itemDTO.setItemId(item.getId());
                    BigDecimal rtd = returnedByLineNon.getOrDefault(item.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                    itemDTO.setQuantityReturnedToDate(rtd.doubleValue());
                    BigDecimal sold = item.getQuantity() != null ? item.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    itemDTO.setQuantityReturnable(sold.subtract(rtd).max(BigDecimal.ZERO).doubleValue());
                    itemDTO.setItemName(item.getProductName());
                    itemDTO.setCategory(item.getProductType());
                    // Use pricePerSqftAfter from product if available, otherwise use stored
                    // pricePerUnit
                    double priceToUse = item.getPricePerUnit().doubleValue();
                    double purchasePrice = 0.0;
                    try {
                        Product product = item.getProduct();
                        if (product != null && product.getPricePerSqftAfter() != null) {
                            purchasePrice = product.getPricePerSqftAfter().doubleValue();
                        }
                    } catch (Exception e) {
                        // If product is lazy-loaded and session is closed, use stored pricePerUnit
                        // This is expected behavior - just use the stored price
                    }
                    itemDTO.setPricePerUnit(priceToUse);
                    itemDTO.setPurchasePrice(purchasePrice);
                    itemDTO.setQuantity(item.getQuantity().doubleValue()); // Changed to doubleValue() to support
                                                                           // decimal quantities
                    itemDTO.setUnit(item.getUnit() != null ? item.getUnit() : "sqft"); // Default for backward
                                                                                       // compatibility
                    itemDTO.setProductImageUrl(item.getProductImageUrl());
                    try {
                        Product product = item.getProduct();
                        if (product != null) {
                            itemDTO.setProductId(product.getId());
                            if (product.getHsnNumber() != null && !product.getHsnNumber().trim().isEmpty()) {
                                itemDTO.setHsnNumber(product.getHsnNumber().trim());
                            }
                        }
                    } catch (Exception e) {
                        // If product is lazy-loaded and session is closed, skip setting productId/hsnNumber
                    }
                    return itemDTO;
                })
                .collect(Collectors.toList());

        responseDTO.setItems(itemDTOs);

        enrichBillPayments(responseDTO, paymentRows, bill.getTotalAmount(), bill.getPaymentMethod(),
                bill.getPaymentStatus().name(), advanceUsed);
        enrichBillReturnSummaryForNonGst(bill, responseDTO, returnedByLineNon);
        return responseDTO;
    }

    /**
     * Lightweight list-row mapper for GET /bills — avoids per-line DTO mapping and lazy-loading every bill's items.
     */
    private BillResponseDTO convertNonGSTToListSummaryDTO(
            BillNonGST bill,
            List<BillPayment> paymentRows,
            BigDecimal advanceUsed,
            Map<Long, BigDecimal> returnedByLine,
            BigDecimal cumulativeReturnValue) {
        BillResponseDTO responseDTO = new BillResponseDTO();
        responseDTO.setId(bill.getId());
        responseDTO.setBillNumber(bill.getBillNumber());
        responseDTO.setBillType("NON_GST");
        responseDTO.setCustomerMobileNumber(bill.getCustomer().getPhone());
        responseDTO.setCustomerId(bill.getCustomer().getId());
        responseDTO.setCustomerName(bill.getCustomer().getCustomerName());
        responseDTO.setAddress(bill.getCustomer().getAddress());
        responseDTO.setGstin(bill.getCustomer().getGstin());
        responseDTO.setCustomerEmail(bill.getCustomer().getEmail());
        responseDTO.setBillDate(bill.getBillDate());
        responseDTO.setTotalSqft(bill.getTotalSqft().doubleValue());
        responseDTO.setSubtotal(bill.getSubtotal().doubleValue());
        responseDTO.setTaxPercentage(0.0);
        responseDTO.setTaxAmount(0.0);
        responseDTO.setServiceCharge(bill.getServiceCharge().doubleValue());
        responseDTO.setLabourCharge(bill.getLabourCharge().doubleValue());
        responseDTO.setTransportationCharge(bill.getTransportationCharge().doubleValue());
        responseDTO.setOtherExpenses(bill.getOtherExpenses() != null ? bill.getOtherExpenses().doubleValue() : 0.0);
        responseDTO.setDiscountAmount(bill.getDiscountAmount().doubleValue());
        responseDTO.setTotalAmount(bill.getTotalAmount().doubleValue());
        responseDTO.setPaymentStatus(bill.getPaymentStatus().name());
        responseDTO.setNotes(bill.getNotes());
        responseDTO.setCreatedAt(bill.getCreatedAt());
        responseDTO.setCreatedByUserId(bill.getCreatedByUserId());
        responseDTO.setLocation(resolveBillLocation(bill, bill.getCustomer()));
        responseDTO.setBackdated(Boolean.TRUE.equals(bill.getBackdated()));
        responseDTO.setOriginalCreatedAt(bill.getOriginalCreatedAt());
        responseDTO.setBackdateReason(bill.getBackdateReason());
        responseDTO.setBackdateApprovedBy(bill.getBackdateApprovedBy());
        responseDTO.setSupplementaryBill(Boolean.TRUE.equals(bill.getSupplementaryBill()));
        responseDTO.setParentBillId(bill.getParentBillId());
        responseDTO.setParentBillType(bill.getParentBillType());
        responseDTO.setSupplementaryReason(bill.getSupplementaryReason());
        String lifecycle = bill.getBillStatus();
        if (!BillLifecycleStatus.isKnown(lifecycle)) {
            lifecycle = BillLifecycleStatus.COMPLETED;
        }
        responseDTO.setBillLifecycleStatus(lifecycle);
        responseDTO.setItems(List.of());
        enrichBillPayments(responseDTO, paymentRows, bill.getTotalAmount(), bill.getPaymentMethod(),
                bill.getPaymentStatus().name(), advanceUsed);
        Map<Long, BigDecimal> returned =
                returnedByLine != null ? returnedByLine : loadReturnedByLineId(BillKind.NON_GST, bill.getId());
        enrichBillReturnSummaryForNonGstList(bill, responseDTO, returned, cumulativeReturnValue);
        return responseDTO;
    }

    private void enrichBillReturnSummaryForNonGstList(
            BillNonGST bill,
            BillResponseDTO dto,
            Map<Long, BigDecimal> returnedByLine,
            BigDecimal cumulativeReturnValue) {
        if (bill == null || dto == null || returnedByLine == null) {
            return;
        }
        BigDecimal originalTotal = nz(bill.getTotalAmount());
        BigDecimal cumVal = cumulativeReturnValue;
        if (cumVal == null) {
            cumVal = billInventoryReturnLineRepository.sumLineReturnValueForBill(BillKind.NON_GST, bill.getId());
        }
        if (cumVal == null) {
            cumVal = BigDecimal.ZERO;
        }
        cumVal = cumVal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal effectiveTotal = originalTotal.subtract(cumVal)
                .max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal origQty = nz(bill.getTotalSqft());
        BigDecimal retQty = sumReturnedQuantities(returnedByLine);
        BigDecimal effQty = origQty.subtract(retQty).max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        BigDecimal adv = BigDecimal.valueOf(dto.getAdvanceUsed() != null ? dto.getAdvanceUsed() : 0.0)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = BigDecimal.valueOf(dto.getTotalPaid() != null ? dto.getTotalPaid() : 0.0)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal surplus = adv.add(paid).subtract(effectiveTotal).setScale(2, RoundingMode.HALF_UP);
        dto.setReturnSummary(new BillReturnSummaryDTO(
                origQty.doubleValue(),
                originalTotal.doubleValue(),
                retQty.doubleValue(),
                cumVal.doubleValue(),
                effQty.doubleValue(),
                effectiveTotal.doubleValue(),
                surplus.max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)).doubleValue()));
    }

    private void enrichNonGstBillDetail(BillResponseDTO dto, BillNonGST bill, String location) {
        if (dto == null || bill == null) {
            return;
        }
        String lifecycle = dto.getBillLifecycleStatus();
        if (BillLifecycleStatus.FULLY_RETURNED.equalsIgnoreCase(lifecycle)) {
            dto.setBillLifecycleStatus(BillLifecycleStatus.RETURNED);
        }
        dto.setReturnHistory(buildStockReturnHistory(BillKind.NON_GST, bill.getId(), bill));
        dto.setSupplementaryBills(buildSupplementarySummaries(bill.getId(), location));
        dto.setBillEvents(billEventService.listEvents(BillKind.NON_GST, bill.getId()).stream()
                .map(this::toBillEventResponseDTO)
                .collect(Collectors.toList()));
    }

    private List<BillSupplementarySummaryDTO> buildSupplementarySummaries(Long parentBillId, String location) {
        if (parentBillId == null || location == null) {
            return List.of();
        }
        return billNonGSTRepository.findSupplementaryByParent(parentBillId, BillKind.NON_GST.name(), location.trim())
                .stream()
                .map(b -> new BillSupplementarySummaryDTO(
                        b.getId(),
                        b.getBillNumber(),
                        b.getBillDate(),
                        b.getTotalAmount() != null ? b.getTotalAmount().doubleValue() : 0.0,
                        b.getPaymentStatus() != null ? b.getPaymentStatus().name() : null,
                        b.getSupplementaryReason()))
                .collect(Collectors.toList());
    }

    private List<BillStockReturnHistoryDTO> buildStockReturnHistory(BillKind kind, Long billId, BillNonGST bill) {
        List<BillInventoryReturn> returns =
                billInventoryReturnRepository.findWithLinesByBillKindAndBillId(kind, billId);
        List<BillStockReturnHistoryDTO> out = new ArrayList<>();
        for (BillInventoryReturn ret : returns) {
            BillStockReturnHistoryDTO h = new BillStockReturnHistoryDTO();
            h.setReturnId(ret.getId());
            h.setBillId(ret.getBillId());
            h.setBillKind(kind.name());
            h.setCreatedAt(ret.getCreatedAt());
            h.setNotes(ret.getNotes());
            h.setCreatedByUserId(ret.getCreatedByUserId());
            List<BillStockReturnLineRequestDTO> reqLines = new ArrayList<>();
            if (ret.getLines() != null) {
                for (BillInventoryReturnLine ln : ret.getLines()) {
                    if (ln.getBillItemId() == null || ln.getQuantityReturned() == null) {
                        continue;
                    }
                    h.getLines().add(new BillStockReturnHistoryDTO.BillStockReturnLineHistoryDTO(
                            ln.getBillItemId(), ln.getQuantityReturned().doubleValue()));
                    reqLines.add(new BillStockReturnLineRequestDTO(ln.getBillItemId(), ln.getQuantityReturned().doubleValue()));
                }
            }
            if (!reqLines.isEmpty() && bill != null) {
                BigDecimal computed = computeNonGstStockReturnSettlement(bill, reqLines);
                h.setComputedReturnAmount(computed.doubleValue());
            }
            String txnType = "STOCK_RETURN_" + ret.getId();
            BigDecimal posted = moneyTransactionRepository
                    .findByReferenceTypeAndReferenceIdAndCategoryAndIsDeletedFalse(
                            MoneyReferenceType.bill, billId, MoneyCategory.BILL_RETURN)
                    .stream()
                    .filter(t -> txnType.equals(t.getTxnType()))
                    .map(MoneyTransaction::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            h.setPostedSettlementAmount(posted.doubleValue());
            out.add(h);
        }
        return out;
    }

    private void markNonGstParentAdjustedAfterSupplementary(Long parentBillId, String parentBillType, String location) {
        if (parentBillId == null || !BillKind.NON_GST.name().equalsIgnoreCase(normalizeParentBillType(parentBillType))) {
            return;
        }
        billNonGSTRepository.findById(parentBillId).ifPresent(parent -> {
            if (!Objects.equals(location, resolveBillLocation(parent, parent.getCustomer()))) {
                return;
            }
            String st = stripBillStatus(parent.getBillStatus());
            if (BillLifecycleStatus.CANCELLED.equalsIgnoreCase(st)
                    || BillLifecycleStatus.isReturned(st)
                    || BillLifecycleStatus.SUPERSEDED.equalsIgnoreCase(st)) {
                return;
            }
            parent.setBillStatus(BillLifecycleStatus.ADJUSTED);
            billNonGSTRepository.save(parent);
        });
    }

    private void finalizeNonGstStockReturn(
            BillNonGST bill,
            BillInventoryReturn saved,
            BillReturnRefundMode refundMode,
            BigDecimal computedReturn,
            BigDecimal postedSettlement,
            String location,
            Long actorUserId) {
        persistBillLifecycleAfterStockReturnNonGst(bill);
        recordBillEvent(
                BillKind.NON_GST,
                bill.getId(),
                BillEventType.STOCK_RETURN_RECORDED,
                null,
                "STOCK_RET_" + saved.getId(),
                actorUserId,
                java.util.Map.of(
                        "returnId", saved.getId(),
                        "computedReturn", computedReturn != null ? computedReturn.toPlainString() : "0",
                        "postedSettlement", postedSettlement != null ? postedSettlement.toPlainString() : "0",
                        "refundMode", refundMode != null ? refundMode.name() : "NO_REFUND"));
        if (refundMode == BillReturnRefundMode.ADVANCE_RESTORE && bill.getCustomer() != null) {
            BigDecimal surplus = computeNonGstCustomerSurplusVersusEffective(bill);
            if (surplus.compareTo(PAY_ROUND_EPS) > 0) {
                Long wId = customerAdvanceService.creditAdvanceSurplusRestoreIfAbsent(
                        bill.getCustomer(),
                        surplus,
                        BillKind.NON_GST,
                        bill.getId(),
                        bill.getBillNumber(),
                        saved.getId());
                if (wId != null) {
                    financialLedgerService.recordBillReturnWalletCredit(
                            location != null ? location.trim() : "",
                            bill.getCustomer().getId(),
                            wId,
                            surplus,
                            bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now());
                    recordBillEvent(
                            BillKind.NON_GST,
                            bill.getId(),
                            BillEventType.ADVANCE_RECALCULATED,
                            null,
                            "STOCK_RET_" + saved.getId(),
                            actorUserId,
                            java.util.Map.of("surplusRestored", surplus.toPlainString(), "walletTxnId", wId));
                }
            }
        }
    }

    /** max(0, advanceUsed + cashPaid − effectiveBillTotal) for NON-GST after cumulative returns. */
    private BigDecimal computeNonGstCustomerSurplusVersusEffective(BillNonGST bill) {
        if (bill == null || bill.getId() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.NON_GST, bill.getId());
        BigDecimal originalTotal = nz(bill.getTotalAmount());
        BigDecimal cumVal = sumCumulativeCommercialFromReturnsNonGst(bill);
        BigDecimal effectiveTotal =
                originalTotal.subtract(cumVal).max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, bill.getId())
                .setScale(2, RoundingMode.HALF_UP);
        List<BillPayment> pays = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, bill.getId());
        BigDecimal paid = sumNonAdvancePayments(pays);
        return adv.add(paid).subtract(effectiveTotal).max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private void enrichBillPayments(BillResponseDTO dto, List<BillPayment> paymentRows, BigDecimal billTotal,
            String storedSummary, String paymentStatusName, BigDecimal advanceUsed) {
        BigDecimal adv = advanceUsed != null ? advanceUsed.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = billTotal.setScale(2, RoundingMode.HALF_UP);
        List<BillPayment> nonAdvanceRows = paymentRows == null
                ? List.of()
                : paymentRows.stream().filter(p -> !isAdvancePayment(p)).collect(Collectors.toList());
        BigDecimal paid = sumNonAdvancePayments(nonAdvanceRows);

        boolean inferLegacyFull = adv.compareTo(BigDecimal.ZERO) == 0
                && nonAdvanceRows.isEmpty()
                && storedSummary != null
                && !storedSummary.isBlank()
                && !"-".equals(storedSummary.trim())
                && "PAID".equals(paymentStatusName)
                && paid.compareTo(BigDecimal.ZERO) == 0;
        if (inferLegacyFull) {
            paid = total;
        }

        dto.setAdvanceUsed(adv.doubleValue());
        dto.setTotalPaid(paid.doubleValue());
        dto.setPaidAmount(paid.doubleValue());
        BigDecimal due = total.subtract(adv).subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        dto.setAmountDue(due.doubleValue());
        dto.setPayments(nonAdvanceRows.stream().map(this::toPaymentResponseDTO).collect(Collectors.toList()));

        String summary;
        if (!nonAdvanceRows.isEmpty()) {
            summary = formatSummaryFromPersisted(nonAdvanceRows);
            if (adv.compareTo(BigDecimal.ZERO) > 0) {
                summary = "Adv ₹" + adv.toPlainString() + " + " + summary;
            }
            if (due.compareTo(BigDecimal.ZERO) > 0) {
                summary = summary + " | Due: ₹" + due.toPlainString();
            }
        } else if (inferLegacyFull) {
            summary = storedSummary.trim();
        } else if (storedSummary != null && !storedSummary.isBlank() && !"-".equals(storedSummary.trim())) {
            summary = storedSummary.trim();
        } else {
            summary = "-";
        }
        setPaymentModeFields(dto, summary);
    }

    private void enrichBillReturnSummaryForGst(BillGST bill, BillResponseDTO dto, Map<Long, BigDecimal> returnedByLine) {
        if (bill == null || dto == null || returnedByLine == null) {
            return;
        }
        BigDecimal originalTotal = nz(bill.getTotalAmount());
        BigDecimal cumVal = sumCumulativeCommercialFromReturnsGst(bill);
        BigDecimal effectiveTotal =
                originalTotal.subtract(cumVal).max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal origQty = nz(bill.getTotalSqft());
        BigDecimal retQty = sumReturnedQuantities(returnedByLine);
        BigDecimal effQty = effectiveRemainingQuantityGst(bill, returnedByLine);
        BigDecimal adv = BigDecimal.valueOf(dto.getAdvanceUsed() != null ? dto.getAdvanceUsed() : 0.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = BigDecimal.valueOf(dto.getTotalPaid() != null ? dto.getTotalPaid() : 0.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal surplus = adv.add(paid).subtract(effectiveTotal).setScale(2, RoundingMode.HALF_UP);
        dto.setReturnSummary(new BillReturnSummaryDTO(
                origQty.doubleValue(),
                originalTotal.doubleValue(),
                retQty.doubleValue(),
                cumVal.doubleValue(),
                effQty.doubleValue(),
                effectiveTotal.doubleValue(),
                surplus.max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)).doubleValue()));
    }

    private void enrichBillReturnSummaryForNonGst(BillNonGST bill, BillResponseDTO dto, Map<Long, BigDecimal> returnedByLine) {
        if (bill == null || dto == null || returnedByLine == null) {
            return;
        }
        BigDecimal originalTotal = nz(bill.getTotalAmount());
        BigDecimal cumVal = sumCumulativeCommercialFromReturnsNonGst(bill);
        BigDecimal effectiveTotal =
                originalTotal.subtract(cumVal).max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal origQty = nz(bill.getTotalSqft());
        BigDecimal retQty = sumReturnedQuantities(returnedByLine);
        BigDecimal effQty = effectiveRemainingQuantityNonGst(bill, returnedByLine);
        BigDecimal adv = BigDecimal.valueOf(dto.getAdvanceUsed() != null ? dto.getAdvanceUsed() : 0.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = BigDecimal.valueOf(dto.getTotalPaid() != null ? dto.getTotalPaid() : 0.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal surplus = adv.add(paid).subtract(effectiveTotal).setScale(2, RoundingMode.HALF_UP);
        dto.setReturnSummary(new BillReturnSummaryDTO(
                origQty.doubleValue(),
                originalTotal.doubleValue(),
                retQty.doubleValue(),
                cumVal.doubleValue(),
                effQty.doubleValue(),
                effectiveTotal.doubleValue(),
                surplus.max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)).doubleValue()));
    }

    private BigDecimal sumCumulativeCommercialFromReturnsGst(BillGST bill) {
        if (bill.getId() == null || bill.getItems() == null || bill.getItems().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        List<BillInventoryReturn> inventoryReturns =
                billInventoryReturnRepository.findWithLinesByBillKindAndBillId(BillKind.GST, bill.getId());
        BigDecimal sum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BillInventoryReturn ret : inventoryReturns) {
            if (ret.getLines() == null || ret.getLines().isEmpty()) {
                continue;
            }
            List<BillStockReturnLineRequestDTO> reqLines = new ArrayList<>();
            for (BillInventoryReturnLine ln : ret.getLines()) {
                if (ln.getBillItemId() == null || ln.getQuantityReturned() == null) {
                    continue;
                }
                if (ln.getQuantityReturned().compareTo(PAY_ROUND_EPS) <= 0) {
                    continue;
                }
                reqLines.add(new BillStockReturnLineRequestDTO(ln.getBillItemId(), ln.getQuantityReturned().doubleValue()));
            }
            if (!reqLines.isEmpty()) {
                sum = sum.add(computeGstStockReturnSettlement(bill, reqLines));
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumCumulativeCommercialFromReturnsNonGst(BillNonGST bill) {
        if (bill.getId() == null || bill.getItems() == null || bill.getItems().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        List<BillInventoryReturn> inventoryReturns =
                billInventoryReturnRepository.findWithLinesByBillKindAndBillId(BillKind.NON_GST, bill.getId());
        BigDecimal sum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BillInventoryReturn ret : inventoryReturns) {
            if (ret.getLines() == null || ret.getLines().isEmpty()) {
                continue;
            }
            List<BillStockReturnLineRequestDTO> reqLines = new ArrayList<>();
            for (BillInventoryReturnLine ln : ret.getLines()) {
                if (ln.getBillItemId() == null || ln.getQuantityReturned() == null) {
                    continue;
                }
                if (ln.getQuantityReturned().compareTo(PAY_ROUND_EPS) <= 0) {
                    continue;
                }
                reqLines.add(new BillStockReturnLineRequestDTO(ln.getBillItemId(), ln.getQuantityReturned().doubleValue()));
            }
            if (!reqLines.isEmpty()) {
                sum = sum.add(computeNonGstStockReturnSettlement(bill, reqLines));
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumReturnedQuantities(Map<Long, BigDecimal> returnedByLine) {
        BigDecimal s = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BigDecimal v : returnedByLine.values()) {
            s = s.add(nz(v));
        }
        return s.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal effectiveRemainingQuantityGst(BillGST bill, Map<Long, BigDecimal> returnedByLine) {
        if (bill.getItems() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BillItemGST line : bill.getItems()) {
            BigDecimal sold = nz(line.getQuantity());
            BigDecimal ret = nz(returnedByLine.get(line.getId()));
            sum = sum.add(sold.subtract(ret).max(BigDecimal.ZERO));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal effectiveRemainingQuantityNonGst(BillNonGST bill, Map<Long, BigDecimal> returnedByLine) {
        if (bill.getItems() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BillItemNonGST line : bill.getItems()) {
            BigDecimal sold = nz(line.getQuantity());
            BigDecimal ret = nz(returnedByLine.get(line.getId()));
            sum = sum.add(sold.subtract(ret).max(BigDecimal.ZERO));
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static String resolveBillLocation(BillGST bill, Customer customer) {
        if (bill != null && bill.getLocation() != null && !bill.getLocation().isBlank()) {
            return bill.getLocation().trim();
        }
        if (customer != null && customer.getLocation() != null && !customer.getLocation().isBlank()) {
            return customer.getLocation().trim();
        }
        return null;
    }

    private static String resolveBillLocation(BillNonGST bill, Customer customer) {
        if (bill != null && bill.getLocation() != null && !bill.getLocation().isBlank()) {
            return bill.getLocation().trim();
        }
        if (customer != null && customer.getLocation() != null && !customer.getLocation().isBlank()) {
            return customer.getLocation().trim();
        }
        return null;
    }

  /** Exposed for cancellation preview (remaining qty to restore per line). */
    public Map<Long, BigDecimal> getReturnedQuantitiesByLine(BillKind kind, Long billId) {
        return loadReturnedByLineId(kind, billId);
    }

    private Map<Long, BigDecimal> loadReturnedByLineId(BillKind kind, Long billId) {
        Map<Long, Map<Long, BigDecimal>> batch = loadReturnedByLineIdBatch(kind, List.of(billId));
        return batch.getOrDefault(billId, Map.of());
    }

    private Map<Long, Map<Long, BigDecimal>> loadReturnedByLineIdBatch(BillKind kind, List<Long> billIds) {
        if (billIds == null || billIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, BigDecimal>> out = new HashMap<>();
        for (Object[] row : billInventoryReturnLineRepository.sumReturnedQuantityGroupedByBillIds(kind, billIds)) {
            if (row == null || row.length < 3 || row[0] == null || row[1] == null) {
                continue;
            }
            Long billId = ((Number) row[0]).longValue();
            Long itemId = ((Number) row[1]).longValue();
            BigDecimal sum = row[2] instanceof BigDecimal bd
                    ? bd
                    : BigDecimal.valueOf(((Number) row[2]).doubleValue());
            out.computeIfAbsent(billId, k -> new HashMap<>())
                    .put(itemId, sum.setScale(2, RoundingMode.HALF_UP));
        }
        return out;
    }

    private Map<Long, BigDecimal> loadCumulativeReturnValueByBillBatch(BillKind kind, List<Long> billIds) {
        if (billIds == null || billIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> out = new HashMap<>();
        for (Object[] row : billInventoryReturnLineRepository.sumLineReturnValueGroupedByBillIds(kind, billIds)) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            Long billId = ((Number) row[0]).longValue();
            BigDecimal sum = row[1] instanceof BigDecimal bd
                    ? bd
                    : BigDecimal.valueOf(((Number) row[1]).doubleValue());
            out.put(billId, sum.setScale(2, RoundingMode.HALF_UP));
        }
        return out;
    }

    private static BillItemGST findGstLine(BillGST bill, Long billItemId) {
        return bill.getItems().stream()
                .filter(i -> i.getId() != null && i.getId().equals(billItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("GST bill line not found: billItemId=" + billItemId));
    }

    private static BillItemNonGST findNonGstLine(BillNonGST bill, Long billItemId) {
        return bill.getItems().stream()
                .filter(i -> i.getId() != null && i.getId().equals(billItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Non-GST bill line not found: billItemId=" + billItemId));
    }

    private void applyStockReturnForGstLine(BillItemGST line, Long billId, BigDecimal qty, String note, String billLocation,
            LocalDate stockLedgerDate) {
        applyStockReturnForGstLine(line, billId, qty, note, billLocation, stockLedgerDate, null, null, null);
    }

    private void applyStockReturnForGstLine(BillItemGST line, Long billId, BigDecimal qty, String note, String billLocation,
            LocalDate stockLedgerDate, String linkedGroupId, Long billVersionId) {
        applyStockReturnForGstLine(line, billId, qty, note, billLocation, stockLedgerDate, linkedGroupId, billVersionId, null);
    }

    private void applyStockReturnForGstLine(BillItemGST line, Long billId, BigDecimal qty, String note, String billLocation,
            LocalDate stockLedgerDate, String linkedGroupId, Long billVersionId, String inventorySourceAction) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal q = qty.setScale(2, RoundingMode.HALF_UP);
        LocalDate d = stockLedgerDate != null ? stockLedgerDate : LocalDate.now();
        String src = inventorySourceAction != null ? inventorySourceAction : "BILL_REVERSAL";
        if (line.getProduct() != null && line.getProduct().getId() != null) {
            productService.recordBillStockReturn(line.getProduct().getId(), q, billId, BillKind.GST, note, billLocation, d,
                    billVersionId, null, linkedGroupId, src);
        } else {
            productService.recordBillStockReturnByName(line.getProductName(), q, billId, BillKind.GST, note, billLocation, d,
                    billVersionId, null, linkedGroupId, src);
        }
    }

    private void applyStockReturnForNonGstLine(BillItemNonGST line, Long billId, BigDecimal qty, String note, String billLocation,
            LocalDate stockLedgerDate) {
        applyStockReturnForNonGstLine(line, billId, qty, note, billLocation, stockLedgerDate, null, null, null);
    }

    private void applyStockReturnForNonGstLine(BillItemNonGST line, Long billId, BigDecimal qty, String note, String billLocation,
            LocalDate stockLedgerDate, String linkedGroupId, Long billVersionId) {
        applyStockReturnForNonGstLine(line, billId, qty, note, billLocation, stockLedgerDate, linkedGroupId, billVersionId, null);
    }

    private void applyStockReturnForNonGstLine(BillItemNonGST line, Long billId, BigDecimal qty, String note, String billLocation,
            LocalDate stockLedgerDate, String linkedGroupId, Long billVersionId, String inventorySourceAction) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal q = qty.setScale(2, RoundingMode.HALF_UP);
        LocalDate d = stockLedgerDate != null ? stockLedgerDate : LocalDate.now();
        String src = inventorySourceAction != null ? inventorySourceAction : "BILL_REVERSAL";
        if (line.getProduct() != null && line.getProduct().getId() != null) {
            productService.recordBillStockReturn(line.getProduct().getId(), q, billId, BillKind.NON_GST, note, billLocation, d,
                    billVersionId, null, linkedGroupId, src);
        } else {
            productService.recordBillStockReturnByName(line.getProductName(), q, billId, BillKind.NON_GST, note, billLocation, d,
                    billVersionId, null, linkedGroupId, src);
        }
    }

    /**
     * Difference-engine increase: reserve the extra quantity, append SALE/OUT, consume reservation (same pattern as bill replace).
     */
    private void applyBillLineQuantityIncreaseStock(
            Long productId,
            String productNameFallback,
            BigDecimal addQty,
            Long billId,
            BillKind billKind,
            String notes,
            String billLocation,
            LocalDate stockDate) {
        if (addQty == null || addQty.compareTo(PAY_ROUND_EPS) <= 0) {
            return;
        }
        BigDecimal q = addQty.setScale(2, RoundingMode.HALF_UP);
        Map<Long, BigDecimal> byId = new HashMap<>();
        Map<String, BigDecimal> byName = new HashMap<>();
        if (productId != null) {
            byId.put(productId, q);
            productService.validateStockAvailability(productId, q);
        } else if (productNameFallback != null && !productNameFallback.isBlank()) {
            byName.put(productNameFallback, q);
            productService.validateStockAvailabilityByName(productNameFallback, q);
        } else {
            return;
        }
        inventoryReservationService.reserveForBill(billId, billKind, byId, byName, billLocation);
        try {
            LocalDate d = stockDate != null ? stockDate : LocalDate.now();
            if (productId != null) {
                productService.deductStock(productId, q, billId, notes, billKind, d, null, null, null,
                        INV_SRC_BILL_LINE_QTY_INCREASE_SALE);
            } else {
                productService.deductStockByName(productNameFallback, q, billId, notes, billKind, d, null, null, null,
                        INV_SRC_BILL_LINE_QTY_INCREASE_SALE);
            }
            inventoryReservationService.consumeForBill(billId, billKind);
        } catch (RuntimeException ex) {
            inventoryReservationService.releaseForBill(billId, billKind);
            throw ex;
        }
    }

    /**
     * Case D — new bill lines: merged reserve → SALE/OUT per product → consume (one batch per request).
     */
    private void applyMergedNewLineSaleStock(
            Long billId,
            BillKind billKind,
            Map<Long, BigDecimal> quantitiesByProductId,
            Map<String, BigDecimal> quantitiesByProductName,
            String billLocation,
            LocalDate stockDate,
            String notes) {
        Map<Long, BigDecimal> idMap = new HashMap<>();
        Map<String, BigDecimal> nameMap = new HashMap<>();
        if (quantitiesByProductId != null) {
            for (Map.Entry<Long, BigDecimal> e : quantitiesByProductId.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && e.getValue().compareTo(PAY_ROUND_EPS) > 0) {
                    idMap.put(e.getKey(), e.getValue().setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
        if (quantitiesByProductName != null) {
            for (Map.Entry<String, BigDecimal> e : quantitiesByProductName.entrySet()) {
                if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null
                        && e.getValue().compareTo(PAY_ROUND_EPS) > 0) {
                    nameMap.put(e.getKey(), e.getValue().setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
        if (idMap.isEmpty() && nameMap.isEmpty()) {
            return;
        }
        for (Long pid : idMap.keySet()) {
            productService.validateStockAvailability(pid, idMap.get(pid));
        }
        for (String name : nameMap.keySet()) {
            productService.validateStockAvailabilityByName(name, nameMap.get(name));
        }
        inventoryReservationService.reserveForBill(billId, billKind, idMap, nameMap, billLocation);
        try {
            LocalDate d = stockDate != null ? stockDate : LocalDate.now();
            for (Map.Entry<Long, BigDecimal> e : idMap.entrySet()) {
                productService.deductStock(e.getKey(), e.getValue(), billId, notes, billKind, d, null, null, null,
                        INV_SRC_BILL_LINE_NEW_ITEM_SALE);
            }
            for (Map.Entry<String, BigDecimal> e : nameMap.entrySet()) {
                productService.deductStockByName(e.getKey(), e.getValue(), billId, notes, billKind, d, null, null, null,
                        INV_SRC_BILL_LINE_NEW_ITEM_SALE);
            }
            inventoryReservationService.consumeForBill(billId, billKind);
        } catch (RuntimeException ex) {
            inventoryReservationService.releaseForBill(billId, billKind);
            throw ex;
        }
    }

    private static void mergeBillItemDtoIntoStockMaps(BillItemDTO dto, Map<Long, BigDecimal> byId, Map<String, BigDecimal> byName) {
        if (dto == null || dto.getQuantity() == null) {
            return;
        }
        BigDecimal q = BigDecimal.valueOf(dto.getQuantity()).setScale(2, RoundingMode.HALF_UP);
        if (q.compareTo(PAY_ROUND_EPS) <= 0) {
            return;
        }
        if (dto.getProductId() != null) {
            byId.merge(dto.getProductId(), q, BigDecimal::add);
        } else if (dto.getItemName() != null && !dto.getItemName().isBlank()) {
            byName.merge(dto.getItemName(), q, BigDecimal::add);
        }
    }

    private void appendGstLineFromBillItemDto(BillGST bill, BillItemDTO itemDTO) {
        BillItemGST item = new BillItemGST();
        item.setProductName(itemDTO.getItemName());
        item.setProductType(itemDTO.getCategory() != null && !itemDTO.getCategory().isBlank() ? itemDTO.getCategory().trim() : "General");
        item.setPricePerUnit(BigDecimal.valueOf(itemDTO.getPricePerUnit()).setScale(2, RoundingMode.HALF_UP));
        item.setQuantity(BigDecimal.valueOf(itemDTO.getQuantity()).setScale(2, RoundingMode.HALF_UP));
        item.setItemTotalPrice(BigDecimal.valueOf(itemDTO.getPricePerUnit())
                .multiply(BigDecimal.valueOf(itemDTO.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP));
        if (itemDTO.getProductImageUrl() != null) {
            item.setProductImageUrl(itemDTO.getProductImageUrl());
        }
        Product product = null;
        if (itemDTO.getProductId() != null) {
            product = productService.getProductEntityById(itemDTO.getProductId());
            item.setProduct(product);
        } else if (itemDTO.getItemName() != null) {
            try {
                product = productService.getProductEntityByName(itemDTO.getItemName());
                item.setProduct(product);
            } catch (RuntimeException ignored) {
                // free-text line without catalog match
            }
        }
        if (product != null && product.getHsnNumber() != null && !product.getHsnNumber().isBlank()) {
            String invHsn = product.getHsnNumber().trim();
            item.setHsnNumber(invHsn);
            if (trimToNull(bill.getHsnCode()) == null) {
                bill.setHsnCode(invHsn);
            }
        }
        if (product != null && product.getUnit() != null && !product.getUnit().trim().isEmpty()) {
            item.setUnit(product.getUnit());
        } else if (itemDTO.getUnit() != null && !itemDTO.getUnit().trim().isEmpty()) {
            item.setUnit(itemDTO.getUnit());
        } else {
            item.setUnit("sqft");
        }
        bill.addItem(item);
    }

    private void appendNonGstLineFromBillItemDto(BillNonGST bill, BillItemDTO itemDTO) {
        BillItemNonGST item = new BillItemNonGST();
        item.setProductName(itemDTO.getItemName());
        item.setProductType(itemDTO.getCategory() != null && !itemDTO.getCategory().isBlank() ? itemDTO.getCategory().trim() : "General");
        item.setPricePerUnit(BigDecimal.valueOf(itemDTO.getPricePerUnit()).setScale(2, RoundingMode.HALF_UP));
        item.setQuantity(BigDecimal.valueOf(itemDTO.getQuantity()).setScale(2, RoundingMode.HALF_UP));
        item.setItemTotalPrice(BigDecimal.valueOf(itemDTO.getPricePerUnit())
                .multiply(BigDecimal.valueOf(itemDTO.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP));
        if (itemDTO.getProductImageUrl() != null) {
            item.setProductImageUrl(itemDTO.getProductImageUrl());
        }
        Product product = null;
        if (itemDTO.getProductId() != null) {
            product = productService.getProductEntityById(itemDTO.getProductId());
            item.setProduct(product);
        } else if (itemDTO.getItemName() != null) {
            try {
                product = productService.getProductEntityByName(itemDTO.getItemName());
                item.setProduct(product);
            } catch (RuntimeException ignored) {
            }
        }
        if (product != null && product.getUnit() != null && !product.getUnit().trim().isEmpty()) {
            item.setUnit(product.getUnit());
        } else if (itemDTO.getUnit() != null && !itemDTO.getUnit().trim().isEmpty()) {
            item.setUnit(itemDTO.getUnit());
        } else {
            item.setUnit("sqft");
        }
        bill.addItem(item);
    }

    private static BillStockReturnResponseDTO toStockReturnResponse(BillInventoryReturn saved) {
        BillStockReturnResponseDTO dto = new BillStockReturnResponseDTO();
        dto.setReturnId(saved.getId());
        dto.setBillKind(saved.getBillKind().name());
        dto.setBillId(saved.getBillId());
        dto.setCreatedAt(saved.getCreatedAt());
        if (saved.getLines() != null) {
            for (BillInventoryReturnLine rl : saved.getLines()) {
                dto.getLines().add(new BillStockReturnResponseDTO.BillStockReturnLineResponseDTO(
                        rl.getBillItemId(),
                        rl.getQuantityReturned() != null ? rl.getQuantityReturned().doubleValue() : 0.0));
            }
        }
        return dto;
    }

    private BillStockReturnResponseDTO embellishStockReturnResponse(
            BillInventoryReturn saved, BigDecimal computedReturn, BigDecimal postedSettlement, BillReturnRefundMode refundMode) {
        BillStockReturnResponseDTO dto = toStockReturnResponse(saved);
        dto.setComputedReturnAmount(computedReturn != null ? computedReturn.setScale(2, RoundingMode.HALF_UP) : null);
        dto.setPostedSettlementAmount(postedSettlement != null ? postedSettlement.setScale(2, RoundingMode.HALF_UP) : null);
        dto.setRefundMode(refundMode);
        return dto;
    }

    private void persistBillLifecycleAfterStockReturnGst(BillGST bill) {
        boolean full = isGstBillFullyStockReturned(bill.getId(), bill);
        bill.setBillStatus(full ? BillLifecycleStatus.FULLY_RETURNED : BillLifecycleStatus.PARTIALLY_RETURNED);
        billGSTRepository.save(bill);
    }

    private void persistBillLifecycleAfterStockReturnNonGst(BillNonGST bill) {
        boolean full = isNonGstBillFullyStockReturnedByInventory(bill.getId(), bill);
        bill.setBillStatus(full ? BillLifecycleStatus.RETURNED : BillLifecycleStatus.PARTIALLY_RETURNED);
        billNonGSTRepository.save(bill);
    }

    private BigDecimal computeGstStockReturnSettlement(BillGST bill, List<BillStockReturnLineRequestDTO> lineReqs) {
        BigDecimal lineReturnSubtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BillStockReturnLineRequestDTO ln : lineReqs) {
            BillItemGST line = findGstLine(bill, ln.getBillItemId());
            BigDecimal sold = nz(line.getQuantity());
            BigDecimal ret = BigDecimal.valueOf(ln.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = nz(line.getItemTotalPrice());
            BigDecimal portion = lineTotal.multiply(ret).divide(sold, 2, RoundingMode.HALF_UP);
            lineReturnSubtotal = lineReturnSubtotal.add(portion);
        }
        BigDecimal sub = nz(bill.getSubtotal());
        if (sub.compareTo(PAY_ROUND_EPS) <= 0) {
            return lineReturnSubtotal.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal taxShare = nz(bill.getTaxAmount()).multiply(lineReturnSubtotal).divide(sub, 2, RoundingMode.HALF_UP);
        BigDecimal discShare = nz(bill.getDiscountAmount()).multiply(lineReturnSubtotal).divide(sub, 2, RoundingMode.HALF_UP);
        return lineReturnSubtotal.add(taxShare).subtract(discShare).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeNonGstStockReturnSettlement(BillNonGST bill, List<BillStockReturnLineRequestDTO> lineReqs) {
        BigDecimal lineReturnSubtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (BillStockReturnLineRequestDTO ln : lineReqs) {
            BillItemNonGST line = findNonGstLine(bill, ln.getBillItemId());
            BigDecimal sold = nz(line.getQuantity());
            BigDecimal ret = BigDecimal.valueOf(ln.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = nz(line.getItemTotalPrice());
            BigDecimal portion = lineTotal.multiply(ret).divide(sold, 2, RoundingMode.HALF_UP);
            lineReturnSubtotal = lineReturnSubtotal.add(portion);
        }
        BigDecimal sub = nz(bill.getSubtotal());
        if (sub.compareTo(PAY_ROUND_EPS) <= 0) {
            return lineReturnSubtotal.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal discShare = nz(bill.getDiscountAmount()).multiply(lineReturnSubtotal).divide(sub, 2, RoundingMode.HALF_UP);
        return lineReturnSubtotal.subtract(discShare).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static String mapReturnRefundModeToStorage(BillReturnRefundMode mode, String legacyRail) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case CASH_REFUND -> {
                String rail = legacyRail != null ? legacyRail.trim().toUpperCase() : "CASH";
                yield rail.contains("UPI") ? "UPI" : "CASH";
            }
            case BANK_REFUND -> "BANK_TRANSFER";
            case WALLET_CREDIT, ADVANCE_RESTORE -> "ADVANCE";
            default -> null;
        };
    }

    private static BillReturnRefundMode resolveStockReturnRefundMode(BillStockReturnRequestDTO req) {
        if (req.getRefundMode() != null) {
            return req.getRefundMode();
        }
        BigDecimal ra = req.getRefundAmount();
        if (ra != null && ra.compareTo(PAY_ROUND_EPS) > 0) {
            if (req.getRefundPaymentMode() == null || req.getRefundPaymentMode().isBlank()) {
                throw new IllegalArgumentException("refundPaymentMode is required when refundAmount is set without refundMode");
            }
            BillPaymentMode m = parseBillPaymentMode(req.getRefundPaymentMode());
            if (m == BillPaymentMode.BANK_TRANSFER || m == BillPaymentMode.CHEQUE) {
                return BillReturnRefundMode.BANK_REFUND;
            }
            return BillReturnRefundMode.CASH_REFUND;
        }
        return BillReturnRefundMode.NO_REFUND;
    }

    private static BigDecimal resolvePostedReturnSettlement(BillReturnRefundMode mode,
            BigDecimal computed,
            BillStockReturnRequestDTO request) {
        if (mode == BillReturnRefundMode.NO_REFUND) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        boolean legacy = request.getRefundMode() == null
                && request.getRefundAmount() != null
                && request.getRefundAmount().compareTo(PAY_ROUND_EPS) > 0;
        if (legacy) {
            return request.getRefundAmount().setScale(2, RoundingMode.HALF_UP);
        }
        return computed != null ? computed.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Cash/bank payout or wallet credit for a documented stock return (never EXPENSE category).
     */
    private void applyBillReturnFinancialSettlement(
            BillKind kind,
            Long billId,
            Long stockReturnId,
            BillReturnRefundMode mode,
            BigDecimal settlementAmount,
            String legacyRefundPaymentModeRaw,
            String billNumber,
            String location,
            Customer customer,
            Long actorUserId,
            LocalDate billBusinessDate) {
        if (mode == null || mode == BillReturnRefundMode.NO_REFUND
                || settlementAmount == null
                || settlementAmount.compareTo(PAY_ROUND_EPS) <= 0
                || stockReturnId == null
                || billId == null) {
            return;
        }
        if (mode == BillReturnRefundMode.WALLET_CREDIT) {
            if (customer == null || customer.getId() == null) {
                throw new IllegalArgumentException("Customer is required for WALLET_CREDIT return settlement");
            }
            LocalDate eventDate = billBusinessDate != null ? billBusinessDate : LocalDate.now();
            Long wId = customerAdvanceService.creditBillReturnToWalletIfAbsent(
                    customer,
                    settlementAmount,
                    kind,
                    billId,
                    billNumber,
                    stockReturnId);
            if (wId != null) {
                financialLedgerService.recordBillReturnWalletCredit(
                        location != null ? location.trim() : "", customer.getId(), wId, settlementAmount, eventDate);
            }
            return;
        }
        if (mode == BillReturnRefundMode.ADVANCE_RESTORE) {
            return;
        }
        String txnType = "STOCK_RETURN_" + stockReturnId;
        if (moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndTxnTypeAndIsDeletedFalse(
                MoneyReferenceType.bill, billId, txnType)) {
            return;
        }
        BigDecimal amount = settlementAmount.setScale(2, RoundingMode.HALF_UP);
        MoneyPaymentMode paymentMode;
        if (mode == BillReturnRefundMode.BANK_REFUND) {
            paymentMode = MoneyPaymentMode.BANK;
        } else {
            String rawRail = legacyRefundPaymentModeRaw != null && !legacyRefundPaymentModeRaw.isBlank()
                    ? legacyRefundPaymentModeRaw.trim()
                    : "CASH";
            BillPaymentMode billMode = parseBillPaymentMode(rawRail);
            if (billMode == BillPaymentMode.BANK_TRANSFER || billMode == BillPaymentMode.CHEQUE) {
                paymentMode = MoneyPaymentMode.BANK;
            } else {
                paymentMode = mapPaymentMode(billMode.name());
            }
        }
        String partyName = customer != null && customer.getCustomerName() != null && !customer.getCustomerName().isBlank()
                ? customer.getCustomerName().trim()
                : (customer != null && customer.getId() != null ? ("Customer_" + customer.getId()) : "Customer_Unknown");
        MoneyTransaction tx = new MoneyTransaction();
        tx.setAmount(amount);
        tx.setDirection(MoneyDirection.OUT);
        tx.setCategory(MoneyCategory.BILL_RETURN);
        tx.setSubCategory(MoneyLedgerCategories.SUB_CUSTOMER_REFUND);
        tx.setTxnType(txnType);
        tx.setPartyId(customer != null ? customer.getId() : null);
        tx.setPartyName(partyName);
        tx.setPaymentMode(paymentMode);
        tx.setReferenceType(MoneyReferenceType.bill);
        tx.setReferenceId(billId);
        tx.setNotes("Stock return settlement | BillNo: " + (billNumber != null ? billNumber : ("#" + billId))
                + " | returnId=" + stockReturnId + " | mode=" + mode);
        tx.setTransactionDate(billBusinessDate != null ? billBusinessDate : LocalDate.now());
        tx.setDateTime(LocalDateTime.now());
        tx.setLocation(location != null ? location.trim() : "");
        tx.setOwnerUserId(actorUserId);
        tx.setStatus(MoneyTxnStatus.ACTIVE);
        tx.setIsDeleted(false);
        billInventoryReturnRepository.findById(stockReturnId).ifPresent(ret -> {
            if (ret.getAdjustmentGroupId() != null && !ret.getAdjustmentGroupId().isBlank()) {
                tx.setAdjustmentGroupId(ret.getAdjustmentGroupId().trim());
                tx.setLinkedGroupId(ret.getAdjustmentGroupId().trim());
            }
        });
        moneyTransactionRepository.save(tx);
    }

    private static BigDecimal nz(BigDecimal b) {
        return b != null ? b.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private void recomputeGstAmountsFromItems(BillGST bill) {
        List<BillItemGST> items = bill.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Bill has no line items");
        }
        BigDecimal totalSqft = BigDecimal.ZERO;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (BillItemGST item : items) {
            BigDecimal q = nz(item.getQuantity());
            if (q.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Each line quantity must be positive");
            }
            BigDecimal p = nz(item.getPricePerUnit());
            totalSqft = totalSqft.add(q);
            subtotal = subtotal.add(p.multiply(q));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxRate = bill.getTaxRate() != null ? bill.getTaxRate() : BigDecimal.ZERO;
        BigDecimal taxAmount = subtotal.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(taxAmount)
                .add(nz(bill.getServiceCharge()))
                .add(nz(bill.getLabourCharge()))
                .add(nz(bill.getTransportationCharge()))
                .add(nz(bill.getOtherExpenses()))
                .subtract(nz(bill.getDiscountAmount()));
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        bill.setTotalSqft(totalSqft.setScale(2, RoundingMode.HALF_UP));
        bill.setSubtotal(subtotal);
        bill.setTaxAmount(taxAmount);
        bill.setTotalAmount(totalAmount);
    }

    private void recomputeNonGstAmountsFromItems(BillNonGST bill) {
        List<BillItemNonGST> items = bill.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Bill has no line items");
        }
        BigDecimal totalSqft = BigDecimal.ZERO;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (BillItemNonGST item : items) {
            BigDecimal q = nz(item.getQuantity());
            if (q.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Each line quantity must be positive");
            }
            BigDecimal p = nz(item.getPricePerUnit());
            totalSqft = totalSqft.add(q);
            subtotal = subtotal.add(p.multiply(q));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(nz(bill.getServiceCharge()))
                .add(nz(bill.getLabourCharge()))
                .add(nz(bill.getTransportationCharge()))
                .add(nz(bill.getOtherExpenses()))
                .subtract(nz(bill.getDiscountAmount()));
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        bill.setTotalSqft(totalSqft.setScale(2, RoundingMode.HALF_UP));
        bill.setSubtotal(subtotal);
        bill.setTotalAmount(totalAmount);
    }

    private static String stripBillStatus(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean isBillStatusLocked(String billStatus) {
        return BillLifecycleStatus.LOCKED.equalsIgnoreCase(stripBillStatus(billStatus));
    }

    private static boolean isLifecycleExchanged(String billStatus) {
        return BillLifecycleStatus.EXCHANGED.equalsIgnoreCase(stripBillStatus(billStatus));
    }

    private boolean isGstBillFullyStockReturned(Long billId, BillGST bill) {
        if (bill.getItems() == null || bill.getItems().isEmpty()) {
            return false;
        }
        Map<Long, BigDecimal> ret = loadReturnedByLineId(BillKind.GST, billId);
        for (BillItemGST line : bill.getItems()) {
            BigDecimal sold = line.getQuantity() != null ? line.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal r = ret.getOrDefault(line.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (sold.subtract(r).compareTo(PAY_ROUND_EPS) > 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isNonGstBillFullyStockReturnedByInventory(Long billId, BillNonGST bill) {
        if (bill.getItems() == null || bill.getItems().isEmpty()) {
            return false;
        }
        Map<Long, BigDecimal> ret = loadReturnedByLineId(BillKind.NON_GST, billId);
        for (BillItemNonGST line : bill.getItems()) {
            BigDecimal sold = line.getQuantity() != null ? line.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal r = ret.getOrDefault(line.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (sold.subtract(r).compareTo(PAY_ROUND_EPS) > 0) {
                return false;
            }
        }
        return true;
    }

    private void assertGstBillMutableForEdits(BillGST bill) {
        if (Boolean.TRUE.equals(bill.getIsDeleted())) {
            throw new IllegalArgumentException("Cannot edit a deleted bill");
        }
        if (Boolean.FALSE.equals(bill.getLatestVersion())) {
            throw new IllegalArgumentException("Cannot edit a superseded bill version");
        }
        if (bill.getPaymentStatus() == BillGST.PaymentStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot edit a cancelled bill");
        }
        String st = stripBillStatus(bill.getBillStatus());
        if ("CANCELLED".equalsIgnoreCase(st) || BillLifecycleStatus.CANCELLED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Cannot edit a cancelled bill");
        }
        if ("SUPERSEDED".equalsIgnoreCase(st) || BillLifecycleStatus.SUPERSEDED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Cannot edit a superseded bill");
        }
        if ("FULLY_RETURNED".equalsIgnoreCase(st) || BillLifecycleStatus.FULLY_RETURNED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Cannot edit a fully returned bill");
        }
        if (isLifecycleExchanged(bill.getBillStatus())) {
            throw new IllegalArgumentException("Cannot edit an exchanged bill");
        }
        if (isBillStatusLocked(bill.getBillStatus())) {
            throw new IllegalArgumentException("Cannot edit a locked bill");
        }
        if (isGstBillFullyStockReturned(bill.getId(), bill)) {
            throw new IllegalArgumentException("Cannot edit a bill with all lines fully returned to stock");
        }
    }

    private void assertNonGstBillMutableForEdits(BillNonGST bill) {
        if (Boolean.TRUE.equals(bill.getIsDeleted())) {
            throw new IllegalArgumentException("Cannot edit a deleted bill");
        }
        if (Boolean.FALSE.equals(bill.getLatestVersion())) {
            throw new IllegalArgumentException("Cannot edit a superseded bill version");
        }
        if (bill.getPaymentStatus() == BillNonGST.PaymentStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot edit a cancelled bill");
        }
        String st = stripBillStatus(bill.getBillStatus());
        if (BillLifecycleStatus.CANCELLED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Cannot edit a cancelled bill");
        }
        if (BillLifecycleStatus.SUPERSEDED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Cannot edit a superseded bill");
        }
        if (BillLifecycleStatus.FULLY_RETURNED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Cannot edit a fully returned bill");
        }
        if (isLifecycleExchanged(bill.getBillStatus())) {
            throw new IllegalArgumentException("Cannot edit an exchanged bill");
        }
        if (isBillStatusLocked(bill.getBillStatus())) {
            throw new IllegalArgumentException("Cannot edit a locked bill");
        }
        if (isNonGstBillFullyStockReturnedByInventory(bill.getId(), bill)) {
            throw new IllegalArgumentException("Cannot edit a bill with all lines fully returned to stock");
        }
    }

    private void assertParentGstBillAllowsSupplementary(BillGST parent) {
        if (Boolean.TRUE.equals(parent.getIsDeleted())) {
            throw new IllegalArgumentException("Parent bill is deleted");
        }
        if (Boolean.FALSE.equals(parent.getLatestVersion())) {
            throw new IllegalArgumentException("Parent bill is not the latest version");
        }
        if (parent.getPaymentStatus() == BillGST.PaymentStatus.CANCELLED) {
            throw new IllegalArgumentException("Parent bill is cancelled");
        }
        String st = stripBillStatus(parent.getBillStatus());
        if ("CANCELLED".equalsIgnoreCase(st) || BillLifecycleStatus.CANCELLED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Parent bill is cancelled");
        }
        if ("SUPERSEDED".equalsIgnoreCase(st) || BillLifecycleStatus.SUPERSEDED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Parent bill is superseded");
        }
        if ("FULLY_RETURNED".equalsIgnoreCase(st) || BillLifecycleStatus.FULLY_RETURNED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Parent bill is fully returned");
        }
        if (isLifecycleExchanged(parent.getBillStatus())) {
            throw new IllegalArgumentException("Parent bill is exchanged");
        }
        if (isBillStatusLocked(parent.getBillStatus())) {
            throw new IllegalArgumentException("Parent bill is locked");
        }
        if (isGstBillFullyStockReturned(parent.getId(), parent)) {
            throw new IllegalArgumentException("Parent bill has no remaining sale quantity (fully returned to stock)");
        }
    }

    private void assertParentNonGstBillAllowsSupplementary(BillNonGST parent) {
        if (Boolean.TRUE.equals(parent.getIsDeleted())) {
            throw new IllegalArgumentException("Parent bill is deleted");
        }
        if (Boolean.FALSE.equals(parent.getLatestVersion())) {
            throw new IllegalArgumentException("Parent bill is not the latest version");
        }
        if (parent.getPaymentStatus() == BillNonGST.PaymentStatus.CANCELLED) {
            throw new IllegalArgumentException("Parent bill is cancelled");
        }
        String st = stripBillStatus(parent.getBillStatus());
        if (BillLifecycleStatus.CANCELLED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Parent bill is cancelled");
        }
        if (BillLifecycleStatus.SUPERSEDED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Parent bill is superseded");
        }
        if (BillLifecycleStatus.FULLY_RETURNED.equalsIgnoreCase(st)) {
            throw new IllegalArgumentException("Parent bill is fully returned");
        }
        if (isLifecycleExchanged(parent.getBillStatus())) {
            throw new IllegalArgumentException("Parent bill is exchanged");
        }
        if (isBillStatusLocked(parent.getBillStatus())) {
            throw new IllegalArgumentException("Parent bill is locked");
        }
        if (isNonGstBillFullyStockReturnedByInventory(parent.getId(), parent)) {
            throw new IllegalArgumentException("Parent bill has no remaining sale quantity (fully returned to stock)");
        }
    }

    private static void assertAppliedAdvanceNotExceedingBillTotal(BigDecimal advanceApplied, BigDecimal billTotal) {
        if (advanceApplied == null || billTotal == null) {
            return;
        }
        BigDecimal a = advanceApplied.setScale(2, RoundingMode.HALF_UP);
        BigDecimal t = billTotal.setScale(2, RoundingMode.HALF_UP);
        if (a.subtract(t).compareTo(PAY_ROUND_EPS) > 0) {
            throw new IllegalArgumentException("Invalid advance usage: wallet amount applied exceeds bill total.");
        }
    }

    private void assertBillTotalCoversRecordedPayments(BillKind kind, Long billId, BigDecimal newTotal) {
        List<BillPayment> rows = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(kind, billId);
        BigDecimal paid = sumNonAdvancePayments(rows);
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(kind, billId).setScale(2, RoundingMode.HALF_UP);
        BigDecimal covered = adv.add(paid).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = newTotal.setScale(2, RoundingMode.HALF_UP);
        if (total.add(PAY_ROUND_EPS).compareTo(covered) < 0) {
            throw new IllegalArgumentException(
                    "Adjusted bill total (₹" + total.toPlainString() + ") cannot be less than advance used plus payments (₹"
                            + covered.toPlainString() + ")");
        }
    }

    private void deactivateBillPayments(BillKind kind, Long billId, String location, Long actorUserId) {
        deactivateBillPayments(kind, billId, location, actorUserId, null, null);
    }

    private void deactivateBillPayments(BillKind kind, Long billId, String location, Long actorUserId,
            Long billVersionId, String linkedGroupId) {
        List<BillPayment> rows = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(kind, billId);
        List<BillPayment> reversalRows = new ArrayList<>();
        LocalDate refundDate = LocalDate.now();
        for (BillPayment p : rows) {
            if (Boolean.TRUE.equals(p.getIsDeleted())) {
                continue;
            }
            if (isAdvancePayment(p)) {
                p.setIsDeleted(true);
                p.setUpdatedBy(actorUserId);
                p.setPaymentStatus("INACTIVE");
                billPaymentRepository.save(p);
                voidActiveMoneyTransactionsForBillPayment(p.getId());
                continue;
            }
            if (billPaymentRepository.existsByReversalOfIdAndIsDeletedFalse(p.getId())) {
                billPaymentRepository.findFirstByReversalOfIdAndIsDeletedFalseOrderByIdAsc(p.getId())
                        .ifPresent(rev -> postInHandRefundLedger(rev, billVersionId, linkedGroupId));
                if (!Boolean.TRUE.equals(p.getIsDeleted())) {
                    p.setIsDeleted(true);
                    p.setUpdatedBy(actorUserId);
                    p.setPaymentStatus("INACTIVE");
                    billPaymentRepository.save(p);
                }
                continue;
            }
            BillPayment reversal = buildBillPaymentReversalRow(p, kind, billId, actorUserId, billVersionId, refundDate);
            reversalRows.add(reversal);
            p.setIsDeleted(true);
            p.setUpdatedBy(actorUserId);
            p.setPaymentStatus("INACTIVE");
            p.setIsReversed(true);
            p.setReversedAt(LocalDateTime.now());
            billPaymentRepository.save(p);
        }
        if (!reversalRows.isEmpty()) {
            List<BillPayment> savedReversals = billPaymentRepository.saveAll(reversalRows);
            for (BillPayment reversal : savedReversals) {
                postInHandRefundLedger(reversal, billVersionId, linkedGroupId);
            }
        }
    }

    /**
     * Line-edit resync: deactivate only wallet-mirror {@code bill_payments} rows ({@code sourceType=ADVANCE}),
     * post matching money reversals, then reverse wallet debits and re-apply FIFO up to the current bill total.
     * Does not touch cash/UPI rows — use full {@link #replaceBill} if payment split must change.
     */
    private void deactivateAdvanceBillPaymentsOnly(BillKind kind, Long billId, Long actorUserId) {
        List<BillPayment> rows = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(kind, billId);
        List<BillPayment> reversalRows = new ArrayList<>();
        for (BillPayment p : rows) {
            if (Boolean.TRUE.equals(p.getIsDeleted()) || !isAdvancePayment(p)) {
                continue;
            }
            voidActiveMoneyTransactionsForBillPayment(p.getId());
            if (billPaymentRepository.existsByReversalOfIdAndIsDeletedFalse(p.getId())) {
                billPaymentRepository.findFirstByReversalOfIdAndIsDeletedFalseOrderByIdAsc(p.getId())
                        .ifPresent(rev -> voidActiveMoneyTransactionsForBillPayment(rev.getId()));
                if (!Boolean.TRUE.equals(p.getIsDeleted())) {
                    p.setIsDeleted(true);
                    p.setUpdatedBy(actorUserId);
                    p.setPaymentStatus("INACTIVE");
                    billPaymentRepository.save(p);
                }
                continue;
            }
            BillPayment reversal = new BillPayment();
            reversal.setBillKind(kind);
            reversal.setBillId(billId);
            reversal.setSourceType("BILL_PAYMENT_REVERSAL");
            reversal.setAmount(p.getAmount() != null ? p.getAmount().setScale(2, RoundingMode.HALF_UP).negate() : BigDecimal.ZERO);
            reversal.setPaymentMode(p.getPaymentMode());
            reversal.setPaymentDate(p.getPaymentDate() != null ? p.getPaymentDate() : LocalDate.now());
            reversal.setCreatedBy(actorUserId);
            reversal.setUpdatedBy(actorUserId);
            reversal.setReversalOfId(p.getId());
            reversal.setPaymentStatus("REVERSAL");
            reversalRows.add(reversal);
            p.setIsDeleted(true);
            p.setUpdatedBy(actorUserId);
            p.setPaymentStatus("INACTIVE");
            billPaymentRepository.save(p);
        }
        if (!reversalRows.isEmpty()) {
            billPaymentRepository.saveAll(reversalRows);
        }
    }

    /**
     * Payment / advance recalculation after {@link #patchBillLineQuantities}: subtotal/tax/total already updated on the bill
     * entity; this re-applies customer wallet toward the new total (see {@code customer_wallet_transactions} debits
     * {@code BILL_PAYMENT} and {@code bill_payments} rows with {@code sourceType=ADVANCE}). Legacy {@code customer_advance}
     * / {@code customer_advance_usage} are not written on this path.
     */
    private void resyncAdvanceApplicationAfterLineQuantityEditNonGst(BillNonGST bill, Long actorUserId) {
        if (bill.getCustomer() == null || bill.getCustomer().getId() == null) {
            return;
        }
        deactivateAdvanceBillPaymentsOnly(BillKind.NON_GST, bill.getId(), actorUserId);
        customerAdvanceService.reverseAdvanceUsageForBill(BillKind.NON_GST, bill.getId());
        BigDecimal total = bill.getTotalAmount() != null ? bill.getTotalAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal applied = customerAdvanceService.applyAdvanceFifo(
                bill.getCustomer().getId(), BillKind.NON_GST, bill.getId(), total);
        assertAppliedAdvanceNotExceedingBillTotal(applied, total);
        persistWalletAdvancePayment(BillKind.NON_GST, bill.getId(), applied, bill.getBillDate(), actorUserId);
    }

    /** GST line-edit advance resync when GST bills participate in stock/wallet ({@link #GST_BILLS_AFFECT_STOCK_AND_WALLET}). */
    private void resyncAdvanceApplicationAfterLineQuantityEditGst(BillGST bill, Long actorUserId) {
        if (!GST_BILLS_AFFECT_STOCK_AND_WALLET || bill.getCustomer() == null || bill.getCustomer().getId() == null) {
            return;
        }
        deactivateAdvanceBillPaymentsOnly(BillKind.GST, bill.getId(), actorUserId);
        customerAdvanceService.reverseAdvanceUsageForBill(BillKind.GST, bill.getId());
        BigDecimal total = bill.getTotalAmount() != null ? bill.getTotalAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal applied = customerAdvanceService.applyAdvanceFifo(
                bill.getCustomer().getId(), BillKind.GST, bill.getId(), total);
        assertAppliedAdvanceNotExceedingBillTotal(applied, total);
        persistWalletAdvancePayment(BillKind.GST, bill.getId(), applied, bill.getBillDate(), actorUserId);
    }

    private void revertStockForGstBill(BillGST bill, String location) {
        revertStockForGstBill(bill, location, null, null);
    }

    private void revertStockForGstBill(BillGST bill, String location, String linkedGroupId, Long billVersionId) {
        // When GST bills no longer affect stock, NEW GST bills never deducted
        // any quantity, so reverting (which would add quantity back) would be
        // a phantom restore. We therefore skip this entirely when the feature
        // flag is off — legacy GST bills that had stock previously deducted
        // simply keep that historical deduction. If you ever flip the flag
        // back on, this revert logic returns to normal automatically.
        if (!GST_BILLS_AFFECT_STOCK_AND_WALLET) {
            return;
        }
        if (bill.getItems() == null) {
            return;
        }
        Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.GST, bill.getId());
        String note = "Stock restored via deleted GST bill " + bill.getBillNumber() + " (id=" + bill.getId() + ")";
        for (BillItemGST item : bill.getItems()) {
            BigDecimal sold = item.getQuantity() != null ? item.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal ret = returnedByLine.getOrDefault(item.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = sold.subtract(ret).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (net.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            applyStockReturnForGstLine(item, bill.getId(), net, note, location, bill.getBillDate(), linkedGroupId, billVersionId);
        }
    }

    private void revertStockForNonGstBill(BillNonGST bill, String location) {
        revertStockForNonGstBill(bill, location, null, null);
    }

    private void revertStockForNonGstBill(BillNonGST bill, String location, String linkedGroupId, Long billVersionId) {
        if (bill.getItems() == null) {
            return;
        }
        Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.NON_GST, bill.getId());
        String note = "Stock restored via deleted Non-GST bill " + bill.getBillNumber() + " (id=" + bill.getId() + ")";
        for (BillItemNonGST item : bill.getItems()) {
            BigDecimal sold = item.getQuantity() != null ? item.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal ret = returnedByLine.getOrDefault(item.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = sold.subtract(ret).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (net.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            applyStockReturnForNonGstLine(item, bill.getId(), net, note, location, bill.getBillDate(), linkedGroupId, billVersionId);
        }
    }

    private void refreshBillFinancialsGST(BillGST bill, String location) {
        List<BillPayment> rows = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, bill.getId());
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, bill.getId()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = sumNonAdvancePayments(rows);
        BigDecimal covered = adv.add(paid).setScale(2, RoundingMode.HALF_UP);
        bill.setPaidAmount(paid);
        bill.setPaymentStatus(toGstPaymentStatus(bill.getTotalAmount(), covered));
        List<ResolvedLine> lines = rows.stream()
                .filter(r -> !isAdvancePayment(r))
                .map(r -> new ResolvedLine(
                        r.getAmount() != null ? r.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        r.getPaymentMode() != null ? r.getPaymentMode() : BillPaymentMode.OTHER,
                        r.getPaymentDate()))
                .toList();
        bill.setPaymentMethod(buildPaymentMethodSummary(lines, bill.getTotalAmount(), paid, adv));
        if (location != null && !location.isBlank() && (bill.getLocation() == null || bill.getLocation().isBlank())) {
            bill.setLocation(location.trim());
        }
        billGSTRepository.save(bill);
    }

    private void refreshBillFinancialsNonGST(BillNonGST bill, String location) {
        List<BillPayment> rows = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, bill.getId());
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, bill.getId()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = sumNonAdvancePayments(rows);
        BigDecimal covered = adv.add(paid).setScale(2, RoundingMode.HALF_UP);
        bill.setPaidAmount(paid);
        bill.setPaymentStatus(toNonGstPaymentStatus(bill.getTotalAmount(), covered));
        List<ResolvedLine> lines = rows.stream()
                .filter(r -> !isAdvancePayment(r))
                .map(r -> new ResolvedLine(
                        r.getAmount() != null ? r.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        r.getPaymentMode() != null ? r.getPaymentMode() : BillPaymentMode.OTHER,
                        r.getPaymentDate()))
                .toList();
        bill.setPaymentMethod(buildPaymentMethodSummary(lines, bill.getTotalAmount(), paid, adv));
        if (location != null && !location.isBlank() && (bill.getLocation() == null || bill.getLocation().isBlank())) {
            bill.setLocation(location.trim());
        }
        billNonGSTRepository.save(bill);
    }

    private BillPaymentResponseDTO toPaymentResponseDTO(BillPayment p) {
        BillPaymentResponseDTO d = new BillPaymentResponseDTO();
        d.setPaymentId(p.getId());
        d.setAmount(p.getAmount().doubleValue());
        d.setPaymentMode(p.getPaymentMode().name());
        d.setPaymentDate(p.getPaymentDate());
        return d;
    }

    private static String formatSummaryFromPersisted(List<BillPayment> rows) {
        return rows.stream()
                .map(p -> p.getPaymentMode().name() + " ₹" + p.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .collect(Collectors.joining(", "));
    }

    private Map<String, List<BillPayment>> loadPaymentsGrouped(List<BillGST> gst, List<BillNonGST> non) {
        Map<String, List<BillPayment>> map = new HashMap<>();
        if (gst != null && !gst.isEmpty()) {
            Collection<Long> ids = gst.stream().map(BillGST::getId).toList();
            billPaymentRepository.findByBillKindAndBillIdIn(BillKind.GST, ids).forEach(p -> map
                    .computeIfAbsent(paymentKey(BillKind.GST, p.getBillId()), k -> new ArrayList<>()).add(p));
        }
        if (non != null && !non.isEmpty()) {
            Collection<Long> ids = non.stream().map(BillNonGST::getId).toList();
            billPaymentRepository.findByBillKindAndBillIdIn(BillKind.NON_GST, ids).forEach(p -> map
                    .computeIfAbsent(paymentKey(BillKind.NON_GST, p.getBillId()), k -> new ArrayList<>()).add(p));
        }
        for (List<BillPayment> list : map.values()) {
            list.sort((a, b) -> Long.compare(
                    a.getId() != null ? a.getId() : 0L,
                    b.getId() != null ? b.getId() : 0L));
        }
        return map;
    }

    private static String paymentKey(BillKind kind, Long billId) {
        return kind.name() + ":" + billId;
    }

    private List<ResolvedLine> resolvePaymentLines(BillRequestDTO req, BigDecimal totalAmount, LocalDate billDate, String userRole) {
        List<BillPaymentRequestDTO> incoming = req.getPayments();
        if (incoming != null && !incoming.isEmpty()) {
            List<ResolvedLine> out = new ArrayList<>();
            for (BillPaymentRequestDTO p : incoming) {
                if (p == null || p.getAmount() == null || p.getAmount() <= 0) {
                    continue;
                }
                BigDecimal amt = BigDecimal.valueOf(p.getAmount()).setScale(2, RoundingMode.HALF_UP);
                if (p.getPaymentMode() == null || p.getPaymentMode().isBlank()) {
                    throw new IllegalArgumentException("paymentMode is required for each payment with amount > 0");
                }
                BillPaymentMode mode = parseBillPaymentMode(p.getPaymentMode());
                LocalDate pd = p.getPaymentDate() != null ? p.getPaymentDate() : billDate;
                validateBackdatedPaymentDate(req, pd, userRole);
                out.add(new ResolvedLine(amt, mode, pd));
            }
            BigDecimal sum = sumResolvedLines(out);
            if (sum.subtract(totalAmount.setScale(2, RoundingMode.HALF_UP)).compareTo(PAY_ROUND_EPS) > 0) {
                throw new IllegalArgumentException(
                        "Sum of payments (₹" + sum.toPlainString() + ") cannot exceed bill total (₹"
                                + totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString() + ")");
            }
            return out;
        }
        String legacy = normalizePaymentMethod(req.getPaymentMethod());
        if (legacy != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            BillPaymentMode mode = parseBillPaymentMode(legacy);
            validateBackdatedPaymentDate(req, billDate, userRole);
            return List.of(new ResolvedLine(totalAmount.setScale(2, RoundingMode.HALF_UP), mode, billDate));
        }
        return List.of();
    }

    private static BigDecimal sumResolvedLines(List<ResolvedLine> lines) {
        return lines.stream()
                .map(ResolvedLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BillGST.PaymentStatus toGstPaymentStatus(BigDecimal billTotal, BigDecimal covered) {
        BigDecimal t = billTotal != null ? billTotal.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal c = covered != null ? covered.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (c.compareTo(PAY_ROUND_EPS) <= 0) {
            return BillGST.PaymentStatus.DUE;
        }
        if (c.subtract(t).compareTo(PAY_ROUND_EPS) > 0) {
            return BillGST.PaymentStatus.REFUND_PENDING;
        }
        if (t.subtract(c).compareTo(PAY_ROUND_EPS) > 0) {
            return BillGST.PaymentStatus.PARTIAL;
        }
        return BillGST.PaymentStatus.PAID;
    }

    private static BillNonGST.PaymentStatus toNonGstPaymentStatus(BigDecimal billTotal, BigDecimal covered) {
        BigDecimal t = billTotal != null ? billTotal.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal c = covered != null ? covered.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (c.compareTo(PAY_ROUND_EPS) <= 0) {
            return BillNonGST.PaymentStatus.DUE;
        }
        if (c.subtract(t).compareTo(PAY_ROUND_EPS) > 0) {
            return BillNonGST.PaymentStatus.REFUND_PENDING;
        }
        if (t.subtract(c).compareTo(PAY_ROUND_EPS) > 0) {
            return BillNonGST.PaymentStatus.PARTIAL;
        }
        return BillNonGST.PaymentStatus.PAID;
    }

    /**
     * Short string for {@code bills_*.payment_method}. Many databases still use VARCHAR(50); long enum names
     * like BANK_TRANSFER plus rupee amounts exceed that. Per-line amounts live in {@code bill_payments}.
     * Prefix {@code A} = advance/token applied toward this bill (not a bill_payment row).
     */
    private static String buildPaymentMethodSummary(List<ResolvedLine> lines, BigDecimal totalGross,
            BigDecimal cashPaid, BigDecimal advanceApplied) {
        BigDecimal adv = advanceApplied != null ? advanceApplied.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<String> parts = new ArrayList<>();
        if (adv.compareTo(BigDecimal.ZERO) > 0) {
            parts.add("A" + compactAmountForPaymentSummary(adv));
        }
        for (ResolvedLine line : lines) {
            parts.add(paymentModeCode(line.mode()) + compactAmountForPaymentSummary(line.amount()));
        }
        if (parts.isEmpty()) {
            return "-";
        }
        BigDecimal due = totalGross.subtract(adv).subtract(cashPaid).max(BigDecimal.ZERO).setScale(2,
                RoundingMode.HALF_UP);
        String body = String.join("+", parts);
        String summary;
        if (due.compareTo(BigDecimal.ZERO) > 0) {
            summary = body + "|D:" + compactAmountForPaymentSummary(due);
        } else {
            summary = body;
        }
        if (summary.length() > BILL_PAYMENT_METHOD_MAX_LEN) {
            StringBuilder fb = new StringBuilder();
            if (adv.compareTo(BigDecimal.ZERO) > 0) {
                fb.append("A").append(compactAmountForPaymentSummary(adv)).append("|");
            }
            fb.append(lines.size()).append("×|P:").append(compactAmountForPaymentSummary(cashPaid));
            if (due.compareTo(BigDecimal.ZERO) > 0) {
                fb.append("|D:").append(compactAmountForPaymentSummary(due));
            }
            summary = fb.toString();
        }
        return clampPaymentMethodForPersistence(summary);
    }

    private static char paymentModeCode(BillPaymentMode mode) {
        return switch (mode) {
            case CASH -> 'C';
            case UPI -> 'U';
            case BANK_TRANSFER -> 'B';
            case CHEQUE -> 'Q';
            case WALLET -> 'W';
            case OTHER -> 'O';
        };
    }

    private static String compactAmountForPaymentSummary(BigDecimal a) {
        if (a == null) {
            return "0";
        }
        BigDecimal s = a.setScale(2, RoundingMode.HALF_UP);
        if (s.stripTrailingZeros().scale() <= 0) {
            return s.toBigInteger().toString();
        }
        return s.stripTrailingZeros().toPlainString();
    }

    /** Hard limit for legacy VARCHAR(50) columns. */
    private static String clampPaymentMethodForPersistence(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= BILL_PAYMENT_METHOD_MAX_LEN) {
            return s;
        }
        return s.substring(0, BILL_PAYMENT_METHOD_MAX_LEN - 1) + "\u2026";
    }

    private void persistResolvedPayments(BillKind kind, Long billId, List<ResolvedLine> lines, String location) {
        persistResolvedPayments(kind, billId, lines, location, null, null);
    }

    private void persistResolvedPayments(BillKind kind, Long billId, List<ResolvedLine> lines, String location,
            Long billVersionId, String linkedGroupId) {
        if (lines.isEmpty()) {
            return;
        }
        List<BillPayment> rows = new ArrayList<>();
        for (ResolvedLine line : lines) {
            BillPayment row = new BillPayment();
            row.setBillKind(kind);
            row.setBillId(billId);
            row.setSourceType("BILL_PAYMENT");
            row.setAmount(line.amount());
            row.setPaymentMode(line.mode());
            row.setPaymentDate(line.paymentDate());
            row.setBillVersionId(billVersionId);
            rows.add(row);
        }
        List<BillPayment> savedRows = billPaymentRepository.saveAll(rows);
        if (location != null && !location.isBlank()) {
            for (BillPayment p : savedRows) {
                createTransactionFromBillPayment(p, billVersionId, linkedGroupId, "BILL_PAYMENT");
            }
        }
    }

    private void persistWalletAdvancePayment(BillKind kind, Long billId, BigDecimal amount, LocalDate paymentDate, Long actorUserId) {
        persistWalletAdvancePayment(kind, billId, amount, paymentDate, actorUserId, null, null);
    }

    private void persistWalletAdvancePayment(BillKind kind, Long billId, BigDecimal amount, LocalDate paymentDate,
            Long actorUserId, Long billVersionId, String linkedGroupId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BillPayment row = new BillPayment();
        row.setBillKind(kind);
        row.setBillId(billId);
        row.setSourceType("ADVANCE");
        row.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        row.setPaymentMode(BillPaymentMode.WALLET);
        row.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now());
        row.setCreatedBy(actorUserId);
        row.setUpdatedBy(actorUserId);
        row.setBillVersionId(billVersionId);
        BillPayment saved = billPaymentRepository.save(row);
        createTransactionFromBillPayment(saved, billVersionId, linkedGroupId, "ADVANCE_APPLICATION");
    }

    /**
     * Build and insert one transactions-ledger row for a newly created bill payment split.
     * Idempotency key: (reference_type=bill, reference_id=bill.id, amount, payment_mode).
     */
    private void createTransactionFromBillPayment(BillPayment payment) {
        createTransactionFromBillPayment(payment, null, null, "BILL_PAYMENT");
    }

    /**
     * When a single CASH/UPI payment is removed from a bill, record refund OUT (money returned to customer).
     */
    private void refundRemovedInHandPayment(BillKind kind, Long billId, BillPayment removed, Long actorUserId,
            Long billVersionId, String linkedGroupId) {
        if (removed == null || isAdvancePayment(removed)) {
            if (removed != null) {
                voidActiveMoneyTransactionsForBillPayment(removed.getId());
            }
            return;
        }
        if (billPaymentRepository.existsByReversalOfIdAndIsDeletedFalse(removed.getId())) {
            billPaymentRepository.findFirstByReversalOfIdAndIsDeletedFalseOrderByIdAsc(removed.getId())
                    .ifPresent(rev -> postInHandRefundLedger(rev, billVersionId, linkedGroupId));
            return;
        }
        BillPayment reversal = buildBillPaymentReversalRow(removed, kind, billId, actorUserId, billVersionId, LocalDate.now());
        BillPayment savedReversal = billPaymentRepository.save(reversal);
        postInHandRefundLedger(savedReversal, billVersionId, linkedGroupId);
    }

    /** Idempotent: posts missing OUT refund rows for a bill that was already cancelled (repair path). */
    private void ensureInHandRefundsPostedForBill(BillKind kind, Long billId, Long actorUserId) {
        List<BillPayment> rows = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(kind, billId);
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LocalDate refundDate = LocalDate.now();
        for (BillPayment p : rows) {
            if (isAdvancePayment(p) || p.getAmount() == null || p.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (billPaymentRepository.existsByReversalOfIdAndIsDeletedFalse(p.getId())) {
                billPaymentRepository.findFirstByReversalOfIdAndIsDeletedFalseOrderByIdAsc(p.getId())
                        .ifPresent(rev -> postInHandRefundLedger(rev, null, null));
                continue;
            }
            if (Boolean.TRUE.equals(p.getIsDeleted())) {
                BillPayment reversal = buildBillPaymentReversalRow(p, kind, billId, actorUserId, null, refundDate);
                BillPayment savedReversal = billPaymentRepository.save(reversal);
                postInHandRefundLedger(savedReversal, null, null);
            }
        }
    }

    private static BillPayment buildBillPaymentReversalRow(BillPayment original, BillKind kind, Long billId,
            Long actorUserId, Long billVersionId, LocalDate refundDate) {
        BillPayment reversal = new BillPayment();
        reversal.setBillKind(kind);
        reversal.setBillId(billId);
        reversal.setSourceType("BILL_PAYMENT_REVERSAL");
        reversal.setAmount(original.getAmount() != null
                ? original.getAmount().setScale(2, RoundingMode.HALF_UP).negate()
                : BigDecimal.ZERO);
        reversal.setPaymentMode(original.getPaymentMode());
        reversal.setPaymentDate(refundDate != null ? refundDate : LocalDate.now());
        reversal.setCreatedBy(actorUserId);
        reversal.setUpdatedBy(actorUserId);
        reversal.setBillVersionId(billVersionId);
        reversal.setReversalOfId(original.getId());
        reversal.setPaymentStatus("REVERSAL");
        return reversal;
    }

    /** Posts CASH/UPI/BANK refund OUT for a negative {@code bill_payments} reversal line. */
    private void postInHandRefundLedger(BillPayment reversalPayment, Long billVersionId, String linkedGroupId) {
        if (reversalPayment == null || isAdvancePayment(reversalPayment)) {
            return;
        }
        createTransactionFromBillPayment(reversalPayment, billVersionId, linkedGroupId, "BILL_REVERSAL");
    }

    /**
     * Soft-voids active {@code transactions} rows for a bill payment (wallet advance mirror rows only).
     */
    private void voidActiveMoneyTransactionsForBillPayment(Long billPaymentId) {
        if (billPaymentId == null) {
            return;
        }
        List<MoneyTransaction> rows = moneyTransactionRepository
                .findByBillPaymentIdAndIsDeletedFalseOrderByIdAsc(billPaymentId);
        if (rows.isEmpty()) {
            return;
        }
        for (MoneyTransaction tx : rows) {
            tx.setIsDeleted(true);
            tx.setStatus(MoneyTxnStatus.CANCELLED);
        }
        moneyTransactionRepository.saveAll(rows);
    }

    private void createTransactionFromBillPayment(BillPayment payment, Long billVersionId, String linkedGroupId,
            String txnType) {
        if (payment == null || payment.getBillId() == null || payment.getAmount() == null
                || payment.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        Long billId = payment.getBillId();
        BillKind kind = payment.getBillKind() != null ? payment.getBillKind() : BillKind.NON_GST;
        String billNumber = null;
        String location = "";
        Long customerId = null;
        String customerName = null;
        boolean supplementaryChildBill = false;

        if (kind == BillKind.GST) {
            BillGST bill = billGSTRepository.findById(billId).orElse(null);
            if (bill == null) {
                return;
            }
            billNumber = bill.getBillNumber();
            location = bill.getLocation() != null ? bill.getLocation().trim() : "";
            if (bill.getCustomer() != null) {
                customerId = bill.getCustomer().getId();
                customerName = bill.getCustomer().getCustomerName();
            }
        } else {
            BillNonGST bill = billNonGSTRepository.findById(billId).orElse(null);
            if (bill == null) {
                return;
            }
            billNumber = bill.getBillNumber();
            location = bill.getLocation() != null ? bill.getLocation().trim() : "";
            if (bill.getCustomer() != null) {
                customerId = bill.getCustomer().getId();
                customerName = bill.getCustomer().getCustomerName();
            }
            supplementaryChildBill = Boolean.TRUE.equals(bill.getSupplementaryBill());
        }

        MoneyPaymentMode paymentMode = mapPaymentMode(
                payment.getPaymentMode() != null ? payment.getPaymentMode().name() : null);
        BigDecimal rawAmount = payment.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = rawAmount.abs();
        MoneyDirection direction = rawAmount.compareTo(BigDecimal.ZERO) >= 0 ? MoneyDirection.IN : MoneyDirection.OUT;
        String normalizedTxnType = txnType != null ? txnType.trim().toUpperCase(Locale.ROOT) : "BILL_PAYMENT";
        boolean reversalTxn = normalizedTxnType.contains("REVERSAL");

        // Prefer 1:1 idempotency on bill_payments.id (allows two same-amount CASH lines on one bill).
        if (payment.getId() != null
                && moneyTransactionRepository.existsByBillPaymentIdAndIsDeletedFalse(payment.getId())) {
            return;
        }
        if (!reversalTxn && payment.getId() == null
                && moneyTransactionRepository.existsByReferenceTypeAndReferenceIdAndAmountAndPaymentMode(
                        MoneyReferenceType.bill, billId, amount, paymentMode)) {
            return;
        }

        String partyName = (customerName != null && !customerName.trim().isEmpty())
                ? customerName.trim()
                : (customerId != null ? ("Customer_" + customerId) : "Customer_Unknown");

        MoneyTransaction tx = new MoneyTransaction();
        tx.setAmount(amount);
        tx.setDirection(direction);
        if (reversalTxn) {
            tx.setCategory(MoneyCategory.BILL_REVERSAL);
            tx.setTxnType("BILL_REVERSAL");
            tx.setSubCategory(MoneyLedgerCategories.SUB_BILL_CANCELLATION);
        } else {
            tx.setCategory(MoneyCategory.BILL);
            tx.setTxnType(normalizedTxnType);
            if (kind == BillKind.NON_GST && supplementaryChildBill) {
                tx.setSubCategory(MoneyLedgerCategories.SUB_ADJUSTMENT_PAYMENT);
            } else {
                tx.setSubCategory(resolveBillPaymentSubCategory(normalizedTxnType));
            }
        }
        tx.setPartyId(customerId);
        tx.setPartyName(partyName);
        tx.setPaymentMode(paymentMode);
        tx.setReferenceType(MoneyReferenceType.bill);
        tx.setReferenceId(billId); // IMPORTANT: bill.id (not bill_payment.id)
        tx.setBillPaymentId(payment.getId());
        tx.setBillVersionId(billVersionId);
        tx.setLinkedGroupId(linkedGroupId);
        if (reversalTxn) {
            tx.setNotes("Bill cancellation refund to customer | BillNo: "
                    + (billNumber != null ? billNumber : ("#" + billId)));
        } else {
            tx.setNotes(normalizedTxnType + " | BillNo: " + (billNumber != null ? billNumber : ("#" + billId)));
        }
        tx.setTransactionDate(payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now());
        tx.setDateTime(LocalDateTime.now());
        tx.setLocation(location);
        tx.setOwnerUserId(payment.getCreatedBy());
        tx.setStatus(MoneyTxnStatus.ACTIVE);
        tx.setIsDeleted(false);
        if (reversalTxn && payment.getReversalOfId() != null) {
            moneyTransactionRepository
                    .findFirstByBillPaymentIdAndIsDeletedFalseOrderByIdAsc(payment.getReversalOfId())
                    .ifPresent(orig -> tx.setReversalOfId(orig.getId()));
        }
        moneyTransactionRepository.save(tx);
    }

    /** Links {@code transactions.reversal_of_id} on store-credit money row to an original bill payment IN row. */
    private void patchBillEditStoreCreditMoneyReversal(Long walletTxnId, Long reversalOfMoneyTxnId) {
        if (walletTxnId == null || reversalOfMoneyTxnId == null) {
            return;
        }
        moneyTransactionRepository
                .findByReferenceIdAndReferenceTypeAndCategory(walletTxnId, MoneyReferenceType.other, MoneyCategory.ADVANCE)
                .ifPresent(mt -> {
                    if (!"BILL_EDIT_ADJUSTMENT".equals(mt.getSubCategory())) {
                        return;
                    }
                    mt.setReversalOfId(reversalOfMoneyTxnId);
                    mt.setTxnType("BILL_EDIT_ADJUSTMENT");
                    moneyTransactionRepository.save(mt);
                });
    }

    /**
     * When a Non-GST bill replace lowers the total, prior cash + advance on the bill may exceed the new total.
     * Default: credit excess to customer wallet ({@code BILL_EDIT_ADJUSTMENT}) and mirror in {@code transactions}.
     *
     * @return amount credited to wallet, or zero if none
     */
    private BigDecimal maybeApplyBillEditExcessStoreCredit(
            BillRequestDTO req,
            Customer customer,
            String billLocation,
            BillNonGST bill,
            BigDecimal newTotalAmount,
            BigDecimal priorCoveredOnBill,
            Long billVersionRowId,
            String linkedGroupId,
            Long anchorOriginalBillPaymentMoneyTxnId) {
        String mode = trimToNull(req.getExcessPaymentHandling());
        if ("NONE".equalsIgnoreCase(mode != null ? mode : "")) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (customer == null || customer.getId() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal excess = priorCoveredOnBill.subtract(newTotalAmount).setScale(2, RoundingMode.HALF_UP);
        if (excess.compareTo(PAY_ROUND_EPS) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Long walletTxnId = customerAdvanceService.creditBillEditExcessToWalletIfAbsent(
                customer,
                excess,
                BillKind.NON_GST,
                bill.getId(),
                bill.getBillNumber(),
                billVersionRowId,
                linkedGroupId);
        if (walletTxnId == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        financialLedgerService.recordBillEditStoreCredit(
                billLocation,
                customer.getId(),
                walletTxnId,
                excess,
                bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now());
        patchBillEditStoreCreditMoneyReversal(walletTxnId, anchorOriginalBillPaymentMoneyTxnId);
        return excess;
    }

    /**
     * Ledger sub_category aligned with {@code txn_type}: BILL_PAYMENT, BILL_PAYMENT_REVERSAL, ADVANCE_APPLICATION.
     */
    private static String resolveBillPaymentSubCategory(String normalizedTxnType) {
        String t = normalizedTxnType != null ? normalizedTxnType.trim().toUpperCase(Locale.ROOT) : "BILL_PAYMENT";
        if (t.contains("REVERSAL")) {
            return "BILL_PAYMENT_REVERSAL";
        }
        if ("ADVANCE_APPLICATION".equals(t)) {
            return "ADVANCE_APPLICATION";
        }
        return "BILL_PAYMENT";
    }

    /** CASH->CASH, UPI->UPI, BANK_TRANSFER->BANK, WALLET->UPI. */
    private static MoneyPaymentMode mapPaymentMode(String mode) {
        String m = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        return switch (m) {
            case "CASH" -> MoneyPaymentMode.CASH;
            case "UPI", "WALLET" -> MoneyPaymentMode.UPI;
            case "BANK_TRANSFER", "CHEQUE", "OTHER", "BANK" -> MoneyPaymentMode.BANK;
            default -> MoneyPaymentMode.BANK;
        };
    }

    private String newLinkedGroupId() {
        return java.util.UUID.randomUUID().toString();
    }

    private void recordBillEvent(
            BillKind billKind,
            Long billId,
            BillEventType eventType,
            Long billVersionId,
            String linkedGroupId,
            Long actorUserId,
            Object payloadObj) {
        try {
            billEventService.record(billKind, billId, eventType, billVersionId, linkedGroupId, actorUserId, toJsonSafe(payloadObj));
        } catch (Exception ex) {
            log.warn("record_bill_event_failed kind={} billId={} type={}: {}", billKind, billId, eventType, ex.toString());
        }
    }

    private int nextBillVersionNo(Long billId) {
        return billVersionRepository.findTopByBillIdOrderByVersionNoDescCreatedAtDesc(billId)
                .map(v -> Math.max(1, v.getVersionNo() + 1))
                .orElse(1);
    }

    private Long latestBillVersionId(Long billId) {
        return billVersionRepository.findTopByBillIdOrderByVersionNoDescCreatedAtDesc(billId)
                .map(BillVersion::getId)
                .orElse(null);
    }

    private String toJsonSafe(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"snapshot_serialization_failed\"}";
        }
    }

    /**
     * Inserts {@code bill_versions} early so child rows can reference {@code bill_version_id} (this row's PK).
     * Snapshot is finalized after the bill state is fully applied via {@link #finalizeBillVersionSnapshot}.
     */
    private BillVersion beginBillVersion(Long billId, int versionNo, String actionType, Long previousVersionId,
            Object changeSummaryObj, String editReason, Long actorUserId) {
        if (previousVersionId != null) {
            billVersionRepository.findById(previousVersionId).ifPresent(prev -> {
                prev.setLifecycleStatus("SUPERSEDED");
                billVersionRepository.save(prev);
            });
        }
        BillVersion v = new BillVersion();
        v.setBillId(billId);
        v.setVersionNo(versionNo);
        v.setActionType(actionType);
        v.setPreviousVersionId(previousVersionId);
        v.setSnapshotJson("{\"pending\":true}");
        v.setChangeSummary(toJsonSafe(changeSummaryObj));
        v.setEditReason(editReason);
        v.setCreatedBy(actorUserId);
        v.setLifecycleStatus("ACTIVE");
        return billVersionRepository.save(v);
    }

    private void finalizeBillVersionSnapshot(Long versionRowId, Object snapshotObj) {
        if (versionRowId == null) {
            return;
        }
        billVersionRepository.findById(versionRowId).ifPresent(v -> {
            v.setSnapshotJson(toJsonSafe(snapshotObj));
            billVersionRepository.save(v);
        });
    }

    private static boolean isAdvancePayment(BillPayment p) {
        return p != null && "ADVANCE".equalsIgnoreCase(p.getSourceType());
    }

    private static BigDecimal sumNonAdvancePayments(List<BillPayment> rows) {
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return rows.stream()
                .filter(r -> !isAdvancePayment(r))
                .map(BillPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BillPaymentMode parseBillPaymentMode(String raw) {
        return BillPaymentMode.parseFlexible(raw);
    }

    /** Sets both paymentMethod and paymentMode for list/sale APIs and UI tables. */
    private static void setPaymentModeFields(BillResponseDTO dto, String raw) {
        String display = (raw == null || raw.isBlank()) ? "-" : raw.trim();
        dto.setPaymentMethod(display);
        dto.setPaymentMode(display);
    }

    /** Optional payment mode on bill; null/blank keeps legacy behaviour (no DB column change required). */
    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }
        String t = paymentMethod.trim();
        return t.isEmpty() ? null : t;
    }

    private LocalDate resolveRequestedBillDate(BillRequestDTO req, String userRole) {
        LocalDate requested = req.getBillDate() != null ? req.getBillDate() : LocalDate.now();
        LocalDate today = LocalDate.now();
        if (!requested.isBefore(today)) {
            return requested;
        }
        long days = ChronoUnit.DAYS.between(requested, today);
        if (days > MAX_BACKDATE_DAYS) {
            throw new IllegalArgumentException("Backdated bill is allowed only within last " + MAX_BACKDATE_DAYS + " days");
        }
        if (!isAdmin(userRole)) {
            throw new IllegalArgumentException("Only admin can create backdated bills");
        }
        if (trimToNull(req.getBackdateReason()) == null) {
            throw new IllegalArgumentException("backdateReason is required for backdated bill");
        }
        if (trimToNull(req.getBackdateApprovedBy()) == null) {
            throw new IllegalArgumentException("backdateApprovedBy is required for backdated bill");
        }
        return requested;
    }

    /**
     * Bill replace: keep the bill's historical calendar date when the client omits {@code billDate},
     * and do not treat "unchanged past billDate" as a new backdate (avoids blocking edits to old bills).
     * Changing {@code billDate} to a different past date still uses the same backdate rules as create.
     */
    private LocalDate resolveRequestedBillDateForReplace(BillRequestDTO req, String userRole, LocalDate currentStoredBillDate) {
        LocalDate requested = req.getBillDate() != null ? req.getBillDate() : currentStoredBillDate;
        if (requested == null) {
            requested = LocalDate.now();
        }
        LocalDate today = LocalDate.now();
        if (!requested.isBefore(today)) {
            return requested;
        }
        if (currentStoredBillDate != null && requested.equals(currentStoredBillDate)) {
            return requested;
        }
        long days = ChronoUnit.DAYS.between(requested, today);
        if (days > MAX_BACKDATE_DAYS) {
            throw new IllegalArgumentException("Backdated bill is allowed only within last " + MAX_BACKDATE_DAYS + " days");
        }
        if (!isAdmin(userRole)) {
            throw new IllegalArgumentException("Only admin can create backdated bills");
        }
        if (trimToNull(req.getBackdateReason()) == null) {
            throw new IllegalArgumentException("backdateReason is required for backdated bill");
        }
        if (trimToNull(req.getBackdateApprovedBy()) == null) {
            throw new IllegalArgumentException("backdateApprovedBy is required for backdated bill");
        }
        return requested;
    }

    /**
     * When replacing a bill, if the client does not resend {@code payments} / {@code paymentMethod},
     * reuse prior split rows (scaled down if the new net payable is lower than before).
     */
    private List<ResolvedLine> resolvePaymentLinesForReplace(
            BillRequestDTO req,
            BigDecimal netForPayments,
            LocalDate billDate,
            String userRole,
            List<ResolvedLine> previousNonAdvanceLines,
            BigDecimal headerPaidBeforeReplace,
            String headerPaymentMethodBeforeReplace) {
        List<ResolvedLine> fromRequest = resolvePaymentLines(req, netForPayments, billDate, userRole);
        if (!fromRequest.isEmpty()) {
            return fromRequest;
        }
        if (netForPayments.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        if (previousNonAdvanceLines != null && !previousNonAdvanceLines.isEmpty()) {
            return fitResolvedLinesToNet(previousNonAdvanceLines, netForPayments);
        }
        BigDecimal backup = headerPaidBeforeReplace != null
                ? headerPaidBeforeReplace.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (backup.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        BigDecimal use = backup.min(netForPayments).setScale(2, RoundingMode.HALF_UP);
        if (use.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        BillPaymentMode mode = inferPaymentModeForReplace(headerPaymentMethodBeforeReplace);
        LocalDate payDate = billDate != null ? billDate : LocalDate.now();
        return List.of(new ResolvedLine(use, mode, payDate));
    }

    private static List<ResolvedLine> fitResolvedLinesToNet(List<ResolvedLine> lines, BigDecimal net) {
        if (lines == null || lines.isEmpty() || net == null || net.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        BigDecimal sum = sumResolvedLines(lines);
        if (sum.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        if (sum.compareTo(net) <= 0) {
            return lines.stream()
                    .map(l -> new ResolvedLine(l.amount(), l.mode(), l.paymentDate()))
                    .collect(Collectors.toList());
        }
        BigDecimal factor = net.divide(sum, 8, RoundingMode.HALF_UP);
        List<ResolvedLine> out = new ArrayList<>();
        BigDecimal acc = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (int i = 0; i < lines.size(); i++) {
            ResolvedLine ln = lines.get(i);
            BigDecimal amt;
            if (i == lines.size() - 1) {
                amt = net.subtract(acc).setScale(2, RoundingMode.HALF_UP);
            } else {
                amt = ln.amount().multiply(factor).setScale(2, RoundingMode.HALF_UP);
                acc = acc.add(amt);
            }
            if (amt.compareTo(BigDecimal.ZERO) > 0) {
                out.add(new ResolvedLine(amt, ln.mode(), ln.paymentDate()));
            }
        }
        return out;
    }

    private static BillPaymentMode inferPaymentModeForReplace(String paymentMethodSummary) {
        if (paymentMethodSummary == null || paymentMethodSummary.isBlank() || "-".equals(paymentMethodSummary.trim())) {
            return BillPaymentMode.CASH;
        }
        try {
            return BillPaymentMode.parseFlexible(paymentMethodSummary);
        } catch (IllegalArgumentException ex) {
            String u = paymentMethodSummary.toUpperCase(Locale.ROOT);
            if (u.contains("UPI")) {
                return BillPaymentMode.UPI;
            }
            if (u.contains("BANK") || u.contains("NEFT") || u.contains("RTGS")) {
                return BillPaymentMode.BANK_TRANSFER;
            }
            if (u.contains("CHEQUE") || u.contains("CHECK")) {
                return BillPaymentMode.CHEQUE;
            }
            return BillPaymentMode.CASH;
        }
    }

    private void validateBackdatedPaymentDate(BillRequestDTO req, LocalDate paymentDate, String userRole) {
        if (paymentDate == null || !paymentDate.isBefore(LocalDate.now())) {
            return;
        }
        if (!Boolean.TRUE.equals(req.getAllowBackdatedPaymentDate())) {
            throw new IllegalArgumentException("Past paymentDate requires allowBackdatedPaymentDate=true");
        }
        if (!isAdmin(userRole)) {
            throw new IllegalArgumentException("Only admin can approve backdated paymentDate");
        }
        if (trimToNull(req.getBackdatedPaymentApprovedBy()) == null) {
            throw new IllegalArgumentException("backdatedPaymentApprovedBy is required for past paymentDate");
        }
    }

    private void recomputeSnapshotsForBillMutation(String location, LocalDate billDate, List<ResolvedLine> payLines) {
        recomputeSnapshotsForBillMutation(location, billDate, payLines, null);
    }

    /**
     * Rebuilds stored daily closing snapshots from the earliest impacted calendar day through today so that
     * each day's opening in-hand (prior snapshot closing) stays chained after backdated bills or payment edits.
     */
    private void recomputeSnapshotsForBillMutation(String location, LocalDate billDate, List<ResolvedLine> payLines,
            Set<LocalDate> extraImpactedDates) {
        if (location == null || location.isBlank()) {
            return;
        }
        LocalDate earliest = null;
        if (billDate != null) {
            earliest = billDate;
        }
        if (payLines != null) {
            for (ResolvedLine line : payLines) {
                if (line == null || line.paymentDate() == null) {
                    continue;
                }
                if (earliest == null || line.paymentDate().isBefore(earliest)) {
                    earliest = line.paymentDate();
                }
            }
        }
        if (extraImpactedDates != null) {
            for (LocalDate x : extraImpactedDates) {
                if (x == null) {
                    continue;
                }
                if (earliest == null || x.isBefore(earliest)) {
                    earliest = x;
                }
            }
        }
        if (earliest == null) {
            return;
        }
        cascadeClosingSnapshotsFrom(location, earliest);
    }

    private void recomputeSnapshotsForBillFromDbPayments(String location, LocalDate billDate, BillKind kind, Long billId,
            Set<LocalDate> extraImpactedDates) {
        if (location == null || location.isBlank() || billId == null) {
            return;
        }
        List<ResolvedLine> lines = resolvedNonAdvanceLinesForSnapshot(kind, billId);
        recomputeSnapshotsForBillMutation(location, billDate, lines, extraImpactedDates);
    }

    private void cascadeClosingSnapshotsFrom(String location, LocalDate earliest) {
        LocalDate end = LocalDate.now();
        if (earliest.isAfter(end)) {
            try {
                dailyClosingSnapshotService.createOrUpdateSnapshot(earliest, location);
            } catch (Exception ex) {
                log.warn("snapshot_recompute_failed location={} date={} error={}", location, earliest, ex.getMessage());
            }
            return;
        }
        int steps = 0;
        for (LocalDate d = earliest; !d.isAfter(end); d = d.plusDays(1)) {
            if (steps++ > 450) {
                log.warn("snapshot_cascade_cap_reached location={} from={}", location, earliest);
                break;
            }
            try {
                dailyClosingSnapshotService.createOrUpdateSnapshot(d, location);
            } catch (Exception ex) {
                log.warn("snapshot_recompute_failed location={} date={} error={}", location, d, ex.getMessage());
            }
        }
    }

    private List<ResolvedLine> resolvedNonAdvanceLinesForSnapshot(BillKind kind, Long billId) {
        List<BillPayment> pays = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(kind, billId);
        if (pays == null || pays.isEmpty()) {
            return List.of();
        }
        return pays.stream()
                .filter(p -> !isAdvancePayment(p))
                .map(p -> new ResolvedLine(
                        p.getAmount() != null ? p.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        p.getPaymentMode() != null ? p.getPaymentMode() : BillPaymentMode.OTHER,
                        p.getPaymentDate()))
                .collect(Collectors.toList());
    }

    private void recordBillCancellationAudit(BillKind kind, Long billId, String billNumber, LocalDate billDate,
            Customer customer, BigDecimal totalAmount, String paymentMethodSummary, String paymentStatus,
            String location, Long actorUserId, String cancellationReason) {
        List<BillPayment> pays = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(kind, billId);
        BigDecimal paidFromPayments = sumNonAdvancePayments(pays);
        BigDecimal advanceApplied = customerAdvanceService.sumAdvanceUsedForBill(kind, billId)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal inHandCollected = sumInHandNonAdvancePayments(pays);
        String custName = resolveCustomerDisplayNameForAudit(customer);
        String phone = customer != null ? customer.getPhone() : null;

        BillCancellationLog row = new BillCancellationLog();
        row.setBillKind(kind.name());
        row.setBillId(billId);
        row.setBillNumber(billNumber != null ? billNumber : "");
        row.setLocation(location != null ? location.trim() : "");
        row.setBillDate(billDate != null ? billDate : LocalDate.now());
        row.setCustomerName(custName);
        row.setCustomerPhone(phone);
        row.setTotalAmount(totalAmount != null ? totalAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        row.setPaidFromPayments(paidFromPayments);
        row.setAdvanceApplied(advanceApplied);
        row.setInHandCollected(inHandCollected);
        row.setPaymentMethodSummary(paymentMethodSummary);
        row.setPaymentStatus(paymentStatus);
        row.setCancelledAt(LocalDateTime.now());
        row.setCancelledByUserId(actorUserId);
        row.setCancellationReason(trimToNull(cancellationReason));
        billCancellationLogRepository.save(row);
    }

    private BillCancellationLogDTO toCancellationLogDto(BillCancellationLog e) {
        return BillCancellationLogDTO.builder()
                .id(e.getId())
                .billKind(e.getBillKind())
                .billId(e.getBillId())
                .billNumber(e.getBillNumber())
                .location(e.getLocation())
                .billDate(e.getBillDate())
                .customerName(e.getCustomerName())
                .customerPhone(e.getCustomerPhone())
                .totalAmount(e.getTotalAmount())
                .paidFromPayments(e.getPaidFromPayments())
                .advanceApplied(e.getAdvanceApplied())
                .inHandCollected(e.getInHandCollected())
                .paymentMethodSummary(e.getPaymentMethodSummary())
                .paymentStatus(e.getPaymentStatus())
                .cancelledAt(e.getCancelledAt())
                .cancelledByUserId(e.getCancelledByUserId())
                .cancellationReason(e.getCancellationReason())
                .build();
    }

    private static BigDecimal sumInHandNonAdvancePayments(List<BillPayment> rows) {
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BillPayment p : rows) {
            if (isAdvancePayment(p) || p.getAmount() == null) {
                continue;
            }
            if (p.getPaymentMode() != BillPaymentMode.CASH && p.getPaymentMode() != BillPaymentMode.UPI) {
                continue;
            }
            sum = sum.add(p.getAmount());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static String resolveCustomerDisplayNameForAudit(Customer c) {
        if (c == null) {
            return null;
        }
        if (c.getCustomerName() != null && !c.getCustomerName().isBlank()) {
            return c.getCustomerName().trim();
        }
        if (c.getName() != null && !c.getName().isBlank()) {
            return c.getName().trim();
        }
        return null;
    }

    private static boolean isAdmin(String role) {
        return role != null && "admin".equalsIgnoreCase(role.trim());
    }

    private static BillKind parseBillKind(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Bill type is required");
        }
        String v = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("GST".equals(v)) {
            return BillKind.GST;
        }
        if ("NON_GST".equals(v) || "NONGST".equals(v)) {
            return BillKind.NON_GST;
        }
        throw new IllegalArgumentException("Invalid bill type. Use GST or NON_GST");
    }

    private static String normalizeParentBillType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseBillKind(raw).name();
    }

    private static final int MAX_BILL_VERSION_EDIT_REASON = 500;

    private static String truncateBillVersionEditReason(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= MAX_BILL_VERSION_EDIT_REASON ? t : t.substring(0, MAX_BILL_VERSION_EDIT_REASON);
    }

    private void appendBillLifecycleNoteGst(BillGST bill, String incomingRaw, String eventTag) {
        String incoming = trimToNull(incomingRaw);
        if (incoming == null) {
            return;
        }
        String block = "[" + LocalDate.now() + " " + eventTag + "]\n" + incoming;
        String existing = trimToNull(bill.getNotes());
        bill.setNotes(existing == null ? block : existing + "\n\n" + block);
    }

    private void appendBillLifecycleNoteNonGst(BillNonGST bill, String incomingRaw, String eventTag) {
        String incoming = trimToNull(incomingRaw);
        if (incoming == null) {
            return;
        }
        String block = "[" + LocalDate.now() + " " + eventTag + "]\n" + incoming;
        String existing = trimToNull(bill.getNotes());
        bill.setNotes(existing == null ? block : existing + "\n\n" + block);
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
