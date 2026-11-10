# Aislamiento de los tests de integración de Gesfut (back)

Propuesta para revisión de Código. **Nada de esto se ejecutó** (ni Maven, ni Spring, ni Flyway, ni docker, ni mysql).
Objetivo: que los tests corran **solo** contra el contenedor descartable
`gesfut-test-isolated-20261108174332` (ID `39f7d943…5f38`, `127.0.0.1:52169`) y **nunca** contra el MySQL80
nativo en `127.0.0.1:3306` (base real GESFUT2).

Defensa en capas: **script** (antes de Maven) → **guardián de entorno** (antes de crear beans) →
**guardián de conexión** (antes de abrir el pool) → **grep** (lo que el código de test podría hacer a mano) →
**permisos** del usuario de la base → **verificación de empaquetado**.

## Archivos y destino en el repo

| Archivo (en esta carpeta) | Destino en el repo | Qué hace |
|---|---|---|
| `src/test/java/com/gesfut/testsupport/IsolatedDbRules.java` | igual | Reglas puras (sin Spring): invariantes fijos y parser manual de la URL JDBC. Las reutilizan los dos guardianes. |
| `src/test/java/com/gesfut/testsupport/IsolatedDbGuard.java` | igual | `EnvironmentPostProcessor` (orden `LOWEST_PRECEDENCE`, corre después de ConfigData). Valida perfiles, datasource, overrides de Hikari y Flyway; aborta con `GESFUT ISOLATION GUARD: …`. |
| `src/test/java/com/gesfut/testsupport/IsolatedDbConnectionGuard.java` | igual | `BeanPostProcessor`: valida cada `DataSource` (Hikari, envoltorios, driver-based, desconocidos → rechazo) y el bean `Flyway` antes y después de inicializar, sin conectar. |
| `src/test/java/com/gesfut/testsupport/IsolatedDbGuardInitializer.java` | igual | `ApplicationContextInitializer` que agrega el BPP al BeanFactory de todo contexto Boot (incluye slices). |
| `src/test/resources/META-INF/spring.factories` | igual (fusionar si ya existe) | **Registro principal**: guardián de entorno y initializer. |
| `src/test/java/com/gesfut/testsupport/IsolatedDbGuardTestAutoConfiguration.java` | igual | **Respaldo**: `@AutoConfiguration` con `@Bean static` que registra el BPP solo si el initializer no lo hizo (si ya está, devuelve un BPP vacío). |
| `src/test/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | igual (fusionar si ya existe) | Lista la autoconfiguración de respaldo. |
| `src/test/java/com/gesfut/testsupport/IsolatedDbGuardRegistrationTest.java` | igual | Comprueba el registro real: nombres en todos los `spring.factories` + `SpringFactoriesLoader.forDefaultResourceLocation(..).load(..)` para el EPP y el initializer; `ImportCandidates.load(AutoConfiguration.class, ..)` para el respaldo; idempotencia del respaldo. |
| `src/test/java/com/gesfut/testsupport/IsolatedDbRulesTest.java` | igual | Tests unitarios puros: 3306, rango, localhost, `[::1]`/IPv6, multi-host, failover, variantes de esquema, `address=`, base, usuario, parámetros prohibidos y desconocidos, perfil local, clean-disabled, y positivos. |
| `src/test/java/com/gesfut/testsupport/IsolatedDbGuardTest.java` | igual | Tests del EnvironmentPostProcessor con `MockEnvironment` (sin contexto ni conexión). |
| `src/test/java/com/gesfut/testsupport/IsolatedDbConnectionGuardTest.java` | igual | Tests del BPP con DataSources construidos sin `getConnection()`; verifica que Hikari no arranca (`isRunning()==false`). |
| `negative-fixtures/ExternalDynamicPropertySourceNegativeTest.java` | `scripts/negative-fixtures/` (versionado, fuera de `src/test`: Maven no lo compila) | Test externo con `@DynamicPropertySource` a 3306; `negative-runs.ps1` lo copia temporalmente a `src/test` y lo borra al terminar. |
| `scripts/isolation-common.ps1` | `scripts/aislamiento/` (o fuera del repo) | Invariantes, listas de variables, detección de `application-local.*`, helper de comandos nativos. |
| `scripts/test-isolated.ps1` | idem | Script de corrida: etapas 0-6, `-DryRun`, exit codes, restauración del entorno. |
| `scripts/grep-checks.ps1` | idem | Busca en `src/test` usos que esquiven al guardián; muestra `archivo:línea:patrón` (nunca el contenido). |
| `scripts/check-packaging.ps1` | idem | Tras `mvnw package` (lo corre otra persona): `jar tf` y falla si aparece algo del guardián o un registro de test; revisa también `src/main`. |
| `scripts/negative-runs.ps1` | idem | Plan de 8 corridas que deben fallar por el guardián/preflight; sin `-Run` solo imprime el plan. |
| `scripts/test-listener-check.ps1` | idem | Prueba sin Docker/Maven/base de `Assert-PublishedPortListener` (etapa 4 de `test-isolated.ps1`; el dueno se compara con `-ccontains`, sensible a mayusculas) y de la clave JWT descartable (ver mas abajo): Docker Desktop publica el puerto con un listener que Windows informa con estado vacio; solo se acepta si es 127.0.0.1, dueno `com.docker.backend`/`docker` (nunca mysqld ni otro proceso) y el TCP conecta. 16 casos de listener, 8 de GRANT y 11 de clave JWT, los negativos deben abortar con `GESFUT PREFLIGHT` (exit 2 en `test-isolated.ps1`). |
| `scripts/grant-check.sql` | idem | Consultas de permisos esperados y snippet de `docker exec` sin exponer la credencial. |
| `pc-changes.md` | no va al repo | Procedimiento para dejar `src/test/resources/config/application.properties` sin `spring.datasource.*` ni credenciales, y para pasar `ResultIntegrityIntegrationTests` a datos propios TEST; incluye los supuestos de esquema a verificar contra V1/V2. |
| `pc-changes-ResultIntegrityIntegrationTests.patch` | no va al repo (se aplica) | Patch de referencia (sobre la copia del 7/11): saca la URL 3306, afirma `gesfut_isolated_test`, elimina el `queryForMap` sobre datos preexistentes y usa solo los ids que siembra; `@Sql` antes/después de cada test. |
| `src/test/resources/sql/result-integrity-db-guard.sql` | igual | Primer script de los `@Sql` BEFORE y AFTER: falla con error 1242 si `DATABASE()` no es exactamente `gesfut_isolated_test` (comparación `BINARY DATABASE() <=> 'gesfut_isolated_test'`, sensible a mayúsculas; NULL también falla), antes de cualquier cleanup o seed. No usa `SIGNAL` (solo vale en programas almacenados; un procedure exige `CREATE ROUTINE` y choca con los delimitadores de `ScriptUtils`). |
| `src/test/resources/sql/result-integrity-seed.sql` | igual | Siembra solo datos TEST con ids fijos 990001/990002 (organizador `test+…@example.invalid` con hash ficticio, torneo, equipos, jugador, estadísticas, fecha y partido). |
| `src/test/resources/sql/result-integrity-cleanup.sql` | igual | Borra solo lo sembrado y lo que el test genera sobre ese partido: cada `DELETE` con `WHERE` por ids sembrados y prefijo TEST; sin `TRUNCATE`. Corre antes del seed y después de cada test, siempre después del db-guard. Las estadísticas se borran solo si están ligadas (capturadas por JOIN antes de borrar participantes) a un participante del torneo TEST. |

Los scripts reciben `-RepoRoot` obligatorio, así que pueden vivir fuera del repo (por ejemplo en `C:\Users\matec\prog-tmp\aislamiento\scripts\`).

## Uso previsto (cuando haya APTO)

```powershell
# 1) solo chequeos, sin Maven
.\scripts\test-isolated.ps1 -RepoRoot C:\ruta\al\worktree -DryRun
# 2) corrida real
.\scripts\test-isolated.ps1 -RepoRoot C:\ruta\al\worktree
# 3) negativas (con APTO de Código)
.\scripts\negative-runs.ps1 -RepoRoot C:\ruta\al\worktree          # muestra el plan
.\scripts\negative-runs.ps1 -RepoRoot C:\ruta\al\worktree -Run     # ejecuta
# 4) empaquetado (después de que alguien corra .\mvnw.cmd -B package)
.\scripts\check-packaging.ps1 -RepoRoot C:\ruta\al\worktree
```
Exit codes de `test-isolated.ps1`: `0` OK, `2` abortado en chequeos previos, otro = código de Maven.
El informe final dice en qué etapa se frenó (`ABORTADO 1-entorno: …`, `FRENADO 6-maven: …`, etc.).

## Cobertura de los bloqueantes de la revisión

| # | Bloqueante | Mecanismo | Archivo |
|---|---|---|---|
| 1 | Invariantes fijos en código, no por env | Constantes `HOST=127.0.0.1`, puerto ≠3306 y en 49152-65535, base `gesfut_isolated_test`, usuario `gesfut_test_only`. `GESFUT_ISOLATED_PORT` solo se usa para **exigir además** igualdad con el puerto verificado por el script; si falta, se rechaza. | `IsolatedDbRules` |
| 1 | URL completa normalizada; sin multi-host/failover, localhost, `[::1]`, params peligrosos | Parser manual: esquema exacto `jdbc:mysql://`; rechaza variantes `jdbc:mysql:loadbalance/replication/+srv`, mayúsculas, coma, `address=(…)`, `[`/`]` y cualquier IPv6, puerto ausente, `@` (userinfo), `%`, `#`, `;`, espacios; base exacta; parámetros por **allowlist** (`useSSL`, `allowPublicKeyRetrieval`, `serverTimezone`, `characterEncoding`) con denylist explícita case-insensitive (`socketFactory`, `allowLoadLocalInfile`, `allowLoadLocalInfileInPath`, `autoDeserialize`, `allowUrlInLocalInfile`, `propertiesTransform`, `user`, `password`, interceptores, etc.); duplicados y valores raros rechazados. Devuelve forma normalizada con params ordenados. | `IsolatedDbRules` |
| 1 | Flyway y perfiles | `spring.flyway.url/user` con el mismo chequeo; `spring.flyway.clean-disabled` debe ser `true`; `clean-on-validation-error=true` rechazado. Perfiles activos/`spring.profiles.active`/`include`: solo vacío o `test`. | `IsolatedDbGuard` |
| 2 | Limpiar o rechazar variables de entorno | **Rechaza** (aborta, muestra solo nombres) `SPRING*` (todas, incluye `SPRING_DATASOURCE_*`, `SPRING_FLYWAY_*`, `SPRING_APPLICATION_JSON`, `SPRING_PROFILES_ACTIVE`, `SPRING_CONFIG_*`), `FLYWAY_*`, `JAVA_TOOL_OPTIONS`, `MAVEN_OPTS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`, `MAVEN_ARGS`, `MAVEN_CONFIG`, `MAVEN_DEBUG_OPTS`, `GESFUT_ISOLATED_PORT`, `MYSQL_PWD`. **Limpia** y restaura `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `FLYWAY_ENABLED`. Fuerza `MAVEN_SKIP_RC`. | `isolation-common.ps1`, `test-isolated.ps1` (etapa 1) |
| 2 | Archivos de inyección | Aborta si existen `.mvn/jvm.config`, `.mvn/maven.config` con contenido, `.mvn/extensions.xml`, `mavenrc_pre/post`, `~/.mavenrc`; `~/.m2/settings.xml` (y el `conf/settings.xml` del Maven del wrapper) con `<properties>`, `<activeProfiles>` o perfiles con `<activation>`; devtools globales (`~/.spring-boot-devtools.*`, `~/.config/spring-boot/`); `pom.xml` con `argLine`/`systemPropertyVariables`/`systemProperties`/`environmentVariables` de surefire/failsafe (o `<properties><argLine>`) que toquen datasource/flyway/profiles/config, o `systemPropertiesFile`. | `test-isolated.ps1` (etapa 2) |
| 2 | `application-local.*` sin leerlo | `Test-Path` (nunca lectura) de `.properties/.yml/.yaml` en `./`, `./config/`, `./config/*/`, `src/main/resources[/config]`, `src/test/resources[/config]`, `target/classes[/config]`, `target/test-classes[/config]`. | `isolation-common.ps1` |
| 3 | Registro de los controles | Principal: `spring.factories` (EPP + initializer). Respaldo: `AutoConfiguration.imports` (BPP idempotente). Test de registro con `SpringFactoriesLoader` e `ImportCandidates`. | `spring.factories`, `IsolatedDbGuardTestAutoConfiguration`, `IsolatedDbGuardRegistrationTest` |
| 3 | Segundo control al conectar | `BeanPostProcessor` antes **y** después de inicializar: Hikari (`getJdbcUrl`, `getUsername`, `dataSourceProperties`, sin `dataSourceClassName`/JNDI/DataSource envuelto), `DelegatingDataSource` (desenvuelve), `AbstractDriverBasedDataSource` (DriverManager/SimpleDriver), tipos desconocidos por reflexión o **rechazo**; `Flyway` vía `getConfiguration().getUrl()/getUser()/getDataSource()/isCleanDisabled()`. Cubre `@DynamicPropertySource` y `@Bean` de test. | `IsolatedDbConnectionGuard`, `IsolatedDbGuardInitializer` |
| 3 | grep en los tests (sin excepciones para config ni tests existentes) | `DriverManager`, `DataSourceBuilder`, `new JdbcTemplate`, `DriverManagerDataSource`, `SimpleDriverDataSource`, `new Hikari*`, `@AutoConfigureTestDatabase`, `@DynamicPropertySource`, `@TestPropertySource`, `@ActiveProfiles`, `@ContextConfiguration`/`@SpringJUnitConfig`, `@ImportAutoConfiguration`, `Flyway.configure`, claves `spring.datasource/flyway/profiles/config` en cualquier archivo, `jdbc:`, `System.setProperty`. Allowlist solo para los archivos del guardián; **cualquier** `spring.datasource` en el resto de `src/test` (incluido `config/application.properties`) se marca. | `grep-checks.ps1` (etapa 3) |
| 4 | Contenedor fijado y verificado | ID completo exacto, nombre, `running`, label `gesfut.purpose=isolated-tests`, imagen `mysql:8.0`, `RestartPolicy=no`, no `host`/privilegiado; `MYSQL_USER`/`MYSQL_DATABASE` esperados; `docker port <ID> 3306/tcp` = exactamente una línea `127.0.0.1:<p>`, p≠3306 y en rango; ningún otro puerto publicado; `Get-NetTCPConnection` en p: solo loopback y procesos de Docker, nunca `mysqld`; exactamente un montaje, `volume` local en `/var/lib/mysql`, sin binds/VolumesFrom, no bajo el datadir de MySQL80 (leído del servicio + `my.ini`, solo la línea `datadir`), volumen no compartido con otro contenedor. | `test-isolated.ps1` (etapa 4) |

### Menores

| Menor | Mecanismo | Archivo |
|---|---|---|
| Credencial nunca en línea de comandos ni logs | Se lee del `docker inspect` por ID a una variable y va **solo** a `$env:SPRING_DATASOURCE_PASSWORD` del proceso (`& mvnw.cmd`, sin `Start-Process`); se borra y se restaura el entorno en `finally`. Si apareciera en la salida de Maven, se borra el log y se avisa. Clave JWT: `test-isolated.ps1` genera en cada corrida un `SECRET_KEY` descartable (64 bytes aleatorios, Base64, RNG criptografico) que va solo al entorno del proceso Maven, pisa cualquier `SECRET_KEY` heredada (nunca se usa la real) y se restaura en `finally`; no se imprime ni se escribe (si apareciera en el log, se borra el log) y `test-listener-check.ps1` lo verifica con el AST del script. Tambien fija en la etapa 6 valores ficticios para los placeholders sin default (`FE_URL`, `EMAIL_PORT`, `EMAIL_USER`, `EMAIL_PASS`), pisando los heredados; el SMTP no tiene destino real. Los mensajes del guardián no incluyen URL, usuario recibido ni valores de parámetros. | `test-isolated.ps1`, `IsolatedDbRules` |
| `ErrorActionPreference Stop`, exit codes, restaurar entorno | `Stop` + `Set-StrictMode`; comandos nativos encapsulados (`Invoke-Native`) para que stderr no rompa en PS 5.1; `exit $code` tras `finally`. | `test-isolated.ps1`, `isolation-common.ps1` |
| Guardián y spring.factories solo en `src/test` | `check-packaging.ps1` con `jar tf` + lectura de `spring.factories`/`*.imports` de la app + búsqueda en `src/main`. | `check-packaging.ps1` |
| Flyway clean-disabled | Exigido en el entorno (`IsolatedDbGuard`) y en el bean (`isCleanDisabled()`); el script además setea `SPRING_FLYWAY_CLEAN_DISABLED=true`. | ambos |
| Pruebas negativas con mensaje del guardián, no "connection refused" | 8 corridas (incluye `jdbc:mysql://127.0.0.1:1/nonexistent`, que corta por puerto fuera de rango); exigen el marcador y prohíben `Communications link failure`, `Connection refused`, `Access denied`, `HikariPool-N - Start` (prueba de que no hubo intento de conexión). Sin contraseña en ninguna. Destinos ficticios `127.0.0.1:65001` salvo el caso 3306. | `negative-runs.ps1` |
| GRANT solo sobre `gesfut_isolated_test` | `SHOW GRANTS` dentro del contenedor (la contraseña no sale de él): solo `USAGE ON *.*` + privilegios sobre `` `gesfut_isolated_test`.* ``; sin FILE, SUPER, GRANT OPTION, roles ni otras bases. | `test-isolated.ps1` (etapa 5), `grant-check.sql` |

## Decisiones

- **Registro principal en `spring.factories`; `.imports` solo como respaldo.** En Spring Boot 3.3.4, `EnvironmentPostProcessor` y `ApplicationContextInitializer` **solo** se cargan desde `META-INF/spring.factories`; `AutoConfiguration.imports` es exclusivamente para autoconfiguraciones. Además, las autoconfiguraciones no se cargan en los slices (`@DataJpaTest`, `@JdbcTest`, …), que usan su propia lista, y pueden excluirse (`exclude`, `spring.autoconfigure.exclude`). El initializer de `spring.factories` corre en todo contexto Boot y agrega el BPP antes de que exista cualquier bean. La autoconfiguración de respaldo solo cubre el caso de que el initializer no se haya cargado (por ejemplo, un `spring.factories` mal fusionado); si ya está, no duplica.
- La autoconfiguración de respaldo vive bajo `com.gesfut`, pero el `AutoConfigurationExcludeFilter` de `@SpringBootApplication` la saca del component scan por estar listada en `.imports`.
- **Clave JNDI de Hikari:** se consulta `spring.datasource.hikari.data-source-jndi` (pedido de Código) y también `data-source-j-n-d-i`, que es la forma que Spring Boot publica en su metadata para `setDataSourceJNDI`. Para el `Binder` son el mismo nombre (compara sin guiones), pero se consultan las dos para no depender de eso. El test cubre ambas grafías. El resto de las claves se revisó: todas están en kebab-case correcto.
- **Validación antes y después de inicializar**: el binding de `spring.datasource.hikari.*` lo hace otro BPP; el control completo y obligatorio es en `postProcessAfterInitialization`, que igual ocurre antes del primer `getConnection()`. No se envuelve el DataSource (evita romper código que espera `HikariDataSource`).
- **`GESFUT_ISOLATED_PORT` obligatorio**: correr los tests de integración fuera del script (IDE, `mvnw test` directo) falla con el guardián. Es intencional.
- **Rechazo de `SPRING*` y `FLYWAY_*` completos** (más estricto que la lista pedida). `DB_*` se limpian en vez de rechazarse porque son las variables normales del back.
- **Datadir de MySQL80 no determinable** → advertencia y se continúa, porque ya está exigido que el montaje sea un volumen Docker local (ruta dentro de la VM de Docker, no del disco de Windows) y que no se comparta.
- `.mvn/extensions.xml` aborta (más estricto que lo pedido).
- Los `.ps1` están en **UTF-8 con BOM**: sin BOM, Windows PowerShell 5.1 los lee como ANSI y los acentos pueden romper el parseo. Al copiarlos, conservar la codificación.

## Limitaciones honestas

1. **Hasta aplicar `pc-changes.md` en la PC, la etapa 3 (grep) aborta**: `src/test/resources/config/application.properties` tiene `spring.datasource.*` (URL 3306) y `ResultIntegrityIntegrationTests` tiene la URL 3306 en `@SpringBootTest(properties=…)`. El patch de ese test lo pasa a datos propios TEST (`@Sql` seed + cleanup); **el seed se escribió a partir de las entidades del 7/11, no de V1/V2** (no están en el box): sus supuestos de esquema (columnas, ordinal de `role`, `BINARY(16)` de `code`, `password_changed_at`/`accepted_terms_at` NOT NULL de V2, otras columnas `NOT NULL` nuevas, FKs nuevas) están listados en `pc-changes.md` y hay que verificarlos en la PC. Si alguno no se cumple, el seed falla de forma visible al insertar; la limpieza nunca borra filas sin prefijo TEST.
2. **`application-local.properties` en la raíz del repo** hace abortar la etapa 2 (se importa con `optional:file:./application-local.properties`). Hay que correr desde un **worktree limpio** que no lo tenga; el script no lo renombra ni lo toca.
3. `grep-checks` es heurístico: no detecta código ofuscado, reflexión ni conexiones abiertas desde librerías de test. El BPP no ve conexiones abiertas con `DriverManager` directo en un test (por eso el grep).
4. Contextos de Spring **no Boot** (`@ContextConfiguration`/`@SpringJUnitConfig` puro) no cargan `spring.factories` de Boot: no tienen guardián. El grep los marca.
5. El guardián de entorno no ve `@DynamicPropertySource` (se agrega después); lo cubre el BPP. Si un test cambia el destino mutando un DataSource **después** de su inicialización, ningún control lo ve.
6. La API de Flyway se inspecciona por reflexión (`getConfiguration`, `getUrl`, `getUser`, `getDataSource`, `isCleanDisabled`): si una versión cambia esos nombres, falla cerrado (rechaza), no abierto.
7. `GRANT ALL ON gesfut_isolated_test.*`: en MySQL `_` es comodín en nombres de base de un GRANT; dentro del contenedor descartable no hay otras bases, así que no tiene efecto práctico.
8. Que los procesos de Docker que escuchan el puerto se llamen `com.docker.backend`/`wslrelay`/… depende de la versión de Docker Desktop; si es otro nombre, el script aborta (falla cerrado) y hay que revisar la lista.
9. Los tests de registro instancian, vía `SpringFactoriesLoader`, también los EPP/initializers de otras librerías que tengan constructor sin argumentos; las fallas de los que piden argumentos se ignoran a propósito (`FailureHandler`). No tienen efectos laterales conocidos, pero es una instanciación real.
10. Verificado en el box: **solo sintaxis Java** (parser `java-parser`, sin compilar: no hay JDK) y balance de llaves/paréntesis/strings de los `.ps1` (heurístico: no hay `pwsh`). No se compiló contra Spring/Hikari/JUnit, no se corrió ningún test ni script.
## Clases NO validadas por esta corrida (excepciones conocidas)

Decision de Jefe (10/11): 16 clases de integracion siguen con su propia URL jdbc 3306 de la base de regresion
(`gesfut_codex_regression_20261013`) y NO se editaron salvo agregarles `@Tag("requiere-base-regresion")`:
AddPlayerHttpTests, ArchivedReadOnlyTests, FlywayApagadoArranqueTests, FlywayMigrationTests, InitializeAndDniHttpTests,
MailReactivadoYResetTests, MailVerificacionDesactivadaTests, PasswordChangeSessionTests, PlayerEditIntegrationTests,
ResendVerificationTests, ResetRateLimitTests, ResetYChangePasswordEnBodyTests, TeamBatchAtomicityTests,
TokensOpacosTests, TournamentCheckTeamsTests y config/security/PublicAccessTests.

- **Esas 16 NO deben correrse contra la base aislada** ni se consideran validadas por la corrida de `test-isolated.ps1`.
  El script pasa `-DexcludedGroups=requiere-base-regresion`, asi que no corren; ademas el guardian las frenaria
  (puerto 3306 fuera de rango) si alguien las ejecutara por otra via con el entorno del script.
- La corrida aislada ejecuta: ResultIntegrityIntegrationTests, TestDatabaseGuardTests, GesfutApplicationTests (arranque de contexto
  sobre la base aislada vacia, con Flyway) y los unitarios sin Spring.
- `grep-checks.ps1` tiene la lista explicita y versionada (`$known`). Falla si: aparece una clase nueva con 3306/datasource que no
  esta en la lista; una clase de la lista no tiene el `@Tag`; una clase de la lista usa un patron distinto de los listados; o una
  clase de la lista ya no usa 3306 (migrada: hay que sacarla de la lista y quitarle el `@Tag`).
- Plan: migrarlas de a lotes de 3-4 a datos `@Sql` propios sobre la base aislada (un commit por lote); las dos de Flyway al final.

## Como correr los tests de integracion (leer antes de usar `mvn test` a mano)

- **La unica corrida valida de los tests que tocan la base es `scripts/aislamiento/test-isolated.ps1`.** Solo ese script verifica el contenedor, arma el entorno (puerto, credencial en el entorno del proceso) y pasa `-DexcludedGroups=requiere-base-regresion`.
- Un `mvn test` / `mvnw test` **a mano, sin el script**, no esta soportado: no existe la variable `GESFUT_ISOLATED_PORT`, y los tests de Spring dependen del guardian (`IsolatedDbGuard` + `IsolatedDbConnectionGuard`), que los corta con `GESFUT ISOLATION GUARD`. En particular, **las 16 clases con URL 3306 propia (taggeadas `requiere-base-regresion`) no excluyen nada por si solas**: sin el script se ejecutan y es el guardian el que las frena antes de crear el pool.
- Para correr solo los unitarios sin Spring ni base, pasar una lista explicita de clases en `-Dtest=` (y comprobar que no quede vacia: un `-Dtest` vacio ejecuta toda la suite).
- El `pom.xml` no se toca: no se configura `excludedGroups` ahi a proposito, para que la exclusion dependa del script verificado.
