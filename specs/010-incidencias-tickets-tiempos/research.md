# Research: Incidencias, tickets y tiempos

**Feature**: `010-incidencias-tickets-tiempos` | **Date**: 2026-11-20 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Cada apartado sigue el mismo formato: **Decisión**, **Motivo** y **Alternativas descartadas**. Ninguna decisión de esta investigación crea reglas de negocio. Todas aplican requisitos de la spec, que se citan entre corchetes. El contexto técnico no tiene incógnitas abiertas. Lo único que queda pendiente es de negocio: P-01 y P-02, tratados en R2 y R6.

## R1. Un módulo nuevo `incident` dentro del monolito modular

- **Decisión**: crear `modules/incident` en el backend y `features/incidents` en el frontend. Ambos siguen las capas existentes:
  - backend: `api`, `application`, `domain` e `infrastructure`;
  - frontend: `presentation`, `application`, `data-access` y `domain`.

  El módulo depende solo del núcleo compartido (`shared`). Para los datos de inventario usa un puerto público nuevo, `shared/catalog/IncidentAssetDirectory`, que implementa Inventario. Se agrega una regla ArchUnit (`incident_depends_only_on_the_shared_kernel`) igual a la de Intermitencias.
- **Motivo**: las reglas de `ArchitectureTest` ya prohíben que un módulo dependa de otro. `EquipmentDirectory` y `KiaPointDirectory` no exponen ubicación, enlace de Maps ni conectores, y RF-006.2 pide esos datos [FR-006].
- **Alternativas descartadas**:
  - Ampliar `EquipmentDirectory`: mezclaría la conciliación de identidades de Consumo con la ficha operativa. El precedente de `KiaPointDirectory` separa los puertos por consumidor.
  - Leer las tablas `asset_*` directamente: viola las fronteras entre módulos.

## R2. Numeración `TICKET-NNN` sin duplicados, sin huecos y bloqueada en producción

- **Decisión**:
  - **Contador**: una tabla `inc_ticket_numbering` con una sola fila (`id = 1`). Guarda `start_number` (nulo hasta configurarlo), `next_number`, `environment` (`PRODUCTION` o `TEST`), quién lo configuró y cuándo.
  - **Asignación**: al crear un ticket se ejecuta `UPDATE … SET next_number = next_number + 1 WHERE id = 1 AND start_number IS NOT NULL RETURNING next_number - 1`, en la misma transacción que el `INSERT`. El bloqueo de fila serializa las creaciones simultáneas. Si la transacción revierte, el incremento también revierte: no hay número duplicado ni hueco. Además, `ticket_number` es `UNIQUE`, y el código mostrado (`'TICKET-' || lpad(n::text, 3, '0')`, que crece a 4 o más dígitos de forma natural) es una columna generada, también única.
  - **Idempotencia**: cada solicitud de creación lleva un `clientRequestId` (UUID generado por el cliente al abrir el formulario) con restricción única. Un reenvío o un doble clic devuelve el mismo ticket [FR-015].
  - **Configuración**: solo un Administrador puede configurar el contador, y solo mientras no exista ningún ticket. Debe escribir el número dos veces. Queda auditada. La migración no siembra ningún número [FR-013].
  - **Entorno**: la propiedad `sarc.incidents.numbering.environment` vale `PRODUCTION` en `application-prod.yaml` y `TEST` en `application-dev.yaml`. Es opcional: si falta, la aplicación arranca igual, pero configurar la numeración y crear tickets se rechazan con `TICKET_NUMBERING_ENVIRONMENT_MISMATCH`. Así no se rompe el arranque de los demás módulos ni de las pruebas existentes. Las pruebas de Incidencias la fijan en `TEST`. Al crear un ticket se comprueba primero el ambiente: si la propiedad falta o no coincide con el `environment` guardado, la respuesta es `TICKET_NUMBERING_ENVIRONMENT_MISMATCH`. Solo después, si el contador no está configurado, la respuesta es `TICKET_NUMBERING_NOT_CONFIGURED`. Con la propiedad ausente, por tanto, siempre sale `TICKET_NUMBERING_ENVIRONMENT_MISMATCH`. Esto impide que una base de prueba restaurada en producción, o al revés, siga numerando con valores de otro entorno [FR-014].
  - **P-01**: el número inicial de producción se pide al responsable justo antes de la puesta en marcha y lo configura el Administrador en producción. Los ambientes de prueba usan números ficticios altos (por ejemplo, 9001) que no se parecen a la secuencia real.
- **Motivo**: es atómico sin bloqueos consultivos adicionales, es comprobable con una prueba de concurrencia y no depende de secuencias de PostgreSQL. Una secuencia (`nextval`) deja huecos al revertir y no se reinicia de forma auditada. La propiedad por perfil sigue el patrón de V017 (`sarc.empty_initial_connector_catalog`).
- **Alternativas descartadas**:
  - Una `SEQUENCE` de PostgreSQL: deja huecos y su valor inicial no queda auditado.
  - `max(ticket_number) + 1` con bloqueo consultivo: funciona, pero requiere una lectura y un bloqueo aparte, y es fácil de usar mal desde otro código.
  - Un número por defecto en la migración: prohibido por FR-013.

## R3. Agregado Ticket con episodios, hitos e historial de solo anexión

- **Decisión**: el ticket es la raíz del agregado, con su versión optimista (`BaseAuditableEntity.version`). Contiene:
  - **Episodios** (`inc_episode`): uno al crear el ticket y uno nuevo por cada reapertura. Cada episodio guarda la versión de referencias (R6) vigente en su T0, el resultado y la causa del cierre.
  - **Hitos** (`inc_milestone`): una fila por episodio y tipo T0 a T4, con su estado (`PENDING`, `RECORDED`, `NOT_APPLICABLE` o `NO_DATA`), la hora, el motivo, la marca de retroactivo, quién lo registró y cuándo. Hay una restricción única por episodio y tipo.
  - **Revisiones de hito** (`inc_milestone_revision`): solo se anexan. Guardan el valor anterior y el nuevo, la justificación, el autor y el momento de registro (`recorded_at`).
  - **Eventos** (`inc_event`): la cronología, también de solo anexión. Cada evento tiene su tipo, `occurred_at` (hora del hecho), `recorded_at` (hora del registro), el autor, campos tipados y un `details` JSONB acotado. Un evento erróneo se anula con `annulled_at` y un motivo; nunca se borra [FR-036].
  - **Tablas propias** para lo que tiene ciclo propio o se cuenta en reportes: visitas (`inc_visit`, con eventos por etapa), comunicaciones (`inc_communication`) y evidencias (`inc_evidence`).

  Toda escritura sobre cualquier parte del agregado pasa por el ticket y sube su versión. El cliente envía la versión leída y recibe `409` si otra persona escribió antes [edición simultánea].
- **Motivo**: las columnas T0 a T4 por episodio permiten consultas de reportes simples e indexables. Las tablas de revisión y de eventos dan la auditoría y la reconstrucción histórica (R5). Es el mismo patrón que `itm_window` con `itm_window_revision`, ya probado.
- **Alternativas descartadas**:
  - *Event sourcing* puro, con el estado derivado de eventos: es más costoso de consultar y de migrar, y no lo usa ningún otro módulo de SARC.
  - Columnas `t1_at`, `t1_status`… dentro de `inc_episode`: duplica cinco veces las columnas de estado, motivo y autor, y complica las revisiones por hito.
  - Un estado principal por cada hito: contradice la decisión confirmada de dos estados [FR-030].

## R4. Elemento afectado con cuatro clases y datos conservados en T0

- **Decisión**: `inc_ticket.element_class` toma uno de tres valores: `EQUIPMENT`, `CHATBOT_SERVICE` o `SITE_ELEMENT`. Los conectores elegidos van en `inc_ticket_connector`; un ticket de clase `EQUIPMENT` sin conectores afecta al equipo completo. Unas restricciones `CHECK` garantizan que cada clase tenga solo sus columnas:
  - `EQUIPMENT` exige `equipment_id`. Conserva `equipment_label_at_t0` y `network_at_t0`, la red del equipo en T0 (P-14).
  - `CHATBOT_SERVICE` no tiene equipo, red, ubicación ni conectores. Admite un `chatbot_scope` opcional (`TOTAL` o `PARTIAL`) [Q2].
  - `SITE_ELEMENT` exige `site_element_kind` (`VEHICLE_BARRIER` u `OTHER`), con `element_name` obligatorio si el valor es `OTHER`. Exige también una de dos fuentes de ubicación:
    - `reference_equipment_id`;
    - o `manual_site_name` con `manual_location`, y un `manual_maps_url` opcional.

    Conserva `location_at_t0` y `maps_url_at_t0`. Nunca tiene `network_at_t0` ni conectores [FR-001a].

  La red de los repartos es `network_at_t0`, o `Sin red` cuando es nula.

  **Aviso de duplicado [FR-016]**: el dominio calcula una `duplicate_key` al crear el ticket y la guarda:
  - `EQUIPMENT`: `EQ:{equipment_id}`;
  - `CHATBOT_SERVICE`: `CHATBOT`;
  - `SITE_ELEMENT`: `SITE:{kind}:{sitio}:{elemento}`. El sitio es `reference_equipment_id` o el `manual_site_name` normalizado (minúsculas, sin tildes ni espacios repetidos); el elemento es `element_name` normalizado o vacío.

  La búsqueda de tickets abiertos con la misma clave usa un índice parcial `WHERE state = 'OPEN'`. **No es única**: el analista puede crear otro ticket con motivo. Para conectores, la clave es la del equipo. Así, un ticket de conector avisa de otro abierto del mismo equipo, completo o con otros conectores, y el analista decide.
- **Motivo**: cumple FR-001 a FR-007. Las restricciones en la base son la red de seguridad, como en `asset_equipment`, y la regla vive en el dominio. Conservar la red y la etiqueta en T0 cumple P-14 y D12. La interfaz muestra el nombre actual y el detalle muestra la etiqueta del momento.
- **Alternativas descartadas**:
  - Crear equipos ficticios para el chatbot o las barreras: prohibido expresamente.
  - Una tabla polimórfica por clase: hay pocos campos y las restricciones `CHECK` bastan.
  - Heredar la red del equipo de referencia: el responsable eligió «Sin red».

## R5. Estado al final del período y reconstrucción verificable

- **Decisión**: una política pura de dominio, `TicketStateAt`, calcula el estado de un ticket en un instante `E` a partir de sus episodios. El ticket está **Abierto** si existe un episodio con `T0 < E` y, además, T4 ausente o `T4 ≥ E` [FR-074]. Sus reglas:
  - **Inclusión en un período** `[S, E)` [FR-073]: se incluye si existe un episodio con `T0 < E` y, además, T4 ausente o `T4 ≥ S`.
  - **Momento de evaluación**: `E` es el mínimo entre el fin del rango y el corte. Con «Todo el historial», `E` es el corte.
  - **Reaperturas**: con varios episodios no se duplica el ticket, porque la evaluación es «existe un episodio», y el ticket aparece en una sola fila.
  - **SQL**: la consulta de reportes aplica el mismo predicado con `EXISTS` sobre `inc_episode` y `inc_milestone`. El cálculo de dominio se prueba contra la consulta con los mismos datos.

  Para la **reconstrucción** («qué mostró el informe del 3 de noviembre»), las revisiones de hitos guardan `recorded_at`. La función `MilestonesAsKnownAt(X)` reconstruye T0 y T4 tal como se conocían en el instante X, deshaciendo las revisiones con `recorded_at > X`. Cada informe emitido queda en `report_issued` con criterios, corte, número de filas y huella SHA-256 (`ReportArchive`). Una prueba regenera el informe «como se conocía en el corte» y compara la huella [FR-074a, FR-086, SC-012].
- **Motivo**: separa el cálculo correcto actual (con las correcciones) de la evidencia de lo ya emitido. Los informes emitidos no se alteran, porque son archivos archivados con huella. Lo que se puede reconstruir se deriva de datos de solo anexión.
- **Alternativas descartadas**:
  - Guardar instantáneas de estado diarias: duplica datos y no explica las correcciones.
  - Congelar los hitos después de emitir un informe: impediría las correcciones justificadas que exige el PRD.
  - Recalcular los informes emitidos al corregir: contradice FR-074a.

## R6. Referencias de tiempo y prioridades por versión

- **Decisión**: dos tablas sembradas por migración.
  - `inc_reference_version`: `code`, `label`, `effective_from`, `source_note`. La versión inicial `PROTOCOLO-FIRMADO-1` se rotula «Protocolo de monitoreo firmado (versión por confirmar)».
  - `inc_reference_rule`: por versión y tipo, `validation_max_min`, `recovery_max_min`, `escalation_max_min`, `persistent_after_min` (30; la duración exacta, en segundos, debe ser estrictamente mayor que 30 × 60, porque «más de 30»), `no_recovery_max` y `proposed_priority`.

  Cada episodio guarda `reference_version_id`, la versión vigente en su T0. Si una corrección de T0 cruza la fecha de vigencia de otra versión, se vuelve a resolver la versión y la revisión lo registra. Una versión nueva se agrega solo con una migración nueva, es decir, con un cambio controlado del sistema. Al confirmarse P-02 solo cambia `label` con otra migración, nunca los valores.
- **Motivo**: cumple P-03, P-04 y P-05 sin inventar umbrales. FR-054 queda verificable porque los tipos sin regla no tienen fila.
- **Alternativas descartadas**:
  - Referencias en código Java: un cambio alteraría los cálculos de episodios pasados.
  - Una pantalla de administración: P-11 decidió mantenerlas con cambios controlados.

## R7. Catálogo de tipos de incidencia

- **Decisión**: el enumerado de dominio `IncidentType` declara las clases de elemento que admite cada tipo [FR-022]. Tiene su `CHECK` en la base y etiquetas en español en `IncidentLabels`, para la interfaz y los informes. «Incidencia no catalogada» admite las tres clases. Una reclasificación es un evento con justificación [FR-021].
- **Motivo**: es el mismo estilo de `WindowState` y de los estados de Consumo. Con P-11, el cambio de catálogo es una versión del sistema.
- **Alternativas descartadas**: una tabla editable de tipos (fuera de alcance por P-11).

## R8. Prioridad, responsable y relevo

- **Decisión**: el ticket guarda `priority` (`MEDIUM`, `HIGH`, `CRITICAL`), propuesta desde `inc_reference_rule` al crear, y `current_responsible_id`, que es el usuario creador al inicio. Cambiar la prioridad exige motivo y genera el evento `PRIORITY_CHANGED`. El relevo de turno es el evento `SHIFT_HANDOVER`. Sus campos tipados son: responsable saliente, responsable entrante, estado actual, pendientes, próxima verificación y criterio de cierre. El relevo actualiza `current_responsible_id` y `current_responsible_name` [FR-033, FR-034].
- **Usuarios elegibles**: Identidad implementa un puerto nuevo, `shared/security/IncidentStaffDirectory` (data-model.md), que lista los editores activos, valida uno y da nombres. Incidencias lo expone en `GET /responsibles`, con permiso EDITORS. El nombre del responsable se copia en el ticket al asignarlo, para mostrarlo y ordenar sin cruzar módulos.
- **Motivo**: cumple la decisión de no tener estados adicionales. El responsable entrante debe ser un usuario activo con rol Analista o Administrador.
- **Alternativas descartadas**: abrir `GET /api/v1/users` a los analistas, porque expone datos de administración de cuentas; o leer `UserAccount` desde Incidencias, porque rompe la regla de módulos de `ArchitectureTest`.

## R9. Evidencias: imágenes y PDF en Cloudinary

- **Decisión**:
  - **Formato**: se reutiliza `FileStorage` y se agrega `shared/storage/DocumentFormat.PDF`, que reconoce la firma `%PDF-`, junto a `ImageFormat` (JPEG, PNG y WEBP). Se rechaza cualquier otro formato, incluidos videos y ejecutables, con el código `EVIDENCE_FORMAT_NOT_ALLOWED`.
  - **Tamaño**: máximo 10 MB por archivo, con la propiedad `sarc.incidents.evidence.max-bytes`, por debajo del tope multipart de 20 MB.
  - **Almacenamiento**: carpeta `incidents/evidence`. Las imágenes van con `store` y los PDF con `storeDocument`. La tabla `inc_evidence` guarda la referencia opaca, el formato, el tamaño, el SHA-256, el nombre original saneado, el autor y el evento asociado.
  - **Acceso**: con URLs firmadas temporales, solo después de comprobar el permiso de lectura.
  - **Pruebas**: en los ensayos con la cuenta real, la carpeta es la del entorno de ensayo y no la de producción, porque la cuenta se comparte con otro proyecto.
- **Motivo**: cumple RNF-DAT-01 y TRD 16.1 (el formato real se comprueba por su firma). Es el puerto ya existente.
- **Alternativas descartadas**: guardar los archivos en disco o en la base (TRD 02.1 lo prohíbe).

## R10. Permisos

- **Decisión**: la clase `IncidentPermissions` define:
  - `EDITORS = hasAnyRole('ADMINISTRADOR','ANALISTA')` para crear, registrar, corregir, comunicar, cerrar y reabrir;
  - `READERS = hasAnyRole('ADMINISTRADOR','ANALISTA','GERENTE','SUPERVISOR','ASISTENTE')` para consultas, tablero e informes;
  - `NUMBERING = hasRole('ADMINISTRADOR')`.

  Cada método de servicio lleva `@PreAuthorize`, como en Intermitencias. Un usuario con varios roles recibe la unión de permisos. En el frontend, `INCIDENT_EDITORS` alimenta `roleGuard` y oculta las acciones, solo como experiencia de usuario [FR-090 a FR-092].
- **Motivo**: sigue el patrón vigente y cumple la matriz de seguridad, que se prueba por endpoint.

## R11. Actualización automática

- **Decisión**: reutilizar `core/http/auto-refresh.ts`, que refresca cada 60 s mientras la pantalla está a la vista y hay actividad humana reciente, junto con `RetainedValue` para no parpadear. La interfaz muestra «Actualizado a las hh:mm» y avisa cuando falla la red [FR-063, SC-006]. No se agrega ningún canal de servidor.
- **Motivo**: es el comportamiento acordado en los demás módulos. Un canal de servidor a cliente es materia de la feature transversal de notificaciones (Q4).
- **Alternativas descartadas**: SSE o WebSocket ahora; se anticiparían a la feature transversal.

## R12. Mensaje para WhatsApp compuesto en el servidor

- **Decisión**: `GET /api/v1/incidents/tickets/{id}/message?includeDetection=false&includeActions=false&kind=INITIAL|UPDATE|CLOSURE` devuelve el texto plano. Lo compone `TicketMessageComposer`, una función pura de dominio. El cliente lo muestra en un `textarea` editable y lo copia con `navigator.clipboard.writeText`. Si el portapapeles no está disponible, selecciona el texto y muestra la instrucción «Ctrl+C». Copiar no llama al servidor. «Registrar comunicación» es una operación aparte [FR-055 a FR-060].
- **Motivo**: un solo lugar garantiza que el texto nunca lleve UID/PUK ni URL OCPP (RNF-SEG-07) y que el informe y la interfaz coincidan. Además, se prueba sin navegador.
- **Alternativas descartadas**: componer el texto en el frontend; duplicaría reglas y expondría datos que la vista no necesita.

## R13. Filtros de período compartidos

- **Decisión**: mover `features/consumption/domain/period.ts` y el componente `period-filter` a `shared/ui/period/`, sin cambiar su comportamiento, porque ESLint prohíbe importar entre features. Consumo pasa a importar la versión compartida. «Control de tickets» agrega el valor `ALL_HISTORY` («Todo el historial»), que es el inicial. El tablero de gestión abre en «Este mes» (D13). El backend valida el límite de 366 días, salvo en `ALL_HISTORY` [FR-072, FR-084].
- **Motivo**: los accesos rápidos son idénticos a los de Consumo (Hoy, Últimos 7 días, Este mes, Mes anterior, Este año y Personalizado) y no hay que mantener dos copias.
- **Precaución**: es un *refactor* seguro. Las pruebas de Consumo se ejecutan antes y después del movimiento (skill `safe-refactor`).

## R14. Informes y archivo

- **Decisión**: dos clases de informe, `INCIDENT_CONTROL` e `INCIDENT_MANAGEMENT`.
  - **Generación**: con `CorporatePdf` y `CorporateWorkbook`, desde los datos de una sola consulta de lectura por informe. El tablero de gestión y sus descargas comparten el mismo servicio de resumen (`IncidentSummaryService`), así que las cifras coinciden por construcción [FR-086].
  - **Archivo**: `ReportArchive` guarda cada informe emitido.
  - **Cambios en `ReportPublication`**:
    - agregar las claves `ticketState`, `elementClass` y `incidentType` a la lista permitida;
    - admitir `selectionBasis = ALL_HISTORY`, sin `from` ni `to`;
    - admitir los `periodPreset` de Consumo (`TODAY`, `LAST_7_DAYS`, `THIS_MONTH`, `LAST_MONTH`, `THIS_YEAR`, `CUSTOM`) cuando la clase de informe sea de incidencias.

    El cambio solo agrega casos; las pruebas existentes de `ReportPublication` cubren que no cambie nada para Consumo, Uptime ni Intermitencias.
  - **Visibilidad**: los informes de incidencias no contienen datos restringidos. Por eso `IssuedReportKinds.restrictable` no los incluye y todos los lectores pueden descargarlos desde «Reportes».
  - **Logos**: los de `CorporateBranding`. El PDF de control reproduce la referencia visual de la spec: banda «REGISTRO OPERATIVO», tres columnas y filas alternadas.
- **Motivo**: reutiliza el archivo y las huellas que ya exige RF-RPT-08.

## R15. Indicadores y gráficos

- **Decisión**: `IncidentKpiCalculator` es una función pura de dominio:
  - **Promedios**: por par de hitos, solo con los dos hitos registrados, con el numerador, el denominador y los conteos de «No aplica», «Sin dato» y abiertos.
  - **Porcentaje de validaciones ≤ 5 min**: sobre los episodios cuyo tipo tiene `validation_max_min`.
  - **Juicio de referencia**: `WITHIN`, `OUTSIDE`, `NOT_EVALUABLE` o `NO_REFERENCE`, con la versión del episodio. La atención se compara con T3−T0 (P-06).
  - **Exclusiones**: los cierres «Registrado por error» y «Duplicado» quedan fuera [FR-050].

  Los gráficos usan `sarc-chart-view` con una serie por gráfica y sin ponderación (P-07): por fecha (día de T0), red, tipo, prioridad, visitas, reincidencias, tiempos fuera de referencia y equipos repetidores.
- **Motivo**: cumple FR-045, FR-080 y FR-081, y se puede verificar con valores calculados a mano (SC-004).

## R16. Disponibilidad e intermitencias (P-13)

- **Decisión**: el módulo de incidencias no consulta Uptime ni Intermitencias desde el backend. El tablero de gestión muestra un apartado «Disponibilidad e interrupciones» con enlaces a `/uptime` y a `/intermitencias/tablero`, que conservan el período cuando es compatible. Las notas de metodología del PDF explican dónde está cada cifra y que la disponibilidad estimada de Intermitencias es de prueba y solo la ven Analista y Administrador. No se muestra la meta del 98 % [FR-083].
- **Motivo**: evita duplicar cálculos y respeta la visibilidad restringida de la disponibilidad estimada.
- **Alternativas descartadas**: incrustar cifras de Uptime en el PDF de gestión. Exigiría un puerto entre módulos y una regla de coherencia de cortes que nadie pidió.

## R17. Eventos para la campana futura (Q4)

- **Decisión**: `inc_event` incluye la columna `notifiable` (booleana), que vale `true` para `TICKET_CREATED`, `TICKET_CLOSED`, `TICKET_REOPENED`, `ESCALATED` y `PRIORITY_CHANGED` hacia `CRITICAL`. Hay un índice parcial por `recorded_at`. No hay campana, bandeja ni lectura de esos eventos [FR-096].
- **Motivo**: deja los datos listos sin anticipar el diseño transversal.

## R18. Migración y publicación

- **Decisión**: una sola migración nueva, `V021__incidencias_tickets.sql`, que solo agrega tablas `inc_*`, índices, restricciones y la siembra de la versión de referencias. No modifica tablas existentes; `report_issued.kind` no tiene `CHECK`. Antes de publicar, la migración se ensaya sobre una copia restaurada de producción en un PostgreSQL desechable, nunca sobre la base de compose. La puesta en marcha se publica con el flujo de la Feature 007. Después, el Administrador configura el número inicial (P-01).
- **Motivo**: cumple el Principio III (migraciones inmutables) y la práctica operativa del proyecto.

## R19. Volumen y rendimiento

- **Decisión**: el diseño se dimensiona para 5.000 tickets y 50.000 eventos. Índices previstos:
  - `inc_episode (ticket_id, ordinal)`;
  - `inc_milestone (episode_id, kind)`;
  - un índice de expresión sobre T0 y T4 registrados, para los predicados de período;
  - `inc_ticket (state, last_activity_at)`;
  - `inc_event (ticket_id, recorded_at)`.

  El listado se pagina en el servidor (`PageQuery`). El informe de control de todo el historial se genera en streaming y se mide en el banco de rendimiento (SC-009: 10 s o menos).
- **Motivo**: la secuencia externa va por unos 90 tickets en total, así que el volumen real está muy por debajo. El margen protege contra el crecimiento.

## Skills consultadas para este plan y su uso posterior

| Etapa | Skill | Uso |
|-------|-------|-----|
| Plan (ahora) | `speckit-plan` | Flujo y artefactos de este documento |
| Tareas | `speckit-tasks`, `speckit-analyze` | Desglose y análisis de consistencia entre spec, plan y tareas |
| Migración | `migration`, `postgresql-code-review` | Revisar V021 (solo agrega, restricciones, índices) y ensayarla sobre una copia restaurada |
| Dominio y backend | `clean-architecture`, `spring-boot-engineer`, `spring-data-jpa` | Agregado Ticket, puertos, repositorios, bloqueo optimista, consultas de reportes |
| Seguridad | `springboot-security`, `security-review` | Matriz de permisos, evidencias, URLs firmadas, ausencia de UID/PUK |
| Pruebas | `agent-skills:test-driven-development`, `agent-skills:test-engineer` (agente, si el responsable lo pide) | Pruebas que fallan primero en numeración, hitos, estado al final del período y KPI |
| Refactor del período | `safe-refactor` | Mover `period-filter` a `shared` sin cambio de comportamiento |
| Frontend | `angular-developer`, `impeccable` | Pantallas con Signals y Signal Forms; revisión visual en claro y oscuro, a 1366×768 y 360 px |
| Revisión | `code-review`, `caveman-review` o `agent-skills:review` | Revisión de cada historia antes de cerrarla |
| Verificación | `verify-and-stop`, `run` | Cierre de cada historia con evidencia, sin ampliar el alcance |
