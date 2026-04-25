package com.katariastoneworld.apis.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/** Mirrors {@code BillService#createNonGSTBill} gross total (no tax line). */
class NonGstBillTotalsMathTest {

    private static BigDecimal nz(BigDecimal b) {
        return b != null ? b.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nonGstTotal(BigDecimal subtotal, BigDecimal service, BigDecimal labour, BigDecimal transport,
            BigDecimal other, BigDecimal discount) {
        BigDecimal total = nz(subtotal).add(nz(service)).add(nz(labour)).add(nz(transport)).add(nz(other)).subtract(nz(discount));
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    void simpleSubtotalOnly() {
        assertThat(nonGstTotal(new BigDecimal("250.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO)).isEqualByComparingTo("250.00");
    }

    @Test
    void withChargesAndDiscount() {
        assertThat(nonGstTotal(
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                new BigDecimal("2.00"),
                new BigDecimal("3.00"),
                new BigDecimal("20.00"))).isEqualByComparingTo("100.00");
    }
}
