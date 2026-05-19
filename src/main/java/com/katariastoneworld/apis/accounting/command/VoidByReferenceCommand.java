package com.katariastoneworld.apis.accounting.command;

import com.katariastoneworld.apis.accounting.dto.AccountingReference;

/** Cancel active money lines for a domain reference (append-only; no hard delete). */
public record VoidByReferenceCommand(
        String location,
        AccountingReference reference,
        String voidReason) {

    public static VoidByReferenceCommand of(String location, AccountingReference reference, String voidReason) {
        return new VoidByReferenceCommand(location, reference, voidReason);
    }
}
