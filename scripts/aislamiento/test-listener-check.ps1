<#
.SYNOPSIS
  Prueba (sin Docker, sin Maven, sin base, sin red) la validacion de listeners de Assert-PublishedPortListener.

.DESCRIPTION
  Usa conexiones y duenos FALSOS inyectados por parametro. Cada caso negativo debe abortar con
  "GESFUT PREFLIGHT" (que test-isolated.ps1 convierte en exit 2); los positivos no deben abortar.
  Salida: exit 0 si los 32 casos (13 de listener, 8 de GRANT y 11 de clave JWT descartable) dan lo esperado, exit 1 si alguno no.
#>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'isolation-common.ps1')

function Conn([string]$Addr, [string]$State, [int]$Owner) {
    [pscustomobject]@{ LocalAddress = $Addr; LocalPort = 52169; State = $State; OwningProcess = $Owner }
}
$names = @{ 100 = 'com.docker.backend'; 101 = 'mysqld'; 102 = 'notepad'; 103 = 'dockerd'; 104 = 'docker'; 105 = 'COM.DOCKER.BACKEND'; 106 = 'Com.Docker.Backend' }
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
    @{ N = 'P11 sin estado con dockerd (permitido solo con Listen, no en el fallback)'; C = @(Conn '127.0.0.1' '' 103); Connect = $yes; Expect = 'no es com.docker.backend/docker' },
    @{ N = 'P12 sin estado, dueno COM.DOCKER.BACKEND (otra capitalizacion): la comparacion es sensible a mayusculas'; C = @(Conn '127.0.0.1' '' 105); Connect = $yes; Expect = 'no es com.docker.backend/docker' },
    @{ N = 'P13 Listen, dueno Com.Docker.Backend (otra capitalizacion): la comparacion es sensible a mayusculas'; C = @(Conn '127.0.0.1' 'Listen' 106); Connect = $yes; Expect = 'no reconocido como Docker' }
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
# --- Clave JWT descartable: generador + garantia de que test-isolated.ps1 no la imprime ni la escribe ---
function Get-JwtKeyUsageViolations([string]$ScriptText) {
    $errs = $null; $tokens = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseInput($ScriptText, [ref]$tokens, [ref]$errs)
    $v = New-Object System.Collections.Generic.List[string]
    if ($errs -and $errs.Count -gt 0) { $v.Add('el script no parsea'); return ,$v }
    $uses = $ast.FindAll({ param($n) $n -is [System.Management.Automation.Language.VariableExpressionAst] -and $n.VariablePath.UserPath -ieq 'jwtKey' }, $true)
    $sets = 0; $contains = 0; $nulls = 0; $gens = 0
    foreach ($u in $uses) {
        $p = $u.Parent
        if ($p -is [System.Management.Automation.Language.AssignmentStatementAst] -and [object]::ReferenceEquals($p.Left, $u)) {
            if ($p.Right.Extent.Text.Trim() -eq '$null') { $nulls++ }
            elseif ($p.Right.Extent.Text.Trim() -eq 'New-EphemeralJwtKey') { $gens++ }
            else { $v.Add("asignacion no permitida: $($p.Extent.Text)") }
        } elseif ($p -is [System.Management.Automation.Language.CommandAst] -and $p.GetCommandName() -eq 'Set-ProcessEnv') {
            $sets++
        } elseif ($p -is [System.Management.Automation.Language.InvokeMemberExpressionAst] -and "$($p.Member)" -eq 'Contains' -and @($p.Arguments | Where-Object { [object]::ReferenceEquals($_, $u) }).Count -eq 1) {
            $contains++
        } else {
            $v.Add("uso no permitido de la clave en: $($p.Extent.Text)")
        }
    }
    if ($gens -ne 1) { $v.Add("se esperaba 1 generacion con New-EphemeralJwtKey y hay $gens") }
    if ($sets -lt 1) { $v.Add('la clave no se pasa a Set-ProcessEnv') }
    if ($contains -lt 1) { $v.Add('falta la comprobacion de fuga en el log (Contains)') }
    if ($nulls -lt 1) { $v.Add('la clave no se anula en finally') }
    return ,$v
}
function Get-FunctionOutputCommands([string]$ScriptText, [string]$FunctionName) {
    $errs = $null; $tokens = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseInput($ScriptText, [ref]$tokens, [ref]$errs)
    $fn = $ast.Find({ param($n) $n -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $n.Name -eq $FunctionName }, $true)
    if ($null -eq $fn) { return @('funcion no encontrada') }
    return @($fn.FindAll({ param($n) $n -is [System.Management.Automation.Language.CommandAst] -and $n.GetCommandName() -match '^(Write-|Out-|Set-Content|Add-Content|Tee-Object|Export-|ConvertTo-)' }, $true) | ForEach-Object { $_.Extent.Text })
}
$kcases = @()
$k1 = New-EphemeralJwtKey; $k2 = New-EphemeralJwtKey
$kb = $null; try { $kb = [Convert]::FromBase64String($k1) } catch { }
$kcases += @{ N = 'K1 la clave descartable es Base64 valida de 64 bytes (jwtService la decodifica asi)'; Ok = (($null -ne $kb) -and $kb.Length -eq 64) }
$kcases += @{ N = 'K2 dos claves generadas son distintas'; Ok = ($k1 -cne $k2) }
$kcases += @{ N = 'K3 New-EphemeralJwtKey no imprime ni escribe nada'; Ok = (@(Get-FunctionOutputCommands (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'isolation-common.ps1') -Raw) 'New-EphemeralJwtKey').Count -eq 0) }
$real = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'test-isolated.ps1') -Raw
$rv = Get-JwtKeyUsageViolations $real
$kcases += @{ N = 'K4 test-isolated.ps1 solo genera, pasa a Set-ProcessEnv, compara con el log y anula la clave'; Ok = ($rv.Count -eq 0); Detail = ($rv -join ' | ') }
$kcases += @{ N = 'K5 test-isolated.ps1 no lee SECRET_KEY del entorno (nunca usa la real)'; Ok = (-not ($real -match '(?i)\$env:SECRET_KEY|GetEnvironmentVariable\(\s*[''"]SECRET_KEY')) }
$bad = @(
    @{ N = 'K6 detecta Write-Host con la clave'; T = '$jwtKey = New-EphemeralJwtKey; Set-ProcessEnv ''SECRET_KEY'' $jwtKey; $log.Contains($jwtKey); $jwtKey = $null; Write-Host $jwtKey' },
    @{ N = 'K7 detecta la clave interpolada en el informe'; T = '$jwtKey = New-EphemeralJwtKey; Set-ProcessEnv ''SECRET_KEY'' $jwtKey; $log.Contains($jwtKey); $jwtKey = $null; $script:Report.Add("clave $jwtKey")' },
    @{ N = 'K8 detecta la clave escrita a un archivo'; T = '$jwtKey = New-EphemeralJwtKey; Set-ProcessEnv ''SECRET_KEY'' $jwtKey; $log.Contains($jwtKey); $jwtKey = $null; $jwtKey | Out-File x.txt' },
    @{ N = 'K9 detecta una clave generada de otra forma'; T = '$jwtKey = ''fija''; Set-ProcessEnv ''SECRET_KEY'' $jwtKey; $log.Contains($jwtKey); $jwtKey = $null' },
    @{ N = 'K10 detecta que falte la comprobacion de fuga'; T = '$jwtKey = New-EphemeralJwtKey; Set-ProcessEnv ''SECRET_KEY'' $jwtKey; $jwtKey = $null' },
    @{ N = 'K11 detecta que no se anule la clave'; T = '$jwtKey = New-EphemeralJwtKey; Set-ProcessEnv ''SECRET_KEY'' $jwtKey; $log.Contains($jwtKey)' }
)
foreach ($b in $bad) { $kcases += @{ N = $b.N; Ok = ((Get-JwtKeyUsageViolations $b.T).Count -gt 0) } }
foreach ($k in $kcases) {
    if ($k.Ok) { Write-Host ("OK     " + $k.N) } else { $fail++; Write-Host ("FALLO  " + $k.N + $(if ($k.Detail) { " -> " + $k.Detail } else { '' })) -ForegroundColor Red }
}
$k1 = $null; $k2 = $null; $kb = $null
$total = $cases.Count + $gcases.Count + $kcases.Count
Write-Host ("test-listener-check: {0} casos, {1} fallos" -f $total, $fail)
if ($fail -gt 0) { exit 1 }
exit 0