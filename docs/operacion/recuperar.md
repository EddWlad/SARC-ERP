# Recuperar el servicio

Qué hacer cuando SARC deja de funcionar o una publicación falla. Está escrito para quien no participó en el desarrollo: sigue los pasos en orden y, ante cualquier duda, **detente y avisa al responsable**.

**Responsable**: responsable técnico. **Requisitos previos**: cuenta personal con `sudo` en el VPS (ver `acceso-y-exposicion.md`) y acceso de lectura al repositorio de GitHub.

## Regla de oro (FR-044)

**Si ya se aceptaron datos nuevos, no se revierte ni se restaura nada sin una decisión explícita registrada.** La decisión declara por escrito qué datos se perderían (desde la hora de la copia hasta el momento) y la toma el responsable del producto. La preferencia es **corregir hacia adelante** con una versión nueva. Restaurar es el último recurso y es una restauración real (ver `restaurar.md`).

## 1. Identificar qué pasó

1. Mirar el resumen de la ejecución de **Publicar** en GitHub (código y paso).
2. En el VPS, leer la última línea del registro:

```bash
sudo tail -n 1 /var/lib/sarc/deploy-history.jsonl | jq .
```

   Campos útiles: `result`, `failedStep`, `migrationsApplied` (migraciones aplicadas en ese intento) y `preDeployBackup` (copia previa).
3. Saber si producción está en mantenimiento: existe el archivo `/var/lib/sarc/maintenance.flag`.

## 2. Síntomas y acciones por tipo de fallo

| Síntoma | Qué indica | Acción |
|---|---|---|
| Publicación con código **2** o **10–18** | Nada cambió en producción | Corregir la causa según `publicar.md` y repetir la publicación |
| Código **20** (readiness no llegó a `UP`) | Arranque o migración fallida; producción en mantenimiento | Ver «Migración fallida» |
| Código **21** (comprobación posterior fallida) | La nueva versión arrancó pero algo no pasa; producción en mantenimiento | Ver «Comprobación posterior fallida» |
| `verificar` fallido tras la primera instalación | La primera instalación quedó `FALLIDA` con el mantenimiento activo | Ver «Primera instalación fallida» |
| **Servicio caído** (aviso `servicio` o `sonda-externa`) | `sarc.service` detenido o error de la aplicación | Ver «Servicio caído» |
| **VPS caído** (falta el latido `vps-latido`) | Servidor apagado o sin red | Ver «VPS caído» |
| **Almacenamiento no disponible** (subidas responden 503) | Credenciales de Cloudinary erróneas o servicio de Cloudinary caído | Ver «Almacenamiento no disponible» |
| **Certificado** (aviso `certificado` o conexión no segura) | Renovación fallida | Ver `certificado.md` |

## 3. Corregir hacia adelante o recuperar

Decidir mirando `migrationsApplied` de la publicación fallida:

- **`migrationsApplied` vacío** (la publicación no cambió la base): se puede **volver a la versión anterior** cambiando el enlace `current` (ver abajo). No hay pérdida de datos.
- **`migrationsApplied` con migraciones** (la base cambió): la versión anterior puede no entender el esquema. Opciones, en este orden de preferencia:
  1. **Corregir hacia adelante**: una migración nueva (`V017` o posterior, nunca editar las aplicadas) y una versión nueva, publicadas con el proceso normal.
  2. **Restaurar la copia previa** (`preDeployBackup`): es una restauración real y exige decisión registrada y autorización (ver `restaurar.md`). Como el servicio estaba en mantenimiento durante la publicación, normalmente no hay datos nuevos que perder, pero hay que comprobarlo.

### Volver a la versión anterior (solo si `migrationsApplied` está vacío)

```bash
ls -1 /opt/sarc/releases
sudo ln -sfn /opt/sarc/releases/<versión anterior> /opt/sarc/current.nuevo
sudo mv -T /opt/sarc/current.nuevo /opt/sarc/current
sudo systemctl restart sarc
```

Resultado esperado: `curl -s http://127.0.0.1:8080/actuator/health/readiness` responde `{"status":"UP"}`. Después retirar el mantenimiento (`sudo rm /var/lib/sarc/maintenance.flag`) y registrar lo hecho en el *issue* `alerta-operativa` correspondiente.

## Migración fallida

1. No reiniciar en bucle. Anotar la última migración aplicada: la publicación la informa en su salida, o consultar `flyway_schema_history` (ver `mantener.md`).
2. PostgreSQL revierte la migración que falló; las anteriores del mismo lote pueden haber quedado aplicadas.
3. Elegir según la sección 3. Si `migrationsApplied` no está vacío, **no** se vuelve a la versión anterior.

## Comprobación posterior fallida

1. Leer qué comprobación falló en el resumen (`comprobacion <nombre> FALLO`).
2. Si es una causa externa y la aplicación está sana (por ejemplo la cuenta de verificación), corregirla y ejecutar `sarc-post-checks` en el VPS: `sudo /usr/local/sbin/sarc-post-checks <versión>`.
3. Si es un error de la versión, corregir hacia adelante o recuperar según la sección 3.

## Primera instalación fallida

La base ya tiene tablas de SARC, así que `--primera-instalacion` se rechaza. Como todavía no hay datos reales, la recuperación es **manual, fuera de los scripts y con registro**. Solo si el responsable del producto confirma **por escrito en el *issue* `alerta-operativa`** que no hay datos de usuarios, y **nunca después de una publicación `COMPLETADA`**:

1. Detener el servicio: `sudo systemctl stop sarc`.
2. Recrear el volumen de la base (acción manual excepcional; las copias no se tocan):

```bash
cd /opt/sarc/compose
sudo docker compose --env-file /etc/sarc/sarc.env -p sarc -f compose.prod.yaml stop postgres
sudo docker compose --env-file /etc/sarc/sarc.env -p sarc -f compose.prod.yaml rm -f postgres
sudo docker volume rm sarc_pgdata && sudo docker volume create sarc_pgdata
```

3. Volver a definir `SARC_BOOTSTRAP_ADMIN_*` en `/etc/sarc/sarc.env` si ya se habían retirado.
4. Si quedó un reporte de verificación huérfano en `sarc-prod`, **anotarlo en el registro sin borrarlo**.
5. Repetir la primera instalación (`publicar.md`, sección 5).

## Servicio caído

1. Estado y registros: `sudo systemctl status sarc` y `sudo journalctl -u sarc -n 100 --no-pager` (los registros no llevan secretos).
2. ¿La base responde? `sudo docker compose --env-file /etc/sarc/sarc.env -p sarc -f /opt/sarc/compose/compose.prod.yaml ps`. Si está detenida, iniciarla: `... up -d postgres`.
3. Reiniciar: `sudo systemctl restart sarc` y esperar `UP` en readiness.
4. Si el error es de configuración (variable ausente), el registro nombra la variable; corregir `/etc/sarc/sarc.env`.
5. Si sigue caído, avisar al responsable; no borrar datos ni volúmenes.

## VPS caído

1. Comprobar el estado del servidor en el panel de Contabo y reiniciarlo desde allí si está apagado.
2. Cuando vuelva, comprobar `sarc.service`, la base y el certificado (renovación pendiente por la vigencia corta).
3. Si el servidor no es recuperable, existe el **Auto Backup de Contabo**: restaurarlo **sobrescribe el VPS completo** y es una restauración real (`restaurar.md`).

## Almacenamiento no disponible

Las subidas y descargas de archivos responden 503 sin afectar al resto. Comprobar el estado de Cloudinary y las credenciales de `/etc/sarc/sarc.env`. Los archivos existentes siguen protegidos por el Backup de Cloudinary y la exportación del computador local (`restaurar.md`).

## Después de recuperar

1. Ejecutar las comprobaciones posteriores y confirmar `COMPLETADA`.
2. Cerrar el *issue* `alerta-operativa` con un resumen **sin valores secretos**.
3. Si hubo restauración real, abrir un *issue* `ensayo-restauracion` `pendiente`.

## Si no puedes continuar

Detente y avisa al responsable del producto. Mejor un servicio en mantenimiento que datos perdidos.
