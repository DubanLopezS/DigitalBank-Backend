package com.fabricaescuela.digitalbank.cliente.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos de contacto que se pueden actualizar. El tipo y el número de documento
 * no están aquí porque son datos de identidad que no se editan.
 */
public record ClienteActualizacionRequest(
        @Email(message = "Formato de correo inválido")
        @Pattern(regexp = ".*\\S.*", message = "Formato de correo inválido")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        String email,

        @Pattern(regexp = "\\d{10}", message = "El teléfono debe tener 10 dígitos")
        String telefono
) {
    @AssertTrue(message = "Debe enviar el correo o el teléfono")
    public boolean isAlgunDatoPresente() {
        return email != null || telefono != null;
    }
}
