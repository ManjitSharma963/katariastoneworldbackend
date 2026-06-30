package com.katariastoneworld.apis.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanEditWindowTest {

    @Test
    void allowsEntryWithinSevenDays() {
        LocalDate recent = LocalDate.now().minusDays(3);
        assertThatCode(() -> LoanEditWindow.assertEditable(recent, null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEntryOlderThanSevenDays() {
        LocalDate old = LocalDate.now().minusDays(8);
        assertThatThrownBy(() -> LoanEditWindow.assertEditable(old, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7 days");
    }
}
