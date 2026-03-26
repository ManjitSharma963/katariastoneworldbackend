package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RequiresRole;
import com.katariastoneworld.apis.dto.DealerRequestDTO;
import com.katariastoneworld.apis.dto.DealerResponseDTO;
import com.katariastoneworld.apis.service.DealerService;
import com.katariastoneworld.apis.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({ "/api/dealers", "/dealers" })
@Tag(name = "Dealers", description = "Dealer (middleman) master data — scoped by JWT location")
public class DealerController {

    @Autowired
    private DealerService dealerService;

    @Operation(summary = "List dealers for your location")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    @RequiresRole({ "user", "admin" })
    public ResponseEntity<List<DealerResponseDTO>> list(HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        return ResponseEntity.ok(dealerService.list(location));
    }

    @Operation(summary = "Create dealer")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @RequiresRole("admin")
    public ResponseEntity<DealerResponseDTO> create(
            @Valid @RequestBody DealerRequestDTO body,
            HttpServletRequest request) {
        String location = RequestUtil.getLocationFromRequest(request);
        DealerResponseDTO dto = dealerService.create(body, location);
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }
}
