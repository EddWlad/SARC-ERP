# Evidencia de ensayo en sandbox (T071)

**2026-11-07 · Estado parcial.** Se probaron el CI del PR y la recuperación en Ubuntu 24.04 sobre WSL2, con systemd, un Docker dedicado, PostgreSQL 18 y datos sintéticos. **T071 sigue abierta** para los recorridos de la guía que requieren otras cuentas, una persona ajena o una publicación de ensayo.

Leyenda: **Hecho (local)** = verificado con pruebas o ensayos en el equipo; **Simulado** = verificado con dobles de curl, docker, systemctl y scripts hermanos (prueba la lógica, no el servidor real); **Pendiente** = requiere GitHub, VM o recursos reales.

| # | Escenario | Estado | Evidencia |
|---|---|---|---|
| 1 | CI verde | Hecho (GitHub) | Los seis trabajos pasaron en el commit final del [PR #2](https://github.com/EddWlad/SARC-ERP/actions/runs/42378895925). Maven verificó 597 pruebas en local. |
| 2 | CI rojo | Parcial | Probados con bats: migración modificada (`check-migrations.bats`), dependencia alta (`ci-scripts.bats`), secreto (gitleaks local). PRs de ensayo pendientes |
| 2b | Aviso de CI | Pendiente (GitHub) | `avisar-fallo`: YAML y JavaScript válidos; sin ejecutar |
| 3 | Versión candidata | Parcial | Manifiesto y sumas con `build-manifest.bats`; `build-info` del JAR verificado con `-Dsarc.version`; Release real pendiente |
| 4 | Sin aprobación | Pendiente (GitHub) | `environment: production` configurado en `publicar.yml` |
| 5 | Con aprobación | Simulado | Flujo completo de `sarc-deploy` con dobles (`sarc-deploy-flow.bats`) |
| 5b | Primera instalación + verificar | Simulado | `EN_CURSO`, rechazo con 10, `COMPLETADA`, `FALLIDA` con mantenimiento (bats) |
| 6 | Concurrencia | Simulado | Bloqueo `flock` → código 10 (bats) |
| 7 | Configuración ausente | Simulado | Código 12 sin valores (bats); arranque `prod` sin Cloudinary falla (`StorageConfigTest`) |
| 8 | Copia vieja | Simulado | Código 14 (`backup-freshness.bats`) |
| 8b | Prueba de recepción vencida | Simulado | Código 18 (`schema-and-alerts.bats`) |
| 9 | Migración fallida | Simulado | Código 20 con mantenimiento y sin reactivar la anterior (bats); migración fallida real en VM pendiente |
| 10 | Copia y descarga | Parcial | `sarc-backup.sh manual` creó el volcado cifrado de la base WSL y su SHA-256 coincidió. Siguen pendientes la descarga Windows programada y el latido de recepción. |
| 11 | Computador local apagado | Pendiente | Requiere Healthchecks.io |
| 12 | Restauración aislada | Parcial | En WSL se descifró con `age` y se restauró en otro PostgreSQL 18 sin red. Coincidieron 29 conteos de tablas y Flyway V016. Duró 20 s. `Ensayar-Restauracion.ps1` restauró la misma copia de QA en Windows y confirmó auditoría e historiales. Sigue pendiente contrastar referencias con un índice real de archivos. |
| 13 | Archivos de Cloudinary | Parcial | El proveedor confirmó respaldo de dos muestras privadas en `sarc-qa/backup`. Se eliminaron y se comprobó la ausencia de sus originales y versiones. Sigue pendiente el recorrido de exportación local y recuperación del archivo. |
| 13b | Control de ensayos | Pendiente (GitHub) | `control-ensayos.yml`: YAML y JavaScript válidos |
| 14 | Exposición | Parcial | Plantilla de Nginx verificada en Docker. HTTPS válido para la CA de ensayo en WSL, backend y PostgreSQL en loopback. Sigue pendiente el escaneo externo del VPS. |
| 15 | Regresión | **Hecho (local)** | `regresion.md` |
| 16 | Persona ajena | Pendiente | Requiere a una persona sin contexto |

## Observaciones del ensayo

- Tras `SIGKILL`, systemd reinició Java y Nginx. Desde la espera posterior al reinicio del servicio, HTTPS y readiness respondieron en 48 s y 1 s respectivamente. Docker reinició PostgreSQL tras `pg_ctl -m immediate stop`.
- Tras detener y arrancar la distribución WSL, HTTPS, Angular y readiness respondieron en 29 s de sondeo. Docker, Nginx y Java arrancaron habilitados. Se conservaron un administrador, cinco roles de catálogo y 16 migraciones, con cero equipos.
- El resumen de consumo devolvió 422 al usar el mismo inicio y fin. El chequeo de publicación ahora usa un período de un día con fecha final exclusiva. El doble de la API en `post-checks.bats` reproduce el rechazo de un período vacío.
- Tras V017, una base nueva con perfil `prod` inicia con un administrador, los cinco roles y el catálogo de conectores vacío. La prueba de migración con un equipo y un conector previos conservó ambos.
- El `flywayChecksum` que calcula `build-manifest.sh` se contrastó con el que Flyway real guarda en `flyway_schema_history` sobre las migraciones V001 y V016 (coinciden), de modo que `sarc-schema-check` puede comparar el historial con el manifiesto.
- Un mismo escaneo de secretos reveló 6 falsos positivos preexistentes; ver `secretos.md`.
- Las pruebas de Nginx necesitan Docker y `openssl` en la máquina que las ejecuta; en CI (`ubuntu-latest`) están disponibles.
