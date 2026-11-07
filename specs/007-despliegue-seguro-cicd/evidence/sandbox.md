# Evidencia de ensayo en sandbox (T071)

**Fecha**: 2026-11-07. **Estado: PARCIAL.** Los escenarios 1–16 de `quickstart.md` suponen GitHub (PR, Release, aprobaciones), una VM Ubuntu desechable, cuentas reales o una persona ajena. En esta implementación no hubo *push*, ni VM, ni cuentas: se ensayó lo que puede hacerse con Docker en el equipo, con datos sintéticos y sin secretos. **T071 sigue abierta.**

Leyenda: **Hecho (local)** = verificado con pruebas o ensayos en el equipo; **Simulado** = verificado con dobles de curl, docker, systemctl y scripts hermanos (prueba la lógica, no el servidor real); **Pendiente** = requiere GitHub, VM o recursos reales.

| # | Escenario | Estado | Evidencia |
|---|---|---|---|
| 1 | CI verde | Pendiente (GitHub) | Equivalente local: `./mvnw verify` 595 pruebas, frontend lint/test/build (`regresion.md`) |
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
| 10 | Copia y descarga | Parcial | `sarc-backup.sh` con age real (`sarc-backup.bats`); scripts PowerShell con sintaxis verificada, sin ejecutar en Windows |
| 11 | Computador local apagado | Pendiente | Requiere Healthchecks.io |
| 12 | Restauración aislada | Pendiente | `Ensayar-Restauracion.ps1` sin ejecutar (requiere `age.exe` y una copia real) |
| 13 | Archivos de Cloudinary | Pendiente | Requiere carpeta `sarc-test` y clave de ensayo |
| 13b | Control de ensayos | Pendiente (GitHub) | `control-ensayos.yml`: YAML y JavaScript válidos |
| 14 | Exposición | Parcial | Plantilla de Nginx en contenedor: solo readiness, 404 en el resto, 301, 429 en el ingreso, mantenimiento 503 (`nginx-config.bats`, 9/9); escaneo de puertos en VM pendiente |
| 15 | Regresión | **Hecho (local)** | `regresion.md` |
| 16 | Persona ajena | Pendiente | Requiere a una persona sin contexto |

## Observaciones del ensayo

- El `flywayChecksum` que calcula `build-manifest.sh` se contrastó con el que Flyway real guarda en `flyway_schema_history` sobre las migraciones V001 y V016 (coinciden), de modo que `sarc-schema-check` puede comparar el historial con el manifiesto.
- Un mismo escaneo de secretos reveló 6 falsos positivos preexistentes; ver `secretos.md`.
- Las pruebas de Nginx necesitan Docker y `openssl` en la máquina que las ejecuta; en CI (`ubuntu-latest`) están disponibles.
