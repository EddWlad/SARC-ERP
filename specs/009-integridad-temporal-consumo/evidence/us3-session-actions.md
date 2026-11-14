# US3 · Acciones por sesión (T033)

Fecha: 2026-11-14 · Rama `feat/009-integridad-temporal-consumo` (sin commit).

## Contrato

| Operación | Ruta | Roles | Errores |
|---|---|---|---|
| Detalle | `GET /api/v1/energy/sessions/{id}` | Todos los lectores de Consumo | 404 si no existe; para lectura no operativa también si está dada de baja o no está validada |
| Editar | `PATCH /api/v1/energy/sessions/{id}` | Administrador, Analista | 400 sin motivo o con fecha ilegible, 403, 404 (sesión o equipo), 409 versión obsoleta, 422 `SESSION_UNCHANGED`, `SESSION_DELETED`, `INVALID_SESSION` |
| Eliminar (baja lógica) | `POST /api/v1/energy/sessions/{id}/deletion` | Administrador, Analista | 400 sin motivo, 403, 404, 409; repetir la baja devuelve la sesión ya dada de baja (idempotente) |
| Bajas | `GET /api/v1/energy/sessions?deleted=true` | Lectura operativa | 403 para el resto |

Semántica de `PATCH`: un campo ausente conserva su valor; `null` explícito significa «no informado» (o «sin vincular» en `equipmentId`). Cada fecha llega con su desplazamiento, que pasa a ser el de origen de ese extremo y fija la fecha de conteo.

## Matriz de permisos (probada)

| Acción | Administrador / Analista | Gerente (consulta) |
|---|---|---|
| Ver detalle de una sesión validada | Sí, con revisiones, motivo y autor de la baja | Sí, sin revisiones ni datos de la baja |
| Ver detalle de una baja o de una sesión no validada | Sí | 404 |
| Listar bajas | Sí | 403 |
| Editar | Sí, con motivo | 403 |
| Eliminar | Sí, con motivo | 403 |
| Botones en la interfaz | «Ver», «Editar», «Eliminar» en tabla (menú) y tarjeta | Solo «Ver» |

Pruebas: `ConsumptionSessionActionsApiTest#onlyEditorsMutateAndManagersOnlyReadValidatedActiveSessions`, `sessions-page.spec.ts` «SessionsPage session actions (Feature 009, US3)» (tabla y tarjeta, editor y consulta).

## Escenario de cortes 13 → 14 kWh

`ConsumptionSessionActionsApiTest#anEditorEditsWithAReasonAndTheOfficialFiguresAndHistoryFollow`:

1. Dos sesiones del 21/10 (5 y 8 kWh): el resumen oficial da 2 sesiones y 13 kWh.
2. Editar la de 8 kWh a 9 con motivo: revisión 2, estado `VALIDATED`, resumen 14 kWh; el detalle lista la revisión 1 (`EDIT`, 8 kWh, motivo) y se audita `ENERGY_SESSION_EDITED`.
3. La versión anterior recibe 409 `CONCURRENT_MODIFICATION`; sin motivo, 400; una lectura que redondea al mismo valor (9,0004), 422 `SESSION_UNCHANGED`.
4. `energyKwh: null` deja la energía desconocida, distinta de 0 kWh: 2 sesiones, 5 kWh conocidos y 1 sin energía.
5. Mover el inicio después del fin vigente sin mover el fin da 422 `INVALID_SESSION`; ambos juntos, cada uno en su desplazamiento (UTC y UTC-05:00), cuentan el 22/10.
6. Más de 1.000 kWh pasa a `ENERGY_REVIEW` y sale del total oficial; `equipmentId: null` deja la sesión `PENDING_LINK`.

## Baja y reimportación

- `#aDeletionLeavesTheFiguresKeepsTheRecordAndIsIdempotent`: la baja sale del resumen y de la lista oficial, aparece con `deleted=true`, conserva el registro con motivo y una revisión `DELETION`, y se audita una vez aunque se repita. Una baja rechaza «Marcar prueba» y la edición con `SESSION_DELETED`.
- `#aReimportNeitherReactivatesADeletionNorOverwritesAnEditMadeAfterThePreview`: reimportar una baja la clasifica `DELETED` (lote con `deletedRows = 1`) y confirmar no la reactiva. Un lote preparado y aprobado antes de una edición se omite (`SKIPPED`) y la cifra editada se mantiene.
- Lotes antiguos sin revisión registrada en la fila: la aplicación no corrige una sesión existente sin la clasificación `MODIFIED` aprobada y nunca aplica sobre una baja (`ImportProcessor.applyRow`).
- Las bajas quedan fuera del predicado común (`SessionPredicates`), que usan la lista, el resumen, la tendencia, el ranking y las exportaciones, y de `findPendingLink`. La búsqueda por identidad sí las incluye, para que una reimportación las reconozca.

## Interfaz

- Diálogo `session-action-dialog` (Ver, Editar, Eliminar): hora de la fuente con segundos y el desplazamiento de cada extremo, fecha de conteo, revisiones con su tipo y motivo, motivo obligatorio (máximo 500), solo se envían los campos cambiados y un campo vaciado se envía como «no informado».
- Conflicto 409: el diálogo conserva lo escrito y ofrece «Recargar sesiones» en lugar de reintentar.
- `session-action-dialog.spec.ts`: 6 pruebas. `sessions-page.spec.ts`: acciones según rol en tabla y tarjeta.
- Navegador (sandbox `Feature009BrowserSandbox`, Analista): con la acción Editar, la sesión 1211 pasa de 8 a 9 kWh y el resumen de 119,50 a 120,50 kWh; el detalle lista la revisión anterior. Eliminar se canceló una vez y luego se confirmó con motivo en QA-13: el resumen bajó a 15 sesiones y 109,50 kWh. Cambios sin guardar piden confirmación; el menú y los diálogos responden al teclado. Recorrido a 1440 px en tema oscuro, 768 px en tema oscuro y 360 px en tema claro (tarjeta). Detalle en [us4-results.md](us4-results.md).
- Los campos de fecha muestran la zona de su extremo («Inicio (UTC+00:00)», «Término (UTC-05:00)»), porque cada extremo se escribe en el desplazamiento que declaró la fuente.

## Suites

- Backend: `ConsumptionSessionActionsApiTest` 4/4; suite completa 902/902.
- Frontend: 90 archivos y 506 pruebas tras US3 y US4.

## Decisiones

- Las pruebas de T024 (reimportación y lotes preparados antes de una edición o baja) quedaron en `ConsumptionSessionActionsApiTest`, junto al resto de las acciones, en lugar de ampliar `ConsumptionApiTest`.

- La reimportación de una baja se clasifica en un estado nuevo, `DELETED` («No se tocan»). Así el lote lo muestra en lugar de clasificarlo como sin cambios.
- `EditEnergySessionRequest` usa un deserializador propio: Jackson 3 entrega `Optional.empty()` para un campo ausente y ajusta `OffsetDateTime` a UTC, lo que borraba la diferencia entre ausente y nulo y el desplazamiento escrito.
- Las revisiones del detalle no se paginan: llegan todas, de la más reciente a la más antigua.
