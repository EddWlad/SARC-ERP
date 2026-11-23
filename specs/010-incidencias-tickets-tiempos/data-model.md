# Data Model: Incidencias, tickets y tiempos

**Feature**: `010-incidencias-tickets-tiempos` | **Date**: 2026-11-20 | **Research**: [research.md](research.md)

Este documento describe el modelo lógico. La forma física exacta (tipos y nombres definitivos) se fija en la migración `V021__incidencias_tickets.sql`, que solo agrega tablas y no modifica las existentes. Todas las tablas usan el prefijo `inc_`, identificadores UUID e instantes `TIMESTAMPTZ` en UTC. Los días se interpretan en `America/Guayaquil` (`OperatingZone`).

## Vista general

```text
inc_ticket_numbering (1 fila)
inc_reference_version 1─* inc_reference_rule
inc_ticket 1─* inc_episode 1─* inc_milestone 1─* inc_milestone_revision
inc_ticket 1─* inc_ticket_connector
inc_ticket *─* inc_ticket (inc_ticket_relation)
inc_ticket 1─* inc_event 0..1─* inc_evidence
inc_ticket 1─* inc_visit 1─* inc_event (etapas de la visita)
inc_ticket 1─* inc_communication 0..1─1 inc_evidence
inc_episode *─1 inc_reference_version
```

Referencias externas, siempre por UUID y sin claves foráneas hacia otros módulos, igual que Intermitencias:

- `equipment_id`, `reference_equipment_id` y `connector_id` apuntan a Inventario. Se resuelven con `IncidentAssetDirectory`.
- Los campos de autor (`created_by`, `recorded_by` y similares) apuntan a `auth_user`.

## inc_ticket_numbering

| Campo | Tipo | Reglas |
|-------|------|--------|
| id | SMALLINT PK | Siempre `1` (`CHECK (id = 1)`) |
| start_number | INTEGER NULL | Nulo hasta configurarse. Mayor o igual que 1 |
| next_number | INTEGER NULL | Igual a `start_number` al configurarse. Sube 1 por ticket |
| environment | VARCHAR(12) NULL | `PRODUCTION` o `TEST`. Lo fija la configuración y debe coincidir con `sarc.incidents.numbering.environment`. Si la propiedad falta, no se configura ni se crea ningún ticket |
| configured_at / configured_by | TIMESTAMPTZ / UUID | Auditoría de la configuración |
| version | BIGINT | Bloqueo optimista de la configuración |

- La migración inserta la fila con `start_number`, `next_number` y `environment` nulos. **No siembra ningún número** (FR-013).
- Solo se configura si `SELECT count(*) FROM inc_ticket` = 0. Después de eso la fila es inmutable, salvo `next_number`.
- Al asignar un número se ejecuta `UPDATE … RETURNING` dentro de la transacción de creación (research R2).

## inc_reference_version / inc_reference_rule

**inc_reference_version**

| Campo | Tipo | Reglas |
|-------|------|--------|
| id | UUID PK | |
| code | VARCHAR(40) UNIQUE | Por ejemplo, `PROTOCOLO-FIRMADO-1` |
| label | VARCHAR(160) | «Protocolo de monitoreo firmado, versión 2.0» desde V022 (antes «versión por confirmar», P-02 resuelta el 2026-11-23) |
| effective_from | TIMESTAMPTZ | Vigencia. No se solapan: rige la última con `effective_from ≤ T0` |
| source_note | VARCHAR(500) | Fuente: PRD §10.3, Protocolo §5 |

**inc_reference_rule**: una fila por versión y tipo. Un tipo sin fila no tiene referencia.

| Campo | Tipo | Reglas |
|-------|------|--------|
| version_id | UUID FK | |
| incident_type | VARCHAR(40) | Valor de `IncidentType` |
| validation_max_min | INTEGER NULL | 5 en los tipos con validación de cinco minutos |
| recovery_max_min | INTEGER NULL | Desconexión 300, Falla de conector 180, Conector retenido 120 |
| recovery_extendable | BOOLEAN | `true` solo en Desconexión («ampliable con justificación») |
| escalation_max_min | INTEGER NULL | Espera de conexión 60 |
| persistent_after_min | INTEGER NULL | Punto KIA con caída persistente: 30. Es persistente si la duración exacta, en segundos, es estrictamente mayor que `persistent_after_min × 60`; no se redondea a minutos (P-04) |
| proposed_priority | VARCHAR(10) | `MEDIUM`, `HIGH` o `CRITICAL` (P-05) |

- PK (`version_id`, `incident_type`). Las filas de una versión no se modifican una vez sembradas: un cambio crea otra versión con otra migración (P-03).
- La siembra inicial reproduce las tablas de FR-034 y FR-051. «No permite detener la carga» lleva `validation_max_min = 5` y no tiene `recovery_max_min`.
- Los tipos sin respaldo no tienen fila; en la interfaz su prioridad propuesta por defecto es `MEDIUM`. Esos tipos son: Paro de emergencia, Novedad de instalación o conectividad, Falla del servicio de chatbot, Falla de elemento del sitio e Incidencia no catalogada.

## inc_ticket (raíz del agregado)

Hereda `BaseAuditableEntity` (id, created_at, updated_at, created_by, updated_by, version).

| Campo | Tipo | Reglas |
|-------|------|--------|
| ticket_number | INTEGER UNIQUE NOT NULL | Lo asigna el contador. Inmutable |
| ticket_code | VARCHAR(20) UNIQUE | Columna generada: `'TICKET-' \|\| lpad(ticket_number::text, greatest(3, length(ticket_number::text)), '0')`. `lpad` recorta los textos más largos que su longitud, por eso se usa `greatest`; así 1000 da `TICKET-1000` |
| client_request_id | UUID UNIQUE NOT NULL | Idempotencia de la creación |
| state | VARCHAR(8) | `OPEN` o `CLOSED`. Coincide con el estado del último episodio |
| element_class | VARCHAR(20) | `EQUIPMENT`, `CHATBOT_SERVICE` o `SITE_ELEMENT` |
| equipment_id | UUID NULL | Obligatorio solo en `EQUIPMENT` |
| equipment_label_at_t0 | VARCHAR(150) NULL | Nombre del equipo al crear el ticket (D12: el detalle conserva la etiqueta) |
| network_at_t0 | VARCHAR(20) NULL | `KIA`, `JAC` o `CONECTZ`. Solo en `EQUIPMENT` (P-14) |
| infrastructure_type_at_t0 | VARCHAR(20) NULL | `CHARGING_POINT` o `CHARGING_STATION`. Solo en `EQUIPMENT` |
| chatbot_scope | VARCHAR(10) NULL | `TOTAL` o `PARTIAL`. Solo en `CHATBOT_SERVICE` y opcional |
| site_element_kind | VARCHAR(20) NULL | `VEHICLE_BARRIER` u `OTHER`. Solo en `SITE_ELEMENT` |
| element_name | VARCHAR(120) NULL | Obligatorio si `site_element_kind = OTHER` |
| reference_equipment_id | UUID NULL | `SITE_ELEMENT` con un equipo del sitio |
| manual_site_name / manual_location | VARCHAR(150) / VARCHAR(300) NULL | `SITE_ELEMENT` sin equipo de referencia |
| manual_maps_url | VARCHAR(500) NULL | Opcional; debe ser `https://` |
| site_label_at_t0 | VARCHAR(150) NULL | Nombre del sitio al crear el ticket |
| location_at_t0 / maps_url_at_t0 | VARCHAR(300) / VARCHAR(500) NULL | Ubicación conservada del elemento del sitio |
| incident_type | VARCHAR(40) | Valor de `IncidentType`, compatible con `element_class` |
| report_origin | VARCHAR(20) | `PLATFORM`, `CHATBOT`, `SITE_STAFF`, `CUSTOMER`, `PROVIDER` u `OTHER` |
| report_origin_detail | VARCHAR(200) NULL | Obligatorio si `OTHER` |
| description | VARCHAR(500) NOT NULL | La «novedad» del mensaje |
| priority | VARCHAR(10) | `MEDIUM`, `HIGH` o `CRITICAL` |
| current_responsible_id | UUID NOT NULL | Analista o Administrador activo al asignarlo, validado con `IncidentStaffDirectory`. Al crear, es el autor |
| current_responsible_name | VARCHAR(150) NOT NULL | Copia del nombre del responsable, tomada al crear, al asignar y en el relevo. Solo sirve para ordenar sin leer tablas de Identidad. Para mostrar se usa el nombre actual de `IncidentStaffDirectory.namesOf`, igual que en la cronología. Si un usuario cambia de nombre, el orden sigue el anterior hasta la próxima asignación o relevo |
| duplicate_key | VARCHAR(200) NOT NULL | Clave del aviso de duplicado calculada por el dominio (research R4): `EQ:{id}`, `CHATBOT` o `SITE:{kind}:{sitio}:{elemento}`. No es única |
| duplicate_override_reason | VARCHAR(300) NULL | Motivo para crearlo aunque había otro ticket abierto con la misma clave (FR-016) |
| last_activity_at | TIMESTAMPTZ | «Última actualización» del tablero |

**Restricciones `CHECK` por clase de elemento** (red de seguridad; la regla vive en el dominio):

- `EQUIPMENT`: `equipment_id`, `equipment_label_at_t0`, `network_at_t0` e `infrastructure_type_at_t0` NOT NULL. Las columnas de las otras clases son nulas.
- `CHATBOT_SERVICE`: las columnas de equipo, del sitio y de red son nulas.
- `SITE_ELEMENT`: `site_element_kind` es obligatorio, y además exactamente una de dos opciones: `reference_equipment_id` o bien (`manual_site_name` y `manual_location`). `network_at_t0`, `equipment_id` y los conectores son nulos o no existen.
- `incident_type` debe ser compatible con `element_class`. La lista se repite en el `CHECK` y en `IncidentType`, y una prueba verifica que coincidan.

**Índices**:

- `(state, last_activity_at DESC)`;
- `(duplicate_key) WHERE state = 'OPEN'`, para el aviso de duplicado;
- `(equipment_id)` y `(reference_equipment_id)`, para el historial por equipo;
- `(element_class, site_element_kind, reference_equipment_id)`;
- `(network_at_t0)`;
- `(incident_type)`.

## inc_ticket_connector

| Campo | Tipo | Reglas |
|-------|------|--------|
| ticket_id | UUID FK | |
| connector_id | UUID | Conector por identidad (D06) |
| position_at_t0 | SMALLINT | Posición que se mostraba al crear el ticket |

- PK (`ticket_id`, `connector_id`). Solo en tickets `EQUIPMENT`. Los conectores deben pertenecer al equipo y estar activos al crearse; lo valida el dominio con `IncidentAssetDirectory`.
- Sin filas, la afectación es del equipo completo (FR-002).

## inc_ticket_relation

| Campo | Tipo | Reglas |
|-------|------|--------|
| ticket_id / related_ticket_id | UUID FK | Se guarda una sola fila por par, con `ticket_id < related_ticket_id`. Se muestra en ambos sentidos |
| kind | VARCHAR(20) | `INDEPENDENT_FAILURE` (falla nueva de un equipo con historial) o `COMMON_FAILURE` (misma falla en otro equipo del sitio) |
| reason | VARCHAR(300) NULL | |
| created_at / created_by | | |

## inc_episode

| Campo | Tipo | Reglas |
|-------|------|--------|
| id | UUID PK | |
| ticket_id | UUID FK | |
| ordinal | SMALLINT | 1, 2, … Único por ticket |
| state | VARCHAR(8) | `OPEN` o `CLOSED`. Solo el último episodio puede estar `OPEN` |
| reference_version_id | UUID FK | Versión vigente en T0 (FR-051a) |
| reopen_reason | VARCHAR(500) NULL | Obligatorio si `ordinal > 1` |
| closure_result | VARCHAR(20) NULL | `RECOVERED`, `NO_FAULT_CONFIRMED`, `DUPLICATE` o `REGISTERED_IN_ERROR`. Obligatorio si `CLOSED` |
| duplicate_of_ticket_id | UUID NULL | Obligatorio si `DUPLICATE` |
| cause_kind | VARCHAR(20) NULL | `CONFIRMED`, `PROBABLE` o `UNDETERMINED`. Obligatorio si `CLOSED` |
| cause_text | VARCHAR(500) NULL | Obligatorio salvo `UNDETERMINED` (FR-047). Si `cause_kind = PROBABLE`, el episodio debe tener al menos una evidencia no anulada; es una regla de dominio de `ClosureRequirements` |
| recommendations | VARCHAR(1000) NULL | |
| final_evidence_waiver | VARCHAR(300) NULL | Justificación si no hay evidencia final |
| closure_communication_waiver | VARCHAR(300) NULL | Justificación si no hay comunicación de cierre |
| closure_checks | JSONB NULL | Confirmaciones de FR-047, solo en `EQUIPMENT`: `{platformNormal, stabilityChecked, noAffectedUser, postTest}`, cada una `CONFIRMED` o `NOT_APPLICABLE` |
| deviation_justification | VARCHAR(500) NULL | Justificación de la desviación frente a la referencia (FR-053) |

**Reglas**:

- Un ticket con `closure_result` `DUPLICATE` o `REGISTERED_IN_ERROR` en su último episodio no se reabre (FR-049).
- Índice: (`ticket_id`, `ordinal`).

## inc_milestone y inc_milestone_revision

**inc_milestone**: cinco filas por episodio, creadas con él (T0 `RECORDED` y T1 a T4 `PENDING`).

| Campo | Tipo | Reglas |
|-------|------|--------|
| episode_id | UUID FK | |
| kind | VARCHAR(2) | `T0` a `T4` |
| status | VARCHAR(16) | `PENDING`, `RECORDED`, `NOT_APPLICABLE` o `NO_DATA` |
| occurred_at | TIMESTAMPTZ NULL | NOT NULL si y solo si `RECORDED` |
| reason | VARCHAR(300) NULL | Obligatorio en `NOT_APPLICABLE` y `NO_DATA`, y en `RECORDED` retroactivo |
| retroactive | BOOLEAN | `true` si `occurred_at` es más de 1 min anterior al momento del registro (FR-018, FR-041) |
| order_warning_ack | BOOLEAN | Marca de orden T1 ≤ T2 ≤ T3 incumplido y justificado (FR-043) |
| recorded_by / recorded_at | UUID / TIMESTAMPTZ | Autor y momento del último registro |

**Reglas de validación** (de dominio, con `CHECK` donde se puede):

- PK (`episode_id`, `kind`).
- T0 siempre está `RECORDED`.
- T4 no admite `NOT_APPLICABLE` ni `NO_DATA`: el cierre siempre registra la hora.
- `occurred_at` no puede ser anterior a T0 ni posterior al momento del registro.
- T4 debe ser posterior o igual a todo hito `RECORDED` del episodio.
- Al cerrar, T1 a T3 no pueden quedar en `PENDING`.

**inc_milestone_revision**: solo se anexan filas; nunca se actualizan ni se borran.

| Campo | Tipo | Reglas |
|-------|------|--------|
| id | UUID PK | |
| episode_id, kind | | |
| previous_status / previous_occurred_at / previous_reason | | Nulos en el primer registro |
| new_status / new_occurred_at / new_reason | | |
| justification | VARCHAR(500) NULL | Obligatoria en las correcciones (FR-036) |
| recorded_by / recorded_at | | Necesarios para reconstruir lo conocido en un instante X (research R5) |

Índice: (`episode_id`, `kind`, `recorded_at`).

## inc_event (cronología)

| Campo | Tipo | Reglas |
|-------|------|--------|
| id | UUID PK | |
| ticket_id | UUID FK | |
| episode_id | UUID NULL | El episodio vigente cuando ocurrió |
| type | VARCHAR(30) | Lista abajo |
| occurred_at | TIMESTAMPTZ | Hora del hecho, que puede ser retroactiva |
| recorded_at / recorded_by | | |
| retroactive | BOOLEAN | |
| text | VARCHAR(1000) NULL | Observación, acción, solicitud |
| target_kind | VARCHAR(20) NULL | Obligatorio en `ESCALATED`: `TECHNICAL` (técnicos propios o del proveedor), `SUPERVISION`, `PLATFORM_SUPPORT` u `OTHER`. Solo `TECHNICAL` cuenta para la referencia de escalamiento (FR-052) |
| target_role_or_provider | VARCHAR(150) NULL | En `ESCALATED`: nombre del rol o del proveedor |
| details | JSONB NULL | Campos acotados por tipo y validados por el dominio, nunca libres desde el cliente |
| notifiable | BOOLEAN | Para la campana futura (research R17) |
| annulled_at / annulled_by / annul_reason | NULL | La anulación es la única modificación permitida |

**Tipos de evento**:

- `TICKET_CREATED`
- `VALIDATION`
- `CORRECTIVE_ACTION`
- `ESCALATED`
- `OBSERVATION`
- `DATA_CORRECTED` (con `details`: `field` (`DESCRIPTION` o `REPORT_ORIGIN`), `previous`, `new` y `justification`; FR-036)
- `PRIORITY_CHANGED`
- `RESPONSIBLE_ASSIGNED`
- `SHIFT_HANDOVER` (con `details`: `outgoingResponsibleId`, `incomingResponsibleId`, `currentStatus`, `pending`, `nextCheckAt`, `closureCriterion`; FR-033)
- `RECLASSIFIED` (con el tipo anterior y el nuevo)
- `VISIT_STAGE`
- `COMMUNICATION_RECORDED`
- `EVIDENCE_ADDED`
- `MILESTONE_RECORDED`
- `MILESTONE_CORRECTED`
- `TICKET_CLOSED`
- `TICKET_REOPENED`
- `RELATION_ADDED`

**Reglas**:

- Las acciones que el mensaje incluye con «Incluir acciones realizadas» (FR-056a) son los eventos no anulados del episodio vigente de tipo `CORRECTIVE_ACTION` y `ESCALATED`.
- Índices:
  - (`ticket_id`, `recorded_at`);
  - parcial (`recorded_at`) WHERE `notifiable`.

## inc_visit

| Campo | Tipo | Reglas |
|-------|------|--------|
| id | UUID PK | |
| ticket_id / episode_id | UUID FK | |
| provider_name | VARCHAR(120) NULL | Escrito a mano. Se sugieren los nombres ya usados (P-08) |
| stage | VARCHAR(20) | `REQUESTED`, `ASSIGNED`, `CONFIRMED`, `CANCELLED`, `POSTPONED`, `EXECUTED` o `REPORT_RECEIVED`. Es la etapa actual |
| cancel_decided_by / cancel_evidence_note / reescalation_condition | VARCHAR NULL | Obligatorios en `CANCELLED` y `POSTPONED` (Protocolo §9.4) |

**Reglas**:

- Cada cambio de etapa crea un evento `VISIT_STAGE` con su hora.
- Transiciones permitidas: `REQUESTED` → `ASSIGNED` → `CONFIRMED` → `EXECUTED` → `REPORT_RECEIVED`. Desde `REQUESTED`, `ASSIGNED` y `CONFIRMED` se puede pasar a `CANCELLED` o `POSTPONED`, y desde `POSTPONED` se vuelve a `ASSIGNED` o `CONFIRMED`.
- Los reportes cuentan las visitas solicitadas, confirmadas, canceladas o aplazadas y ejecutadas por etapa alcanzada, no solo por la etapa actual.

## inc_communication

| Campo | Tipo | Reglas |
|-------|------|--------|
| id | UUID PK | |
| ticket_id / episode_id | UUID FK | |
| channel | VARCHAR(30) | `REDAE_INPSERCOM`, `KIA_ELECTROLINERAS`, `CARGADORES_ELECTRICOS`, `AUTOEASTERN_ENERLINK`, `DIRECTIVOS`, `EMAIL` u `OTHER` |
| channel_detail | VARCHAR(150) NULL | Obligatorio si `OTHER` |
| kind | VARCHAR(10) | `INITIAL`, `UPDATE` o `CLOSURE` |
| sent_at | TIMESTAMPTZ | Hora real informada por el analista. No puede ser futura |
| retroactive | BOOLEAN | |
| justification | VARCHAR(300) NULL | Obligatoria si es retroactiva |
| recorded_by / recorded_at | | |
| annulled_* | | Igual que los eventos |

Copiar el texto no crea ninguna fila (FR-059). La comunicación de cierre se cumple con una fila `CLOSURE` no anulada del episodio, o con `closure_communication_waiver`.

## inc_evidence

| Campo | Tipo | Reglas |
|-------|------|--------|
| id | UUID PK | |
| ticket_id | UUID FK | |
| event_id | UUID NULL | El evento que la aporta. Las evidencias de cierre usan el evento `TICKET_CLOSED` |
| communication_id | UUID NULL | |
| purpose | VARCHAR(12) | `GENERAL`, `INITIAL` o `FINAL` |
| storage_ref | VARCHAR(255) | Referencia opaca de `FileStorage` |
| format | VARCHAR(8) | `JPEG`, `PNG`, `WEBP` o `PDF`, según la firma binaria |
| size_bytes | BIGINT | Como máximo `sarc.incidents.evidence.max-bytes` (10 MB) |
| sha256 | CHAR(64) | |
| original_name | VARCHAR(200) | Saneado |
| uploaded_by / uploaded_at | | |

Una evidencia no se borra: si se cargó por error, se anula su evento asociado.

## Reglas de estado

```text
Ticket:   OPEN ──cerrar(FR-047/048/048a)──▶ CLOSED ──reabrir(FR-049)──▶ OPEN
Episodio: OPEN ──cerrar──▶ CLOSED   (cada reapertura crea un episodio nuevo en OPEN)
Hito T1–T3: PENDING ─▶ RECORDED | NOT_APPLICABLE | NO_DATA   (correcciones entre estos tres, con revisión)
Hito T4:    PENDING ─▶ RECORDED (solo al cerrar)
```

- Un ticket está `OPEN` si y solo si su último episodio está `OPEN`.
- No hay más estados principales. Las condiciones del tablero (FR-062) se derivan:
  - pendiente de validación: T1 `PENDING`;
  - en atención: T2 `RECORDED` y T3 `PENDING`;
  - recuperado en observación: T3 `RECORDED` y T4 `PENDING`.

## Reglas derivadas (dominio puro)

- **`TicketStateAt(E)`** y **`IncludedIn([S, E))`**, según research R5. Usan la `occurred_at` de T0 y T4 en estado `RECORDED`. Un episodio abierto tiene T4 ausente.
- **`MilestonesAsKnownAt(X)`**: aplica las revisiones con `recorded_at ≤ X`. Solo se usa para reconstruir y comprobar informes ya emitidos.
- **`IncidentKpiCalculator`**: las duraciones por par (T1−T0, T2−T0, T3−T0, T4−T0, T4−T3) en minutos enteros redondeados hacia abajo, más el juicio de referencia con la versión del episodio:
  - `WITHIN`: dentro de la referencia;
  - `OUTSIDE`: fuera de la referencia;
  - `NOT_EVALUABLE`: falta el hito necesario;
  - `NO_REFERENCE`: el tipo no tiene referencia.

  Escalamiento de la Espera de conexión (FR-052). Se calcula así:
  - `waitEnd` es T3 registrado o, con el episodio abierto, el corte. `esc` es el primer evento `ESCALATED` no anulado con `target_kind = TECHNICAL` y `occurred_at ≤ waitEnd`; un escalamiento posterior a `waitEnd` no cuenta.
  - Si `esc` existe y `esc.occurred_at − T0 ≤ escalation_max_min`, el episodio está `WITHIN`.
  - Si no, se compara `waitEnd − T0`: si supera `escalation_max_min`, `OUTSIDE`; si no, `WITHIN`.
  - Si el episodio está cerrado sin T3 registrado, `waitEnd` no existe. Con un `esc` hecho dentro de `escalation_max_min`, `WITHIN`; con uno más tardío, `OUTSIDE`; sin escalamiento técnico, `NOT_EVALUABLE`.
- **`TicketMessageComposer`**: el texto con número, elemento, ubicación, Maps, novedad y estado, y las líneas opcionales de T0 y de acciones. Nunca incluye UID/PUK ni URL OCPP.

## Puerto público nuevo: `IncidentAssetDirectory`

Lo implementa Inventario. Expone solo lo que necesita Incidencias:

- `search(text, limit)`: equipos activos por nombre o código, sin distinguir mayúsculas ni tildes.
- `findByIds(ids)`: incluye inactivos y eliminados, porque el histórico les pertenece. Devuelve `IncidentAsset(id, name, assignedCode, network, infrastructureType, location, mapsUrl, adminStatus, connectors[])`.
- `IncidentConnector(id, position, typeLabel, active)`.

Nunca expone UID/PUK, URL OCPP ni serie (RNF-SEG-07).

## Puerto público nuevo: `IncidentStaffDirectory`

Lo implementa Identidad, en `shared/security`. Incidencias no puede leer tablas ni clases de Identidad, y `/api/v1/users` es solo para el Administrador, así que este puerto da lo mínimo:

- `activeEditors()`: usuarios con estado `ACTIVE` y rol `ADMINISTRADOR` o `ANALISTA`, como `StaffMember(id, fullName)`, ordenados por nombre;
- `findActiveEditor(id)`: el mismo filtro para un usuario; vacío si no existe, está inactivo o no tiene rol de edición;
- `namesOf(ids)`: nombres de cualquier usuario, incluidos los inactivos, para mostrar autores de eventos y responsables anteriores.

Nunca expone correo, contraseña, estado de bloqueo ni roles.
