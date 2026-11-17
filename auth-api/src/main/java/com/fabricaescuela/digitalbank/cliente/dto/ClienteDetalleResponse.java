package com.fabricaescuela.digitalbank.cliente.dto;

import com.fabricaescuela.digitalbank.cliente.entity.Cliente;
import com.fabricaescuela.digitalbank.cliente.entity.TipoDocumento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ClienteDetalleResponse(
        UUID clienteId,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String telefono,
        String email,
        String estado,
        LocalDateTime fechaRegistro,
        List<CuentaDelClienteResponse> cuentas
) {

    public static ClienteDetalleResponse from(Cliente cliente, List<CuentaDelClienteResponse> cuentas) {
        return new ClienteDetalleResponse(
                cliente.getId(),
                cliente.getTipoDocumento(),
                cliente.getNumeroDocumento(),
                cliente.getNombres(),
                cliente.getApellidos(),
                cliente.getFechaNacimiento(),
                cliente.getTelefono(),
                cliente.getEmail(),
                cliente.getEstado().name(),
                cliente.getFechaRegistro(),
                cuentas
        );
    }
}