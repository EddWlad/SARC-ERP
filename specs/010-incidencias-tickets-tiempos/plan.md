# Implementation Plan: Incidencias, tickets y tiempos

**Branch**: `main` local (Spec Kit reporta `010-incidencias-tickets-tiempos` por `.specify/feature.json`; este comando no crea ramas) | **Date**: 2026-11-20 | **Spec**: [spec.md](spec.md)

**Input**: la spec `specs/010-incidencias-tickets-tiempos/spec.md`, con las aclaraciones Q1 a Q5 y las decisiones P-03 a P-16 aprobadas el 2026-11-20, y el registro CC-PRD-010-20261120.

## Summary

El plan crea un módulo nuevo, `incident`, dentro del monolito modular. Registra incidencias de cuatro clases de elemento afectado:

- equipo completo;
- conectores concretos;
- servicio de chatbot;
- elementos del sitio, como barreras vehiculares u otros.

Cada ticket recibe un número `TICKET-NNN` que no se repite. Solo tiene dos estados principales, Abierto y Cerrado. Cada episodio lleva sus hitos T0–T4, y la cronología es de solo anexión. El módulo prepara el texto de WhatsApp para copiar a mano y emite dos informes archivados, «Control de tickets» y «Gestión de incidencias», en PDF y Excel.

El diseño se apoya en dos ideas:

1. **Hechos fechados y revisiones**: cada hito guarda cuándo ocurrió (`occurred_at`) y cuándo se registró (`recorded_at`), y cada corrección deja una revisión. Así se reconstruye el estado de cualquier ticket al final de cualquier período, y lo que se sabía en el momento de emitir un informe.
2. **Reutilización**:
   - `AuditTrail`, `FileStorage`, `ReportArchive`, `CorporatePdf` y `CorporateWorkbook`;
   - un puerto nuevo del catálogo que implementa Inventario (`IncidentAssetDirectory`);
   - un puerto nuevo de solo lectura que implementa Identidad (`IncidentStaffDirectory`), para elegir y nombrar responsables;
   - en la interfaz, los patrones de Consumo, Inventario e Intermitencias.

El esquema se crea con una sola migración, `V021`, que solo agrega tablas. La numeración de producción no se siembra: P-01 se pide al responsable justo antes de publicar, y los ambientes de prueba usan números ficticios.

## Verificación previa de la especificación

Antes de diseñar, la spec y la trazabilidad se actualizaron con las decisiones aprobadas al iniciar el plan:

| # | Decisión | Estado en la spec | Acción |
|---|----------|-------------------|--------|
| 1 | P-16 Histórico del Excel | FR-095 | No se importa; SARC contiene solo los tickets creados en el sistema |
| 2 | P-10 Barreras y otros elementos del sitio | FR-001, FR-001a, FR-005, FR-016, FR-022, FR-048a, FR-060, FR-075, US3-5 a US3-7 | Tres precisiones confirmadas por el responsable: (a) ubicación por **equipo de referencia** o manual; (b) grupo **«Sin red»**; (c) catálogo **Barrera vehicular + Otro elemento del sitio**. Sin equipos ficticios, conectores ni red heredada |
| 3 | P-12 Estado al final del período | FR-073, FR-074, FR-074a, US6-4a y 4b, casos límite | Regla de inclusión Q1 conservada; estado con `TicketStateAt(E)`; los informes emitidos no cambian |
| 4 | P-03 Referencias versionadas | FR-051a | Versión inmutable por episodio en T0 |
| 5 | P-04 KIA persistente | FR-051 | Más de 30 min (estricto); es una clasificación, no un límite |
| 6 | P-05 Prioridades | FR-034 | Tabla propuesta por tipo; ajustable con motivo |
| 7 | P-06 Recuperación y cierre | FR-045, FR-052 | T3−T0 y T4−T0 por separado; la referencia se compara con T3−T0 |
| 8 | P-07 Gráficos sin ponderar | FR-082 | Sin pesos inventados |
| 9 | P-08 Visitas manuales | FR-035 | Proveedor escrito a mano, con sugerencias |
| 10 | P-11 Catálogo controlado | FR-020 | Cambios por cambio controlado del sistema; incluye «Incidencia no catalogada» |
| 11 | P-13 Uptime e Intermitencias | FR-083 | Se remite a esos módulos; no se recalcula |
| 12 | P-14 Red histórica | FR-007 | `network_at_t0` |
| 13 | Lista de control | `checklists/requirements.md` | Se corrigió la nota obsoleta «Quedan tres»; 16/16 |
| 14 | Pendientes | Sección «Decisiones» | Solo **P-01** (número inicial) y **P-02** (versión del protocolo). Ninguna bloquea el plan ni el desarrollo |

## Technical Context

**Language/Version**: Java 25 (backend) y TypeScript con Angular 22 (frontend).

**Primary Dependencies**:

- Spring Boot 4.1.1, Spring Security con `@PreAuthorize` y Spring Session JDBC;
- Spring Data JPA con Hibernate, Jakarta Validation y Flyway;
- OpenPDF (`CorporatePdf`), Apache POI (`CorporateWorkbook`) y el SDK de Cloudinary (`FileStorage`);
- Angular Material 3, Signals y Signal Forms, `@lucide/angular` y Chart.js (`sarc-chart-view`).

No se agrega ninguna dependencia.

**Storage**: PostgreSQL 18, con tablas nuevas `inc_*` ([data-model.md](data-model.md)). Las evidencias van a Cloudinary en `incidents/evidence` y los informes emitidos al archivo vigente (`reports/incident_*`).

**Testing**:

- Backend: JUnit 5, Testcontainers con PostgreSQL 18, ArchUnit y `OpenApiContractTest`.
- Frontend: `npm test`, `lint` y `format:check`.
- Navegador: `Feature010BrowserSandbox`.

**Target Platform**: VPS con Ubuntu, Docker Compose y PostgreSQL 18, publicado con el flujo de la Feature 007.

**Project Type**: aplicación web: backend en monolito modular y SPA Angular.

**Performance Goals** (research R19, SC-009):

- listado paginado en menos de 1 s y tablero de un año en menos de 2 s, con 5.000 tickets;
- «Control de tickets» de todo el historial en 10 s o menos;
- creación de un ticket en menos de 500 ms.

**Constraints**:

- zona `America/Guayaquil`, minutos enteros redondeados hacia abajo, rangos con fin exclusivo de hasta 366 días;
- autorización en el servidor;
- nunca UID/PUK ni OCPP en ticket, mensaje o informe;
- repositorio público: datos sintéticos en las pruebas;
- migraciones inmutables;
- publicación con aprobación de `production`.

**Scale/Scope**:

- unos 90 tickets históricos en la secuencia externa; diseño para 5.000 tickets y 50.000 eventos;
- 7 historias, 82 FR y 12 SC;
- unas 6 pantallas y 9 diálogos nuevos.

Sin `NEEDS CLARIFICATION`: todo quedó resuelto en [research.md](research.md), R1 a R19.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Regla | Evaluación previa | Evidencia |
|-------|-------------------|-----------|
| I. Especificación antes de cambios | PASA | La spec aclarada existe en `specs/010-incidencias-tickets-tiempos/`, con escenarios verificables y decisiones constitucionales identificadas |
| II. Reglas de negocio explícitas | PASA | Cada regla del diseño cita su FR. Las restricciones `CHECK` reproducen FR-001a, FR-005 y FR-042, sin crear políticas nuevas. P-01 y P-02 siguen abiertas sin una regla implícita: no hay número por defecto, y el rótulo de versión dice «por confirmar» |
| III. Migraciones inmutables | PASA | Solo `V021__incidencias_tickets.sql`, nueva; V001 a V020 intactas. Las referencias futuras van en migraciones nuevas (P-03) |
| D06 (conector por posición) | PASA | `inc_ticket_connector` usa el UUID de la posición y guarda la posición en T0 |
| D07 (reemplazo) | PASA | El ticket queda en el equipo original |
| D12 (nombres actuales) | PASA | Los informes y el mensaje usan el nombre actual; el detalle muestra la etiqueta en T0. Los informes archivados no se modifican |
| D13 (mes corriente) | PASA con nota de la spec | El tablero y el reporte de gestión inician en «Este mes». «Control de tickets» inicia en «Todo el historial» por decisión confirmada (spec, «Conformidad constitucional»); no es el tablero de consumo al que se refiere D13 |
| D04 (UID/PUK) | PASA | No se exponen ni se comparan; `IncidentAssetDirectory` no los devuelve |
| D14 (Top 5 con empates) | No aplica | No hay Top 5 de energía. Los «equipos repetidores» se ordenan por episodios, sin umbral ni etiqueta |
| D01 a D03, D05, D08 a D11 | No aplican | Sin cambios en usuarios, importación, horarios ni energía. Identidad solo agrega `IncidentStaffDirectoryService`, de solo lectura, que no toca correos, contraseñas ni la autogestión del administrador |
| Jerarquía PRD / TRD | PASA | Las diferencias con el PRD están en CC-PRD-010. No hay desviación técnica que requiera ADR aparte, salvo el módulo nuevo (ADR 0014) |

## Project Structure

### Documentation (this feature)

```text
specs/010-incidencias-tickets-tiempos/
├── spec.md
├── plan.md              # este archivo
├── research.md          # decisiones R1–R19 y skills por etapa
├── data-model.md        # tablas inc_*, estados, reglas derivadas, puerto
├── quickstart.md        # escenarios, permisos, regresiones, revisión visual
├── contracts/
│   ├── incidents-api.md      # endpoints, permisos y errores
│   ├── whatsapp-message.md   # plantilla y opciones
│   └── reports.md            # Control de tickets y Gestión: PDF, Excel y archivo
├── checklists/requirements.md
└── tasks.md             # lo genera /speckit-tasks (no este comando)
```

### Source Code (repository root)

```text
sarc-bakend/src/main/java/com/tidsec/sarc_bakend/
├── shared/catalog/
│   ├── IncidentAssetDirectory.java     # NUEVO puerto: búsqueda y lectura de equipos, conectores y ubicación
│   └── IncidentAsset.java              # NUEVO record (sin UID/PUK, OCPP ni serie)
├── shared/security/IncidentStaffDirectory.java  # NUEVO puerto: editores activos y nombres (sin correo ni roles)
├── shared/storage/DocumentFormat.java  # NUEVO: firma PDF (%PDF-), junto a ImageFormat
├── shared/report/ReportPublication.java   # se amplían las claves (ticketState, elementClass, incidentType, priority) y ALL_HISTORY
├── modules/inventory/application/service/
│   └── IncidentAssetDirectoryService.java  # NUEVO: implementa el puerto (solo lectura)
├── modules/identity/application/service/
│   └── IncidentStaffDirectoryService.java  # NUEVO: implementa IncidentStaffDirectory (solo lectura)
├── modules/reporting/domain/model/IssuedReportKinds.java  # + INCIDENT_CONTROL, INCIDENT_MANAGEMENT (no restringibles)
└── modules/incident/                   # NUEVO módulo
    ├── api/{controller,request,response}/   # Tickets, Milestones, Events, Visits, Communications, Evidence,
    │                                        # Message, Numbering, Summary, Reports
    ├── application/{service,command,result,port}/
    ├── domain/
    │   ├── model/ticket/       # Ticket (raíz), Episode, Milestone, MilestoneRevision, TicketEvent,
    │   │                       # Visit, Communication, Evidence, AffectedElement, DuplicateKey
    │   ├── model/reference/    # ReferenceVersion, ReferenceRule, IncidentType, Priority
    │   ├── model/numbering/    # TicketNumbering, TicketCode
    │   ├── policy/             # TicketStateAt, MilestonesAsKnownAt, ClosureRequirements,
    │   │                       # MilestoneOrderPolicy, RetroactivePolicy, IncidentKpiCalculator
    │   ├── message/            # TicketMessageComposer
    │   └── repository/
    └── infrastructure/{persistence,report,security}/   # generadores PDF/XLSX (IncidentPermissions vive en application/service: ArchUnit)

sarc-bakend/src/main/resources/db/migration/V021__incidencias_tickets.sql   # NUEVA
sarc-bakend/src/main/resources/application*.yaml   # sarc.incidents.numbering.environment, evidence.max-bytes
sarc-bakend/docs/adr/0014-modulo-incidencias.md     # NUEVO ADR (módulo, puertos, numeración, cortes)
sarc-bakend/docs/api/openapi.json                   # se regenera

sarc-frontend/src/app/
├── layout/navigation.ts                  # entrada «Incidencias»
├── app.routes.ts                         # ruta /incidencias (carga diferida)
├── shared/ui/period/                     # period.ts y period-filter trasladados desde Consumo (research R13) + ALL_HISTORY opcional
├── features/consumption/…                # solo cambia el import del período
├── features/reports/domain/issued-report.ts  # + INCIDENT_CONTROL «Control de tickets», INCIDENT_MANAGEMENT «Gestión de incidencias»
└── features/incidents/
    ├── incidents.routes.ts
    ├── domain/        # permisos, etiquetas de tipo, clase y estado, marcas de hito, duraciones
    ├── data-access/   # clientes HTTP con tipos generados
    ├── application/   # facades: tablero, listado, detalle, creación, control, numeración (refreshWhileVisible)
    └── presentation/
        ├── incidents-page/         # contenedor con pestañas (patrón intermittency-page)
        ├── dashboard-page/         tickets-page/         ticket-create-page/
        ├── ticket-detail-page/     # encabezado, elemento, hitos por episodio, cronología, evidencias
        ├── control-page/           numbering-page/
        ├── milestone-dialog/       close-dialog/         reopen-dialog/
        ├── event-dialog/           visit-dialog/         handover-dialog/
        ├── message-dialog/         communication-dialog/ correction-dialog/
        ├── element-picker/         # equipo, conectores, chatbot o elemento del sitio
        └── ticket-state-badge/     milestone-mark/
```

**Structure Decision**: se sigue la Opción 2 (web) que ya usa el repositorio. El módulo `incident` replica la estructura por capas (`api`, `application`, `domain`, `infrastructure`).

- Inventario solo se toca de forma aditiva, para implementar `IncidentAssetDirectory`. Incidencias nunca importa sus clases, y `ArchitectureTest` lo verifica con una regla nueva.
- Identidad solo se toca de forma aditiva, para implementar `IncidentStaffDirectory`. Así un Analista puede elegir el responsable y el relevo sin acceder a `/api/v1/users`, que sigue siendo solo para el Administrador.
- Reportes solo agrega dos tipos.
- Consumo solo cambia el import del período, con un traslado seguro y sin cambios de comportamiento.

## Arquitectura y límites entre módulos

```text
 Angular /incidencias  (tablero · tickets · registrar · detalle · control · numeración)
            │ REST /api/v1/incidents  (contracts/incidents-api.md)
            ▼
 modules/incident ── api ─▶ application (casos de uso, @Transactional) ─▶ domain (puro, sin Spring)
            │                         │
            │                         ├─▶ shared/catalog/IncidentAssetDirectory ◀── implementa modules/inventory
            │                         ├─▶ shared/storage/FileStorage (evidencias)
            │                         ├─▶ shared/audit/AuditTrail
            │                         └─▶ shared/catalog/ReportArchive ◀── implementa modules/reporting
            └── infrastructure (JPA, generadores PDF/XLSX, IncidentPermissions)

 Uptime / Intermitencias: solo enlaces desde la interfaz y el informe (P-13). Ninguna dependencia de código.
```

Reglas:

- El dominio no conoce Spring ni JPA. Las reglas derivadas son funciones puras con `Clock` inyectado, para poder probar los cortes con instantes fijos.
- La transacción de escritura bloquea la fila del ticket por `version` (bloqueo optimista). Solo la creación toma además el bloqueo de fila del contador (research R2).
- Las lecturas de informes usan una transacción de solo lectura `REPEATABLE READ` con un único `cutOff` ([contracts/reports.md](contracts/reports.md)).

## Persistencia, episodios, eventos y evidencias

El detalle está en [data-model.md](data-model.md).

- **Agregado**: `Ticket` es la raíz; la `version` está en `inc_ticket`. Episodios, hitos, eventos, visitas, comunicaciones y evidencias se cambian solo a través de él, y cada operación incrementa la versión. Dos editores simultáneos: el segundo recibe 409 `TICKET_VERSION_CONFLICT`.
- **Solo anexión**:
  - `inc_milestone_revision` y `inc_event` no se actualizan, salvo la anulación del evento;
  - `inc_evidence` no se borra;
  - `inc_milestone` guarda el valor vigente, para consultar rápido; las revisiones guardan la historia completa.
- **Episodios**: al reabrir se crea el episodio `n+1` con T0 propio y la versión de referencias vigente en ese T0. Los anteriores quedan intactos (FR-044).
- **Cronología**: une `inc_event`, las revisiones de hitos, las comunicaciones y las etapas de visita, ordenadas por `occurred_at` y, en empate, por `recorded_at`. Los registros retroactivos y los anulados llevan su marca.
- **Evidencias**:
  - el formato se decide por la firma binaria (JPEG, PNG, WEBP o PDF), no por la extensión;
  - máximo 10 MB, configurable;
  - se guardan en `FileStorage` (`incidents/evidence/{ticketId}/{evidenceId}`) con SHA-256;
  - se descargan por redirección a una URL firmada de vida corta, previa autorización READERS.

## Numeración y prevención de duplicados

- **Contador** (research R2):
  - fila única `inc_ticket_numbering`;
  - `UPDATE … RETURNING` dentro de la transacción de creación, lo que da números sin huecos ni duplicados;
  - restricción `UNIQUE` sobre `ticket_number` como red de seguridad.
- **Bloqueo de producción**: sin `start_number` no se crean tickets (409 `TICKET_NUMBERING_NOT_CONFIGURED`).
- **Configuración**:
  - solo la hace un Administrador, y solo si no hay tickets;
  - exige confirmar el código resultante;
  - el `environment` guardado debe coincidir con la propiedad del servidor, así una base de prueba restaurada en producción, o al revés, se detecta (409 `TICKET_NUMBERING_ENVIRONMENT_MISMATCH`);
  - la propiedad es opcional para no romper el arranque: si falta, la aplicación funciona, pero no se configura la numeración ni se crean tickets.
- **Pruebas**: `TEST` con 9001 u otro valor ficticio. Producción con P-01, pedido el día de la publicación.
- **Reenvíos**: `client_request_id` único. El mismo identificador devuelve el ticket ya creado, sin consumir otro número.
- **Duplicados de negocio** (FR-016): `duplicate_key` por clase, con un índice parcial de tickets abiertos.
  - El formulario consulta `open-matches` mientras se llena y el servidor lo vuelve a validar al crear.
  - Crear el ticket de todos modos exige un motivo, que se audita.
  - Una falla común en varios equipos produce un ticket por equipo, con una relación `COMMON_FAILURE` (Q5).

## Estado al final del período y reconstrucción verificable

Definiciones, con `E = min(fin del rango, cutOff)` y `S` como inicio del rango (research R5):

- **Incluido** en `[S, E)`: existe un episodio con `T0 < E` y, además, `T4` ausente o `T4 ≥ S` (FR-073).
- **Abierto al final**: existe un episodio con `T0 < E` y, además, `T4` ausente o `T4 ≥ E`. En otro caso está Cerrado (FR-074).
- **Reabierto**: el ticket aparece una vez; su estado es el del episodio vigente en `E`.
- **Límite**: `T4 = E` significa Abierto, porque el fin es exclusivo.

**Datos usados**: los valores vigentes al corte, ya corregidos (FR-074a).

**Verificación en dos niveles**:

1. **Fidelidad**: la copia archivada en `report_issued` y su SHA-256 demuestran qué se entregó.
2. **Reglas**: `MilestonesAsKnownAt(X)` aplica las revisiones con `recorded_at ≤ X` y regenera las filas. Su huella canónica (`rowsSha256`, sin nombres visibles que cambian por D12) debe coincidir con la guardada en la emisión.

   Una corrección posterior de T4 cambia los informes nuevos, pero el informe emitido y su reconstrucción con su `cutOff` siguen iguales. Lo prueban el escenario 31 del quickstart y SC-012.

**Por qué funciona**: los episodios no se borran, los hitos tienen revisiones con `recorded_at`, y T0 y T4 son los únicos datos que deciden el estado. No hace falta una tabla de instantáneas por período.

## Referencias, prioridades e indicadores

- `inc_reference_version` y `inc_reference_rule`, sembradas por V021 con FR-034 y FR-051 (versión `PROTOCOLO-FIRMADO-1`, rótulo «por confirmar» hasta P-02).
- Cada episodio guarda su versión en T0.
- `IncidentKpiCalculator` devuelve duraciones `null` cuando falta un hito, nunca 0, y un resultado de referencia de cuatro valores: `WITHIN`, `OUTSIDE`, `NOT_EVALUABLE` o `NO_REFERENCE`.
- Los promedios llevan su denominador y los excluidos por causa (FR-080). Los cierres por duplicado o error quedan aparte (FR-050).
- El tablero, el PDF y el Excel consumen el mismo `IncidentReportQuery` (FR-086).
- Los tipos sin respaldo (chatbot, elemento del sitio, no catalogada y otros) se rotulan «Sin referencia aprobada». No se les inventan umbrales (FR-054) ni se les aplican los tiempos de primera respuesta de consultas del chatbot (FR-051, US3-3).

## Interfaz (Angular) y armonía con los demás módulos

Se revisaron `DESIGN.md`, `PRODUCT.md`, `shared/ui` y las pantallas de Consumo, Inventario e Intermitencias. El módulo **no crea variantes visuales**; reutiliza:

- `_list-page.scss` para listados con filtros, tabla y paginación, que pasa a tarjetas por debajo de 768 px con `injectIsHandset`;
- `_dashboard.scss` (`kpi-grid`, `kpi-tile`) y `sarc-chart-view` con `chart-theme`;
- `_badges.scss` para estado, prioridad, marcas de hito y clase de elemento;
- `_dialog-form.scss`, `WIDE_DIALOG` (760 px), `guardUnsavedChanges` y `confirm-dialog`;
- `_section-tabs.scss` y `_page-chrome.scss`, con el contenedor de pestañas como `intermittency-page`;
- los campos `appearance="outline"` y `floatLabel="always"`, y los iconos `@lucide/angular`;
- `refreshWhileVisible` cada 60 s (FR-063, SC-006);
- `shared/ui/period/` para los filtros de período.

| Pantalla | Patrón de referencia | Notas |
|----------|----------------------|-------|
| Tablero `/incidencias/tablero` | Tablero de Intermitencias | KPI con denominador, condiciones derivadas (FR-062), gráficos sin ponderar y enlaces a Uptime e Intermitencias. «Descargar PDF» y «Descargar Excel» como botones *outlined* en `header-actions` |
| Tickets `/incidencias/tickets` | `sessions-page` de Consumo | Filtros FR-061; columnas código, elemento, tipo, prioridad, estado, responsable, antigüedad y última actualización. «Nuevo ticket», la acción principal, a la derecha del encabezado, visible solo para editores |
| Registrar (diálogo desde `/incidencias/tickets`) | «Nuevo equipo» de Inventario (`WIDE_DIALOG`) | Sustituye a la sección «Registrar» (decisión del responsable, 2026-11-23): se abre con «Nuevo ticket» y no hay ruta propia. Tres campos visibles (elemento, tipo, descripción); «Más datos» despliega origen y prioridad en dos columnas, T0 y evidencia. Aviso de duplicado en línea. «Crear ticket» a la derecha y «Cancelar» a su izquierda; al crear abre el ticket nuevo |
| Detalle `/incidencias/tickets/:id` | Detalle de lote de Intermitencias | Encabezado con código, insignia de estado y acciones según `allowedActions`. Bloques: elemento, hitos del episodio actual (con selector de episodios), duraciones y referencias, cronología, visitas, comunicaciones y evidencias. «Mensaje para WhatsApp» como acción secundaria |
| Control `/incidencias/control` | Listado con período | Período con «Todo el historial» al inicio y las tres columnas de FR-070; pie con totales; descargas en `header-actions` |
| Numeración `/incidencias/numeracion` | Formulario de Inventario | Solo Administrador. Explica el bloqueo, pide el número y la confirmación del código, y muestra el ambiente esperado |

Diálogos (`WIDE_DIALOG`): hito, cierre (requisitos según la clase, con la lista de faltantes), reapertura, evento, visita, relevo, mensaje (`textarea` editable, dos casillas desactivadas al abrir, «Copiar» como acción principal y «Registrar envío» como secundaria), comunicación y corrección de datos (descripción y origen, con justificación obligatoria, FR-036).

**Accesibilidad y responsive**:

- contraste de al menos 4,5:1 y foco visible de 3 px;
- el estado nunca se indica solo con color;
- los gráficos llevan `role="img"`, texto alternativo y leyenda textual;
- etiquetas siempre visibles; los errores y las descripciones no se solapan, con textos largos probados;
- revisión a 360, 390, 768, 1024, 1366 y 1440 px, en claro y oscuro;
- textos en español sin jerga técnica.

**Revisión visual con evidencia** (antes de dar por terminada cada historia con interfaz):

1. `impeccable` en modo crítica y auditoría sobre las capturas del sandbox, comparadas con Consumo e Intermitencias.
2. Se corrige cada inconsistencia o se justifica en el registro de cierre.
3. Las capturas de las pantallas y estados (vacío, carga, error, roles) quedan como evidencia en `quickstart.md`, «Evidencia del cierre».

## Implementación por fases

El orden sigue las prioridades de la spec. Cada fase cierra con pruebas verdes, revisión de código y verificación. `/speckit-tasks` la desglosa.

| Fase | Contenido | Historias |
|------|-----------|-----------|
| 0. Base | ADR 0014, V021 ensayada en PostgreSQL desechable, `IncidentPermissions`, puertos `IncidentAssetDirectory` e `IncidentStaffDirectory` con sus implementaciones, `DocumentFormat`, numeración y su pantalla, traslado seguro de `period` a `shared/ui`, ruta y navegación, regla ArchUnit | — |
| 1. Crear y ver | Creación de equipo, conectores, chatbot y elemento del sitio; aviso de duplicado; relaciones; listado y detalle básico | US1, US3 |
| 2. Hitos y seguimiento | Hitos con revisiones, eventos, prioridad, responsable, relevo, visitas, evidencias, cronología, referencias y duraciones | US2 |
| 3. Cerrar y reabrir | `ClosureRequirements` por clase, episodios nuevos, prohibiciones | US5 |
| 4. Mensaje | `TicketMessageComposer`, diálogo, registro de comunicación | US4 |
| 5. Control de tickets | `TicketStateAt`, pantalla, PDF y Excel, archivo con `rowsSha256`, `MilestonesAsKnownAt` | US6 |
| 6. Gestión | `IncidentKpiCalculator`, tablero, PDF y Excel, coherencia | US7 |
| 7. Cierre | Seguridad completa, regresión, rendimiento, sandbox de navegador, revisión visual, ensayo de V021 sobre la copia restaurada | — |

US3 amplía la creación de US1 y puede empezar en cuanto termina US1. US4 depende de US1. US5 depende de US2. US6 depende de US5, porque el estado al final del período necesita cierres y reaperturas, y US7 depende de US6, porque reutiliza `IncidentReportQuery` y los tipos de informe. El orden detallado está en tasks.md, «Dependencies & Execution Order».

## Estrategia de pruebas

El detalle de escenarios está en [quickstart.md](quickstart.md).

- **Dominio** (rápidas, sin Spring), empezando por pruebas que fallan:
  - `TicketStateAt`, con casos de límite, reapertura, período sin terminar y T4 igual a E;
  - `MilestonesAsKnownAt`;
  - `IncidentKpiCalculator`, con nulos, «No aplica», «Sin dato», abiertos y exclusiones por duplicado o error;
  - `ClosureRequirements` para las cuatro clases;
  - `MilestoneOrderPolicy` y `RetroactivePolicy` (un minuto);
  - `TicketMessageComposer`, con instantánea y sin UID/PUK;
  - `DuplicateKey` (normalización);
  - la compatibilidad `IncidentType`/clase.
- **Persistencia** con Testcontainers:
  - restricciones `CHECK` por clase;
  - unicidad del número y de `client_request_id`;
  - revisiones que nunca se actualizan;
  - siembra de referencias.
- **Concurrencia**: 50 creaciones paralelas sin huecos ni duplicados; dos ediciones con la misma `version` dan 409; carrera entre crear y configurar la numeración.
- **API y seguridad**: `IncidentSecurityTest` recorre cada endpoint con los cinco roles, sin sesión y por acceso directo. La consulta nunca cambia tablas, y `allowedActions` es coherente con la autorización.
- **Informes**:
  - coherencia del tablero, PDF y Excel con el mismo corte;
  - las sumas de `Episodios` reproducen `Resumen`;
  - encabezado y «Sin tickets»;
  - chatbot y sitio sin cargador;
  - reconstrucción de un informe emitido antes de una corrección.
- **Contrato y arquitectura**: `OpenApiContractTest` y `ArchitectureTest`, con una regla nueva: `incident` no depende de `inventory` ni de otros módulos.
- **Regresión**: `Feature010RegressionBaseline` sobre Consumo, Uptime, Intermitencias, Inventario e Informes; las pruebas de Consumo pasan antes y después del traslado del período.
- **Frontend**: pruebas de los facades, del selector de elemento, del formulario de tres campos, de los diálogos (validaciones y no solapamiento) y de los guards de rol.
- **Navegador**: `Feature010BrowserSandbox` y la revisión visual descrita arriba.
- **Rendimiento**: banco con `-Dsarc.qa.performance010=true`.

## Migraciones, respaldo y despliegue

- `V021__incidencias_tickets.sql` hace solo esto:
  - crea las tablas `inc_*` con sus índices y restricciones;
  - inserta la fila vacía de numeración;
  - siembra la versión de referencias `PROTOCOLO-FIRMADO-1`.
- No altera tablas existentes; `report_issued.kind` no tiene `CHECK` (research R18).
- Revisión con `migration` y `postgresql-code-review`: tipos, índices parciales, `CHECK` y ausencia de bloqueos largos.
- **Ensayo**: primero en PostgreSQL desechable (Testcontainers, `IncidentMigrationTest`). Antes de publicar, sobre la última copia cifrada restaurada en un entorno aislado, con un rol que no es superusuario, comparando los conteos de las tablas existentes antes y después. Nunca sobre la base de compose.
- **Publicación** (fuera de este comando): respaldo previo, después `ci.yml`, `version-candidata.yml` y `publicar.yml` con aprobación del entorno `production`. A continuación, un Administrador configura P-01 y se cierra el *issue* `ensayo-restauracion` en 7 días.
- **Vuelta atrás**: se reinstala la imagen anterior. Las tablas `inc_*` no afectan a otros módulos. Si ya hay tickets reales, se conservan; no se borran.

## Riesgos, dependencias y decisiones pendientes

| Riesgo o dependencia | Impacto | Mitigación |
|----------------------|---------|------------|
| P-01 se fija tarde o mal | Números repetidos con la secuencia externa | No se siembra ningún número; producción queda bloqueada hasta configurar; se exige confirmar el código; la configuración solo es posible sin tickets |
| P-02 (protocolo 2.0 o 1.0) | Rótulo de la fuente incorrecto | Rótulo «por confirmar». Si cambian los valores, se crea una versión nueva (P-03) sin tocar episodios anteriores |
| Una base de prueba se restaura en producción, o al revés | Numeración cruzada | Se compara el `environment` guardado con la propiedad del servidor |
| Correcciones de T0 o T4 después de emitir | Informes contradictorios | Archivo inmutable, `rowsSha256` y `MilestonesAsKnownAt` |
| Volumen de eventos y del informe de todo el historial | Lentitud | Índices de R19, paginación y generación en streaming; banco de rendimiento |
| Traslado de `period` a `shared` | Regresión en Consumo | `safe-refactor`: pruebas antes y después y capturas comparadas |
| Inventario cambia nombres o desactiva equipos | Etiquetas inconsistentes | D12 en informes; etiqueta en T0 en el detalle; `findByIds` incluye inactivos |
| Cuenta de Cloudinary compartida con MedicApp | Mezcla de archivos | Carpeta `incidents/evidence`; las pruebas usan `FileStorage` simulado o una carpeta `sarc-qa010-*` vacía antes y después |
| Expectativa de envío automático a WhatsApp o de campana | Alcance creciente | FR-055, FR-059 y FR-096: solo copiar; los eventos quedan marcados `notifiable` para la feature transversal |
| Ponderación o umbral de recurrencia no definidos | Gráficos inventados | P-07: sin ponderar; repetidores sin etiqueta |

**Dependencias**:

- Inventario: el puerto nuevo, de solo lectura.
- Reportes: dos tipos nuevos.
- Autenticación: roles vigentes.
- `FileStorage` y `AuditTrail`.
- La Feature 007, para publicar.
- Uptime e Intermitencias, solo como enlaces.

**Pendientes que no bloquean**: **P-01** y **P-02** (sección «Decisiones» de la spec). Se resuelven antes de la puesta en marcha (Principio II).

## Skills por etapa

Cada skill se invoca en su etapa y se siguen sus instrucciones; mencionarla no basta. El detalle está en research.md, «Skills consultadas».

| Etapa | Skills | Cuándo |
|-------|--------|--------|
| Plan | `speckit-plan` | Este documento |
| Tareas y consistencia | `speckit-tasks`, `speckit-analyze` | Después de aprobar el plan, antes de implementar |
| Migración | `migration`, `postgresql-code-review` | Al escribir V021 y antes de su ensayo |
| Arquitectura y dominio | `clean-architecture` | Agregado Ticket, políticas puras y puertos (fases 0 a 3) |
| Backend | `spring-boot-engineer`, `spring-data-jpa` | Controladores, transacciones, bloqueo optimista, consultas de informes |
| Seguridad | `springboot-security`, `security-review` | Permisos, evidencias, URL firmadas, CSRF, ausencia de UID/PUK; al cerrar las fases 1, 2 y 7 |
| Pruebas | `agent-skills:test-driven-development` (el agente `test-engineer` solo si el responsable lo pide) | Primero las pruebas que fallan en numeración, estado al final del período, KPI y cierre |
| Refactor | `safe-refactor` | Traslado de `period` a `shared/ui` (fase 0) |
| Frontend | `angular-developer` | Componentes con Signals y Signal Forms, rutas, guards, `httpResource` y facades |
| Diseño y revisión visual | `impeccable` | Antes de maquetar cada pantalla (shape) y al cerrarla (critique, audit, polish) con capturas en claro y oscuro |
| Revisión de código | `code-review` | Al cerrar cada fase, sobre el diff |
| Verificación | `verify-and-stop`, `run` | Cierre de cada fase con evidencia, sin ampliar el alcance; cerrar los puertos 8080, 4200 y 5432 |

## Constitution Check (post-design)

| Regla | Resultado | Evidencia del diseño |
|-------|-----------|----------------------|
| I | PASA | Todo el diseño remite a FR y escenarios de la spec |
| II | PASA | Las restricciones `CHECK` reproducen reglas ya escritas (FR-001a, FR-005, FR-042, FR-043), sin crear políticas nuevas. La regla de duplicados de FR-016 se implementa como clave, sin unicidad, para respetar «crear con motivo». P-01 y P-02 siguen abiertas sin una regla implícita |
| III | PASA | Solo V021, aditiva; los cambios futuros de referencias van en migraciones nuevas |
| D04, D06, D07, D12, D13 | PASA | Ver la evaluación previa; el diseño las mantiene: puerto sin UID/PUK, conector por UUID de posición, equipo original, nombre actual con etiqueta en T0, «Este mes» en Gestión |
| Jerarquía documental | PASA | CC-PRD-010 recoge las diferencias con el PRD; ADR 0014 recoge el módulo |

Sin violaciones.

## Complexity Tracking

Sin violaciones constitucionales que justificar. Una decisión merece nota aunque no viola nada: los hitos tienen dos tablas, el valor vigente y las revisiones. Esto añade una tabla frente a guardar solo las revisiones, pero evita recalcular el valor vigente en cada lista. La reconstrucción verificable exige las revisiones de todos modos.
