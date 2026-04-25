package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ClientTransactionRequestDTO;
import com.katariastoneworld.apis.dto.ClientTransactionResponseDTO;
import com.katariastoneworld.apis.entity.BillPaymentMode;
import com.katariastoneworld.apis.entity.ClientTransaction;
import com.katariastoneworld.apis.entity.ClientTransactionType;
import com.katariastoneworld.apis.repository.ClientTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class ClientTransactionService {

    @Autowired
    private ClientTransactionRepository clientTransactionRepository;

    @Autowired
    private FinancialLedgerService financialLedgerService;

    public ClientTransactionResponseDTO create(ClientTransactionRequestDTO req, String location, Long actorUserId) {
        ClientTransactionType type = ClientTransactionType.parseFlexible(req.getTransactionType());
        BillPaymentMode mode = BillPaymentMode.parseFlexible(req.getPaymentMode());
        LocalDate d = req.getTransactionDate() != null ? req.getTransactionDate() : LocalDate.now();
        BigDecimal amt = req.getAmount();

        ClientTransaction tx = new ClientTransaction();
        tx.setClientId(req.getClientId().trim());
        tx.setTransactionType(type);
        tx.setAmount(amt);
        tx.setPaymentMode(mode);
        tx.setTransactionDate(d);
        tx.setNotes(req.getNotes());
        tx.setLocation(location);
        tx.setCreatedBy(actorUserId);
        ClientTransaction saved = clientTransactionRepository.save(tx);

        if (type == ClientTransactionType.PAYMENT_OUT || type == ClientTransactionType.PURCHASE) {
            financialLedgerService.recordClientPaymentOut(
                    location,
                    saved.getClientId(),
                    saved.getId(),
                    mode,
                    amt,
                    d
            );
        } else if (type == ClientTransactionType.PAYMENT_IN) {
            financialLedgerService.recordClientPaymentIn(
                    location,
                    saved.getClientId(),
                    saved.getId(),
                    mode,
                    amt,
                    d
            );
        }

        return toDto(saved, null);
    }

    public List<ClientTransactionResponseDTO> list(String location, LocalDate from, LocalDate to, String typeRaw) {
        List<ClientTransaction> rows;
        if (from != null && to != null) {
            if (typeRaw != null && !typeRaw.isBlank()) {
                rows = clientTransactionRepository.findByLocationAndTransactionTypeAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(
                        location, ClientTransactionType.parseFlexible(typeRaw), from, to);
            } else {
                rows = clientTransactionRepository.findByLocationAndTransactionDateBetweenOrderByTransactionDateDescIdDesc(location, from, to);
            }
        } else {
            rows = clientTransactionRepository.findByLocationOrderByTransactionDateDescIdDesc(location);
        }
        return rows.stream().map(r -> toDto(r, null)).toList();
    }

    /**
     * Chronological ledger for one client with running balance (signed: IN +, OUT/PURCHASE −).
     */
    public List<ClientTransactionResponseDTO> runningLedgerForClient(String location, String clientId) {
        if (location == null || location.isBlank() || clientId == null || clientId.isBlank()) {
            return List.of();
        }
        String loc = location.trim();
        String cid = clientId.trim();
        List<ClientTransaction> rows = clientTransactionRepository.findByLocationOrderByTransactionDateDescIdDesc(loc).stream()
                .filter(t -> cid.equalsIgnoreCase(t.getClientId() != null ? t.getClientId().trim() : ""))
                .sorted(Comparator
                        .comparing(ClientTransaction::getTransactionDate)
                        .thenComparing(ClientTransaction::getId))
                .toList();
        BigDecimal run = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<ClientTransactionResponseDTO> out = new ArrayList<>();
        for (ClientTransaction row : rows) {
            BigDecimal delta = signedAmount(row);
            run = run.add(delta).setScale(2, RoundingMode.HALF_UP);
            out.add(toDto(row, run));
        }
        return out;
    }

    private static BigDecimal signedAmount(ClientTransaction row) {
        BigDecimal a = row.getAmount() != null ? row.getAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (row.getTransactionType() == ClientTransactionType.PAYMENT_IN) {
            return a;
        }
        return a.negate();
    }

    private ClientTransactionResponseDTO toDto(ClientTransaction row, BigDecimal runningBalanceAfter) {
        ClientTransactionResponseDTO dto = ClientTransactionResponseDTO.builder()
                .id(row.getId())
                .clientId(row.getClientId())
                .transactionType(row.getTransactionType() != null ? row.getTransactionType().name() : null)
                .amount(row.getAmount())
                .paymentMode(row.getPaymentMode() != null ? row.getPaymentMode().name() : null)
                .transactionDate(row.getTransactionDate())
                .notes(row.getNotes())
                .location(row.getLocation())
                .createdAt(row.getCreatedAt())
                .build();
        dto.setRunningBalanceAfter(runningBalanceAfter);
        return dto;
    }
}

