package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.SupplierRequestDTO;
import com.katariastoneworld.apis.dto.SupplierResponseDTO;
import com.katariastoneworld.apis.entity.Supplier;
import com.katariastoneworld.apis.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class SupplierService {

    @Autowired
    private SupplierRepository supplierRepository;

    public SupplierResponseDTO create(SupplierRequestDTO dto, String location) {
        final String loc = location == null ? "" : location.trim();
        Supplier s = new Supplier();
        s.setName(dto.getName().trim());
        s.setContactNumber(trimToNull(dto.getContactNumber()));
        s.setAddress(trimToNull(dto.getAddress()));
        s.setLocation(loc);
        Supplier saved = supplierRepository.save(s);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SupplierResponseDTO> list(String location) {
        final String loc = location == null ? "" : location.trim();
        return supplierRepository.findByLocationOrderByNameAsc(loc).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Resolve supplier for linking to a product; must exist and match location.
     */
    public Supplier requireForProduct(Long supplierId, String location) {
        final String loc = location == null ? "" : location.trim();
        return supplierRepository.findByIdAndLocation(supplierId, loc)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + supplierId));
    }

    private SupplierResponseDTO toDto(Supplier s) {
        return SupplierResponseDTO.builder()
                .id(s.getId())
                .name(s.getName())
                .contactNumber(s.getContactNumber())
                .address(s.getAddress())
                .location(s.getLocation())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
