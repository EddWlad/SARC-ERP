# Puertas finales (T126)

Ejecutadas el 2026-11-01, tras todas las correcciones.

| Puerta | Resultado |
|---|---|
| `./mvnw test` | 511 pruebas, 0 fallos, 0 errores, 0 omitidas (repetida el 2026-11-02 tras el informe semanal tal como se envía, V014) |
| `npm test` | 203 pruebas en 49 archivos, todas pasan (repetida el 2026-11-02) |
| `npm run lint` | Sin hallazgos |
| `npm run format:check` | Avisa 64 archivos por finales de línea CRLF del checkout en Windows; con `--end-of-line auto`, todo conforme |
| `npm run build` | Correcto; inicial 341,77 kB, 95,09 kB comprimido, bajo el presupuesto de 500 kB |
| Hashes V001–V012 | Iguales a `baseline.md`; V013 es nueva |

Una falla de la primera pasada, `UptimeExcelRendererTest`, dependía del separador decimal del locale por defecto. Se corrigió la prueba; las celdas de producción son numéricas.

## Revalidación final de cierre — 2026-11-04

| Puerta | Resultado |
|---|---|
| Maven completo | **517 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**, 5 min 26 s |
| Regresiones enfocadas QA | 51 aprobadas después de reproducir los fallos |
| Angular completo | **205 pruebas en 50 archivos**, todas aprobadas con maxWorkers=2 temporal |
| Lint | Correcto |
| Prettier --end-of-line auto | Correcto; format:check estricto mantiene la diferencia CRLF local documentada |
| Build | Correcto, inicial **341,77 kB**, transferencia estimada 95,02 kB |
| Hashes V001–V012 | Todos iguales al baseline |
| git diff --check | Sin errores |

Sin migración de BD dev ni proveedor real durante estas pruebas. Los logs locales de cierre se conservan en target/ y archivos .log ignorados; las cifras publicadas distinguen validación técnica de T124 humana pendiente. Detalle de correcciones y límites: [closure-qa.md](closure-qa.md). Los cambios posteriores corresponden a Feature-006 y no forman parte de esta validación.
