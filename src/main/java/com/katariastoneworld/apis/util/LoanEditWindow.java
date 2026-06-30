package com.katariastoneworld.apis.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class LoanEditWindow {

    public static final int DAYS = 7;

    private LoanEditWindow() {
    }

    public static void assertEditable(LocalDate entryDate, LocalDateTime createdAt) {
        LocalDate ref = entryDate != null ? entryDate
                : (createdAt != null ? createdAt.toLocalDate() : LocalDate.now());
        LocalDate cutoff = LocalDate.now().minusDays(DAYS);
        if (ref.isBefore(cutoff)) {
            throw new IllegalArgumentException(
                    "Only loan transactions from the last " + DAYS + " days can be edited or deleted.");
        }
    }
}
