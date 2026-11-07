# Data Model — Feature-006

**Propuesta 2026-11-04, sin ejecutar.** Base V013–V015. Nueva migración `V016__uptime_referencia_temporal.sql` (confirmar número libre al implementar). No editar migraciones anteriores. Mantener UUID, BaseAuditableEntity/@Version, revisiones, exclusiones, alias y puertos de inventario existentes.

## 1. Modelo temporal de dominio

`MeasurementPeriod` agrupa precisión temporal, límites, zona, referenceWeek y tipo de ventana de referencia. Java puro; una sola validación reutilizada en captura/importación/corrección/conciliación. No extenderlo a otros módulos.

| Precisión | Límites guardados | Referencia | Cálculo |
|---|---|---|---|
| KNOWN_RANGE | Ambos instantes reales no nulos, fin>inicio y no futuro | Opcional para históricos reales/otras ventanas; obligatoria en captura semanal nueva | Camino existente por minutos |
| REFERENCE_WEEK | Ambos nulos | Lunes de referencia obligatorio; reference_period_type=ROLLING_7D | Porcentaje fuente; media de conectores |
| REQUIRES_RECONCILIATION | Antecedentes existentes, ambos o ninguno; nunca afirmados como rango real en respuesta | Solo si la referencia está acreditada; si no, nula | No aporta hasta conciliación; original intacto |

Zona America/Guayaquil; los instantes reales se guardan UTC. El tipo nominal no es duración observada. Conservar segundos en límites/identidad y regla vigente de minutos de cálculo; no redondeo temporal nuevo. Fecha lunes identifica referencia, no implica comienzo del intervalo. Mantener semana cerrada para el upload semanal como en005; no introducir por técnica una prohibición nueva para el contexto de captura manual.

## 2. Cambios en upt_measurement

| Columna nueva/afectada | Regla |
|---|---|
| period_precision VARCHAR(32) | Enum de los tres casos anteriores |
| reference_week DATE | Nullable según precisión, debe ser lunes cuando exista |
| reference_period_type VARCHAR(20) | ROLLING_7D para REFERENCE_WEEK; nulo en conocidos sin referencia de informe |
| period_start / period_end | Quitar NOT NULL absoluto; CHECK condicional por precisión. REQUIRES conserva límites anteriores como antecedentes y exige ambos/nulos, con orden si existen |
| conector/equipo/source/latest/effective/excluded/auditoría/version | Se conservan, sin reasignar inventario ni reiniciar validaciones |

Índices/constraints propuestos:

- KNOWN_RANGE: UNIQUE parcial `(connector_id, source, period_start, period_end)`.
- REFERENCE_WEEK: UNIQUE parcial `(connector_id, source, reference_period_type, reference_week)`.
- REQUIRES_RECONCILIATION fuera de estas claves canónicas, precisamente para conservar colisiones sin elegir un ganador. Excluidos con identidad canónica conservan su unicidad; reimportarlos no duplica.
- Índice de lectura por `(reference_week DESC, period_precision, connector_id)`; conservar los índices útiles de fechas para conocidos. Comprobar con EXPLAIN en fixture, no añadir índices redundantes por anticipación.
- CHECK de enum, lunes, límites completos/orden y obligatoriedad de referencia/tipo de ventana. La restricción antigua uq_upt_measurement_identity se reemplaza solo después de preparar/validar casos condicionales.

La referencia no forma parte de la identidad de KNOWN_RANGE. Reenviar un intervalo idéntico con otra referencia no mueve silenciosamente el registro a otra semana: devolver conflicto de metadata para conciliación. REFERENCE_WEEK jamás se fusiona con KNOWN_RANGE por coincidencia de conector/semana.

## 3. Revisiones: preservar valores y decisiones

No editar retrospectivamente columnas inmutables de `upt_measurement_revision`: reported_percent, minutos, review_state/validated_by/at/justificación, exact_confirmed_*, etiquetas D12, latest/effective y procedencia permanecen como antecedentes. Una transición de precisión temporal no usa register/correct porque esos métodos crean PENDING y perderían la validación conservada exigida por FR020.

Añadir acceso de dominio a `effectivePeriodType`: REFERENCE_WEEK → ROLLING_7D; KNOWN_RANGE → periodType de la revisión; REQUIRES → no elegible. Una revisión histórica que decía EXACT_WEEK conserva ese dato en historial con la decisión de transición; toda consulta nueva filtra por precisión efectiva antes de considerar oficialidad. Así no vuelven a entrar semanas falsas por mirar solo el enum histórico.

Las correcciones posteriores de valores siguen el flujo actual: motivo obligatorio, nueva revisión pendiente y vigente anterior hasta validar. En referencia, usar el porcentaje fuente sin redondear a texto de presentación. REPORTED_PERCENT no implica precisión temporal; son dos ejes distintos.

### 3.1. Validación de valores sin duración real — ajuste U1, 2026-11-05

Separar dentro de MeasurementRules la evaluación numérica de las comprobaciones que necesitan un intervalo real. Una entrada explícita por precisión (con MeasurementPeriod) selecciona el recorrido; no pasar 0, 10080 ni la duración nominal como sustituto de una duración desconocida.

- KNOWN_RANGE conserva PeriodRules.check/durationMinutes, fórmula, tolerancias y observaciones de005, incluidos segundos en la identidad y minutos según la regla vigente.
- REFERENCE_WEEK no llama a PeriodRules.check/durationMinutes sobre límites nulos. Conserva ROLLING_7D y referencia, pero no evalúa WINDOW_DURATION_MISMATCH, SUM_ABOVE_DURATION o SUM_BELOW_DURATION contra una duración supuesta, ni calcula un porcentaje o desconexión reconstruyendo un intervalo.
- Mantener las reglas numéricas de005: conversión de horas al minuto más cercano; minutos enteros no negativos representables en los campos actuales; porcentaje de fuente obligatorio para REPORTED_PERCENT, precisión hasta cuatro decimales y rango validable de0 a100. Conservar crudos y el tratamiento vigente de desbordamientos, ausencias exigidas y SUM_ZERO; Ocupado ausente del modelo semanal sigue siendo ausencia permitida. Los errores que hoy impiden registrar y las observaciones no validables mantienen su distinción; no convertir todas las observaciones en rechazos del lote.
- Importación/preview, confirmación, vinculación posterior, corrección directa y validación usan esta misma evaluación. MeasurementService.correct/correctionData debe ramificar por la precisión guardada, sin asumir fechas presentes; una corrección ordinaria no cambia identidad temporal ni puede confirmar exactitud de REFERENCE_WEEK.
- Una corrección válida crea revisión PENDING y mantiene la vigente anterior hasta validarla. La transiciónFR020 conserva las decisiones anteriores y no vuelve a evaluar/aprobar valores por cambiar solo su precisión.

Prueba mínima: informe con porcentaje97,6800, Disponible9815, Con falla0, Fuera de línea234, Ocupado ausente y límites nulos puede completar importación/validación/corrección sin NPE ni duración inventada; se mantiene el valor fuente. Añadir casos de minutos negativos/desbordados, porcentaje ausente, fuera de0–100 o con escala no admitida, y acceso directo a corrección/validación. Probar también que una suma conocida superior al nominal no genera por sí sola una comparación temporal en REFERENCE_WEEK; las demás reglas numéricas siguen aplicándose.

## 4. Registro persistente de decisión temporal

Nueva tabla local `upt_measurement_temporal_change`, sin servicio de auditoría alternativo:

- id UUID, measurement_id FK, policy_version (`006-1`), expected_measurement_version, outcome (`APPLIED`, `REQUIRES_RECONCILIATION`, `VERIFIED_KNOWN_RANGE`).
- previous_temporal/result_temporal JSONB objetos: límites anteriores, precisión/tipo/referencia, números de revisiones afectadas y metadatos de declaración exacta. Guardar antes de vaciar límites canónicos al pasar a referencia.
- evidence JSONB objeto: lote, checksum comprobado del original, hoja/fila, huella de la lectura, hallazgos de horarios presentes/ausentes y revisión de origen. Las referencias privadas permanecen internas; no se serializan en respuestas generales.
- actor_id nullable para proceso, actor_kind SYSTEM/USER, occurred_at, reason; auditoría compartida con referencia al evento.
- clave idempotente por medición+versión origen+policy_version para aplicación; la misma decisión no genera otra entrada. Un resultado terminal se conserva; la conciliación posterior añade otro evento, no lo reescribe.

No clonar validaciones ni exigirlas nuevamente por cambiar solo la clasificación. No usar logs de consola como único antecedente. Control transaccional: comprobar @Version y las evidencias antes de cambiar cabecera, insertar evento y audit en la misma transacción. Si cambia mientras se lee el archivo, 409/reintento, sin aplicar a un estado ya distinto.

### 4.1. Trabajo recuperable de transición — ajuste U2, 2026-11-05

ImportJobs es el ejecutor en memoria, no la persistencia del trabajo. Añadir una sola tabla local `upt_temporal_transition_work` en la nueva V016, modelo `TemporalTransitionWork` y puerto/adaptador propios de uptime. Reutilizar BaseAuditableEntity/@Version, ImportJobs.submitAfterCommit, TransactionTemplate y el patrón de recuperación existente; sin motor de trabajos, dependencia ni cambios de infraestructura compartida.

| Campo | Restricción |
|---|---|
| id, auditoría, version | UUID y auditoría/@Version de BaseAuditableEntity |
| requested_by | UUID NOT NULL del operador autorizado que solicita |
| request_key | UUID NOT NULL; UNIQUE(requested_by, request_key) |
| policy_version | VARCHAR(20) NOT NULL; valor006-1 |
| request_hash | VARCHAR(64) NOT NULL; SHA-256 hexadecimal del contenido canónico |
| items | JSONB array NOT NULL no vacío de objetos {id UUID, version entero no negativo}; sin id repetido |
| results | JSONB objeto NOT NULL, inicialmente {}; clave id de medición, valor con versión origen, resultado y eventId cuando exista |
| status | VARCHAR(20) NOT NULL; QUEUED, RUNNING, COMPLETED, FAILED o INTERRUPTED |
| attempt | INTEGER NOT NULL, inicial0, no negativo; aumenta al iniciar una ejecución |
| last_attempt_by | UUID nullable antes del primer intento; operador que inicia/reintenta |
| started_at, finished_at | TIMESTAMPTZ nullable antes de comenzar/terminar el intento |
| failure_code | VARCHAR(64) nullable; código técnico permitido, nunca excepción, URL, secreto o referencia privada |

CHECK de tipos JSONB, vocabularios y valores escalares; verificar forma de cada item/resultado y correspondencia con items en la capa de aplicación. La lista aceptada queda inmutable; su hash ordena por UUID y versión, sin depender del orden enviado. El máximo500 de la API es el tamaño técnico del bloque ya previsto, no un límite al total de mediciones: el cliente puede enviar sucesivos bloques. No guardar bytes de originales en el trabajo.

1. Persistir QUEUED y la solicitud completa antes de submitAfterCommit. Una repetición de requested_by/request_key con mismo hash devuelve el mismo trabajo; distinto contenido devuelve409. La repetición no reinicia un trabajo fallido: se usa retry explícito.
2. Un solo worker reclama QUEUED con @Version, pasa a RUNNING e incrementa attempt. Dos envíos o reintentos concurrentes no pueden ejecutar el mismo intento. No mantener una transacción mientras se leen originales.
3. Cada decisión por medición se confirma junto con su evento temporal, AuditTrail y entrada results en una misma transacción corta, comprobando versión del trabajo y de la medición. STALE_VERSION y ALREADY_APPLIED también se guardan como resultados del item, sin inventar un evento de cambio. Todo resultado guardado es terminal dentro de ese trabajo.
4. Reinicio: ampliar InterruptedUptimeImportRecovery para marcar los trabajos QUEUED/RUNNING como INTERRUPTED, conservando solicitud y resultados. No reanudar con un SecurityContext perdido ni suplantar al solicitante; la página Revisión permite reintento explícito por un operador autorizado.
5. Retry solo desde FAILED/INTERRUPTED, con versión del trabajo, conserva items/results, vuelve a QUEUED y procesa únicamente los items sin resultado. Mantiene las versiones originalmente solicitadas: si la medición cambió, STALE_VERSION y nueva solicitud tras revisión explícita; no actualizar versiones automáticamente.
6. Cola llena: onRejected persiste FAILED/UPTIME_TRANSITION_QUEUE_FULL; no cambia mediciones ni borra resultados. Un fallo técnico inesperado persiste FAILED y código seguro. Un original inaccesible/ambiguo conserva el resultado funcional REQUIRES_RECONCILIATION establecido enFR020, no se transforma en aprobación ni reintento infinito.
7. Cuando todos los items tienen resultado, COMPLETED; puede contener conciliaciones o versiones obsoletas, sin implicar que todos los datos están incluidos. Un crash tras el último item confirmado y antes de cerrar permite retry que solo finaliza el trabajo, sin aplicar de nuevo.

La petición y el actor de cada intento permanecen trazados con AuditTrail. Los trabajos son consulta operativa; los eventos temporales append-only conservan la historia de decisiones. La autorización de consulta/retry es la misma de transición para ADMINISTRADOR/ANALISTA, incluida unión de roles.

## 5. Lotes y filas

`upt_import_batch`: agregar reference_week y precisión del lote. weekStart mantiene compatibilidad de nombre, con semántica de referencia; los declared_period_start/end antiguos quedan como antecedentes, no fuente para límites nuevos. Mantener BaseImportBatch, originales/checksum/contadores, ImportJobs y estados.

`upt_import_row`: agregar period_precision/reference_week/reference_period_type, conservar límites nullable, hoja/fila y crudos. Nueva procedencia temporal identifica si cada límite vino en la fuente; para informe sin horarios no se rellenan. Mantener clasificación/outcome y decisiones actuales; incluir precisión en `seen` y comparación. En filas pendientes la referencia sigue permitiendo contar sin atribuir aunque no exista conector resuelto.

`UptimeRowParser` devuelve evidencia de columnas/rango de fuente, no deduce exactitud ni sustituye por semana. La inspección del original de005 usa el mismo puerto y TabularReader sin forzar DeclaredPeriod, verificando checksum y hoja/fila exacta. No deducir ausencia de límites solo por source_values incompleto.

## 6. Proyecciones y políticas

- Hechos conocidos: mantener tiempo evaluado y cálculo existente, ampliar con cuatro minutos nullable y metadata temporal para composición; no leer COALESCE como dato de fuente.
- Hechos de referencia: reportedPercent BigDecimal, precisión/referencia y cuatro duraciones nullable, revisión vigente/exclusión/clase. No tener evaluatedMinutes/downMinutes artificiales.
- `UptimeCalculator` mantiene sus métodos para conocidos y añade métodos explícitos de referencia con resultado `ReferenceReportKpis`; reutilizar precedencia de estados/denominador, no una jerarquía paralela de calculadoras o servicios.
- Composición: cuatro `Long` nullable, totalKnownMinutes, selectedMeasurements, completeMeasurements y contador conocido por estado. Sumas SQL bigint/Java long; null si nadie informó,0 si hubo ceros reales. Completitud temporal y completitud de estados no son el mismo porcentaje.
- Cobertura por apartado: una unidad por conector/referencia; no unir conocidos+referencia en un denominador de KPI general. Cuando evidencia acredite referencia y apartado destino, REQUIRES_RECONCILIATION se proyecta como UnitState.PENDING_REVIEW (no VALIDATED ni WITHOUT_MEASUREMENT), queda fuera del numerador y conserva denominador según alcance; una colisión de varias mediciones del mismo conector genera una unidad pendiente, no varias. No cambia ReviewState de las revisiones originales. Si la referencia está probada pero no el apartado, mostrar su cantidad separada de conciliación temporal en la referencia, sin imputarla a ambos bloques; si tampoco la referencia se acredita, permanece solo en la cola global operativa. Detalles/motivos solo operativos; ninguna ambigüedad se distribuye por inferencia.
- Pendientes sin vincular: conteo por referencia/tipo/apartado cuando corresponde; no cláusula `period_end IS NULL` que incluya todas las semanas. Rangos reales usan solo límites conocidos. Operaciones reciben el conjunto realmente seleccionado, no todo el lookback.
- UptimeView/UptimeReport amplían la sección de referencia y mantienen la sección conocida/oficial. `ReportPublication` añade selección por referencia validada sin exigir from/to; los criterios anteriores permanecen válidos para los módulos anteriores.

## 7. Transición V015 → 006

1. Diagnóstico paginado de toda cadena donde un lote tenía declared_period_start/end: es un conjunto de candidatos, no prueba definitiva. Antes de exponer indicadores nuevos, marcarlos para revisión temporal si aún no se comprobó evidencia; conservar los límites existentes como antecedentes.
2. Leer original retenido fuera de transacción de escritura, verificar checksum/hoja/fila, valores y todas las revisiones FILE/MANUAL/BLOCK. No tocar archivos emitidos.
3. Caso inequívoco sin horarios de fuente y referencia única, sin colisión: snapshot del antecedente, pasar cabecera a REFERENCE_WEEK con fechas null, preservar IDs/revisiones/validaciones y excluir su EXACT_WEEK histórico de indicadores oficiales nuevos. La revisión vigente sigue validada sin intervención humana extra.
4. Original con horarios realmente acreditados y concordantes: mantener KNOWN_RANGE y guardar comprobación; no reinterpretar una captura real como referencia.
5. Colisión entre mediciones, cadena mixta, original inexistente/inaccesible, valores/rango incongruentes o porcentaje fuente requerido ausente: REQUIRES_RECONCILIATION. No escoger fila por última fecha, unir cadenas o generar porcentaje de fuente.
6. Conciliación por Administrador/Analista: versión, motivo y evidencia; confirmar referencia o rango conocido. Colisión canónica →409 hasta decisión explícita; la exclusión existente permite apartar el registro que la persona determine, conservándolo, sin merge de versiones. Restaurar un excluido no resuelve la ambigüedad ni sortea la unicidad.
7. Reejecutar reconoce eventos ya aplicados; no duplica decisiones ni validaciones. Recuperar interrupciones con resultados persistentes y patrón ImportJobs existente; ninguna llamada de almacenamiento en Flyway.

Aplicar schema y transición durante ventana coordinada con escrituras antiguas detenidas. No publicar una cifra oficial apoyada en candidato sin verificar durante la transición. El proceso automático termina con contadores aplicados/verificados/conflictos; pendientes quedan visibles para operar, sin atribución supuesta. Acceso a almacenamiento real es condición de operación en despliegue, no se prueba con secretos dev durante planificación.

## 8. Compatibilidad

Migración nueva y pruebas de upgrade con referencias a UUID/reportes existentes. No modificar V001–V015, energy_*, asset_*, criterios válidos de Consumo/Inventario ni bytes/hash/cortes archivados. Despliegue backend/frontend conjunto por nuevos nulos. No binario005 contra datos006; rollback mediante backup íntegro coordinado o migración hacia delante. El mecanismo concreto se implementará y probará después de tasks/analyze, no se ejecuta aquí.
