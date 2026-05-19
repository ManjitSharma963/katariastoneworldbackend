package com.katariastoneworld.apis.accounting.command;

/** Cancel active money line by idempotency {@code request_id} (append-only). */
public record VoidByRequestIdCommand(String location, String requestId, String voidReason) {

    public static VoidByRequestIdCommand of(String location, String requestId, String voidReason) {
        return new VoidByRequestIdCommand(location, requestId, voidReason);
    }
}
