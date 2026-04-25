package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.BillItemDTO;
import com.katariastoneworld.apis.dto.BillLineQuantitiesPatchRequestDTO;
import com.katariastoneworld.apis.dto.BillLineQuantityPatchLineDTO;
import com.katariastoneworld.apis.dto.BillPaymentRequestDTO;
import com.katariastoneworld.apis.dto.BillPaymentResponseDTO;
import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.dto.BillStockReturnLineRequestDTO;
import com.katariastoneworld.apis.dto.BillStockReturnRequestDTO;
import com.katariastoneworld.apis.dto.BillStockReturnResponseDTO;
import com.katariastoneworld.apis.dto.BillCancellationLogDTO;
import com.katariastoneworld.apis.entity.BillGST;
import com.katariastoneworld.apis.entity.BillCancellationLog;
import com.katariastoneworld.apis.entity.BillInventoryReturn;
import com.katariastoneworld.apis.entity.BillInventoryReturnLine;
import com.katariastoneworld.apis.entity.BillItemGST;
import com.katariastoneworld.apis.entity.BillItemNonGST;
import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.BillNonGST;
import com.katariastoneworld.apis.entity.BillPayment;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.Customer;
import com.katariastoneworld.apis.entity.Product;
import com.katariastoneworld.apis.repository.BillCancellationLogRepository;
import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillInventoryReturnLineRepository;
import com.katariastoneworld.apis.repository.BillInventoryReturnRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
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

@Service
@Transactional
public class BillService {
    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    private static final BigDecimal PAY_ROUND_EPS = new BigDecimal("0.01");

    /**
     * Legacy DBs often use VARCHAR(50) for {@code payment_method}. Summaries must stay within this;
     * full split details remain in {@code bill_payments}.
     */
    private static final int BILL_PAYMENT_METHOD_MAX_LEN = 50;
    private static final long MAX_BACKDATE_DAYS = 7;

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
    private DailyBudgetService dailyBudgetService;

    @Autowired
    private BillPaymentRepository billPaymentRepository;

    @Autowired
    private CustomerAdvanceService customerAdvanceService;

    @Autowired
    private InventoryReservationService inventoryReservationService;

    @Autowired
    private FinancialLedgerService financialLedgerService;

    @Autowired
    private BillInventoryReturnRepository billInventoryReturnRepository;

    @Autowired
    private BillInventoryReturnLineRepository billInventoryReturnLineRepository;

    @Autowired
    private DailyClosingSnapshotService dailyClosingSnapshotService;

    @Autowired
    private BillCancellationLogRepository billCancellationLogRepository;

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
            parentCustomer = parent.getCustomer();
        } else {
            BillNonGST parent = billNonGSTRepository.findByIdWithItemsAndProducts(parentBillId)
                    .orElseThrow(() -> new RuntimeException("Parent Non-GST bill not found with id: " + parentBillId));
            String parentLoc = resolveBillLocation(parent, parent.getCustomer());
            if (!Objects.equals(parentLoc, location)) {
                throw new RuntimeException("Parent Non-GST bill not found with id: " + parentBillId);
            }
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
        if (Boolean.TRUE.equals(bill.getIsDeleted())) {
            throw new IllegalArgumentException("Cannot edit a deleted bill");
        }
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("GST Bill not found with id: " + billId);
        }
        Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.GST, billId);
        if (!returnedByLine.isEmpty()) {
            throw new IllegalArgumentException("Cannot fully edit bill with stock returns. Use quantity patch/stock return flow.");
        }

        LocalDate oldBillDate = bill.getBillDate();
        List<ResolvedLine> oldPayLines = resolvedNonAdvanceLinesForSnapshot(BillKind.GST, billId);
        Customer oldCustomer = bill.getCustomer();

        // Undo existing effects first.
        inventoryReservationService.releaseForBill(billId, BillKind.GST);
        revertStockForGstBill(bill, billLocation);
        deactivateBillPayments(BillKind.GST, billId, billLocation, actorUserId);
        customerAdvanceService.reverseAdvanceUsageForBill(BillKind.GST, billId);

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

        LocalDate effectiveBillDate = resolveRequestedBillDate(req, userRole);
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
        for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
            productService.validateStockAvailability(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
            productService.validateStockAvailabilityByName(entry.getKey(), entry.getValue());
        }

        bill.getItems().clear();
        String firstInventoryHsn = null;
        for (BillItemDTO itemDTO : req.getItems()) {
            BillItemGST item = new BillItemGST();
            item.setProductName(itemDTO.getItemName());
            item.setProductType(itemDTO.getCategory());
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

        BigDecimal advanceApplied = customerAdvanceService.applyAdvanceFifo(customer.getId(), BillKind.GST, bill.getId(), totalAmount);
        persistWalletAdvancePayment(BillKind.GST, bill.getId(), advanceApplied, bill.getBillDate(), actorUserId);
        BigDecimal netForPayments = totalAmount.subtract(advanceApplied).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        List<ResolvedLine> payLines = resolvePaymentLines(req, netForPayments, bill.getBillDate(), userRole);
        BigDecimal totalPaid = sumResolvedLines(payLines);
        BigDecimal covered = advanceApplied.add(totalPaid);
        bill.setPaidAmount(totalPaid);
        bill.setPaymentStatus(toGstPaymentStatus(totalAmount, covered));
        bill.setPaymentMethod(buildPaymentMethodSummary(payLines, totalAmount, totalPaid, advanceApplied));
        billGSTRepository.save(bill);

        inventoryReservationService.releaseForBill(bill.getId(), BillKind.GST);
        inventoryReservationService.reserveForBill(
                bill.getId(), BillKind.GST, productQuantitiesById, productQuantitiesByName, billLocation);
        try {
            String note = "Stock deducted via edited GST bill " + bill.getBillNumber() + " (id=" + bill.getId() + ")";
            LocalDate stockDate = bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now();
            for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
                productService.deductStock(entry.getKey(), entry.getValue(), bill.getId(), note, BillKind.GST, stockDate);
            }
            for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
                productService.deductStockByName(entry.getKey(), entry.getValue(), bill.getId(), note, BillKind.GST, stockDate);
            }
            inventoryReservationService.consumeForBill(bill.getId(), BillKind.GST);
        } catch (RuntimeException ex) {
            inventoryReservationService.releaseForBill(bill.getId(), BillKind.GST);
            throw ex;
        }

        persistResolvedPayments(BillKind.GST, bill.getId(), payLines, billLocation);
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

        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, bill.getId()).setScale(2, RoundingMode.HALF_UP);
        BillResponseDTO response = convertGSTToResponseDTO(
                bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, bill.getId()),
                adv);
        boolean isSimpleBill = (req.getSimpleBill() != null && req.getSimpleBill())
                || (req.getTaxPercentage() != null && req.getTaxPercentage() == 0);
        response.setSimpleBill(isSimpleBill);
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
        if (Boolean.TRUE.equals(bill.getIsDeleted())) {
            throw new IllegalArgumentException("Cannot edit a deleted bill");
        }
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.NON_GST, billId);
        if (!returnedByLine.isEmpty()) {
            throw new IllegalArgumentException("Cannot fully edit bill with stock returns. Use quantity patch/stock return flow.");
        }

        LocalDate oldBillDate = bill.getBillDate();
        List<ResolvedLine> oldPayLines = resolvedNonAdvanceLinesForSnapshot(BillKind.NON_GST, billId);
        Customer oldCustomer = bill.getCustomer();

        inventoryReservationService.releaseForBill(billId, BillKind.NON_GST);
        revertStockForNonGstBill(bill, billLocation);
        deactivateBillPayments(BillKind.NON_GST, billId, billLocation, actorUserId);
        customerAdvanceService.reverseAdvanceUsageForBill(BillKind.NON_GST, billId);

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

        LocalDate effectiveBillDate = resolveRequestedBillDate(req, userRole);
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
            item.setProductType(itemDTO.getCategory());
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

        BigDecimal advanceApplied = customerAdvanceService.applyAdvanceFifo(customer.getId(), BillKind.NON_GST, bill.getId(), totalAmount);
        persistWalletAdvancePayment(BillKind.NON_GST, bill.getId(), advanceApplied, bill.getBillDate(), actorUserId);
        BigDecimal netForPayments = totalAmount.subtract(advanceApplied).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        List<ResolvedLine> payLines = resolvePaymentLines(req, netForPayments, bill.getBillDate(), userRole);
        BigDecimal totalPaid = sumResolvedLines(payLines);
        BigDecimal covered = advanceApplied.add(totalPaid);
        bill.setPaidAmount(totalPaid);
        bill.setPaymentStatus(toNonGstPaymentStatus(totalAmount, covered));
        bill.setPaymentMethod(buildPaymentMethodSummary(payLines, totalAmount, totalPaid, advanceApplied));
        billNonGSTRepository.save(bill);

        inventoryReservationService.releaseForBill(bill.getId(), BillKind.NON_GST);
        inventoryReservationService.reserveForBill(
                bill.getId(), BillKind.NON_GST, productQuantitiesById, productQuantitiesByName, billLocation);
        try {
            String note = "Stock deducted via edited Non-GST bill " + bill.getBillNumber() + " (id=" + bill.getId() + ")";
            LocalDate stockDate = bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now();
            for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
                productService.deductStock(entry.getKey(), entry.getValue(), bill.getId(), note, BillKind.NON_GST, stockDate);
            }
            for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
                productService.deductStockByName(entry.getKey(), entry.getValue(), bill.getId(), note, BillKind.NON_GST, stockDate);
            }
            inventoryReservationService.consumeForBill(bill.getId(), BillKind.NON_GST);
        } catch (RuntimeException ex) {
            inventoryReservationService.releaseForBill(bill.getId(), BillKind.NON_GST);
            throw ex;
        }

        persistResolvedPayments(BillKind.NON_GST, bill.getId(), payLines, billLocation);
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

        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, bill.getId()).setScale(2, RoundingMode.HALF_UP);
        BillResponseDTO response = convertNonGSTToResponseDTO(
                bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, bill.getId()),
                adv);
        boolean isSimpleBill = (req.getSimpleBill() != null && req.getSimpleBill())
                || (req.getTaxPercentage() != null && req.getTaxPercentage() == 0);
        response.setSimpleBill(isSimpleBill);
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

        // Validate stock availability for all products (by ID)
        for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
            productService.validateStockAvailability(entry.getKey(), entry.getValue());
        }

        // Validate stock availability for all products (by name)
        for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
            productService.validateStockAvailabilityByName(entry.getKey(), entry.getValue());
        }

        // Add items to bill (per-line HSN from inventory product when linked)
        String firstInventoryHsn = null;
        for (BillItemDTO itemDTO : billRequestDTO.getItems()) {
            BillItemGST item = new BillItemGST();
            item.setProductName(itemDTO.getItemName());
            item.setProductType(itemDTO.getCategory());
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

        BigDecimal advanceApplied = customerAdvanceService.applyAdvanceFifo(
                customer.getId(), BillKind.GST, savedBill.getId(), totalAmount);
        persistWalletAdvancePayment(BillKind.GST, savedBill.getId(), advanceApplied, savedBill.getBillDate(), createdByUserId);
        BigDecimal netForPayments = totalAmount.subtract(advanceApplied).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        List<ResolvedLine> payLines = resolvePaymentLines(billRequestDTO, netForPayments, savedBill.getBillDate(), userRole);
        BigDecimal totalPaidCash = sumResolvedLines(payLines);
        BigDecimal covered = advanceApplied.add(totalPaidCash);
        savedBill.setPaidAmount(totalPaidCash.setScale(2, RoundingMode.HALF_UP));
        savedBill.setPaymentStatus(toGstPaymentStatus(totalAmount, covered));
        savedBill.setPaymentMethod(buildPaymentMethodSummary(payLines, totalAmount, totalPaidCash, advanceApplied));
        billGSTRepository.save(savedBill);

        inventoryReservationService.reserveForBill(
                savedBill.getId(), BillKind.GST, productQuantitiesById, productQuantitiesByName, location);
        try {
            String gstBillNote = "Stock deducted via GST bill " + savedBill.getBillNumber() + " (id=" + savedBill.getId() + ")";
            LocalDate stockDate = savedBill.getBillDate() != null ? savedBill.getBillDate() : LocalDate.now();
            for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
                productService.deductStock(entry.getKey(), entry.getValue(), savedBill.getId(), gstBillNote, BillKind.GST, stockDate);
            }
            for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
                productService.deductStockByName(entry.getKey(), entry.getValue(), savedBill.getId(), gstBillNote, BillKind.GST, stockDate);
            }
            inventoryReservationService.consumeForBill(savedBill.getId(), BillKind.GST);
        } catch (RuntimeException ex) {
            inventoryReservationService.releaseForBill(savedBill.getId(), BillKind.GST);
            throw ex;
        }

        persistResolvedPayments(BillKind.GST, savedBill.getId(), payLines, resolveBillLocation(savedBill, customer));
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
            item.setProductType(itemDTO.getCategory());
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

        BigDecimal advanceApplied = customerAdvanceService.applyAdvanceFifo(
                customer.getId(), BillKind.NON_GST, savedBill.getId(), totalAmount);
        persistWalletAdvancePayment(BillKind.NON_GST, savedBill.getId(), advanceApplied, savedBill.getBillDate(), createdByUserId);
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
                productService.deductStock(entry.getKey(), entry.getValue(), savedBill.getId(), nonGstBillNote, BillKind.NON_GST, stockDate);
            }
            for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
                productService.deductStockByName(entry.getKey(), entry.getValue(), savedBill.getId(), nonGstBillNote, BillKind.NON_GST, stockDate);
            }
            inventoryReservationService.consumeForBill(savedBill.getId(), BillKind.NON_GST);
        } catch (RuntimeException ex) {
            inventoryReservationService.releaseForBill(savedBill.getId(), BillKind.NON_GST);
            throw ex;
        }

        persistResolvedPayments(BillKind.NON_GST, savedBill.getId(), payLines, resolveBillLocation(savedBill, customer));
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
        log.info("bill_create_success kind=NON_GST billId={} billNo={} total={} paid={} advance={}",
                savedBill.getId(), savedBill.getBillNumber(), totalAmount, totalPaidCash, advanceApplied);

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
            List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId);
            BigDecimal paid = sumNonAdvancePayments(existing);
            BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal remaining = bill.getTotalAmount().subtract(adv).subtract(paid).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.subtract(remaining).compareTo(PAY_ROUND_EPS) > 0) {
                throw new IllegalArgumentException("Payment amount cannot exceed remaining bill amount");
            }
            BillPayment row = new BillPayment();
            row.setBillKind(BillKind.GST);
            row.setBillId(billId);
            row.setSourceType("BILL_PAYMENT");
            row.setAmount(amount);
            row.setPaymentMode(mode);
            row.setPaymentDate(paymentRequest.getPaymentDate() != null ? paymentRequest.getPaymentDate() : LocalDate.now());
            row.setCreatedBy(actorUserId);
            row.setUpdatedBy(actorUserId);
            BillPayment saved = billPaymentRepository.save(row);
            financialLedgerService.syncBillPayment(billLocation, BillKind.GST, billId, saved.getId(), saved.getPaymentMode(),
                    saved.getAmount(), saved.getPaymentDate(), true);
            refreshBillFinancialsGST(bill, billLocation);
            BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.GST, billId, null);
            return convertGSTToResponseDTO(bill, billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId),
                    updatedAdv);
        }
        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        Customer customer = bill.getCustomer();
        String billLocation = resolveBillLocation(bill, customer);
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId);
        BigDecimal paid = sumNonAdvancePayments(existing);
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = bill.getTotalAmount().subtract(adv).subtract(paid).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.subtract(remaining).compareTo(PAY_ROUND_EPS) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed remaining bill amount");
        }
        BillPayment row = new BillPayment();
        row.setBillKind(BillKind.NON_GST);
        row.setBillId(billId);
        row.setSourceType("BILL_PAYMENT");
        row.setAmount(amount);
        row.setPaymentMode(mode);
        row.setPaymentDate(paymentRequest.getPaymentDate() != null ? paymentRequest.getPaymentDate() : LocalDate.now());
        row.setCreatedBy(actorUserId);
        row.setUpdatedBy(actorUserId);
        BillPayment saved = billPaymentRepository.save(row);
        financialLedgerService.syncBillPayment(billLocation, BillKind.NON_GST, billId, saved.getId(), saved.getPaymentMode(),
                saved.getAmount(), saved.getPaymentDate(), true);
        refreshBillFinancialsNonGST(bill, billLocation);
        BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.NON_GST, billId, null);
        return convertNonGSTToResponseDTO(bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId), updatedAdv);
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
            financialLedgerService.syncBillPayment(billLocation, BillKind.GST, billId, saved.getId(), saved.getPaymentMode(),
                    saved.getAmount(), saved.getPaymentDate(), true);
            refreshBillFinancialsGST(bill, billLocation);
            BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            Set<LocalDate> extra = null;
            if (oldPaymentDate != null && saved.getPaymentDate() != null && !oldPaymentDate.equals(saved.getPaymentDate())) {
                extra = new HashSet<>();
                extra.add(oldPaymentDate);
            }
            recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.GST, billId, extra);
            return convertGSTToResponseDTO(bill, billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId),
                    updatedAdv);
        }

        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
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
        financialLedgerService.syncBillPayment(billLocation, BillKind.NON_GST, billId, saved.getId(), saved.getPaymentMode(),
                saved.getAmount(), saved.getPaymentDate(), true);
        refreshBillFinancialsNonGST(bill, billLocation);
        BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        Set<LocalDate> extra = null;
        if (oldPaymentDate != null && saved.getPaymentDate() != null && !oldPaymentDate.equals(saved.getPaymentDate())) {
            extra = new HashSet<>();
            extra.add(oldPaymentDate);
        }
        recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.NON_GST, billId, extra);
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
            row.setIsDeleted(true);
            row.setUpdatedBy(actorUserId);
            BillPayment saved = billPaymentRepository.save(row);
            financialLedgerService.syncBillPayment(billLocation, BillKind.GST, billId, saved.getId(), saved.getPaymentMode(),
                    saved.getAmount(), saved.getPaymentDate(), false);
            refreshBillFinancialsGST(bill, billLocation);
            BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            Set<LocalDate> extra = saved.getPaymentDate() != null ? Set.of(saved.getPaymentDate()) : null;
            recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.GST, billId, extra);
            return convertGSTToResponseDTO(bill, billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId),
                    updatedAdv);
        }

        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        row.setIsDeleted(true);
        row.setUpdatedBy(actorUserId);
        BillPayment saved = billPaymentRepository.save(row);
        financialLedgerService.syncBillPayment(billLocation, BillKind.NON_GST, billId, saved.getId(), saved.getPaymentMode(),
                saved.getAmount(), saved.getPaymentDate(), false);
        refreshBillFinancialsNonGST(bill, billLocation);
        BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        Set<LocalDate> extra = saved.getPaymentDate() != null ? Set.of(saved.getPaymentDate()) : null;
        recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.NON_GST, billId, extra);
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
        if ("GST".equalsIgnoreCase(billType)) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
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
                applyStockReturnForGstLine(line, bill.getId(), rl.getQuantityReturned(), noteBase, billLocation, bill.getBillDate());
            }
            return toStockReturnResponse(saved);
        }

        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        Map<Long, BigDecimal> returnedBefore = loadReturnedByLineId(BillKind.NON_GST, billId);
        BillInventoryReturn header = new BillInventoryReturn();
        header.setBillKind(BillKind.NON_GST);
        header.setBillId(billId);
        header.setLocation(location != null ? location.trim() : null);
        header.setNotes(request.getNotes());
        header.setCreatedByUserId(actorUserId);
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
            header.addLine(rl);
        }
        BillInventoryReturn saved = billInventoryReturnRepository.save(header);
        String noteBase = "Partial stock return id=" + saved.getId() + " for Non-GST bill " + bill.getBillNumber() + " (bill id="
                + bill.getId() + ")";
        for (BillInventoryReturnLine rl : saved.getLines()) {
            BillItemNonGST line = findNonGstLine(bill, rl.getBillItemId());
            applyStockReturnForNonGstLine(line, bill.getId(), rl.getQuantityReturned(), noteBase, billLocation, bill.getBillDate());
        }
        return toStockReturnResponse(saved);
    }

    /**
     * Patch billed quantities on existing lines: decreases call inventory return; increases call stock deduction.
     * Lines that already have partial stock returns cannot have quantity reduced (avoid double stock movement);
     * increases are still allowed.
     */
    public BillResponseDTO patchBillLineQuantities(Long billId, String billType, BillLineQuantitiesPatchRequestDTO request,
            String location, Long actorUserId) {
        if (request == null || request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("At least one line patch is required");
        }
        Set<Long> seen = new HashSet<>();
        for (BillLineQuantityPatchLineDTO ln : request.getLines()) {
            if (ln.getBillItemId() == null || !seen.add(ln.getBillItemId())) {
                throw new IllegalArgumentException("Each billItemId must appear once and be non-null");
            }
        }
        if ("GST".equalsIgnoreCase(billType)) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.GST, billId);
            for (BillLineQuantityPatchLineDTO patch : request.getLines()) {
                BillItemGST line = findGstLine(bill, patch.getBillItemId());
                BigDecimal oldQ = line.getQuantity() != null ? line.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                BigDecimal newQ = BigDecimal.valueOf(patch.getQuantity()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal r = returnedByLine.getOrDefault(line.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                if (newQ.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Quantity must be positive for line id=" + line.getId());
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
                if (delta.compareTo(PAY_ROUND_EPS) > 0) {
                    applyStockReturnForGstLine(line, bill.getId(), delta, note, billLocation, bill.getBillDate());
                } else if (delta.compareTo(PAY_ROUND_EPS.negate()) < 0) {
                    BigDecimal add = delta.negate();
                    LocalDate stockDate = bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now();
                    if (line.getProduct() != null && line.getProduct().getId() != null) {
                        productService.deductStock(line.getProduct().getId(), add, bill.getId(), note, BillKind.GST, stockDate);
                    } else {
                        productService.deductStockByName(line.getProductName(), add, bill.getId(), note, BillKind.GST, stockDate);
                    }
                }
                line.setQuantity(newQ);
                BigDecimal price = line.getPricePerUnit() != null ? line.getPricePerUnit().setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                line.setItemTotalPrice(price.multiply(newQ).setScale(2, RoundingMode.HALF_UP));
            }
            recomputeGstAmountsFromItems(bill);
            assertBillTotalCoversRecordedPayments(BillKind.GST, billId, bill.getTotalAmount());
            bill.setUpdatedByUserId(actorUserId);
            billGSTRepository.save(bill);
            refreshBillFinancialsGST(bill, billLocation);
            BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.GST, billId, null);
            return convertGSTToResponseDTO(bill, billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId), adv);
        }

        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        Map<Long, BigDecimal> returnedByLine = loadReturnedByLineId(BillKind.NON_GST, billId);
        for (BillLineQuantityPatchLineDTO patch : request.getLines()) {
            BillItemNonGST line = findNonGstLine(bill, patch.getBillItemId());
            BigDecimal oldQ = line.getQuantity() != null ? line.getQuantity().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal newQ = BigDecimal.valueOf(patch.getQuantity()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal r = returnedByLine.getOrDefault(line.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (newQ.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for line id=" + line.getId());
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
                applyStockReturnForNonGstLine(line, bill.getId(), delta, note, billLocation, bill.getBillDate());
            } else if (delta.compareTo(PAY_ROUND_EPS.negate()) < 0) {
                BigDecimal add = delta.negate();
                LocalDate stockDate = bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now();
                if (line.getProduct() != null && line.getProduct().getId() != null) {
                    productService.deductStock(line.getProduct().getId(), add, bill.getId(), note, BillKind.NON_GST, stockDate);
                } else {
                    productService.deductStockByName(line.getProductName(), add, bill.getId(), note, BillKind.NON_GST, stockDate);
                }
            }
            line.setQuantity(newQ);
            BigDecimal price = line.getPricePerUnit() != null ? line.getPricePerUnit().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            line.setItemTotalPrice(price.multiply(newQ).setScale(2, RoundingMode.HALF_UP));
        }
        recomputeNonGstAmountsFromItems(bill);
        assertBillTotalCoversRecordedPayments(BillKind.NON_GST, billId, bill.getTotalAmount());
        bill.setUpdatedByUserId(actorUserId);
        billNonGSTRepository.save(bill);
        refreshBillFinancialsNonGST(bill, billLocation);
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        recomputeSnapshotsForBillFromDbPayments(billLocation, bill.getBillDate(), BillKind.NON_GST, billId, null);
        return convertNonGSTToResponseDTO(bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId), adv);
    }

    /**
     * Soft-delete a bill and reverse its side effects (pending, partial, or fully paid):
     * - restore inventory for remaining line quantities (sold minus prior partial returns)
     * - soft-delete every active bill payment and reverse CASH/UPI in-hand via ledger + daily budget
     * - restore customer advance amounts applied to this bill
     */
    public void deleteBill(Long billId, String billType, String location, Long actorUserId) {
        if ("GST".equalsIgnoreCase(billType)) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            if (Boolean.TRUE.equals(bill.getIsDeleted())) {
                log.info("delete_bill_skip_already_deleted kind=GST billId={}", billId);
                return;
            }
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            List<ResolvedLine> snapshotPayLines = resolvedNonAdvanceLinesForSnapshot(BillKind.GST, billId);
            recordBillCancellationAudit(BillKind.GST, billId, bill.getBillNumber(), bill.getBillDate(), bill.getCustomer(),
                    bill.getTotalAmount(), bill.getPaymentMethod(),
                    bill.getPaymentStatus() != null ? bill.getPaymentStatus().name() : null,
                    billLocation, actorUserId);
            inventoryReservationService.releaseForBill(billId, BillKind.GST);
            revertStockForGstBill(bill, billLocation);
            deactivateBillPayments(BillKind.GST, billId, billLocation, actorUserId);
            customerAdvanceService.reverseAdvanceUsageForBill(BillKind.GST, billId);
            bill.setIsDeleted(true);
            bill.setUpdatedByUserId(actorUserId);
            bill.setPaymentStatus(BillGST.PaymentStatus.CANCELLED);
            bill.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            bill.setPaymentMethod("-");
            billGSTRepository.save(bill);
            recomputeSnapshotsForBillMutation(billLocation, bill.getBillDate(), snapshotPayLines);
            return;
        }

        BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        if (Boolean.TRUE.equals(bill.getIsDeleted())) {
            log.info("delete_bill_skip_already_deleted kind=NON_GST billId={}", billId);
            return;
        }
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        List<ResolvedLine> snapshotPayLines = resolvedNonAdvanceLinesForSnapshot(BillKind.NON_GST, billId);
        recordBillCancellationAudit(BillKind.NON_GST, billId, bill.getBillNumber(), bill.getBillDate(), bill.getCustomer(),
                bill.getTotalAmount(), bill.getPaymentMethod(),
                bill.getPaymentStatus() != null ? bill.getPaymentStatus().name() : null,
                billLocation, actorUserId);
        inventoryReservationService.releaseForBill(billId, BillKind.NON_GST);
        revertStockForNonGstBill(bill, billLocation);
        deactivateBillPayments(BillKind.NON_GST, billId, billLocation, actorUserId);
        customerAdvanceService.reverseAdvanceUsageForBill(BillKind.NON_GST, billId);
        bill.setIsDeleted(true);
        bill.setUpdatedByUserId(actorUserId);
        bill.setPaymentStatus(BillNonGST.PaymentStatus.CANCELLED);
        bill.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        bill.setPaymentMethod("-");
        billNonGSTRepository.save(bill);
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
    public void deleteBillById(Long billId, String location, Long actorUserId) {
        try {
            deleteBill(billId, "GST", location, actorUserId);
            return;
        } catch (RuntimeException ignored) {
            // try non-gst
        }
        deleteBill(billId, "NON_GST", location, actorUserId);
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
            return convertNonGSTToResponseDTO(bill, payments, adv);
        }
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
        List<BillGST> gstEntities = createdByUserId != null
                ? billGSTRepository.findByBillLocationAndCreatedByUserId(location, createdByUserId)
                : billGSTRepository.findByBillLocation(location);
        List<BillNonGST> nonEntities = createdByUserId != null
                ? billNonGSTRepository.findByBillLocationAndCreatedByUserId(location, createdByUserId)
                : billNonGSTRepository.findByBillLocation(location);

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
                .sorted((a, b) -> {
                    // Sort by bill date descending (most recent first), then by created date
                    int dateCompare = b.getBillDate().compareTo(a.getBillDate());
                    if (dateCompare != 0) {
                        return dateCompare;
                    }
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .collect(Collectors.toList());
    }

    public List<BillResponseDTO> getAllSales(String location) {
        // Same as getAllBills but with a dedicated method name for sales
        return getAllBills(location, null);
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
                    log.info("Using pricePerUnit={} for Non-GST item DTO mapping", priceToUse);
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
        return responseDTO;
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

    private Map<Long, BigDecimal> loadReturnedByLineId(BillKind kind, Long billId) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : billInventoryReturnLineRepository.sumReturnedQuantityGroupedByBillItemId(kind, billId)) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            Long itemId = ((Number) row[0]).longValue();
            BigDecimal sum = row[1] instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) row[1]).doubleValue());
            map.put(itemId, sum.setScale(2, RoundingMode.HALF_UP));
        }
        return map;
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
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal q = qty.setScale(2, RoundingMode.HALF_UP);
        LocalDate d = stockLedgerDate != null ? stockLedgerDate : LocalDate.now();
        if (line.getProduct() != null && line.getProduct().getId() != null) {
            productService.recordBillStockReturn(line.getProduct().getId(), q, billId, BillKind.GST, note, billLocation, d);
        } else {
            productService.recordBillStockReturnByName(line.getProductName(), q, billId, BillKind.GST, note, billLocation, d);
        }
    }

    private void applyStockReturnForNonGstLine(BillItemNonGST line, Long billId, BigDecimal qty, String note, String billLocation,
            LocalDate stockLedgerDate) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal q = qty.setScale(2, RoundingMode.HALF_UP);
        LocalDate d = stockLedgerDate != null ? stockLedgerDate : LocalDate.now();
        if (line.getProduct() != null && line.getProduct().getId() != null) {
            productService.recordBillStockReturn(line.getProduct().getId(), q, billId, BillKind.NON_GST, note, billLocation, d);
        } else {
            productService.recordBillStockReturnByName(line.getProductName(), q, billId, BillKind.NON_GST, note, billLocation, d);
        }
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
        List<BillPayment> rows = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(kind, billId);
        for (BillPayment p : rows) {
            if (Boolean.TRUE.equals(p.getIsDeleted())) {
                continue;
            }
            p.setIsDeleted(true);
            p.setUpdatedBy(actorUserId);
            BillPayment saved = billPaymentRepository.save(p);
            financialLedgerService.syncBillPayment(location, kind, billId, saved.getId(), saved.getPaymentMode(),
                    saved.getAmount(), saved.getPaymentDate(), false);
        }
    }

    private void revertStockForGstBill(BillGST bill, String location) {
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
            applyStockReturnForGstLine(item, bill.getId(), net, note, location, bill.getBillDate());
        }
    }

    private void revertStockForNonGstBill(BillNonGST bill, String location) {
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
            applyStockReturnForNonGstLine(item, bill.getId(), net, note, location, bill.getBillDate());
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

    private static BillGST.PaymentStatus toGstPaymentStatus(BigDecimal totalAmount, BigDecimal paid) {
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        paid = paid.setScale(2, RoundingMode.HALF_UP);
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return BillGST.PaymentStatus.DUE;
        }
        if (paid.compareTo(totalAmount) >= 0 || paid.subtract(totalAmount).abs().compareTo(PAY_ROUND_EPS) <= 0) {
            return BillGST.PaymentStatus.PAID;
        }
        return BillGST.PaymentStatus.PARTIAL;
    }

    private static BillNonGST.PaymentStatus toNonGstPaymentStatus(BigDecimal totalAmount, BigDecimal paid) {
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        paid = paid.setScale(2, RoundingMode.HALF_UP);
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return BillNonGST.PaymentStatus.DUE;
        }
        if (paid.compareTo(totalAmount) >= 0 || paid.subtract(totalAmount).abs().compareTo(PAY_ROUND_EPS) <= 0) {
            return BillNonGST.PaymentStatus.PAID;
        }
        return BillNonGST.PaymentStatus.PARTIAL;
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
            rows.add(row);
        }
        List<BillPayment> savedRows = billPaymentRepository.saveAll(rows);
        if (location != null && !location.isBlank()) {
            for (BillPayment p : savedRows) {
                financialLedgerService.syncBillPayment(location.trim(), kind, billId, p.getId(), p.getPaymentMode(),
                        p.getAmount(), p.getPaymentDate(), true);
            }
        }
    }

    private void persistWalletAdvancePayment(BillKind kind, Long billId, BigDecimal amount, LocalDate paymentDate, Long actorUserId) {
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
        billPaymentRepository.save(row);
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
            String location, Long actorUserId) {
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

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
