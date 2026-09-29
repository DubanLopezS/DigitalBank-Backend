package com.fabricaescuela.digitalbank.auth.interfaces.services;

import java.util.UUID;

public interface ICredencialesService {
    void registrarCredencialesCliente(UUID clienteId, String email, String rawPassword);
}