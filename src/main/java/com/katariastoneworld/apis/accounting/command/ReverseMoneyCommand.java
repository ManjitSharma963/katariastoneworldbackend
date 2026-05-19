package com.katariastoneworld.apis.accounting.command;

/** Reverse an existing {@code transactions} row by id (offsetting row + cancel original). */
public record ReverseMoneyCommand(
        String location,
        Long transactionId,
        String requestId,
        String reason) {
}
