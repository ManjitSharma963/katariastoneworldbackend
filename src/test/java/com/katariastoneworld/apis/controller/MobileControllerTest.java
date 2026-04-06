package com.katariastoneworld.apis.controller;

import com.katariastoneworld.apis.config.RoleAuthorizationFilter;
import com.katariastoneworld.apis.config.WebConfig;
import com.katariastoneworld.apis.dto.MobileDashboardDTO;
import com.katariastoneworld.apis.service.JwtUtil;
import com.katariastoneworld.apis.service.MobileDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MobileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebConfig.class, RoleAuthorizationFilter.class})
@ActiveProfiles("test")
class MobileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MobileDashboardService mobileDashboardService;

    /** Satisfies {@link com.katariastoneworld.apis.config.JwtAuthenticationFilter} in sliced context. */
    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void dashboard_ok_whenRoleAndLocationAttributesSet() throws Exception {
        when(mobileDashboardService.buildForDate(eq("LocA"), any(LocalDate.class)))
                .thenReturn(MobileDashboardDTO.builder()
                        .date("2026-03-01")
                        .totalSales(100.0)
                        .totalExpense(30.0)
                        .netBalance(70.0)
                        .paymentModes(Map.of("CASH", 100.0))
                        .build());

        mockMvc.perform(get("/api/mobile/dashboard")
                        .requestAttr("userRole", "admin")
                        .requestAttr("userLocation", "LocA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netBalance").value(70.0))
                .andExpect(jsonPath("$.date").value("2026-03-01"));
    }

    @Test
    void dashboard_forbiddenWithoutRole() throws Exception {
        mockMvc.perform(get("/api/mobile/dashboard")
                        .requestAttr("userLocation", "LocA"))
                .andExpect(status().isForbidden());
    }
}
