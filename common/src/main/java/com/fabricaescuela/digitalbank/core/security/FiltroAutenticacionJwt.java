package com.fabricaescuela.digitalbank.core.security;

import jakarta.servlet.Filter;

/**
 * Marca el filtro que valida el JWT en cada API. Cada servicio tiene su propia
 * implementación, y la configuración de seguridad compartida la ubica por este tipo.
 */
public interface FiltroAutenticacionJwt extends Filter {
}
