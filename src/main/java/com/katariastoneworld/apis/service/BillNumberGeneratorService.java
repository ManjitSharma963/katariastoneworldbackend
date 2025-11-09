package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.repository.BillGSTRepository;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class BillNumberGeneratorService {
    
    @Autowired
    private BillGSTRepository billGSTRepository;
    
    @Autowired
    private BillNonGSTRepository billNonGSTRepository;
    
    public String generateUniqueBillNumber() {
        String prefix = "BILL";
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timeStr = String.valueOf(System.currentTimeMillis()).substring(7);
        
        String billNumber = prefix + "-" + dateStr + "-" + timeStr;
        
        // Ensure uniqueness across both tables
        int suffix = 1;
        String uniqueBillNumber = billNumber;
        while (billGSTRepository.existsByBillNumber(uniqueBillNumber) || 
               billNonGSTRepository.existsByBillNumber(uniqueBillNumber)) {
            uniqueBillNumber = billNumber + "-" + suffix++;
        }
        
        return uniqueBillNumber;
    }
    
    public boolean isBillNumberExists(String billNumber) {
        return billGSTRepository.existsByBillNumber(billNumber) || 
               billNonGSTRepository.existsByBillNumber(billNumber);
    }
}

