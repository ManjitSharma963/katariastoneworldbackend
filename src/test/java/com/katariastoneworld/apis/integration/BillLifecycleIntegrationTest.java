package com.katariastoneworld.apis.integration;

import com.katariastoneworld.apis.constants.BillLifecycleStatus;
import com.katariastoneworld.apis.dto.BillItemDTO;
import com.katariastoneworld.apis.dto.BillRequestDTO;
import com.katariastoneworld.apis.dto.BillResponseDTO;
import com.katariastoneworld.apis.dto.BillStockReturnLineRequestDTO;
import com.katariastoneworld.apis.dto.BillStockReturnRequestDTO;
import com.katariastoneworld.apis.dto.ProductRequestDTO;
import com.katariastoneworld.apis.dto.ProductResponseDTO;
import com.katariastoneworld.apis.entity.BillNonGST;
import com.katariastoneworld.apis.repository.BillNonGSTRepository;
import com.katariastoneworld.apis.service.BillNumberGeneratorService;
import com.katariastoneworld.apis.service.BillService;
import com.katariastoneworld.apis.service.EmailService;
import com.katariastoneworld.apis.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class BillLifecycleIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BillService billService;

    @Autowired
    private BillNonGSTRepository billNonGSTRepository;

    @MockBean
    private EmailService emailService;
    @MockBean
    private BillNumberGeneratorService billNumberGeneratorService;

    @Test
    void fullBillLifecycle_createPartialReturnDelete_restoresStockCorrectly() {
        String location = "Test-Branch";
        Long actor = 1001L;
        when(billNumberGeneratorService.generateNonGSTBillNumber(location, actor)).thenReturn("NGB-TEST-1001");

        ProductRequestDTO p = new ProductRequestDTO();
        p.setName("Italian Marble A");
        p.setSlug("italian-marble-a-it");
        p.setProductType("marble");
        p.setPricePerUnit(100.0);
        p.setQuantity(100.0);
        p.setUnit("sqft");
        p.setPrimaryImageUrl("https://example.com/marble.jpg");
        ProductResponseDTO createdProduct = productService.createProduct(p, location);

        BillItemDTO item = new BillItemDTO();
        item.setItemName("Italian Marble A");
        item.setCategory("marble");
        item.setPricePerUnit(100.0);
        item.setQuantity(10.0);
        item.setProductId(createdProduct.getId());
        item.setUnit("sqft");

        BillRequestDTO req = new BillRequestDTO();
        req.setCustomerMobileNumber("9999999999");
        req.setCustomerName("Test Customer");
        req.setAddress("Gurgaon, Haryana");
        req.setItems(List.of(item));
        req.setTaxPercentage(0.0);
        req.setDiscountAmount(0.0);
        req.setLabourCharge(0.0);
        req.setTransportationCharge(0.0);
        req.setOtherExpenses(0.0);

        BillResponseDTO createdBill = billService.createBill(req, location, actor, "admin");
        assertThat(createdBill.getBillType()).isEqualTo("NON_GST");

        ProductResponseDTO afterCreate = productService.getProductById(createdProduct.getId(), location);
        assertThat(afterCreate.getQuantity()).isEqualTo(90.0);

        Long lineId = createdBill.getItems().get(0).getItemId();
        BillStockReturnRequestDTO partialReturn = new BillStockReturnRequestDTO();
        partialReturn.setNotes("Customer returned a few sqft");
        partialReturn.setLines(List.of(new BillStockReturnLineRequestDTO(lineId, 4.0)));

        billService.recordPartialStockReturn(createdBill.getId(), "nongst", partialReturn, location, actor);

        BillNonGST afterReturn = billNonGSTRepository.findById(createdBill.getId()).orElseThrow();
        assertThat(afterReturn.getBillStatus()).isEqualTo(BillLifecycleStatus.PARTIALLY_RETURNED);

        ProductResponseDTO afterPartialReturn = productService.getProductById(createdProduct.getId(), location);
        assertThat(afterPartialReturn.getQuantity()).isEqualTo(94.0);

        billService.deleteBill(createdBill.getId(), "nongst", location, actor, null);

        ProductResponseDTO afterDelete = productService.getProductById(createdProduct.getId(), location);
        assertThat(afterDelete.getQuantity()).isEqualTo(100.0);
    }
}
