-- grant-check.sql — NO ejecutar contra MySQL80. Solo contra el contenedor descartable.
-- Verifica que gesfut_test_only tenga privilegios SOLO sobre gesfut_isolated_test.*.
--
-- Cómo correrlo sin exponer la credencial (la contraseña nunca sale del contenedor: el sh interno
-- usa su propio MYSQL_PASSWORD como MYSQL_PWD; el SQL entra por stdin):
--
--   Get-Content .\grant-check.sql -Raw |
--     docker exec -i 39f7d94396ef603f251a946a0ca51dbd160e32a63aef8c7d8618533218645f38 `
--       sh -c 'MYSQL_PWD=$MYSQL_PASSWORD exec mysql -ugesfut_test_only -N -B'
--
-- (test-isolated.ps1 hace lo mismo en la etapa 5 con SHOW GRANTS y valida el resultado.)

-- 1) Identidad efectiva
SELECT CURRENT_USER();
-- Esperado: gesfut_test_only@%

-- 2) Grants del usuario
SHOW GRANTS FOR 'gesfut_test_only'@'%';
-- Esperado EXACTAMENTE (entrypoint oficial de mysql:8.0):
--   GRANT USAGE ON *.* TO `gesfut_test_only`@`%`
--   GRANT ALL PRIVILEGES ON `gesfut_isolated_test`.* TO `gesfut_test_only`@`%`
-- Rechazar si aparece: cualquier privilegio distinto de USAGE sobre *.*, FILE, SUPER,
-- WITH GRANT OPTION, otra base, o grants de roles.

-- 3) Privilegios globales vistos desde information_schema (solo debe haber USAGE)
SELECT PRIVILEGE_TYPE, IS_GRANTABLE
  FROM information_schema.USER_PRIVILEGES
 WHERE GRANTEE = '''gesfut_test_only''@''%''';
-- Esperado: una sola fila: USAGE | NO

-- 4) Privilegios por base: solo gesfut_isolated_test y nunca grantables
SELECT DISTINCT TABLE_SCHEMA, IS_GRANTABLE
  FROM information_schema.SCHEMA_PRIVILEGES
 WHERE GRANTEE = '''gesfut_test_only''@''%''';
-- Esperado: gesfut_isolated_test | NO   (ninguna otra fila)

-- 5) Bases visibles para el usuario
SHOW DATABASES;
-- Esperado: information_schema, performance_schema, gesfut_isolated_test (no GESFUT2: es otro servidor).
