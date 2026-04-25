package com.katariastoneworld.apis.integration;

import com.katariastoneworld.apis.dto.ProductRequestDTO;
import com.katariastoneworld.apis.dto.ProductResponseDTO;
import com.katariastoneworld.apis.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StockConcurrencyIntegrationTest {

    @Autowired
    private ProductService productService;

    @Test
    void concurrentDeduction_onlyOneSucceeds_whenStockInsufficientForBoth() throws Exception {
        String location = "Test-Branch";

        ProductRequestDTO p = new ProductRequestDTO();
        p.setName("Granite Concurrency");
        p.setSlug("granite-concurrency-1");
        p.setProductType("granite");
        p.setPricePerUnit(200.0);
        p.setQuantity(10.0);
        p.setUnit("sqft");
        p.setPrimaryImageUrl("https://example.com/granite.jpg");
        ProductResponseDTO created = productService.createProduct(p, location);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> t1 = () -> attemptDeduct(created.getId(), start);
        Callable<Boolean> t2 = () -> attemptDeduct(created.getId(), start);

        Future<Boolean> f1 = pool.submit(t1);
        Future<Boolean> f2 = pool.submit(t2);
        start.countDown();

        List<Boolean> results = new ArrayList<>();
        results.add(f1.get(20, TimeUnit.SECONDS));
        results.add(f2.get(20, TimeUnit.SECONDS));

        pool.shutdownNow();

        long successCount = results.stream().filter(Boolean::booleanValue).count();
        long failureCount = results.size() - successCount;

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);

        ProductResponseDTO after = productService.getProductById(created.getId(), location);
        assertThat(BigDecimal.valueOf(after.getQuantity())).isEqualByComparingTo("2.0");
    }

    private boolean attemptDeduct(Long productId, CountDownLatch start) {
        try {
            start.await(10, TimeUnit.SECONDS);
            productService.deductStock(productId, new BigDecimal("8.00"), null, "concurrency test", null);
            return true;
        } catch (RuntimeException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
