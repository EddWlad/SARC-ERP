Scripts del computador local autorizado (descarga de copias, exportación de Cloudinary, ensayo de restauración). Sin valores reales.

- `config.ejemplo.json`: plantilla de la configuración (copiar a `%ProgramData%\SarcCopias\config.json`, fuera del repositorio, con permisos solo para el usuario restringido).
- Los `.ps1` son solo ASCII y se ejecutan con Windows PowerShell 5.1 o superior. Si el computador no es Windows, se reescriben en Bash con el mismo contrato (`contracts/backup-artifacts.md`).

## Instalación en el computador local (resumen)

1. Carpeta `%ProgramData%\SarcCopias` con permisos solo para el usuario: `config.json`, la llave privada de `sarc-backup`, su `known_hosts` fijado, el archivo de la identidad `age` y `bin\age.exe`. En `config.json`, `binDir` apunta a la carpeta de `age.exe` porque la tarea programada no hereda el PATH.
2. Copia de los scripts a `%ProgramData%\SarcCopias\scripts` y registro de la tarea con `Registrar-Tarea.ps1 -Usuario '<equipo>\<usuario>' -Script '<ruta de la copia>'`. Así la tarea no depende de la rama ni del árbol de trabajo. Tras cambiar un script en el repositorio, hay que volver a copiarlo.
3. Comprobar con `Start-ScheduledTask` que el resultado es 0 y que el registro del mes en `registros\` dice «descarga completada».
4. Añadir `healthchecksUrl` cuando exista la comprobación `descarga-local`. Sin ella la descarga funciona, pero no hay aviso si falla.
