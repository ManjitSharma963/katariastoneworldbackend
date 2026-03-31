package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ClientTransactionRequestDTO;
import com.katariastoneworld.apis.dto.ClientTransactionResponseDTO;
import com.katariastoneworld.apis.dto.LedgerRequest;
import com.katariastoneworld.apis.entity.*;
import com.katariastoneworld.apis.repository.ClientTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
            financialLedgerService.createEntry(LedgerRequest.builder()
                    .location(location)
                    .sourceType("CLIENT_TRANSACTION")
                    .sourceId(String.valueOf(saved.getId()))
                    .entryType(LedgerEntryType.DEBIT)
                    .amount(amt)
                    .paymentMode(mode)
                    .referenceType("CUSTOMER")
                    .referenceId(saved.getClientId())
                    .eventDate(d)
                    .build());
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

        return toDto(saved);
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
        return rows.stream().map(this::toDto).toList();
    }

    private ClientTransactionResponseDTO toDto(ClientTransaction row) {
        return ClientTransactionResponseDTO.builder()
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
    }
}
