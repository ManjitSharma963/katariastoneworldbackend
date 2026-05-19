package com.katariastoneworld.apis.accounting.internal;

import com.katariastoneworld.apis.accounting.command.PostMoneyCommand;
import com.katariastoneworld.apis.entity.MoneyDirection;

import java.math.BigDecimal;

final class PostingValidator {

    private PostingValidator() {
    }

    static void validatePost(PostMoneyCommand command, MoneyDirection expectedDirection) {
        if (command == null) {
            throw new IllegalArgumentException("PostMoneyCommand is required");
        }
        if (command.location() == null || command.location().isBlank()) {
            throw new IllegalArgumentException("location is required");
        }
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (command.direction() != expectedDirection) {
            throw new IllegalArgumentException("direction must be " + expectedDirection);
        }
        if (command.category() == null || command.referenceType() == null || command.referenceId() == null) {
            throw new IllegalArgumentException("category, referenceType, and referenceId are required");
        }
        if (command.paymentMode() == null) {
            throw new IllegalArgumentException("paymentMode is required");
        }
    }
}
