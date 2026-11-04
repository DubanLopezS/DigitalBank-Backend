package com.fabricaescuela.digitalbank.core.util;

import java.util.Locale;

/**
 * Deja el correo en una sola forma (sin espacios a los lados y en minúsculas)
 * para que el registro y el login no distingan mayúsculas.
 */
public final class NormalizadorCorreo {

    private NormalizadorCorreo() {}

    public static String normalizar(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
