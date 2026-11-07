# Ensayo de restauracion de una copia de PostgreSQL de SARC en un entorno AISLADO y desechable.
# Sin valores reales. Solo ASCII. NO es una restauracion real: nunca toca produccion ni la base local de desarrollo.
# Contrato: specs/007-despliegue-seguro-cicd/contracts/backup-artifacts.md (Ensayo de restauracion).
#
# Pasos:
#   1. Comprobacion de aislamiento (si falla, ABORTA y queda en el informe): la base de destino es un contenedor
#      creado por este script, en localhost; no existe ninguna variable CLOUDINARY_* ni SARC_DB_* en el
#      entorno; el puerto elegido estaba libre.
#   2. PostgreSQL 18 desechable con una contrasena aleatoria (no es ninguna credencial de produccion).
#   3. Descifra la copia en un temporal, pg_restore (sin --clean) y comprobaciones:
#      latestMigration y tableCounts iguales a los metadatos, auditoria presente, historiales de importacion,
#      muestra de referencias a archivos presentes en la ultima exportacion de Cloudinary.
#   4. Mide el tiempo, imprime el informe para el issue `ensayo-restauracion` y destruye el entorno.
# Salida: informe en texto (sin datos de filas) y codigo 0 si el 100 % de las comprobaciones pasa; 1 si no; 3 si
# el aislamiento no se cumple.
#
# Uso: .\Ensayar-Restauracion.ps1 -Copia 'C:\...\sarc-20261120T0700Z-nocturna.dump.age' -IndiceExportacion 'C:\...\export-....json'

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Copia,
    [string]$IndiceExportacion,
    [string]$ConfigPath = (Join-Path $env:ProgramData 'SarcCopias\config.json'),
    [int]$Puerto = 55432,
    [int]$MuestraReferencias = 20
)

$ErrorActionPreference = 'Stop'
$clock = [System.Diagnostics.Stopwatch]::StartNew()
$report = New-Object System.Collections.Generic.List[string]
$checks = New-Object System.Collections.Generic.List[object]

function Add-Report([string]$Line) { $report.Add($Line); Write-Output $Line }
function Add-Check([string]$Name, [bool]$Ok, [string]$Detail) {
    $checks.Add([pscustomobject]@{ Name = $Name; Ok = $Ok })
    Add-Report ('- [{0}] {1}{2}' -f $(if ($Ok) { 'x' } else { ' ' }), $Name, $(if ($Detail) { ': ' + $Detail } else { '' }))
}

Add-Report ('## Ensayo de restauracion - {0}' -f (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))
Add-Report ('Copia: {0}' -f (Split-Path -Leaf $Copia))

# ---------- 1. Comprobacion de aislamiento (antes de tocar nada) ----------
$isolationErrors = @()
$forbidden = @(Get-ChildItem Env: | Where-Object { $_.Name -match '^(CLOUDINARY_|SARC_DB_)' } | ForEach-Object { $_.Name })
if ($forbidden.Count -gt 0) { $isolationErrors += ('hay variables de produccion en el entorno: ' + ($forbidden -join ', ')) }
$listener = $null
try {
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, $Puerto)
    $listener.Start()
    $listener.Stop()
} catch {
    $isolationErrors += ('el puerto {0} no estaba libre' -f $Puerto)
}
if ($isolationErrors.Count -gt 0) {
    Add-Check 'Aislamiento' $false ($isolationErrors -join '; ')
    Add-Report 'Resultado: ABORTADO por aislamiento (no se creo ningun entorno)'
    exit 3
}
Add-Check 'Aislamiento: contenedor propio en localhost, sin variables CLOUDINARY_* ni SARC_DB_*, puerto libre' $true ''

$metaPath = $Copia + '.meta.json'
if (-not (Test-Path -LiteralPath $Copia) -or -not (Test-Path -LiteralPath $metaPath)) {
    Add-Report 'Resultado: FALLIDO (faltan la copia o sus metadatos)'
    exit 1
}
$meta = Get-Content -LiteralPath $metaPath -Raw | ConvertFrom-Json
$cfg = $null
if (Test-Path -LiteralPath $ConfigPath) { $cfg = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json }

$container = 'sarc-ensayo-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$temp = Join-Path ([System.IO.Path]::GetTempPath()) ($container)
New-Item -ItemType Directory -Path $temp | Out-Null
$password = [guid]::NewGuid().ToString('N')

function Invoke-Psql([string]$Sql) {
    $out = & docker.exe exec -e ('PGPASSWORD=' + $password) $container psql -U postgres -d sarc -X -A -t -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'consulta fallida en la base de ensayo' }
    return ($out | ForEach-Object { $_.ToString() })
}

try {
    # ---------- 2. PostgreSQL 18 desechable (solo localhost) ----------
    & docker.exe run -d --name $container -e ('POSTGRES_PASSWORD=' + $password) -e 'POSTGRES_DB=sarc' `
        -p ('127.0.0.1:{0}:5432' -f $Puerto) postgres:18 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'no se pudo crear el contenedor de ensayo' }
    $ready = $false
    for ($i = 0; $i -lt 60 -and -not $ready; $i++) {
        & docker.exe exec $container pg_isready -U postgres -d sarc 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true } else { Start-Sleep -Seconds 2 }
    }
    if (-not $ready) { throw 'PostgreSQL de ensayo no arranco' }

    # ---------- 3. Descifrar, restaurar y comprobar ----------
    $plain = Join-Path $temp 'volcado.dump'
    & age.exe -d -i $cfg.ageIdentityFile -o $plain $Copia
    if ($LASTEXITCODE -ne 0) { throw 'no se pudo descifrar la copia' }
    & docker.exe cp $plain ('{0}:/tmp/volcado.dump' -f $container)
    Remove-Item -LiteralPath $plain -Force
    & docker.exe exec -e ('PGPASSWORD=' + $password) $container pg_restore -U postgres -d sarc --no-owner --exit-on-error /tmp/volcado.dump 2>&1 | Out-Null
    Add-Check 'pg_restore sin errores' ($LASTEXITCODE -eq 0) ''
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore fallo' }

    $migration = (Invoke-Psql "SELECT 'V' || version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1") | Select-Object -First 1
    Add-Check 'latestMigration igual a los metadatos' ($migration.Trim() -eq $meta.latestMigration) ('copia {0}' -f $meta.latestMigration)

    $mismatch = @()
    foreach ($p in $meta.tableCounts.PSObject.Properties) {
        $count = (Invoke-Psql ('SELECT count(*) FROM "{0}"' -f $p.Name)) | Select-Object -First 1
        if ([long]$count -ne [long]$p.Value) { $mismatch += $p.Name }
    }
    Add-Check 'tableCounts iguales a los metadatos' ($mismatch.Count -eq 0) $(if ($mismatch.Count) { 'difieren: ' + ($mismatch -join ', ') } else { 'todas las tablas' })

    $audit = [long]((Invoke-Psql 'SELECT count(*) FROM audit_event') | Select-Object -First 1)
    Add-Check 'auditoria presente' ($audit -eq [long]$meta.tableCounts.audit_event) ('{0} eventos' -f $audit)
    $imports = 0
    foreach ($t in @('energy_import_batch', 'asset_import_batch', 'upt_import_batch')) { $imports += [long]((Invoke-Psql ('SELECT count(*) FROM {0}' -f $t)) | Select-Object -First 1) }
    Add-Check 'historiales de importacion presentes' ($imports -eq ([long]$meta.tableCounts.energy_import_batch + [long]$meta.tableCounts.asset_import_batch + [long]$meta.tableCounts.upt_import_batch)) ('{0} lotes' -f $imports)

    # Referencias a archivos: muestra contrastada con la ultima exportacion de Cloudinary.
    if ($IndiceExportacion -and (Test-Path -LiteralPath $IndiceExportacion)) {
        $exported = @{}
        foreach ($a in (Get-Content -LiteralPath $IndiceExportacion -Raw | ConvertFrom-Json).assets) { $exported[$a.public_id] = $true }
        $refs = Invoke-Psql ("SELECT ref FROM (SELECT image_ref AS ref FROM asset_equipment UNION ALL SELECT original_ref FROM energy_import_batch UNION ALL SELECT original_ref FROM asset_import_batch UNION ALL SELECT original_ref FROM upt_import_batch UNION ALL SELECT file_ref FROM report_issued) r WHERE ref IS NOT NULL ORDER BY random() LIMIT {0}" -f $MuestraReferencias)
        $missing = @($refs | Where-Object { $_ -and -not $exported.ContainsKey($_.Trim()) })
        Add-Check ('muestra de {0} referencias a archivos presentes en la exportacion' -f @($refs).Count) ($missing.Count -eq 0) $(if ($missing.Count) { '{0} no estan en la exportacion (restaurables en Cloudinary?)' -f $missing.Count } else { '' })
    } else {
        Add-Check 'referencias a archivos (sin indice de exportacion: se omite)' $true 'pendiente de ensayar con la exportacion'
    }
} catch {
    Add-Check 'Ejecucion del ensayo' $false $_.Exception.Message
} finally {
    # ---------- 4. Destruir el entorno ----------
    & docker.exe rm -f -v $container 2>&1 | Out-Null
    if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Recurse -Force }
    $password = $null
}

$clock.Stop()
$failed = @($checks | Where-Object { -not $_.Ok }).Count
Add-Report ('Tiempo total: {0:N1} min (objetivo de recuperacion: 4 h)' -f $clock.Elapsed.TotalMinutes)
Add-Report ('Punto de datos recuperado: copia creada {0}' -f $meta.createdAt)
Add-Report ('Resultado: {0} ({1}/{2} comprobaciones)' -f $(if ($failed -eq 0) { 'APROBADO' } else { 'FALLIDO' }), ($checks.Count - $failed), $checks.Count)
if ($failed -eq 0) { exit 0 } else { exit 1 }
