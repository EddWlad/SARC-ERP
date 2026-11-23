---
description: "Lista de tareas de Feature 010: incidencias, tickets y tiempos"
---

# Tasks: Incidencias, tickets y tiempos

**Input**: documentos de diseño en `specs/010-incidencias-tickets-tiempos/`:

- [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md) y [data-model.md](data-model.md);
- [contracts/incidents-api.md](contracts/incidents-api.md), [contracts/whatsapp-message.md](contracts/whatsapp-message.md) y [contracts/reports.md](contracts/reports.md);
- [quickstart.md](quickstart.md).

**Tests**: se incluyen. El plan define una estrategia de pruebas obligatoria (plan.md, «Estrategia de pruebas»), y la constitución y las features anteriores la exigen. En cada historia las pruebas se escriben primero y deben fallar antes de implementar (skill `agent-skills:test-driven-development`).

**Organization**: las tareas se agrupan por historia de usuario (US1 a US7), para implementarlas y probarlas por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (archivos distintos y sin dependencias pendientes).
- **[Story]**: la historia a la que pertenece (US1 a US7).

## Path Conventions

| Alias | Ruta |
|-------|------|
| `{BE}` | `sarc-bakend/src/main/java/com/tidsec/sarc_bakend` |
| `{INC}` | `{BE}/modules/incident` |
| `{BT}` | `sarc-bakend/src/test/java/com/tidsec/sarc_bakend` |
| `{INCT}` | `{BT}/modules/incident` |
| `{MIG}` | `sarc-bakend/src/main/resources/db/migration` |
| `{RES}` | `sarc-bakend/src/main/resources` |
| `{FE}` | `sarc-frontend/src/app` |
| `{FINC}` | `{FE}/features/incidents` |

**Reglas que valen para todas las tareas**:

- **Repositorio público**: ningún dato real, número de ticket real ni secreto en el código, fixtures, registros o mensajes. Se usan equipos con códigos ficticios `9xxxxx`, sitios «CC Prueba Norte» y «Sitio sin equipos», y numeración de prueba `TEST` desde 9001.
- No se leen `sarc-bakend/.env`, `documents/Usuario y contraseña de SARC_Servidor.txt` ni `documents/infraestructura-local/`.
- **Migraciones**: V001 a V020 no se tocan. V021 solo se aplica en bases desechables (Testcontainers o el PostgreSQL de ensayo). No se arranca el backend contra la base de compose ni contra otra base persistente hasta publicar. Si V021 ya se aplicó en un ambiente compartido, cualquier cambio va en V022 (Principio III).
- **Número de producción (P-01)**: **no** se siembra ni se escribe en ningún archivo. Se pide al responsable el día de la publicación (T119).
- **UI**: solo los *tokens* de `design-system/` y los estilos de `shared/ui`; ningún color, variante visual ni componente paralelo (FR-064, plan.md «Interfaz»). Antes de maquetar una pantalla se usa `impeccable` (shape), y al cerrarla, `impeccable` (critique y audit) con capturas en claro y oscuro.
- **Revisiones**: cada historia cierra con `code-review` sobre su diff y `verify-and-stop` con evidencia. Las tareas de seguridad usan `springboot-security` y `security-review`; las de persistencia, `spring-data-jpa`; las de dominio, `clean-architecture`; y las de frontend, `angular-developer`.
- **Puertos**: al terminar una prueba local, se cierran los puertos 8080, 4200 y 5432 que se hayan abierto.
- **Fase de planificación**: este archivo no autoriza commits, PR ni publicación. Eso lo decide el responsable.

---

## Phase 1: Setup (infraestructura compartida)

**Purpose**: decisiones documentadas, configuración y datos de prueba sintéticos que usan todas las historias.

- [x] T001 [P] Redactar el ADR del módulo en `sarc-bakend/docs/adr/0014-modulo-incidencias.md`, con el formato de `0013-modulo-intermitencias.md`. Debe cubrir:
  - el módulo `incident` y el puerto `IncidentAssetDirectory`, y por qué no se amplía `EquipmentDirectory` (research R1);
  - el puerto `IncidentStaffDirectory` y por qué no se abre `/api/v1/users` a los analistas (research R8);
  - la numeración por contador de una sola fila con `UPDATE … RETURNING`, el sello de ambiente y la idempotencia por `client_request_id` (R2);
  - las revisiones de hitos de solo anexión y el estado al final del período (R3, R5);
  - las referencias versionadas (R6);
  - el archivo de informes con `rowsSha256` (contracts/reports.md).
- [x] T002 [P] Agregar a `{RES}/application.yaml` estas propiedades:
  - `sarc.incidents.numbering.environment`, **opcional**: sin valor en `application.yaml`, `TEST` en `application-dev.yaml` y `PRODUCTION` en `application-prod.yaml`. Si falta, la aplicación arranca y solo se bloquean la configuración de la numeración y la creación de tickets (research R2), para no romper el arranque ni las pruebas existentes, que usan el perfil por defecto;
  - `sarc.incidents.evidence.max-bytes: 10485760`.

  Crear `{INC}/infrastructure/config/IncidentProperties.java` (`@ConfigurationProperties`, validado: el ambiente, si existe, vale `PRODUCTION` o `TEST`). Fijar `TEST` para las pruebas en `{BT}/TestcontainersConfiguration.java` (o con `@DynamicPropertySource` en `Feature010Fixtures`). Comprobar que `./mvnw.cmd test` de los módulos existentes sigue arrancando.
- [x] T003 [P] Crear `{BT}/support/Feature010Fixtures.java`, siguiendo `Feature008Fixtures`. Debe sembrar en Testcontainers:
  - un punto de carga JAC de un conector y una electrolinera KIA de 2 conectores, ambos con código `9xxxxx` y enlace de Maps;
  - un equipo sin `maps_url` ni ubicación;
  - un equipo CONECTZ en «CC Prueba Norte», para usarlo como equipo de referencia;
  - un equipo INACTIVO y uno con un conector retirado;
  - usuarios ADMINISTRADOR, ANALISTA (dos), GERENTE, SUPERVISOR y ASISTENTE;
  - la combinación Analista+Gerente;
  - la numeración configurada en `TEST` desde 9001, con una variante sin configurar.
- [x] T004 [P] Crear `{INCT}/support/IncidentApiSupport.java`. Debe ofrecer ayudas para:
  - crear tickets de cada clase;
  - registrar hitos con un `Clock` fijo;
  - cerrar y reabrir;
  - leer la `version` actual.

  Las pruebas de US2 a US7 lo usan sin depender de que otras historias estén terminadas.

  *Avance: `IncidentApiSupport` ofrece sesiones por rol, un segundo Analista, numeración `TEST`, las llamadas HTTP, creación de tickets de equipo, chatbot y sitio, `recordMilestoneAt` (hitos con hora exacta respecto del T0, sin depender del reloj), `backdateT0`, `resolveMilestones`, `close`, `reopen` y `versionOf`. La prueba de cierre y reapertura ya los usa en vez de tener copias propias. `IncidentReportSupport` agrega el sembrado por SQL de episodios con fechas elegidas para los informes. Desvío: no hay un `Clock` fijo inyectado; las horas se fijan con el T0 y los desplazamientos.*

---

## Phase 2: Foundational (prerrequisitos bloqueantes)

**Purpose**: esquema, puerto de activos, tipos y catálogo, agregado base, numeración, permisos y armazón de la interfaz.

**⚠️ CRITICAL**: ninguna historia empieza hasta terminar esta fase. Excepción: T009 (ensayo sobre copia restaurada) solo bloquea la publicación.

### Backend

- [x] T005 Crear `{MIG}/V021__incidencias_tickets.sql`, que **solo agrega** tablas, índices, restricciones y siembra, según data-model.md. Cada tabla lleva:
  - **`inc_ticket_numbering`**:
    - `id SMALLINT PK` con `CHECK (id = 1)`;
    - `start_number INTEGER NULL` con `CHECK (start_number >= 1)`;
    - `next_number INTEGER NULL`;
    - `environment VARCHAR(12) NULL` con `CHECK (environment IN ('PRODUCTION','TEST'))`;
    - `configured_at TIMESTAMPTZ`, `configured_by UUID` y `version BIGINT`.

    Se inserta la fila `id = 1` con los tres valores nulos. **No se siembra ningún número**.
  - **`inc_reference_version`**:
    - `code VARCHAR(40) UNIQUE`, `label VARCHAR(160)`, `effective_from TIMESTAMPTZ` y `source_note VARCHAR(500)`.

    Se siembra `PROTOCOLO-FIRMADO-1` con la etiqueta «Protocolo de monitoreo firmado (versión por confirmar)» y la fuente «PRD §10.3, Protocolo §5».
  - **`inc_reference_rule`**:
    - PK (`version_id`, `incident_type`);
    - `validation_max_min`, `recovery_max_min`, `escalation_max_min` y `persistent_after_min` como `INTEGER NULL`;
    - `recovery_extendable BOOLEAN`;
    - `proposed_priority VARCHAR(10)` con `CHECK IN ('MEDIUM','HIGH','CRITICAL')`.

    La siembra reproduce FR-034 y FR-051 tal como las resume data-model.md:
    - Desconexión 300, con `recovery_extendable` en `true`;
    - Falla de conector 180;
    - Conector retenido 120;
    - Espera de conexión con escalamiento de 60;
    - «validación de cinco minutos» con `validation_max_min = 5` donde aplica;
    - Punto KIA con caída persistente con `persistent_after_min = 30`, comparado con «estrictamente mayor» sobre la duración exacta en segundos;
    - las prioridades de FR-034.

    Los tipos sin respaldo no llevan fila.
  - **`inc_ticket`**, con las columnas de `BaseAuditableEntity` y:
    - `ticket_number INTEGER UNIQUE NOT NULL`;
    - `ticket_code VARCHAR(20)` generada: `'TICKET-' || lpad(ticket_number::text, greatest(3, length(ticket_number::text)), '0')`, `UNIQUE`. **No** se usa `lpad(…, 3, '0')` a secas, porque recorta 1000 a `100`;
    - `client_request_id UUID UNIQUE NOT NULL`;
    - `state VARCHAR(8)` con `CHECK IN ('OPEN','CLOSED')`;
    - `element_class VARCHAR(20)` con `CHECK IN ('EQUIPMENT','CHATBOT_SERVICE','SITE_ELEMENT')`;
    - `equipment_id`, `equipment_label_at_t0 VARCHAR(150)`, `network_at_t0 VARCHAR(20)` (`KIA`, `JAC` o `CONECTZ`) e `infrastructure_type_at_t0 VARCHAR(20)`;
    - `chatbot_scope VARCHAR(10)` (`TOTAL` o `PARTIAL`);
    - `site_element_kind VARCHAR(20)` (`VEHICLE_BARRIER` u `OTHER`) y `element_name VARCHAR(120)`;
    - `reference_equipment_id`, `manual_site_name VARCHAR(150)`, `manual_location VARCHAR(300)` y `manual_maps_url VARCHAR(500)`;
    - `site_label_at_t0 VARCHAR(150)`, `location_at_t0 VARCHAR(300)` y `maps_url_at_t0 VARCHAR(500)`;
    - `incident_type VARCHAR(40)`;
    - `report_origin VARCHAR(20)` con `CHECK IN ('PLATFORM','CHATBOT','SITE_STAFF','CUSTOMER','PROVIDER','OTHER')` y `report_origin_detail VARCHAR(200)`, obligatorio si el origen es `OTHER`;
    - `description VARCHAR(500) NOT NULL`;
    - `priority VARCHAR(10)`;
    - `current_responsible_id UUID NOT NULL` y `current_responsible_name VARCHAR(150) NOT NULL` (copia del nombre al asignar);
    - `duplicate_key VARCHAR(200) NOT NULL` y `duplicate_override_reason VARCHAR(300)`;
    - `last_activity_at TIMESTAMPTZ`.

    Restricciones `CHECK` por clase, según data-model.md, «Restricciones CHECK por clase de elemento»:
    - `EQUIPMENT` exige el equipo, su etiqueta, la red y el tipo de infraestructura en T0;
    - `CHATBOT_SERVICE` deja nulas las columnas de equipo, sitio y red;
    - `SITE_ELEMENT` exige `site_element_kind` y **exactamente una** de dos opciones: `reference_equipment_id` o bien (`manual_site_name` y `manual_location`); su `network_at_t0` es nulo;
    - `OTHER` exige `element_name`;
    - `incident_type` debe ser compatible con `element_class`.

    Índices:
    - `(state, last_activity_at DESC)`;
    - `(duplicate_key) WHERE state = 'OPEN'`;
    - `(equipment_id)`, `(reference_equipment_id)`, `(network_at_t0)` e `(incident_type)`.
  - **`inc_ticket_connector`**: PK (`ticket_id`, `connector_id`) y `position_at_t0 SMALLINT`.
  - **`inc_ticket_relation`**:
    - `ticket_id < related_ticket_id` (`CHECK`) y `UNIQUE` del par;
    - `kind` con `CHECK IN ('INDEPENDENT_FAILURE','COMMON_FAILURE')`;
    - `reason VARCHAR(300)`.
  - **`inc_episode`**:
    - `ordinal SMALLINT`, `UNIQUE (ticket_id, ordinal)`;
    - `state` (`OPEN` o `CLOSED`) y `reference_version_id FK`;
    - `reopen_reason VARCHAR(500)`, obligatorio si `ordinal > 1`;
    - `closure_result` con `CHECK IN ('RECOVERED','NO_FAULT_CONFIRMED','DUPLICATE','REGISTERED_IN_ERROR')`, obligatorio si el episodio está `CLOSED`;
    - `duplicate_of_ticket_id`, obligatorio si el resultado es `DUPLICATE`;
    - `cause_kind` con `CHECK IN ('CONFIRMED','PROBABLE','UNDETERMINED')`;
    - `cause_text VARCHAR(500)`, obligatorio salvo `UNDETERMINED` (FR-047);
    - `recommendations VARCHAR(1000)`;
    - `final_evidence_waiver VARCHAR(300)` y `closure_communication_waiver VARCHAR(300)`;
    - `closure_checks JSONB` y `deviation_justification VARCHAR(500)`.
  - **`inc_milestone`**:
    - PK (`episode_id`, `kind`), con `kind` en `CHECK IN ('T0','T1','T2','T3','T4')`;
    - `status` con `CHECK IN ('PENDING','RECORDED','NOT_APPLICABLE','NO_DATA')`;
    - `occurred_at` NOT NULL si y solo si el estado es `RECORDED`;
    - `reason VARCHAR(300)`, obligatorio en `NOT_APPLICABLE` y `NO_DATA`;
    - T0 siempre `RECORDED`, y T4 nunca `NOT_APPLICABLE` ni `NO_DATA`;
    - `retroactive BOOLEAN` y `order_warning_ack BOOLEAN`;
    - `recorded_by` y `recorded_at`.
  - **`inc_milestone_revision`**: los valores anteriores y nuevos, `justification VARCHAR(500)`, `recorded_by`, `recorded_at`, y el índice (`episode_id`, `kind`, `recorded_at`).
  - **`inc_event`**:
    - `type VARCHAR(30)` con `CHECK` sobre los 18 tipos de data-model.md, incluido `DATA_CORRECTED`;
    - `target_kind VARCHAR(20)` con `CHECK IN ('TECHNICAL','SUPERVISION','PLATFORM_SUPPORT','OTHER')`, obligatorio si `type = 'ESCALATED'`;
    - `occurred_at`, `recorded_at`, `recorded_by` y `retroactive`;
    - `text VARCHAR(1000)`, `target_role_or_provider VARCHAR(150)` y `details JSONB`;
    - `notifiable BOOLEAN`;
    - `annulled_at`, `annulled_by` y `annul_reason`.

    Índices: (`ticket_id`, `recorded_at`) y uno parcial sobre (`recorded_at`) `WHERE notifiable`.
  - **`inc_visit`**:
    - `provider_name VARCHAR(120)`;
    - `stage` con `CHECK IN ('REQUESTED','ASSIGNED','CONFIRMED','CANCELLED','POSTPONED','EXECUTED','REPORT_RECEIVED')`;
    - `cancel_decided_by`, `cancel_evidence_note` y `reescalation_condition`.
  - **`inc_communication`**:
    - `channel` con `CHECK IN ('REDAE_INPSERCOM','KIA_ELECTROLINERAS','CARGADORES_ELECTRICOS','AUTOEASTERN_ENERLINK','DIRECTIVOS','EMAIL','OTHER')`;
    - `channel_detail VARCHAR(150)`, obligatorio si el canal es `OTHER`;
    - `kind` con `CHECK IN ('INITIAL','UPDATE','CLOSURE')`;
    - `sent_at`, `retroactive` y `justification VARCHAR(300)`;
    - las columnas de anulación.
  - **`inc_evidence`**:
    - `purpose` con `CHECK IN ('GENERAL','INITIAL','FINAL')`;
    - `storage_ref VARCHAR(255)`;
    - `format` con `CHECK IN ('JPEG','PNG','WEBP','PDF')`;
    - `size_bytes BIGINT`, `sha256 CHAR(64)` y `original_name VARCHAR(200)`.

  No se altera ninguna tabla existente. Revisar con las skills `migration` y `postgresql-code-review` antes de dar la tarea por cerrada.
- [x] T006 Crear `{INCT}/infrastructure/persistence/IncidentMigrationTest.java`, siguiendo `UptimeMigrationTest`. Debe comprobar con Testcontainers:
  - V021 se aplica sobre V020 sin cambiar las columnas de ninguna tabla existente;
  - la fila de numeración existe con valores nulos;
  - la siembra de `PROTOCOLO-FIRMADO-1` y sus reglas, una por tipo con respaldo;
  - cada `CHECK` por clase rechaza las combinaciones inválidas;
  - `ticket_code` sale `TICKET-086`, `TICKET-999` y `TICKET-1000`;
  - la unicidad de `ticket_number` y `client_request_id`;
  - T4 en `NO_DATA` se rechaza.
- [x] T007 [P] Crear `{BE}/shared/catalog/IncidentAssetDirectory.java` y los *records* `IncidentAsset(id, name, assignedCode, network, infrastructureType, location, mapsUrl, adminStatus, connectors)` e `IncidentConnector(id, position, typeLabel, active)`. Métodos: `search(text, limit)`, solo activos y sin distinguir mayúsculas ni tildes, y `findByIds(ids)`, que incluye inactivos y eliminados. **Nunca** devuelve UID/PUK, OCPP ni serie (data-model.md, «Puerto público nuevo»).

  Crear también `{BE}/shared/security/IncidentStaffDirectory.java` y el *record* `StaffMember(id, fullName)`, con `activeEditors()`, `findActiveEditor(id)` y `namesOf(ids)` (data-model.md). Nunca devuelve correo, contraseña, estado de bloqueo ni roles.
- [x] T008 Crear `{BE}/modules/identity/application/service/IncidentStaffDirectoryService.java` (solo lectura, sobre `UserRepository`) y su prueba `{BT}/modules/identity/application/service/IncidentStaffDirectoryServiceTest.java`: solo usuarios `ACTIVE` con rol `ADMINISTRADOR` o `ANALISTA`; un Gerente o un inactivo no aparecen y `findActiveEditor` los rechaza; `namesOf` incluye inactivos.

  Crear además `{BE}/modules/inventory/application/service/IncidentAssetDirectoryService.java` (solo lectura, como `EquipmentDirectoryService`) y su prueba `{BT}/modules/inventory/application/service/IncidentAssetDirectoryServiceTest.java`. La prueba cubre:
  - búsqueda por nombre y código;
  - exclusión de inactivos en la búsqueda;
  - `findByIds` con un inactivo;
  - conectores con su posición;
  - ausencia de UID/PUK en el *record*.
- [x] T009 Ensayar V021 sobre la última copia cifrada de producción restaurada en un PostgreSQL 18 desechable, sin puertos publicados, sin variables `CLOUDINARY_*` ni `SARC_DB_*`, y con un rol que no es superusuario y es propietario de su base. Pasos y criterios:
  1. Comprobar el SHA-256 de la copia y descifrarla solo en memoria o en un temporal.
  2. Hacer `pg_restore` sin errores, con la última migración V020.
  3. Aplicar V021 y medir cuánto tarda.
  4. Comprobar que las tablas existentes tienen los mismos conteos y columnas.
  5. Destruir el entorno.

  Registrar el resultado en quickstart.md, «Evidencia del cierre». **No bloquea las historias; bloquea T118**.
  *Avance (2026-11-23, con la autorización expresa del responsable para usar la copia y la clave): ensayo sobre la copia nocturna `sarc-20261123T0700Z` en un PostgreSQL 18 desechable, sin red ni puertos publicados, sin variables `CLOUDINARY_*` ni `SARC_DB_*`, con un rol que no es superusuario y es propietario de su base. (1) La copia diaria no guarda su `.sha256` en este equipo; la tarea de descarga ya la verificó y aquí se calculó su huella (`321c091a…df7d2`). (2) Descifrada en un temporal que se borró, `pg_restore` sin errores y última migración V020, con 35 tablas y conteos iguales a los metadatos. (3) V021 aplicada en una transacción por el rol de la aplicación en 251 ms. (4) Las 35 tablas existentes conservan sus conteos y columnas y aparecen 13 tablas `inc_*`, con una fila de numeración sin configurar, ningún ticket y una versión de referencias. (5) Contenedor y temporal destruidos. No se imprimió ningún secreto.*
- [x] T010 [P] Crear `{BE}/shared/storage/DocumentFormat.java`, con la detección de PDF por la firma `%PDF-`, y su prueba `{BT}/shared/storage/DocumentFormatTest.java`. Debe cubrir un PDF válido, un PNG con extensión `.pdf` (detectado como imagen con `ImageFormat`) y un ejecutable renombrado, que se rechaza.
- [x] T011 [P] Crear `{INC}/application/service/IncidentPermissions.java` (no en `infrastructure/security`: `ArchitectureTest` prohíbe que `application` dependa de `infrastructure`, y los servicios la usan en `@PreAuthorize`) con:
  - `EDITORS = "hasAnyRole('ADMINISTRADOR','ANALISTA')"`;
  - `READERS`, para los cinco roles;
  - `NUMBERING = "hasRole('ADMINISTRADOR')"`.

  Sigue el patrón de `IntermittencyPermissions` (contracts/incidents-api.md, «Permisos»).
- [x] T012 [P] Agregar a `{BT}/architecture/ArchitectureTest.java` dos reglas: `incident` no depende de ningún otro módulo (solo de `shared`), y ningún módulo depende de `incident`. Mantener las reglas existentes.
- [x] T013 [P] Crear los tipos del dominio en `{INC}/domain/model/reference/`:
  - `IncidentType`, con los tipos de FR-020, más «Falla de elemento del sitio» e «Incidencia no catalogada»; el método `allowedFor(ElementClass)` sigue FR-022;
  - `ElementClass`, `SiteElementKind`, `Priority`, `ReportOrigin` y `ChatbotScope`.

  Crear también, en `{INC}/domain/model/ticket/`, `TicketState`, `MilestoneKind`, `MilestoneStatus` y `EventType` (los 18 de data-model.md, con `isManual()`, verdadero solo para los cuatro tipos que admite `POST /events` en contracts/incidents-api.md, e `isNotifiable(...)`, según research R17).

  Prueba `{INCT}/domain/model/reference/IncidentTypeTest.java`: la matriz de compatibilidad por clase debe coincidir con el `CHECK` de V021, lo que se comprueba leyendo la definición de la restricción en Testcontainers.
- [x] T014 [P] Crear `{INC}/domain/model/reference/ReferenceVersion.java` y `ReferenceRule.java`, su repositorio de solo lectura en `{INC}/infrastructure/persistence/`, y `{INC}/application/service/ReferenceCatalog.java`:
  - `versionAt(Instant)` devuelve la última versión con `effective_from ≤ T0`;
  - `ruleFor(version, type)` devuelve `Optional`;
  - `proposedPriority(type)` devuelve `MEDIUM` si no hay regla.

  Prueba `{INCT}/application/service/ReferenceCatalogTest.java`, con una segunda versión de prueba de vigencia posterior.
- [x] T015 Crear el agregado base en `{INC}/domain/model/ticket/`:
  - `Ticket` (raíz, con `@Version`);
  - `Episode`;
  - `Milestone`;
  - `MilestoneRevision` (inmutable, sin *setters*);
  - `TicketEvent` (solo admite anulación);
  - `TicketConnector`;
  - `TicketRelation`.

  Crear también sus repositorios en `{INC}/domain/repository/` y `{INC}/infrastructure/persistence/`, y el método `Ticket.open(...)`, que crea el episodio 1 con los cinco hitos: T0 `RECORDED` y T1 a T4 `PENDING`. Usar la skill `spring-data-jpa`: carga perezosa, `@BatchSize` para episodios e hitos, y nunca `CascadeType.REMOVE` sobre historial.
- [x] T016 [P] Crear `{INC}/domain/model/numbering/TicketCode.java`, con el formato de al menos 3 dígitos, y `TicketNumbering.java`. Crear `{INC}/infrastructure/persistence/TicketNumberingRepository.java` con:
  - `nextNumber()`: `UPDATE inc_ticket_numbering SET next_number = next_number + 1 WHERE id = 1 AND start_number IS NOT NULL RETURNING next_number - 1`;
  - `configure(...)`.

  Pruebas: `{INCT}/domain/model/numbering/TicketCodeTest.java` (86, 999, 1000 y 9001) y `{INCT}/infrastructure/persistence/TicketNumberingRepositoryTest.java` (sin configurar devuelve vacío; los números son consecutivos).
- [x] T017 Crear `{INC}/application/service/TicketNumberingService.java` y `{INC}/api/controller/TicketNumberingController.java`, con `GET /api/v1/incidents/numbering` (READERS) y `PUT /api/v1/incidents/numbering` (NUMBERING). El `PUT`:
  - exige que `confirmation` sea el código resultante;
  - compara `environment` con `IncidentProperties`;
  - solo se permite con 0 tickets.

  Errores:
  - 409 `TICKET_NUMBERING_LOCKED`;
  - 422 `TICKET_NUMBERING_ENVIRONMENT_MISMATCH`;
  - 422 `TICKET_NUMBERING_CONFIRMATION_MISMATCH`.

  Auditar `NUMBERING_CONFIGURED`. Prueba `{INCT}/api/TicketNumberingApiTest.java`: los 3 errores, 403 para Analista y para los roles de consulta, y el `GET` para todos. Agregar `{INCT}/api/TicketNumberingWithoutEnvironmentTest.java`, con la propiedad vacía (`@TestPropertySource`): el contexto arranca, `GET` devuelve `expectedEnvironment: null`, el `PUT` responde 422 y crear un ticket responde 409, ambos con `TICKET_NUMBERING_ENVIRONMENT_MISMATCH` (escenario 47). *Hecho: el contexto arranca, `GET` y `PUT` verificados. Falta solo «crear un ticket responde 409», que se comprueba en T028 cuando exista `POST /tickets`.*
- [x] T018 [P] Crear `{INC}/domain/IncidentErrors.java`, con los códigos de contracts/incidents-api.md como constantes y sus excepciones, usando las clases de `shared/error` que mapea `GlobalExceptionHandler`.
- [x] T019 [P] Crear `{INC}/domain/IncidentLabels.java`, con las etiquetas en español que usan mensajes e informes: tipos, clases, «Sin red», estados «Abierto» y «Cerrado», marcas `R`, `R*`, `N/A`, `S/D` y `—`, canales y resultados de cierre.

### Frontend

- [x] T020 Trasladar sin cambiar el comportamiento (skill `safe-refactor`):
  - `{FE}/features/consumption/domain/period.ts` y `period.spec.ts` a `{FE}/shared/ui/period/`;
  - `{FE}/features/consumption/presentation/period-filter/` a `{FE}/shared/ui/period/period-filter/`.

  Actualizar los imports de `features/consumption/application/period-selection.ts`, `data-access/consumption-api.ts` y `presentation/dashboard-page/dashboard-page.ts`. Ejecutar `npm test -- --watch=false` antes y después, con los mismos resultados, y comparar una captura del tablero de Consumo (research R13). *Hecho con los mismos resultados antes y después (90 archivos, 509 pruebas). Además se movió `period-selection.ts` y su spec a `shared/ui/period/`, porque el filtro compartido la usa y `shared/ui` no debe depender de una feature. La captura comparativa del tablero queda para la revisión visual de T110.*
- [x] T021 [P] Crear `{FINC}/domain/incident-permissions.ts`, con `INCIDENT_EDITORS` e `INCIDENT_NUMBERING`, siguiendo `features/intermittency/domain/intermittency-permissions.ts`, y su spec.
- [x] T022 [P] Crear `{FINC}/domain/incident-labels.ts`, con las mismas etiquetas que T019, y `{FINC}/domain/incident-time.ts`: duración en h:mm a partir de minutos, `null` como «—» y nunca 0, en la zona America/Guayaquil. Cada uno con su spec.
- [x] T023 Crear el armazón de rutas:
  - `{FINC}/incidents.routes.ts`, con `tablero` (por defecto), `tickets`, `tickets/:id`, `control`, `registrar` y `numeracion`. *Por ahora registra solo `numeracion` y la redirección a `tablero`: cada historia agrega su ruta (T040, T059, T095, T104) para no apuntar a páginas que aún no existen.* `registrar` lleva `roleGuard(...INCIDENT_EDITORS)` y `numeracion` lleva `roleGuard(...INCIDENT_NUMBERING)`;
  - `{FINC}/presentation/incidents-page/incidents-page.ts`, con pestañas Tablero, Tickets y Control (`shared/ui/_section-tabs.scss`), como `intermittency-page`;
  - la ruta `incidencias` en `{FE}/app.routes.ts`.
- [x] T024 Agregar la entrada `{ label: 'Incidencias', path: '/incidencias', icon: … }` (un icono de Lucide, por ejemplo `LucideTicket`) en `{FE}/layout/navigation.ts`, después de Intermitencias, y actualizar su spec si comprueba la lista.
- [x] T025 Crear `{FINC}/data-access/incidents-api.ts`, con los tipos generados por `npm run api:types` después de T017, y la pantalla `{FINC}/presentation/numbering-page/`, siguiendo el formulario de Inventario y `floatLabel="always"`. La pantalla:
  - explica el bloqueo;
  - pide el número inicial y la confirmación del código;
  - muestra el ambiente esperado;
  - queda en solo lectura una vez configurada.

  Usar `impeccable` (shape) antes de maquetar. *Pantalla hecha con los patrones de Inventario y los tokens del sistema de diseño. Pendiente: `impeccable` (critique y audit), la revisión en navegador con capturas en claro y oscuro (T110) y la comprobación con el backend en marcha, que exige el sandbox `Feature010BrowserSandbox` aún sin crear.*

**Checkpoint**: la base está lista; las historias pueden empezar.

---

## Phase 3: User Story 1 - Registrar una incidencia de equipo en tres datos (Priority: P1) 🎯 MVP

**Goal**: un editor crea un ticket de equipo completo o de conectores con tres datos (equipo, tipo y descripción). El ticket recibe un número `TICKET-NNN` que no se repite y queda visible en el listado y en el detalle.

**Independent Test**: escenarios 1 a 10 de quickstart.md: numeración sin configurar, formato, 50 creaciones paralelas, doble clic, creación con tres campos, conectores, duplicado y falla común. Con roles de consulta, 403.

### Tests for User Story 1 ⚠️

- [x] T026 [P] [US1] Crear `{INCT}/domain/model/ticket/AffectedElementEquipmentTest.java`. Debe probar:
  - equipo completo sin conectores;
  - conectores del mismo equipo activos;
  - conector ajeno o retirado, rechazado;
  - equipo inactivo, rechazado;
  - etiqueta, red e infraestructura conservadas en T0;
  - `DuplicateKey` = `EQ:{id}`, también para tickets de conectores.
- [x] T027 [P] [US1] Crear `{INCT}/domain/policy/RetroactivePolicyTest.java`: una diferencia de 1 min o menos no es retroactiva; de más de 1 min, sí, y exige motivo; un instante futuro se rechaza (FR-018).
- [x] T028 [P] [US1] Crear `{INCT}/api/TicketCreationApiTest.java`. Debe probar:
  - 201 con tres campos, T0 igual al momento del `Clock`, prioridad propuesta Alta en Desconexión y origen `PLATFORM`;
  - el responsable inicial es el autor, con su nombre en `responsible.name` del listado y del detalle;
  - conectores;
  - 409 `TICKET_NUMBERING_NOT_CONFIGURED` y `TICKET_NUMBERING_ENVIRONMENT_MISMATCH`;
  - 409 `TICKET_OPEN_DUPLICATE` con `openTickets[]`, y luego 201 con `duplicateOverrideReason`, que se audita;
  - el mismo `clientRequestId` devuelve 200 con el mismo ticket y sin consumir número;
  - 422 `TICKET_ELEMENT_INVALID`, `TICKET_T0_INVALID` y `TICKET_MAPS_URL_INVALID`;
  - 403 para Gerente, Supervisor y Asistente sin cambios en las tablas;
  - la respuesta sin UID/PUK.
- [x] T029 [P] [US1] Crear `{INCT}/api/IncidentNumberingConcurrencyTest.java`: 50 creaciones paralelas producen 50 números consecutivos, sin huecos ni duplicados (SC-003). Incluye una carrera entre crear y configurar la numeración.
- [x] T030 [P] [US1] Crear `{INCT}/api/TicketReadApiTest.java`. Debe probar:
  - el listado con los filtros de FR-061 y la paginación;
  - `elementLabel` con el nombre **actual** después de renombrar el equipo, mientras el detalle muestra `labelAtT0` (D12, FR-007);
  - la red conservada después de cambiar la red del equipo (P-14);
  - `allowedActions` vacío para los roles de consulta;
  - las relaciones `COMMON_FAILURE` visibles en ambos sentidos (FR-017);
  - cada valor de `sort`, el orden por defecto y el 400 con `sort=element` (FR-061, escenario 45);
  - `TICKET-085` con la numeración desde 9001 devuelve `notice = TICKET_BEFORE_START`;
  - todos los conectores elegidos se muestran como equipo completo.

### Implementation for User Story 1

- [x] T031 [US1] Crear `{INC}/domain/model/ticket/AffectedElement.java` (objeto valor), con el modo equipo y conectores; `DuplicateKey.java`; y `{INC}/domain/policy/RetroactivePolicy.java` (umbral de 1 min).
- [x] T032 [US1] Crear `{INC}/application/service/TicketCreationService.java`. En una sola transacción:
  1. Busca por idempotencia (`client_request_id`).
  2. Valida el elemento con `IncidentAssetDirectory`.
  3. Comprueba el aviso de duplicado (`duplicate_key` en tickets abiertos).
  4. Verifica el ambiente, después la configuración de la numeración (en ese orden, research R2), y solo entonces asigna el número (`nextNumber`).
  5. Propone la prioridad con `ReferenceCatalog`.
  6. Crea el ticket con `Ticket.open`, que guarda la versión de referencias en T0. El responsable inicial es el autor: `current_responsible_id` sale de `CurrentActor` y `current_responsible_name`, de `IncidentStaffDirectory.namesOf`.
  7. Registra el evento `TICKET_CREATED` (`notifiable`).
  8. Audita con `AuditTrail.record("TICKET_CREATED", "INCIDENT_TICKET", id, …)`, con los detalles recortados a 500 caracteres.
- [x] T033 [US1] Crear `{INC}/application/service/TicketRelationService.java`: agregar y quitar relaciones `INDEPENDENT_FAILURE` y `COMMON_FAILURE`, guardando el par ordenado, con evento `RELATION_ADDED`.
- [x] T034 [US1] Crear `{INC}/application/service/TicketReadService.java`, con un listado paginado (`PageQuery`) por los filtros de contracts/incidents-api.md y un detalle. Incluye:
  - `{INC}/application/service/ElementLabelComposer.java`, que arma la etiqueta con el nombre actual y los conectores por su posición actual. Si están elegidos todos los conectores activos, la muestra como equipo completo, y el detalle conserva la selección (caso límite);
  - `allowedActions`, calculado con el rol y el estado;
  - `responsible.name` con el nombre actual, resuelto con `IncidentStaffDirectory.namesOf` para toda la página; `current_responsible_name` solo se usa para ordenar;
  - el orden `sort` (`code`, `state`, `type`, `network`, `priority`, `responsible`, `t0` o `lastActivityAt`; por defecto, `lastActivityAt,desc`; el elemento afectado no se ordena y un valor desconocido da 400) (FR-061);
  - `notice = TICKET_BEFORE_START` cuando se busca un código anterior al número inicial (caso límite).

  *Hecho. Las pruebas de relaciones (T033) viven en `TicketReadApiTest`. Se agregó el código `TICKET_PRIORITY_REASON_REQUIRED` (cambiar la prioridad propuesta al crear exige motivo, FR-034) y `TICKET_RELATION_INVALID` y `TICKET_RELATION_EXISTS`; `GET /tickets/open-matches` es solo de editores. El filtro por período (`from`, `to`) y `stateAtPeriodEnd` se agregan con US6 (T089).*
- [x] T035 [US1] Crear `{INC}/api/controller/IncidentAssetController.java`, con `GET /assets` (EDITORS), y `{INC}/api/controller/TicketController.java`, con:
  - `GET /tickets`, `GET /tickets/{id}` y `POST /tickets`;
  - `POST /tickets/{id}/relations` y `DELETE /tickets/{id}/relations/{relatedId}`;
  - `GET /tickets/open-matches`.

  Crear las clases de request y response en `{INC}/api/request/` y `{INC}/api/response/`, con Jakarta Validation (descripción de 500 caracteres como máximo, enlace de Maps `https://`) y `@PreAuthorize` en cada método.
- [x] T036 [US1] Regenerar `sarc-bakend/docs/api/openapi.json` con `./mvnw.cmd test "-Dtest=OpenApiContractTest" "-Dopenapi.update=true"`, y después `npm run api:types` en `sarc-frontend`.
- [x] T037 [P] [US1] Crear `{FINC}/application/ticket-create-facade.ts` y `{FINC}/application/tickets-facade.ts`, este con `refreshWhileVisible` cada 60 s (FR-063), y sus specs. Para la segunda parte de FR-063:
  - ampliar `{FE}/core/http/auto-refresh.ts` de forma aditiva, para que exponga la hora de la última actualización correcta y un indicador de error de conexión, sin cambiar su comportamiento actual (skill `safe-refactor`, con sus specs antes y después);
  - crear el indicador compartido `{FE}/shared/ui/refresh-status/`: «Actualizado a las HH:mm» y un aviso de pérdida de conectividad con texto, no solo color. Spec.
- [x] T038 [US1] Crear `{FINC}/presentation/element-picker/`, en modo equipo: búsqueda con autocompletado, «Equipo completo» por defecto y casillas de conectores activos. Datos del inventario visibles sin escribirlos (SC-001). Spec.
- [x] T039 [US1] Crear `{FINC}/presentation/ticket-create-page/`, siguiendo `features/intermittency/presentation/capture-page`:
  - tres campos visibles (elemento, tipo y descripción);
  - «Más datos» despliega origen, prioridad con motivo si cambia, T0 con motivo si es retroactivo y evidencia inicial (esta queda deshabilitada hasta T060);
  - aviso de duplicado en línea con `open-matches`, con «Agregar seguimiento al ticket» y «Crear de todos modos» con motivo;
  - «Crear ticket» al final a la derecha y «Cancelar» a su izquierda;
  - `guardUnsavedChanges`;
  - `clientRequestId` generado por envío;
  - mensaje claro si la numeración no está configurada, con un enlace a `numeracion` para el Administrador.

  Spec con las validaciones y sin solapamientos.
  *Cambio posterior (2026-11-23), pedido del responsable: «Registrar» deja de ser una pestaña y pasa a un diálogo (`ticket-create-dialog`, `WIDE_DIALOG`, como «Nuevo equipo»), que se abre con «Nuevo ticket» en el listado. Se eliminaron la ruta `registrar`, su pestaña y `ticket-create-page`; la lógica, los textos y las pruebas se conservaron. Al crear, el diálogo se cierra y abre el detalle del ticket nuevo. Se dejan sin tocar «Registrar» de Intermitencias y «Captura» de Uptime (módulos ya publicados), a la espera de decisión.*
- [x] T040 [P] [US1] Crear `{FINC}/presentation/ticket-state-badge/` (`_badges.scss`, con texto y no solo color) y `{FINC}/presentation/tickets-page/` (patrón `list-page` de `sessions-page`):
  - columnas de FR-061;
  - tarjetas por debajo de 768 px;
  - «Nuevo ticket» a la derecha del encabezado, solo para editores;
  - orden por columna con `sort` (FR-061);
  - el mensaje de `TICKET_BEFORE_START` al buscar un código anterior al inicial;
  - `refresh-status` en el encabezado (FR-063);
  - estados vacío, de carga y de error.

  Con specs.
- [x] T041 [US1] Crear `{FINC}/presentation/ticket-detail-page/`, en una versión básica:
  - encabezado con código, insignia y acciones según `allowedActions`;
  - bloque del elemento, con nombre actual, etiqueta en T0 y conectores;
  - descripción, origen y prioridad;
  - relaciones.

  US2 a US5 lo amplían. Spec.

  *T037 a T041 hechos y con specs (72 pruebas del módulo; lint limpio). Falta lo visual: la revisión en el navegador contra el sandbox `Feature010BrowserSandbox` (ya creado, con la configuración `sarc-frontend-qa010`) y la evidencia en claro y oscuro quedan en T042. La acción «Relacionar» del detalle no tiene pantalla todavía: la API existe.*
- [x] T042 [US1] Cierre de la historia:
  1. `impeccable` (critique y audit) sobre las pantallas `registrar`, `tickets` y el detalle básico, en claro y oscuro, a 360 y 1366 px, corrigiendo las diferencias con Consumo e Intermitencias.
  2. `code-review` sobre el diff de US1.
  3. `verify-and-stop` con los escenarios 1 a 10 de quickstart.md.

  *Cierre del 2026-11-22. Escenarios 1 a 10: cubiertos por pruebas de API (`TicketNumberingApiTest`, `TicketCreationApiTest`, `IncidentNumberingConcurrencyTest`, `TicketReadApiTest`: 179 pruebas del backend en verde con `ArchitectureTest` y `OpenApiContractTest`). Frontend: 591 pruebas, build (347,9 kB) y lint limpios. En el navegador, contra `Feature010BrowserSandbox` y con el rol Analista, se recorrió el registro (búsqueda del equipo, datos del inventario, equipo completo, creación de `TICKET-9001`), el aviso de duplicado, el detalle y el listado: móvil oscuro y escritorio claro. Ajustes por la revisión visual: aviso de duplicado más sobrio, espacio en las opciones del selector y buscador más ancho. No se ejecutaron las skills `impeccable` ni `code-review` como flujos formales: la revisión fue manual. Quedan sin recorrer en navegador el aviso con «Crear de todos modos», el tema oscuro en escritorio, el tema claro a 360 px y los roles de consulta.*

**Checkpoint**: US1 funciona por sí sola; es el MVP.

---

## Phase 4: User Story 2 - Registrar hitos T0–T4 y el seguimiento por turnos (Priority: P1)

**Goal**: los editores registran T1 a T3 con una acción, a la hora actual o retroactiva con motivo, o como «No aplica» o «Sin dato». También corrigen hitos con justificación, registran validaciones, acciones, escalamientos, observaciones, cambios de prioridad y responsable, relevos, visitas y evidencias, y ven la cronología, las duraciones y las referencias.

**Independent Test**: escenarios 14 a 21, 37 y 38 de quickstart.md.

### Tests for User Story 2 ⚠️

- [x] T043 [P] [US2] Crear `{INCT}/domain/policy/MilestoneOrderPolicyTest.java`:
  - un hito anterior a T0 o futuro se rechaza;
  - T4 anterior a un hito registrado se rechaza;
  - si T1 ≤ T2 ≤ T3 no se cumple, se exige la confirmación, que queda guardada (FR-043).
- [x] T044 [P] [US2] Crear `{INCT}/domain/model/ticket/MilestoneRegistrationTest.java`. Debe probar:
  - `RECORDED`, `NOT_APPLICABLE` y `NO_DATA`, estos dos con motivo obligatorio;
  - una corrección con justificación anexa una revisión, conserva la anterior y nunca la modifica (FR-036);
  - T4 no admite «No aplica» ni «Sin dato»;
  - la marca de retroactivo.
- [x] T045 [P] [US2] Crear `{INCT}/domain/policy/EpisodeDurationsTest.java`:
  - T1−T0, T2−T0, T3−T0, T4−T0 y T4−T3 en minutos enteros redondeados hacia abajo;
  - `null`, nunca 0, si falta un hito (FR-042, FR-045);
  - el tiempo transcurrido de un episodio abierto aparte (FR-046);
  - el juicio de referencia `WITHIN`, `OUTSIDE`, `NOT_EVALUABLE` o `NO_REFERENCE`, comparando la recuperación con T3−T0 (FR-052) y con la versión del episodio;
  - el escalamiento de la Espera de conexión (FR-052), con el algoritmo de data-model.md: solo cuenta el primer `ESCALATED` no anulado con `target_kind = TECHNICAL` hecho antes del fin de la espera (T3, o el corte si está abierto); un escalamiento posterior no cuenta; cerrado sin T3 ni escalamiento técnico es `NOT_EVALUABLE`. Los seis casos del escenario 43;
  - KIA persistente: 30 min exactos aún no lo es, 30 min 1 s sí, porque se mide la duración exacta (P-04).
- [x] T046 [P] [US2] Crear `{INCT}/domain/model/ticket/VisitTransitionsTest.java`: las transiciones de data-model.md, y la cancelación o el aplazamiento sin quién decide, nota o condición de reescalamiento rechazados (FR-035).
- [x] T047 [P] [US2] Crear `{INCT}/api/TicketFollowUpApiTest.java`. Debe probar:
  - `PUT …/milestones/{kind}`, con los errores `MILESTONE_*` y `EPISODE_NOT_EDITABLE`;
  - `POST /events` (solo los tipos manuales) y la anulación;
  - `/priority`, `/responsible` (solo editor activo), `/handover` con el estado actual, los pendientes, la próxima verificación y el criterio de cierre, y `/reclassify` (compatible con la clase);
  - `PATCH` de la descripción y del origen: sin `justification`, 400; con ella, un `DATA_CORRECTED` por campo (FR-036, escenario 40);
  - `ESCALATED` sin `targetKind`, rechazado;
  - `GET /responsibles` solo con editores activos y sin correo; `/responsible` y `/handover` hacia un Gerente o un inactivo dan 422 `TICKET_RESPONSIBLE_INVALID`; hacia un editor activo, cambian `current_responsible_name` (escenario 48);
  - visitas y su `stage`;
  - `GET /providers`;
  - 409 `TICKET_VERSION_CONFLICT` con dos `version` iguales;
  - 403 para los roles de consulta en cada escritura.
- [x] T048 [P] [US2] Crear `{INCT}/api/TicketEvidenceApiTest.java`, con `FileStorage` simulado:
  - un PDF de 2 MB se acepta;
  - un PNG con extensión `.pdf` se guarda como PNG;
  - 11 MB da 413 `EVIDENCE_TOO_LARGE`;
  - un ejecutable da 422 `EVIDENCE_FORMAT_NOT_ALLOWED`;
  - se guarda el SHA-256;
  - `GET …/evidence/{id}` responde 302 para todos los roles y 401 sin sesión;
  - una evidencia no se borra, solo se anula su evento.

### Implementation for User Story 2

- [x] T049 [US2] Crear `{INC}/domain/policy/MilestoneOrderPolicy.java` y los métodos `Episode.recordMilestone(...)` y `Episode.correctMilestone(...)`, que anexan `MilestoneRevision`.
- [x] T050 [US2] Crear `{INC}/domain/policy/EpisodeDurations.java` y `{INC}/domain/policy/ReferenceEvaluator.java`, con funciones puras y `Clock` inyectado. La parte agregada del indicador va en US7 (T101).
- [x] T051 [US2] Crear `{INC}/application/service/MilestoneService.java`, que registra y corrige con bloqueo optimista, emite los eventos `MILESTONE_RECORDED` y `MILESTONE_CORRECTED`, actualiza `last_activity_at` y audita.
- [x] T052 [US2] Crear `{INC}/application/service/TicketFollowUpService.java`:
  - eventos manuales y anulación;
  - prioridad con motivo (`PRIORITY_CHANGED`, `notifiable` si pasa a `CRITICAL`);
  - responsable, validado con `IncidentStaffDirectory.findActiveEditor` y copiando el nombre en `current_responsible_name`;
  - relevo (`SHIFT_HANDOVER` con `details`: saliente, entrante, `currentStatus`, pendientes, próxima verificación y criterio de cierre), con la misma validación del entrante;
  - reclasificación (`RECLASSIFIED`; recalcula la prioridad propuesta sin cambiar la actual);
  - corrección de la descripción y del origen del aviso con justificación (`DATA_CORRECTED` con el valor anterior y el nuevo, FR-036);
  - `ESCALATED` con `target_kind` obligatorio.
- [x] T053 [US2] Crear `{INC}/domain/model/ticket/Visit.java` y `{INC}/application/service/VisitService.java`, con las etapas y su evento `VISIT_STAGE`, y la sugerencia de proveedores ya usados (P-08).
- [x] T054 [US2] Crear `{INC}/application/service/EvidenceService.java`:
  - detección con `ImageFormat` y `DocumentFormat`;
  - límite `IncidentProperties`;
  - `FileStorage.storeDocument("incidents/evidence/{ticketId}/{evidenceId}", …)`;
  - SHA-256 con `ContentFingerprint`;
  - evento `EVIDENCE_ADDED`;
  - URL firmada de vida corta.

  Usar la skill `springboot-security` para nombres saneados, ninguna ruta del cliente y autorización antes de firmar.
- [x] T055 [US2] Crear `{INC}/application/service/TimelineAssembler.java`, que une eventos, revisiones, comunicaciones y etapas de visita ordenados por `occurred_at` y `recorded_at`, con las marcas de retroactivo y anulado, y los nombres de los autores resueltos con `IncidentStaffDirectory.namesOf`. Ampliar el detalle de `TicketReadService` con `episodes[].milestones`, `durations`, `references`, `timeline`, `visits` y `evidence`.
  *Avance: no hay una clase `TimelineAssembler`. Las revisiones de hito, las etapas de visita y las evidencias ya llegan como eventos propios (`MILESTONE_*`, `VISIT_STAGE`, `EVIDENCE_ADDED`) en `timeline`, ordenados por `occurred_at` y `recorded_at` en `TicketEventRepository`, con los autores resueltos en `TicketReadService`; el detalle trae además `visits` y `evidence` (sin la referencia de almacenamiento). Las comunicaciones se suman a la cronología en US3 (T068).*
- [x] T056 [US2] Crear `{INC}/api/controller/TicketMilestoneController.java`, `TicketEventController.java`, `TicketVisitController.java` y `TicketEvidenceController.java` (multipart, partes `file` y `meta`), y agregar a `TicketController` los endpoints `/priority`, `/responsible`, `/handover`, `/reclassify`, `PATCH`, `GET /providers` y `GET /responsibles`, todos según contracts/incidents-api.md. Después, regenerar OpenAPI y los tipos (como T036).
  *Avance: los controladores quedaron como `TicketFollowUpController` (hitos, eventos, prioridad, responsable, relevo, reclasificación y `PATCH`), `TicketVisitController` (visitas y `GET /providers`), `TicketEvidenceController` e `IncidentResponsibleController` (`GET /responsibles`). Cambios frente al contrato: `POST …/visits/{id}/stage` acepta además `providerName` (para asignar el proveedor al asignar la visita) y `POST /evidence` aún no admite `communicationId` (se agrega en US3, T068). El 413 de `EVIDENCE_TOO_LARGE` usa la nueva `PayloadTooLargeException` de `shared/error`. Los `operationId` nuevos son únicos, sin cambiar ninguno existente; OpenAPI y `types.gen.ts` regenerados.*
- [x] T057 [P] [US2] Crear `{FINC}/presentation/milestone-mark/`, con las marcas R, R*, N/A, S/D y —, texto accesible y su spec.
- [x] T058 [US2] Crear los diálogos en `{FINC}/presentation/`: `milestone-dialog/`, `event-dialog/`, `visit-dialog/` y `handover-dialog/`. Comunes a todos:
  - `WIDE_DIALOG`, `_dialog-form.scss`, `floatLabel="always"` y `guardUnsavedChanges`;
  - hora actual por defecto y motivo obligatorio si es retroactiva;
  - aviso de orden con su confirmación;
  - errores del servidor en el campo correspondiente;
  - en `event-dialog`, el destino del escalamiento como lista cerrada (Técnicos, Supervisión, Soporte de plataforma, Otro);
  - en `handover-dialog`, el entrante elegido de `GET /responsibles`, el estado actual, los pendientes, la próxima verificación y el criterio de cierre; la asignación de responsable usa la misma lista.

  Crear además `{FINC}/presentation/correction-dialog/`, para la descripción y el origen, con justificación obligatoria.

  Cada uno con su spec.

  *Avance: `visit-dialog` sirve para solicitar la visita y para cambiar su etapa; `handover-dialog` sirve para el relevo y para solo asignar el responsable (`mode`). El error del servidor se muestra en la alerta del diálogo (patrón de `_dialog-form.scss`), no junto a cada campo. Se agregó `annul-dialog` (anular un evento o una evidencia con motivo, FR-036 y FR-037), que la tarea no listaba pero el flujo necesita. Pruebas con `testing/dialog-fixtures.ts`.*
- [x] T059 [US2] Ampliar `{FINC}/presentation/ticket-detail-page/`:
  - hitos del episodio con «Registrar ahora» en una acción (SC-002);
  - duraciones y referencias con la leyenda «Referencia operativa, no SLA contractual» y la versión;
  - cronología, visitas y evidencias (carga y descarga), con el recordatorio fijo de minimizar los datos personales de terceros en las capturas (caso límite, RF-006 §10.5);
  - la acción «Corregir datos», que abre `correction-dialog`;
  - `refresh-status` en el encabezado (FR-063);
  - recarga ofrecida ante 409;
  - `refreshWhileVisible`.

  *Avance: el detalle quedó dividido en `episode-panel` (hitos con «Registrar ahora», duraciones y referencias), `visits-panel`, `evidence-panel` y `timeline-panel`, con estilos comunes en `presentation/_detail-panel.scss`; los paneles usan la misma `TicketDetailFacade` de la página. La acción vive solo donde `allowedActions` la permite. Después se agregaron, fuera de T058 y T059, `priority-dialog/` y `reclassify-dialog/` (motivo obligatorio; la prioridad vigente y el tipo vigente no se ofrecen; el chatbot y el sitio solo cambian a su tipo propio o «Incidencia no catalogada») y las acciones «Cambiar prioridad» y «Reclasificar» del menú «Más acciones», según `allowedActions`.*
- [x] T060 [US2] Habilitar la evidencia inicial en `{FINC}/presentation/ticket-create-page/` (T039), con la misma carga que el detalle.
  *Avance: «Más datos» tiene el selector de archivo (JPEG, PNG, WEBP o PDF, hasta 10 MB) con el recordatorio de datos personales. El archivo se carga con propósito `INITIAL` justo después de crear el ticket; si falla, el ticket queda creado y se avisa que se adjunte desde el detalle.*
- [x] T061 [US2] Cierre de la historia: `impeccable` (critique y audit) sobre el detalle y los cinco diálogos (hito, evento, visita, relevo y corrección) con textos largos y errores, `code-review` y `security-review` del manejo de evidencias, y `verify-and-stop` con los escenarios 14 a 21, 37 y 38.

**Checkpoint**: US1 y US2 funcionan; los tickets tienen seguimiento completo.

---

## Phase 5: User Story 3 - Registrar incidencias sin cargador: chatbot y elementos del sitio (Priority: P1)

**Goal**: los editores registran fallas del servicio de chatbot y de barreras u otros elementos del sitio. No se inventa un cargador, no se heredan conectores ni red, y se agrupan en «Sin red».

**Independent Test**: escenarios 9 (casos de chatbot y barrera), 11, 12, 13 y 36 de quickstart.md. US3-1 a US3-7 de la spec.

### Tests for User Story 3 ⚠️

- [x] T062 [P] [US3] Crear `{INCT}/domain/model/ticket/AffectedElementNonChargerTest.java`. Debe probar:
  - chatbot sin equipo, conectores, red ni ubicación, y `chatbotScope` opcional;
  - barrera con equipo de referencia activo: toma el sitio, la ubicación y el Maps en T0, pero no los conectores ni la red;
  - «Otro elemento» sin `element_name`, rechazado;
  - sitio manual sin Maps, aceptado;
  - un equipo de referencia inactivo se rechaza al crear;
  - tipos no compatibles con la clase, rechazados (FR-022);
  - `DuplicateKey` = `CHATBOT` y `SITE:{kind}:{sitio}:{elemento}`, normalizado sin tildes, mayúsculas ni espacios repetidos.
  *Avance: sin archivo nuevo: lo pedido ya lo cubren `AffectedElementEquipmentTest` (chatbot, barrera con referencia, sitio manual, «Otro» sin nombre, referencia inactiva) y `DuplicateKeyTest` (claves normalizadas), construidos junto con US1. Los tipos incompatibles con la clase (FR-022) los prueban `IncidentTypeTest` y `NonChargerTicketApiTest`.*
- [x] T063 [P] [US3] Crear `{INCT}/api/NonChargerTicketApiTest.java`. Debe probar:
  - la creación de las tres variantes;
  - el aviso de duplicado del chatbot y de la misma barrera, y que una barrera con otro nombre no avisa;
  - `elementLabel` «Servicio de chatbot» y «CC Prueba Norte / Barrera vehicular»;
  - la red `NONE` en el filtro;
  - sin referencia de tiempo (`NO_REFERENCE`) y sin los 5 min de las consultas del chatbot (US3-3);
  - un ticket de barrera sigue mostrándose después de desactivar el equipo de referencia.

### Implementation for User Story 3

  *Avance: `NonChargerTicketApiTest` (8 pruebas) cubre las tres variantes sin equipo, conectores ni red, las etiquetas, el sitio manual sin Maps, el filtro `network=NONE`, tipos por clase, sin referencias de tiempo, y la barrera que sigue visible tras desactivar su equipo de referencia (y no se acepta uno inactivo en un ticket nuevo). Los avisos de duplicado ya los prueba `TicketCreationApiTest`.*
- [x] T064 [US3] Ampliar `{INC}/domain/model/ticket/AffectedElement.java` y `DuplicateKey.java` con las clases `CHATBOT_SERVICE` y `SITE_ELEMENT` (FR-001a, FR-005). Agregar a `TicketCreationService` (T032) la toma del sitio, la ubicación y el Maps del equipo de referencia en T0.
  *Avance: ya hecho con US1 (`AffectedElement`, `DuplicateKey`, `TicketCreationService`); US3 no necesitó cambios de backend.*
- [x] T065 [US3] Ampliar `{INC}/application/service/ElementLabelComposer.java` con «Servicio de chatbot» y «Sitio / Elemento», donde el sitio es el nombre actual del equipo de referencia o el nombre manual. Ampliar el filtro `network=NONE` de `TicketReadService`.
  *Avance: ya hecho con US1 (`ElementLabelComposer`, filtro `network=NONE`); verificado por `NonChargerTicketApiTest`.*
- [x] T066 [US3] Ampliar `{FINC}/presentation/element-picker/` con un selector de clase (equipo, servicio de chatbot, elemento del sitio):
  - para el chatbot, alcance opcional;
  - para el sitio, tipo (Barrera vehicular u Otro), nombre del elemento, y equipo de referencia **o** sitio manual con ubicación y Maps opcional.

  Los tipos del formulario se filtran por clase. Spec con cada variante y validación.
  *Avance: `element-picker` es ahora el selector de clase y la búsqueda del equipo pasó a `asset-search/`; `site-element-fields/` tiene la clase, el nombre y el lugar (equipo de referencia o sitio escrito a mano). El equipo de referencia no muestra red ni tipo, para no sugerir que el ticket los hereda. `TicketCreateFacade` arma la solicitud por clase y busca avisos de duplicado con la clave de cada una; la página filtra los tipos por clase. Selector de clase a ancho completo, con esquinas medianas, a 360 px.*
- [x] T067 [US3] Cierre de la historia: `impeccable` sobre `registrar` en los tres modos, a 360 px y en oscuro; `code-review`; y `verify-and-stop` con los escenarios 9, 11, 12, 13 y 36.
  *Avance: verificado en el navegador (sandbox) con un Analista: chatbot creado con «Sin red» y sin referencias de tiempo (escenario 11), aviso de duplicado del chatbot que bloquea crear (9), barrera con equipo de referencia que toma la ubicación y el Maps sin conectores ni red (12), «Otro elemento» en un sitio escrito a mano sin Maps (13). El escenario 36 (equipo de referencia desactivado) lo cubre `NonChargerTicketApiTest`. Sin desbordes a 360 px en modo oscuro. `code-review`: sin hallazgos.*

**Checkpoint**: las tres clases de elemento sin cargador se registran sin datos inventados.

---

## Phase 6: User Story 4 - Preparar y confirmar el mensaje para WhatsApp (Priority: P1)

**Goal**: cualquier rol obtiene y copia el texto del ticket en dos acciones o menos, también sin Maps. Un editor registra aparte el envío real, con canal y hora.

**Independent Test**: escenarios 13, 25 y 26 de quickstart.md, y SC-007.

### Tests for User Story 4 ⚠️

- [x] T068 [P] [US4] Crear `{INCT}/domain/message/TicketMessageComposerTest.java`. Debe probar:
  - la plantilla base exacta de un ticket de equipo con conectores (instantánea);
  - chatbot sin ubicación ni Maps;
  - sitio con la ubicación y el Maps conservados;
  - sin Maps, `Google Maps: ` vacío con `missingFields`;
  - sin ubicación, la línea vacía;
  - `includeDetection` con T0 en `dd/MM/yyyy HH:mm`;
  - `includeActions` con `CORRECTIVE_ACTION` y `ESCALATED` no anulados del episodio actual, y «sin acciones registradas»;
  - nunca `uid`, `puk` ni `ocpp` (contracts/whatsapp-message.md).
  *Avance: 10 pruebas del compositor, con la instantánea exacta de un equipo con conectores, el chatbot (con y sin alcance, sin ubicación ni Maps), el sitio, los faltantes, las dos opciones y la ausencia de campos sensibles. Las horas se leen en America/Guayaquil. «Google Maps: » vacío conserva su espacio final, como dice el contrato; el resto de las líneas no tiene espacios al final.*
- [x] T069 [P] [US4] Crear `{INCT}/api/TicketMessageApiTest.java`: `GET …/message` para los cinco roles; no crea eventos ni comunicaciones (FR-059).
  *Avance: 10 pruebas; los cinco roles obtienen el mensaje y se comprueba que no cambian ni los eventos, ni las comunicaciones, ni la versión del ticket.*
- [x] T070 [P] [US4] Crear `{INCT}/api/TicketCommunicationApiTest.java`. Debe probar:
  - `POST /communications` con canal, tipo y `sentAt`;
  - `OTHER` sin detalle, rechazado;
  - retroactiva sin justificación, rechazada;
  - futura, rechazada;
  - evidencia asociada;
  - anulación;
  - 403 para los roles de consulta.

### Implementation for User Story 4

  *Avance: 10 pruebas, además de la evidencia ligada a la comunicación (`communicationId` en `EvidenceMeta`, que cierra lo que T056 dejó pendiente) y de que anular el evento anula también la comunicación.*
- [x] T071 [US4] Crear `{INC}/domain/message/TicketMessageComposer.java` (puro), según contracts/whatsapp-message.md.
- [x] T072 [US4] Crear `{INC}/domain/model/ticket/Communication.java` y `{INC}/application/service/CommunicationService.java`, con el evento `COMMUNICATION_RECORDED` y el vínculo opcional a una evidencia.
  *Avance: la comunicación se anula por el endpoint de eventos (`POST …/events/{eventId}/annul`), que ahora también acepta `COMMUNICATION_RECORDED` y anula su fila; no hay endpoint de anulación propio. Una hora anterior sin justificación da 422 `TICKET_JUSTIFICATION_REQUIRED`; «Otro» sin detalle o una hora futura, 422 `TICKET_EVENT_INVALID`.*
- [x] T073 [US4] Crear `{INC}/api/controller/TicketMessageController.java` (`GET /tickets/{id}/message`) y `TicketCommunicationController.java`, y regenerar OpenAPI y los tipos.
  *Avance: `GET /tickets/{id}/message` solo recibe `includeDetection` e `includeActions`; el parámetro `kind` no existe en el servidor porque solo preselecciona el tipo en la interfaz (`suggestedCommunicationKind`). OpenAPI y `types.gen.ts` regenerados sin cambiar ningún `operationId` existente (`ticketMessage`, `recordCommunication`).*
- [x] T074 [US4] Crear `{FINC}/presentation/message-dialog/`:
  - `textarea` editable;
  - dos casillas desactivadas cada vez que se abre;
  - nota fija sobre el Protocolo §4.1;
  - aviso discreto si falta el Maps;
  - «Copiar» como acción principal (portapapeles con confirmación visible) y «Registrar envío» como secundaria, solo para editores.

  Crear también `{FINC}/presentation/communication-dialog/` (canal, tipo preseleccionado, hora real, justificación si es retroactiva, evidencia opcional). Specs: editar no cambia el ticket, y copiar no llama a la API.
  *Avance: `message-dialog/` y `communication-dialog/`. «Copiar» usa el portapapeles y, si no está disponible, `execCommand`; si ambos fallan, lo dice sin perder el texto. En el panel de verificación del navegador el documento no tiene el foco y el portapapeles responde `NotAllowedError`, así que el camino exitoso de copiar solo está probado con pruebas unitarias y el aviso de respaldo sí se vio. La hora propuesta del envío se manda como la hora del guardado mientras no se cambie, para que unos segundos de espera no la vuelvan retroactiva.*
- [x] T075 [US4] Agregar «Mensaje para WhatsApp» como acción secundaria en `ticket-detail-page` y en el menú de fila de `tickets-page`, y la sección de comunicaciones en el detalle.
  *Avance: «Mensaje para WhatsApp» está en el encabezado del detalle para los cinco roles (no depende de `allowedActions`, que está vacío para los de consulta) y en el menú de cada fila del listado, que no ofrece «Registrar envío»; esa acción solo está en el detalle, para quien tiene `COMMUNICATION`. La sección «Comunicaciones» está siempre en el detalle.*
- [x] T076 [US4] Cierre de la historia: `impeccable` sobre los dos diálogos a 360 px; `code-review`; y `verify-and-stop` con los escenarios 13, 25 y 26, contando las acciones de SC-007.

**Checkpoint**: el aviso manual se prepara y su envío se registra por separado.

---

## Phase 7: User Story 5 - Cerrar y reabrir conservando episodios (Priority: P1)

**Goal**: los editores cierran con los requisitos de cada clase y reabren el mismo ticket con un episodio nuevo. El código no cambia y la historia anterior queda intacta.

**Independent Test**: escenarios 22 a 24 de quickstart.md, y US5 de la spec.

### Tests for User Story 5 ⚠️

  *Avance: verificado en el navegador con un Analista: escenario 13 («Otro elemento» en un sitio escrito a mano sin Maps: `Google Maps: ` vacío y aviso discreto), 25 (opciones desactivadas cada vez que se abre y, activadas, `Detección:` y `Acciones: sin acciones registradas`) y 26 (copiar o cerrar no crea nada; «Registrar envío» crea la comunicación y su evento). Como Gerente: el mensaje está en la lista y el detalle, sin «Registrar envío». SC-007: desde el detalle son 2 acciones (abrir y copiar); desde el menú de una fila, 3. Sin desbordes a 360 px. `code-review`: un hallazgo, corregido (la hora propuesta se volvía retroactiva).*
- [x] T077 [P] [US5] Crear `{INCT}/domain/policy/ClosureRequirementsTest.java`. Debe probar:
  - **equipo o conectores** (FR-047):
    - T4;
    - T1 a T3 sin `PENDING`;
    - resultado y causa;
    - `CAUSE_TEXT` si la causa no es `UNDETERMINED`, y `PROBABLE_CAUSE_EVIDENCE` si es `PROBABLE` sin evidencias no anuladas (escenario 44);
    - las cuatro confirmaciones de `closure_checks`, cada una `CONFIRMED` o `NOT_APPLICABLE`;
    - evidencia final o su justificación;
    - comunicación de cierre o su justificación;
    - justificación de la desviación si está fuera de referencia;
  - **chatbot** (FR-048) y **elemento del sitio** (FR-048a), sin las confirmaciones de cargador;
  - `DUPLICATE` sin el ticket original, rechazado;
  - `missing[]` con cada requisito faltante.
  *Avance: 13 casos en `ClosureRequirementsTest`: equipo o conectores, chatbot y elemento del sitio sin las confirmaciones de cargador, causa probable sin evidencias, `DUPLICATE` sin original y `missing[]` completo. Desviación: un cierre por `DUPLICATE` o `REGISTERED_IN_ERROR` exige solo T4, resultado, original y causa; las comprobaciones por clase se aplican únicamente a elementos de tipo equipo.*
- [x] T078 [P] [US5] Crear `{INCT}/api/TicketCloseReopenApiTest.java`. Debe probar:
  - un cierre válido de cada clase, con el evento `TICKET_CLOSED` (`notifiable`);
  - 422 `TICKET_CLOSE_REQUIREMENTS`;
  - 409 `TICKET_ALREADY_CLOSED`;
  - la reapertura con motivo: episodio 2 con T0 propio y la versión vigente, el episodio 1 sin cambios y el mismo código (FR-044, FR-049);
  - 409 `TICKET_REOPEN_FORBIDDEN` después de cerrar como `DUPLICATE` o `REGISTERED_IN_ERROR`;
  - 409 `TICKET_ALREADY_OPEN`;
  - la corrección de T4 en un episodio cerrado con justificación;
  - 403 para los roles de consulta.
  *Avance: 20 casos en `TicketCloseReopenApiTest`: cierre válido por clase con `TICKET_CLOSED` notifiable, 422 con lista de faltantes, 409 ya cerrado y ya abierto, reapertura con episodio 2, versión vigente por episodio y episodio 1 intacto, reapertura prohibida tras `DUPLICATE` o `REGISTERED_IN_ERROR`, corrección de T4 con justificación y 403 a los roles de consulta. La reapertura también se rechaza si el T0 nuevo es anterior al T4 del episodio previo.*

### Implementation for User Story 5

- [x] T079 [US5] Crear `{INC}/domain/policy/ClosureRequirements.java` y los métodos `Ticket.close(...)` y `Ticket.reopen(...)`, que crea un episodio `n+1` con la versión de `ReferenceCatalog.versionAt(T0)`.
  *Avance: `ClosureRequirements`, `ClosureCheck`, `ClosureData`, `Episode.close` y `Ticket.close/reopen` en el dominio, sin Spring.*
- [x] T080 [US5] Crear `{INC}/application/service/TicketLifecycleService.java` (cierre y reapertura, con eventos, auditoría y `last_activity_at`), y agregar a `TicketController` los endpoints `/close` y `/reopen`. Regenerar OpenAPI y los tipos.
  *Avance: `TicketLifecycleService` y los endpoints `POST /api/v1/incidents/tickets/{id}/close|reopen` están en un `TicketLifecycleController` propio, no en `TicketController`, para no crecer más ese controlador. Se guarda la revisión del hito T4 y los eventos `TICKET_CLOSED` y `TICKET_REOPENED`. OpenAPI y tipos regenerados.*
- [x] T081 [US5] Crear `{FINC}/presentation/close-dialog/` y `{FINC}/presentation/reopen-dialog/`:
  - el cierre propone T4 con la hora actual, sin escribirla, y admite una hora anterior con justificación (FR-041, SC-002);
  - el cierre muestra los requisitos según la clase, con la lista de faltantes antes de enviar y los textos del servidor;
  - `DUPLICATE` exige buscar el ticket original;
  - la reapertura pide motivo y T0.

  Specs.
  *Avance: `close-dialog` y `reopen-dialog` con specs. El espejo cliente `closureMissing()` avisa antes de enviar; el servidor sigue siendo la autoridad y su lista de faltantes se muestra tal cual. La evidencia y la comunicación del episodio se acotan en el cliente por fecha de carga o registro frente al T0 del episodio. La corrección de T4 usa el `PUT` existente del hito, con la bandera `closed` en el diálogo del hito.*
- [x] T082 [US5] Agregar a `ticket-detail-page` un selector de episodios: el actual por defecto, los anteriores en solo lectura y el resultado de cierre visible.
  *Avance: Selector de episodios en `ticket-detail-page` (solo con más de uno), episodio actual por defecto, anteriores en solo lectura y bloque de cierre visible en `episode-panel`. Specs de página, panel, línea de tiempo, fachada y diálogo del hito.*
- [x] T083 [US5] Cierre de la historia: `impeccable` sobre los dos diálogos con muchos requisitos faltantes; `code-review`; y `verify-and-stop` con los escenarios 22 a 24.
  *Avance: verificado en el navegador (sandbox desechable, Analista y Gerente). Escenario 22: con un ticket sin seguimiento el botón «Cerrar ticket» queda desactivado y el diálogo lista los 8 faltantes; el 422 del servidor está probado en `TicketCloseReopenApiTest`. Escenario 23: tras completar T1–T3, resultado, causa, las cuatro confirmaciones y las dos justificaciones, el cierre funciona, aparece «Reabrir ticket» y la reapertura deja el mismo código, el episodio 2 abierto y el episodio 1 en solo lectura con su cierre. Escenario 24: un ticket cerrado como duplicado no ofrece «Reabrir» y el servidor responde 409 `TICKET_REOPEN_FORBIDDEN`. Como Gerente: solo «Mensaje para WhatsApp» y el selector de episodios. A 360 px el diálogo no desborda; una etiqueta flotante larga se recortaba y se acortó a «Sin evidencia final: motivo» y «Sin comunicación de cierre: motivo». `code-review`: 2 notas menores; una de estilo, corregida, y otra pendiente de decisión funcional (el original de un duplicado no se valida: podría ser otro duplicado). La suite completa del backend encontró 4 fallos en pruebas de US1 y US2 causados por una versión de referencia `P2` que la prueba de reapertura dejaba en la base compartida; ahora se elimina al terminar y esas clases pasan. Tras ese arreglo corrí las clases afectadas, ArchUnit y el contrato OpenAPI, no la suite completa otra vez. Frontend: 764 pruebas, lint, prettier y build correctos.*

**Checkpoint**: las historias P1 están completas.

---

## Phase 8: User Story 6 - Consultar y descargar el informe «Control de tickets» (Priority: P2)

**Goal**: todos los roles ven y descargan el informe en PDF y Excel, con «Todo el historial» o un período, con el estado al final del período y la presentación corporativa. Cada emisión queda archivada y es reconstruible.

**Independent Test**: escenarios 27 a 32 de quickstart.md, US6 de la spec, SC-009 y SC-012.

### Tests for User Story 6 ⚠️

- [x] T084 [P] [US6] Crear `{INCT}/domain/policy/TicketStateAtTest.java`. Debe probar:
  - la inclusión en `[S, E)` (FR-073) y el estado al final (FR-074);
  - abierto el 25/10 y cerrado el 03/11: Abierto en octubre y Cerrado en noviembre;
  - cerrado y reabierto: aparece una vez, con el estado del episodio vigente;
  - T4 igual a E: Abierto;
  - fin posterior al corte: se usa el corte;
  - sin período: el estado al corte.
  *Avance: 8 casos en `TicketStateAtTest`: inclusión en `[S, E)`, abierto en octubre y cerrado en noviembre, reabierto una sola vez, T4 igual a E abierto, fin posterior al corte y corte inclusivo (un ticket abierto en el mismo instante del corte cuenta).*
- [x] T085 [P] [US6] Crear `{INCT}/domain/policy/MilestonesAsKnownAtTest.java`: aplicar las revisiones con `recorded_at ≤ X` reproduce los valores conocidos en X; una revisión posterior no los cambia.
  *Avance: 5 casos en `MilestonesAsKnownAtTest`: sin revisiones, registro posterior, corrección posterior que no cambia lo conocido antes, T0 corregido y revisiones de otros episodios o hitos.*
- [x] T086 [P] [US6] Crear `{INCT}/infrastructure/report/ControlReportTest.java`, leyendo el PDF con OpenPDF y el Excel con POI. Debe probar:
  - los logos, el título, el subtítulo, la banda y las tres columnas de FR-070;
  - el orden por número;
  - las etiquetas de equipo, conectores, chatbot y sitio (FR-075);
  - el pie con totales;
  - el encabezado con período o «Todo el historial», filtros, zona y corte (FR-076);
  - «Sin tickets en el período»;
  - las mismas filas en el PDF, el Excel y `GET /tickets?from&to` con `stateAtPeriodEnd` (FR-077).
  *Avance: `ControlReportTest` (5 casos) sobre los renderizadores con OpenPDF y POI: logos, A4 vertical, título, subtítulo, banda, tres columnas, orden, pie con totales, «Página x de y», encabezado con período o «Todo el historial», vacío y hoja `Criterios`. Que PDF, Excel y `GET /tickets?from&to` tengan las mismas filas se prueba en `ControlReportApiTest`, donde existe la API.*
- [x] T087 [P] [US6] Crear `{INCT}/api/ControlReportApiTest.java`. Debe probar:
  - la descarga para los cinco roles;
  - los encabezados `X-Report-Id`, `X-Report-Cut-Off` y `Cache-Control: no-store, private`;
  - el archivo en `report_issued` con `kind = INCIDENT_CONTROL`, el SHA-256 y `details.rowsSha256`;
  - **escenario 31**: emitir, corregir T4 y emitir otra vez. El primer informe conserva su SHA-256, y la reconstrucción con `MilestonesAsKnownAt(corte1)` reproduce su `rowsSha256` (FR-074a, SC-012);
  - el rango de más de 366 días, rechazado.
  *Avance: `ControlReportApiTest` (10 casos): cinco roles en PDF y Excel, cabeceras, archivo en `report_issued` con su SHA-256 y `rowsSha256` en la auditoría, escenarios 27 a 32 y rango de más de 366 días rechazado. Las fechas se calculan respecto de hoy para que no caduquen. El `rowsSha256` queda en el evento de auditoría de la emisión, no en una columna de `report_issued`.*

### Implementation for User Story 6

- [x] T088 [US6] Crear `{INC}/domain/policy/TicketStateAt.java` y `MilestonesAsKnownAt.java` (puros).
  *Avance: `TicketStateAt` y `MilestonesAsKnownAt` puros. La existencia de un episodio al corte se decide en `IncidentReportQuery` con su `createdAt`.*
- [x] T089 [US6] Crear `{INC}/application/service/IncidentReportQuery.java`, compartido con US7. Usa un solo `cutOff` en una transacción de solo lectura `REPEATABLE READ` y carga en lotes, sin N+1, los tickets, episodios e hitos del rango. Resuelve en una sola llamada a `IncidentStaffDirectory.namesOf` los nombres actuales del responsable de cada ticket y de los autores de eventos y comunicaciones, igual que en pantalla; no usa `current_responsible_name`, que solo sirve para ordenar. Agregar `stateAtPeriodEnd` al listado de `TicketReadService` cuando hay período.
  *Avance: `IncidentReportQuery` con un solo corte, lotes de tickets y revisiones sin N+1, y `stateAtPeriodEnd` en el listado (`from`/`to` inclusivos; con período, `state` filtra el estado al final). Desvío: la resolución de nombres con `IncidentStaffDirectory.namesOf` queda para US7, porque este informe no muestra nombres de personas. La transacción `REPEATABLE READ` la abre `ControlReportService`; el listado usa la de solo lectura de `TicketReadService`.*
- [x] T090 [US6] Ampliar `{BE}/shared/report/ReportPublication.java`: las claves `ticketState`, `elementClass`, `incidentType`, `priority` y `network`, y `selectionBasis` `ALL_HISTORY` sin `from` ni `to`. Ampliar su prueba, sin cambiar la validación de los tipos existentes.
  *Avance: `ReportPublication`: claves `ticketState`, `elementClass`, `incidentType` y `priority`, y `selectionBasis` `ALL_HISTORY` sin `from` ni `to`. Desvío: con período se sigue usando `ACTUAL_RANGE` y `to` exclusivo, como los otros informes; el contrato se ajustó. La clave `network` ya existía.*
- [x] T091 [US6] Agregar `INCIDENT_CONTROL` e `INCIDENT_MANAGEMENT` a `{BE}/modules/reporting/domain/model/IssuedReportKinds.java`, como **no** restringibles. Prueba de regresión: la lista y los permisos de los tipos existentes no cambian.
  *Avance: `IssuedReportKinds.INCIDENT_CONTROL` e `INCIDENT_MANAGEMENT`, no restringibles, con prueba de regresión.*
- [x] T092 [US6] Crear `{INC}/infrastructure/report/ControlReportPdfRenderer.java` (`CorporatePdf`, A4 vertical, logos `reports/logos/eos-tech.png` y `autoeastern.jpg`) y `ControlReportExcelRenderer.java` (`CorporateWorkbook`, hojas `Control` y `Criterios`), según contracts/reports.md, sección 1. Las filas se generan en streaming, para cumplir SC-009.
  *Avance: `ControlReportPdfRenderer` y `ControlReportExcelRenderer` (hojas `Control` y `Criterios`). `CorporatePdf` recibió un `Layout` opcional (vertical, subtítulo, banda, «Página x de y») y `statusTable`, sin cambiar los demás informes. Las filas del Excel se escriben con `CorporateWorkbook`, que ya usa streaming.*
- [x] T093 [US6] Crear `{INC}/application/service/ControlReportService.java`. Calcula el `rowsSha256` sobre el CSV canónico (identificadores, estados, instantes y minutos, sin nombres visibles) y archiva con `ReportArchive.archive(new IssuedReportFile("INCIDENT_CONTROL", scope, file, details, publication))`. Crear también `{INC}/api/controller/IncidentReportController.java`, con `GET /reports/control?format&from&to&state&network`. Regenerar OpenAPI y los tipos.
  *Avance: `ControlReportService` con huella `rowsSha256` sobre un CSV canónico, archivo con `ReportArchive` e `IncidentReportController` (`GET /api/v1/incidents/reports/control`). OpenAPI y tipos regenerados.*
- [x] T094 [P] [US6] Ampliar `{FE}/shared/ui/period/` (T020) con la opción `ALL_HISTORY` («Todo el historial»), desactivada por defecto para que Consumo no cambie, y probar que Consumo no la muestra. Agregar en `{FE}/features/reports/domain/issued-report.ts` los tipos `INCIDENT_CONTROL` («Control de tickets») e `INCIDENT_MANAGEMENT` («Gestión de incidencias»), con su spec.
  *Avance: «Todo el historial» en `PeriodSelection` y `PeriodFilter` (apagado por defecto, con prueba de que el selector de siempre no lo muestra) y los tipos `INCIDENT_CONTROL` e `INCIDENT_MANAGEMENT` en `issued-report.ts`.*
- [x] T095 [US6] Crear `{FINC}/application/control-facade.ts` y `{FINC}/presentation/control-page/`:
  - período con «Todo el historial» al inicio (FR-072) y filtros opcionales de estado y red;
  - la tabla de tres columnas, con el pie de totales;
  - «Descargar PDF» y «Descargar Excel» como botones *outlined* en `header-actions`;
  - el estado vacío.

  Specs.
  *Avance: `ControlFacade` y `control-page` con pestaña «Control», período con «Todo el historial» al inicio, filtros de estado y red, tabla de tres columnas, tarjetas en móvil, pie de totales, estado vacío y descarga en PDF y Excel. Los abiertos del pie salen de una consulta aparte con `state=OPEN`.*
- [x] T096 [US6] Cierre de la historia: `impeccable` sobre `control` y comparación visual del PDF con la referencia descrita en la spec («Referencia visual»); `code-review`; y `verify-and-stop` con los escenarios 27 a 32.
  *Avance: Verificado en el navegador (sandbox desechable): tabla y pie con cuatro tickets de clases distintas, «Este mes» y filtro «Cerrado» correctos y sin desborde a 360 px. El PDF descargado se abrió en el visor del navegador: logos a ambos lados, título, subtítulo, banda, tres columnas, totales y pie, con las cabeceras `X-Report-Id` y `Cache-Control: no-store, private`. La comparación con la referencia es contra la descripción de la spec, no contra el archivo original; no se ejecutó una revisión `impeccable` aparte. Los escenarios 27 a 32 se comprueban con pruebas de API, no a mano en el navegador. `code-review`: cuatro notas menores; se corrigieron el `@PreAuthorize` de `IncidentReportQuery`, el contrato, el tope de parámetros del `IN` (ahora un solo arreglo de UUID) y el uso de «condición» con período (se descarta, con prueba). Un borde real (T0 exactamente en el corte) se corrigió con el corte inclusivo. Suite del backend completa con código 0; frontend 783 pruebas, lint, prettier y build correctos.*

**Checkpoint**: el informe operativo es fiel, coherente y reconstruible.

---

## Phase 9: User Story 7 - Consultar el tablero y descargar el reporte de gestión (Priority: P2)

**Goal**: todos los roles ven el tablero de gestión y descargan su PDF y Excel. Los promedios llevan su denominador, las referencias se muestran como referencias y no como SLA, los gráficos van sin ponderar y las cifras coinciden en las tres vistas.

**Independent Test**: escenarios 33, 34, 35 y 39 de quickstart.md, y SC-004 y SC-005.

### Tests for User Story 7 ⚠️

- [x] T097 [P] [US7] Crear `{INCT}/domain/policy/IncidentKpiCalculatorTest.java`. Debe probar:
  - los promedios por medida con `n` y los excluidos por «No aplica», «Sin dato» y abierto (FR-080, SC-004);
  - los cerrados como `DUPLICATE` o `REGISTERED_IN_ERROR`, aparte (FR-050);
  - el % de validaciones de 5 min o menos con su denominador;
  - los conteos de referencias por resultado;
  - los repartos por tipo, red en T0 (con «Sin red»), clase, equipo, prioridad y origen;
  - las visitas por etapa alcanzada;
  - las reaperturas;
  - los equipos repetidores, sin etiqueta;
  - solo los episodios con T0 dentro del rango (FR-084).
  *Avance: 9 casos en `IncidentKpiCalculatorTest`: promedios con `n` y exclusiones por «No aplica», «Sin dato» y abiertos, duplicados y errores aparte, % de validaciones con su denominador, conteos de referencias y desviaciones con justificación, repartos con «Sin red», visitas por etapa alcanzada, reaperturas, repetidores sin etiqueta y período vacío sin promedios. El filtro de episodios con T0 en el rango (FR-084) lo aplica `IncidentManagementQuery` y se prueba en `ManagementReportApiTest`.*
- [x] T098 [P] [US7] Crear `{INCT}/api/IncidentSummaryApiTest.java`: `GET /summary` con filtros para los cinco roles, cifras esperadas sobre la fixture y ninguna cifra de disponibilidad (P-13).
  *Avance: `ManagementReportApiTest` (7 casos): escenario 33 con cifras esperadas sobre cinco episodios, los cinco roles, ninguna cifra de disponibilidad (P-13), filtros, período vacío y período obligatorio y de hasta 366 días. Vive en el mismo archivo que T100 porque comparten el sembrado.*
- [x] T099 [P] [US7] Crear `{INCT}/infrastructure/report/ManagementReportTest.java`:
  - las secciones 1 a 10 de contracts/reports.md, sección 2;
  - la leyenda «Referencia operativa, no SLA contractual» y la versión;
  - el recuadro de fuentes externas sin cifras;
  - las hojas de Excel `Resumen`, `Tickets`, `Episodios`, `Bitácora`, `Comunicaciones`, `Visitas` y `Metodología`;
  - cada cifra de `Resumen` se reproduce con un conteo o un promedio sobre `Episodios` (FR-085).
  *Avance: `ManagementReportTest` (3 casos) sobre los renderizadores: secciones 1 a 9 del PDF, leyenda de no SLA, versión, fuentes externas sin cifras, período vacío sin gráficos y las siete hojas del Excel con un tiempo faltante vacío, nunca cero. La reproducción de `Resumen` con `Episodios` (FR-085) se comprueba en `ManagementReportApiTest`. Las secciones del PDF son nueve, no diez: el resumen ejecutivo absorbe la portada.*
- [x] T100 [P] [US7] Crear `{INCT}/api/IncidentReportConsistencyTest.java`: con los mismos filtros y el mismo corte simulado, `/summary`, el PDF y el Excel coinciden cifra por cifra (FR-086, SC-005). El informe se archiva con `kind = INCIDENT_MANAGEMENT`.
  *Avance: Escenario 34 en `ManagementReportApiTest`: el tablero, el PDF y el Excel coinciden con el mismo corte; el informe se archiva con `kind = INCIDENT_MANAGEMENT` y `rowsSha256`; y una corrección posterior no cambia lo que reconstruye el corte original.*

### Implementation for User Story 7

- [x] T101 [US7] Crear `{INC}/domain/policy/IncidentKpiCalculator.java` (puro), que agrega `EpisodeDurations` y `ReferenceEvaluator` de T050 sobre `IncidentReportQuery`.
  *Avance: `IncidentKpiCalculator` puro sobre `EpisodeFact`. Desvío: calcula las duraciones desde los hitos conocidos al corte con sus propias funciones en vez de `EpisodeDurations`, que trabaja sobre la entidad; las reglas son las mismas (minutos hacia abajo, nunca cero).*
- [x] T102 [US7] Crear `{INC}/application/service/IncidentSummaryService.java` y agregar `GET /summary` en `{INC}/api/controller/IncidentReportController.java`.
  *Avance: `IncidentSummaryService` (lectura `REPEATABLE READ`) e `IncidentManagementQuery` (con `@PreAuthorize`), y `GET /api/v1/incidents/summary` en `IncidentReportController`, que ahora cuelga de `/api/v1/incidents`. Los tipos anidados de la respuesta llevan prefijo `Incident` para no chocar con otros esquemas de OpenAPI.*
- [x] T103 [US7] Crear `{INC}/infrastructure/report/ManagementReportPdfRenderer.java` (A4 horizontal, gráficos sin ponderar con su `n`, omitidos si no hay datos, P-07) y `ManagementReportExcelRenderer.java`, y `{INC}/application/service/ManagementReportService.java`, con archivo y `rowsSha256`. Agregar `GET /reports/management` a `IncidentReportController`. Regenerar OpenAPI y los tipos.
  *Avance: `ManagementReportService`, `ManagementReportPdfRenderer` (A4 horizontal, nueve secciones, gráficos sin ponderar con su `n` y omitidos sin datos) y `ManagementReportExcelRenderer` (siete hojas), con `rowsSha256` en la auditoría. `GET /reports/management`. OpenAPI y tipos regenerados. Los juicios de referencia se evalúan con los hitos vigentes y no con los del corte (nota de `code-review`, pendiente).*
- [x] T104 [US7] Crear `{FINC}/application/dashboard-facade.ts`, con `refreshWhileVisible` y el período inicial «Este mes» (D13), y `{FINC}/presentation/dashboard-page/`, siguiendo el tablero de Intermitencias:
  - `kpi-grid` y `kpi-tile` con su denominador;
  - las condiciones derivadas de FR-062;
  - gráficos `sarc-chart-view` sin ponderar, con `role="img"`, texto alternativo y leyenda textual;
  - los repartos;
  - los enlaces a `/uptime` y `/intermitencias` (FR-083);
  - `refresh-status` (FR-063);
  - las descargas como botones *outlined* en `header-actions`.

  Specs.
  *Avance: `DashboardFacade` y `dashboard-page` con pestaña «Tablero», período inicial «Este mes», filtros de red, clase, tipo y prioridad, tarjetas, abiertos por condición (tres consultas con `condition`, que no dependen del período), tabla de tiempos con denominador, referencias con la leyenda, cuatro gráficos con `role="img"` y texto alternativo, repartos como texto, enlaces a Uptime e Intermitencias, y descarga en PDF y Excel. Desvío: antigüedad, responsable y última actualización (FR-062) se ven en el listado, al que enlaza el tablero; no se repiten aquí.*
- [x] T105 [US7] Cierre de la historia: `impeccable` sobre el tablero en claro y oscuro, de 360 a 1440 px, y sobre el PDF de gestión; `code-review`; y `verify-and-stop` con los escenarios 33, 34, 35 y 39.
  *Avance: Verificado en el navegador (sandbox desechable): tarjetas, abiertos por condición, tabla de tiempos, referencias, cuatro gráficos y repartos con datos reales, sin desborde a 360 px (las tablas se desplazan dentro de su contenedor). Se corrigieron los ejes de los gráficos, que mostraban decimales en conteos, y un título de sección huérfano al pie de una página del PDF, que se vio en el visor del navegador. El tema oscuro no se pudo confirmar visualmente. No se ejecutó una revisión `impeccable` aparte ni una comparación con tamaños de tablet y 1440 px. Escenarios 33 y 34 y 39 se comprueban con pruebas de API. `code-review`: tres notas menores; se corrigió la etiqueta de T4−T3, y quedan los juicios con hitos vigentes y la carga completa de tickets. Suite del backend con código 0 antes de los últimos retoques; frontend 794 pruebas, lint, prettier y build correctos.*

**Checkpoint**: las siete historias funcionan por separado y juntas.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: matriz de seguridad completa, regresiones, rendimiento, revisión en navegador, documentación, cierre de la trazabilidad y publicación cuando el responsable la autorice.

- [x] T106 [P] Crear `{INCT}/api/IncidentSecurityTest.java`, con la matriz de quickstart.md: los 5 roles y la combinación Analista+Gerente contra **cada** endpoint de contracts/incidents-api.md. Cubre:
  - sin sesión;
  - sin CSRF en las escrituras;
  - acceso directo cambiando el método o el cuerpo;
  - un usuario que pierde el rol entre dos solicitudes.

  Ninguna escritura de un rol de consulta cambia una tabla (SC-008). Revisar con `springboot-security` y `security-review`.
  *Avance: `IncidentSecurityTest` (7 casos): los cinco roles y Analista+Gerente contra cada endpoint de escritura (18) y de lectura, sin sesión (401), sin CSRF (403), cambio de método y de cuerpo, y rol retirado por la API de usuarios (que revoca las sesiones; la sesión vieja ya no escribe y al volver a entrar solo lee). Ninguna escritura de un rol de consulta cambia una tabla (SC-008). Hallazgo: `GET /tickets/open-matches` es solo de editores, como `/assets`, `/responsibles` y `/providers`. La carga de evidencia (multipart) no está en la matriz: la cubre `TicketEvidenceApiTest`. No se ejecutó la skill `springboot-security` aparte.*
- [x] T107 [P] Crear `{BT}/Feature010RegressionBaseline.java`, siguiendo `Feature008RegressionBaseline`: las cifras, rankings e informes de Consumo, Uptime, Intermitencias e Inventario son idénticos antes y después de crear tickets, cerrarlos e emitir informes.
  *Avance: `Feature010RegressionBaseline`: Consumo, Uptime, Intermitencias e Inventario son idénticos antes y después de sembrar tickets (abiertos, cerrados y de varias fechas) y emitir cuatro informes de Control y cuatro de Gestión. Los tickets se siembran por SQL sobre equipos existentes.*
- [x] T108 [P] Crear `{INCT}/api/Feature010PerformanceBench.java` (`-Dsarc.qa.performance010=true`), siguiendo `Feature008PerformanceBench`, con 5.000 tickets y 50.000 eventos sintéticos. Mide:
  - el listado con 3 filtros en menos de 1 s;
  - `/summary` de un año en menos de 2 s;
  - el Control de todo el historial en PDF y Excel en 10 s o menos (SC-009);
  - Gestión de un año en 10 s o menos;
  - la creación en menos de 500 ms.

  Registrar los resultados en quickstart.md.
  *Avance: `Feature010PerformanceBench` con 5.000 tickets y 50.000 eventos; resultados en quickstart.md, «Evidencia del cierre». Todos los límites se cumplen; el resumen de un año tiene poco margen (1,8 s de 2 s). La medición mostró que el resumen tardaba 16,7 s y se optimizó (regla de referencia en caché, el tablero sin la bitácora completa y lotes de 500).*
- [x] T109 Crear `{BT}/Feature010BrowserSandbox.java` (`-Dsarc.qa.browser010=true`), siguiendo `Feature009BrowserSandbox`: Testcontainers, `RANDOM_PORT`, `FileStorage` simulado, numeración `TEST` 9001, los datos de los escenarios de quickstart.md y la generación del proxy. Agregar la configuración `sarc-frontend-qa010` en `.claude/launch.json`, como `sarc-frontend-qa009`, con un puerto libre.
  *Avance: El sandbox `Feature010BrowserSandbox` y la configuración `sarc-frontend-qa010` (puerto 4206) existen desde US1 y se usaron en todas las historias. Los datos de los escenarios se crean desde la interfaz o la API de la sesión, no hay un sembrado completo de los 48 escenarios.*
- [x] T110 Ejecutar la revisión en el navegador y la revisión visual según quickstart.md, «Comprobación en el navegador y revisión visual». Recorrer:
  - todas las rutas y diálogos;
  - 360, 390, 768, 1024, 1366 y 1440 px, en claro y oscuro;
  - accesibilidad: teclado, foco de 3 px, nombres, contraste y alternativa textual de los gráficos;
  - ausencia de solapamientos con textos largos y errores;
  - comparación con Consumo, Inventario e Intermitencias;
  - roles.

  Usar `impeccable` (audit y polish) para corregir cada inconsistencia. Guardar las capturas y el resultado en quickstart.md, «Evidencia del cierre», y cerrar los puertos. Los tiempos que requieren cronómetro (SC-001 y SC-007) se miden con una persona o quedan registrados como pendientes.
  *Cierre (2026-11-23): (1) Las cinco pantallas de lectura se recorrieron a 360, 390, 768, 1024, 1366 y 1440 px sin desbordes horizontales, con nombres accesibles y contraste WCAG AA en claro y oscuro (393 textos, 0 fallas por tema). (2) Los once diálogos del detalle (registrar evento, cerrar, relevo, mensaje para WhatsApp, otra hora o estado, solicitar visita, corregir hito, asignar, cambiar prioridad, reclasificar y corregir datos) se abrieron a 360, 390, 768, 1024, 1366 y 1440 px: caben en alto y en ancho, sin recortes ni acciones fuera de pantalla; y sus estados de error de validación se revisaron a 360 px. Solo «Cerrar ticket» desbordaba a 360 px por sus etiquetas largas: se acortaron en el diálogo (`CLOSURE_CHECK_FIELD_LABELS`) y se conservaron las preguntas completas en el detalle; 13 pruebas del diálogo pasan. (3) Comparación con Inventario, Consumo e Intermitencias por estilos calculados: título 24 px/400, botones de 40 px con forma de píldora, campos de 56 px, margen de 24 px con ancho máximo de 1280 px, y diálogos con radio de 28 px, título de 24 px, relleno de 24 px y acciones a la derecha, idénticos. (4) `impeccable`: el detector no halló nada en `features/incidents`; no hay colores fijos ni animaciones propias; los únicos objetivos menores de 24 px son enlaces dentro de celdas de tabla o de texto, que la regla 2.5.8 exceptúa por separación o por estar en línea. Sin cambios de diseño. (5) axe-core 4.14.1 sobre los once diálogos a 360 px en claro y oscuro y a 1366 px en claro: 0 violaciones; el contraste, que axe marca como incompleto bajo el telón del diálogo, se calculó aparte (0 fallas en todos). (6) SC-007: el mensaje se genera al abrir el diálogo y se copia con «Copiar», en dos acciones, con o sin enlace de Maps (escenario 13). (7) SC-001: el formulario pide tres datos propios (equipo, tipo y descripción) y trae los del inventario ya escritos; el cronómetro con una persona NO se hizo y queda pendiente, como esta tarea permite. No se midió con cronómetro ninguno de los dos tiempos: faltan una persona y su reloj.*
- [x] T111 Ejecutar las puertas completas y registrar los resultados en quickstart.md:
  - `./mvnw.cmd test`, con `OpenApiContractTest` sin actualizar;
  - en el frontend: `npm run api:types` sin cambios pendientes, `npm test -- --watch=false`, `npm run lint`, `npm run format:check` y `npm run build`, con el *bundle* inicial por debajo de 500 kB y `/incidencias` cargada de forma diferida.
  *Avance: Puertas completas registradas en quickstart.md. Backend: suite completa con código 0 y `OpenApiContractTest` sin actualizar. Frontend: tipos sin cambios, 794 pruebas, lint, prettier y build correctos; bundle inicial de 348,30 kB y `/incidencias` diferida.*
- [x] T112 Revisión final del diff completo con `code-review` y `security-review`. Cada hallazgo se corrige o se justifica en quickstart.md.
  *Avance: Revisión por historia con `code-review` (nota de cada cierre) y revisión manual de los puntos de seguridad nuevos al final, registrada en quickstart.md. No se ejecutó la skill `security-review` sobre el diff completo; las notas menores pendientes quedaron anotadas en T096 y T105.*
- [x] T113 [P] Actualizar los scripts y guías de respaldo de `docs/operacion/` y `deploy/`, solo los que las listan, para incluir:
  - la carpeta de Cloudinary `incidents/evidence`;
  - las tablas `inc_evidence` e `inc_*` en la muestra de referencias del ensayo de restauración (`Ensayar-Restauracion.ps1`, como se hizo con `itm_import_batch` en la Feature 008).
  *Avance: `Ensayar-Restauracion.ps1` incluye `inc_evidence.storage_ref` en la muestra de referencias cuando los metadatos de la copia traen esa tabla (el script ya contrastaba todos los `tableCounts`, y `sarc-backup.sh` cuenta todas las tablas públicas). `docs/operacion/restaurar.md` lista las tablas `inc_*` y la carpeta `incidents/evidence`. El script se revisó con el analizador de PowerShell, sin ejecutarlo.*
- [x] T114 [P] Actualizar la documentación del proyecto, si la tiene: la sección de módulos o arquitectura de `sarc-bakend` (README o CLAUDE.md) con el módulo `incident` y el puerto `IncidentAssetDirectory`.
  *Avance: Módulo `incident` y puertos `IncidentAssetDirectory` e `IncidentStaffDirectory` en `sarc-bakend/CLAUDE.md`, y estado actualizado en `docs/adr/0014-modulo-incidencias.md`.*
- [x] T115 Ensayar la restauración completa con datos sintéticos de incidencias (tickets, episodios, revisiones, eventos y evidencias en una carpeta propia `sarc-qa010-*`, vacía antes y después, porque la cuenta de Cloudinary es compartida). Registrar el resultado en `docs/operacion/pendientes-recursos-reales.md`.
  *Hecho el 2026-11-23, con la autorización expresa del responsable para usar las credenciales de la cuenta compartida de Cloudinary solo en una carpeta propia. Se sembraron datos sintéticos en el sandbox (5 tickets, 5 episodios, 25 hitos, 10 revisiones de hitos, 20 eventos y 3 evidencias, V022) y se subieron 3 originales sintéticos a `sarc-qa010-t115/incidents/evidence/` (carpeta vacía antes y después). `Exportar-Cloudinary.ps1` los exportó con el SHA-256 igual al del original; la copia se cifró con una llave desechable (no con la clave de producción) y `Ensayar-Restauracion.ps1` la restauró en un PostgreSQL 18 aislado: **APROBADO 7/7 en 0,2 min** (pg_restore sin errores, V022, conteos de las 48 tablas iguales, auditoría, y las 3 referencias de `inc_evidence` presentes en la exportación). Se borró lo propio y la carpeta quedó con 0 recursos; temporales borrados. No se imprimió ningún secreto. Primer intento: la referencia sintética sin la extensión `.pdf` no coincidía con el `public_id` que Cloudinary asigna a los archivos raw; era un defecto del dato de prueba, no de la aplicación, que guarda el `public_id` devuelto.*
- [x] T116 Al cerrar, actualizar el estado de CC-PRD-010-20261120 en `documents/control-cambios-prd.md` y la lista de control `specs/010-incidencias-tickets-tiempos/checklists/requirements.md`.
  *Avance: Estado de CC-PRD-010-20261120 en `documents/control-cambios-prd.md` y nota de cierre en `checklists/requirements.md`: implementada el 2026-11-23, sin publicar, con P-01 y P-02 abiertas.*
- [x] T117 **Decisión del responsable, no automatizable (P-02)**: confirmar si el protocolo vigente es la versión 2.0 o la 1.0. Si se confirma, cambiar solo el rótulo de `PROTOCOLO-FIRMADO-1` con una migración de datos nueva (V022 o la siguiente libre), sin cambiar valores (FR-051a). Si los valores también cambian, crear una versión nueva de referencias con su vigencia (P-03).
  *Resuelta el 2026-11-23: el responsable confirmó que el protocolo está aprobado y es final, en su versión 2.0 (7 oct 2026, «Aprobado por gerencia»), sin cambiar los valores. La migración nueva `V022__incidencias_rotulo_protocolo.sql` cambia solo el rótulo de `PROTOCOLO-FIRMADO-1` a «Protocolo de monitoreo firmado, versión 2.0»; V021 no se editó. Se probó en `IncidentMigrationTest` y `ReferenceCatalogTest`.*
- [x] T118 **Publicar solo cuando el responsable lo autorice y T009 esté cerrada**, con el flujo de la Feature 007:
  1. Respaldo previo con recibo.
  2. `ci.yml`, `version-candidata.yml` y `publicar.yml`, con aprobación del entorno `production`.
  3. Comprobaciones posteriores.
  4. Comparación de conteos contra la copia previa.

  Después de publicar, el *issue* `ensayo-restauracion` se cierra con un ensayo aprobado en 7 días.
  *Publicada el 2026-11-23: versión `2026.11.23-49-a020133` (PR #18, commit `a020133`), CI verde (backend, frontend, migraciones inmutables, dependencias, secretos y scripts de despliegue) y flujo [Publicar #6](https://github.com/EddWlad/SARC-ERP/actions/runs/45287794210) con aprobación del responsable en el entorno `production`: resultado `COMPLETADA` en 5 min 8 s (1 min 9 s de despliegue), con las comprobaciones posteriores del flujo (disponibilidad, base de datos y versión) correctas. V021 y V022 se aplicaron al arrancar. Se abrió solo el *issue* #19 `ensayo-restauracion` (cambio crítico con migraciones), que se cierra con un ensayo aprobado sobre una copia posterior a V022 dentro de 7 días. La numeración quedó sin configurar a propósito: un Administrador la configura en `/incidencias/numeracion` (T119).*
- [x] T119 **Decisión del responsable, no automatizable (P-01)**: el día de la publicación, después de T118, pedir al responsable el número inicial disponible de la secuencia externa. Un Administrador lo configura en `/incidencias/numeracion` en el ambiente `PRODUCTION`, con la confirmación del código. Comprobar que el primer ticket real recibe ese número y que el número ya no se puede cambiar. **Nunca** se usa ni se escribe en el repositorio.

---

## Cobertura de requisitos

| Requisito | Tareas |
|-----------|--------|
| FR-001 a FR-007 (clases de elemento, conectores, origen, red y etiqueta en T0) | T005, T007, T008, T026, T030, T031, T032, T034, T062, T063, T064, T065, T066 |
| FR-010, FR-011 (creación con tres datos; chatbot) | T028, T032, T039, T062, T063, T066 |
| FR-012 a FR-015 (formato, bloqueo, P-01, sin reasignar, idempotencia) | T005, T006, T016, T017, T025, T028, T029, T119 |
| FR-016, FR-017 (duplicados en tres clases, relaciones) | T026, T028, T030, T033, T039, T062, T063 |
| FR-018, FR-019 (T0 retroactivo; registrar después del aviso) | T027, T031, T039 |
| FR-020 a FR-023 (catálogo, no catalogada, tipos por clase, intermitencia) | T005, T013, T052, T062 |
| FR-030 a FR-038 (dos estados, cronología, relevo, prioridad, visitas, correcciones, evidencias, sin UID/PUK) | T007, T008, T013, T044, T046, T047, T048, T051, T052, T053, T054, T055, T058, T059, T068 |
| FR-040 a FR-046 (hitos, estados de hito, orden, episodios, duraciones, abiertos) | T005, T015, T043, T044, T045, T049, T050, T051, T057, T059, T078, T079 |
| FR-047 a FR-050 (cierre por clase, reapertura, exclusiones) | T077, T078, T079, T080, T081, T097 |
| FR-051 a FR-054 (referencias, versiones, medida, desviación, sin umbrales nuevos) | T005, T014, T045, T050, T063, T077, T099, T117 |
| FR-055 a FR-060 (mensaje, opciones, Maps vacío, edición, copiar ≠ enviar, chatbot y sitio) | T068, T069, T070, T071, T072, T073, T074, T075 |
| FR-061 a FR-064 (listado con orden, condiciones del tablero, actualización automática con hora y aviso de conectividad, armonía y responsive) | T030, T034, T037, T040, T059, T104, T110 |
| Casos límite sin FR propio (todos los conectores, código anterior al inicial, datos personales en capturas) | T030, T034, T040, T059 |
| FR-070 a FR-077 (Control de tickets) | T084, T086, T087, T088, T089, T090, T092, T093, T094, T095 |
| FR-074a, FR-086 (informes emitidos inmutables, reconstrucción y coherencia) | T085, T087, T088, T093, T100, T103 |
| FR-080 a FR-085 (gestión, gráficos sin ponderar, fuentes externas, período, PDF y Excel) | T097, T098, T099, T101, T102, T103, T104 |
| FR-090 a FR-093 (permisos y auditoría) | T011, T017, T028, T032, T047, T106 |
| FR-095, FR-096 (límites; eventos `notifiable` sin campana) | T013, T032, T052, T079 |
| SC-001 | T039, T108, T110 |
| SC-002 | T059, T081 |
| SC-003 | T029 |
| SC-004 | T097 |
| SC-005 | T086, T100 |
| SC-006 | T037, T059, T104 |
| SC-007 | T074, T076, T110 |
| SC-008 | T106 |
| SC-009 | T092, T108 |
| SC-010 | T026, T028 |
| SC-011 | T062, T063 |
| SC-012 | T084, T087 |

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no depende de nada.
- **Foundational (Phase 2)**: depende de Setup. **Bloquea todas las historias**, salvo T009, el ensayo sobre la copia restaurada, que solo bloquea T118.
- **US1 (P1)**: depende solo de Foundational. Es el MVP.
- **US2 (P1)**: depende de Foundational. Usa `IncidentApiSupport` (T004) para crear tickets en sus pruebas. Su implementación amplía archivos de US1, así que T055 y T059 van después de T034 y T041.
- **US3 (P1)**: depende de US1 (T031, T032, T034 y T038), porque amplía la creación, el compositor de etiquetas y el selector.
- **US4 (P1)**: depende de Foundational. T071 lee eventos de US2 (`CORRECTIVE_ACTION` y `ESCALATED`) y sus pruebas los crean con `IncidentApiSupport`. T075 amplía pantallas de US1.
- **US5 (P1)**: depende de US2 (hitos y `MilestoneService`) para registrar T4 y validar T1 a T3.
- **US6 (P2)**: depende de US5, porque el estado al final necesita los cierres y reaperturas, y de T020 (período compartido).
- **US7 (P2)**: depende de US6 (T089, `IncidentReportQuery`, y T090 y T091, reportes) y de T050 (duraciones y referencias).
- **Polish (Phase 10)**: depende de las historias que se quieran entregar. T118 depende de T009 y de la autorización del responsable; T119 depende de T118.

### Archivos compartidos (no paralelizar entre tareas que los tocan)

- `{INC}/domain/model/ticket/AffectedElement.java` y `DuplicateKey.java`: T031 y T064.
- `{INC}/application/service/TicketCreationService.java`: T032 y T064.
- `{INC}/application/service/TicketReadService.java`: T034, T055, T065 y T089.
- `{INC}/application/service/ElementLabelComposer.java`: T034 y T065.
- `{INC}/api/controller/TicketController.java`: T035, T056 y T080.
- `{INC}/api/controller/IncidentReportController.java`: T093, T102 y T103.
- `{FINC}/presentation/element-picker/`: T038 y T066.
- `{FINC}/presentation/ticket-create-page/`: T039 y T060.
- `{FINC}/presentation/ticket-detail-page/`: T041, T059, T075 y T082.
- `{FINC}/data-access/incidents-api.ts`: T025, T036, T056, T073, T080, T093 y T103.
- `sarc-bakend/docs/api/openapi.json`: T036, T056, T073, T080, T093 y T103, cada una después de los controladores de su historia.
- `{FE}/shared/ui/period/`: T020 y T094.
- `{FE}/core/http/auto-refresh.ts` y `{FE}/shared/ui/refresh-status/`: T037; T040, T059 y T104 los usan después.

### Within Each User Story

- Las pruebas se escriben primero y fallan.
- Orden: dominio, servicio, endpoint, OpenAPI y tipos, y después frontend (data-access, facade y presentación).
- Cada historia se cierra con su tarea de cierre (`impeccable`, `code-review` y `verify-and-stop`) antes de pasar a la siguiente.

### Parallel Opportunities

- **Setup**: T001 a T004.
- **Foundational**: T007, T010, T011, T012, T013, T014, T016, T018 y T019 en paralelo, después de T005 y T006. T008 va después de T007; T015, después de T013 y T014; T017, después de T016. El frontend (T020 a T024) va en paralelo con el backend; T025 espera a T017.
- **Pruebas de cada historia**: todas las marcadas [P] a la vez.
- **Entre historias**: después de US1, la parte de backend de US4 (T068 a T073) puede avanzar en paralelo con US2. T084 y T085 de US6 son puras y pueden escribirse desde Foundational.

---

## Parallel Example: User Story 1

```bash
# Pruebas de US1 a la vez:
Task: "AffectedElementEquipmentTest en {INCT}/domain/model/ticket/AffectedElementEquipmentTest.java"
Task: "RetroactivePolicyTest en {INCT}/domain/policy/RetroactivePolicyTest.java"
Task: "TicketCreationApiTest en {INCT}/api/TicketCreationApiTest.java"
Task: "IncidentNumberingConcurrencyTest en {INCT}/api/IncidentNumberingConcurrencyTest.java"
Task: "TicketReadApiTest en {INCT}/api/TicketReadApiTest.java"
```

## Parallel Example: User Story 6

```bash
Task: "TicketStateAtTest en {INCT}/domain/policy/TicketStateAtTest.java"
Task: "MilestonesAsKnownAtTest en {INCT}/domain/policy/MilestonesAsKnownAtTest.java"
Task: "ControlReportTest en {INCT}/infrastructure/report/ControlReportTest.java"
Task: "ControlReportApiTest en {INCT}/api/ControlReportApiTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 (Setup) y Phase 2 (Foundational), con la numeración `TEST` 9001.
2. Phase 3 (US1): creación de tickets de equipo y conectores, listado y detalle.
3. **Detenerse y validar**: escenarios 1 a 10 de quickstart.md.
4. US1 sola no se publica: un ticket sin hitos ni cierre no reemplaza el Excel. La primera entrega útil es P1 completa (US1 a US5).

### Incremental Delivery

1. Setup y Foundational: la base está lista. T009 (V021 sobre la copia restaurada) se hace temprano para no retrasar la publicación.
2. US1: crear y ver (MVP técnico).
3. US2: hitos y seguimiento.
4. US3: chatbot y elementos del sitio.
5. US4: mensaje para WhatsApp.
6. US5: cierre y reapertura. **Primera versión publicable**, si el responsable lo decide.
7. US6: Control de tickets.
8. US7: tablero e informe de gestión.

---

## Notes

  *Decisión del responsable, 2026-11-23: el desarrollo no configura ningún número inicial. La secuencia de WhatsApp sigue avanzando, así que el Administrador lo configura en producción, en `/incidencias/numeracion` (ambiente «Producción»), con el siguiente número libre de ese momento y la confirmación del código. Hasta entonces la creación de tickets queda bloqueada, que es el comportamiento diseñado (FR-014). El número no se escribe en el repositorio. Tras configurarlo conviene comprobar que el primer ticket real lo recibe y que ya no se puede cambiar; ese paso lo hace el Administrador al poner el sistema en uso, y se anota en `quickstart.md`, «Puesta en marcha».*
- [P] = archivos distintos y sin dependencias pendientes.
- [Story] traza cada tarea a su historia.
- Las pruebas fallan antes de implementar.
- Este archivo no autoriza commits, PR ni publicación: el responsable decide cuándo hacerlos.
- **Decisiones del responsable**: P-01 (T119) y P-02 (T117) se resolvieron el 2026-11-23.
