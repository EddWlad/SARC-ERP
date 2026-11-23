# Contrato REST: Incidencias

**Feature**: `010-incidencias-tickets-tiempos` | **Modelo**: [data-model.md](../data-model.md)

Convenciones heredadas de los módulos existentes:

- Base `/api/v1/incidents`. La sesión es por cookie (Spring Session JDBC) y las escrituras llevan CSRF.
- JSON en camelCase. Instantes en ISO-8601 con zona; días `LocalDate` interpretados en `America/Guayaquil`.
- Los errores siguen `ProblemDetail` con la propiedad `code` (`GlobalExceptionHandler`). La validación de entrada devuelve 400 `VALIDATION_FAILED`.
- Toda escritura lleva `version` (la del ticket raíz). Si no coincide, responde 409 `TICKET_VERSION_CONFLICT` y la interfaz ofrece recargar (patrón de Intermitencias).
- Paginación `page`/`size` con `PageQuery.MAX_SIZE`.
- Los informes devuelven `X-Report-Id` y `X-Report-Cut-Off` con `Cache-Control: no-store, private`.
- La fuente publicada es `sarc-bakend/docs/api/openapi.json`, verificada por `OpenApiContractTest`. El frontend regenera sus tipos con `npm run api:types`.

## Permisos

| Grupo | Expresión | Uso |
|-------|-----------|-----|
| `IncidentPermissions.READERS` | `isAuthenticated()` con cualquiera de los cinco roles | Lecturas, mensaje, informes |
| `IncidentPermissions.EDITORS` | `hasAnyRole('ADMINISTRADOR','ANALISTA')` | Toda escritura |
| `IncidentPermissions.NUMBERING` | `hasRole('ADMINISTRADOR')` | Configurar la numeración |

Se aplica con `@PreAuthorize` en cada método del controlador. La interfaz oculta las acciones, pero la autorización vale en el servidor (FR-090 a FR-093). Una escritura con rol de lectura devuelve 403 y no deja cambios.

## Numeración

| Método y ruta | Permiso | Descripción |
|---------------|---------|-------------|
| `GET /numbering` | READERS | `{configured, environment, startNumber, nextCode, expectedEnvironment, ticketsExist}`. `nextCode` es informativo; el número se asigna solo al crear. `expectedEnvironment` es `null` si la propiedad del servidor falta, y en ese caso no se puede configurar ni crear |
| `PUT /numbering` | NUMBERING | Body `{startNumber, environment, confirmation}`. `confirmation` debe repetir el código resultante, por ejemplo `"TICKET-086"` |

Errores de `PUT /numbering`:

- 409 `TICKET_NUMBERING_LOCKED`: ya hay tickets.
- 422 `TICKET_NUMBERING_ENVIRONMENT_MISMATCH`: el `environment` enviado no coincide con `sarc.incidents.numbering.environment`, o la propiedad falta. Aquí es 422 porque el dato de entrada es incorrecto. Al crear un ticket, el mismo código responde 409, porque es el estado guardado el que no coincide con el servidor (intencional).
- 422 `TICKET_NUMBERING_CONFIRMATION_MISMATCH`.

## Activos para el formulario

| Método y ruta | Permiso | Descripción |
|---------------|---------|-------------|
| `GET /assets?search=&limit=` | EDITORS | Equipos activos. Respuesta `IncidentAssetResponse[]`: `{id, name, assignedCode, network, infrastructureType, location, mapsUrl, connectors:[{id, position, typeLabel}]}`. Nunca incluye UID/PUK ni OCPP |
| `GET /tickets/open-matches?elementClass=&equipmentId=&siteElementKind=&referenceEquipmentId=&manualSiteName=&elementName=` | EDITORS | Tickets abiertos con la misma clave de duplicado (FR-016, research R4), para avisar antes de enviar el formulario. Para el chatbot, devuelve cualquier ticket abierto del servicio |
| `GET /providers?search=` | EDITORS | Nombres de proveedor ya usados en visitas (P-08) |
| `GET /responsibles` | EDITORS | Usuarios elegibles como responsable: activos con rol Analista o Administrador. Respuesta `[{id, name}]`, ordenada por nombre. No incluye correo ni roles (`IncidentStaffDirectory`) |

## Tickets

| Método y ruta | Permiso | Descripción |
|---------------|---------|-------------|
| `GET /tickets` | READERS | Lista paginada |
| `GET /tickets/{id}` | READERS | Detalle completo |
| `POST /tickets` | EDITORS | Crear |
| `PATCH /tickets/{id}` | EDITORS | Corregir la descripción o el origen del aviso, con justificación |
| `POST /tickets/{id}/reclassify` | EDITORS | Cambiar el tipo de incidencia |
| `POST /tickets/{id}/priority` | EDITORS | Cambiar la prioridad |
| `POST /tickets/{id}/responsible` | EDITORS | Asignar responsable |
| `POST /tickets/{id}/handover` | EDITORS | Relevo de turno |
| `POST /tickets/{id}/relations` | EDITORS | Relacionar tickets |
| `POST /tickets/{id}/close` | EDITORS | Cerrar |
| `POST /tickets/{id}/reopen` | EDITORS | Reabrir |

### `GET /tickets`

Filtros:

- `from`, `to` (días), con la regla de inclusión FR-073;
- `state` (`OPEN`, `CLOSED`), `elementClass`, `incidentType`, `priority`, `network` (incluye `NONE` para «Sin red»), `equipmentId`, `responsibleId`;
- `condition` (`PENDING_VALIDATION`, `IN_ATTENTION`, `RECOVERED_OBSERVING`);
- `search` (código o texto), `page`, `size`;
- `sort`: `code`, `state`, `type`, `network`, `priority`, `responsible`, `t0` (antigüedad) o `lastActivityAt`, con `,asc` o `,desc`. El valor por defecto es `lastActivityAt,desc`. El elemento afectado no se ordena (FR-061). `network` ordena por la red conservada, con «Sin red» al final; `responsible`, por `current_responsible_name` (la copia guardada al asignar; puede ser un nombre anterior si el usuario cambió de nombre después); `type`, por la etiqueta en español del tipo (`IncidentLabels`), no por su código. Un valor desconocido devuelve 400 `VALIDATION_FAILED`.

Respuesta: `{items: TicketSummary[], total, page, size, cutOff, notice?}`.

- `notice = "TICKET_BEFORE_START"` cuando `search` es un código `TICKET-n` con `n` menor que el número inicial configurado. La interfaz muestra: «Ese ticket pertenece a la secuencia externa anterior y no está registrado en SARC» (caso límite de la spec).

`TicketSummary`: `{id, code, state, stateAtPeriodEnd?, elementClass, elementLabel, network, incidentType, incidentTypeLabel, priority, responsible:{id, name}, t0, lastActivityAt, condition, episodeCount, version}`.

- `elementLabel` es el texto ya compuesto con el **nombre actual** (D12, FR-075): equipo, más conectores por posición actual (si están elegidos todos los conectores activos, se muestra como equipo completo, aunque la selección se conserva en el detalle); «Servicio de chatbot»; o «Sitio / Elemento». En «Sitio / Elemento», el sitio es el nombre actual del equipo de referencia o el nombre escrito a mano. La etiqueta en T0 se muestra solo en el detalle (`equipment.labelAtT0`).
- `stateAtPeriodEnd` se envía solo cuando hay período. El estado actual va siempre en `state`.

### `POST /tickets`

Body:

```json
{
  "clientRequestId": "uuid",
  "elementClass": "EQUIPMENT",
  "equipmentId": "uuid",
  "connectorIds": [],
  "chatbotScope": null,
  "siteElement": null,
  "incidentType": "DISCONNECTION",
  "description": "texto ≤ 500",
  "reportOrigin": "PLATFORM",
  "reportOriginDetail": null,
  "priority": null,
  "priorityReason": null,
  "t0": null,
  "t0Reason": null,
  "duplicateOverrideReason": null
}
```

- `connectorIds` vacío significa el equipo completo.
- `siteElement` lleva `{kind, elementName?, referenceEquipmentId?, manualSiteName?, manualLocation?, manualMapsUrl?}`.
- Si `priority` es nulo, se usa la prioridad propuesta.
- Si `t0` es nulo, T0 es el momento de creación. Un `t0` anterior exige `t0Reason`.
- Solo los tres primeros campos de negocio son obligatorios en la interfaz: elemento, tipo y descripción (FR-010). Los demás tienen valores por defecto y se pueden desplegar.

Respuestas:

- 201 con `TicketDetail`.
- 200 con el mismo ticket si `clientRequestId` ya existía (idempotencia).

Errores:

| Código | HTTP | Causa |
|--------|------|-------|
| `TICKET_NUMBERING_ENVIRONMENT_MISMATCH` | 409 | La propiedad de ambiente del servidor falta o no coincide con la numeración guardada. Se comprueba antes que la configuración |
| `TICKET_NUMBERING_NOT_CONFIGURED` | 409 | El ambiente coincide, pero falta configurar la numeración |
| `TICKET_OPEN_DUPLICATE` | 409 | Ya hay tickets abiertos del mismo equipo, del servicio de chatbot o del mismo elemento del sitio. Lleva `openTickets[]`; se reintenta con `duplicateOverrideReason` o se agrega el seguimiento al ticket existente (FR-016) |
| `TICKET_ELEMENT_INVALID` | 422 | Clase incompatible con los campos o con el tipo; conector ajeno o inactivo; equipo inactivo |
| `TICKET_T0_INVALID` | 422 | T0 futuro, o retroactivo sin motivo |
| `TICKET_MAPS_URL_INVALID` | 422 | Enlace de Maps que no es `https://` |

### `GET /tickets/{id}`

`TicketDetail` = `TicketSummary` más:

- `description`, `reportOrigin`, `reportOriginDetail`;
- elemento: `equipment:{id, labelAtT0, currentName, active}?`, `connectors:[{id, positionAtT0, currentPosition?, active}]`, `chatbotScope?`, `siteElement?`, `location`, `mapsUrl`;
- `referenceVersion:{code, label}`;
- `episodes:[EpisodeDetail]`;
- `relations:[{ticketId, code, kind, reason}]`;
- `timeline:[EventResponse]`, con los anulados marcados;
- `visits:[VisitResponse]`, `communications:[CommunicationResponse]`, `evidence:[EvidenceResponse]`;
- `allowedActions[]`: lo calcula el servidor con el rol y el estado. La interfaz lo usa para mostrar acciones, pero la autorización sigue en el servidor.

`EpisodeDetail`:

- `{ordinal, state, reopenReason, closure?}`;
- `milestones:[{kind, status, occurredAt, reason, retroactive, orderWarningAck, recordedBy, recordedAt, revisions:[…]}]`;
- `durations:[{pair:"T3-T0", minutes|null, missing:[kinds]}]`;
- `references:[{measure, limitMinutes, extendable, result:"WITHIN|OUTSIDE|NOT_EVALUABLE|NO_REFERENCE"}]`;
- `deviationJustification`.

Una duración no calculable lleva `minutes: null` y nunca 0 (FR-044).

### Seguimiento y acciones sobre el ticket

Todas las acciones responden con el `TicketDetail` actualizado y crean su evento.

| Acción | Body | Reglas |
|--------|------|--------|
| `PATCH /tickets/{id}` | `{version, description?, reportOrigin?, reportOriginDetail?, justification}` | Corrige la descripción o el origen del aviso (FR-036). La justificación es obligatoria. Crea un evento `DATA_CORRECTED` por campo, con el valor anterior y el nuevo. El elemento afectado no se corrige: se cierra como `REGISTERED_IN_ERROR` y se crea otro ticket |
| `/reclassify` | `{version, incidentType, reason}` | Debe ser compatible con la clase. Recalcula la prioridad propuesta, pero no cambia la actual |
| `/priority` | `{version, priority, reason}` | Evento `PRIORITY_CHANGED` |
| `/responsible` | `{version, responsibleId}` | Solo un Analista o Administrador activo, de `GET /responsibles`. Si no lo es, 422 `TICKET_RESPONSIBLE_INVALID`. Actualiza `current_responsible_name` |
| `/handover` | `{version, incomingResponsibleId, currentStatus, pending, nextCheckAt?, closureCriterion}`. El entrante sigue la regla de `/responsible` (422 `TICKET_RESPONSIBLE_INVALID`) | Cambia el responsable y deja el evento `SHIFT_HANDOVER` con el saliente, el entrante, el estado actual, los pendientes, la próxima verificación y el criterio de cierre (FR-033) |
| `/relations` | `{version, relatedTicketId, kind, reason?}` | `DELETE /tickets/{id}/relations/{relatedId}?version=` quita una relación |
| `/close` | `{version, t4, t4Reason?, result, duplicateOfTicketId?, causeKind, causeText?, recommendations?, closureChecks?, finalEvidenceWaiver?, closureCommunicationWaiver?, deviationJustification?}` | Ver abajo |
| `/reopen` | `{version, reason, t0?, t0Reason?}` | Ver abajo |

Errores del cierre (`/close`):

- 422 `TICKET_CLOSE_REQUIREMENTS`, con `missing[]`, que nombra cada requisito faltante según la clase (FR-047, FR-048, FR-048a). Valores posibles: `T4`, `T1`, `T2`, `T3`, `RESULT`, `DUPLICATE_OF`, `CAUSE`, `CAUSE_TEXT`, `PROBABLE_CAUSE_EVIDENCE`, `CLOSURE_CHECKS`, `FINAL_EVIDENCE`, `CLOSURE_COMMUNICATION` y `DEVIATION_JUSTIFICATION`.
- 409 `TICKET_ALREADY_CLOSED`.

Reglas y errores de la reapertura (`/reopen`):

- Crea un episodio nuevo con T0 propio (por defecto, el momento de reabrir) y la versión de referencias vigente.
- 409 `TICKET_REOPEN_FORBIDDEN` si el resultado fue `DUPLICATE` o `REGISTERED_IN_ERROR`.
- 409 `TICKET_ALREADY_OPEN`.

## Hitos

| Método y ruta | Body | Reglas |
|---------------|------|--------|
| `PUT /tickets/{id}/episodes/{ordinal}/milestones/{kind}` | `{version, status, occurredAt?, reason?, justification?, orderWarningAck?}` | Registra o corrige un hito. `kind` va de `T1` a `T3`, o `T0` solo para corregir. Si el hito ya tenía valor es una corrección y exige `justification`. Siempre anexa una revisión |

Las respuestas siguen el mismo patrón que las acciones. El hito T4 se registra solo al cerrar, y se corrige con `kind=T4` en un episodio cerrado, con `justification`.

Errores:

| Código | HTTP | Causa |
|--------|------|-------|
| `MILESTONE_BEFORE_T0` | 422 | Hora anterior a T0 |
| `MILESTONE_IN_FUTURE` | 422 | Hora futura |
| `MILESTONE_ORDER_UNCONFIRMED` | 422 | El orden T1 ≤ T2 ≤ T3 no se cumple y no llegó `orderWarningAck` |
| `MILESTONE_REASON_REQUIRED` | 422 | Falta el motivo de «No aplica», «Sin dato» o de un registro retroactivo |
| `MILESTONE_T4_NOT_APPLICABLE` | 422 | Se pidió «No aplica» o «Sin dato» para T4 |
| `EPISODE_NOT_EDITABLE` | 409 | Se intentó registrar T1 a T3 sobre un episodio cerrado sin `justification` |

## Eventos

| Método y ruta | Body | Reglas |
|---------------|------|--------|
| `POST /tickets/{id}/events` | `{version, type, occurredAt?, text, targetKind?, targetRoleOrProvider?}`. `targetKind` es obligatorio en `ESCALATED` (`TECHNICAL`, `SUPERVISION`, `PLATFORM_SUPPORT` u `OTHER`) | `type` admite `VALIDATION`, `CORRECTIVE_ACTION`, `ESCALATED` u `OBSERVATION`. Los demás tipos los genera el sistema. Si `occurredAt` es más de 1 min anterior al momento del registro, marca el evento como retroactivo |
| `POST /tickets/{id}/events/{eventId}/annul` | `{version, reason}` | Solo para eventos manuales, comunicaciones y evidencias. Nunca se borran |

## Visitas

| Método y ruta | Body |
|---------------|------|
| `POST /tickets/{id}/visits` | `{version, providerName?, occurredAt?, note?}`. Crea la visita en etapa `REQUESTED` |
| `POST /tickets/{id}/visits/{visitId}/stage` | `{version, stage, occurredAt?, note?, providerName?, cancelDecidedBy?, cancelEvidenceNote?, reescalationCondition?}`. `providerName` se indica al asignar la visita |

Error: 422 `VISIT_TRANSITION_INVALID`, o si faltan los campos que exige una cancelación o un aplazamiento.

## Comunicaciones

| Método y ruta | Body |
|---------------|------|
| `POST /tickets/{id}/communications` | `{version, channel, channelDetail?, kind, sentAt, justification?}`. Registra el envío manual (FR-058). Se responde con la opción «Registrar envío» del diálogo del mensaje |

Copiar el mensaje no llama a este endpoint.

## Evidencias

| Método y ruta | Body |
|---------------|------|
| `POST /tickets/{id}/evidence` (multipart) | Partes `file` y `meta` (`{version, purpose, eventId?, communicationId?, note?}`) |
| `GET /tickets/{id}/evidence/{evidenceId}` | Redirección 302 a la URL firmada de `FileStorage`, de vida corta. Permiso READERS |

Reglas y errores de la carga:

- El formato se valida por firma binaria: JPEG, PNG, WEBP o PDF (`%PDF-`).
- 413 `EVIDENCE_TOO_LARGE` si supera 10 MB; 422 `EVIDENCE_FORMAT_NOT_ALLOWED`.
- El límite multipart global (20 MB por archivo) no cambia.

## Mensaje para WhatsApp

| Método y ruta | Permiso | Descripción |
|---------------|---------|-------------|
| `GET /tickets/{id}/message?kind=INITIAL\|UPDATE\|CLOSURE&includeDetection=false&includeActions=false` | READERS | `{text, missingFields:["mapsUrl"], ticketCode, state}`. Solo lectura; no crea eventos. Reglas en [whatsapp-message.md](whatsapp-message.md) |

## Tablero y resumen

| Método y ruta | Permiso | Descripción |
|---------------|---------|-------------|
| `GET /summary?from=&to=&network=&elementClass=&incidentType=&priority=` | READERS | Cifras del tablero, con las mismas reglas que el informe de gestión (FR-080, FR-086) |

`GET /summary` devuelve:

- `openNow`, `openedInPeriod`, `closedInPeriod`, `reopenedInPeriod`;
- `byCondition`;
- `byType[]`, `byNetwork[]`, `byElementClass[]`, `byPriority[]`, `byOrigin[]`, `byEquipmentTop[]`;
- `averages:[{measure, averageMinutes|null, n, excluded}]`, `references:[{measure, within, outside, notEvaluable, noReference}]`;
- `visits:{requested, confirmed, cancelledOrPostponed, executed}`;
- `cutOff`.

Cada promedio lleva su denominador `n` y cuántos episodios quedaron fuera por falta de hitos (`excluded`).

## Informes

| Método y ruta | Permiso | Descripción |
|---------------|---------|-------------|
| `GET /reports/control?format=PDF\|XLSX&from=&to=` | READERS | «Control de tickets». Sin `from`/`to` incluye todo el historial (FR-072) |
| `GET /reports/management?format=PDF\|XLSX&from=&to=&…filtros de summary` | READERS | Informe de gestión |

Ambos archivan la emisión con `ReportArchive` (`kind` `INCIDENT_CONTROL` o `INCIDENT_MANAGEMENT`, con el criterio de selección y la huella) y devuelven `X-Report-Id` y `X-Report-Cut-Off`. El detalle está en [reports.md](reports.md).

Para el rango, `from` y `to` son días inclusivos en la interfaz; el servidor convierte `to` al inicio del día siguiente (fin exclusivo E, FR-074). El rango máximo es de 366 días, como en `MAX_PERIOD_DAYS`, salvo «Todo el historial».

## Auditoría

Cada escritura registra `AuditTrail.record(action, "INCIDENT_TICKET", ticketId, details)`, con acciones como `TICKET_CREATED`, `MILESTONE_CORRECTED`, `TICKET_CLOSED` o `NUMBERING_CONFIGURED`. Los detalles no incluyen texto libre largo ni datos sensibles; se recortan a 500 caracteres.
