-- A4 #2 y #5 (31/10): columnas nuevas en users.
--  password_changed_at: invalida las sesiones (JWT) emitidas antes del ultimo cambio de contrasena.
--  accepted_terms_at: cuando acepto Terminos y Privacidad (la logica del registro va en otro commit).
ALTER TABLE users
    ADD COLUMN password_changed_at DATETIME(6) NULL,
    ADD COLUMN accepted_terms_at DATETIME(6) NULL;

-- M1: los tokens de reset/verificacion anteriores a A4 #1 eran JWT en claro (empiezan con eyJ); ya no sirven y se borran.
UPDATE users SET reset_token = NULL, reset_token_expiration = NULL
 WHERE reset_token LIKE 'reset:eyJ%' OR reset_token LIKE 'verify:eyJ%';