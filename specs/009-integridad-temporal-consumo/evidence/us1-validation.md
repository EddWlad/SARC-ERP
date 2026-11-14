# US1 · Validación (T016)

Fecha: 2026-11-14 · Rama `feat/009-integridad-temporal-consumo` (sin commit).

## Alcance validado

| Requisito | Prueba | Resultado |
|---|---|---|
| FR-002 hora de origen con segundos y desplazamiento por extremo | `SessionParsersTest` (15): 1211 con fin en UTC-05:00, fin sin declaración usa el del inicio, UTC-03:00 admitido, encabezado ilegible/fuera de rango/contradictorio → `SOURCE_OFFSET_INVALID` | 15/15 |
| FR-002 dominio y migración | `EnergySessionTest` 16/16, `SourceOffsetMigrationTest` 3/3, `MigrationUpgradeTest` 1/1 (V020) | OK |
| FR-005/006 límites `[from, to)` día y mes, filtro de fuente sin mover fechas | `ConsumptionApiTest#eachSessionCountsOnTheStartDateDeclaredByItsSourceAlsoInTheConsolidatedView`, `#monthBoundariesAreHalfOpenOnTheSourceStartDate` | 2/2 |
| Concordancia 5/8/13 kWh en total, tendencia y ranking; 0 kWh ≠ desconocida | mismo caso API (`daily` 5/8, `top` 8/5) y `ConsumptionCalculatorTest#trendCountAndRankingAgree…` | OK |
| FR-006 rótulo «Fecha de inicio según la fuente» y corte propio | `ConsumptionPdfRendererTest#dailyTrendNamesTheSourceDateCriterionAndTheCutOff`, `ConsumptionExcelRendererTest#transactionsShowEachEndInItsOwnSourceOffset…` | OK |
| Excel: hora de la fuente con desplazamiento de cada extremo; Wallbox en hora de Ecuador | 1211 `11/11/2026 00:30:00 (UTC+00:00)` → `10/11/2026 21:10:05 (UTC-05:00)`, cuenta el 11/11; W-77 `10/11/2026 22:00:00 (UTC-05:00)`, cuenta el 10/11 | OK |
| D13 rango inicial de mes en curso | `period-selection.spec.ts` (1.º de mes, fin de mes, cambio de mes) | OK |
| Lista, tarjeta, vista previa y diálogo con fin en su desplazamiento | `sessions-page.spec.ts` «SessionsPage source time» (tabla y tarjeta), sin depender de la zona del navegador | OK |
| Tablero identifica su hora de cálculo | `dashboard-page.spec.ts`: «Calculado 21/10/2026 10:00 (hora de Ecuador) · Fecha de inicio según la fuente» | OK |

## Suites

- Frontend: `npm run build` correcto (inicial 347,75 kB, presupuesto 500 kB); `ng test` 88 archivos, 481 pruebas, todas correctas (base 478).
- Backend: ver la sección «Suite completa» al final.

## Decisiones

- La hora de origen se muestra con segundos también para Wallbox (mismo formato en una tabla mixta). FR-003 se mantiene: Wallbox conserva su zona y su atribución; solo cambia el formato.
- La nota de metodología del PDF cita UTC+00:00 y UTC-05:00 como ejemplos, porque el importador admite cualquier desplazamiento válido declarado.
- El contrato `docs/api/openapi.json` y los tipos generados incluyen `sourceEndUtcOffsetMinutes` (sesión y fila) y `deletedAt` (sesión).

## Suite completa

- Backend `./mvnw test`: 898/898 correctas al cerrar US1. Tras US3, 902/902 correctas el 2026-11-14, con 0 fallos, 0 errores y 0 omitidas; los sandboxes de navegador quedan fuera porque son opcionales por propiedad.
