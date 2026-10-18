-- Crea los usuarios internos (ADMIN y CAJERO), ya que no hay endpoint para eso.
-- Se corre una sola vez, después de que core-api haya aplicado las migraciones.
-- Antes de correrlo hay que reemplazar los correos y contraseñas entre < >.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO usuario (email, password_hash, rol)
VALUES
    ('<CORREO_ADMIN>', crypt('<CONTRASENA_ADMIN>', gen_salt('bf', 10)), 'ADMIN'),
    ('<CORREO_CAJERO>', crypt('<CONTRASENA_CAJERO>', gen_salt('bf', 10)), 'CAJERO')
ON CONFLICT (email) DO NOTHING;
