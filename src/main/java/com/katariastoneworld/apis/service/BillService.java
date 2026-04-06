package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.BillItemDTO;
import com.katariastoneworld.apis.dto.BillPaymentRequestDTO;
import com.katariastoneworld.apis.dto.BillPaymentResponseDTO;
import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.event.BillPaymentLedgerSyncEvent;
import com.katariastoneworld.apis.repository.BillRepository;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bill lifecycle and payments. Class-level {@link Transactional} ensures payment persistence and
 * {@link BillPaymentLedgerSyncEvent} handling (synchronous) commit or roll back together.
 */
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

    private record ResolvedLine(BigDecimal amount, BillPaymentMode mode, LocalDate paymentDate) {
    }

    private final BillRepository billRepository;
    private final CustomerService customerService;
    private final BillNumberGeneratorService billNumberGeneratorService;
    private final ProductService productService;
    private final EmailService emailService;
    private final BillPaymentRepository billPaymentRepository;
    private final CustomerAdvanceService customerAdvanceService;
    private final ApplicationEventPublisher eventPublisher;

    public BillService(BillRepository billRepository,
            CustomerService customerService,
            BillNumberGeneratorService billNumberGeneratorService,
            ProductService productService,
            EmailService emailService,
            BillPaymentRepository billPaymentRepository,
            CustomerAdvanceService customerAdvanceService,
            ApplicationEventPublisher eventPublisher) {
        this.billRepository = billRepository;
        this.customerService = customerService;
        this.billNumberGeneratorService = billNumberGeneratorService;
        this.productService = productService;
        this.emailService = emailService;
        this.billPaymentRepository = billPaymentRepository;
        this.customerAdvanceService = customerAdvanceService;
        this.eventPublisher = eventPublisher;
    }

    public BillResponseDTO createBill(BillRequestDTO billRequestDTO, String location, Long createdByUserId) {
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
        System.out.println("[Bill] Other expenses from request: raw=" + billRequestDTO.getOtherExpenses()
                + ", applied=" + otherExpenses);

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

        if (isGST) {
            return createGSTBill(billRequestDTO, customer, location, billNumber, totalSqft, subtotal,
                    taxPercentage, serviceCharge, labourCharge, transportationCharge, otherExpenses, discountAmount, createdByUserId);
        } else {
            return createNonGSTBill(billRequestDTO, customer, location, billNumber, totalSqft, subtotal,
                    serviceCharge, labourCharge, transportationCharge, otherExpenses, discountAmount, createdByUserId);
        }
    }

    private BillResponseDTO createGSTBill(BillRequestDTO billRequestDTO, Customer customer, String location,
            String billNumber, BigDecimal totalSqft, BigDecimal subtotal,
            BigDecimal taxRate, BigDecimal serviceCharge, BigDecimal labourCharge,
            BigDecimal transportationCharge, BigDecimal otherExpenses, BigDecimal discountAmount, Long createdByUserId) {
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

        Bill bill = new Bill();
        bill.setBillKind(BillKind.GST);
        bill.setBillNumber(billNumber);
        bill.setCustomer(customer);
        bill.setLocation(location != null ? location.trim() : null);
        bill.setBillDate(LocalDate.now());
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
        bill.setPaymentStatus(Bill.PaymentStatus.DUE);
        bill.setPaymentMethod("-");
        // bill-level hsnCode: set after items (from request or first product in inventory)
        bill.setVehicleNo(trimToNull(billRequestDTO.getVehicleNo()));
        bill.setDeliveryAddress(trimToNull(billRequestDTO.getDeliveryAddress()));
        bill.setCreatedByUserId(createdByUserId);
        System.out.println("[Bill GST] Bill " + billNumber + " created with otherExpenses=" + otherExpenses
                + ", totalAmount=" + totalAmount);

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

        String firstInventoryHsn = null;
        int lineNo = 1;
        for (BillItemDTO itemDTO : billRequestDTO.getItems()) {
            BillLineItem item = new BillLineItem();
            item.setLineNo(lineNo++);
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

        Bill savedBill = billRepository.save(bill);

        BigDecimal advanceApplied = customerAdvanceService.applyAdvanceFifo(
                customer.getId(), BillKind.GST, savedBill.getId(), totalAmount);
        BigDecimal netForPayments = totalAmount.subtract(advanceApplied).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        List<ResolvedLine> payLines = resolvePaymentLines(billRequestDTO, netForPayments, savedBill.getBillDate());
        BigDecimal totalPaidCash = sumResolvedLines(payLines);
        BigDecimal covered = advanceApplied.add(totalPaidCash);
        savedBill.setPaidAmount(totalPaidCash.setScale(2, RoundingMode.HALF_UP));
        savedBill.setPaymentStatus(toBillPaymentStatus(totalAmount, covered));
        savedBill.setPaymentMethod(buildPaymentMethodSummary(payLines, totalAmount, totalPaidCash, advanceApplied));
        billRepository.save(savedBill);

        // Deduct stock from products after bill is saved (grouped by productId)
        String gstBillNote = "Stock deducted via GST bill " + savedBill.getBillNumber() + " (id=" + savedBill.getId() + ")";
        for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
            productService.deductStock(entry.getKey(), entry.getValue(), savedBill.getId(), gstBillNote);
        }

        // Deduct stock from products after bill is saved (grouped by product name)
        for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
            productService.deductStockByName(entry.getKey(), entry.getValue(), savedBill.getId(), gstBillNote);
        }

        persistResolvedPayments(BillKind.GST, savedBill.getId(), payLines, resolveBillLocation(savedBill, customer));

        // Convert to response DTO
        BillResponseDTO responseDTO = convertToResponseDTO(savedBill,
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
            BigDecimal transportationCharge, BigDecimal otherExpenses, BigDecimal discountAmount, Long createdByUserId) {
        // Always calculate total amount (no tax) to include all charges: subtotal +
        // serviceCharge + labourCharge + transportationCharge + otherExpenses - discountAmount
        BigDecimal totalAmount = subtotal.add(serviceCharge).add(labourCharge)
                .add(transportationCharge).add(otherExpenses).subtract(discountAmount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO;
        }
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);

        Bill bill = new Bill();
        bill.setBillKind(BillKind.NON_GST);
        bill.setBillNumber(billNumber);
        bill.setCustomer(customer);
        bill.setLocation(location != null ? location.trim() : null);
        bill.setBillDate(LocalDate.now());
        bill.setTotalSqft(totalSqft.setScale(2, RoundingMode.HALF_UP));
        bill.setSubtotal(subtotal);
        bill.setTaxRate(BigDecimal.ZERO);
        bill.setTaxAmount(BigDecimal.ZERO);
        bill.setServiceCharge(serviceCharge);
        bill.setLabourCharge(labourCharge);
        bill.setTransportationCharge(transportationCharge);
        bill.setOtherExpenses(otherExpenses);
        bill.setDiscountAmount(discountAmount);
        bill.setTotalAmount(totalAmount);
        bill.setPaymentStatus(Bill.PaymentStatus.DUE);
        bill.setPaymentMethod("-");
        bill.setCreatedByUserId(createdByUserId);
        System.out.println("[Bill Non-GST] Bill " + billNumber + " created with otherExpenses=" + otherExpenses
                + ", totalAmount=" + totalAmount);

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

        int lineNo = 1;
        for (BillItemDTO itemDTO : billRequestDTO.getItems()) {
            BillLineItem item = new BillLineItem();
            item.setLineNo(lineNo++);
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

        Bill savedBill = billRepository.save(bill);

        BigDecimal advanceApplied = customerAdvanceService.applyAdvanceFifo(
                customer.getId(), BillKind.NON_GST, savedBill.getId(), totalAmount);
        BigDecimal netForPayments = totalAmount.subtract(advanceApplied).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        List<ResolvedLine> payLines = resolvePaymentLines(billRequestDTO, netForPayments, savedBill.getBillDate());
        BigDecimal totalPaidCash = sumResolvedLines(payLines);
        BigDecimal covered = advanceApplied.add(totalPaidCash);
        savedBill.setPaidAmount(totalPaidCash.setScale(2, RoundingMode.HALF_UP));
        savedBill.setPaymentStatus(toBillPaymentStatus(totalAmount, covered));
        savedBill.setPaymentMethod(buildPaymentMethodSummary(payLines, totalAmount, totalPaidCash, advanceApplied));
        billRepository.save(savedBill);

        // Deduct stock from products after bill is saved (grouped by productId)
        String nonGstBillNote = "Stock deducted via Non-GST bill " + savedBill.getBillNumber() + " (id=" + savedBill.getId() + ")";
        for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
            productService.deductStock(entry.getKey(), entry.getValue(), savedBill.getId(), nonGstBillNote);
        }

        // Deduct stock from products after bill is saved (grouped by product name)
        for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
            productService.deductStockByName(entry.getKey(), entry.getValue(), savedBill.getId(), nonGstBillNote);
        }

        persistResolvedPayments(BillKind.NON_GST, savedBill.getId(), payLines, resolveBillLocation(savedBill, customer));

        // Convert to response DTO
        BillResponseDTO responseDTO = convertToResponseDTO(savedBill,
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
            Bill bill = billRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            if (bill.getBillKind() != BillKind.GST) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            Customer customer = bill.getCustomer();
            String billLocation = resolveBillLocation(bill, customer);
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId);
            BigDecimal paid = existing.stream().map(BillPayment::getAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
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
            row.setAmount(amount);
            row.setPaymentMode(mode);
            row.setPaymentDate(paymentRequest.getPaymentDate() != null ? paymentRequest.getPaymentDate() : LocalDate.now());
            row.setCreatedBy(actorUserId);
            row.setUpdatedBy(actorUserId);
            BillPayment saved = billPaymentRepository.save(row);
            publishBillPaymentLedgerSync(billLocation, BillKind.GST, billId, saved, true);
            refreshBillFinancials(bill, billLocation);
            BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            return convertToResponseDTO(bill, billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId),
                    updatedAdv);
        }
        Bill bill = billRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        if (bill.getBillKind() != BillKind.NON_GST) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        Customer customer = bill.getCustomer();
        String billLocation = resolveBillLocation(bill, customer);
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId);
        BigDecimal paid = existing.stream().map(BillPayment::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
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
        row.setAmount(amount);
        row.setPaymentMode(mode);
        row.setPaymentDate(paymentRequest.getPaymentDate() != null ? paymentRequest.getPaymentDate() : LocalDate.now());
        row.setCreatedBy(actorUserId);
        row.setUpdatedBy(actorUserId);
        BillPayment saved = billPaymentRepository.save(row);
        publishBillPaymentLedgerSync(billLocation, BillKind.NON_GST, billId, saved, true);
        refreshBillFinancials(bill, billLocation);
        BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        return convertToResponseDTO(bill,
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
        BigDecimal oldAmount = row.getAmount() != null ? row.getAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BillPaymentMode newMode = parseBillPaymentMode(paymentRequest.getPaymentMode());
        BigDecimal newAmount = BigDecimal.valueOf(paymentRequest.getAmount()).setScale(2, RoundingMode.HALF_UP);

        if (kind == BillKind.GST) {
            Bill bill = billRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            if (bill.getBillKind() != BillKind.GST) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId);
            BigDecimal paid = existing.stream().map(BillPayment::getAmount).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
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
            publishBillPaymentLedgerSync(billLocation, BillKind.GST, billId, saved, true);
            refreshBillFinancials(bill, billLocation);
            BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            return convertToResponseDTO(bill, billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId),
                    updatedAdv);
        }

        Bill bill = billRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        if (bill.getBillKind() != BillKind.NON_GST) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        List<BillPayment> existing = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId);
        BigDecimal paid = existing.stream().map(BillPayment::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
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
        publishBillPaymentLedgerSync(billLocation, BillKind.NON_GST, billId, saved, true);
        refreshBillFinancials(bill, billLocation);
        BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        return convertToResponseDTO(bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId), updatedAdv);
    }

    public BillResponseDTO deletePaymentOnBill(Long billId, String billType, Long paymentId, String location, Long actorUserId) {
        BillKind kind = "GST".equalsIgnoreCase(billType) ? BillKind.GST : BillKind.NON_GST;
        BillPayment row = billPaymentRepository.findByIdAndBillKindAndBillId(paymentId, kind, billId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));

        if (kind == BillKind.GST) {
            Bill bill = billRepository.findByIdWithItemsAndProducts(billId)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + billId));
            if (bill.getBillKind() != BillKind.GST) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            String billLocation = resolveBillLocation(bill, bill.getCustomer());
            if (!Objects.equals(location, billLocation)) {
                throw new RuntimeException("GST Bill not found with id: " + billId);
            }
            row.setIsDeleted(true);
            row.setUpdatedBy(actorUserId);
            BillPayment saved = billPaymentRepository.save(row);
            publishBillPaymentLedgerSync(billLocation, BillKind.GST, billId, saved, false);
            refreshBillFinancials(bill, billLocation);
            BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, billId);
            return convertToResponseDTO(bill, billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, billId),
                    updatedAdv);
        }

        Bill bill = billRepository.findByIdWithItemsAndProducts(billId)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + billId));
        if (bill.getBillKind() != BillKind.NON_GST) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        String billLocation = resolveBillLocation(bill, bill.getCustomer());
        if (!Objects.equals(location, billLocation)) {
            throw new RuntimeException("NonGST Bill not found with id: " + billId);
        }
        row.setIsDeleted(true);
        row.setUpdatedBy(actorUserId);
        BillPayment saved = billPaymentRepository.save(row);
        publishBillPaymentLedgerSync(billLocation, BillKind.NON_GST, billId, saved, false);
        refreshBillFinancials(bill, billLocation);
        BigDecimal updatedAdv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, billId);
        return convertToResponseDTO(bill,
                billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, billId), updatedAdv);
    }

    public BillResponseDTO getBillById(Long id, String billType, String location) {
        if ("GST".equalsIgnoreCase(billType) || "gst".equalsIgnoreCase(billType)) {
            Bill bill = billRepository.findByIdWithItemsAndProducts(id)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + id));
            if (bill.getBillKind() != BillKind.GST) {
                throw new RuntimeException("GST Bill not found with id: " + id);
            }
            if (!location.equals(resolveBillLocation(bill, bill.getCustomer()))) {
                throw new RuntimeException("GST Bill not found with id: " + id);
            }
            List<BillPayment> payments = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.GST, id);
            BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.GST, id);
            return convertToResponseDTO(bill, payments, adv);
        }
        Bill bill = billRepository.findByIdWithItemsAndProducts(id)
                .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + id));
        if (bill.getBillKind() != BillKind.NON_GST) {
            throw new RuntimeException("NonGST Bill not found with id: " + id);
        }
        if (!location.equals(resolveBillLocation(bill, bill.getCustomer()))) {
            throw new RuntimeException("NonGST Bill not found with id: " + id);
        }
        List<BillPayment> payments = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(BillKind.NON_GST, id);
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(BillKind.NON_GST, id);
        return convertToResponseDTO(bill, payments, adv);
    }

    public BillResponseDTO getBillByBillNumber(String billNumber, String location) {
        Bill bill = billRepository.findByBillNumberAndBillLocationWithItemsAndProducts(billNumber, location).orElse(null);
        if (bill == null) {
            throw new RuntimeException("Bill not found with bill number: " + billNumber);
        }
        BillKind kind = bill.getBillKind();
        List<BillPayment> payments = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(kind, bill.getId());
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(kind, bill.getId());
        return convertToResponseDTO(bill, payments, adv);
    }

    public List<BillResponseDTO> getAllBills(String location, Long createdByUserId) {
        List<Bill> entities = createdByUserId != null
                ? billRepository.findByBillLocationAndCreatedByUserId(location, createdByUserId)
                : billRepository.findByBillLocation(location);

        List<Bill> gstEntities = entities.stream().filter(b -> b.getBillKind() == BillKind.GST).toList();
        List<Bill> nonEntities = entities.stream().filter(b -> b.getBillKind() == BillKind.NON_GST).toList();

        Map<String, List<BillPayment>> paymentMap = loadPaymentsGrouped(gstEntities, nonEntities);
        Map<String, BigDecimal> advanceMap = customerAdvanceService.sumAdvanceUsedGrouped(
                gstEntities.stream().map(Bill::getId).toList(),
                nonEntities.stream().map(Bill::getId).toList());

        List<BillResponseDTO> gstBills = gstEntities.stream()
                .map(b -> convertToResponseDTO(b,
                        paymentMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), List.of()),
                        advanceMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        List<BillResponseDTO> nonGstBills = nonEntities.stream()
                .map(b -> convertToResponseDTO(b,
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
        List<Bill> all = billRepository.findByCustomer(customer);
        List<Bill> gstEntities = all.stream().filter(b -> b.getBillKind() == BillKind.GST).toList();
        List<Bill> nonEntities = all.stream().filter(b -> b.getBillKind() == BillKind.NON_GST).toList();
        Map<String, List<BillPayment>> paymentMap = loadPaymentsGrouped(gstEntities, nonEntities);
        Map<String, BigDecimal> advanceMap = customerAdvanceService.sumAdvanceUsedGrouped(
                gstEntities.stream().map(Bill::getId).toList(),
                nonEntities.stream().map(Bill::getId).toList());

        List<BillResponseDTO> gstBills = gstEntities.stream()
                .map(b -> convertToResponseDTO(b,
                        paymentMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), List.of()),
                        advanceMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        List<BillResponseDTO> nonGstBills = nonEntities.stream()
                .map(b -> convertToResponseDTO(b,
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
        List<Bill> all = billRepository.findByCustomer(customer);
        List<Bill> gstEntities = all.stream().filter(b -> b.getBillKind() == BillKind.GST).toList();
        List<Bill> nonEntities = all.stream().filter(b -> b.getBillKind() == BillKind.NON_GST).toList();

        Map<String, List<BillPayment>> paymentMap = loadPaymentsGrouped(gstEntities, nonEntities);
        Map<String, BigDecimal> advanceMap = customerAdvanceService.sumAdvanceUsedGrouped(
                gstEntities.stream().map(Bill::getId).toList(),
                nonEntities.stream().map(Bill::getId).toList());

        final String loc = location == null ? "" : location.trim();

        List<BillResponseDTO> gstBills = gstEntities.stream()
                .filter(b -> {
                    String bLoc = resolveBillLocation(b, b.getCustomer());
                    return bLoc != null && bLoc.equals(loc);
                })
                .map(b -> convertToResponseDTO(b,
                        paymentMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), List.of()),
                        advanceMap.getOrDefault(paymentKey(BillKind.GST, b.getId()), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        List<BillResponseDTO> nonGstBills = nonEntities.stream()
                .filter(b -> {
                    String bLoc = resolveBillLocation(b, b.getCustomer());
                    return bLoc != null && bLoc.equals(loc);
                })
                .map(b -> convertToResponseDTO(b,
                        paymentMap.getOrDefault(paymentKey(BillKind.NON_GST, b.getId()), List.of()),
                        advanceMap.getOrDefault(paymentKey(BillKind.NON_GST, b.getId()), BigDecimal.ZERO)))
                .collect(Collectors.toList());

        return Stream.concat(gstBills.stream(), nonGstBills.stream())
                .collect(Collectors.toList());
    }

    private BillResponseDTO convertToResponseDTO(Bill bill, List<BillPayment> paymentRows,
            BigDecimal advanceUsed) {
        if (bill.getBillKind() == BillKind.GST) {
            return convertGstBillToResponseDTO(bill, paymentRows, advanceUsed);
        }
        return convertNonGstBillToResponseDTO(bill, paymentRows, advanceUsed);
    }

    private BillResponseDTO convertGstBillToResponseDTO(Bill bill, List<BillPayment> paymentRows,
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
        responseDTO.setTaxPercentage(bill.getTaxRate() != null ? bill.getTaxRate().doubleValue() : 0.0);
        responseDTO.setTaxAmount(bill.getTaxAmount() != null ? bill.getTaxAmount().doubleValue() : 0.0);
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

        // Convert items
        List<BillItemDTO> itemDTOs = bill.getItems().stream()
                .map(item -> {
                    BillItemDTO itemDTO = new BillItemDTO();
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

    private BillResponseDTO convertNonGstBillToResponseDTO(Bill bill, List<BillPayment> paymentRows,
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

        // Convert items
        List<BillItemDTO> itemDTOs = bill.getItems().stream()
                .map(item -> {
                    BillItemDTO itemDTO = new BillItemDTO();
                    itemDTO.setItemName(item.getProductName());
                    itemDTO.setCategory(item.getProductType());
                    // Use pricePerSqftAfter from product if available, otherwise use stored
                    // pricePerUnit
                    double priceToUse = item.getPricePerUnit().doubleValue();
                    double purchasePrice = 0.0;
                    System.out.println("prince to use " + priceToUse);
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
        BigDecimal paid = paymentRows.stream()
                .map(BillPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        boolean inferLegacyFull = adv.compareTo(BigDecimal.ZERO) == 0
                && paymentRows.isEmpty()
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
        dto.setPayments(paymentRows.stream().map(this::toPaymentResponseDTO).collect(Collectors.toList()));

        String summary;
        if (!paymentRows.isEmpty()) {
            summary = formatSummaryFromPersisted(paymentRows);
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

    private static String resolveBillLocation(Bill bill, Customer customer) {
        if (bill != null && bill.getLocation() != null && !bill.getLocation().isBlank()) {
            return bill.getLocation().trim();
        }
        if (customer != null && customer.getLocation() != null && !customer.getLocation().isBlank()) {
            return customer.getLocation().trim();
        }
        return null;
    }

    private void refreshBillFinancials(Bill bill, String location) {
        BillKind kind = bill.getBillKind();
        List<BillPayment> rows = billPaymentRepository.findByBillKindAndBillIdOrderByIdAsc(kind, bill.getId());
        BigDecimal adv = customerAdvanceService.sumAdvanceUsedForBill(kind, bill.getId()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = rows.stream().map(BillPayment::getAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal covered = adv.add(paid).setScale(2, RoundingMode.HALF_UP);
        bill.setPaidAmount(paid);
        bill.setPaymentStatus(toBillPaymentStatus(bill.getTotalAmount(), covered));
        List<ResolvedLine> lines = rows.stream()
                .map(r -> new ResolvedLine(
                        r.getAmount() != null ? r.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        r.getPaymentMode() != null ? r.getPaymentMode() : BillPaymentMode.OTHER,
                        r.getPaymentDate()))
                .toList();
        bill.setPaymentMethod(buildPaymentMethodSummary(lines, bill.getTotalAmount(), paid, adv));
        if (location != null && !location.isBlank() && (bill.getLocation() == null || bill.getLocation().isBlank())) {
            bill.setLocation(location.trim());
        }
        billRepository.save(bill);
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

    private Map<String, List<BillPayment>> loadPaymentsGrouped(List<Bill> gst, List<Bill> non) {
        Map<String, List<BillPayment>> map = new HashMap<>();
        if (gst != null && !gst.isEmpty()) {
            Collection<Long> ids = gst.stream().map(Bill::getId).toList();
            billPaymentRepository.findByBillKindAndBillIdIn(BillKind.GST, ids).forEach(p -> map
                    .computeIfAbsent(paymentKey(BillKind.GST, p.getBillId()), k -> new ArrayList<>()).add(p));
        }
        if (non != null && !non.isEmpty()) {
            Collection<Long> ids = non.stream().map(Bill::getId).toList();
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

    private List<ResolvedLine> resolvePaymentLines(BillRequestDTO req, BigDecimal totalAmount, LocalDate billDate) {
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

    private static Bill.PaymentStatus toBillPaymentStatus(BigDecimal totalAmount, BigDecimal paid) {
        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        paid = paid.setScale(2, RoundingMode.HALF_UP);
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return Bill.PaymentStatus.DUE;
        }
        if (paid.compareTo(totalAmount) >= 0 || paid.subtract(totalAmount).abs().compareTo(PAY_ROUND_EPS) <= 0) {
            return Bill.PaymentStatus.PAID;
        }
        return Bill.PaymentStatus.PARTIAL;
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
            row.setAmount(line.amount());
            row.setPaymentMode(line.mode());
            row.setPaymentDate(line.paymentDate());
            rows.add(row);
        }
        List<BillPayment> savedRows = billPaymentRepository.saveAll(rows);
        if (location != null && !location.isBlank()) {
            for (BillPayment p : savedRows) {
                publishBillPaymentLedgerSync(location.trim(), kind, billId, p, true);
            }
        }
    }

    private void publishBillPaymentLedgerSync(String billLocation, BillKind kind, Long billId, BillPayment saved,
            boolean active) {
        if (billLocation == null || billLocation.isBlank() || saved == null || saved.getId() == null) {
            return;
        }
        eventPublisher.publishEvent(new BillPaymentLedgerSyncEvent(
                billLocation.trim(),
                kind,
                billId,
                saved.getId(),
                saved.getPaymentMode(),
                saved.getAmount(),
                saved.getPaymentDate(),
                active));
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

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
