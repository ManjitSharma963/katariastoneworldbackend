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
     * Generate next GST bill number. Firm-initial prefix series (e.g. KSW-1, KMR-1).
     * Bhondsi → Kataria Stone World (KSW). Tapugada → Kataria Marbles (KMR).
     * Uses lock + existence check so concurrent creates and odd legacy {@code bill_number} values cannot produce duplicates.
     */
    public String generateGSTBillNumber(String location, Long createdByUserId) {
        synchronized (gstBillNumberLock) {
            String prefix = locationPrefix(location) + "-";
            return nextFreePrefixedBillNumber(
                    prefix,
                    () -> billGSTRepository.findMaxBillNumberForPrefix(prefix),
                    billGSTRepository::existsByBillNumber);
        }
    }

    /** Generate next Non-GST bill number (firm-initial prefix series, e.g. KSW-1, KMR-1). */
    public String generateNonGSTBillNumber(String location, Long createdByUserId) {
        synchronized (nonGstBillNumberLock) {
            String prefix = locationPrefix(location) + "-";
            return nextFreePrefixedBillNumber(
                    prefix,
                    () -> billNonGSTRepository.findMaxBillNumberForPrefix(prefix),
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

    private String nextFreePrefixedBillNumber(String prefix, MaxBillNumberQuery maxQuery, BillNumberExistsQuery existsQuery) {
        Integer max = maxQuery.findMax();
        int nextNumber = (max == null) ? 1 : max + 1;
        int guard = 0;
        while (existsQuery.exists(prefix + nextNumber) && guard++ < 50_000) {
            nextNumber++;
        }
        if (guard >= 50_000) {
            throw new IllegalStateException("Could not allocate a free bill number after many attempts");
        }
        return prefix + nextNumber;
    }

    /**
     * Bill series prefix from branch location (JWT). Maps to firm trading names:
     * <ul>
     *   <li>Bhondsi — Kataria Stone World → {@code KSW}</li>
     *   <li>Tapugada — Kataria Marbles → {@code KMR}</li>
     * </ul>
     * Unknown locations fall back to first three alphanumerics of the location string, else {@code LOC}.
     */
    private static String locationPrefix(String location) {
        if (location == null) return "LOC";
        String l = location.trim().toLowerCase();
        if (l.isEmpty()) return "LOC";
        if (l.startsWith("bhondsi")) return "KSW";
        if (l.startsWith("tapugada")) return "KMR";
        String letters = l.replaceAll("[^a-z0-9]", "");
        if (letters.length() >= 3) return letters.substring(0, 3).toUpperCase();
        return letters.toUpperCase();
    }

    /**
     * @deprecated Use generateGSTBillNumber() or generateNonGSTBillNumber() instead
     * This method is kept for backward compatibility but should not be used
     */
    @Deprecated
    public String generateUniqueBillNumber() {
        return generateGSTBillNumber(null, null);
    }

    /** @deprecated Use {@link #generateGSTBillNumber(String, Long)} */
    public String generateGSTBillNumber() {
        return generateGSTBillNumber(null, null);
    }

    /** @deprecated Use {@link #generateNonGSTBillNumber(String, Long)} */
    public String generateNonGSTBillNumber() {
        return generateNonGSTBillNumber(null, null);
    }
    
    public boolean isBillNumberExists(String billNumber) {
        return billGSTRepository.existsByBillNumber(billNumber) || 
               billNonGSTRepository.existsByBillNumber(billNumber);
    }
}

