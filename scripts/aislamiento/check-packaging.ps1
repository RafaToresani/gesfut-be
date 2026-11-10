<#
.SYNOPSIS
  Verifica que nada del guardián de tests quede en el artefacto de producción ni en src/main.
  Se corre DESPUÉS de que otra persona ejecute "mvnw.cmd -B package" (este script NO corre Maven).
  Usa "jar tf" si hay JDK; si no, lista el zip con .NET. Exit 1 si encuentra algo.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [string]$JarPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
if (-not $JarPath) { $JarPath = Join-Path $RepoRoot 'target\gesfut-0.0.1-SNAPSHOT.jar' }
if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    Write-Host "No existe $JarPath. Primero alguien tiene que correr: .\mvnw.cmd -B package (con el aislamiento)."
    exit 1
}

$problems = New-Object System.Collections.Generic.List[string]

# 1) Listado de entradas: jar tf (preferido) o .NET
$entries = $null
$jarExe = $null
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\jar.exe'))) { $jarExe = Join-Path $env:JAVA_HOME 'bin\jar.exe' } elseif (Get-Command jar -ErrorAction SilentlyContinue) { $jarExe = (Get-Command jar).Source }
if ($jarExe) {
    $entries = @(& $jarExe tf $JarPath)
    if ($LASTEXITCODE -ne 0) { Write-Host 'jar tf falló'; exit 1 }
    Write-Host "Listado con: $jarExe tf"
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
try {
    if (-not $entries) {
        $entries = @($zip.Entries | ForEach-Object { $_.FullName })
        Write-Host 'Listado con: System.IO.Compression (no se encontró jar.exe)'
    }
    # Clases del guardián que NUNCA deben aparecer (además del patrón genérico IsolatedDb|testsupport).
    $guardClasses = @('IsolatedDbRules', 'IsolatedDbGuard', 'IsolatedDbConnectionGuard', 'IsolatedDbGuardInitializer',
        'IsolatedDbGuardTestAutoConfiguration', 'ExternalDynamicPropertySourceNegativeTest')
    foreach ($e in $entries) {
        foreach ($gc in $guardClasses) {
            if ($e -match ('(^|/)' + $gc + '(\$[^/]*)?\.class$')) { $problems.Add("clase del guardián en el jar: $e") }
        }
        if ($e -match '(?i)IsolatedDb|testsupport') { $problems.Add("entrada de test en el jar: $e") }
        if ($e -match '(?i)^BOOT-INF/classes/META-INF/spring/org\.springframework\.boot\.autoconfigure\.AutoConfiguration\.imports$') {
            Write-Host "aviso: la app tiene AutoConfiguration.imports propio; se revisa su contenido abajo"
        }
        if ($e -match '(?i)^BOOT-INF/lib/gesfut.*tests?\.jar$') { $problems.Add("test-jar embebido: $e") }
    }
    # 2) spring.factories / AutoConfiguration.imports de la app (no los de librerías en BOOT-INF/lib)
    foreach ($ze in $zip.Entries) {
        $n = $ze.FullName
        if ($n -like 'BOOT-INF/lib/*') { continue }
        if ($n -match '(^|/)META-INF/spring\.factories$' -or $n -match '(^|/)META-INF/spring/.+\.imports$') {
            $reader = New-Object System.IO.StreamReader($ze.Open())
            try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if ($content -match 'com\.gesfut\.testsupport|IsolatedDb') { $problems.Add("registro de test en $n") }
            else { Write-Host "aviso: existe $n en la app (sin referencias al guardián)" }
        }
    }
} finally { $zip.Dispose() }

# 3) src/main no debe contener nada del guardián
$main = Join-Path $RepoRoot 'src\main'
foreach ($hit in @(Get-ChildItem -LiteralPath $main -Recurse -File -Force | Where-Object { $_.Extension -in @('.java', '.factories', '.imports', '.properties', '.yml', '.yaml') } | Select-String -Pattern 'com\.gesfut\.testsupport|IsolatedDb|IsolatedDbGuardTestAutoConfiguration' -List)) {
    $problems.Add("src/main referencia el guardián: $($hit.Path.Substring($RepoRoot.Length))")
}
if (Test-Path -LiteralPath (Join-Path $main 'java\com\gesfut\testsupport')) { $problems.Add('existe src/main/java/com/gesfut/testsupport') }

if ($problems.Count -gt 0) {
    Write-Host 'check-packaging: FALLA' -ForegroundColor Red
    foreach ($p in $problems) { Write-Host "  $p" }
    exit 1
}
Write-Host "check-packaging: OK ($($entries.Count) entradas revisadas; nada del guardián en el jar ni en src/main)"
exit 0
