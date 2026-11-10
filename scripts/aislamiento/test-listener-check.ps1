<#
.SYNOPSIS
  Prueba (sin Docker, sin Maven, sin base, sin red) la validacion de listeners de Assert-PublishedPortListener.

.DESCRIPTION
  Usa conexiones y duenos FALSOS inyectados por parametro. Cada caso negativo debe abortar con
  "GESFUT PREFLIGHT" (que test-isolated.ps1 convierte en exit 2); los positivos no deben abortar.
  Salida: exit 0 si los 19 casos (11 de listener y 8 de GRANT) dan lo esperado, exit 1 si alguno no.
#>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'isolation-common.ps1')

function Conn([string]$Addr, [string]$State, [int]$Owner) {
    [pscustomobject]@{ LocalAddress = $Addr; LocalPort = 52169; State = $State; OwningProcess = $Owner }
}
$names = @{ 100 = 'com.docker.backend'; 101 = 'mysqld'; 102 = 'notepad'; 103 = 'dockerd'; 104 = 'docker' }
$namer = { param($ProcessId) $names[[int]$ProcessId] }
$yes = { param($P) $true }
$no  = { param($P) $false }

# Expect: 'ok' o un fragmento del mensaje de aborto.
$cases = @(
    @{ N = 'P1 Listen, loopback, docker backend (comportamiento original)'; C = @(Conn '127.0.0.1' 'Listen' 100); Connect = $yes; Expect = 'ok' },
    @{ N = 'P2 sin estado, loopback, com.docker.backend, conecta (el fix)'; C = @(Conn '127.0.0.1' '' 100); Connect = $yes; Expect = 'ok' },
    @{ N = 'P3 sin estado (0), loopback, docker, conecta'; C = @(Conn '127.0.0.1' '0' 104); Connect = $yes; Expect = 'ok' },
    @{ N = 'P4 sin estado, dueno mysqld'; C = @(Conn '127.0.0.1' '' 101); Connect = $yes; Expect = 'mysqld' },
    @{ N = 'P5 sin estado, dueno otro proceso'; C = @(Conn '127.0.0.1' '' 102); Connect = $yes; Expect = 'no es com.docker.backend/docker' },
    @{ N = 'P6 sin estado, no loopback (0.0.0.0)'; C = @(Conn '0.0.0.0' '' 100); Connect = $yes; Expect = 'solo se acepta 127.0.0.1' },
    @{ N = 'P7 sin estado, docker y loopback pero no conecta'; C = @(Conn '127.0.0.1' '' 100); Connect = $no; Expect = 'no acepta conexiones' },
    @{ N = 'P8 sin entradas'; C = @(); Connect = $yes; Expect = 'nadie escucha' },
    @{ N = 'P9 solo Established (no cuenta como listener)'; C = @(Conn '127.0.0.1' 'Established' 100); Connect = $yes; Expect = 'nadie escucha' },
    @{ N = 'P10 Listen en 0.0.0.0 mas una entrada sin estado valida'; C = @((Conn '0.0.0.0' 'Listen' 100), (Conn '127.0.0.1' '' 100)); Connect = $yes; Expect = 'no loopback' },
    @{ N = 'P11 sin estado con dockerd (permitido solo con Listen, no en el fallback)'; C = @(Conn '127.0.0.1' '' 103); Connect = $yes; Expect = 'no es com.docker.backend/docker' }
)
$fail = 0
foreach ($c in $cases) {
    $msg = $null
    try { Assert-PublishedPortListener -Port 52169 -Connections $c.C -GetProcessName $namer -CanConnect $c.Connect } catch { $msg = $_.Exception.Message }
    $okRun = if ($c.Expect -eq 'ok') { $null -eq $msg } else { ($null -ne $msg) -and $msg.StartsWith($script:PreflightMarker) -and $msg.Contains($c.Expect) }
    if ($okRun) { Write-Host ("OK     " + $c.N) } else { $fail++; Write-Host ("FALLO  " + $c.N + " -> " + $msg) -ForegroundColor Red }
}
# --- Grants: salida de mysql -N -B (backslash duplicado) ---
$bt = [string][char]96
$gcases = @(
    @{ N = 'G1 base aislada tal cual la imprime -B (con \\)'; O = ($bt + 'gesfut\\_isolated\\_test' + $bt + '.*'); Expect = $true },
    @{ N = 'G2 base aislada con \_ (sin batch)'; O = ($bt + 'gesfut\_isolated\_test' + $bt + '.*'); Expect = $true },
    @{ N = 'G3 base aislada sin escapes'; O = ($bt + 'gesfut_isolated_test' + $bt + '.*'); Expect = $true },
    @{ N = 'G4 otra base (GESFUT2)'; O = ($bt + 'GESFUT2' + $bt + '.*'); Expect = $false },
    @{ N = 'G5 comodin de todas las bases'; O = ($bt + '%' + $bt + '.*'); Expect = $false },
    @{ N = 'G6 prefijo con comodin que abarca otras bases'; O = ($bt + 'gesfut\\_%' + $bt + '.*'); Expect = $false },
    @{ N = 'G7 tabla puntual de la base aislada'; O = ($bt + 'gesfut\\_isolated\\_test' + $bt + '.matches'); Expect = $false },
    @{ N = 'G8 cuatro backslashes (patron distinto)'; O = ($bt + 'gesfut\\\\_isolated\\\\_test' + $bt + '.*'); Expect = $false }
)
foreach ($g in $gcases) {
    $r = Test-GrantObjectIsIsolatedDb $g.O
    if ($r -eq $g.Expect) { Write-Host ("OK     " + $g.N) } else { $fail++; Write-Host ("FALLO  " + $g.N) -ForegroundColor Red }
}
$total = $cases.Count + $gcases.Count
Write-Host ("test-listener-check: {0} casos, {1} fallos" -f $total, $fail)
if ($fail -gt 0) { exit 1 }
exit 0