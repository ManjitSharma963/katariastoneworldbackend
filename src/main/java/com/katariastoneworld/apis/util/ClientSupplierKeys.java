package com.katariastoneworld.apis.util;

import java.util.Locale;

public final class ClientSupplierKeys {

    private ClientSupplierKeys() {
    }

    public static String normalize(String clientName) {
        if (clientName == null) {
            return "";
        }
        return clientName.trim().toLowerCase(Locale.ROOT);
    }
}
