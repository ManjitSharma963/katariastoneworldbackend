package com.katariastoneworld.apis.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.katariastoneworld.apis.service.BillNumberGeneratorService;
import com.katariastoneworld.apis.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiE2EFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailService emailService;

    @MockBean
    private BillNumberGeneratorService billNumberGeneratorService;

    @BeforeEach
    void setUpBillNumbers() {
        AtomicLong seq = new AtomicLong(1000);
        when(billNumberGeneratorService.generateNonGSTBillNumber(anyString(), anyLong()))
                .thenAnswer(inv -> "NGB-" + seq.incrementAndGet());
        when(billNumberGeneratorService.generateGSTBillNumber(anyString(), anyLong()))
                .thenAnswer(inv -> "GB-" + seq.incrementAndGet());
    }

    @Test
    void protectedEndpoint_requiresJwtToken() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void authFlow_registerLoginAndFetchMe_succeeds() throws Exception {
        String email = "admin.auth.e2e@kataria.test";
        String token = registerAndLogin("Admin E2E", email, "secret123", "Bhondsi", "admin");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.location").value("Bhondsi"))
                .andExpect(jsonPath("$.data.role").value("admin"));
    }

    @Test
    void billsFlow_respectsRoleAndRunsThroughAuthControllerAndDb() throws Exception {
        String adminToken = registerAndLogin("Admin E2E", "admin.billing.e2e@kataria.test", "secret123", "Bhondsi", "admin");
        String userToken = registerAndLogin("User E2E", "user.billing.e2e@kataria.test", "secret123", "Bhondsi", "user");

        MvcResult createdProductResult = mockMvc.perform(post("/api/inventory")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "E2E Marble",
                                  "slug": "e2e-marble-001",
                                  "productType": "marble",
                                  "pricePerUnit": 120,
                                  "quantity": 200,
                                  "unit": "sqft",
                                  "primaryImageUrl": "https://example.com/e2e-marble.jpg"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();

        Long productId = readDataFieldAsLong(createdProductResult, "id");

        mockMvc.perform(post("/api/bills")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerMobileNumber": "8888888888",
                                  "customerName": "E2E Buyer",
                                  "address": "Test Gurgaon",
                                  "items": [
                                    {
                                      "itemName": "E2E Marble",
                                      "category": "marble",
                                      "pricePerUnit": 120,
                                      "quantity": 5,
                                      "productId": %d,
                                      "unit": "sqft"
                                    }
                                  ],
                                  "taxPercentage": 0,
                                  "discountAmount": 0,
                                  "labourCharge": 0,
                                  "transportationCharge": 0,
                                  "otherExpenses": 0
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.totalAmount").value(600.0));

        mockMvc.perform(get("/api/bills")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(get("/api/bills")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }

    private String registerAndLogin(String name, String email, String password, String location, String role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "email": "%s",
                                  "password": "%s",
                                  "location": "%s",
                                  "role": "%s"
                                }
                                """.formatted(name, email, password, location, role)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        JsonNode root = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private Long readDataFieldAsLong(MvcResult result, String field) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path(field).asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

