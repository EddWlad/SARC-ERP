# Exporta al computador local los archivos de SARC guardados en Cloudinary (carpeta de produccion).
# Sin valores reales: configuracion en el JSON fuera del repositorio (config.ejemplo.json). Solo ASCII.
#
# - Lista con la API de administracion los recursos de la carpeta (image y raw, entrega `authenticated`).
# - Descarga de forma incremental por public_id + version con URL firmadas de descarga privada.
# - Escribe export-<fecha>.json con public_id, resource_type, version, bytes y sha256.
# - Solo lectura sobre Cloudinary (no sube, no borra, no modifica nada). Usa la clave de API dedicada a
#   la exportacion; el secreto se lee de un archivo (apiSecretFile), nunca de un argumento ni del registro.
# Contrato: specs/007-despliegue-seguro-cicd/contracts/backup-artifacts.md (Exportacion de Cloudinary).
# Mensual y antes de cambios criticos. Con -SoloListar solo cuenta recursos (no descarga).

[CmdletBinding()]
param(
    [string]$ConfigPath = (Join-Path $env:ProgramData 'SarcCopias\config.json'),
    [switch]$SoloListar
)

$ErrorActionPreference = 'Stop'

function Get-Sha1Hex([string]$Text) {
    $sha1 = [System.Security.Cryptography.SHA1]::Create()
    return ([BitConverter]::ToString($sha1.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text))) -replace '-', '').ToLower()
}

if (-not (Test-Path -LiteralPath $ConfigPath)) { Write-Output 'ERROR falta el archivo de configuracion'; exit 12 }
$cfg = (Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json).cloudinary
if (-not $cfg -or -not (Test-Path -LiteralPath $cfg.apiSecretFile)) { Write-Output 'ERROR configuracion de Cloudinary incompleta'; exit 12 }
$secret = (Get-Content -LiteralPath $cfg.apiSecretFile -Raw).Trim()
$cloud = $cfg.cloudName
$authHeader = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($cfg.apiKey + ':' + $secret)) }
$exportRoot = $cfg.exportDir
$filesDir = Join-Path $exportRoot 'archivos'
New-Item -ItemType Directory -Force -Path $filesDir | Out-Null

# Indice de la exportacion anterior para descargar solo lo nuevo o cambiado (public_id + version).
$previous = @{}
$last = Get-ChildItem -Path $exportRoot -Filter 'export-*.json' -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
if ($last) {
    foreach ($a in (Get-Content -LiteralPath $last.FullName -Raw | ConvertFrom-Json).assets) {
        $previous[('{0}|{1}|{2}' -f $a.resource_type, $a.public_id, $a.version)] = $a
    }
}

$assets = New-Object System.Collections.Generic.List[object]
foreach ($resourceType in @('image', 'raw')) {
    $cursor = $null
    do {
        $uri = 'https://api.cloudinary.com/v1_1/{0}/resources/{1}/authenticated?prefix={2}&max_results=500' -f $cloud, $resourceType, [uri]::EscapeDataString($cfg.folder + '/')
        if ($cursor) { $uri += '&next_cursor=' + [uri]::EscapeDataString($cursor) }
        $page = Invoke-RestMethod -Uri $uri -Headers $authHeader -Method Get -TimeoutSec 60
        foreach ($r in $page.resources) {
            $assets.Add([pscustomobject]@{
                    public_id     = $r.public_id
                    resource_type = $resourceType
                    version       = [string]$r.version
                    bytes         = [long]$r.bytes
                    format        = $r.format
                    sha256        = $null
                })
        }
        $cursor = $page.next_cursor
    } while ($cursor)
}
Write-Output ('recursos en la carpeta: {0}' -f $assets.Count)
if ($SoloListar) { exit 0 }

$downloaded = 0
foreach ($a in $assets) {
    $key = '{0}|{1}|{2}' -f $a.resource_type, $a.public_id, $a.version
    $localName = ($a.resource_type + '_' + $a.version + '_' + ($a.public_id -replace '[\\/:*?"<>|]', '_'))
    $localPath = Join-Path $filesDir $localName
    if ($previous.ContainsKey($key) -and $previous[$key].sha256 -and (Test-Path -LiteralPath $localPath)) {
        $a.sha256 = $previous[$key].sha256
        continue
    }
    # Descarga privada firmada (mismo mecanismo que usa la aplicacion): firma = SHA-1 de los parametros
    # ordenados alfabeticamente (clave=valor unidos por &) seguidos del secreto de API.
    $params = [ordered]@{ public_id = $a.public_id; timestamp = [string][DateTimeOffset]::UtcNow.ToUnixTimeSeconds(); type = 'authenticated' }
    if ($a.resource_type -eq 'image' -and $a.format) { $params['format'] = $a.format }
    $toSign = (($params.GetEnumerator() | Sort-Object Name | ForEach-Object { '{0}={1}' -f $_.Name, $_.Value }) -join '&') + $secret
    $query = (($params.GetEnumerator() | ForEach-Object { '{0}={1}' -f $_.Name, [uri]::EscapeDataString([string]$_.Value) }) -join '&')
    $url = 'https://api.cloudinary.com/v1_1/{0}/{1}/download?{2}&api_key={3}&signature={4}' -f $cloud, $a.resource_type, $query, $cfg.apiKey, (Get-Sha1Hex $toSign)
    Invoke-WebRequest -Uri $url -OutFile $localPath -UseBasicParsing -TimeoutSec 120
    $a.sha256 = (Get-FileHash -LiteralPath $localPath -Algorithm SHA256).Hash.ToLower()
    $downloaded++
}

$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmZ')
$index = [pscustomobject]@{
    exportedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    folder     = $cfg.folder
    assets     = $assets | ForEach-Object { [pscustomobject]@{ public_id = $_.public_id; resource_type = $_.resource_type; version = $_.version; bytes = $_.bytes; sha256 = $_.sha256 } }
}
$index | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $exportRoot ('export-{0}.json' -f $stamp)) -Encoding ASCII
Write-Output ('exportacion completada: {0} recursos, {1} descargados' -f $assets.Count, $downloaded)
