# Research: Intermitencias de puntos de carga KIA

Fuentes: código de `sarc-bakend` y `sarc-frontend` en `main` (18bf63f), PRD v1.1, la spec 008 y las plantillas de `documents/plantillas/` (se leyeron sin versionarlas). No quedan *NEEDS CLARIFICATION* técnicos.

## R1. Módulo nuevo y acceso a Inventario

- **Decision**: crear el módulo `modules/intermittency` con un puerto público nuevo, `shared/catalog/KiaPointDirectory`, que implementa Inventario. Expone estas operaciones:
  - `findByAssignedCodes(Collection<String>)` devuelve `Map<String, List<KiaPointReference>>`. Una lista permite detectar un código asignado a varios equipos.
  - `findByIds(Collection<UUID>)`.
  - `search(String text, int limit)`.
  - `inScope(Instant at)`.

  El record `KiaPointReference` incluye `id`, `name`, `assignedCode`, `usageStatus`, `adminStatus` y `inScope`. `inScope` vale `true` solo si la red es KIA y el tipo es CHARGING_POINT.
- **Rationale**: `EquipmentDirectory.findBySerials` busca por número de serie y `EquipmentReference` no incluye ni el uso ni el estado administrativo. Ambos datos hacen falta para FR-003 y FR-045. Un puerto propio evita modificar el contrato que ya consumen Consumo y Uptime. La búsqueda compara el código en forma literal y quita solo los espacios exteriores (FR-003). Esto es compatible con el formato `^[1-9][0-9]{5}$` que exige V008.
- **Alternatives considered**: ampliar `EquipmentReference` obliga a cambiar todas sus llamadas. Leer `asset_equipment` directamente desde Intermitencias rompe la regla de módulos y ArchUnit lo rechaza.

## R2. Prevención de solapamientos y duplicados

- **Decision**: usar dos barreras:
  1. En la aplicación, `pg_advisory_xact_lock` por equipo en toda escritura (captura, cierre, corrección y confirmación de lote), seguido de la verificación de dominio de FR-014 y FR-015, que produce mensajes claros.
  2. En la base de datos, `EXCLUDE USING gist (equipment_id WITH =, tstzrange(start_at, coalesce(end_at,'infinity'), '[)') WITH &&) WHERE (state <> 'ANNULLED')`, junto con el índice único parcial `(equipment_id, start_at, end_at) WHERE state <> 'ANNULLED'`.
- **Rationale**: la exclusión es la única forma declarativa de impedir que dos rangos se solapen. El bloqueo garantiza mensajes comprensibles y orden. Las ventanas de prueba también participan, porque una ventana de prueba solapada seguiría siendo un dato incoherente. `btree_gist` es una extensión *trusted* desde PostgreSQL 13 y está incluida en la imagen oficial de PostgreSQL 18, así que la puede crear el propietario de la base. **Pendiente de comprobar en el ensayo de migración**: que el rol de la aplicación sea propietario de la base en producción.
- **Alternatives considered**: solo verificar en la aplicación, que sería vulnerable a un camino de escritura futuro mal hecho. También un trigger, que pondría lógica de producto en SQL contra el principio II. **Alternativa si `btree_gist` no está disponible**: retirar la línea de exclusión y la creación de la extensión antes de publicar la versión candidata. Se conservarían el bloqueo y el índice único, y quedaría registrado en el ADR. Para que esto no reescriba una migración aplicada (principio III), hasta que pase el ensayo de T006 V018 solo se aplica en bases desechables (Testcontainers o el PostgreSQL de ensayo): no se arranca el backend contra la base de compose ni contra ninguna base persistente. Si aun así V018 llegó a aplicarse en una base persistente, V018 no se edita: la alternativa se expresa en una migración nueva (V019) que retira la restricción de exclusión.

## R3. Vinculación por código y alcance

- **Decision**: clasificar el código de cada fila según el resultado de la búsqueda:

  | Resultado de la búsqueda | Clasificación de la fila | Motivo |
  |---|---|---|
  | Sin resultado | PENDING_LINK | `UNKNOWN_CODE` |
  | Más de un equipo | PENDING_LINK | `AMBIGUOUS_CODE`, sin elegir ninguno (D05) |
  | Equipo fuera de alcance (otra red o electrolinera) | OUT_OF_SCOPE | — |
  | Equipo ELIMINADO | PENDING_LINK | `DELETED_EQUIPMENT`, recuperable desde Inventario |
  | Equipo INACTIVO | Se vincula | Observación informativa `INACTIVE_EQUIPMENT` |
  | Nombre del archivo distinto del actual | Se vincula | Observación informativa `NAME_DIFFERS`, que no bloquea |

  Hoy `AMBIGUOUS_CODE` no puede darse con datos reales: V004 impone `UNIQUE (assigned_code)` y V008 no lo retira. Se mantiene la clasificación como defensa, por si esa restricción cambia, y se prueba con un `KiaPointDirectory` simulado.

  El nombre del archivo se usa como sugerencia de búsqueda en la interfaz, nunca como vínculo automático.
- **Rationale**: aplica FR-003, D05 y D12, y el caso del punto KIA LIBRE sin código.

## R4. Lectura e interpretación del archivo

- **Decision**: se reutiliza `TabularReader.read(byte[])` para XLSX y CSV. Solo lee la primera hoja (D09) y detecta el separador entre tabulador, `;` y `,`. Para el texto pegado se usa `readText(String)`. Sobre esas filas actúa el `ImportRowInterpreter` del módulo:
  - **Encabezados**: se normalizan sin tildes, sin distinguir mayúsculas y con espacios simples. Se reconocen con `^inicio (\d+)$` y `^fin (\d+)$` para N ≥ 1, sin límite. Si no hay fila de encabezados (el primer valor no coincide con «fecha»), se usa el orden de la plantilla operativa.
  - **Fecha**: `TabularReader` entrega una celda de fecha como `uuuu-MM-dd HH:mm:ss` y la parte de hora debe ser 00:00:00. También se aceptan `dd/MM/uuuu`, `uuuu-MM-dd` y un número de serie de Excel en texto. Cualquier otro formato es INVALID con el motivo `DATE_FORMAT`.
  - **Hora**: se acepta en tres formas:
    1. celda con formato de hora, que llega como `1899-12-31 HH:mm:ss` o `1899-12-30 HH:mm:ss`; se toma la hora del día;
    2. fracción decimal entre 0 y menos de 1, que se multiplica por 86.400 s;
    3. texto `H:mm` o `HH:mm`.

    Con segundos totales `s`, el minuto es `m = round(s/60)` si `|s − 60·m| < 1`; si no, es INVALID con el motivo `SECONDS_AMBIGUOUS`. Una fracción de 1 o más es INVALID.
  - **Fin anterior o igual al inicio**: produce `END_BEFORE_START`. Un fin igual al inicio produce además `ZERO_DURATION`. El analista resuelve con `endsNextDay=true` o editando la hora (FR-020, FR-026). Una fila del archivo nunca forma un fin posterior al día siguiente: para eso existe la edición `endAt` completa en la vista previa.
  - **Total inactivo**: se interpreta como hora o fracción; un total de 1 o más se toma como días, que es lo que hace Excel con `[h]:mm`. Se compara con la suma recalculada en minutos de las ventanas válidas no omitidas, y la comparación se repite tras cada edición o decisión. Si la diferencia es de 1 minuto o más, se agrega la observación `TOTAL_MISMATCH`, que exige `acknowledgeTotal` o una corrección (FR-027).
  - **Sin encabezado**: con `k` columnas después de la cuarta, si `k` es impar la última es Total inactivo; si es par, todas son pares (contracts/import-format.md).
- **Rationale**: la plantilla real guarda las horas como fracción y la fecha como número de serie; con el formato aplicado, POI las entrega como fecha y hora. Cubrir las tres formas evita desplazamientos de minutos. La regla de menos de 1 s absorbe los errores de coma flotante, como `0.0597222…·1440 = 86.000`, sin corregir datos reales.
- **Alternatives considered**: tomar Total inactivo como verdad, que contradice FR-027 y la fila 3. Suponer que todo fin anterior al inicio cruza la medianoche, que dejaría las filas 61, 94 y 240 como caídas de casi 24 h.

## R5. Vista previa editable

- **Decision**: cada `itm_import_row` guarda tres elementos:
  - `raw` (JSON), con los valores originales por columna; no se modifica;
  - `edits` (JSON), con las correcciones por campo o ventana, más autor y momento;
  - `interpretation` (JSON), con las ventanas resultantes, la clasificación, los errores y las observaciones.

  `PATCH /import-batches/{id}/rows/{rowId}` aplica una edición con `version` optimista y vuelve a interpretar esa fila. Después reclasifica las filas del mismo punto y de los días afectados, para detectar duplicados y solapamientos dentro del lote y contra lo ya guardado. Esto se hace en la misma petición (síncrono), porque un punto-día tiene pocas filas. Las ediciones solo se aceptan si el lote está en estado READY (`requireReviewable`).
- **Rationale**: FR-026 exige conservar el original y marcar la corrección como manual. La reclasificación síncrona da respuesta inmediata en la interfaz. La interpretación inicial del archivo sigue ejecutándose en segundo plano con `ImportJobs`, igual que en Consumo y Uptime.
- **Alternatives considered**: volver a cargar el archivo después de editarlo fuera de SARC, que no cumple FR-026. Un editor de celdas genérico, que pierde la semántica de ventana.

## R6. Reimportación y conflictos

- **Decision**: para cada ventana interpretada que ya tiene punto:

  | Situación respecto de lo guardado | Clasificación | Qué ocurre |
  |---|---|---|
  | Existe una ventana no anulada con el mismo punto, inicio y fin | IDENTICAL | No se crea nada |
  | Se solapa con una ventana existente no anulada | CONFLICT | El analista decide `KEEP_EXISTING` (se omite) o `REPLACE` (crea una revisión de la ventana existente con justificación, FR-029) |
  | Se solapa con otra ventana del mismo lote | CONFLICT intralote, código `OVERLAP_IN_BATCH` | Se resuelve con `MERGE`, `OMIT` o editando la fila (`PATCH`) |
  | Es idéntica a otra ventana del mismo lote | `DUPLICATE_IN_BATCH` | Se resuelve con `OMIT` sobre una de las dos |

  La confirmación nunca anula ventanas existentes que no aparecen en el archivo.
- **Rationale**: aplica CA-005-04 y CA-G-003, y los casos de las filas 3 y 228.

## R7. Concurrencia

- **Decision**:
  - Las tablas de cabecera de ventana, fila de lote y día revisado heredan el `@Version` de `BaseAuditableEntity`. Una versión desactualizada devuelve 409 `CONCURRENT_MODIFICATION`.
  - La confirmación de lote y la captura toman `pg_advisory_xact_lock(hash('itm', equipment_id))` en orden ascendente de equipo, para evitar interbloqueos.
  - Al marcar un día se toma el bloqueo por día.
- **Rationale**: cumple FR-018 y repite el patrón de versión optimista de `MarkTestRequest`.

## R8. Tiempo y porciones

- **Decision**:
  - Se guardan `start_at` y `end_at` como `timestamptz`, siempre en minuto exacto (CHECK `extract(second …) = 0`), más `duration_minutes`, que es un valor derivado y verificado.
  - `PeriodSlicer`, en el dominio y sin dependencias, divide las ventanas por día local en `ZoneId.of("America/Guayaquil")` y calcula las intersecciones con [desde, hasta).
  - Las quincenas son [día 1, día 16) y [día 16, día 1 del mes siguiente).
  - Los «últimos 15 días» son [hoy − 14, hoy + 1).
  - Un evento ABIERTO usa `min(corte, fin del rango)` para su duración provisional.
  - **Instante futuro** (FR-021b): `instante > truncar_a_minuto(ahora) + 59 s` se rechaza, es decir, se admite cualquier instante dentro del minuto en curso. La regla se aplica al validar, al confirmar y al cerrar.
  - **Ventana larga** (FR-021a): una ventana cerrada con `duration_minutes ≥ 240` queda en estado REVIEW con la observación `LONG_WINDOW`.
- **Rationale**: aplica PRD §3.2, RF-005.7 y el ejemplo 23:50–00:20. Calcular las porciones en Java, como hace `ConsumptionCalculator`, basta para unas 6.000 ventanas al año.

## R9. Indicadores, Top 10, franjas y disponibilidad

- **Decision**: `IntermittencyCalculator` recibe las ventanas que intersecan el rango y su estado:
  - **Oficial**: solo cuentan las CONFIRMED con porción mayor que 0. Se calculan el total en minutos, los puntos afectados, las ventanas, los pares (punto, día), los recurrentes (3 o más días) y el promedio (total ÷ ventanas, en minutos con 1 decimal).
  - **No oficial**: las de estado OPEN (cantidad y minutos provisionales), REVIEW, TEST, y las filas PENDING_LINK y CONFLICT de lotes confirmados. Se informan como cantidades por estado.
  - **Top 10**: se ordena por minutos en forma descendente. `umbral = minutos[9]` si hay al menos 10 puntos; se incluyen todos los que tengan minutos iguales o mayores al umbral y se activa `extendedByTies` si son más de 10. Los empates se presentan ordenados por ventanas ↓, días ↓ y nombre actual ↑ (sin distinguir mayúsculas ni tildes).
  - **Pasteles**:
    - `DurationBand.of(totalMinutes)` asigna las franjas [0,5), [5,15), [15,60), [60,240) y [240,∞); cada ventana suma su porción;
    - la concentración compara el Top 10 (con sus empates) con el resto;
    - otro pastel compara recurrentes con no recurrentes.

    La suma de cada pastel debe ser igual al total oficial; esa igualdad se verifica en las pruebas.
  - **Serie diaria** (FR-035): los minutos de cada día son su porción; una ventana cuenta en cada día con porción mayor que 0. Si alguna cuenta en más de un día, se activa `dailyWindowsMayExceedTotal`.
  - **Porcentaje de días del período** (Excel, FR-039): días afectados ÷ días calendario de `[desde, min(hasta, día del corte + 1))`.
  - **Disponibilidad**:
    - El alcance son los puntos con `inScope`, sin el estado DESCONECTADO. Se agregan los inactivos o eliminados que tengan ventanas en el rango. El resto se lista como exclusiones.
    - Los días candidatos son los días del rango marcados como revisados, ya terminados y anteriores al corte.
    - `blockedDays` son los días tocados por filas PENDING_LINK de lotes confirmados; aplican a toda la red.
    - Por punto, se excluyen además los días en que el punto tiene ventanas REVIEW o filas CONFLICT, y cada día desde el día de inicio de una ventana OPEN hasta el día del corte (FR-045).
    - `evaluado = 1440 × díasCubiertos`, `fuera = porción CONFIRMED en esos días`, `pct = (evaluado − fuera) / evaluado`, con `BigDecimal` y HALF_UP a 2 decimales al presentarlo.
    - El agregado se pondera por minutos.
    - La cobertura de la red es `díasCubiertosRed / díasEnAlcance`, donde `díasEnAlcance` son los días del rango ya terminados hasta el corte (sin hoy ni días futuros). Un día de red está cubierto si está marcado, terminado y no bloqueado. La cobertura por punto usa sus propios días.
- **Rationale**: aplica FR-033 a FR-036, FR-042 a FR-046a, la regla de empates de D14 por analogía y las decisiones del responsable.

## R10. Permisos

- **Decision**: crear `IntermittencyPermissions.EDITORS = hasAnyRole('ADMINISTRADOR','ANALISTA')` e `IntermittencyPermissions.AVAILABILITY` con la misma expresión, pero en una constante aparte. También `IntermittencyReadAccess` en la infraestructura del módulo, con `canReadOperational()` y `canReadAvailability()`. Las consultas oficiales quedan disponibles para todo usuario autenticado, como en Consumo. El detalle operativo se filtra en el servicio, igual que con `ConsumptionReadAccess`.
- **Rationale**: aplica FR-004 a FR-007. Una constante aparte permite cambiar la visibilidad de la prueba sin tocar los permisos de edición.

## R11. Informes y archivo

- **Decision**: crear `IntermittencyPdfRenderer` sobre `CorporatePdf` con logos, sus gráficos (`IntermittencyPdfCharts`, con el mismo enfoque que `ConsumptionPdfCharts`) e `IntermittencyExcelRenderer` sobre `CorporateWorkbook`, con las hojas Resumen, Por cargador, Ventanas, Fuera de oficiales y, si corresponde, Disponibilidad. El tipo es `kind = "INTERMITTENCY"`. Para `ReportPublication` se hacen dos ampliaciones aditivas:
  - claves `periodPreset` (FIRST_HALF, SECOND_HALF, LAST_15_DAYS, CUSTOM) y `equipmentIds`;
  - `periodPreset` se valida en una lista propia, sin tocar `PERIOD_TYPES` de Uptime.

  `IssuedReportService.download` aplica la verificación de contenido restringido a `CONSUMPTION` e `INTERMITTENCY`.
- **Rationale**: aplica FR-038 a FR-040 y RF-RPT-08 con la misma lógica de bloqueo de la Feature 004.
- **Alternatives considered**: un `ReportContentProfile` nuevo, `WITH_AVAILABILITY`. Hoy el público es el mismo que en `WITH_OPERATIONS`, así que se reutiliza. Si la orden de FR-006 cambia, se añadirá el perfil nuevo con su migración de datos.

## R12. Interfaz y armonía visual

- **Decision**:
  - Las rutas viven en `/intermitencias`: `tablero`, `ventanas`, `registrar`, `revision`, `dias-revisados`, `importaciones` e `importaciones/:id`. Todas salvo `tablero` y `ventanas` usan `roleGuard(ADMINISTRADOR, ANALISTA)`.
  - El menú suma la entrada «Intermitencias» con el icono `LucideActivitySquare` o `LucideZapOff`, elegido entre los que ya trae `@lucide/angular`, sin dependencias nuevas.
  - Se reutilizan `sarc-chart-view` (gráficos de línea y de barras), `shared/imports/*` (encabezado de lote, filtro de clasificación, diálogos de confirmación e insignias), `shared/ui/_dashboard.scss`, `_list-page.scss`, `_badges.scss`, `_section-tabs.scss` y `handset`.
  - `share-chart` y `top-equipment` se trasladan de `features/consumption/presentation` a `shared/ui`. El traslado es mecánico, sin cambios de comportamiento ni de estilos, y Consumo pasa a importarlos desde ahí. Así Intermitencias no importa código de otra feature.
  - Los colores y la tipografía salen solo de `design-system/_tokens.scss`.
- **Rationale**: aplica FR-048 y FR-049, y la memoria del proyecto «Armonía UI», según la cual las pantallas nuevas replican los patrones de Consumo e Inventario.
- **Alternatives considered**: copiar los componentes, que duplica código y lleva a que diverjan. Importarlos entre features, que acopla Intermitencias a Consumo.

## R13. Migración y producción

- **Decision**: crear `V018__intermitencias_kia.sql` (DDL en data-model.md). No se cargan datos, no se cambian tablas existentes ni se usa `ALTER` sobre tablas de otros módulos. Antes de la versión candidata se ensaya en un PostgreSQL 18 desechable, restaurado desde la última copia cifrada (procedimiento de `docs/operacion/restaurar.md`). Hay que comprobar que la migración tarde menos de 5 s y que se pueda crear `btree_gist`.
- **Rationale**: cumple el principio III y la Feature 007. La copia diaria ya incluye todo el esquema.

## R14. Contrato REST y tipos del frontend

- **Decision**: los endpoints se describen en `contracts/intermittency-api.md`. Al implementarlos se regenera `docs/api/openapi.json` con `OpenApiContractTest -Dopenapi.update=true` y después `npm run api:types`. Los campos opcionales usan `@Schema(nullable = true)`.

## R15. Fixtures y datos reales

- **Decision**: las pruebas usan un fixture **sintético**, `src/test/resources/intermittency/registro-operativo-sintetico.xlsx`, generado por la propia prueba o versionado. Tiene códigos ficticios en el rango `9xxxxx`, que se verificaron libres solo en el contenedor de prueba, y nombres genéricos. Reproduce con exactitud las horas, los totales y las anomalías de las filas reales citadas en la spec. Las plantillas reales no se versionan.
- **Rationale**: el repositorio es público y los Códigos asignados de los puntos BLOQUEADO autorizan la carga, así que no deben publicarse. Hoy `documents/plantillas/Intermitencia_copiamos_nube.xlsx` y `Informe_Intermitencias_*.xlsx` no están ignorados. Se recomienda que el responsable decida si se agregan a `.gitignore`.

## R16. Historial e informe ya emitido

- **Decision**: cada cambio crea una `itm_window_revision` (número, valores, estado, justificación, autor y momento). La cabecera `itm_window` guarda la revisión vigente desnormalizada para consultar rápido y la etiqueta del punto en la fecha del hecho (`point_label`). Los informes archivados no se regeneran.
- **Rationale**: aplica FR-017, D12, RF-RPT-08 y SC-009, con el mismo patrón que `upt_measurement` y `upt_measurement_revision`.
