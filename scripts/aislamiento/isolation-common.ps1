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

# Clave JWT DESCARTABLE para los tests (jwtService hace Decoders.BASE64.decode + Keys.hmacShaKeyFor: Base64, minimo 32 bytes;
# 64 bytes sirven para HS256/384/512). RNG criptografico, distinta en cada corrida. El llamador NUNCA debe imprimirla ni
# escribirla: solo Set-ProcessEnv y la comprobacion de fuga en el log (lo verifica test-listener-check.ps1).
function New-EphemeralJwtKey {
    $bytes = New-Object 'byte[]' 64
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
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

# Duenos aceptados cuando Windows informa el listener con State vacio/0 (Docker Desktop publica el puerto con un
# reenviador propio y Get-NetTCPConnection lo devuelve sin estado). Mas estricta que AllowedListenerProcesses.
$script:FallbackListenerProcesses = @('com.docker.backend', 'docker')

function Test-TcpConnect([int]$Port) {
    $c = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $c.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne(3000)) { return $false }
        $c.EndConnect($iar)
        return $c.Connected
    } catch {
        return $false
    } finally {
        $c.Close()
    }
}

function Get-ProcessNameById([int]$ProcessId) {
    $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($proc) { return [string]$proc.ProcessName }
    return "<pid $ProcessId>"
}

# Valida quien atiende el puerto publicado por el contenedor fijado por ID (el puerto sale de `docker port <ID>`,
# verificado antes en Test-Container). $Connections: salida de Get-NetTCPConnection -LocalPort <puerto> (sin filtrar por estado).
#  - Si hay entradas Listen, solo se miran esas (comportamiento original: loopback + proceso de Docker, nunca mysqld).
#  - Si NO hay entradas Listen, se aceptan las de State vacio/0 (rareza de Docker Desktop) solo si TODAS cumplen:
#    LocalAddress 127.0.0.1, dueno com.docker.backend o docker (nunca mysqld ni otro proceso) y una conexion TCP a
#    127.0.0.1:<puerto> realmente se establece.
# $GetProcessName y $CanConnect se pueden reemplazar en las pruebas (scripts\aislamiento\test-listener-check.ps1).
function Assert-PublishedPortListener {
    param(
        [Parameter(Mandatory)][int]$Port,
        [object[]]$Connections = @(),
        [scriptblock]$GetProcessName = { param($ProcessId) Get-ProcessNameById $ProcessId },
        [scriptblock]$CanConnect = { param($P) Test-TcpConnect $P }
    )
    $all = @($Connections | Where-Object { $null -ne $_ })
    $listen = @($all | Where-Object { "$($_.State)" -eq 'Listen' })
    $fallback = $false
    if ($listen.Count -gt 0) {
        $entries = $listen
    } else {
        # OwningProcess 0 (Idle) son restos de conexiones cerradas (TIME_WAIT) de corridas anteriores: un socket en escucha nunca lo tiene, se ignoran.
        $entries = @($all | Where-Object { ([string]::IsNullOrEmpty("$($_.State)") -or "$($_.State)" -eq '0') -and [int]$_.OwningProcess -ne 0 })
        $fallback = $true
    }
    if ($entries.Count -eq 0) { Stop-Preflight "nadie escucha en 127.0.0.1:$Port" }
    foreach ($l in $entries) {
        if ($fallback) {
            if ($l.LocalAddress -cne '127.0.0.1') { Stop-Preflight "el puerto $Port figura sin estado en $($l.LocalAddress) (solo se acepta 127.0.0.1)" }
        } elseif (@('127.0.0.1', '::1') -notcontains $l.LocalAddress) {
            Stop-Preflight "el puerto $Port escucha en $($l.LocalAddress) (no loopback)"
        }
        $pname = [string](& $GetProcessName $l.OwningProcess)
        if ($pname -match '(?i)^mysqld') { Stop-Preflight "el puerto $Port lo escucha mysqld (MySQL nativo)" }
        if ($fallback) {
            if (-not ($script:FallbackListenerProcesses -ccontains $pname)) { Stop-Preflight "el puerto $Port figura sin estado y lo atiende un proceso que no es com.docker.backend/docker: $pname" }
        } elseif (-not ($script:AllowedListenerProcesses -ccontains $pname)) {
            Stop-Preflight "el puerto $Port lo escucha un proceso no reconocido como Docker: $pname"
        }
    }
    if ($fallback) {
        if (-not (& $CanConnect $Port)) { Stop-Preflight "el puerto $Port figura sin estado y no acepta conexiones TCP en 127.0.0.1" }
    }
}
# Objeto de un GRANT de nivel base de datos tal como lo imprime `mysql -N -B` (modo batch): el backslash se duplica,
# asi que el grant real `gesfut\_isolated\_test`.* (guion bajo escapado = literal) sale como `gesfut\\_isolated\\_test`.*
# Se aceptan SOLO estas tres escrituras de la base aislada; cualquier otra base u objeto aborta.
function Test-GrantObjectIsIsolatedDb([string]$Obj) {
    $bt = [string][char]96
    $accepted = @(
        ($bt + 'gesfut_isolated_test' + $bt + '.*'),
        ($bt + 'gesfut\_isolated\_test' + $bt + '.*'),
        ($bt + 'gesfut\\_isolated\\_test' + $bt + '.*')
    )
    foreach ($a in $accepted) { if ($Obj -ceq $a) { return $true } }
    return $false
}
function Test-PortListening([int]$Port) {
    $l = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    return ($l.Count -gt 0)
}
