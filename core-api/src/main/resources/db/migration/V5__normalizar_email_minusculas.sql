-- El correo se guarda siempre en minúsculas y sin espacios a los lados (HZ-08).
-- Si ya hay correos que solo se diferencian en mayúsculas o espacios, la migración
-- se detiene: esos casos se revisan a mano, aquí no se borra ni se fusiona nada.
DO $$
DECLARE
    choques_cliente INT;
    choques_usuario INT;
BEGIN
    SELECT count(*) INTO choques_cliente
    FROM (
        SELECT lower(btrim(email))
        FROM cliente
        GROUP BY lower(btrim(email))
        HAVING count(*) > 1
    ) repetidos;

    SELECT count(*) INTO choques_usuario
    FROM (
        SELECT lower(btrim(email))
        FROM usuario
        GROUP BY lower(btrim(email))
        HAVING count(*) > 1
    ) repetidos;

    IF choques_cliente > 0 OR choques_usuario > 0 THEN
        RAISE EXCEPTION 'V5 detenida: hay correos que solo se diferencian en mayúsculas o espacios (cliente: %, usuario: %). Corríjalos a mano y vuelva a migrar.',
            choques_cliente, choques_usuario;
    END IF;
END $$;

UPDATE cliente
SET email = lower(btrim(email))
WHERE email <> lower(btrim(email));

UPDATE usuario
SET email = lower(btrim(email))
WHERE email <> lower(btrim(email));

ALTER TABLE cliente
    ADD CONSTRAINT ck_cliente_email_minusculas CHECK (email = lower(email));

ALTER TABLE usuario
    ADD CONSTRAINT ck_usuario_email_minusculas CHECK (email = lower(email));
