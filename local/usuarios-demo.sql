-- Usuarios internos para la base LOCAL de docker-compose y Kubernetes (solo demo).
-- Se corre una vez, cuando core-api ya aplicó las migraciones:
--   docker compose exec postgres psql -U digitalbank -d digitalbank -f /scripts/usuarios-demo.sql
-- Nunca usar estas claves en Supabase: allá se usa docs/scripts/crear-usuarios-internos.sql.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO usuario (email, password_hash, rol)
VALUES
    ('admin@digitalbank.local',  crypt('Admin12345*',  gen_salt('bf', 10)), 'ADMIN'),
    ('cajero@digitalbank.local', crypt('Cajero12345*', gen_salt('bf', 10)), 'CAJERO')
ON CONFLICT (email) DO NOTHING;

SELECT email, rol FROM usuario WHERE rol IN ('ADMIN', 'CAJERO') ORDER BY rol;
