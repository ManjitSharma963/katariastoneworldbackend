package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.repository.BillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BillNumberGeneratorService {

    private final Object gstBillNumberLock = new Object();
    private final Object nonGstBillNumberLock = new Object();

    @Autowired
    private BillRepository billRepository;

    public String generateGSTBillNumber(String location, Long createdByUserId) {
        synchronized (gstBillNumberLock) {
            String prefix = locationPrefix(location) + "-";
            return nextFreePrefixedBillNumber(
                    prefix,
                    () -> billRepository.findMaxBillNumberForPrefixAndKind(prefix, BillKind.GST.name()),
                    n -> billRepository.existsByBillNumber(n));
        }
    }

    public String generateNonGSTBillNumber(String location, Long createdByUserId) {
        synchronized (nonGstBillNumberLock) {
            String prefix = locationPrefix(location) + "-";
            return nextFreePrefixedBillNumber(
                    prefix,
                    () -> billRepository.findMaxBillNumberForPrefixAndKind(prefix, BillKind.NON_GST.name()),
                    n -> billRepository.existsByBillNumber(n));
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

    private static String locationPrefix(String location) {
        if (location == null) {
            return "LOC";
        }
        String l = location.trim().toLowerCase();
        if (l.isEmpty()) {
            return "LOC";
        }
        if (l.startsWith("bhondsi")) {
            return "KSW";
        }
        if (l.startsWith("tapugada")) {
            return "KMR";
        }
        String letters = l.replaceAll("[^a-z0-9]", "");
        if (letters.length() >= 3) {
            return letters.substring(0, 3).toUpperCase();
        }
        return letters.toUpperCase();
    }

    @Deprecated
    public String generateUniqueBillNumber() {
        return generateGSTBillNumber(null, null);
    }

    @Deprecated
    public String generateGSTBillNumber() {
        return generateGSTBillNumber(null, null);
    }

    @Deprecated
    public String generateNonGSTBillNumber() {
        return generateNonGSTBillNumber(null, null);
    }

    public boolean isBillNumberExists(String billNumber) {
        return billRepository.existsByBillNumber(billNumber);
    }
}
