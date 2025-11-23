package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ClientPurchasePaymentRequestDTO;
import com.katariastoneworld.apis.dto.ClientPurchasePaymentResponseDTO;
import com.katariastoneworld.apis.dto.ClientPurchaseRequestDTO;
import com.katariastoneworld.apis.dto.ClientPurchaseResponseDTO;
import com.katariastoneworld.apis.entity.ClientPurchase;
import com.katariastoneworld.apis.entity.ClientPurchasePayment;
import com.katariastoneworld.apis.repository.ClientPurchasePaymentRepository;
import com.katariastoneworld.apis.repository.ClientPurchaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClientPurchaseService {
    
    @Autowired
    private ClientPurchaseRepository clientPurchaseRepository;
    
    @Autowired
    private ClientPurchasePaymentRepository clientPurchasePaymentRepository;
    
    public ClientPurchaseResponseDTO createClientPurchase(ClientPurchaseRequestDTO requestDTO, String location) {
        ClientPurchase clientPurchase = new ClientPurchase();
        clientPurchase.setClientName(requestDTO.getClientName());
        clientPurchase.setPurchaseDescription(requestDTO.getPurchaseDescription());
        clientPurchase.setTotalAmount(requestDTO.getTotalAmount());
        clientPurchase.setPurchaseDate(requestDTO.getPurchaseDate());
        clientPurchase.setNotes(requestDTO.getNotes());
        clientPurchase.setLocation(location);
        
        ClientPurchase saved = clientPurchaseRepository.save(clientPurchase);
        return convertToResponseDTO(saved);
    }
    
    public List<ClientPurchaseResponseDTO> getAllClientPurchases(String location) {
        List<ClientPurchase> purchases = clientPurchaseRepository.findByLocationOrderByPurchaseDateDesc(location);
        return purchases.stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }
    
    public ClientPurchaseResponseDTO getClientPurchaseById(Long id, String location) {
        ClientPurchase clientPurchase = clientPurchaseRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Client purchase not found with id: " + id));
        return convertToResponseDTO(clientPurchase);
    }
    
    public ClientPurchaseResponseDTO updateClientPurchase(Long id, ClientPurchaseRequestDTO requestDTO, String location) {
        ClientPurchase clientPurchase = clientPurchaseRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Client purchase not found with id: " + id));
        
        clientPurchase.setClientName(requestDTO.getClientName());
        clientPurchase.setPurchaseDescription(requestDTO.getPurchaseDescription());
        clientPurchase.setTotalAmount(requestDTO.getTotalAmount());
        clientPurchase.setPurchaseDate(requestDTO.getPurchaseDate());
        clientPurchase.setNotes(requestDTO.getNotes());
        // Location should not be updated
        
        ClientPurchase updated = clientPurchaseRepository.save(clientPurchase);
        return convertToResponseDTO(updated);
    }
    
    public void deleteClientPurchase(Long id, String location) {
        ClientPurchase clientPurchase = clientPurchaseRepository.findByIdAndLocation(id, location)
                .orElseThrow(() -> new RuntimeException("Client purchase not found with id: " + id));
        clientPurchaseRepository.delete(clientPurchase);
    }
    
    public ClientPurchasePaymentResponseDTO createPayment(Long clientPurchaseId, ClientPurchasePaymentRequestDTO requestDTO, String location) {
        ClientPurchase clientPurchase = clientPurchaseRepository.findByIdAndLocation(clientPurchaseId, location)
                .orElseThrow(() -> new RuntimeException("Client purchase not found with id: " + clientPurchaseId));
        
        ClientPurchasePayment payment = new ClientPurchasePayment();
        payment.setClientPurchase(clientPurchase);
        payment.setClientId(requestDTO.getClientId());
        payment.setAmount(requestDTO.getAmount());
        payment.setDate(requestDTO.getDate());
        payment.setPaymentMethod(requestDTO.getPaymentMethod());
        payment.setNotes(requestDTO.getNotes());
        
        ClientPurchasePayment saved = clientPurchasePaymentRepository.save(payment);
        return convertToPaymentResponseDTO(saved);
    }
    
    public List<ClientPurchasePaymentResponseDTO> getPaymentHistory(Long clientPurchaseId, String location) {
        // Verify that the client purchase exists and belongs to the location
        clientPurchaseRepository.findByIdAndLocation(clientPurchaseId, location)
                .orElseThrow(() -> new RuntimeException("Client purchase not found with id: " + clientPurchaseId));
        
        List<ClientPurchasePayment> payments = clientPurchasePaymentRepository.findByClientPurchaseIdAndLocation(clientPurchaseId, location);
        return payments.stream()
                .sorted((p1, p2) -> {
                    int dateCompare = p2.getDate().compareTo(p1.getDate());
                    if (dateCompare != 0) return dateCompare;
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
    
    private ClientPurchaseResponseDTO convertToResponseDTO(ClientPurchase clientPurchase) {
        ClientPurchaseResponseDTO dto = new ClientPurchaseResponseDTO();
        dto.setId(clientPurchase.getId());
        dto.setClientName(clientPurchase.getClientName());
        dto.setPurchaseDescription(clientPurchase.getPurchaseDescription());
        dto.setTotalAmount(clientPurchase.getTotalAmount());
        dto.setPurchaseDate(clientPurchase.getPurchaseDate());
        dto.setNotes(clientPurchase.getNotes());
        dto.setLocation(clientPurchase.getLocation());
        dto.setCreatedAt(clientPurchase.getCreatedAt());
        dto.setUpdatedAt(clientPurchase.getUpdatedAt());
        return dto;
    }
    
    private ClientPurchasePaymentResponseDTO convertToPaymentResponseDTO(ClientPurchasePayment payment) {
        ClientPurchasePaymentResponseDTO dto = new ClientPurchasePaymentResponseDTO();
        dto.setId(payment.getId());
        dto.setClientPurchaseId(payment.getClientPurchase().getId());
        dto.setClientId(payment.getClientId());
        dto.setAmount(payment.getAmount());
        dto.setDate(payment.getDate());
        dto.setPaymentMethod(payment.getPaymentMethod());
        dto.setNotes(payment.getNotes());
        dto.setCreatedAt(payment.getCreatedAt());
        dto.setUpdatedAt(payment.getUpdatedAt());
        return dto;
    }
}

