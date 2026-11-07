# Restaurar copias

Hay **dos cosas distintas** que no deben confundirse:

1. **Ensayo de restauración**: se hace en un entorno aislado y desechable, con frecuencia mensual y después de cambios críticos. No toca producción.
2. **Restauración real en producción**: excepcional, con autorización y registro. Sustituye datos de producción.

**Responsable**: ensayo, responsable técnico; restauración real, responsable del producto (autoriza) y responsable técnico (ejecuta). **Requisitos previos**: ver cada sección.

## Ensayo de restauración (aislado, mensual y tras cambios críticos)

Comprueba que una copia puede restaurarse y que los datos están íntegros. **Nunca** usa credenciales de producción ni de Cloudinary, ni la base local de desarrollo.

**Cuándo**: cada mes y tras cada cambio crítico: una publicación que aplicó migraciones (se abre un *issue* `ensayo-restauracion` `pendiente` automáticamente), el reemplazo del computador local o un cambio de proveedor.

**Requisitos previos**: computador local autorizado con Docker, `age.exe` y la clave privada `age`; una copia descargada y verificada con sus metadatos (`.meta.json`); opcionalmente la última exportación de Cloudinary.

**Pasos**:

1. En el computador local, abrir PowerShell y elegir la copia verificada más reciente (o la que indique el cambio crítico):

```powershell
.\Ensayar-Restauracion.ps1 -Copia '<carpeta de copias>\diarias\<archivo>.dump.age' -IndiceExportacion '<carpeta de exportación>\export-<fecha>.json'
```

2. El script primero **comprueba el aislamiento** y aborta (código 3) si: hay variables `CLOUDINARY_*` o `SARC_DB_*` en el entorno, o el puerto elegido ya está en uso. El destino siempre es un contenedor PostgreSQL 18 creado por el propio script en `localhost` con una contraseña aleatoria.
3. Restaura sin `--clean` y comprueba: última migración y conteos por tabla iguales a los metadatos, auditoría presente, historiales de importación presentes y una muestra de referencias a archivos presente en la exportación de Cloudinary.
4. Imprime un informe con el tiempo total (objetivo de recuperación: 4 h) y el punto de datos recuperado, y **destruye el entorno**.
5. Pegar el informe en el *issue* `ensayo-restauracion` (sin datos de filas).

**Resultado esperado**: `Resultado: APROBADO (n/n comprobaciones)`.

**Criterio para cerrar el *issue***:

- **Aprobado** solo si pasa el **100 %** de las comprobaciones: cerrar el *issue* con la etiqueta `aprobado`.
- Si alguna falla: dejar el *issue* **abierto** con la etiqueta `fallido`, corregir la causa y repetir el ensayo. Un *issue* `pendiente` por cambio crítico se cierra con el ensayo que lo resuelve.
- El flujo `control-ensayos` avisa a los 28 y 31 días sin un ensayo aprobado y si un `pendiente` lleva más de 7 días abierto.

**Si falla el aislamiento o el ensayo**: no insistir con otras credenciales. Anotar el motivo en el *issue* y avisar al responsable.

## Restauración real en producción (excepcional, con autorización y registro)

Solo cuando no hay otra salida (ver la regla de oro de `recuperar.md`). **Sustituye los datos de producción por los de la copia.**

**Requisitos previos**:

- **Autorización explícita registrada** del responsable del producto en un *issue* `alerta-operativa`, que declara **qué datos posteriores a la copia se perderán** (desde `createdAt` de la copia hasta el momento de la restauración).
- Copia elegida (la previa a la publicación o la más reciente verificada) y su descifrado posible en el computador local.

**Pasos**:

1. **Activar el mantenimiento**: `sudo touch /var/lib/sarc/maintenance.flag` y detener el servicio (`sudo systemctl stop sarc`).
2. **Hacer una copia del estado actual** antes de tocar nada: `sudo /usr/local/sbin/sarc-backup manual` y descargarla al computador local.
3. **Restaurar** la copia elegida en la base de producción siguiendo el procedimiento aprobado (la copia está cifrada: se descifra en el computador local y se transfiere por una vía segura a una cuenta administrativa; no se deja sin cifrar en el VPS). Nunca se restaura con `--clean` sobre una base en uso sin la copia del paso 2.
4. **Identificar los datos posteriores a la copia para volver a cargarlos** (FR-040): comparar la copia del estado actual (paso 2) con la restaurada y listar:
   - historiales de importación de consumo, inventario y uptime y reportes emitidos con fecha posterior a `createdAt` de la copia;
   - la ubicación de sus originales en Cloudinary (`original_ref`, `file_ref`) o en la última exportación del computador local.
   Esos datos se vuelven a cargar con las funciones normales de SARC.
5. Iniciar el servicio y ejecutar las **comprobaciones posteriores** completas (`sarc-post-checks`): deben dar `COMPLETADA`.
6. **Retirar el mantenimiento**: `sudo rm /var/lib/sarc/maintenance.flag`.
7. **Registrar**: en el *issue* de autorización, qué copia se usó, hora, qué datos se perdieron y cuáles se volvieron a cargar. Abrir un *issue* `ensayo-restauracion` `pendiente` (cambio crítico).

**Resultado esperado**: servicio activo con los datos de la copia y las comprobaciones posteriores en verde.

## Auto Backup de Contabo (FR-045)

Es una copia **del VPS completo** del proveedor. Sirve para reconstruir el servidor ante un desastre. **Restaurarlo sobrescribe todo el VPS**, incluida la base en el estado del punto elegido: es una restauración real y necesita autorización y registro. No cubre Cloudinary ni el computador local, y no se usa para recuperar solo la base (la consistencia de sus puntos no está documentada). Revisión semanal del panel (ver `avisos.md`).

## Archivos de Cloudinary (FR-041, FR-046)

- **Backup de Cloudinary** permite restaurar archivos eliminados y versiones anteriores desde la consola. Tras comprobar la cuota y una copia real de muestra, habilitar `SARC_STORAGE_CLOUDINARY_BACKUP_UPLOADS=true` en el VPS. SARC pide backup al subir cada archivo de `sarc-prod`, sin cambiar el ajuste global de la cuenta compartida. Ver `pendientes-recursos-reales.md`.
- **Exportación local mensual** (`Exportar-Cloudinary.ps1`, solo lectura sobre Cloudinary): deja copias de los archivos y un índice `export-<fecha>.json`. Sirve como respaldo independiente y para comprobar en el ensayo que las referencias guardadas en la base existen.
- Toda referencia guardada en la base de producción debe existir en Cloudinary (actual o restaurable) o en la exportación.

## Si no puedes continuar

Detente. Una restauración mal hecha destruye datos: avisa al responsable del producto antes de seguir.
