package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BillNumberGeneratorService {

    /** Serialize GST number allocation (same table + unique bill_number) to avoid duplicate under concurrency. */
    private final Object gstBillNumberLock = new Object();
    /** Serialize Non-GST number allocation. */
    private final Object nonGstBillNumberLock = new Object();
    
    @Autowired
    private BillGSTRepository billGSTRepository;
    
    @Autowired
    private BillNonGSTRepository billNonGSTRepository;
    
    /**
     * Generate next GST bill number. Global sequence on {@code bills_gst}; {@code createdByUserId} is not used for numbering.
     * Uses lock + existence check so concurrent creates and odd legacy {@code bill_number} values cannot produce duplicates.
     */
    public String generateGSTBillNumber(Long createdByUserId) {
        synchronized (gstBillNumberLock) {
            return nextFreeNumericBillNumber(
                    billGSTRepository::findMaxBillNumber,
                    billGSTRepository::existsByBillNumber);
        }
    }

    /**
     * Generate next Non-GST bill number (global sequence on {@code bills_non_gst}).
     */
    public String generateNonGSTBillNumber(Long createdByUserId) {
        synchronized (nonGstBillNumberLock) {
            return nextFreeNumericBillNumber(
                    billNonGSTRepository::findMaxBillNumber,
                    billNonGSTRepository::existsByBillNumber);
        }
    }

    @FunctionalInterface
    private interface MaxBillNumberQuery {
        Integer findMax();
    }

    @FunctionalInterface
    private interface BillNumberExistsQuery {
        boolean exists(String billNumber);
    }

    /**
     * Next numeric bill string: max+1 from purely numeric rows, then skip forward if that value already exists
     * (handles gaps, legacy non-numeric rows confusing MAX, and races after unlock in other tiers).
     */
    private String nextFreeNumericBillNumber(MaxBillNumberQuery maxQuery, BillNumberExistsQuery existsQuery) {
        Integer maxBillNumber = maxQuery.findMax();
        int nextNumber = (maxBillNumber == null) ? 1 : maxBillNumber + 1;
        int guard = 0;
        while (existsQuery.exists(String.valueOf(nextNumber)) && guard++ < 50_000) {
            nextNumber++;
        }
        if (guard >= 50_000) {
            throw new IllegalStateException("Could not allocate a free bill number after many attempts");
        }
        return String.valueOf(nextNumber);
    }
    
    /**
     * @deprecated Use generateGSTBillNumber() or generateNonGSTBillNumber() instead
     * This method is kept for backward compatibility but should not be used
     */
    @Deprecated
    public String generateUniqueBillNumber() {
        return generateGSTBillNumber(null);
    }

    /** @deprecated Use {@link #generateGSTBillNumber(Long)} with user id */
    public String generateGSTBillNumber() {
        return generateGSTBillNumber(null);
    }

    /** @deprecated Use {@link #generateNonGSTBillNumber(Long)} with user id */
    public String generateNonGSTBillNumber() {
        return generateNonGSTBillNumber(null);
    }
    
    public boolean isBillNumberExists(String billNumber) {
        return billGSTRepository.existsByBillNumber(billNumber) || 
               billNonGSTRepository.existsByBillNumber(billNumber);
    }
}

