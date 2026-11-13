# Contrato REST: Intermitencias

Todas las rutas comienzan con `/api/v1/intermittency` y requieren sesión. Los errores se devuelven como `application/problem+json` con un `code` estable (lo hace `GlobalExceptionHandler`). Las fechas se escriben en ISO 8601 con desfase, por ejemplo `2026-10-15T23:50:00-05:00`. Los días usan el formato `yyyy-MM-dd` y los rangos tienen el fin exclusivo. El contrato definitivo se genera en `docs/api/openapi.json` (research R14).

**Roles**:
- **E**: editores, es decir, ADMINISTRADOR o ANALISTA.
- **T**: cualquier usuario autenticado.
- **D**: quienes pueden ver la disponibilidad, `IntermittencyPermissions.AVAILABILITY`; hoy son ADMINISTRADOR o ANALISTA.

## Puntos KIA

| Método y ruta | Rol | Descripción |
|---------------|-----|-------------|
| `GET /points?query=&limit=20` | T | Busca puntos de carga KIA por código o nombre, para la captura (FR-019) y para el filtro por punto del dashboard, que usan todos los roles (FR-032). Devuelve `[{id, name, assignedCode, usageStatus, adminStatus}]`. El código ya se muestra a todos los roles en el Top 10 (FR-034) |
| `POST /points/{equipmentId}/test` | E | `{from, to, justification}`: marca como TEST las ventanas CONFIRMED o REVIEW del punto en ese rango. Es una operación sobre el rango, no una marca permanente del punto (FR-016). Devuelve la cantidad de ventanas afectadas y la cantidad de ventanas OPEN del rango que quedaron sin marcar |
| `POST /points/{equipmentId}/untest` | E | `{from, to, justification}`: quita la marca TEST de las ventanas del punto en ese rango; cada una vuelve a CONFIRMED o REVIEW según las validaciones (FR-016). Devuelve la cantidad de ventanas afectadas |

## Ventanas

| Método y ruta | Rol | Descripción |
|---------------|-----|-------------|
| `POST /windows` | E | Captura de un punto y un día: `{equipmentId, windows:[{startAt, endAt?, comment?}]}`. Es atómica: si una ventana falla, no se guarda ninguna, y la respuesta trae el error de cada una. Devuelve las ventanas creadas con su estado (CONFIRMED, REVIEW u OPEN) y el total del día (FR-019 a FR-021b) |
| `GET /windows?from&to&equipmentIds&states&page&size` | T | Historial paginado. Quien no es editor recibe solo CONFIRMED, sin `reviewReasons` ni la procedencia del lote (FR-005) |
| `GET /windows/{id}` | T, revisiones solo E | Detalle. El campo `revisions[]` (autor, momento, cambio y justificación) es nulo para quien no es editor. Si la ventana no está CONFIRMED (OPEN, REVIEW, TEST o ANNULLED), quien no es editor recibe 404, igual que si no existiera, para no revelar detalle operativo (FR-005) |
| `PATCH /windows/{id}` | E | `{version, startAt?, endAt?, comment?, justification}`: corrección (FR-017) |
| `POST /windows/{id}/close` | E | `{version, endAt}`: deja la ventana en CONFIRMED o en REVIEW si dura 240 min o más (FR-012, FR-021a) |
| `POST /windows/{id}/confirm-review` | E | `{version, justification}`: pasa de REVIEW a CONFIRMED |
| `POST /windows/{id}/test` · `/untest` · `/annul` | E | `{version, justification}` |

## Lotes de importación

| Método y ruta | Rol | Descripción |
|---------------|-----|-------------|
| `POST /import-batches` (multipart `file`) | E | XLSX o CSV de hasta 20 MiB. Devuelve 202 con `{id, status: PARSING}` (FR-022) |
| `POST /import-batches/paste` | E | `{text}`: bloque pegado (tabuladores) con la misma interpretación |
| `GET /import-batches?page` | E | Historial de lotes |
| `GET /import-batches/{id}` | E | Estado, conteos por clasificación, rango de días, archivo y huella |
| `GET /import-batches/{id}/rows?classification&page` | E | Filas con `raw`, `edits`, `interpretation` y `decision` (FR-025) |
| `PATCH /import-batches/{id}/rows/{rowId}` | E | `{version, edits:[{target:'date'\|'code'\|'comment'\|'test'\|'window', pair?, startAt?, endAt?, endsNextDay?, value?}], addWindows?, removePairs?}`. Edita la fila, la vuelve a interpretar y reclasifica las filas relacionadas. `target:'test'` marca la fila o una ventana como prueba; al confirmar, esas ventanas se crean directamente en estado TEST (FR-016, FR-026). Devuelve la fila y los conteos actualizados |
| `POST /import-batches/{id}/rows/{rowId}/link` | E | `{version, equipmentId}`: vincula la fila de forma explícita a un punto KIA (FR-003) |
| `POST /import-batches/{id}/rows/{rowId}/decision` | E | `{version, decision: APPLY\|SKIP\|KEEP_PENDING, skipReason?, acknowledgeTotal?, windows:[{pair, resolution: KEEP_EXISTING\|REPLACE\|MERGE\|OMIT\|OPEN, justification?}]}` (FR-027 a FR-029) |
| `POST /import-batches/{id}/confirm` | E | Devuelve 202. Antes vuelve a validar los instantes futuros y la ausencia de conflictos (FR-021b, FR-028). Las filas que siguen INVALID o CONFLICT sin decisión bloquean la confirmación (`IMPORT_UNRESOLVED_ROWS`) |
| `POST /import-batches/{id}/discard` · `/retry` | E | Ciclo de `BaseImportBatch` |
| `GET /pending-rows?from&to&classification&page` | E | Filas KEEP_PENDING de lotes COMPLETED sin resolver (PENDING_LINK y CONFLICT), con su lote, fila, días tocados y motivo (FR-028, US3-4) |
| `POST /import-batches/{id}/rows/{rowId}/resolve` | E | Solo para una fila KEEP_PENDING de un lote COMPLETED: `{version, equipmentId?, edits?, decision: APPLY\|SKIP, skipReason?, windows:[{pair, resolution, justification?}]}`. Vuelve a validar con las reglas de duplicado, solapamiento, ventana larga e instante futuro, y crea las ventanas bajo el bloqueo por punto. Registra `resolved_by` y `resolved_at`; la cobertura se recupera (FR-045) |

## Días revisados

| Método y ruta | Rol | Descripción |
|---------------|-----|-------------|
| `GET /reviewed-days?from&to` | E | Un registro por cada día de `[from, to)`, con o sin marca, del más antiguo al más reciente: `[{day, active, markedBy, markedAt, version, finished, pendingLinkRows, unconfirmedBatches}]`. `markedBy`, `markedAt` y `version` faltan en un día que nunca se marcó; `finished` es falso para hoy y los días futuros; `pendingLinkRows` son las filas por vincular de lotes confirmados que tocan el día; `unconfirmedBatches`, los lotes en vista previa que lo tocan. Sin rango, los últimos 31 días, hoy incluido. Un rango sin días o de más de 366 es 422 `PERIOD_INVALID` o `PERIOD_TOO_LONG` |
| `PUT /reviewed-days/{day}` | E | Marca el día; responde 422 `DAY_NOT_FINISHED` si el día no terminó (FR-044). Marcar un día ya marcado no cambia nada ni deja otro evento. La respuesta es `{day, warnings:[{batchId, fileName}]}`: los lotes en vista previa que tocan ese día, que todavía no afectan la cobertura (FR-045) |
| `POST /reviewed-days/{day}/withdraw` | E | `{version, justification}`: retira la marca; la justificación es obligatoria. Un día sin registro es 404 `REVIEWED_DAY_NOT_FOUND`; uno sin marca vigente, 422 `DAY_NOT_REVIEWED`. Responde el día con su nueva `version` |

## Consulta y dashboard

| Método y ruta | Rol | Descripción |
|---------------|-----|-------------|
| `GET /summary?preset=FIRST_HALF\|SECOND_HALF\|LAST_15_DAYS\|CUSTOM&month=yyyy-MM&from&to&equipmentIds` | T | Devuelve los campos de la tabla siguiente |
| `GET /availability?mismos parámetros` | **D** | Devuelve la disponibilidad estimada. Quien no tiene el permiso recibe 403 `AVAILABILITY_RESTRICTED` (FR-006) |

La respuesta de `/summary` contiene:

| Campo | Contenido | Quién lo recibe |
|-------|-----------|-----------------|
| `period` | `{preset, from, to, cutOff, timeZone}`; `from` incluido y `to` exclusivo, igual que en el resto de la API | Todos |
| `official` | `{minutes, affectedPoints, windows, pointDays, recurrentPoints, averageMinutes}` | Todos |
| `outsideOfficial` | `{open:{count, provisionalMinutes}, review, test, pendingLink, conflict}` | Todos, como cantidades |
| `outsideOfficial.details` | `{windows:[{windowId, equipmentId, pointLabel, state, startAt, endAt, minutes, reasons}], pendingRows:[{batchId, rowNumber, classification, equipmentId}], truncated}`; hasta 100 de cada lista, las ventanas de la más reciente a la más antigua (si se acota, se pierde lo más antiguo); `minutes` es la porción del período, o la provisional si la ventana está abierta. Es nulo para quien no edita | Solo editores |
| `top` | `[{equipmentId, code, name, minutes, windows, days}]` | Todos |
| `topExtendedByTies` | Si el Top 10 tiene más de diez por empate | Todos |
| `daily` | `[{day, minutes, windows}]`. `windows` cuenta cada ventana en cada día en que tiene tiempo confirmado, así que una ventana que cruza la medianoche cuenta en ambos días y la suma puede superar `official.windows` (FR-035) | Todos |
| `dailyWindowsMayExceedTotal` | `true` si alguna ventana cuenta en más de un día; el gráfico muestra la nota correspondiente | Todos |
| `byDurationBand` | `[{band, minutes, windows}]`, siempre las cinco franjas en orden (con ceros) | Todos |
| `concentration` | `{topMinutes, restMinutes}` | Todos |
| `recurrence` | `{recurrentMinutes, nonRecurrentMinutes}` | Todos |
| `generatedAt` | Momento del cálculo | Todos |

`/summary` **nunca** incluye campos de disponibilidad.

Sin ventanas confirmadas en el período, `official` trae ceros (`averageMinutes` nulo) y `daily`, `top` y `byDurationBand` vienen vacíos: no hay series ni pasteles falsos. `outsideOfficial` se calcula igual.

Con `equipmentIds`, todas las cifras se limitan a esos puntos. `pendingLink` cuenta filas sin punto, que no se pueden atribuir a ninguno, así que con puntos elegidos es 0; `conflict` solo cuenta los conflictos de esos puntos. Las filas pendientes se asignan al período por los días que tocan.

Valores de `preset`:
- `FIRST_HALF` y `SECOND_HALF` usan `month`.
- `LAST_15_DAYS` va de hoy − 14 a hoy + 1.
- `CUSTOM` usa `from` y `to`, con un máximo de 366 días (`PERIOD_TOO_LONG`).
- Si no se indica, el valor por defecto es la quincena en curso (FR-031).

La respuesta de `/availability` contiene:
- `{aggregate:{percent, evaluatedMinutes, offlineMinutes}, coverage:{coveredDays, daysInScope}, blockedDays:[{day, pendingRows}], points:[{equipmentId, code, name, percent\|null, evaluatedMinutes, offlineMinutes, coveredDays}], exclusions:[{equipmentId, code, name, reason}], labels:{estimated:true, test:true, origin}, limitations[]}`;
- `coverage.daysInScope` cuenta los días del rango ya terminados hasta el corte; hoy y los días futuros no cuentan como cubiertos ni como sin datos (FR-043, FR-046);
- un `percent` ausente (el JSON omite los nulos) significa «Sin datos», nunca 100 %. Vale para el agregado y para cada punto;
- `period` es igual al de `/summary`: `{preset, from, to, cutOff, timeZone}`, con `to` exclusivo;
- los puntos son los puntos KIA en alcance (activos y no DESCONECTADOS) más los inactivos o eliminados con ventanas CONFIRMED, OPEN o POR REVISAR en el rango. Las ventanas de prueba y las anuladas no cuentan. Con `equipmentIds`, solo los elegidos;
- `exclusions[].reason` es `DISCONNECTED`, `INACTIVE` o `DELETED`;
- un punto solo se evalúa desde el primer día completo en que ya existía: antes de su alta no suma tiempo evaluado ni cobertura, y si su alta es posterior a todos los días revisados queda «Sin datos» (`coveredDays` 0). El estado actual (inactivo, desconectado) se evalúa al consultar (FR-045);
- `points` viene ordenado de menor a mayor porcentaje, con los «Sin datos» al final (FR-046a).

## Informes

| Método y ruta | Rol | Descripción |
|---------------|-----|-------------|
| `GET /report?format=PDF\|XLSX&mismos parámetros de /summary` | T | Genera, archiva (`kind = INTERMITTENCY`) y devuelve el archivo. Incluye el apartado de disponibilidad solo si quien lo pide tiene el permiso D en ese momento (contracts/reports.md). Si las ventanas confirmadas del rango superan 50.000, responde 422 `REPORT_TOO_LARGE` sin generar ni archivar nada (FR-041a) |

Para descargar un informe ya archivado se usa el endpoint vigente de `modules/reporting`. Con `kind = INTERMITTENCY` y un perfil distinto de `OFFICIAL_ONLY`, quien no tiene permiso recibe 403 `REPORT_OPERATIONAL_CONTENT_RESTRICTED` (FR-040).

## Errores principales

| Código | HTTP | Cuándo |
|--------|------|--------|
| `NOT_KIA_CHARGING_POINT` | 422 | El equipo está fuera de alcance (FR-001) |
| `WINDOW_OVERLAP` | 409 | Solapamiento con otra ventana (FR-014) |
| `WINDOW_DUPLICATE` | 409 | Ya existe una ventana idéntica (FR-015) |
| `WINDOW_END_BEFORE_START` | 422 | El fin es anterior o igual al inicio (FR-020) |
| `WINDOW_FUTURE_INSTANT` | 422 | Un instante es posterior al minuto en curso (FR-021b) |
| `JUSTIFICATION_REQUIRED` | 422 | Falta la justificación (FR-017, FR-021a) |
| `CONCURRENT_MODIFICATION` | 409 | La versión está desactualizada (FR-018) |
| `IMPORT_UNRESOLVED_ROWS` | 422 | Se intenta confirmar con filas inválidas o en conflicto sin decisión |
| `OPEN_TEST_NOT_ALLOWED` | 422 | Se elige la decisión OPEN para una ventana marcada como prueba, o se marca como prueba una ventana con OPEN (FR-016) |
| `IMPORT_HEADER_MISSING` | 422 | Falta Fecha, Código cargador o el Fin de un Inicio; el mensaje nombra la columna |
| `DAY_NOT_FINISHED` | 422 | Se intenta marcar un día que no terminó (FR-044) |
| `PERIOD_TOO_LONG` | 422 | El rango supera 366 días |
| `PERIOD_INVALID` | 422 | El rango no tiene al menos un día: el fin es exclusivo |
| `JUSTIFICATION_TOO_LONG` | 422 | La justificación supera 500 caracteres |
| `DAY_NOT_REVIEWED` | 422 | Se retira la marca de un día que no está marcado (FR-044) |
| `REVIEWED_DAY_NOT_FOUND` | 404 | El día nunca se marcó |
| `REPORT_TOO_LARGE` | 422 | El informe tendría más de 50.000 ventanas confirmadas con tiempo en el rango (FR-041a). El mensaje pide acortar el período o elegir menos puntos. Mismo código y estado que los informes de Consumo e Inventario |
| `AVAILABILITY_RESTRICTED` | 403 | Pide la disponibilidad alguien sin el permiso D |
| `ACCESS_DENIED` | 403 | Rol insuficiente para editar (FR-004) |

Los mensajes están en español y nunca incluyen secretos ni datos de otros puntos.
