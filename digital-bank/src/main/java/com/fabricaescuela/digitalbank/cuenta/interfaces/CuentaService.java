package com.fabricaescuela.digitalbank.cuenta.interfaces;

import com.fabricaescuela.digitalbank.cuenta.dto.AperturaCuentaRequest;
import com.fabricaescuela.digitalbank.cuenta.dto.CuentaResponse;

import java.util.UUID;

public interface CuentaService {
    CuentaResponse abrirCuenta(AperturaCuentaRequest request, UUID clienteIdAutenticado);
}