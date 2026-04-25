package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.InventoryReservation;
import com.katariastoneworld.apis.entity.InventoryReservationStatus;
import com.katariastoneworld.apis.entity.Product;
import com.katariastoneworld.apis.repository.InventoryReservationRepository;
import com.katariastoneworld.apis.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
public class InventoryReservationService {

    private static final int DEFAULT_RESERVATION_MINUTES = 30;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private ProductRepository productRepository;

    /**
     * Soft-reserve stock for a bill after the bill row exists. Reduces effective available for other buyers until consumed or released.
     */
    public void reserveForBill(
            Long billId,
            BillKind billKind,
            Map<Long, BigDecimal> quantitiesByProductId,
            Map<String, BigDecimal> quantitiesByProductName,
            String location) {
        Objects.requireNonNull(billId);
        Objects.requireNonNull(billKind);
        String kindStr = billKind.name();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(DEFAULT_RESERVATION_MINUTES);

        if (quantitiesByProductId != null) {
            for (Map.Entry<Long, BigDecimal> e : quantitiesByProductId.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                reserveOne(e.getKey(), scale(e.getValue()), billId, kindStr, expiresAt, location);
            }
        }
        if (quantitiesByProductName != null) {
            for (Map.Entry<String, BigDecimal> e : quantitiesByProductName.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Product p = productRepository.findByName(e.getKey())
                        .filter(pr -> location == null || location.equals(pr.getLocation()))
                        .orElse(null);
                if (p == null) {
                    continue;
                }
                reserveOne(p.getId(), scale(e.getValue()), billId, kindStr, expiresAt, location);
            }
        }
    }

    private void reserveOne(Long productId, BigDecimal qty, Long billId, String billKind, LocalDateTime expiresAt, String location) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));
        if (location != null && !location.equals(product.getLocation())) {
            throw new RuntimeException("Product not found with id: " + productId);
        }
        BigDecimal current = product.getQuantity() != null ? product.getQuantity() : BigDecimal.ZERO;
        BigDecimal reserved = scale(inventoryReservationRepository.sumActiveReservedByProductId(productId, LocalDateTime.now()));
        BigDecimal available = current.subtract(reserved);
        if (available.subtract(qty).compareTo(BigDecimal.ZERO) < 0) {
            String unit = product.getUnit() != null ? product.getUnit() : "sqft";
            throw new RuntimeException("Insufficient stock for product: " + product.getName()
                    + ". Available (after reservations): " + available + " " + unit + ", Requested: " + qty + " " + unit);
        }
        InventoryReservation r = new InventoryReservation();
        r.setProductId(productId);
        r.setReservedQuantity(qty);
        r.setReferenceId(billId);
        r.setBillKind(billKind);
        r.setStatus(InventoryReservationStatus.ACTIVE);
        r.setExpiresAt(expiresAt);
        inventoryReservationRepository.save(r);
    }

    public void consumeForBill(Long billId, BillKind billKind) {
        if (billId == null || billKind == null) {
            return;
        }
        for (InventoryReservation r : inventoryReservationRepository.findByReferenceIdAndBillKindAndStatus(
                billId, billKind.name(), InventoryReservationStatus.ACTIVE)) {
            r.setStatus(InventoryReservationStatus.CONSUMED);
            inventoryReservationRepository.save(r);
        }
    }

    public void releaseForBill(Long billId, BillKind billKind) {
        if (billId == null || billKind == null) {
            return;
        }
        for (InventoryReservation r : inventoryReservationRepository.findByReferenceIdAndBillKindAndStatus(
                billId, billKind.name(), InventoryReservationStatus.ACTIVE)) {
            r.setStatus(InventoryReservationStatus.RELEASED);
            inventoryReservationRepository.save(r);
        }
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal sumActiveReservedForProduct(Long productId) {
        BigDecimal v = inventoryReservationRepository.sumActiveReservedByProductId(productId, LocalDateTime.now());
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Active reservations for other buyers only (excludes this bill's own holds), used when applying a sale deduction.
     */
    @Transactional(readOnly = true)
    public BigDecimal sumActiveReservedExcludingBill(Long productId, Long billId, BillKind billKind) {
        if (billId == null || billKind == null) {
            return sumActiveReservedForProduct(productId);
        }
        BigDecimal total = sumActiveReservedForProduct(productId);
        BigDecimal own = nvl(inventoryReservationRepository.sumActiveReservedForBill(
                productId, billId, billKind.name(), LocalDateTime.now()));
        return total.subtract(own).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
