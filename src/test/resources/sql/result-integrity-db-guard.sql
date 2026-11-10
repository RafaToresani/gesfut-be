-- result-integrity-db-guard.sql
-- Va PRIMERO en los @Sql BEFORE y AFTER de ResultIntegrityIntegrationTests: si la conexion no esta en
-- exactamente gesfut_isolated_test (o DATABASE() es NULL), falla ANTES de cualquier cleanup o seed.
--
-- Por que no SIGNAL: en MySQL, SIGNAL solo vale dentro de programas almacenados. Crear un procedure exige
-- CREATE ROUTINE y choca con los delimitadores de ScriptUtils (separa por ';'). Se usa una sola sentencia simple.
--
-- Como falla: la subconsulta escalar devuelve 0 filas (NULL, sin error) cuando la base es la correcta y
-- 2 filas cuando no lo es -> error 1242 "Subquery returns more than 1 row". ScriptUtils trata el error como
-- falla (errorMode FAIL_ON_ERROR) y no sigue con el resto de los scripts del @Sql.
-- No depende de evaluar solo una rama de IF: la condicion esta en el WHERE, asi que el resultado es el mismo
-- aunque el optimizador evalue la subconsulta por adelantado.
-- BINARY hace la comparacion sensible a mayusculas (GESFUT_ISOLATED_TEST no pasa aunque el servidor no distinga).
-- '<=>' es igualdad segura ante NULL: BINARY NULL es NULL y NULL <=> 'gesfut_isolated_test' es 0,
-- asi que DATABASE() NULL tambien falla. BINARY tiene mas precedencia que '<=>'.
-- Nota: desde MySQL 8.0.27 el operador BINARY da un WARNING de deprecacion (no error; ScriptUtils no falla
-- por warnings). Equivalente sin warning: CAST(DATABASE() AS BINARY) <=> 'gesfut_isolated_test'.
SELECT (SELECT two.x
          FROM (SELECT 1 AS x UNION ALL SELECT 2 AS x) AS two
         WHERE NOT (BINARY DATABASE() <=> 'gesfut_isolated_test')) AS gesfut_isolated_db_guard;
