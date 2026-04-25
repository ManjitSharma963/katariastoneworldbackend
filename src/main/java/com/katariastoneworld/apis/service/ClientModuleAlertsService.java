package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ClientModuleAlertDTO;
import com.katariastoneworld.apis.entity.ClientPurchase;
import com.katariastoneworld.apis.entity.ClientSupplierAccount;
import com.katariastoneworld.apis.repository.ClientPurchasePaymentRepository;
import com.katariastoneworld.apis.repository.ClientPurchaseRepository;
import com.katariastoneworld.apis.repository.ClientSupplierAccountRepository;
import com.katariastoneworld.apis.util.ClientSupplierKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ClientModuleAlertsService {

    @Autowired
    private ClientPurchaseRepository clientPurchaseRepository;

    @Autowired
    private ClientPurchasePaymentRepository clientPurchasePaymentRepository;

    @Autowired
    private ClientSupplierAccountRepository clientSupplierAccountRepository;

    public List<ClientModuleAlertDTO> listAlerts(String location) {
        if (location == null || location.isBlank()) {
            return List.of();
        }
        String loc = location.trim();
        LocalDate today = LocalDate.now();

        Map<Long, BigDecimal> paidByPurchase = new HashMap<>();
        for (Object[] pair : clientPurchasePaymentRepository.sumPaidAmountsGroupedByPurchaseId(loc)) {
            if (pair == null || pair.length < 2 || pair[0] == null || pair[1] == null) {
                continue;
            }
            paidByPurchase.put(((Number) pair[0]).longValue(),
                    new BigDecimal(pair[1].toString()).setScale(2, RoundingMode.HALF_UP));
        }

        List<ClientPurchase> purchases = clientPurchaseRepository.findByLocationOrderByPurchaseDateDesc(loc);
        Map<String, BigDecimal> outstandingByClientKey = new HashMap<>();
        List<ClientModuleAlertDTO> alerts = new ArrayList<>();

        for (ClientPurchase p : purchases) {
            BigDecimal paid = paidByPurchase.getOrDefault(p.getId(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal total = p.getTotalAmount() != null ? p.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal outstanding = total.subtract(paid).setScale(2, RoundingMode.HALF_UP);
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String key = ClientSupplierKeys.normalize(p.getClientName());
            outstandingByClientKey.merge(key, outstanding, BigDecimal::add);

            if (p.getDueDate() != null && p.getDueDate().isBefore(today)) {
                alerts.add(new ClientModuleAlertDTO(
                        "OVERDUE",
                        key,
                        p.getClientName(),
                        "Purchase #" + p.getId() + " — due " + p.getDueDate(),
                        outstanding,
                        p.getDueDate(),
                        null));
            }
        }

        Set<String> creditAlerted = new HashSet<>();
        for (Map.Entry<String, BigDecimal> e : outstandingByClientKey.entrySet()) {
            if (e.getKey().isEmpty()) {
                continue;
            }
            ClientSupplierAccount acc = clientSupplierAccountRepository
                    .findByLocationAndClientKey(loc, e.getKey())
                    .orElse(null);
            if (acc == null || acc.getCreditLimit() == null) {
                continue;
            }
            BigDecimal limit = acc.getCreditLimit().setScale(2, RoundingMode.HALF_UP);
            if (e.getValue().compareTo(limit) > 0 && creditAlerted.add(e.getKey())) {
                String display = acc.getDisplayName() != null && !acc.getDisplayName().isBlank()
                        ? acc.getDisplayName()
                        : e.getKey();
                alerts.add(new ClientModuleAlertDTO(
                        "OVER_CREDIT_LIMIT",
                        e.getKey(),
                        display,
                        "Total outstanding ₹" + e.getValue() + " exceeds credit limit ₹" + limit,
                        e.getValue(),
                        null,
                        limit));
            }
        }

        return alerts;
    }
}
