package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BillNumberGeneratorService {
    
    @Autowired
    private BillGSTRepository billGSTRepository;
    
    @Autowired
    private BillNonGSTRepository billNonGSTRepository;
    
    /**
     * Generate sequential bill number for GST bills (1, 2, 3, 4...) for the given user.
     * Each user has their own sequence, so no conflict between users.
     * @param createdByUserId user who is creating the bill; if null, uses global max (backward compatible).
     */
    public String generateGSTBillNumber(Long createdByUserId) {
        Integer maxBillNumber = (createdByUserId != null)
                ? billGSTRepository.findMaxBillNumberByCreatedByUserId(createdByUserId)
                : billGSTRepository.findMaxBillNumber();
        int nextNumber = (maxBillNumber == null) ? 1 : maxBillNumber + 1;
        return String.valueOf(nextNumber);
    }

    /**
     * Generate sequential bill number for Non-GST bills (1, 2, 3, 4...) for the given user.
     * Each user has their own sequence, so no conflict between users.
     * @param createdByUserId user who is creating the bill; if null, uses global max (backward compatible).
     */
    public String generateNonGSTBillNumber(Long createdByUserId) {
        Integer maxBillNumber = (createdByUserId != null)
                ? billNonGSTRepository.findMaxBillNumberByCreatedByUserId(createdByUserId)
                : billNonGSTRepository.findMaxBillNumber();
        int nextNumber = (maxBillNumber == null) ? 1 : maxBillNumber + 1;
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

