-- 1. Las tablas de auditoría no se editan ni se borran.
CREATE FUNCTION fn_bloquear_cambios_auditoria() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La tabla % es de auditoría: no se permite % de filas', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_aud_datos_cliente_inmutable
    BEFORE UPDATE OR DELETE ON auditoria_datos_cliente
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios_auditoria();

CREATE TRIGGER trg_mov_admin_cliente_inmutable
    BEFORE UPDATE OR DELETE ON movimiento_administrativo_cliente
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios_auditoria();

CREATE TRIGGER trg_mov_admin_cuenta_inmutable
    BEFORE UPDATE OR DELETE ON movimiento_administrativo_cuenta
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_cambios_auditoria();

-- 2. El historial de transacciones no se borra 
CREATE FUNCTION fn_bloquear_borrado_transaccion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Las transacciones no se borran: el historial se conserva';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transaccion_no_borrar
    BEFORE DELETE ON transaccion
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado_transaccion();

-- 3. Una transacción solo puede pasar de COMPLETADA a REVERSADA, y solo en ese
--    momento se enlaza a su reverso (que debe ser un REVERSO que la apunte a ella).
CREATE FUNCTION fn_proteger_transaccion() RETURNS trigger AS $$
BEGIN
    IF NEW.cuenta_id          IS DISTINCT FROM OLD.cuenta_id
    OR NEW.tipo               IS DISTINCT FROM OLD.tipo
    OR NEW.monto              IS DISTINCT FROM OLD.monto
    OR NEW.saldo_anterior     IS DISTINCT FROM OLD.saldo_anterior
    OR NEW.saldo_nuevo        IS DISTINCT FROM OLD.saldo_nuevo
    OR NEW.origen             IS DISTINCT FROM OLD.origen
    OR NEW.fecha_hora         IS DISTINCT FROM OLD.fecha_hora
    OR NEW.transferencia_id   IS DISTINCT FROM OLD.transferencia_id
    OR NEW.sentido            IS DISTINCT FROM OLD.sentido
    OR NEW.descripcion        IS DISTINCT FROM OLD.descripcion
    OR NEW.usuario_id         IS DISTINCT FROM OLD.usuario_id
    OR NEW.motivo             IS DISTINCT FROM OLD.motivo THEN
        RAISE EXCEPTION 'Una transacción no se modifica: solo puede pasar a REVERSADA';
    END IF;

    IF NEW.estado IS DISTINCT FROM OLD.estado
        AND NOT (OLD.estado = 'COMPLETADA' AND NEW.estado = 'REVERSADA') THEN
        RAISE EXCEPTION 'Cambio de estado no permitido: % a %', OLD.estado, NEW.estado;
    END IF;

    -- El enlace solo se asigna una vez, al pasar a REVERSADA.
    IF NEW.transaccion_relacionada_id IS DISTINCT FROM OLD.transaccion_relacionada_id
        AND NOT (OLD.estado = 'COMPLETADA' AND NEW.estado = 'REVERSADA'
                AND OLD.transaccion_relacionada_id IS NULL) THEN
        RAISE EXCEPTION 'El enlace con el reverso solo se asigna al pasar a REVERSADA';
    END IF;

    -- Al pasar a REVERSADA, el enlace debe apuntar al REVERSO que anula esta transacción.
    IF OLD.estado = 'COMPLETADA' AND NEW.estado = 'REVERSADA'
        AND NOT EXISTS (
            SELECT 1 FROM transaccion r
            WHERE r.id = NEW.transaccion_relacionada_id
                AND r.tipo = 'REVERSO'
                AND r.transaccion_relacionada_id = NEW.id) THEN
        RAISE EXCEPTION 'La transacción solo puede pasar a REVERSADA enlazada a su REVERSO';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transaccion_proteger
    BEFORE UPDATE ON transaccion
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_transaccion();

-- 4. Un cliente INACTIVO no recibe cuentas nuevas (HU-04).
CREATE FUNCTION fn_validar_cliente_activo_cuenta() RETURNS trigger AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM cliente WHERE id = NEW.cliente_id AND estado = 'INACTIVO') THEN
        RAISE EXCEPTION 'El cliente está inactivo: no puede tener cuentas nuevas';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cuenta_cliente_activo
    BEFORE INSERT ON cuenta
    FOR EACH ROW EXECUTE FUNCTION fn_validar_cliente_activo_cuenta();