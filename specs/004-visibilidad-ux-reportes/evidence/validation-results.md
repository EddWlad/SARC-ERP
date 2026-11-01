# Validación 004

Fecha 2026-11-01. Base `7354d6e`, cambios de 004 sobre la rama `codex/004-visibilidad-ux-reportes`. Sin BD dev: PostgreSQL 18 desechable por Testcontainers.

## Puertas (T062)

| Comando | Resultado |
|---|---|
| `./mvnw verify` (sarc-bakend) | exit 0, 266 pruebas, 0 fallos. Incluye ArchUnit, OpenApiContractTest, regresiones 001–003 y V011 |
| `npm run api:types` | exit 0, tipos sin diferencias con los versionados |
| `npm test -- --watch=false` | exit 0, 163 pruebas |
| `npm run lint` | exit 0 |
| `npm run build` | exit 0, inicial 341,67 kB, presupuesto 500 kB sin cambiar |
| Hashes V001–V010 contra `baseline-migrations.json` | 10 de 10 iguales; sin diferencias en Git. V011 es nueva |

Base previa: backend 239, frontend 132 pruebas, inicial 341,56 kB.

## Revisión de código

Revisión del diff completo antes de T062. Corregido: cuerpo de descarga sin timeout, descarga sin comprobar SHA-256, texto de equipo no elegido ignorado en la emisión, código muerto y duplicado en errores. Java de 004 formateado al estilo del proyecto. Sin aplicar: lista de fuentes duplicada en `ReportPublication` (el kernel no puede importar `SessionSource`) y búfer de hasta 100 MB por descarga, latente con tamaños reales.

## Trazabilidad

| Requisito | Evidencia | Estado |
|---|---|---|
| FR-001, 002, 004, 006, SC-001, SC-002 | `visibility-results.md`, QA01–QA03 | Cumplido |
| FR-003, 016, 017, SC-007, SC-008 | `pdf-results.md`, `excel-results.md`, `private-storage-results.md`, QA04, QA10 | Cumplido; entrega real con proveedor en PDF (T066) |
| FR-005, SC-009 | Regresiones, QA14, hashes | Cumplido; QA13 PASS |
| FR-007, 008, 009, SC-003 | QA05–QA08, `IssuedReportAccessApiTest` | Cumplido en SARC; caducidad de URL del proveedor verificada (T066) |
| FR-010–015, SC-004, 005, 006 | `visual-audit.md`, `visual-matrix.md`, `accessibility-results.md` | Cumplido con excepción aceptada: lector de pantalla y dispositivo físico sin probar, capturas previas solo de V-08. Sin incumplimientos confirmados; no se afirma AA completa |
| FR-016, 018, 019 | `pdf-results.md`, QA09, QA10 | Cumplido |
| FR-020 | `excel-evaluation.md`, `excel-windows-results.md`, QA11 | Cumplido: NATIVO, abierto en Excel Windows sin reparación |
| FR-021 | QA15, límites 366 días y 100.000 filas | Cumplido |
| FR-022, SC-010 | `performance-results.md` | Cumplido en portátil tras calentar, tablero con margen corto; primera carga de sesiones excede la meta. VPS sin medir. 500 kB sí |

## Puertas finales, 2026-11-01

| Comando | Resultado |
|---|---|
| `./mvnw verify` | exit 0, 270 pruebas, 0 fallos. Incluye V012 |
| `npm test -- --watch=false` | exit 0, 164 pruebas |
| `npm run lint` | exit 0 |
| `npm run build` | exit 0, inicial 341,67 kB |
| Migraciones contra base `7354d6e` | V001–V010 sin cambios; solo se añaden V011 y V012 |

## Constitución

I, especificación: cambios de producto dentro de spec, plan y tasks. II, reglas de negocio: visibilidad y revisión cubiertas por pruebas; D01–D14 sin cambio de código, por regresiones 001–003. III, migraciones: inmutables, ver tabla anterior.

## Límites al cierre

- SC-006: no se afirma WCAG 2.2 AA completa; ver excepción.
- SC-010: solo portátil, sin VPS. Primera carga de sesiones 4,9 s tras arrancar y tablero a 4,95 s de 5 s.
- Proveedor: cuenta compartida con MedicApp, no QA dedicada; sin ensayo de cuerpo detenido.
- Sin commit de archivos de herramientas locales.
