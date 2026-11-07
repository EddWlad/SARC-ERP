# Evidencia SC-015: sin regresiones

**Fecha**: 2026-11-07. Ejecutado en el equipo de desarrollo, sobre contenedores desechables (Testcontainers `postgres:18`).

| Comprobación | Comando | Resultado |
|---|---|---|
| Backend completo (unitarias, ArchUnit, integración, contrato OpenAPI, prueba de actualización y pruebas nuevas de esta feature) | `./mvnw -B -o verify` en `sarc-bakend` | **595 pruebas, 0 fallos, 0 errores, 0 omitidas** · `BUILD SUCCESS` |
| Lint del frontend | `npm run lint` | Todos los archivos pasan |
| Formato del frontend | `npm run format:check` | Estilo Prettier correcto |
| Pruebas del frontend | `npm test -- --watch=false` | **55 archivos, 225 pruebas, 0 fallos** |
| Compilación del frontend | `npm run build` | Correcta; salida en `dist/sarc-frontend/browser` |
| Contrato REST sin cambios | `git diff` sobre `sarc-bakend/docs/api/openapi.json` y `sarc-frontend/src/app/core/api/generated` | **0 líneas de diferencia** |
| Scripts de despliegue | `shellcheck` sobre `deploy/scripts`, `deploy/release` y `.github/scripts` · `bats deploy/tests/` | Limpio · **116 pruebas** en verde (las 9 de Nginx se ejecutaron con Docker en el equipo y se omiten en el contenedor de pruebas sin Docker) |
| Flujos de GitHub | Análisis de YAML y de los scripts `github-script` embebidos | `ci.yml`, `version-candidata.yml`, `publicar.yml`, `sonda-externa.yml` y `control-ensayos.yml` válidos |

Cambios de código de la aplicación de esta feature (los tres previstos): Cloudinary obligatorio en el perfil `prod`, *readiness* con base de datos y patrón de registro con `traceId`, más `build-info` con la versión en `pom.xml`. Ninguna regla de negocio, permiso, importación, indicador, historial ni reporte cambió (FR-050), y no se creó ninguna migración.

## No ejecutado aquí

La ejecución del CI en GitHub (PR verde y rojos, versión candidata, `publicar` con aprobación) requiere publicar en GitHub; esta implementación no hace *commit* ni *push*. Se ejecutará al integrar.
