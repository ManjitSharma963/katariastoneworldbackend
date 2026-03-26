package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.DealerRequestDTO;
import com.katariastoneworld.apis.dto.DealerResponseDTO;
import com.katariastoneworld.apis.entity.Dealer;
import com.katariastoneworld.apis.repository.DealerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class DealerService {

    @Autowired
    private DealerRepository dealerRepository;

    public DealerResponseDTO create(DealerRequestDTO dto, String location) {
        final String loc = location == null ? "" : location.trim();
        Dealer d = new Dealer();
        d.setName(dto.getName().trim());
        d.setContactNumber(trimToNull(dto.getContactNumber()));
        d.setAddress(trimToNull(dto.getAddress()));
        d.setLocation(loc);
        Dealer saved = dealerRepository.save(d);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<DealerResponseDTO> list(String location) {
        final String loc = location == null ? "" : location.trim();
        return dealerRepository.findByLocationOrderByNameAsc(loc).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Dealer requireForProduct(Long dealerId, String location) {
        final String loc = location == null ? "" : location.trim();
        return dealerRepository.findByIdAndLocation(dealerId, loc)
                .orElseThrow(() -> new RuntimeException("Dealer not found with id: " + dealerId));
    }

    private DealerResponseDTO toDto(Dealer d) {
        return DealerResponseDTO.builder()
                .id(d.getId())
                .name(d.getName())
                .contactNumber(d.getContactNumber())
                .address(d.getAddress())
                .location(d.getLocation())
                .createdAt(d.getCreatedAt())
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
