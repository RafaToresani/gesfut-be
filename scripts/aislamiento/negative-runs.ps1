<#
.SYNOPSIS
  Plan (y ejecutor opcional) de 8 corridas NEGATIVAS: cada una tiene que FALLAR por el guardián
  ("GESFUT ISOLATION GUARD") o por el preflight del script ("GESFUT PREFLIGHT"), y NUNCA por
  "Communications link failure"/"Connection refused"/"Access denied" ni con el pool de Hikari arrancando.

.DESCRIPTION
  Sin -Run solo imprime el plan. Con -Run ejecuta las corridas (requiere el APTO de Código).
  Reglas de seguridad de las corridas:
    - NUNCA se pasa contraseña (SPRING_DATASOURCE_PASSWORD no se setea): aunque el guardián fallara,
      no hay credencial válida para ningún MySQL.
    - Los destinos son ficticios (127.0.0.1:65001, sin nadie escuchando; se verifica antes), salvo el caso
      3306, que existe justamente para demostrar que el guardián corta ANTES de crear el DataSource.
    - El fixture @DynamicPropertySource se copia temporalmente y se borra en finally.

.PARAMETER RepoRoot
  Raíz del repo/worktree (pom.xml + mvnw.cmd).

.PARAMETER Run
  Ejecuta las corridas. Sin este switch, solo muestra el plan.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [switch]$Run
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'isolation-common.ps1')

$FakePort = 65001
$ForbiddenOutput = 'Communications link failure|Connection refused|Access denied for user|HikariPool-\d+ - Start'
$FixtureSrc = Join-Path (Split-Path $PSScriptRoot -Parent) 'negative-fixtures\ExternalDynamicPropertySourceNegativeTest.java'
$FixtureRelDir = 'src\test\java\com\gesfut\testsupport\negative'

function New-TargetEnv([string]$Url, [string]$User, [int]$ExpectedPort) {
    return [ordered]@{
        SPRING_DATASOURCE_URL        = $Url
        SPRING_DATASOURCE_USERNAME   = $User
        FLYWAY_ENABLED               = 'true'
        SPRING_FLYWAY_CLEAN_DISABLED = 'true'
        GESFUT_ISOLATED_PORT         = "$ExpectedPort"
        MAVEN_SKIP_RC                = 'on'
    }
}
$okFakeUrl = "jdbc:mysql://127.0.0.1:$FakePort/gesfut_isolated_test"

$cases = @(
    [pscustomobject]@{ Id = 'N1-destino-3306'; Kind = 'mvn'
        Env = (New-TargetEnv 'jdbc:mysql://127.0.0.1:3306/gesfut_isolated_test' 'gesfut_test_only' 3306)
        MavenArgs = @(); TestClass = 'GesfutApplicationTests'; Fixture = $false; Expect = 'puerto 3306'
        Why = 'URL al MySQL80 nativo: el EnvironmentPostProcessor aborta antes de crear el DataSource (sin "HikariPool ... Start" en la salida).' },
    [pscustomobject]@{ Id = 'N2-usuario-distinto'; Kind = 'mvn'
        Env = (New-TargetEnv $okFakeUrl 'otro_usuario' $FakePort)
        MavenArgs = @(); TestClass = 'GesfutApplicationTests'; Fixture = $false; Expect = 'no usa exactamente el usuario'
        Why = 'Destino ficticio válido pero usuario distinto.' },
    [pscustomobject]@{ Id = 'N3-base-distinta'; Kind = 'mvn'
        Env = (New-TargetEnv "jdbc:mysql://127.0.0.1:$FakePort/otra_base" 'gesfut_test_only' $FakePort)
        MavenArgs = @(); TestClass = 'GesfutApplicationTests'; Fixture = $false; Expect = 'no usa exactamente la base'
        Why = 'Destino ficticio con otra base.' },
    [pscustomobject]@{ Id = 'N4-perfil-local-por-entorno'; Kind = 'script'
        Env = [ordered]@{ SPRING_PROFILES_ACTIVE = 'local' }
        Expect = 'SPRING_PROFILES_ACTIVE'
        Why = 'test-isolated.ps1 -DryRun tiene que abortar en la etapa 1 (exit 2) sin llegar a Maven.' },
    [pscustomobject]@{ Id = 'N5-perfil-local-por-D'; Kind = 'mvn'
        Env = (New-TargetEnv $okFakeUrl 'gesfut_test_only' $FakePort)
        MavenArgs = @('-Dspring.profiles.active=local'); TestClass = 'GesfutApplicationTests'; Fixture = $false; Expect = "perfil 'local'"
        Why = 'El perfil llega como -D (surefire lo pasa al fork); lo frena el guardián.' },
    [pscustomobject]@{ Id = 'N6-MAVEN_OPTS-con-D'; Kind = 'script'
        Env = [ordered]@{ MAVEN_OPTS = '-Dspring.datasource.url=jdbc:mysql://127.0.0.1:3306/GESFUT2' }
        Expect = 'MAVEN_OPTS'
        Why = 'test-isolated.ps1 -DryRun tiene que rechazar MAVEN_OPTS en la etapa 1 (exit 2).' },
    [pscustomobject]@{ Id = 'N8-puerto-1-base-inexistente'; Kind = 'mvn'
        Env = (New-TargetEnv 'jdbc:mysql://127.0.0.1:1/nonexistent' 'gesfut_test_only' 1)
        MavenArgs = @(); TestClass = 'GesfutApplicationTests'; Fixture = $false; Expect = 'fuera del rango publicado'
        Why = 'Destino ficticio 127.0.0.1:1/nonexistent: el guardián corta por puerto antes de crear el DataSource (nunca "Connection refused").' },
    [pscustomobject]@{ Id = 'N7-DynamicPropertySource-externo'; Kind = 'mvn'
        Env = (New-TargetEnv $okFakeUrl 'gesfut_test_only' $FakePort)
        MavenArgs = @(); TestClass = 'ExternalDynamicPropertySourceNegativeTest'; Fixture = $true; Expect = 'puerto 3306'
        Why = 'Un test externo apunta a 3306 con @DynamicPropertySource; lo frena el BeanPostProcessor antes de arrancar Hikari.' }
)

Write-Host '==== PLAN de corridas negativas ====' -ForegroundColor Cyan
foreach ($c in $cases) {
    Write-Host ("{0} [{1}] espera '{2}' + marcador del guardián/preflight. {3}" -f $c.Id, $c.Kind, $c.Expect, $c.Why)
}
Write-Host "Prohibido en la salida: $ForbiddenOutput"
if (-not $Run) {
    Write-Host 'Solo plan (sin -Run). No se ejecutó nada.'
    exit 0
}

# ------------------------------------------------------------------ ejecución (solo con -Run)
$results = New-Object System.Collections.Generic.List[string]
$failures = 0
$fixtureDst = $null
$createdFixtureDir = $false
$pushed = $false
try {
    $RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
    Assert-RepoRoot $RepoRoot
    Test-ProcessEnvironment
    $local = Find-ApplicationLocal $RepoRoot
    if ($local.Count -gt 0) { Stop-Preflight ('existe application-local.* (no se lee): ' + ($local -join ', ')) }
    if (Test-PortListening $FakePort) { Stop-Preflight "hay algo escuchando en $FakePort; elegí otro puerto ficticio" }
    if (-not (Test-Path -LiteralPath $FixtureSrc -PathType Leaf)) { Stop-Preflight "no está el fixture: $FixtureSrc" }

    $psExe = (Get-Process -Id $PID).Path
    Push-Location -LiteralPath $RepoRoot; $pushed = $true

    foreach ($c in $cases) {
        Write-Host "== $($c.Id) ==" -ForegroundColor Cyan
        $caseSaved = @{}
        foreach ($k in $c.Env.Keys) { $caseSaved[$k] = [Environment]::GetEnvironmentVariable($k, 'Process') }
        try {
            if ($c.Kind -eq 'mvn' -and $c.Fixture) {
                $dir = Join-Path $RepoRoot $FixtureRelDir
                if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null; $createdFixtureDir = $true }
                $fixtureDst = Join-Path $dir 'ExternalDynamicPropertySourceNegativeTest.java'
                if (Test-Path -LiteralPath $fixtureDst) { Stop-Preflight 'el fixture ya existe en el repo; no se pisa' }
                Copy-Item -LiteralPath $FixtureSrc -Destination $fixtureDst
            }
            foreach ($k in $c.Env.Keys) { [Environment]::SetEnvironmentVariable($k, $c.Env[$k], 'Process') }

            $startedAt = Get-Date
            $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
            try {
                if ($c.Kind -eq 'script') {
                    $out = & $psExe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'test-isolated.ps1') -RepoRoot $RepoRoot -DryRun 2>&1 | ForEach-Object { "$_" }
                } else {
                    $mvnArgs = @('-B', 'test', "-Dtest=$($c.TestClass)", '-Dsurefire.failIfNoSpecifiedTests=false') + $c.MavenArgs
                    $out = & (Join-Path $RepoRoot 'mvnw.cmd') @mvnArgs 2>&1 | ForEach-Object { "$_" }
                }
                $code = $LASTEXITCODE
            } finally { $ErrorActionPreference = $prev }
            $text = ($out -join "`n")
            if ($c.Kind -eq 'mvn') {
                # La consola de surefire puede recortar causas: se suman los reportes de ESTA corrida (no los viejos).
                $reports = Join-Path $RepoRoot 'target\surefire-reports'
                if (Test-Path -LiteralPath $reports) {
                    foreach ($rf in @(Get-ChildItem -LiteralPath $reports -File -Filter "*$($c.TestClass)*" | Where-Object { $_.LastWriteTime -ge $startedAt })) {
                        $text += "`n" + (Get-Content -LiteralPath $rf.FullName -Raw)
                    }
                }
            }

            $marker = if ($c.Kind -eq 'script') { $script:PreflightMarker } else { $script:GuardMarker }
            $ok = ($code -ne 0) -and $text.Contains($marker) -and $text.Contains($c.Expect) -and ($text -notmatch $ForbiddenOutput)
            if ($c.Kind -eq 'script' -and $code -ne 2) { $ok = $false }
            if ($ok) {
                $results.Add("OK     $($c.Id): exit=$code, frenado por $marker")
            } else {
                $failures++
                $why = @()
                if ($code -eq 0) { $why += 'exit 0 (no falló)' }
                if (-not $text.Contains($marker)) { $why += "sin '$marker'" }
                if (-not $text.Contains($c.Expect)) { $why += "sin '$($c.Expect)'" }
                if ($text -match $ForbiddenOutput) { $why += "INTENTÓ CONECTAR ($($Matches[0]))" }
                $results.Add("FALLA  $($c.Id): exit=$code; " + ($why -join '; '))
            }
        } finally {
            foreach ($k in $caseSaved.Keys) { [Environment]::SetEnvironmentVariable($k, $caseSaved[$k], 'Process') }
            if ($fixtureDst -and (Test-Path -LiteralPath $fixtureDst)) { Remove-Item -LiteralPath $fixtureDst -Force }
            $fixtureDst = $null
            if ($createdFixtureDir) {
                $dir = Join-Path $RepoRoot $FixtureRelDir
                if ((Test-Path -LiteralPath $dir) -and @(Get-ChildItem -LiteralPath $dir -Force).Count -eq 0) { Remove-Item -LiteralPath $dir -Force }
                $createdFixtureDir = $false
            }
        }
    }
} catch {
    $results.Add("ABORTADO: $($_.Exception.Message)")
    $failures++
} finally {
    Restore-ProcessEnv
    if ($pushed) { Pop-Location }
    Write-Host ''
    Write-Host '==== INFORME negative-runs ====' -ForegroundColor Cyan
    foreach ($r in $results) { Write-Host $r }
}
if ($failures -gt 0) { exit 1 }
exit 0
