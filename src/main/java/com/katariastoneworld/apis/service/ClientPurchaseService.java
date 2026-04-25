package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ClientPurchasePaymentRequestDTO;
import com.katariastoneworld.apis.dto.ClientPurchasePaymentResponseDTO;
import com.katariastoneworld.apis.dto.ClientPurchaseRequestDTO;
import com.katariastoneworld.apis.dto.ClientPurchaseResponseDTO;
import com.katariastoneworld.apis.dto.ClientTransactionRequestDTO;
import com.katariastoneworld.apis.entity.ClientPurchase;
import com.katariastoneworld.apis.entity.ClientPurchasePayment;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClientPurchaseService {

    @Autowired
    private ClientPurchaseRepository clientPurchaseRepository;

    @Autowired
    private ClientPurchasePaymentRepository clientPurchasePaymentRepository;

    @Autowired
    private ClientTransactionService clientTransactionService;

    @Autowired
    private ClientSupplierAccountRepository clientSupplierAccountRepository;

    public ClientPurchaseResponseDTO createClientPurchase(ClientPurchaseRequestDTO requestDTO, String location) {
        ClientPurchase clientPurchase = new ClientPurchase();
        clientPurchase.setClientName(requestDTO.getClientName());
        clientPurchase.setPurchaseDescription(requestDTO.getPurchaseDescription());
        clientPurchase.setTotalAmount(requestDTO.getTotalAmount());
        clientPurchase.setPurchaseDate(requestDTO.getPurchaseDate());
        clientPurchase.setDueDate(resolveDueDate(requestDTO, location));
        clientPurchase.setNotes(requestDTO.getNotes());
        clientPurchase.setLocation(location);
        ClientPurchase saved = clientPurchaseRepository.save(clientPurchase);
        BigDecimal paid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return convertToResponseDTO(saved, paid);
    }

    public List<ClientPurchaseResponseDTO> getAllClientPurchases(String location) {
        if (location == null || location.trim().isEmpty()) {
            throw new RuntimeException("Location is required.");
        }
        Map<Long, BigDecimal> paidMap = loadPaidTotalsByPurchaseId(location.trim());
        List<ClientPurchase> purchases = clientPurchaseRepository.findByLocationOrderByPurchaseDateDesc(location);
        return purchases.stream()
                .map(p -> convertToResponseDTO(p, paidMap.getOrDefault(p.getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    public ClientPurchaseResponseDTO getClientPurchaseById(Long id, String location) {
        ClientPurchase clientPurchase = clientPurchaseRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Client purchase not found with id: " + id));
        BigDecimal paid = nullToZero(clientPurchasePaymentRepository.sumAmountForPurchaseId(id))
                .setScale(2, RoundingMode.HALF_UP);
        return convertToResponseDTO(clientPurchase, paid);
    }

    public ClientPurchaseResponseDTO updateClientPurchase(Long id, ClientPurchaseRequestDTO requestDTO, String location) {
        ClientPurchase clientPurchase = clientPurchaseRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Client purchase not found with id: " + id));
        clientPurchase.setClientName(requestDTO.getClientName());
        clientPurchase.setPurchaseDescription(requestDTO.getPurchaseDescription());
        clientPurchase.setTotalAmount(requestDTO.getTotalAmount());
        clientPurchase.setPurchaseDate(requestDTO.getPurchaseDate());
        clientPurchase.setDueDate(resolveDueDate(requestDTO, location));
        clientPurchase.setNotes(requestDTO.getNotes());
        ClientPurchase updated = clientPurchaseRepository.save(clientPurchase);
        BigDecimal paid = nullToZero(clientPurchasePaymentRepository.sumAmountForPurchaseId(id))
                .setScale(2, RoundingMode.HALF_UP);
        return convertToResponseDTO(updated, paid);
    }

    public void deleteClientPurchase(Long id, String location) {
        ClientPurchase clientPurchase = clientPurchaseRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Client purchase not found with id: " + id));
        clientPurchaseRepository.delete(clientPurchase);
    }

    public ClientPurchasePaymentResponseDTO createPayment(Long clientPurchaseId, ClientPurchasePaymentRequestDTO requestDTO,
            String location) {
        ClientPurchase clientPurchase = clientPurchaseRepository.findByIdAndLocation(clientPurchaseId, location)
                .orElseThrow(() -> new RuntimeException("Client purchase not found with id: " + clientPurchaseId));

        ClientPurchasePayment payment = new ClientPurchasePayment();
        payment.setClientPurchase(clientPurchase);
        payment.setClientId(requestDTO.getClientId());
        payment.setAmount(requestDTO.getAmount());
        payment.setPaymentDate(requestDTO.getDate() != null ? requestDTO.getDate() : LocalDate.now());
        payment.setPaymentMethod(requestDTO.getPaymentMethod());
        payment.setNotes(requestDTO.getNotes());

        ClientPurchasePayment saved = clientPurchasePaymentRepository.save(payment);

        ClientTransactionRequestDTO tx = new ClientTransactionRequestDTO();
        tx.setClientId(requestDTO.getClientId());
        tx.setTransactionType("PAYMENT_OUT");
        tx.setAmount(requestDTO.getAmount());
        tx.setPaymentMode(requestDTO.getPaymentMethod());
        tx.setTransactionDate(requestDTO.getDate());
        String richNote = "Payment to " + clientPurchase.getClientName() + " — "
                + (clientPurchase.getPurchaseDescription() != null ? clientPurchase.getPurchaseDescription() : "Purchase");
        if (requestDTO.getNotes() != null && !requestDTO.getNotes().isBlank()) {
            richNote = richNote + " — " + requestDTO.getNotes().trim();
        }
        tx.setNotes(richNote);
        clientTransactionService.create(tx, location, null);

        return convertToPaymentResponseDTO(saved);
    }

    public List<ClientPurchasePaymentResponseDTO> getPaymentHistory(Long clientPurchaseId, String location) {
        clientPurchaseRepository.findByIdAndLocation(clientPurchaseId, location)
                .orElseThrow(() -> new RuntimeException("Client purchase not found with id: " + clientPurchaseId));

        List<ClientPurchasePayment> payments = clientPurchasePaymentRepository.findByClientPurchaseIdAndLocation(
                clientPurchaseId, location);
        return payments.stream()
                .sorted((p1, p2) -> {
                    int dateCompare = p2.getPaymentDate().compareTo(p1.getPaymentDate());
                    if (dateCompare != 0) {
                        return dateCompare;
                    }
                    return p2.getCreatedAt().compareTo(p1.getCreatedAt());
                })
                .map(this::convertToPaymentResponseDTO)
                .collect(Collectors.toList());
    }

    public List<ClientPurchasePaymentResponseDTO> getAllPayments(String location) {
        List<ClientPurchasePayment> payments = clientPurchasePaymentRepository.findByLocationOrderByDateDesc(location);
        return payments.stream()
                .map(this::convertToPaymentResponseDTO)
                .collect(Collectors.toList());
    }

    private LocalDate resolveDueDate(ClientPurchaseRequestDTO requestDTO, String location) {
        if (requestDTO.getDueDate() != null) {
            return requestDTO.getDueDate();
        }
        if (requestDTO.getPurchaseDate() == null || requestDTO.getClientName() == null) {
            return null;
        }
        String key = ClientSupplierKeys.normalize(requestDTO.getClientName());
        if (key.isEmpty()) {
            return null;
        }
        return clientSupplierAccountRepository.findByLocationAndClientKey(location != null ? location.trim() : "", key)
                .filter(a -> a.getPaymentTermsDays() != null && a.getPaymentTermsDays() >= 0)
                .map(a -> requestDTO.getPurchaseDate().plusDays(a.getPaymentTermsDays()))
                .orElse(null);
    }

    private Map<Long, BigDecimal> loadPaidTotalsByPurchaseId(String location) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] pair : clientPurchasePaymentRepository.sumPaidAmountsGroupedByPurchaseId(location)) {
            if (pair == null || pair.length < 2 || pair[0] == null || pair[1] == null) {
                continue;
            }
            long pid = ((Number) pair[0]).longValue();
            BigDecimal amt = new BigDecimal(pair[1].toString()).setScale(2, RoundingMode.HALF_UP);
            map.put(pid, amt);
        }
        return map;
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private ClientPurchaseResponseDTO convertToResponseDTO(ClientPurchase clientPurchase, BigDecimal amountPaid) {
        ClientPurchaseResponseDTO dto = new ClientPurchaseResponseDTO();
        dto.setId(clientPurchase.getId());
        dto.setClientName(clientPurchase.getClientName());
        dto.setPurchaseDescription(clientPurchase.getPurchaseDescription());
        dto.setTotalAmount(clientPurchase.getTotalAmount());
        dto.setPurchaseDate(clientPurchase.getPurchaseDate());
        dto.setDueDate(clientPurchase.getDueDate());
        dto.setNotes(clientPurchase.getNotes());
        dto.setLocation(clientPurchase.getLocation());
        dto.setCreatedAt(clientPurchase.getCreatedAt());
        dto.setUpdatedAt(clientPurchase.getUpdatedAt());
        BigDecimal paid = amountPaid != null ? amountPaid.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        dto.setAmountPaid(paid);
        BigDecimal total = clientPurchase.getTotalAmount() != null
                ? clientPurchase.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal out = total.subtract(paid).setScale(2, RoundingMode.HALF_UP);
        if (out.compareTo(BigDecimal.ZERO) < 0) {
            out = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        dto.setAmountOutstanding(out);
        return dto;
    }

    private ClientPurchasePaymentResponseDTO convertToPaymentResponseDTO(ClientPurchasePayment payment) {
        ClientPurchasePaymentResponseDTO dto = new ClientPurchasePaymentResponseDTO();
        dto.setId(payment.getId());
        dto.setClientPurchaseId(payment.getClientPurchase().getId());
        dto.setClientId(payment.getClientId());
        dto.setAmount(payment.getAmount());
        dto.setDate(payment.getPaymentDate());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setNotes(payment.getNotes());
        dto.setCreatedAt(payment.getCreatedAt());
        dto.setUpdatedAt(payment.getUpdatedAt());
        return dto;
    }
}
