package com.fabricaescuela.digitalbank.cuenta.service;

import com.fabricaescuela.digitalbank.cuenta.repository.CuentaRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class GeneradorNumeroCuenta {

    private static final int LONGITUD = 10;
    private static final int MAX_INTENTOS = 25;

    private final SecureRandom random = new SecureRandom();
    private final CuentaRepository cuentaRepository;

    public GeneradorNumeroCuenta(CuentaRepository cuentaRepository) {
        this.cuentaRepository = cuentaRepository;
    }

    public String generar() {
        for (int intento = 0; intento < MAX_INTENTOS; intento++) {
            String candidato = construir();
            if (!cuentaRepository.existsByNumeroCuenta(candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("No fue posible generar un numero de cuenta unico");
    }

    private String construir() {
        StringBuilder sb = new StringBuilder(LONGITUD);
        sb.append(1 + random.nextInt(9));
        for (int i = 1; i < LONGITUD; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}