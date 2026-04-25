package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.ClientSupplierAccountRequestDTO;
import com.katariastoneworld.apis.dto.ClientSupplierAccountResponseDTO;
import com.katariastoneworld.apis.dto.ClientSupplierAccountUpdateDTO;
import com.katariastoneworld.apis.entity.ClientSupplierAccount;
import com.katariastoneworld.apis.repository.ClientSupplierAccountRepository;
import com.katariastoneworld.apis.util.ClientSupplierKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ClientSupplierAccountService {

    @Autowired
    private ClientSupplierAccountRepository clientSupplierAccountRepository;

    public List<ClientSupplierAccountResponseDTO> list(String location) {
        if (location == null || location.isBlank()) {
            throw new RuntimeException("Location is required.");
        }
        String loc = location.trim();
        return clientSupplierAccountRepository.findByLocationOrderByDisplayNameAscClientKeyAsc(loc).stream()
                .map(this::toDto)
                .toList();
    }

    public ClientSupplierAccountResponseDTO create(String location, ClientSupplierAccountRequestDTO dto) {
        if (location == null || location.isBlank()) {
            throw new RuntimeException("Location is required.");
        }
        String loc = location.trim();
        String key = ClientSupplierKeys.normalize(dto.getClientName());
        if (key.isEmpty()) {
            throw new RuntimeException("Client name is required.");
        }
        if (clientSupplierAccountRepository.findByLocationAndClientKey(loc, key).isPresent()) {
            throw new RuntimeException("An account already exists for this client/supplier name at this location.");
        }
        ClientSupplierAccount row = new ClientSupplierAccount();
        row.setLocation(loc);
        row.setClientKey(key);
        String disp = dto.getDisplayName();
        row.setDisplayName(disp != null && !disp.isBlank() ? disp.trim() : dto.getClientName().trim());
        row.setCreditLimit(dto.getCreditLimit());
        row.setPaymentTermsDays(dto.getPaymentTermsDays());
        ClientSupplierAccount saved = clientSupplierAccountRepository.save(row);
        return toDto(saved);
    }

    public ClientSupplierAccountResponseDTO update(Long id, String location, ClientSupplierAccountUpdateDTO dto) {
        String loc = location != null ? location.trim() : "";
        ClientSupplierAccount row = clientSupplierAccountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier account not found: " + id));
        if (!loc.equalsIgnoreCase(row.getLocation())) {
            throw new RuntimeException("Supplier account not found for this location.");
        }
        if (dto.getDisplayName() != null) {
            row.setDisplayName(dto.getDisplayName().isBlank() ? row.getDisplayName() : dto.getDisplayName().trim());
        }
        if (dto.getCreditLimit() != null) {
            row.setCreditLimit(dto.getCreditLimit());
        }
        if (dto.getPaymentTermsDays() != null) {
            row.setPaymentTermsDays(dto.getPaymentTermsDays());
        }
        return toDto(clientSupplierAccountRepository.save(row));
    }

    private ClientSupplierAccountResponseDTO toDto(ClientSupplierAccount row) {
        return new ClientSupplierAccountResponseDTO(
                row.getId(),
                row.getLocation(),
                row.getClientKey(),
                row.getDisplayName(),
                row.getCreditLimit(),
                row.getPaymentTermsDays(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
