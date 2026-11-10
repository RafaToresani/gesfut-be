# isolation-common.ps1
# Funciones y listas compartidas por test-isolated.ps1 y negative-runs.ps1 (se carga con dot-source).
# Compatible con Windows PowerShell 5.1 y PowerShell 7. No imprime credenciales ni contenidos de archivos.

Set-StrictMode -Version Latest

# ---------------- Invariantes fijos ----------------
$script:ContainerId    = '39f7d94396ef603f251a946a0ca51dbd160e32a63aef8c7d8618533218645f38'
$script:ContainerName  = 'gesfut-test-isolated-20261108174332'
$script:ExpectedImage  = 'mysql:8.0'
$script:LabelKey       = 'gesfut.purpose'
$script:LabelValue     = 'isolated-tests'
$script:DbName         = 'gesfut_isolated_test'
$script:DbUser         = 'gesfut_test_only'
$script:MinPort        = 49152
$script:MaxPort        = 65535
$script:GuardMarker    = 'GESFUT ISOLATION GUARD'
$script:PreflightMarker = 'GESFUT PREFLIGHT'
$script:AllowedListenerProcesses = @('com.docker.backend', 'com.docker.proxy', 'wslrelay', 'vpnkit', 'docker-proxy', 'dockerd')

# Variables que, si están presentes, ABORTAN (indican configuración que podría redirigir los tests).
$script:RejectEnvExact = @(
    'SPRING_APPLICATION_JSON', 'SPRING_PROFILES_ACTIVE', 'SPRING_PROFILES_INCLUDE',
    'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT', 'SPRING_CONFIG_NAME',
    'JAVA_TOOL_OPTIONS', 'MAVEN_OPTS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'MAVEN_ARGS',
    'MAVEN_CONFIG', 'MAVEN_DEBUG_OPTS', 'GESFUT_ISOLATED_PORT', 'MYSQL_PWD'
)
# Prefijos que ABORTAN (case-insensitive; cubre SPRING_DATASOURCE_*, SPRING_FLYWAY_*, spring.* como nombre de variable).
$script:RejectEnvPrefixes = @('SPRING', 'FLYWAY_')
# Variables normales del back (las usa application.properties): se LIMPIAN para el proceso y se restauran al final.
$script:CleanEnv = @('DB_NAME', 'DB_USERNAME', 'DB_PASSWORD', 'FLYWAY_ENABLED')

# Ubicaciones donde se DETECTA (sin leer) application-local.*
$script:AppLocalDirs = @('.', 'config', 'src\main\resources', 'src\main\resources\config',
    'src\test\resources', 'src\test\resources\config', 'target\classes', 'target\classes\config',
    'target\test-classes', 'target\test-classes\config')
$script:AppLocalNames = @('application-local.properties', 'application-local.yml', 'application-local.yaml')

# ---------------- Utilidades ----------------
$script:SavedEnv = @{}

function Stop-Preflight([string]$Message) {
    throw "$($script:PreflightMarker): $Message"
}

function Set-ProcessEnv([string]$Name, $Value) {
    if (-not $script:SavedEnv.ContainsKey($Name)) {
        $script:SavedEnv[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Restore-ProcessEnv {
    foreach ($k in @($script:SavedEnv.Keys)) {
        [Environment]::SetEnvironmentVariable($k, $script:SavedEnv[$k], 'Process')
    }
    $script:SavedEnv = @{}
}

# Ejecuta un comando nativo sin que stderr dispare errores en PS 5.1; devuelve objeto con stdout/stderr/exit.
# Nunca loguea los argumentos (no deben contener secretos de todos modos).
function Invoke-Native {
    # Nota: $InputText va sin tipo a propósito: [string] convertiría $null en '' y siempre habría stdin.
    param([Parameter(Mandatory)][string]$Exe, [string[]]$Arguments = @(), $InputText = $null)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($null -ne $InputText) {
            $all = $InputText | & $Exe @Arguments 2>&1
        } else {
            $all = & $Exe @Arguments 2>&1
        }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
    $out = @(); $err = @()
    foreach ($line in @($all)) {
        if ($line -is [System.Management.Automation.ErrorRecord]) { $err += $line.ToString() } else { $out += [string]$line }
    }
    return [pscustomobject]@{ ExitCode = $code; StdOut = $out; StdErr = $err }
}

function Assert-RepoRoot([string]$RepoRoot) {
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot 'pom.xml') -PathType Leaf)) { Stop-Preflight "RepoRoot sin pom.xml: $RepoRoot" }
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot 'mvnw.cmd') -PathType Leaf)) { Stop-Preflight "RepoRoot sin mvnw.cmd: $RepoRoot" }
}

# Revisa variables de entorno: aborta con las peligrosas (solo muestra NOMBRES) y limpia las normales.
function Test-ProcessEnvironment {
    $names = @(Get-ChildItem Env: | ForEach-Object { $_.Name })
    $bad = New-Object System.Collections.Generic.List[string]
    foreach ($n in $names) {
        if ($script:CleanEnv -contains $n) { continue }
        if ($script:RejectEnvExact -contains $n) { $bad.Add($n); continue }
        foreach ($p in $script:RejectEnvPrefixes) {
            if ($n -like "$p*") { $bad.Add($n); break }
        }
    }
    if ($bad.Count -gt 0) {
        Stop-Preflight ("variables de entorno no permitidas presentes (abrí una consola limpia o quitalas): " + (($bad | Sort-Object -Unique) -join ', '))
    }
    foreach ($n in $script:CleanEnv) { Set-ProcessEnv $n $null }
}

# Detecta application-local.* SIN leerlos. Devuelve rutas relativas encontradas.
function Find-ApplicationLocal([string]$RepoRoot) {
    $found = New-Object System.Collections.Generic.List[string]
    $dirs = New-Object System.Collections.Generic.List[string]
    foreach ($d in $script:AppLocalDirs) { $dirs.Add($d) }
    # Spring Boot también busca en ./config/*/
    $cfg = Join-Path $RepoRoot 'config'
    if (Test-Path -LiteralPath $cfg -PathType Container) {
        foreach ($sub in @(Get-ChildItem -LiteralPath $cfg -Directory -Force -ErrorAction SilentlyContinue)) { $dirs.Add("config\$($sub.Name)") }
    }
    foreach ($d in $dirs) {
        foreach ($f in $script:AppLocalNames) {
            $rel = Join-Path $d $f
            if (Test-Path -LiteralPath (Join-Path $RepoRoot $rel) -PathType Leaf) { $found.Add($rel) }
        }
    }
    return ,$found
}

function Test-PortListening([int]$Port) {
    $l = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    return ($l.Count -gt 0)
}
