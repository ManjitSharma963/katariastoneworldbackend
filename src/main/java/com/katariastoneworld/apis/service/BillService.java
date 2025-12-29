package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.BillItemDTO;
import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional
public class BillService {
    
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
    
    public BillResponseDTO createBill(BillRequestDTO billRequestDTO, String location) {
        // Get or create customer with details
        System.out.println("Creating bill: service" + billRequestDTO);
        Customer customer = customerService.getOrCreateCustomer(
                billRequestDTO.getCustomerMobileNumber(),
                billRequestDTO.getCustomerName(),
                billRequestDTO.getAddress(),
                billRequestDTO.getGstin(),
                billRequestDTO.getCustomerEmail(),
                location
        );
        
        // Calculate total sqft from items (quantity represents sqft)
        // Calculate total quantity (sum of all item quantities regardless of unit)
        // Note: This is called "totalSqft" for backward compatibility, but it's actually
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
        
        // Discount amount
        BigDecimal discountAmount = BigDecimal.valueOf(billRequestDTO.getDiscountAmount())
                .setScale(2, RoundingMode.HALF_UP);
        
        // Determine if GST or NonGST based on tax percentage
        BigDecimal taxPercentage = BigDecimal.valueOf(billRequestDTO.getTaxPercentage());
        boolean isGST = taxPercentage.compareTo(BigDecimal.ZERO) > 0;
        
        // Generate bill number based on bill type (separate series for GST and Non-GST)
        String billNumber;
        if (isGST) {
            billNumber = billNumberGeneratorService.generateGSTBillNumber();
        } else {
            billNumber = billNumberGeneratorService.generateNonGSTBillNumber();
        }
        
        if (isGST) {
            return createGSTBill(billRequestDTO, customer, billNumber, totalSqft, subtotal, 
                    taxPercentage, serviceCharge, labourCharge, transportationCharge, discountAmount);
        } else {
            return createNonGSTBill(billRequestDTO, customer, billNumber, totalSqft, subtotal, 
                    serviceCharge, labourCharge, transportationCharge, discountAmount);
        }
    }
    
    private BillResponseDTO createGSTBill(BillRequestDTO billRequestDTO, Customer customer, 
                                         String billNumber, BigDecimal totalSqft, BigDecimal subtotal,
                                         BigDecimal taxRate, BigDecimal serviceCharge, BigDecimal labourCharge,
                                         BigDecimal transportationCharge, BigDecimal discountAmount) {
        // Calculate tax amount
        BigDecimal taxAmount = subtotal.multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        
        // Use provided totalAmount if available, otherwise calculate it
        BigDecimal totalAmount;
        if (billRequestDTO.getTotalAmount() != null) {
            totalAmount = BigDecimal.valueOf(billRequestDTO.getTotalAmount())
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            // Calculate total amount: subtotal + tax + serviceCharge + labourCharge + transportationCharge - discountAmount
            totalAmount = subtotal.add(taxAmount).add(serviceCharge)
                    .add(labourCharge).add(transportationCharge).subtract(discountAmount);
            if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
                totalAmount = BigDecimal.ZERO;
            }
            totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        }
        
        // Create GST Bill entity
        BillGST bill = new BillGST();
        bill.setBillNumber(billNumber);
        bill.setCustomer(customer);
        bill.setBillDate(LocalDate.now());
        bill.setTotalSqft(totalSqft.setScale(2, RoundingMode.HALF_UP));
        bill.setSubtotal(subtotal);
        bill.setTaxRate(taxRate);
        bill.setTaxAmount(taxAmount);
        bill.setServiceCharge(serviceCharge);
        bill.setLabourCharge(labourCharge);
        bill.setTransportationCharge(transportationCharge);
        bill.setDiscountAmount(discountAmount);
        bill.setTotalAmount(totalAmount);
        bill.setPaymentStatus(BillGST.PaymentStatus.PAID);
        
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
                    // Product not found by name - this is OK, item will be saved without product link
                    // Stock deduction will still happen if product exists
                }
            }
            
            // Set unit from product if available, otherwise use from DTO or default to "sqft"
            if (product != null && product.getUnit() != null && !product.getUnit().trim().isEmpty()) {
                item.setUnit(product.getUnit());
            } else if (itemDTO.getUnit() != null && !itemDTO.getUnit().trim().isEmpty()) {
                item.setUnit(itemDTO.getUnit());
            } else {
                item.setUnit("sqft"); // Default for backward compatibility
            }
            
            bill.addItem(item);
        }
        
        // Save GST bill
        BillGST savedBill = billGSTRepository.save(bill);
        
        // Deduct stock from products after bill is saved (grouped by productId)
        for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
            productService.deductStock(entry.getKey(), entry.getValue());
        }
        
        // Deduct stock from products after bill is saved (grouped by product name)
        for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
            productService.deductStockByName(entry.getKey(), entry.getValue());
        }
        
        // Convert to response DTO
        BillResponseDTO responseDTO = convertGSTToResponseDTO(savedBill);
        // Automatically set simpleBill to true if taxPercentage is 0, or use the flag from request
        boolean isSimpleBill = (billRequestDTO.getSimpleBill() != null && billRequestDTO.getSimpleBill()) 
                              || (billRequestDTO.getTaxPercentage() != null && billRequestDTO.getTaxPercentage() == 0);
        responseDTO.setSimpleBill(isSimpleBill);
        
        // Send email to customer asynchronously (non-blocking)
        emailService.sendBillEmail(responseDTO, customer.getEmail());
        System.out.println("Bill created successfully. Email will be sent in background for bill: " + savedBill.getBillNumber());
        
        return responseDTO;
    }
    
    private BillResponseDTO createNonGSTBill(BillRequestDTO billRequestDTO, Customer customer, 
                                             String billNumber, BigDecimal totalSqft, BigDecimal subtotal,
                                             BigDecimal serviceCharge, BigDecimal labourCharge,
                                             BigDecimal transportationCharge, BigDecimal discountAmount) {
        // Use provided totalAmount if available, otherwise calculate it (no tax)
        BigDecimal totalAmount;
        if (billRequestDTO.getTotalAmount() != null) {
            totalAmount = BigDecimal.valueOf(billRequestDTO.getTotalAmount())
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            // Calculate total amount (no tax): subtotal + serviceCharge + labourCharge + transportationCharge - discountAmount
            totalAmount = subtotal.add(serviceCharge).add(labourCharge)
                    .add(transportationCharge).subtract(discountAmount);
            if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
                totalAmount = BigDecimal.ZERO;
            }
            totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        }
        
        // Create NonGST Bill entity
        BillNonGST bill = new BillNonGST();
        bill.setBillNumber(billNumber);
        bill.setCustomer(customer);
        bill.setBillDate(LocalDate.now());
        bill.setTotalSqft(totalSqft.setScale(2, RoundingMode.HALF_UP));
        bill.setSubtotal(subtotal);
        bill.setServiceCharge(serviceCharge);
        bill.setLabourCharge(labourCharge);
        bill.setTransportationCharge(transportationCharge);
        bill.setDiscountAmount(discountAmount);
        bill.setTotalAmount(totalAmount);
        bill.setPaymentStatus(BillNonGST.PaymentStatus.PAID);
        
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
                    // Product not found by name - this is OK, item will be saved without product link
                    // Stock deduction will still happen if product exists
                }
            }
            
            // Set unit from product if available, otherwise use from DTO or default to "sqft"
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
        
        // Deduct stock from products after bill is saved (grouped by productId)
        for (Map.Entry<Long, BigDecimal> entry : productQuantitiesById.entrySet()) {
            productService.deductStock(entry.getKey(), entry.getValue());
        }
        
        // Deduct stock from products after bill is saved (grouped by product name)
        for (Map.Entry<String, BigDecimal> entry : productQuantitiesByName.entrySet()) {
            productService.deductStockByName(entry.getKey(), entry.getValue());
        }
        
        // Convert to response DTO
        BillResponseDTO responseDTO = convertNonGSTToResponseDTO(savedBill);
        // Automatically set simpleBill to true if taxPercentage is 0, or use the flag from request
        boolean isSimpleBill = (billRequestDTO.getSimpleBill() != null && billRequestDTO.getSimpleBill()) 
                              || (billRequestDTO.getTaxPercentage() != null && billRequestDTO.getTaxPercentage() == 0);
        responseDTO.setSimpleBill(isSimpleBill);
        
        // Send email to customer asynchronously (non-blocking)
        emailService.sendBillEmail(responseDTO, customer.getEmail());
        System.out.println("Bill created successfully. Email will be sent in background for bill: " + savedBill.getBillNumber());
        
        return responseDTO;
    }
    
    public BillResponseDTO getBillById(Long id, String billType, String location) {
        if ("GST".equalsIgnoreCase(billType) || "gst".equalsIgnoreCase(billType)) {
            BillGST bill = billGSTRepository.findByIdWithItemsAndProducts(id)
                    .orElseThrow(() -> new RuntimeException("GST Bill not found with id: " + id));
            // Verify location matches
            if (!location.equals(bill.getCustomer().getLocation())) {
                throw new RuntimeException("GST Bill not found with id: " + id);
            }
            return convertGSTToResponseDTO(bill);
        } else {
            BillNonGST bill = billNonGSTRepository.findByIdWithItemsAndProducts(id)
                    .orElseThrow(() -> new RuntimeException("NonGST Bill not found with id: " + id));
            // Verify location matches
            if (!location.equals(bill.getCustomer().getLocation())) {
                throw new RuntimeException("NonGST Bill not found with id: " + id);
            }
            return convertNonGSTToResponseDTO(bill);
        }
    }
    
    public BillResponseDTO getBillByBillNumber(String billNumber, String location) {
        // Check GST bills first
        BillGST gstBill = billGSTRepository.findByBillNumberAndCustomerLocationWithItemsAndProducts(billNumber, location).orElse(null);
        if (gstBill != null) {
            return convertGSTToResponseDTO(gstBill);
        }
        
        // Check NonGST bills
        BillNonGST nonGstBill = billNonGSTRepository.findByBillNumberAndCustomerLocationWithItemsAndProducts(billNumber, location).orElse(null);
        if (nonGstBill != null) {
            return convertNonGSTToResponseDTO(nonGstBill);
        }
        
        throw new RuntimeException("Bill not found with bill number: " + billNumber);
    }
    
    public List<BillResponseDTO> getAllBills(String location) {
        // Combine both GST and NonGST bills for the location, sorted by bill date (most recent first)
        List<BillResponseDTO> gstBills = billGSTRepository.findByCustomerLocation(location).stream()
                .map(this::convertGSTToResponseDTO)
                .collect(Collectors.toList());
        
        List<BillResponseDTO> nonGstBills = billNonGSTRepository.findByCustomerLocation(location).stream()
                .map(this::convertNonGSTToResponseDTO)
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
        return getAllBills(location);
    }
    
    public List<BillResponseDTO> getBillsByMobileNumber(String mobileNumber) {
        Customer customer = customerService.getCustomerByPhone(mobileNumber);
        
        // Combine both GST and NonGST bills for the customer
        List<BillResponseDTO> gstBills = billGSTRepository.findByCustomer(customer).stream()
                .map(this::convertGSTToResponseDTO)
                .collect(Collectors.toList());
        
        List<BillResponseDTO> nonGstBills = billNonGSTRepository.findByCustomer(customer).stream()
                .map(this::convertNonGSTToResponseDTO)
                .collect(Collectors.toList());
        
        return Stream.concat(gstBills.stream(), nonGstBills.stream())
                .collect(Collectors.toList());
    }
    
    private BillResponseDTO convertGSTToResponseDTO(BillGST bill) {
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
        responseDTO.setTotalSqft(bill.getTotalSqft().doubleValue());
        responseDTO.setSubtotal(bill.getSubtotal().doubleValue());
        responseDTO.setTaxPercentage(bill.getTaxRate().doubleValue());
        responseDTO.setTaxAmount(bill.getTaxAmount().doubleValue());
        responseDTO.setServiceCharge(bill.getServiceCharge().doubleValue());
        responseDTO.setLabourCharge(bill.getLabourCharge().doubleValue());
        responseDTO.setTransportationCharge(bill.getTransportationCharge().doubleValue());
        responseDTO.setDiscountAmount(bill.getDiscountAmount().doubleValue());
        responseDTO.setTotalAmount(bill.getTotalAmount().doubleValue());
        responseDTO.setPaymentStatus(bill.getPaymentStatus().name());
        responseDTO.setPaymentMethod(bill.getPaymentMethod());
        responseDTO.setNotes(bill.getNotes());
        responseDTO.setCreatedAt(bill.getCreatedAt());
        
        // Convert items
        List<BillItemDTO> itemDTOs = bill.getItems().stream()
                .map(item -> {
                    BillItemDTO itemDTO = new BillItemDTO();
                    itemDTO.setItemName(item.getProductName());
                    itemDTO.setCategory(item.getProductType());
                    // Use pricePerSqftAfter from product if available, otherwise use stored pricePerUnit
                    double priceToUse = item.getPricePerUnit().doubleValue();
                    try {
                        Product product = item.getProduct();
                        if (product != null && product.getPricePerSqftAfter() != null) {
                            priceToUse = product.getPricePerSqftAfter().doubleValue();
                        }
                    } catch (Exception e) {
                        // If product is lazy-loaded and session is closed, use stored pricePerUnit
                        // This is expected behavior - just use the stored price
                    }
                    itemDTO.setPricePerUnit(priceToUse);
                    itemDTO.setQuantity(item.getQuantity().doubleValue()); // Changed to doubleValue() to support decimal quantities
                    itemDTO.setUnit(item.getUnit() != null ? item.getUnit() : "sqft"); // Default for backward compatibility
                    itemDTO.setProductImageUrl(item.getProductImageUrl());
                    try {
                        Product product = item.getProduct();
                        if (product != null) {
                            itemDTO.setProductId(product.getId());
                        }
                    } catch (Exception e) {
                        // If product is lazy-loaded and session is closed, skip setting productId
                    }
                    return itemDTO;
                })
                .collect(Collectors.toList());
        
        responseDTO.setItems(itemDTOs);
        
        return responseDTO;
    }
    
    private BillResponseDTO convertNonGSTToResponseDTO(BillNonGST bill) {
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
        responseDTO.setDiscountAmount(bill.getDiscountAmount().doubleValue());
        responseDTO.setTotalAmount(bill.getTotalAmount().doubleValue());
        responseDTO.setPaymentStatus(bill.getPaymentStatus().name());
        responseDTO.setPaymentMethod(bill.getPaymentMethod());
        responseDTO.setNotes(bill.getNotes());
        responseDTO.setCreatedAt(bill.getCreatedAt());
        
        // Convert items
        List<BillItemDTO> itemDTOs = bill.getItems().stream()
                .map(item -> {
                    BillItemDTO itemDTO = new BillItemDTO();
                    itemDTO.setItemName(item.getProductName());
                    itemDTO.setCategory(item.getProductType());
                    // Use pricePerSqftAfter from product if available, otherwise use stored pricePerUnit
                    double priceToUse = item.getPricePerUnit().doubleValue();
                    try {
                        Product product = item.getProduct();
                        if (product != null && product.getPricePerSqftAfter() != null) {
                            priceToUse = product.getPricePerSqftAfter().doubleValue();
                        }
                    } catch (Exception e) {
                        // If product is lazy-loaded and session is closed, use stored pricePerUnit
                        // This is expected behavior - just use the stored price
                    }
                    itemDTO.setPricePerUnit(priceToUse);
                    itemDTO.setQuantity(item.getQuantity().doubleValue()); // Changed to doubleValue() to support decimal quantities
                    itemDTO.setUnit(item.getUnit() != null ? item.getUnit() : "sqft"); // Default for backward compatibility
                    itemDTO.setProductImageUrl(item.getProductImageUrl());
                    try {
                        Product product = item.getProduct();
                        if (product != null) {
                            itemDTO.setProductId(product.getId());
                        }
                    } catch (Exception e) {
                        // If product is lazy-loaded and session is closed, skip setting productId
                    }
                    return itemDTO;
                })
                .collect(Collectors.toList());
        
        responseDTO.setItems(itemDTOs);
        
        return responseDTO;
    }
}
