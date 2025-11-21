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
     * Generate sequential bill number for GST bills (1, 2, 3, 4...)
     * Each GST bill gets the next sequential number
     */
    public String generateGSTBillNumber() {
        Integer maxBillNumber = billGSTRepository.findMaxBillNumber();
        int nextNumber = (maxBillNumber == null) ? 1 : maxBillNumber + 1;
        return String.valueOf(nextNumber);
    }
    
    /**
     * Generate sequential bill number for Non-GST bills (1, 2, 3, 4...)
     * Each Non-GST bill gets the next sequential number
     * This is a separate series from GST bills
     */
    public String generateNonGSTBillNumber() {
        Integer maxBillNumber = billNonGSTRepository.findMaxBillNumber();
        int nextNumber = (maxBillNumber == null) ? 1 : maxBillNumber + 1;
        return String.valueOf(nextNumber);
    }
    
    /**
     * @deprecated Use generateGSTBillNumber() or generateNonGSTBillNumber() instead
     * This method is kept for backward compatibility but should not be used
     */
    @Deprecated
    public String generateUniqueBillNumber() {
        // Fallback to GST numbering for backward compatibility
        return generateGSTBillNumber();
    }
    
    public boolean isBillNumberExists(String billNumber) {
        return billGSTRepository.existsByBillNumber(billNumber) || 
               billNonGSTRepository.existsByBillNumber(billNumber);
    }
}

