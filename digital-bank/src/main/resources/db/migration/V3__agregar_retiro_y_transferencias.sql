-- Se quita el CHECK de tipo y se vuelve a crear abajo con los valores nuevos.
ALTER TABLE transaccion
    DROP CONSTRAINT ck_transaccion_tipo;

-- transferencia_id liga las dos filas de una transferencia; no es FK porque no apunta a ninguna tabla.
ALTER TABLE transaccion
    ADD COLUMN transferencia_id UUID,
    ADD COLUMN sentido VARCHAR(10),
    ADD COLUMN descripcion VARCHAR(255);

-- transferencia_id y sentido son obligatorios en las filas de transferencia y NULL en las demás.
ALTER TABLE transaccion
    ADD CONSTRAINT ck_transaccion_tipo CHECK (tipo IN ('DEPOSITO','RETIRO','TRANSFERENCIA')),
    ADD CONSTRAINT ck_transaccion_sentido CHECK (sentido IN ('SALIENTE','ENTRANTE')),
    ADD CONSTRAINT ck_transaccion_transferencia CHECK ((tipo = 'TRANSFERENCIA' AND transferencia_id IS NOT NULL AND sentido IS NOT NULL) OR (tipo <> 'TRANSFERENCIA' AND transferencia_id IS NULL AND sentido IS NULL));

CREATE INDEX idx_transaccion_transferencia ON transaccion(transferencia_id);
