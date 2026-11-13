# Implementation Plan: Intermitencias de puntos de carga KIA

**Branch**: `main` local (Spec Kit reporta `008-intermitencias-kia` por `.specify/feature.json`; este comando no crea ramas) | **Date**: 2026-11-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-intermitencias-kia/spec.md`, con las aclaraciones aprobadas el 2026-11-10 y el registro CC-PRD-008-20261110.

## Summary

Este plan crea un módulo nuevo, `intermittency`, dentro del monolito modular. Su objetivo es registrar las ventanas de intermitencia de los puntos de carga KIA de dos maneras: por captura manual o por importación (XLSX, CSV o pegado) con una vista previa que se puede editar. El módulo también calcula indicadores oficiales a partir del tiempo confirmado (Top 10, tendencia y tres pasteles), emite PDF y Excel archivados y calcula la disponibilidad estimada de prueba solo para Administrador y Analista.

El plan reutiliza lo que ya existe:

- el ciclo de lote de `shared/importing`, la lectura `TabularReader` y la auditoría `AuditTrail`;
- la identidad de los equipos por medio de un puerto nuevo del catálogo, que implementa Inventario;
- los generadores corporativos de `shared/report` y el archivo `ReportArchive`;
- en la interfaz, los componentes de Consumo y Uptime.

El esquema se crea con una migración nueva y solo agrega tablas (`V018`). No modifica tablas existentes. Se publica por el flujo de la Feature 007.

## Verificación previa de la especificación

La spec y el control de cambios se revisaron contra los siete puntos solicitados antes de diseñar:

| # | Punto | Estado en la spec | Acción |
|---|-------|-------------------|--------|
| 1 | Disponibilidad por rol y roles combinados | FR-006: la reciben quienes tengan Analista o Administrador, solos o combinados; la combinación de Gerente, Supervisor y Asistente no la recibe | Sin cambios. Coincide con el acceso operativo vigente (`ConsumptionReadAccess`), pero se modela como un permiso propio (research R10) |
| 2 | Cierre de una ventana abierta de 4 h o más | Antes, FR-012 decía «pasa a Confirmada» sin mencionar FR-021a | Precisado en FR-012: al cerrarse pasa a «Por revisar»; mientras sigue abierta, nunca pasa a revisión por su duración |
| 3 | Interpretación del archivo frente a la corrección de varios días | FR-024 solo forma un fin en el mismo día o el siguiente; antes, FR-026 no permitía editar el fin completo | Precisado en FR-026: en la vista previa se edita el fin con fecha y hora completas, y queda como corrección manual |
| 4 | Cobertura con filas pendientes de vincular | Antes, FR-045 suponía conocer el punto | **Decisión del responsable**: el día queda sin cubrir para toda la red hasta resolver la fila; solo cuentan los lotes confirmados (FR-045) |
| 5 | Límites de las franjas y ventanas que atraviesan el período | Antes, FR-036 tenía límites ambiguos («1 a 4 h» frente a «más de 4 h») | **Decisión del responsable**: franjas [0, 5), [5, 15), [15, 60), [60, 240) y 240 o más; la franja depende de la duración total y la ventana solo aporta los minutos dentro del período (FR-036) |
| 6 | Inicios y fines futuros | FR-021b no decía nada sobre la resolución ni sobre confirmar más tarde | Precisado: el minuto en curso se admite y la regla se vuelve a aplicar al confirmar |
| 7 | Correspondencia entre spec y control de cambios | El control de cambios solo tenía 3 de las 9 decisiones | CC-PRD-008 ahora registra las decisiones 4 a 9 con su FR |

No quedan reglas de negocio pendientes. Las precisiones 2, 3 y 6 hacen explícito lo que ya decían otras partes de la spec, sin crear reglas nuevas. Las precisiones 4 y 5 fueron aprobadas en esta sesión.

Los análisis de consistencia posteriores (`/speckit-analyze`, 2026-11-10) agregaron seis decisiones aprobadas por el responsable, registradas como aclaraciones 10 a 15 de CC-PRD-008 y ya integradas en este diseño:
- ventanas contadas en cada día que tocan (FR-035);
- punto de prueba como operación sobre un rango (FR-016);
- días en alcance sin contar hoy (FR-046);
- porcentaje de días hasta el corte (FR-039);
- ventana de prueba siempre cerrada (FR-016);
- no solapamiento en todo estado no anulado, con la ventana abierta ocupando hasta su cierre (FR-014).

También se retiraron de la spec los Códigos asignados reales.

## Technical Context

**Language/Version**: Java 25 (backend) y TypeScript con Angular 22 (frontend).

**Primary Dependencies**: Spring Boot 4, Spring Security con Spring Session, Spring Data JPA con Hibernate, Jakarta Validation, Flyway, Apache POI (lectura con `TabularReader` y Excel con `CorporateWorkbook`), el generador PDF de `CorporatePdf`, el SDK de Cloudinary (`FileStorage`), Angular Material, Signals y Chart.js 4.6 (`sarc-chart-view`). No se agrega ninguna dependencia de librería. La base de datos sí necesita la extensión `btree_gist`, que viene con PostgreSQL (research R2).

**Storage**: PostgreSQL 18, con tablas nuevas de prefijo `itm_` y la extensión `btree_gist` para impedir solapamientos. Los originales importados y los informes emitidos se guardan en Cloudinary, en `imports/intermittency` y en el archivo de reportes vigente.

**Testing**:
- Backend: JUnit 5, Testcontainers con PostgreSQL 18, ArchUnit y `OpenAPI ContractTest`.
- Frontend: las pruebas unitarias del proyecto (`npm test`) y el lint.
- Navegador: el sandbox con el patrón `Feature00xBrowserSandbox`.

**Target Platform**: VPS Contabo con Ubuntu, Docker Compose y PostgreSQL 18, publicado según la Feature 007.

**Project Type**: aplicación web, con backend en monolito modular y SPA Angular.

**Performance Goals**:
- La vista previa de 248 filas está lista en menos de 30 s (SC-002).
- Un dashboard quincenal se calcula en menos de 2 s con un año de datos (unas 6.000 ventanas).
- La captura manual se guarda en menos de 1 s.

**Constraints**:
- Minutos enteros y zona America/Guayaquil.
- Rangos con fin exclusivo, de hasta 366 días.
- Ningún secreto en los registros.
- La autorización se valida en el backend.
- Repositorio público: sin datos reales en los fixtures.
- Migraciones inmutables.
- La publicación requiere la aprobación de `production`.

**Scale/Scope**:
- Unos 80 puntos KIA y de 250 a 500 ventanas por quincena.
- Lotes de hasta 50.000 filas (límite de `TabularReader`); en la práctica, menos de 1.000.
- 6 historias, 56 FR (FR-001 a FR-052, más FR-021a, FR-021b, FR-041a y FR-046a), 12 SC.
- Unas 7 pantallas nuevas.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Regla | Evaluación previa | Evidencia |
|-------|-------------------|-----------|
| I. Especificación antes de cambios | PASA | La spec aprobada y aclarada existe en `specs/008-intermitencias-kia/` |
| II. Reglas de negocio explícitas | PASA | Cada regla del diseño cita su FR. La restricción de exclusión en SQL implementa FR-014 tal como lo describe la spec: todo estado no anulado, y la ventana abierta hasta que se cierra (aclaración 15 de CC-PRD-008); no crea una política nueva |
| III. Migraciones inmutables | PASA | Solo `V018__intermitencias_kia.sql`, nueva; V001 a V017 quedan intactas |
| D04 (política por clase de identificador) | PASA | FR-003 fija la política del Código asignado: literal sin espacios exteriores; el valor original queda en `raw` |
| D05 (normalización y coincidencia ambigua) | PASA | V008 limita el código a seis dígitos, así que la normalización de D05 equivale a la comparación literal; un código asociado a varias identidades queda pendiente de vincular y no se elige uno (FR-003) |
| D07 (reemplazo físico) | PASA | Las ventanas siguen en el equipo original; no se trasladan al sucesor |
| D09 (primera hoja) | PASA | Se usa `TabularReader.read`, no `readSheets` |
| D12 (nombres actuales) | PASA | Los informes muestran el nombre actual y el historial guarda la etiqueta del hecho |
| D14 (empates) | PASA por analogía | Se aplica al Top 10 por decisión de la spec, sin enmendar D14 |
| D06, D08, D10, D11, D13 | No aplican | No hay conectores por posición, horarios, energía ni período de Consumo. De D04 solo aplica la política por clase de identificador; no hay UID/PUK |
| Jerarquía PRD / TRD | PASA | Las diferencias con el PRD están en CC-PRD-008; la desviación técnica (`btree_gist`) se documenta como ADR |

## Project Structure

### Documentation (this feature)

```text
specs/008-intermitencias-kia/
├── spec.md
├── plan.md              # este archivo
├── research.md          # decisiones R1–R16
├── data-model.md        # entidades, estados e invariantes
├── quickstart.md        # escenarios de validación
├── contracts/
│   ├── intermittency-api.md   # endpoints, permisos y errores
│   ├── import-format.md       # XLSX, CSV y pegado; interpretación
│   └── reports.md             # PDF, Excel y archivo
├── checklists/requirements.md
└── tasks.md             # lo genera /speckit-tasks (no este comando)
```

### Source Code (repository root)

```text
sarc-bakend/src/main/java/com/tidsec/sarc_bakend/
├── shared/catalog/
│   ├── KiaPointDirectory.java         # NUEVO puerto: puntos KIA por código, id o búsqueda
│   └── KiaPointReference.java         # NUEVO record: id, nombre, código, uso, estado administrativo
├── shared/report/ReportPublication.java   # se amplía la lista de claves y tipos de período permitidos
├── modules/inventory/application/service/
│   └── KiaPointDirectoryService.java  # NUEVO: implementa el puerto (solo lectura), igual que EquipmentDirectoryService
├── modules/reporting/application/service/IssuedReportService.java  # se agrega INTERMITTENCY a la verificación de contenido restringido
└── modules/intermittency/             # NUEVO módulo
    ├── api/{controller,request,response}/
    ├── application/{service,command,result,port}/
    ├── domain/
    │   ├── model/window/              # IntermittencyWindow, WindowRevision, WindowState, ...
    │   ├── model/importing/           # IntermittencyImportBatch, ImportRow, RowWindow, clasificaciones
    │   ├── model/coverage/            # ReviewedDay
    │   ├── policy/                    # PeriodSlicer, DurationBand, LongWindowPolicy, FutureInstantPolicy
    │   └── repository/
    └── infrastructure/{persistence,parser,report,security}/

sarc-bakend/src/main/resources/db/migration/V018__intermitencias_kia.sql   # NUEVA
sarc-bakend/docs/adr/0013-modulo-intermitencias.md                       # NUEVO ADR (módulo, puerto y exclusión btree_gist)
sarc-bakend/docs/api/openapi.json                                         # se regenera

sarc-frontend/src/app/
├── layout/navigation.ts                 # entrada «Intermitencias»
├── app.routes.ts                        # ruta /intermitencias
├── shared/ui/share-chart/ y top-equipment/  # se trasladan desde Consumo sin cambiar su comportamiento (research R12)
├── features/reports/domain/issued-report.ts  # se agrega el tipo INTERMITTENCY («Intermitencias»)
└── features/intermittency/
    ├── intermittency.routes.ts
    ├── domain/  (permisos, períodos de quincena, etiquetas, duraciones)
    ├── data-access/
    ├── application/  (facades: dashboard, captura, lote, revisión, días revisados, disponibilidad)
    └── presentation/
        ├── dashboard-page/  windows-page/  capture-page/  imports-page/
        ├── import-batch-page/  review-page/  reviewed-days-panel/  availability-section/
```

**Structure Decision**: se sigue la Opción 2 (web) que ya usa el repositorio. El módulo nuevo replica la estructura por capas y roles de `CLAUDE.md` (`api`, `application`, `domain` e `infrastructure`). Inventario solo se toca de forma aditiva, para implementar el puerto `KiaPointDirectory`; Intermitencias nunca importa sus clases. Consumo y Uptime no se modifican, salvo el traslado sin cambio de comportamiento de dos componentes visuales a `shared/ui`.

## Arquitectura y límites entre módulos

```text
          ┌────────────── Angular /intermitencias ───────────────┐
          │ tablero · ventanas · registrar · importaciones · lote │
          │ revisión · días revisados · disponibilidad (A/An)    │
          └──────────────────────────┬───────────────────────────┘
                                     │ REST /api/v1/intermittency (ver contracts/)
┌────────────────────────────────────┴──────────────────────────────────────────┐
│ modules/intermittency                                                          │
│  api ─▶ application (@PreAuthorize, @Transactional, AuditTrail)               │
│           ├─ WindowService (captura, corrección, cierre, anulación, prueba)    │
│           ├─ IntermittencyImportService + ImportRowInterpreter (ImportJobs)    │
│           ├─ ReviewedDayService                                                │
│           ├─ IntermittencySummaryService + Calculator (oficial)                │
│           ├─ AvailabilityService (estimada; permiso propio)                    │
│           └─ IntermittencyReportService (PDF/Excel ─▶ ReportArchive)           │
│  domain: ventanas, revisiones, lotes, días revisados, políticas puras         │
└──────┬───────────────────────┬──────────────────────┬─────────────────────────┘
       │ KiaPointDirectory     │ ReportArchive        │ FileStorage / AuditTrail
       ▼ (shared/catalog)      ▼ (modules/reporting)  ▼ (shared)
  modules/inventory      report_issued + Cloudinary   audit_event
```

- **Reglas de dependencia**: `intermittency` solo depende de `shared/*` y de los puertos `shared/catalog`. ArchUnit ya verifica que `domain` no dependa de Spring y que ningún módulo importe clases de otro.
- **Uptime queda aislado (FR-047)**: no hay puertos ni lecturas entre `intermittency` y `uptime`, ni KPI combinados.
- **Escritura serializada por punto**: toda escritura sobre ventanas toma un bloqueo transaccional consultivo por equipo (`pg_advisory_xact_lock`). La restricción de exclusión es la segunda barrera (research R2 y R7).

## Ciclo de vida (resumen; detalle en data-model.md)

```text
Ventana:  (captura | confirmación de lote) ─▶ ABIERTA ─cerrar─▶ ¿≥240 min? ─sí─▶ POR_REVISAR ─confirmar+justificación─▶ CONFIRMADA
                                         └─▶ CONFIRMADA ◀──── no ──────────┘
          cualquier estado ─anular+justificación─▶ ANULADA        CONFIRMADA o POR_REVISAR ⇄ PRUEBA (con justificación, por ventana o por punto y rango)
          confirmación de lote con marca de prueba en la vista previa ─▶ PRUEBA
          PENDIENTE_VINCULAR existe solo como fila de lote (sin punto), no como ventana.
Lote:     PARSING ─▶ READY (editable, revalidable) ─▶ CONFIRMING ─▶ COMPLETED   | FAILED (reintentable) | DISCARDED
Fila:     NEW · IDENTICAL · CONFLICT · INVALID · PENDING_LINK · OUT_OF_SCOPE · REVIEW  ─decisión─▶ APPLY · SKIP · KEEP_PENDING
Día:      sin marca ─marcar (A/An, día terminado)─▶ REVISADO ─retirar+justificación─▶ sin marca (historial)
```

## Cálculos (detalle en research R8 y R9)

- **Duración** = ⌊(fin − inicio) / 60 s⌋. Como los instantes ya están en minutos exactos, la diferencia es entera. **Porción en un rango** = minutos de la intersección de [inicio, fin) con el rango [desde, hasta) en America/Guayaquil.
- **Indicadores oficiales**: solo ventanas CONFIRMADAS cuya porción sea mayor que 0.
  - Las ventanas se cuentan una vez por consulta.
  - Los días-equipo son los pares (punto, día local) con porción mayor que 0.
  - Un punto es recurrente si tiene 3 o más días distintos.
- **Top 10**: se ordena por minutos de forma descendente. Se calcula el umbral, que son los minutos del décimo puesto, y se incluyen todos los que tengan minutos iguales o mayores a ese umbral. El orden de presentación es: ventanas ↓, días ↓ y nombre ↑.
- **Franjas del pastel**: se calculan con la duración total de la ventana y se suma su porción.
- **Serie diaria**: los minutos de cada día son su porción; una ventana cuenta en cada día con porción mayor que 0, con nota cuando la suma de barras supera el total (FR-035).
- **Disponibilidad estimada**:
  - Tiempo evaluado = 1.440 × días cubiertos del punto.
  - Fuera de línea = porción CONFIRMADA en esos días.
  - Valor = (evaluado − fuera de línea) / evaluado, redondeado HALF_UP a 2 decimales al presentarlo.
  - El agregado es Σ(evaluado − fuera de línea) / Σ evaluado.
  - La cobertura se mide sobre los días del rango ya terminados hasta el corte; hoy y los días futuros no cuentan (FR-046).
  - Un día está cubierto para un punto si se cumplen todas estas condiciones: el día está marcado y terminado; el punto no tiene ese día ventanas POR_REVISAR ni filas en conflicto, ni una ventana ABIERTA iniciada ese día o antes (una abierta bloquea cada día desde su inicio hasta el corte, FR-045); y no hay filas PENDING_LINK de lotes confirmados que toquen ese día.

## Protección de la disponibilidad estimada

| Superficie | Mecanismo |
|------------|-----------|
| Consulta | Endpoint propio `GET /availability` con `@PreAuthorize(IntermittencyPermissions.AVAILABILITY)`. El resumen del dashboard nunca incluye campos de disponibilidad, así que no hay campos que ocultar |
| UI | La sección y su petición solo se crean si el usuario tiene el permiso. Aunque se fuerce desde el navegador, el backend responde 403 |
| PDF/Excel | El generador agrega el apartado solo si el emisor tiene el permiso en ese momento. El informe se archiva con `ReportContentProfile.WITH_OPERATIONS` |
| Informe archivado | `IssuedReportService.download` amplía su verificación a `kind = INTERMITTENCY`. Un perfil distinto de `OFFICIAL_ONLY` sin el permiso devuelve `REPORT_OPERATIONAL_CONTENT_RESTRICTED` y ofrece emitir uno nuevo (Feature 004) |
| Auditoría y registros | No se registran valores de disponibilidad en los mensajes; solo el identificador del informe y su alcance |

`IntermittencyPermissions.AVAILABILITY` es `hasAnyRole('ADMINISTRADOR','ANALISTA')`, el mismo conjunto que `EDITORS`, pero es una constante aparte. Cuando el responsable cambie la orden (FR-006) solo se modificará esa constante y su prueba.

## Consistencia entre dashboard, PDF y Excel

El dashboard, el PDF y el Excel usan el mismo `IntermittencyCalculator`, aplicado a la misma consulta con filtros y corte normalizados (`IntermittencyQuery`). El informe guarda en `ReportPublication.criteria` los valores `from`, `to`, `equipmentIds`, `periodPreset` y `timeZone`, y su `cutOff` es el momento del cálculo. La prueba SC-006 compara las tres salidas con un único resultado esperado.

## Estrategia de pruebas (detalle en quickstart.md)

1. **Pruebas puras de dominio**:
   - `PeriodSlicer`: cruce de medianoche, de quincena y de fin de mes, y ventanas de varios días.
   - `DurationBand`: los límites 4/5, 14/15, 59/60 y 239/240.
   - `FutureInstantPolicy`: minuto en curso frente al siguiente.
   - `LongWindowPolicy`, empates del Top 10 y fórmula de disponibilidad, con el ejemplo 95,00 % y 99,50 %.
2. **Intérprete de importación**: un fixture **sintético** con códigos ficticios (research R15) reproduce las filas 3, 57, 61, 94, 99, 117, 155, 163, 228 y 240 de la plantilla, con sus horas y totales. Se usa el mismo contenido en XLSX, en CSV (con `,` y con `;`) y pegado; las horas nativas de Excel, en fracción y como texto `HH:mm`; y 7 pares de Inicio y Fin.
3. **Integración con Testcontainers**:
   - Confirmar un lote y luego reimportarlo sin crear nada nuevo.
   - Conflicto y resolución.
   - La restricción de exclusión frente a escrituras concurrentes (dos hilos) y la versión optimista.
   - Un día revisado con una fila PENDING_LINK, que lo bloquea para toda la red.
   - Ventanas abiertas y su cierre a 240 minutos o más.
   - El historial de revisiones.
4. **Seguridad**: una matriz de 5 roles más combinaciones (Analista+Gerente, Administrador+Asistente, Gerente+Supervisor+Asistente) contra cada endpoint, las descargas y los informes archivados, para cumplir SC-007.
5. **Contrato**: `OpenApiContractTest` regenerado y `npm run api:types`.
6. **Frontend**:
   - Facades y formateo de h:mm (36:42) y de los períodos de quincena.
   - Rutas protegidas por rol.
   - La sección de disponibilidad no se monta sin el permiso.
7. **Navegador y diseño** (`Feature008BrowserSandbox`): anchos de 360, 390, 768, 1024 y 1440 píxeles, temas claro y oscuro, comprobaciones de WCAG 2.2 AA y una comparación visual con Consumo.
8. **Regresión**: la suite completa (`./mvnw test` y `npm test`) más líneas base de Consumo y Uptime, con el patrón de `Feature005ConsumptionBaseline`. Sus cifras deben quedar idénticas.

## Migraciones, respaldo y despliegue

- **`V018__intermitencias_kia.sql`**:
  - `CREATE EXTENSION IF NOT EXISTS btree_gist;`
  - Tablas `itm_window`, `itm_window_revision`, `itm_import_batch`, `itm_import_row`, `itm_reviewed_day` e `itm_reviewed_day_event`.
  - Índices, la restricción de exclusión y el índice único parcial.

  No altera tablas existentes. Antes de la versión candidata se ensaya sobre una copia restaurada de producción en un PostgreSQL desechable (memoria «Migration edits break dev DB»). `btree_gist` es una extensión *trusted* desde PostgreSQL 13, así que puede crearla el propietario de la base sin ser superusuario. Si la imagen o el rol no lo permiten, el despliegue falla en la migración antes de exponer la versión; la mitigación está en research R2.
- **Compatibilidad hacia atrás**: la versión anterior no lee tablas `itm_`. Si hay que volver a ella después de publicar, la migración se conserva y no se pierden datos. Una vez que existan ventanas reales, volver atrás no las descarta (Feature 007, `recuperar.md`).
- **Respaldo**: `pg_dump` ya incluye las tablas nuevas sin cambios de configuración. Los originales importados quedan en la carpeta de Cloudinary de las importaciones. **Dependencia**: la clave de API dedicada a la exportación y el Backup de Cloudinary de `sarc-prod` (T083 de la Feature 007) siguen pendientes y se necesitan para la primera exportación mensual real. El ensayo de la Feature 008 (T127, 2026-11-13) cubrió ventanas, lotes y días revisados en la base y un original sintético en una carpeta propia de Cloudinary.
- **Publicación**:
  - CI verde.
  - `version-candidata.yml`.
  - `publicar.yml` con la aprobación de `production`.
  - Prueba de recepción de avisos vigente.
  - Copia local de menos de 24 h.

  Sin cambios en los workflows. Después de publicar se verifica con la cuenta de verificación (no Administrador) que el dashboard abra vacío sin errores.

## Riesgos y dependencias

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| La plantilla real con **códigos asignados** de puntos BLOQUEADO está sin seguimiento y no está ignorada en git; el repositorio es público | Exposición de los códigos de autorización de carga | Nunca se versiona ni se usa como fixture: se usan fixtures sintéticos. Se recomienda al responsable decidir si se ignoran `documents/plantillas/Intermit*.xlsx` y `Informe_Intermitencias_*.xlsx` (no se hace en esta etapa) |
| `btree_gist` no se puede crear en producción | La migración falla y el despliegue se detiene en mantenimiento | Ensayo previo en una copia restaurada. Alternativa sin la extensión: la restricción se sustituye por el bloqueo consultivo más la verificación del dominio (research R2) |
| Interpretar las horas de Excel (fracción frente a celda con formato de fecha) | Minutos desplazados | Pruebas con ambos formatos y la regla de ±1 s; la interpretación queda visible en la vista previa |
| Una fila PENDING_LINK bloquea días para toda la red | Cobertura baja si no se resuelve | Aviso al marcar el día y contador de pendientes en la sección de disponibilidad (decisión aprobada) |
| Trasladar `share-chart` y `top-equipment` a `shared/ui` | Regresión visual en Consumo | Traslado sin cambio de comportamiento, cubierto por las pruebas de Consumo y una comparación visual |
| La clave de exportación de Cloudinary sigue pendiente (Feature 007, T083) | La primera exportación mensual real de `sarc-prod` no incluiría los originales importados | No bloquea el cierre de la feature: el ensayo con un original sintético ya pasó (T127) |
| Ampliar `ReportPublication.KEYS` y `IssuedReportService` en código compartido | Efecto sobre Consumo y Uptime | Cambio aditivo cubierto por las pruebas de informes existentes. Revisión de US5 (2026-11-13): las reglas de `periodPreset` y `equipmentIds` viven en el núcleo compartido porque ya alberga las claves de Uptime y Consumo; un registro de validaciones por tipo de informe queda como mejora futura, no necesaria para esta feature |
| El tope de 50.000 ventanas por informe (FR-041a) se verifica tras cargar las ventanas del rango | Memoria y tiempo si un rango grande se rechaza al final | El tablero ya carga el mismo conjunto para cualquier usuario y el alcance es de hasta 366 días por unos 80 puntos; el tope protege el tamaño del archivo. Si el volumen crece, contar antes de cargar, como `InventoryReportService` |

## Constitution Check (post-design)

| Regla | Evaluación posterior | Evidencia en el diseño |
|-------|----------------------|------------------------|
| I | PASA | Ningún comportamiento sin FR. data-model.md cita el FR de cada invariante |
| II | PASA | La exclusión SQL, el índice único y los CHECK replican FR-014, FR-015 y FR-008. Ninguna regla vive solo en SQL; la spec las describe todas |
| III | PASA | Una sola migración nueva y aditiva, sin tocar las aplicadas |
| D04, D05, D07, D09, D12 | PASA | R3 (vinculación), R4 (primera hoja), data-model (etiqueta del hecho) |
| D14 por analogía | PASA | R9: umbral con empates, sin exclusión por identificador |
| Jerarquía | PASA | ADR para `btree_gist`. Las decisiones del responsable están en la spec y en CC-PRD-008, sin modificar el PDF del PRD |

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Extensión `btree_gist` (no se usa en otros módulos) | Garantiza FR-014 incluso con escrituras concurrentes desde la captura y desde la confirmación de lotes | Un índice único no puede expresar el solapamiento de rangos, y un bloqueo solo de aplicación no protege contra errores futuros en otro camino de escritura |
| Puerto nuevo `KiaPointDirectory` en lugar de ampliar `EquipmentDirectory` | Necesita el uso, el estado administrativo y la búsqueda de puntos KIA, sin cambiar el record que ya consumen Consumo y Uptime | Ampliar `EquipmentReference` obligaría a tocar sus llamadas y pruebas sin beneficio para esos módulos |
