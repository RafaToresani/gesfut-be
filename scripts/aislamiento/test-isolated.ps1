<#
.SYNOPSIS
  Corre los tests del back de Gesfut SOLO contra el MySQL descartable (contenedor fijo), nunca contra MySQL80/GESFUT2.

.DESCRIPTION
  Etapas (el informe final dice en cuál se frenó):
    1 entorno      - aborta con variables peligrosas (SPRING*, FLYWAY_*, MAVEN_OPTS, JAVA_TOOL_OPTIONS, ...);
                     limpia DB_NAME/DB_USERNAME/DB_PASSWORD/FLYWAY_ENABLED (se restauran al final).
    2 archivos     - .mvn/jvm.config, .mvn/maven.config, .mvn/extensions.xml, mavenrc, ~/.m2/settings.xml,
                     devtools globales, surefire/failsafe en pom.xml, application-local.* (solo existencia).
    3 grep         - grep-checks.ps1 sobre src/test.
    4 contenedor   - ID fijo, estado, label, imagen, restart, puerto, listener, volumen, MySQL80.
    5 permisos     - SHOW GRANTS dentro del contenedor (credencial que nunca sale del contenedor).
    6 maven        - mvnw -B test con la credencial SOLO como variable de entorno del proceso.
  Nada de esto ejecuta Spring/Flyway hasta la etapa 6, y con -DryRun la etapa 6 no corre.

.PARAMETER RepoRoot
  Raíz del repo (donde están pom.xml y mvnw.cmd). Obligatorio. Recomendado: un worktree limpio
  (sin application-local.properties en la raíz, que aborta la etapa 2).

.PARAMETER DryRun
  Hace todos los chequeos (1-5) y no corre Maven.

.PARAMETER TestFilter
  Opcional: valor para -Dtest (solo [A-Za-z0-9_.*#,+-]).

.NOTES
  Códigos de salida: 0 OK; 2 abortado en chequeos previos; otro = código de Maven.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [switch]$DryRun,
    [string]$TestFilter
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'isolation-common.ps1')

$script:Stage = 'inicio'
$script:Report = New-Object System.Collections.Generic.List[string]
function Enter-Stage([string]$Name) {
    $script:Stage = $Name
    Write-Host "== ETAPA $Name ==" -ForegroundColor Cyan
}
function Complete-Stage([string]$Detail) {
    $script:Report.Add(("OK      {0} {1}" -f $script:Stage, $Detail))
}

# ---------------------------------------------------------------------------------------------
# Etapa 2: archivos que pueden inyectar configuración
# ---------------------------------------------------------------------------------------------
function Test-SettingsXml([string]$Path, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
    try { [xml]$x = Get-Content -LiteralPath $Path -Raw } catch { Stop-Preflight "$Label no se pudo parsear como XML (no se muestra el contenido)" }
    if (@($x.SelectNodes("//*[local-name()='properties']")).Count -gt 0) { Stop-Preflight "$Label define <properties>" }
    if (@($x.SelectNodes("//*[local-name()='activeProfile']")).Count -gt 0) { Stop-Preflight "$Label tiene <activeProfiles>" }
    if (@($x.SelectNodes("//*[local-name()='profile']/*[local-name()='activation']")).Count -gt 0) { Stop-Preflight "$Label tiene perfiles con <activation>" }
}

function Test-PomSurefire([string]$PomPath) {
    try { [xml]$pom = Get-Content -LiteralPath $PomPath -Raw } catch { Stop-Preflight 'pom.xml no se pudo parsear' }
    $risky = '(?i)datasource|flyway|profiles|spring\.config|spring_config|SPRING_|DB_NAME|DB_USERNAME|DB_PASSWORD|jdbc:|GESFUT_ISOLATED'
    $plugins = @($pom.SelectNodes("//*[local-name()='plugin'][*[local-name()='artifactId' and (normalize-space(.)='maven-surefire-plugin' or normalize-space(.)='maven-failsafe-plugin')]]"))
    foreach ($p in $plugins) {
        foreach ($tag in @('argLine', 'systemPropertyVariables', 'systemProperties', 'environmentVariables')) {
            foreach ($n in @($p.SelectNodes(".//*[local-name()='$tag']"))) {
                if ($n.OuterXml -match $risky) { Stop-Preflight "pom.xml: surefire/failsafe <$tag> toca datasource/flyway/profiles/config" }
            }
        }
        if (@($p.SelectNodes(".//*[local-name()='systemPropertiesFile']")).Count -gt 0) { Stop-Preflight 'pom.xml: surefire/failsafe usa <systemPropertiesFile>' }
    }
    # argLine también puede venir de <properties><argLine> (global o en perfiles)
    foreach ($n in @($pom.SelectNodes("//*[local-name()='properties']/*[local-name()='argLine']"))) {
        if ($n.OuterXml -match $risky) { Stop-Preflight 'pom.xml: <properties><argLine> toca datasource/flyway/profiles/config' }
    }
}

function Test-InjectionFiles([string]$RepoRoot) {
    $mvnDir = Join-Path $RepoRoot '.mvn'
    if (Test-Path -LiteralPath (Join-Path $mvnDir 'jvm.config') -PathType Leaf) { Stop-Preflight '.mvn/jvm.config existe' }
    $mc = Join-Path $mvnDir 'maven.config'
    if (Test-Path -LiteralPath $mc -PathType Leaf) {
        if ((Get-Content -LiteralPath $mc -Raw) -match '\S') { Stop-Preflight '.mvn/maven.config tiene contenido' }
    }
    if (Test-Path -LiteralPath (Join-Path $mvnDir 'extensions.xml') -PathType Leaf) { Stop-Preflight '.mvn/extensions.xml existe (extensiones de Maven no revisadas)' }

    $homeDir = [Environment]::GetFolderPath('UserProfile')
    foreach ($rc in @('mavenrc_pre.bat', 'mavenrc_pre.cmd', 'mavenrc_post.bat', 'mavenrc_post.cmd', '.mavenrc')) {
        if (Test-Path -LiteralPath (Join-Path $homeDir $rc) -PathType Leaf) { Stop-Preflight "existe ~/$rc" }
    }
    Test-SettingsXml (Join-Path $homeDir '.m2\settings.xml') '~/.m2/settings.xml'
    $dists = Join-Path $homeDir '.m2\wrapper\dists'
    if (Test-Path -LiteralPath $dists -PathType Container) {
        foreach ($s in @(Get-ChildItem -LiteralPath $dists -Recurse -Filter 'settings.xml' -File -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match '\\conf\\settings\.xml$' })) {
            Test-SettingsXml $s.FullName 'settings.xml global del Maven del wrapper'
        }
    }
    foreach ($dt in @('.spring-boot-devtools.properties', '.spring-boot-devtools.yml', '.spring-boot-devtools.yaml')) {
        if (Test-Path -LiteralPath (Join-Path $homeDir $dt) -PathType Leaf) { Stop-Preflight "existe ~/$dt" }
    }
    $sbCfg = Join-Path $homeDir '.config\spring-boot'
    if (Test-Path -LiteralPath $sbCfg -PathType Container) {
        if (@(Get-ChildItem -LiteralPath $sbCfg -Recurse -File -Force -ErrorAction SilentlyContinue).Count -gt 0) { Stop-Preflight 'existe ~/.config/spring-boot/ con archivos' }
    }
    Test-PomSurefire (Join-Path $RepoRoot 'pom.xml')

    $local = Find-ApplicationLocal $RepoRoot
    if ($local.Count -gt 0) {
        Stop-Preflight ('existe application-local.* (no se lee): ' + ($local -join ', ') + '. Usá un worktree limpio.')
    }
}

# ---------------------------------------------------------------------------------------------
# Etapa 4: contenedor
# ---------------------------------------------------------------------------------------------
function Get-MySql80DataDir {
    $svc = Get-ItemProperty -LiteralPath 'HKLM:\SYSTEM\CurrentControlSet\Services\MySQL80' -ErrorAction SilentlyContinue
    if ($null -eq $svc -or -not ($svc.PSObject.Properties.Name -contains 'ImagePath')) { return $null }
    $m = [regex]::Match([string]$svc.ImagePath, '--defaults-file="?([^"]+?)"?(\s|$)')
    if (-not $m.Success) { return $null }
    $ini = $m.Groups[1].Value
    if (-not (Test-Path -LiteralPath $ini -PathType Leaf)) { return $null }
    # Solo se extrae la línea datadir; no se imprime nada del archivo.
    foreach ($line in [System.IO.File]::ReadLines($ini)) {
        $d = [regex]::Match($line, '^\s*datadir\s*=\s*"?([^"]+?)"?\s*$', 'IgnoreCase')
        if ($d.Success) { return $d.Groups[1].Value.Replace('/', '\').TrimEnd('\') }
    }
    return $null
}

function Test-Container {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Stop-Preflight 'no se encontró el CLI de docker' }
    $r = Invoke-Native docker @('inspect', '--type', 'container', $script:ContainerId)
    if ($r.ExitCode -ne 0) { Stop-Preflight 'docker inspect falló para el ID fijo del contenedor' }
    # El JSON contiene Env (con la credencial): se mantiene en memoria y NUNCA se imprime.
    $c = @(($r.StdOut -join "`n") | ConvertFrom-Json)[0]

    if ($c.Id -cne $script:ContainerId) { Stop-Preflight 'el ID inspeccionado no coincide exactamente con el fijado' }
    if ($c.Name -cne "/$($script:ContainerName)") { Stop-Preflight 'el nombre del contenedor no coincide' }
    if ($c.State.Status -cne 'running') { Stop-Preflight "el contenedor no está running (estado: $($c.State.Status))" }
    if ($c.Config.Image -cne $script:ExpectedImage) { Stop-Preflight "imagen distinta de $($script:ExpectedImage)" }
    $labels = $c.Config.Labels
    if ($null -eq $labels -or -not ($labels.PSObject.Properties.Name -contains $script:LabelKey) -or $labels.($script:LabelKey) -cne $script:LabelValue) {
        Stop-Preflight "falta el label $($script:LabelKey)=$($script:LabelValue)"
    }
    if ($c.HostConfig.RestartPolicy.Name -cne 'no') { Stop-Preflight 'RestartPolicy distinta de "no"' }
    if ($c.HostConfig.NetworkMode -eq 'host') { Stop-Preflight 'NetworkMode host no permitido' }
    if ($c.HostConfig.Privileged) { Stop-Preflight 'contenedor privilegiado no permitido' }
    if (@($c.HostConfig.Binds | Where-Object { $_ }).Count -gt 0) { Stop-Preflight 'el contenedor tiene bind mounts' }
    if (@($c.HostConfig.VolumesFrom | Where-Object { $_ }).Count -gt 0) { Stop-Preflight 'el contenedor usa VolumesFrom' }

    # Env del contenedor: usuario/base esperados y presencia de la contraseña (sin mostrarla).
    $envList = @($c.Config.Env)
    if (-not ($envList -ccontains "MYSQL_USER=$($script:DbUser)")) { Stop-Preflight 'MYSQL_USER del contenedor no es el esperado' }
    if (-not ($envList -ccontains "MYSQL_DATABASE=$($script:DbName)")) { Stop-Preflight 'MYSQL_DATABASE del contenedor no es la esperada' }
    $pwLine = @($envList | Where-Object { $_.StartsWith('MYSQL_PASSWORD=', [StringComparison]::Ordinal) })
    if ($pwLine.Count -ne 1 -or $pwLine[0].Length -le 'MYSQL_PASSWORD='.Length) { Stop-Preflight 'MYSQL_PASSWORD ausente o vacía en el contenedor' }

    # Montajes: exactamente un volumen en /var/lib/mysql
    $mounts = @($c.Mounts)
    if ($mounts.Count -ne 1) { Stop-Preflight "se esperaba exactamente 1 montaje y hay $($mounts.Count)" }
    $m = $mounts[0]
    if ($m.Type -cne 'volume') { Stop-Preflight "el montaje no es de tipo volume ($($m.Type))" }
    if ($m.Destination -cne '/var/lib/mysql') { Stop-Preflight 'el montaje no está en /var/lib/mysql' }
    if ($m.Driver -cne 'local') { Stop-Preflight 'el volumen no usa el driver local' }
    if ([string]::IsNullOrEmpty($m.Name)) { Stop-Preflight 'volumen sin nombre' }
    $datadir = Get-MySql80DataDir
    if ($null -eq $datadir) {
        Write-Warning 'No se pudo determinar el datadir de MySQL80; queda verificado que el montaje es un volumen Docker (no bind).'
    } else {
        $src = ([string]$m.Source).Replace('/', '\').TrimEnd('\')
        if ($src.StartsWith($datadir, [StringComparison]::OrdinalIgnoreCase)) { Stop-Preflight 'el volumen está bajo el datadir de MySQL80' }
    }
    $users = Invoke-Native docker @('ps', '-a', '--no-trunc', '--filter', "volume=$($m.Name)", '--format', '{{.ID}}')
    if ($users.ExitCode -ne 0) { Stop-Preflight 'docker ps --filter volume falló' }
    $ids = @($users.StdOut | Where-Object { $_ -match '\S' } | ForEach-Object { $_.Trim() })
    if ($ids.Count -ne 1 -or $ids[0] -cne $script:ContainerId) { Stop-Preflight 'el volumen de datos está compartido con otro contenedor' }

    # Puertos: solo 3306/tcp publicado, y en 127.0.0.1
    if ($null -eq $c.NetworkSettings.Ports) { Stop-Preflight 'el contenedor no informa puertos' }
    foreach ($prop in @($c.NetworkSettings.Ports.PSObject.Properties)) {
        if ($prop.Name -ne '3306/tcp' -and $null -ne $prop.Value -and @($prop.Value).Count -gt 0) {
            Stop-Preflight "hay otro puerto publicado ($($prop.Name))"
        }
    }
    $pr = Invoke-Native docker @('port', $script:ContainerId, '3306/tcp')
    if ($pr.ExitCode -ne 0) { Stop-Preflight 'docker port falló' }
    $lines = @($pr.StdOut | Where-Object { $_ -match '\S' } | ForEach-Object { $_.Trim() })
    if ($lines.Count -ne 1) { Stop-Preflight "docker port devolvió $($lines.Count) líneas; se espera exactamente 1 (127.0.0.1:<p>)" }
    $pm = [regex]::Match($lines[0], '^127\.0\.0\.1:(\d{1,5})$')
    if (-not $pm.Success) { Stop-Preflight 'el puerto no está publicado exactamente en 127.0.0.1' }
    $port = [int]$pm.Groups[1].Value
    if ($port -eq 3306) { Stop-Preflight 'el puerto publicado es 3306' }
    if ($port -lt $script:MinPort -or $port -gt $script:MaxPort) { Stop-Preflight "puerto $port fuera de $($script:MinPort)-$($script:MaxPort)" }

    # Quién escucha en ese puerto: solo procesos de Docker, solo loopback, nunca mysqld.
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
    if ($listeners.Count -eq 0) { Stop-Preflight "nadie escucha en 127.0.0.1:$port" }
    foreach ($l in $listeners) {
        if (@('127.0.0.1', '::1') -notcontains $l.LocalAddress) { Stop-Preflight "el puerto $port escucha en $($l.LocalAddress) (no loopback)" }
        $proc = Get-Process -Id $l.OwningProcess -ErrorAction SilentlyContinue
        $pname = if ($proc) { $proc.ProcessName } else { "<pid $($l.OwningProcess)>" }
        if ($pname -match '(?i)^mysqld') { Stop-Preflight "el puerto $port lo escucha mysqld (MySQL nativo)" }
        if ($script:AllowedListenerProcesses -notcontains $pname) { Stop-Preflight "el puerto $port lo escucha un proceso no reconocido como Docker: $pname" }
    }
    return $port
}

# ---------------------------------------------------------------------------------------------
# Etapa 5: permisos. La credencial no sale del contenedor: el sh interno usa su propio MYSQL_PASSWORD
# como MYSQL_PWD. El SQL entra por stdin (sin comillas dobles en argumentos, compatible PS 5.1).
# ---------------------------------------------------------------------------------------------
function Test-Grants {
    $sql = "SHOW GRANTS FOR 'gesfut_test_only'@'%';"
    $r = Invoke-Native docker @('exec', '-i', $script:ContainerId, 'sh', '-c', 'MYSQL_PWD=$MYSQL_PASSWORD exec mysql -ugesfut_test_only -N -B') -InputText $sql
    if ($r.ExitCode -ne 0) { Stop-Preflight 'no se pudieron leer los GRANTs dentro del contenedor' }
    $grants = @($r.StdOut | Where-Object { $_ -match '\S' } | ForEach-Object { $_.Trim() })
    if ($grants.Count -eq 0) { Stop-Preflight 'SHOW GRANTS no devolvió filas' }
    $onDb = $false
    foreach ($g in $grants) {
        if ($g -match '(?i)WITH GRANT OPTION') { Stop-Preflight 'el usuario tiene GRANT OPTION' }
        $gm = [regex]::Match($g, '^GRANT (.+?) ON (\S+) TO ')
        if (-not $gm.Success) { Stop-Preflight 'hay un GRANT que no es de privilegios sobre objetos (rol/proxy)' }
        $privs = $gm.Groups[1].Value; $obj = $gm.Groups[2].Value
        if ($privs -match '(?i)\bFILE\b|\bSUPER\b') { Stop-Preflight 'el usuario tiene FILE o SUPER' }
        if ($obj -ceq '*.*') {
            if ($privs -cne 'USAGE') { Stop-Preflight 'el usuario tiene privilegios globales (*.*) además de USAGE' }
        } elseif ($obj -ceq '`gesfut_isolated_test`.*' -or $obj -ceq '`gesfut\_isolated\_test`.*') {
            $onDb = $true
        } else {
            Stop-Preflight 'el usuario tiene privilegios sobre otra base u objeto'
        }
    }
    if (-not $onDb) { Stop-Preflight 'el usuario no tiene privilegios sobre gesfut_isolated_test' }
}

# ---------------------------------------------------------------------------------------------
# Principal
# ---------------------------------------------------------------------------------------------
$exitCode = 0
$password = $null
$logFile = $null
$pushed = $false
try {
    Enter-Stage '0-repo'
    $RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
    Assert-RepoRoot $RepoRoot
    if ($TestFilter -and $TestFilter -notmatch '^[A-Za-z0-9_.*#,+-]+$') { Stop-Preflight 'TestFilter con caracteres no permitidos' }
    Complete-Stage $RepoRoot

    Enter-Stage '1-entorno'
    Test-ProcessEnvironment
    Complete-Stage 'sin variables peligrosas; DB_* limpiadas para este proceso'

    Enter-Stage '2-archivos'
    Test-InjectionFiles $RepoRoot
    Complete-Stage 'sin archivos de inyección ni application-local.*'

    Enter-Stage '3-grep'
    & (Join-Path $PSScriptRoot 'grep-checks.ps1') -RepoRoot $RepoRoot
    if ($LASTEXITCODE -ne 0) { Stop-Preflight 'grep-checks.ps1 encontró usos no permitidos en src/test' }
    Complete-Stage 'src/test limpio'

    Enter-Stage '4-contenedor'
    $port = Test-Container
    Complete-Stage "contenedor verificado, 127.0.0.1:$port"

    Enter-Stage '5-permisos'
    Test-Grants
    Complete-Stage "solo privilegios sobre $($script:DbName).*"

    if ($DryRun) {
        $script:Report.Add('SALTEADA 6-maven (-DryRun)')
    } else {
        Enter-Stage '6-maven'
        # Credencial: se lee del env del contenedor a una variable y va SOLO al entorno del proceso mvn.
        $r = Invoke-Native docker @('inspect', '--type', 'container', $script:ContainerId)
        if ($r.ExitCode -ne 0) { Stop-Preflight 'docker inspect falló al leer la credencial' }
        $c = @(($r.StdOut -join "`n") | ConvertFrom-Json)[0]
        $pwLine = @(@($c.Config.Env) | Where-Object { $_.StartsWith('MYSQL_PASSWORD=', [StringComparison]::Ordinal) })[0]
        $password = $pwLine.Substring('MYSQL_PASSWORD='.Length)
        $c = $null; $r = $null; $pwLine = $null

        Set-ProcessEnv 'SPRING_DATASOURCE_URL' "jdbc:mysql://127.0.0.1:$port/$($script:DbName)"
        Set-ProcessEnv 'SPRING_DATASOURCE_USERNAME' $script:DbUser
        Set-ProcessEnv 'SPRING_DATASOURCE_PASSWORD' $password
        Set-ProcessEnv 'FLYWAY_ENABLED' 'true'
        Set-ProcessEnv 'SPRING_FLYWAY_CLEAN_DISABLED' 'true'
        Set-ProcessEnv 'GESFUT_ISOLATED_PORT' "$port"
        Set-ProcessEnv 'MAVEN_SKIP_RC' 'on'

        # Las clases con base de regresion propia (@Tag requiere-base-regresion) NUNCA corren aca: no estan validadas por esta corrida.
        $mvnArgs = @('-B', 'test', '-DexcludedGroups=requiere-base-regresion')
        if ($TestFilter) { $mvnArgs += @("-Dtest=$TestFilter", '-Dsurefire.failIfNoSpecifiedTests=false') }
        $logFile = Join-Path ([IO.Path]::GetTempPath()) ("gesfut-isolated-{0:yyyyMMdd-HHmmss}.log" -f (Get-Date))

        Push-Location -LiteralPath $RepoRoot; $pushed = $true
        $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
        try {
            & (Join-Path $RepoRoot 'mvnw.cmd') @mvnArgs 2>&1 | ForEach-Object { "$_" } | Tee-Object -FilePath $logFile
            $mvnCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = $prev }

        $log = Get-Content -LiteralPath $logFile -Raw
        if ($log -and $log.Contains($password)) {
            Remove-Item -LiteralPath $logFile -Force
            $logFile = $null
            Write-Warning 'La credencial apareció en la salida de Maven: se borró el log. Revisar qué la imprimió.'
        }
        if ($log -match [regex]::Escape($script:GuardMarker)) {
            $script:Report.Add('FRENADO 6-maven: el guardián de aislamiento abortó el contexto (ver mensaje GESFUT ISOLATION GUARD)')
        }
        if ($log -match 'Communications link failure|Connection refused') {
            $script:Report.Add('ATENCION 6-maven: hubo un error de conexión (no es el guardián)')
        }
        $summary = @([regex]::Matches([string]$log, 'Tests run: \d+, Failures: \d+, Errors: \d+, Skipped: \d+(?![\d,])') | ForEach-Object { $_.Value })
        if ($summary.Count -gt 0) { $script:Report.Add("resumen 6-maven: $($summary[-1])") }
        if ($mvnCode -eq 0) { Complete-Stage 'mvnw -B test OK' } else { $script:Report.Add("FALLO   6-maven exit=$mvnCode") }
        $exitCode = $mvnCode
    }
} catch {
    $msg = $_.Exception.Message
    if ($msg -like "$($script:PreflightMarker)*") {
        $script:Report.Add("ABORTADO $($script:Stage): $msg")
        $exitCode = 2
    } else {
        # Error inesperado: solo tipo y mensaje (los mensajes de este script no contienen secretos).
        $script:Report.Add("ERROR   $($script:Stage): $($_.Exception.GetType().Name): $msg")
        $exitCode = 2
    }
} finally {
    $password = $null
    Remove-Variable -Name password -ErrorAction SilentlyContinue
    Restore-ProcessEnv
    if ($pushed) { Pop-Location }
    Write-Host ''
    Write-Host '==== INFORME test-isolated ====' -ForegroundColor Cyan
    foreach ($line in $script:Report) { Write-Host $line }
    if ($logFile) { Write-Host "log: $logFile" }
    Write-Host "exit: $exitCode"
}
exit $exitCode
