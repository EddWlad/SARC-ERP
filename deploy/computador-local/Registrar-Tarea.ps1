# Registra la tarea programada que ejecuta Descargar-CopiaSarc.ps1 en el computador local autorizado.
# Sin valores reales. Solo ASCII (Windows PowerShell 5.1).
#
# La tarea corre todos los dias a la hora indicada y, ademas, al iniciar sesion y cuando se perdio la
# ejecucion anterior (equipo apagado): StartWhenAvailable. Usa el usuario restringido indicado y no
# pide privilegios elevados.
#
# Uso (en una consola de PowerShell, una sola vez):
#   .\Registrar-Tarea.ps1 -Usuario 'DOMINIO\usuario-restringido'
#   .\Registrar-Tarea.ps1 -Usuario 'DOMINIO\usuario-restringido' -Hora '03:30'

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Usuario,
    [string]$Hora = '03:30',
    [string]$NombreTarea = 'SARC - Descargar copia de PostgreSQL',
    [string]$Script = (Join-Path $PSScriptRoot 'Descargar-CopiaSarc.ps1')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Script)) { throw 'No se encuentra Descargar-CopiaSarc.ps1' }

$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument ('-NoProfile -ExecutionPolicy Bypass -File "{0}"' -f $Script)
$triggers = @(
    (New-ScheduledTaskTrigger -Daily -At $Hora),
    (New-ScheduledTaskTrigger -AtLogOn -User $Usuario)
)
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 15) -ExecutionTimeLimit (New-TimeSpan -Hours 2) `
    -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$principal = New-ScheduledTaskPrincipal -UserId $Usuario -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName $NombreTarea -Action $action -Trigger $triggers `
    -Settings $settings -Principal $principal -Force | Out-Null
Write-Output ('Tarea registrada: ' + $NombreTarea)
