package com.katariastoneworld.apis.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents and locks the GST bill total math used at bill creation time
 * ({@code BillService#createGSTBill}): subtotal → tax → gross with charges and discount.
 */
class GstBillTotalsMathTest {

    private static BigDecimal taxOnSubtotal(BigDecimal subtotal, BigDecimal taxRatePercent) {
        return subtotal.multiply(taxRatePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal grossTotal(
            BigDecimal subtotal,
            BigDecimal taxRatePercent,
            BigDecimal serviceCharge,
            BigDecimal labour,
            BigDecimal transport,
            BigDecimal other,
            BigDecimal discount) {
        BigDecimal taxAmount = taxOnSubtotal(subtotal, taxRatePercent);
        BigDecimal total = subtotal.add(taxAmount)
                .add(nz(serviceCharge))
                .add(nz(labour))
                .add(nz(transport))
                .add(nz(other))
                .subtract(nz(discount));
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal b) {
        return b != null ? b.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    void subtotal100_at18Percent_taxIs18() {
        BigDecimal subtotal = new BigDecimal("100.00");
        assertThat(taxOnSubtotal(subtotal, new BigDecimal("18"))).isEqualByComparingTo("18.00");
    }

    @ParameterizedTest
    @CsvSource({
            "100.00, 18, 18.00, 118.00",
            "50.00, 5, 2.50, 52.50",
            "99.99, 18, 18.00, 117.99"
    })
    void taxAndSimpleTotal(String subtotalStr, String rateStr, String expectedTax, String expectedTotal) {
        BigDecimal subtotal = new BigDecimal(subtotalStr);
        BigDecimal rate = new BigDecimal(rateStr);
        assertThat(taxOnSubtotal(subtotal, rate)).isEqualByComparingTo(expectedTax);
        BigDecimal gross = grossTotal(subtotal, rate, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO);
        assertThat(gross).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void chargesAndDiscount_foldIntoTotal_likeBillService() {
        BigDecimal subtotal = new BigDecimal("200.00");
        BigDecimal rate = new BigDecimal("9");
        // tax = 18.00
        BigDecimal total = grossTotal(subtotal, rate,
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                new BigDecimal("3.00"),
                new BigDecimal("2.00"),
                new BigDecimal("50.00"));
        // 200 + 18 + 10 + 5 + 3 + 2 - 50 = 188
        assertThat(total).isEqualByComparingTo("188.00");
    }

    @Test
    void discountCannotDriveTotal_negativeFloorsToZero() {
        BigDecimal total = grossTotal(new BigDecimal("10.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("999.00"));
        assertThat(total).isEqualByComparingTo("0.00");
    }
}
