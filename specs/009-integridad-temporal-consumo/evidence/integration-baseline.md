# Evidencia — Base de integración (T001–T002)

**Fecha**: 2026-11-13 (hora de Ecuador).

## T001 — Checkout y referencia

| Dato | Valor |
| --- | --- |
| Rama de trabajo | `feat/009-integridad-temporal-consumo`, creada desde `docs/publicacion-hotfix-009` (`2dd7dac`), que parte de `main` en `793e92f` |
| Versión en producción | `2026.11.14-41-793e92f` (hotfix Fase H, `COMPLETADA`) |
| Versión de regresión de Feature 008 | `2026.11.13-35-9937edb` (V018), incluida en `main` |
| Historial Flyway | V001–V019 aplicadas en producción. V019 = `consumo_desplazamiento_origen` (hotfix). La migración de esta entrega es `V020` |
| Archivos compartidos con Feature 008 | `shared/ui/chart/chart-view.ts` (solo si US4 lo exige), `reporting` (archivo de informes), `MigrationUpgradeTest` |
| Selección de feature para speckit | `check-prerequisites.ps1` devuelve la selección persistida (008). Se trabaja con `specs/009-integridad-temporal-consumo` de forma explícita; no se modificó la selección persistida |

## T002 — Resultados base

Los comandos de `.github/workflows/ci.yml` corrieron sobre el commit del hotfix (`8c54b7c`, mismo árbol de código que `793e92f`):

| Comando | Resultado |
| --- | --- |
| `./mvnw test` (Testcontainers PG18) | 889 pruebas, 0 fallos, 0 errores |
| `npm test` | 88 archivos, 478 pruebas, todas correctas |
| `npm run build` | Compilación correcta |
| CI del PR #12 (6 comprobaciones) | Todas en `success` |

Limitaciones: no hay `gh` en el equipo local. El PR y los flujos se manejan desde el navegador y el estado se lee de la API pública de GitHub.
