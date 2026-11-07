# Mantener el entorno

Tareas periódicas y de mantenimiento del VPS de SARC. Cada una indica cómo verificar el resultado y qué hacer si falla.

**Responsable**: responsable técnico. **Requisitos previos**: cuenta personal con `sudo` (ver `acceso-y-exposicion.md`). Antes de cualquier tarea que reinicie servicios, comprobar que no hay una publicación en curso (`/var/lib/sarc/deploy.lock` no debe estar tomado y la última línea de `deploy-history.jsonl` no debe estar `EN_CURSO`).

## Instalar o actualizar los archivos de `deploy/` en el VPS

Los scripts y plantillas del repositorio se instalan con estos nombres (sin extensión para los scripts):

| Archivo del repositorio | Destino en el VPS | Propietario y permisos |
|---|---|---|
| `deploy/scripts/<nombre>.sh` | `/usr/local/sbin/<nombre>` | root:root 0755 |
| `deploy/scripts/lib/common.sh` | `/usr/local/lib/sarc/common.sh` | root:root 0644 |
| `deploy/scripts/sarc-deploy-ssh-wrapper.sh` | `/usr/local/sbin/sarc-deploy-ssh-wrapper` | root:root 0755 |
| `deploy/sudoers/sarc-deploy` | `/etc/sudoers.d/sarc-deploy` (tras `visudo -c -f`) | root:root 0440 |
| `deploy/compose/compose.prod.yaml` | `/opt/sarc/compose/compose.prod.yaml` | root:root 0644 |
| `deploy/nginx/maintenance.html` | `/opt/sarc/maintenance/maintenance.html` | root:root 0644 |
| `deploy/nginx/sarc.conf.template` | `/etc/nginx/conf.d/sarc.conf` (renderizado con la IP) | root:root 0644 |
| `deploy/systemd/*.service`, `*.timer` | `/etc/systemd/system/` | root:root 0644 |
| `deploy/env/*.example` | `/etc/sarc/sarc.env` y `/etc/sarc/verificacion.env` (con valores reales) | `sarc.env` 0640 root:sarc; `verificacion.env` 0600 root |

Paquetes necesarios: Nginx, Docker Engine con Compose, Temurin 25, `age`, `jq`, `unzip`, `openssl`, `curl`, Certbot ≥ 5.4 (snap), `fail2ban` y `unattended-upgrades`. Tras instalar unidades: `sudo systemctl daemon-reload` y habilitar con `sudo systemctl enable --now sarc-heartbeat.timer sarc-check-disk.timer sarc-check-cert.timer`. El temporizador `sarc-backup.timer` se activa antes de la segunda publicación (`publicar.md`).

Las rutas de scripts, el volumen `sarc_pgdata` (`sudo docker volume create sarc_pgdata`) y `/var/lib/sarc` (`sarc:sarc`, 0750) y `/var/backups/sarc` (root, 0750, grupo `sarc-backup` de solo lectura) se crean una sola vez.

Resultado esperado de una actualización: `sudo systemctl list-timers | grep sarc` lista los temporizadores y `shellcheck` del repositorio está limpio.

## Actualizaciones del sistema, Java, Docker y PostgreSQL

1. **Sistema operativo**: `unattended-upgrades` aplica las de seguridad. Los reinicios se planifican en una ventana acordada: avisar, ejecutar `sudo reboot`, y después comprobar `sarc.service`, la base y el certificado.
2. **Java (Temurin) y Docker**: actualizar con el gestor de paquetes en ventana acordada; reiniciar `sarc.service` y comprobar readiness.
3. **PostgreSQL**: solo actualizaciones **menores** de la imagen `postgres:18` (el volumen se conserva). Una versión mayor exige un procedimiento propio con copia previa y ensayo de restauración: no se hace sin decisión del responsable del producto.
4. **Dependencias de la aplicación**: entran por el flujo normal (CI → versión candidata → publicación).
5. **Verificación posterior**: tras cualquier actualización, `sudo /usr/local/sbin/sarc-post-checks <versión actual>` debe dar `COMPLETADA`.

**Si fallan**: no borrar nada; seguir `recuperar.md`. Un servicio que no arranca tras actualizar Java o Docker se revierte con el gestor de paquetes (versión anterior) o se corrige hacia adelante.

## Cambio de IP o incorporación de un dominio

Ver `certificado.md` («Incorporar un dominio después»). Es un cambio de configuración sin pérdida de datos: DNS, certificado nuevo, `server_name`, `SARC_PUBLIC_URL`, `SARC_PUBLIC_IP` y las comprobaciones posteriores.

## Disco lleno (aviso `disco`, uso > 80 %)

**Se puede liberar sin tocar datos**:

- Copias del VPS que **ya tienen recibo** del computador local (carpeta `/var/backups/sarc`, más antiguas que la retención de 7 días).
- Registros rotados antiguos (`journalctl --vacuum-time=30d`, archivos `.gz` de Nginx anteriores a 30 días).
- Versiones antiguas más allá de las 5 últimas en `/opt/sarc/releases` (nunca la actual ni la anterior).
- Imágenes de Docker sin uso: `sudo docker image prune`.

**Nunca se borra**:

- El volumen `sarc_pgdata` (la base de datos).
- `/etc/sarc` (configuración y secretos).
- Copias **sin recibo** del computador local.
- La tabla de auditoría de SARC.

Si tras liberar el uso sigue alto, avisar al responsable: puede hacer falta más disco.

## Reemplazo del computador local

El computador local guarda la clave privada de las copias; reemplazarlo es un cambio crítico.

1. Designar el equipo nuevo (ver `decisiones-previas.md`: sistema operativo, Docker, disco cifrado, espacio).
2. Generar un par de claves `age` en el equipo nuevo y guardar el respaldo **offline** de la clave privada con el responsable (la custodia offline de la clave es obligatoria: sin ella las copias no se pueden descifrar).
3. Instalar la clave pública nueva en `/etc/sarc/backup-recipient.pub` (las copias nuevas se cifran para el equipo nuevo) y generar una copia manual: `sudo /usr/local/sbin/sarc-backup manual`.
4. Instalar la clave SSH de `sarc-backup` del equipo nuevo y **revocar la del anterior** (comprobar que ya no ingresa).
5. Copiar al equipo nuevo las copias anteriores si se necesitan (se descifran con la clave antigua) y registrar la tarea programada (`Registrar-Tarea.ps1`).
6. Abrir un *issue* `ensayo-restauracion` `pendiente` (cambio crítico) y ejecutar el ensayo (`restaurar.md`).
7. Confirmar el latido `descarga-local` y siete días de recibos.

## Rutinas

| Frecuencia | Tarea | Dónde se describe |
|---|---|---|
| Semanal | Revisar el panel de Auto Backup de Contabo | `avisos.md` |
| Mensual | Ensayo de restauración | `restaurar.md` |
| Mensual | Exportación de Cloudinary | `restaurar.md` |
| Mensual | Comprobar que `sonda-externa` y `control-ensayos` siguen activos | `avisos.md` |
| Anual o ante exposición | Rotar secretos | `entornos-y-secretos.md` |
| Tras cambios de personas | Prueba de recepción de avisos | `avisos.md`, `publicar.md` |

## Si no puedes continuar

Detén la tarea y avisa al responsable del producto. No improvises con datos ni secretos reales.
