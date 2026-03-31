package com.katariastoneworld.apis.util;

/**
 * Current user id for ledger audit fields, set per request after JWT validation.
 */
public final class LedgerAuditContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private LedgerAuditContext() {
    }

    public static void setUserId(Long userId) {
        if (userId == null) {
            USER_ID.remove();
        } else {
            USER_ID.set(userId);
        }
    }

    public static Long getUserIdOrNull() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
