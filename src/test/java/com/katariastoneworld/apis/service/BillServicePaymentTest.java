package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.dto.BillPaymentRequestDTO;
import com.katariastoneworld.apis.repository.BillRepository;
import com.katariastoneworld.apis.repository.BillPaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class BillServicePaymentTest {

    @Mock
    private BillRepository billRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private BillNumberGeneratorService billNumberGeneratorService;
    @Mock
    private ProductService productService;
    @Mock
    private EmailService emailService;
    @Mock
    private BillPaymentRepository billPaymentRepository;
    @Mock
    private CustomerAdvanceService customerAdvanceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BillService billService;

    @Test
    void addPaymentToBill_rejectsNullRequest() {
        assertThrows(IllegalArgumentException.class,
                () -> billService.addPaymentToBill(1L, "GST", null, "Loc", 1L));
    }

    @Test
    void addPaymentToBill_rejectsNonPositiveAmount() {
        BillPaymentRequestDTO dto = new BillPaymentRequestDTO();
        dto.setAmount(0.0);
        dto.setPaymentMode("CASH");
        assertThrows(IllegalArgumentException.class,
                () -> billService.addPaymentToBill(1L, "GST", dto, "Loc", 1L));
    }
}
