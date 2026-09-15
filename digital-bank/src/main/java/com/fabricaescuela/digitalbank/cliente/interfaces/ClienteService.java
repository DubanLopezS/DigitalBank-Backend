package com.fabricaescuela.digitalbank.cliente.interfaces;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteRegistroRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;

public interface ClienteService {
    ClienteResponse registrarCliente(ClienteRegistroRequest request);
}
