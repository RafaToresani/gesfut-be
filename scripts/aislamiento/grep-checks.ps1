<#
.SYNOPSIS
  Busca en src/test usos que podrían abrir conexiones o redirigir la base por fuera del guardián.
  Falla (exit 1) si encuentra alguno no allowlisteado. Muestra archivo:línea:patrón, NUNCA el contenido
  de la línea (los .properties de test podrían tener credenciales).
#>
[CmdletBinding()]
param([Parameter(Mandatory)][string]$RepoRoot)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$testDir = Join-Path (Resolve-Path -LiteralPath $RepoRoot).Path 'src\test'
if (-not (Test-Path -LiteralPath $testDir -PathType Container)) { Write-Host "No existe $testDir"; exit 1 }

$patterns = @(
    @{ Id = 'DriverManager';            Regex = '\bDriverManager\b' },
    @{ Id = 'DriverManagerDataSource';  Regex = 'DriverManagerDataSource' },
    @{ Id = 'SimpleDriverDataSource';   Regex = 'SimpleDriverDataSource' },
    @{ Id = 'DataSourceBuilder';        Regex = 'DataSourceBuilder' },
    @{ Id = 'new-JdbcTemplate';         Regex = 'new\s+(NamedParameter)?JdbcTemplate\s*\(' },
    @{ Id = 'new-Hikari';               Regex = 'new\s+Hikari(DataSource|Config)\s*\(' },
    @{ Id = 'AutoConfigureTestDatabase'; Regex = '@AutoConfigureTestDatabase' },
    @{ Id = 'DynamicPropertySource';    Regex = '@DynamicPropertySource|DynamicPropertyRegistry' },
    @{ Id = 'TestPropertySource';       Regex = '@TestPropertySource' },
    @{ Id = 'ActiveProfiles';           Regex = '@ActiveProfiles' },
    @{ Id = 'ContextConfiguration';     Regex = '@ContextConfiguration|@SpringJUnitConfig' },
    @{ Id = 'ImportAutoConfiguration';  Regex = '@ImportAutoConfiguration' },
    @{ Id = 'Flyway-manual';            Regex = 'Flyway\s*\.\s*configure|FluentConfiguration' },
    @{ Id = 'spring-config-keys';       Regex = '(?i)spring[._-]?(datasource|flyway|profiles|config|sql[._-]init|r2dbc)' },
    @{ Id = 'jdbc-url';                 Regex = '(?i)jdbc:|mysqlx:' },
    @{ Id = 'System.setProperty';       Regex = 'System\s*\.\s*setProperty' }
)

# Allowlist: ruta relativa a src/test (con /) -> patrones permitidos ('*' = todos).
$allow = @{
    'java/com/gesfut/testsupport/IsolatedDbRules.java'                = @('*')
    'java/com/gesfut/testsupport/IsolatedDbGuard.java'                = @('*')
    'java/com/gesfut/testsupport/IsolatedDbConnectionGuard.java'      = @('*')
    'java/com/gesfut/testsupport/IsolatedDbGuardInitializer.java'     = @('*')
    'java/com/gesfut/testsupport/IsolatedDbRulesTest.java'            = @('*')
    'java/com/gesfut/testsupport/IsolatedDbGuardTest.java'            = @('*')
    'java/com/gesfut/testsupport/IsolatedDbConnectionGuardTest.java'  = @('*')
    'java/com/gesfut/testsupport/IsolatedDbGuardTestAutoConfiguration.java' = @('*')
    'java/com/gesfut/testsupport/IsolatedDbGuardRegistrationTest.java'      = @('*')
    'resources/META-INF/spring.factories'                             = @('*')
    'resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports' = @('*')
    # Sin excepciones para src/test/resources/config/application.properties ni para ningún otro archivo:
    # cualquier spring.datasource.* en src/test se marca (decisión de Jefe; la inyecta solo el script).
}

# Excepciones CONOCIDAS y versionadas (decision de Jefe, 10/11): 8 clases de integracion que todavia tienen su propia
# URL jdbc 3306 de la base de regresion. Estan marcadas con @Tag("requiere-base-regresion"), test-isolated.ps1 las EXCLUYE
# y NO se consideran validadas por la corrida aislada. Reglas de este chequeo:
#   - solo se toleran los patrones listados para ese archivo (un uso nuevo de otro tipo FALLA);
#   - el archivo debe tener el @Tag; si no lo tiene, FALLA;
#   - si el archivo ya no usa ninguno de los patrones (fue migrado), FALLA hasta sacarlo de esta lista;
#   - cualquier otra clase con 3306/datasource que no este aca FALLA.
# Para sacar una clase: migrarla a datos propios + base aislada, quitarle el @Tag y borrar su linea de esta lista.
$known = @{
    'java/com/gesfut/AddPlayerHttpTests.java' = @('jdbc-url','spring-config-keys')
    'java/com/gesfut/ArchivedReadOnlyTests.java' = @('jdbc-url','spring-config-keys')
    'java/com/gesfut/FlywayApagadoArranqueTests.java' = @('DriverManagerDataSource','Flyway-manual','jdbc-url','new-JdbcTemplate','spring-config-keys')
    'java/com/gesfut/FlywayMigrationTests.java' = @('DriverManagerDataSource','Flyway-manual','jdbc-url','new-JdbcTemplate','spring-config-keys')
    'java/com/gesfut/InitializeAndDniHttpTests.java' = @('jdbc-url','spring-config-keys')
    'java/com/gesfut/ResendVerificationTests.java' = @('jdbc-url','spring-config-keys')
    'java/com/gesfut/TournamentCheckTeamsTests.java' = @('jdbc-url','spring-config-keys')
    'java/com/gesfut/config/security/PublicAccessTests.java' = @('jdbc-url','spring-config-keys')
}
$knownTag = '@Tag("requiere-base-regresion")'
$knownHits = @{}

$extensions = @('.java', '.kt', '.groovy', '.properties', '.yml', '.yaml', '.xml', '.factories', '.imports', '.json', '.sql')
$violations = New-Object System.Collections.Generic.List[string]

foreach ($f in @(Get-ChildItem -LiteralPath $testDir -Recurse -File -Force)) {
    $rel = $f.FullName.Substring($testDir.Length).TrimStart('\', '/').Replace('\', '/')
    if ($f.Name -match '^application-local\.(properties|ya?ml)$') { $violations.Add("${rel}: application-local.* dentro de src/test"); continue }
    if ($extensions -notcontains $f.Extension.ToLowerInvariant()) { continue }
    $allowed = @()
    if ($allow.ContainsKey($rel)) { $allowed = $allow[$rel] }
    if ($allowed -contains '*') { continue }
    $isKnown = $known.ContainsKey($rel)
    if ($isKnown) {
        $allowed = @($known[$rel])
        $knownHits[$rel] = 0
        if (-not ([System.IO.File]::ReadAllText($f.FullName)).Contains($knownTag)) { $violations.Add("${rel}: excepcion conocida SIN $knownTag") }
    }
    $lineNo = 0
    foreach ($line in [System.IO.File]::ReadLines($f.FullName)) {
        $lineNo++
        foreach ($p in $patterns) {
            if ($line -match $p.Regex) {
                if ($allowed -contains $p.Id) { if ($isKnown) { $knownHits[$rel]++ }; continue }
                $violations.Add("${rel}:${lineNo}: $($p.Id)")
            }
        }
    }
}

foreach ($k in $known.Keys) {
    if (-not $knownHits.ContainsKey($k)) { $violations.Add("${k}: esta en la lista de excepciones pero el archivo no existe (sacarla de la lista)") }
    elseif ($knownHits[$k] -eq 0) { $violations.Add("${k}: ya no usa 3306/datasource: sacarla de la lista de excepciones y quitarle el @Tag") }
}

if ($violations.Count -gt 0) {
    Write-Host 'grep-checks: usos NO permitidos en src/test (archivo:línea:patrón):' -ForegroundColor Red
    foreach ($v in $violations) { Write-Host "  $v" }
    exit 1
}
Write-Host "grep-checks: OK (sin usos no permitidos en src/test; $($known.Count) excepciones conocidas con @Tag, NO validadas por la corrida aislada)"
exit 0
