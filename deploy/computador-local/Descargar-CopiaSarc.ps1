# Descarga y verifica las copias cifradas de PostgreSQL de SARC en el computador local autorizado.
# Sin valores reales en este archivo: la configuracion vive en un archivo JSON fuera del repositorio
# (ver deploy/computador-local/config.ejemplo.json). Solo ASCII: Windows PowerShell 5.1 lee mal UTF-8 sin BOM.
#
# Que hace (contrato: specs/007-despliegue-seguro-cicd/contracts/backup-artifacts.md):
#   1. Lista por SFTP las copias sarc-*.dump.age del VPS (cuenta sarc-backup, clave propia, sin shell).
#   2. Descarga las nuevas y verifica la suma SHA-256.
#   3. Descifra en una carpeta temporal con la clave privada de `age` y ejecuta `pg_restore --list`
#      con un contenedor postgres:18 desechable; borra el temporal siempre.
#   4. Guarda la copia cifrada (30 diarias + 12 mensuales) y sube el recibo received/<archivo>.ok.
#   5. Envia el latido `descarga-local` a Healthchecks.io (o /fail si algo falla).
# Registra solo estados y nombres de copia; nunca rutas de claves, contenidos ni salidas de comandos.
# Requisitos: OpenSSH (sftp.exe), age.exe, Docker Desktop, disco cifrado y usuario restringido.

[CmdletBinding()]
param(
    [string]$ConfigPath = (Join-Path $env:ProgramData 'SarcCopias\config.json')
)

$ErrorActionPreference = 'Stop'

function Write-Log([string]$Level, [string]$Message) {
    $line = '{0} {1} {2}' -f (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'), $Level, $Message
    Write-Output $line
    if ($script:LogFile) { Add-Content -Path $script:LogFile -Value $line }
}

function Send-Heartbeat([string]$Suffix) {
    if (-not $script:Config.healthchecksUrl) { return }
    try {
        $url = $script:Config.healthchecksUrl.TrimEnd('/') + $Suffix
        Invoke-WebRequest -Uri $url -Method Get -UseBasicParsing -TimeoutSec 10 | Out-Null
    } catch {
        Write-Log 'ERROR' 'no se pudo enviar el latido descarga-local'
    }
}

function Invoke-Sftp([string[]]$Commands) {
    $batch = Join-Path $script:Temp 'lote.sftp'
    Set-Content -Path $batch -Value $Commands -Encoding ASCII
    $sftpArgs = @('-b', $batch, '-i', $script:Config.sshKeyFile, '-P', [string]$script:Config.port,
        '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', ('UserKnownHostsFile=' + $script:Config.knownHostsFile),
        ('{0}@{1}' -f $script:Config.user, $script:Config.host))
    $out = & sftp.exe @sftpArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'fallo de SFTP' }
    return $out
}

if (-not (Test-Path -LiteralPath $ConfigPath)) {
    Write-Output 'ERROR falta el archivo de configuracion'
    exit 12
}
$script:Config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
# Carpeta opcional con age.exe: la tarea programada no hereda el PATH del usuario.
if ($script:Config.binDir) { $env:Path = $script:Config.binDir + ';' + $env:Path }
$dest = $script:Config.destDir
$daily = Join-Path $dest 'diarias'
$monthly = Join-Path $dest 'mensuales'
New-Item -ItemType Directory -Force -Path $daily, $monthly, (Join-Path $dest 'registros') | Out-Null
$script:LogFile = Join-Path $dest ('registros\descarga-{0}.log' -f (Get-Date -Format 'yyyyMM'))

# Una sola ejecucion a la vez.
$lock = New-Object System.Threading.Mutex($false, 'Global\SarcDescargarCopia')
if (-not $lock.WaitOne(0)) { Write-Log 'ERROR' 'ya hay una descarga en curso'; exit 10 }

$script:Temp = Join-Path ([System.IO.Path]::GetTempPath()) ('sarc-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $script:Temp | Out-Null
$failed = $false

try {
    Send-Heartbeat '/start'
    # 1. Copias disponibles en el VPS.
    $listing = Invoke-Sftp @('ls -1')
    $remote = @($listing | Where-Object { $_ -match '^sarc-\d{8}T\d{4}Z-(nocturna|previa|manual)\.dump\.age$' })
    $known = @(Get-ChildItem -Path $daily, $monthly -Filter 'sarc-*.dump.age' -ErrorAction SilentlyContinue | ForEach-Object { $_.Name })
    $pending = @($remote | Where-Object { $known -notcontains $_ })
    Write-Log 'INFO' ('copias nuevas por descargar: {0}' -f $pending.Count)

    foreach ($name in $pending) {
        # 2. Descarga y suma.
        Invoke-Sftp @(('get {0} {1}' -f $name, $script:Temp), ('get {0}.sha256 {1}' -f $name, $script:Temp), ('get {0}.meta.json {1}' -f $name, $script:Temp)) | Out-Null
        $file = Join-Path $script:Temp $name
        $expected = ((Get-Content -LiteralPath ($file + '.sha256') -TotalCount 1) -split '\s+')[0].ToLower()
        $actual = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower()
        if ($actual -ne $expected) { throw ('suma distinta en ' + $name) }

        # 3. Descifrar en temporal y comprobar que PostgreSQL 18 puede leer el volcado.
        $plain = Join-Path $script:Temp 'volcado.dump'
        & age.exe -d -i $script:Config.ageIdentityFile -o $plain $file
        if ($LASTEXITCODE -ne 0) { throw ('no se pudo descifrar ' + $name) }
        & docker.exe run --rm -v ('{0}:/datos:ro' -f $script:Temp) postgres:18 pg_restore --list /datos/volcado.dump 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw ('pg_restore --list fallo en ' + $name) }
        Remove-Item -LiteralPath $plain -Force

        # 4. Guardar la copia cifrada y subir el recibo.
        Copy-Item -LiteralPath $file -Destination (Join-Path $daily $name)
        Copy-Item -LiteralPath ($file + '.meta.json') -Destination (Join-Path $daily ($name + '.meta.json'))
        $month = $name.Substring(5, 6)
        $hasMonth = Get-ChildItem -Path $monthly -Filter ('sarc-{0}*' -f $month) -ErrorAction SilentlyContinue
        if (-not $hasMonth) {
            Copy-Item -LiteralPath $file -Destination (Join-Path $monthly $name)
            Copy-Item -LiteralPath ($file + '.meta.json') -Destination (Join-Path $monthly ($name + '.meta.json'))
        }
        $receipt = Join-Path $script:Temp ($name + '.ok')
        Set-Content -Path $receipt -Encoding ASCII -Value @(
            ('fecha=' + (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')),
            ('archivo=' + $name),
            ('sha256=' + $actual))
        Invoke-Sftp @(('put {0} received/{1}.ok' -f $receipt, $name)) | Out-Null
        Write-Log 'INFO' ('copia verificada y recibo enviado: ' + $name)
    }

    # Retencion local: 30 diarias y 12 mensuales (las mas recientes).
    Get-ChildItem -Path $daily -Filter 'sarc-*.dump.age' | Sort-Object Name -Descending | Select-Object -Skip 30 | ForEach-Object { Remove-Item -Force -LiteralPath $_.FullName, ($_.FullName + '.meta.json') -ErrorAction SilentlyContinue }
    Get-ChildItem -Path $monthly -Filter 'sarc-*.dump.age' | Sort-Object Name -Descending | Select-Object -Skip 12 | ForEach-Object { Remove-Item -Force -LiteralPath $_.FullName, ($_.FullName + '.meta.json') -ErrorAction SilentlyContinue }
    Write-Log 'INFO' 'descarga completada'
} catch {
    $failed = $true
    Write-Log 'ERROR' ('descarga fallida: ' + $_.Exception.Message)
} finally {
    # El temporal (con copias descifradas) se borra siempre.
    if (Test-Path -LiteralPath $script:Temp) { Remove-Item -LiteralPath $script:Temp -Recurse -Force }
    $lock.ReleaseMutex()
}

if ($failed) { Send-Heartbeat '/fail'; exit 1 }
Send-Heartbeat ''
exit 0
