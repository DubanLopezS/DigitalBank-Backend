package com.fabricaescuela.digitalbank.integracion;

import com.fabricaescuela.digitalbank.cuenta.exception.ClienteNoHabilitadoException;
import com.fabricaescuela.digitalbank.cuenta.exception.SaldoReversoInsuficienteException;
import com.fabricaescuela.digitalbank.transaccion.dto.DepositoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.RetiroRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.ReversoRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.ReversoResponse;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaRequest;
import com.fabricaescuela.digitalbank.transaccion.dto.TransferenciaResponse;
import com.fabricaescuela.digitalbank.transaccion.entity.OrigenDeposito;
import com.fabricaescuela.digitalbank.transaccion.exception.TransaccionNoReversableException;
import com.fabricaescuela.digitalbank.transaccion.interfaces.repositories.ITransaccionRepository;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IDepositoService;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IRetiroService;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.IReversoService;
import com.fabricaescuela.digitalbank.transaccion.interfaces.services.ITransferenciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Reverso de transacciones (HU-12) sobre PostgreSQL")
class ReversoPostgresIntegrationTest extends IntegracionPostgresBase {

    private static final UUID ADMIN = UUID.randomUUID();
    private static final String MOTIVO = "Movimiento registrado por error";

    @Autowired
    private IDepositoService depositoService;

    @Autowired
    private IRetiroService retiroService;

    @Autowired
    private ITransferenciaService transferenciaService;

    @Autowired
    private IReversoService reversoService;

    @Autowired
    private ITransaccionRepository transaccionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID clienteId;
    private UUID cuentaA;
    private UUID cuentaB;

    @BeforeEach
    void prepararCuentas() {
        clienteId = insertarCliente("1000000001", "ACTIVO");
        cuentaA = insertarCuenta(clienteId, "1000000001", BigDecimal.ZERO);
        cuentaB = insertarCuenta(clienteId, "1000000002", BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Reversar un depósito devuelve el saldo, crea la fila REVERSO y deja la original REVERSADA")
    void reversaUnDeposito() {
        UUID depositoId = depositar(cuentaA, "100000.00");

        ReversoResponse response = reversoService.reversar(depositoId, new ReversoRequest(MOTIVO), ADMIN);

        UUID reversoId = response.reversos().get(0).reversoId();
        Map<String, Object> original = fila(depositoId);
        Map<String, Object> reverso = fila(reversoId);
        assertThat(saldoDe(cuentaA)).isEqualByComparingTo("0.00");
        assertThat(original).containsEntry("estado", "REVERSADA").containsEntry("transaccion_relacionada_id", reversoId);
        assertThat(reverso)
                .containsEntry("tipo", "REVERSO")
                .containsEntry("estado", "COMPLETADA")
                .containsEntry("sentido", "SALIENTE")
                .containsEntry("usuario_id", ADMIN)
                .containsEntry("motivo", MOTIVO)
                .containsEntry("transaccion_relacionada_id", depositoId);
        assertThat((BigDecimal) reverso.get("saldo_anterior")).isEqualByComparingTo("100000.00");
        assertThat((BigDecimal) reverso.get("saldo_nuevo")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Reversar una transferencia devuelve el dinero al origen y deja cuatro filas con el mismo transferencia_id")
    void reversaUnaTransferencia() {
        depositar(cuentaA, "100000.00");
        TransferenciaResponse transferencia = transferir(cuentaA, "1000000002", "30000.00");

        reversoService.reversar(transferencia.id(), new ReversoRequest(MOTIVO), ADMIN);

        assertThat(saldoDe(cuentaA)).isEqualByComparingTo("100000.00");
        assertThat(saldoDe(cuentaB)).isEqualByComparingTo("0.00");
        List<String> filas = jdbc.sql("""
                        SELECT tipo || '/' || estado || '/' || sentido FROM transaccion
                        WHERE transferencia_id = :transferenciaId ORDER BY tipo, sentido
                        """)
                .param("transferenciaId", transferencia.transferenciaId())
                .query(String.class)
                .list();
        assertThat(filas).containsExactly(
                "REVERSO/COMPLETADA/ENTRANTE", "REVERSO/COMPLETADA/SALIENTE",
                "TRANSFERENCIA/REVERSADA/ENTRANTE", "TRANSFERENCIA/REVERSADA/SALIENTE");
    }

    @Test
    @DisplayName("Si otra petición reversa la transacción después de que esta ya consultó su tipo, responde 409 y no la reversa dos veces")
    void segundoReversoConLecturaPreviaResponde409() {
        UUID depositoId = depositar(cuentaA, "100000.00");
        // Segundo depósito para que el saldo alcance y el resultado no dependa de "saldo insuficiente".
        depositar(cuentaA, "100000.00");
        TransactionTemplate peticion = new TransactionTemplate(transactionManager);
        TransactionTemplate otraPeticion = new TransactionTemplate(transactionManager);
        otraPeticion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Es la ventana real del servicio: ya consultó el tipo y, antes de bloquear la fila, otra petición
        // la reversa y confirma. Al bloquear debe ver el estado nuevo, no el que había al consultar.
        assertThatThrownBy(() -> peticion.executeWithoutResult(estado -> {
            transaccionRepository.buscarResumen(depositoId);
            otraPeticion.executeWithoutResult(e -> reversoService.reversar(depositoId, new ReversoRequest(MOTIVO), ADMIN));
            reversoService.reversar(depositoId, new ReversoRequest(MOTIVO), ADMIN);
        })).isInstanceOf(TransaccionNoReversableException.class);

        assertThat(contarReversos()).isEqualTo(1);
        assertThat(saldoDe(cuentaA)).isEqualByComparingTo("100000.00");
    }

    // Se repite porque una carrera entre hilos puede salir bien una vez por suerte.
    @RepeatedTest(value = 10, name = "intento {currentRepetition} de {totalRepetitions}")
    @DisplayName("Dos reversos simultáneos de la misma transacción: uno se aplica y el otro recibe 409")
    void dosReversosSimultaneos() throws Exception {
        UUID depositoId = depositar(cuentaA, "100000.00");
        depositar(cuentaA, "100000.00");
        CountDownLatch largada = new CountDownLatch(1);
        Callable<String> intento = () -> {
            largada.await();
            try {
                reversoService.reversar(depositoId, new ReversoRequest(MOTIVO), ADMIN);
                return "aplicado";
            } catch (TransaccionNoReversableException e) {
                return "409";
            }
        };
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<String> primero = hilos.submit(intento);
            Future<String> segundo = hilos.submit(intento);
            largada.countDown();

            List<String> resultados = List.of(primero.get(30, TimeUnit.SECONDS), segundo.get(30, TimeUnit.SECONDS));

            assertThat(resultados).containsExactlyInAnyOrder("aplicado", "409");
        } finally {
            hilos.shutdownNow();
        }
        assertThat(contarReversos()).isEqualTo(1);
        assertThat(saldoDe(cuentaA)).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("Si el destino ya gastó el dinero, el reverso de la transferencia falla y no queda nada a medias")
    void transferenciaSinSaldoEnDestinoNoDejaNadaAMedias() {
        depositar(cuentaA, "100000.00");
        TransferenciaResponse transferencia = transferir(cuentaA, "1000000002", "30000.00");
        retiroService.registrarRetiro(cuentaB, new RetiroRequest(new BigDecimal("30000.00")), clienteId, UUID.randomUUID());

        assertThatThrownBy(() -> reversoService.reversar(transferencia.id(), new ReversoRequest(MOTIVO), ADMIN))
                .isInstanceOf(SaldoReversoInsuficienteException.class);

        // El crédito al origen se aplicó antes de fallar el débito al destino; el rollback lo deshace.
        assertThat(saldoDe(cuentaA)).isEqualByComparingTo("70000.00");
        assertThat(saldoDe(cuentaB)).isEqualByComparingTo("0.00");
        assertThat(contarReversos()).isZero();
        assertThat(fila(transferencia.id())).containsEntry("estado", "COMPLETADA");
    }

    @Test
    @DisplayName("No se reversa si el cliente de la cuenta está inactivo, y no cambia nada")
    void clienteInactivoNoSeReversa() {
        UUID depositoId = depositar(cuentaA, "100000.00");
        jdbc.sql("UPDATE cliente SET estado = 'INACTIVO' WHERE id = :id").param("id", clienteId).update();

        assertThatThrownBy(() -> reversoService.reversar(depositoId, new ReversoRequest(MOTIVO), ADMIN))
                .isInstanceOf(ClienteNoHabilitadoException.class);

        assertThat(saldoDe(cuentaA)).isEqualByComparingTo("100000.00");
        assertThat(fila(depositoId)).containsEntry("estado", "COMPLETADA");
        assertThat(contarReversos()).isZero();
    }

    private UUID depositar(UUID cuentaId, String monto) {
        return depositoService.registrarDeposito(cuentaId,
                new DepositoRequest(new BigDecimal(monto), OrigenDeposito.EFECTIVO), UUID.randomUUID()).id();
    }

    private TransferenciaResponse transferir(UUID origenId, String numeroDestino, String monto) {
        return transferenciaService.registrarTransferencia(origenId,
                new TransferenciaRequest(numeroDestino, new BigDecimal(monto), "Pago de prueba"),
                clienteId, UUID.randomUUID());
    }

    private Map<String, Object> fila(UUID transaccionId) {
        return jdbc.sql("SELECT * FROM transaccion WHERE id = :id").param("id", transaccionId).query().singleRow();
    }

    private int contarReversos() {
        return jdbc.sql("SELECT count(*) FROM transaccion WHERE tipo = 'REVERSO'").query(Integer.class).single();
    }
}
