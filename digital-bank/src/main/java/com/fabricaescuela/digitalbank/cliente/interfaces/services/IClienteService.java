package com.fabricaescuela.digitalbank.cliente.interfaces.services;

import com.fabricaescuela.digitalbank.cliente.dto.ClienteRegistroRequest;
import com.fabricaescuela.digitalbank.cliente.dto.ClienteResponse;

public interface IClienteService {
    ClienteResponse registrarCliente(ClienteRegistroRequest request);
}
