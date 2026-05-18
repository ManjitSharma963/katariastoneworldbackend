package com.katariastoneworld.apis.service;

import com.katariastoneworld.apis.entity.BillKind;
import com.katariastoneworld.apis.entity.InventoryDirection;
import com.katariastoneworld.apis.entity.InventoryReferenceType;
import com.katariastoneworld.apis.entity.InventoryTxnType;
import com.katariastoneworld.apis.entity.Product;
import com.katariastoneworld.apis.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceStockTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryTransactionService inventoryTransactionService;
    @Mock
    private InventoryReservationService inventoryReservationService;
    @Mock
    private ProductChangeHistoryService productChangeHistoryService;
    @Mock
    private SupplierService supplierService;
    @Mock
    private DealerService dealerService;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService();
        ReflectionTestUtils.setField(productService, "productRepository", productRepository);
        ReflectionTestUtils.setField(productService, "inventoryTransactionService", inventoryTransactionService);
        ReflectionTestUtils.setField(productService, "inventoryReservationService", inventoryReservationService);
        ReflectionTestUtils.setField(productService, "productChangeHistoryService", productChangeHistoryService);
        ReflectionTestUtils.setField(productService, "supplierService", supplierService);
        ReflectionTestUtils.setField(productService, "dealerService", dealerService);
    }

    @Test
    void validateStockAvailability_throwsWhenReservedConsumesAvailability() {
        Long pid = 1L;
        Product p = new Product();
        p.setId(pid);
        p.setName("Marble A");
        p.setQuantity(new BigDecimal("10.00"));
        p.setUnit("sqft");
        when(productRepository.findById(pid)).thenReturn(java.util.Optional.of(p));
        when(inventoryReservationService.sumActiveReservedForProduct(pid)).thenReturn(new BigDecimal("8.00"));

        assertThatThrownBy(() -> productService.validateStockAvailability(pid, new BigDecimal("3.00")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void validateStockAvailability_okWhenEnoughAfterReservations() {
        Long pid = 2L;
        Product p = new Product();
        p.setId(pid);
        p.setName("Granite B");
        p.setQuantity(new BigDecimal("10.00"));
        when(productRepository.findById(pid)).thenReturn(java.util.Optional.of(p));
        when(inventoryReservationService.sumActiveReservedForProduct(pid)).thenReturn(new BigDecimal("2.00"));

        productService.validateStockAvailability(pid, new BigDecimal("7.00"));
    }

    @Test
    void deductStock_appendsSaleOutAndReducesCachedQuantity() {
        Long pid = 3L;
        Product locked = new Product();
        locked.setId(pid);
        locked.setName("Tile");
        locked.setQuantity(new BigDecimal("50.00"));
        locked.setUnit("sqft");
        locked.setLocationId(9L);
        when(productRepository.findByIdForUpdate(pid)).thenReturn(java.util.Optional.of(locked));
        when(inventoryReservationService.sumActiveReservedExcludingBill(pid, 100L, BillKind.GST))
                .thenReturn(new BigDecimal("0.00"));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.deductStock(pid, new BigDecimal("12.50"), 100L, "Test note", BillKind.GST);

        var qtyCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(qtyCaptor.capture());
        assertThat(qtyCaptor.getValue().getQuantity()).isEqualByComparingTo("37.50");

        verify(inventoryTransactionService).append(
                eq(pid),
                eq(InventoryTxnType.SALE),
                eq(InventoryDirection.OUT),
                eq(new BigDecimal("12.50")),
                eq(InventoryReferenceType.BILL),
                eq(100L),
                eq("GST"),
                eq("Test note"),
                eq(9L),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull());
    }

    @Test
    void deductStock_throwsWhenNotEnoughAfterExcludingSameBillReservations() {
        Long pid = 4L;
        Product locked = new Product();
        locked.setId(pid);
        locked.setName("Low");
        locked.setQuantity(new BigDecimal("10.00"));
        locked.setUnit("sqft");
        when(productRepository.findByIdForUpdate(pid)).thenReturn(java.util.Optional.of(locked));
        when(inventoryReservationService.sumActiveReservedExcludingBill(pid, 5L, BillKind.NON_GST))
                .thenReturn(new BigDecimal("4.00"));

        assertThatThrownBy(() -> productService.deductStock(pid, new BigDecimal("8.00"), 5L, "n", BillKind.NON_GST))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void recordBillStockReturn_wrongLocation_throws() {
        Long pid = 5L;
        Product locked = new Product();
        locked.setId(pid);
        locked.setLocation("Branch-A");
        when(productRepository.findByIdForUpdate(pid)).thenReturn(java.util.Optional.of(locked));

        assertThatThrownBy(() -> productService.recordBillStockReturn(
                pid, new BigDecimal("1.00"), 9L, BillKind.GST, "n", "Branch-B"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void recordBillStockReturn_appendsReturnInAndIncreasesQty() {
        Long pid = 6L;
        Product locked = new Product();
        locked.setId(pid);
        locked.setLocation("Loc1");
        locked.setQuantity(new BigDecimal("5.00"));
        locked.setLocationId(2L);
        when(productRepository.findByIdForUpdate(pid)).thenReturn(java.util.Optional.of(locked));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryTransactionService.findLatestBillSaleTransactionId(eq(pid), eq(44L), eq(BillKind.GST)))
                .thenReturn(Optional.empty());

        productService.recordBillStockReturn(pid, new BigDecimal("2.25"), 44L, BillKind.GST, "Partial", "Loc1");

        verify(inventoryTransactionService).append(
                eq(pid),
                eq(InventoryTxnType.RETURN),
                eq(InventoryDirection.IN),
                eq(new BigDecimal("2.25")),
                eq(InventoryReferenceType.BILL),
                eq(44L),
                eq("GST"),
                eq("Partial"),
                eq(2L),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull());
        var cap = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(cap.capture());
        assertThat(cap.getValue().getQuantity()).isEqualByComparingTo("7.25");
    }
}
