package com.fabricaescuela.digitalbank.auth.interfaces;

import java.util.UUID;

public interface CredencialesService {
    void registrarCredencialesCliente(UUID clienteId, String email, String rawPassword);
}