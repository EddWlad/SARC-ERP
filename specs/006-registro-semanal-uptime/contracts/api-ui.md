# Contrato API y UI — Registro semanal de uptime (006)

**Diseño 2026-11-04; no endpoints implementados en esta fase.** Extender `/api/v1/uptime` en sus controllers/DTOs actuales. Contrato ejecutable futuro: OpenAPI generado y tipos Angular derivados. Las rutas de005 siguen; los cambios de nulabilidad requieren entrega coordinada, no clientes antiguos contra supuestos nuevos.

## 1. Seguridad y formato

Sesión/CSRF/ProblemDetail existentes. Cinco roles consultan y descargan; ADMINISTRADOR/ANALISTA escriben, concilian y ven revisión operativa. Unión de roles. Rechazar acceso directo no autorizado igual que005. Ningún DTO público añade UID/PUK, OCPP, referencias de archivo privadas, motivos/versiones ni valores de revisión no visible para consulta.

Porcentajes decimales viajan como texto; minutos enteros conocidos como números, desconocidos como null/omitidos según serialización NON_NULL existente. Una propiedad ausente no representa cero. Uso uniforme de periodo/precisión y etiquetas en español.

## 2. Metadata temporal

`temporalPrecision`: KNOWN_RANGE | REFERENCE_WEEK | REQUIRES_RECONCILIATION.
`referenceWeek`: fecha lunes, nullable cuando no se acredita o para históricos reales sin contexto.
`periodStart/periodEnd`: instantes con zona/segundos cuando KNOWN_RANGE; desconocidos null/ausentes en REFERENCE_WEEK y no afirmados como reales en conciliación. Antecedentes asignados se consultan en el historial operativo con su etiqueta, no como rangos reales actuales.
`periodType` efectivo: ROLLING_7D para referencia, tipo vigente verificado para conocidos. EXACT_WEEK histórico de un Excel reclasificado se conserva en antecedente pero no vuelve como tipo oficial efectivo.

Esta metadata se aplica a lote/fila, medición/detail, revisión contextual, resultados y secciones de reportes; no inferir precisión desde uptimeBasis.

## 3. Captura y grilla

`GET /grid`: añadir `referenceWeek` para grilla semanal de captura. En esta variante no se exigen límites iguales de todos los conectores. La situación de captura corresponde a KNOWN_RANGE de esa referencia y puede mostrar que ya existe un informe aparte, sin bloquear registrar la captura real. Conservar consulta por periodStart/end para otros rangos; ambos o ninguno y no combinación ambigua con la variante de referencia.

`POST /measurements/parse`: mismo bloque por etiquetas, unidades y respuesta numérica; sin persistir ni fabricar fechas.

`POST /measurements`: añadir referenceWeek para contexto semanal; temporalPrecision KNOWN_RANGE (por defecto del recorrido manual), límites reales obligatorios por conector. El texto pegado/origen se conserva. Ejemplo de captura:

```json
{
  "connectorId": "UUID existente",
  "source": "ENERLINK",
  "referenceWeek": "2026-10-26",
  "periodType": "ROLLING_7D",
  "periodStart": "2026-10-28T08:06:20-05:00",
  "periodEnd": "2026-11-04T08:06:20-05:00",
  "exactConfirmed": false,
  "availableMinutes": 9815,
  "occupiedMinutes": 31,
  "faultMinutes": 0,
  "offlineMinutes": 234,
  "origin": "BLOCK",
  "sourceText": "bloque original"
}
```

La referencia no altera esas horas. Igual identidad/lectura:200; nueva:201; distinta:409 con identidad/version para corrección. Nueva medición queda pendiente. Otro referenceWeek sobre la misma identidad real no la mueve silenciosamente: conflicto de metadata y cambio explícito trazado. Límites parciales/invertidos/futuros:422 con borrador conservado por UI.

`/corrections`, `/classifications`, `/validate`, `/validation`, `/exclude`, `/restore` mantienen permisos, versión y revisión efectiva. Corrección de valores de referencia conserva identidad temporal y exige motivo; no permite crear EXACT_WEEK con referencia sin horario. Clasificaciones especiales sin porcentaje continúan fuera de promedios. La transición FR020 no usa estas correcciones ordinarias para resetear validaciones.

UI: 7d predeterminada; semana/contexto en grilla y campos Desde/Hasta en borrador del conector. `datetime-local step=1`, formato con o sin segundos normalizado antes de -05:00; no agregar :00 sobre un valor ya completo. Guardar y siguiente limpia valores/horas, conserva contexto y foco. Opciones24h/30d conservadas como ventanas separadas; un rango de filtro no se convierte en límite obligatorio del borrador de otro conector.

## 4. Importaciones

Conservar multipart `/imports` con `weekStart` y archivo semanal; weekStart pasa a significar solo referenceWeek. Leer dos hojas de este modelo, conservar hoja/fila/original/crudos; parser identifica horarios presentes o ausentes antes de aplicar cualquier contexto. Para el modelo semanal sin horarios: REFERENCE_WEEK, periodStart/end ausentes, tipo ROLLING_7D, reportedPercent de fuente y Ocupado ausente. No pedir columnas nuevas al usuario.

Mantener la validación de lunes/semana terminada del upload005. No extender esa restricción por defecto a cualquier referencia de captura manual como si fuera período observado. El archivo mensual sigue fuera de alcance; no habilitar el futuro filtro personalizado.

Mantener `/paste`, historial/filas, `/rows/decision`, `/confirm`, `/retry`, `/discard`, `/pending-links` y alias. Pegar tabla con límites reales sigue siendo un recorrido conocido. No sustituir límites de fuente por weekStart. Confirmación exacta sin límites conocidos →422 `UPTIME_REAL_RANGE_REQUIRED`, sin promover filas; ocultar esa acción en UI para REFERENCE_WEEK.

Identidad de referencia: conector/fuente/ROLLING_7D/referenceWeek; comparación de fuente/minutos/porcentaje/crudos igual en preview, confirmación y vínculo posterior. Filas iguales en otro archivo no escriben. Distintas quedan en conflicto y solo producen revisión justificada, no alta nueva. Solicitudes concurrentes usan UNIQUE+versión. Captura real del mismo conector no es esa identidad. Etiquetas desconocidas/desbordadas conservan pendiente con motivo y el lote puede continuar.

### 4.1. Evaluación sin horario real (U1)

La precisión guardada selecciona la política del backend; el cliente no proporciona un número de minutos nominal para suplir límites ausentes. Preview, confirmación, vínculo posterior, corrección directa y validación comparten MeasurementRules, con contrato de modelo3.1.

- KNOWN_RANGE conserva validación de rango real, fórmula y tolerancias005.
- REFERENCE_WEEK conserva validación numérica vigente y porcentaje fuente; no llama a cálculo de duración con null, no compara sumas/ventana con un intervalo supuesto y no rellena Ocupado. Conserva distinción entre error de registro y observación no validable de005.
- `/measurements/{id}/corrections` obtiene precisión/referencia del registro existente: no exige Desde/Hasta para corregir una referencia, no cambia su identidad ni admite EXACT_WEEK. La nueva revisión es PENDING; la anterior validada sigue vigente. La validación posterior usa la misma política sin horario.
- Pruebas: porcentaje97,6800 y estados9815/null/0/234, fechas null; números negativos/desbordados, porcentaje obligatorio ausente, fuera de0–100 o más de cuatro decimales; corrección/validación directas. Conservar crudos y mensajes españoles sin rechazar las demás filas del lote.

## 5. Selección común de consulta

Extender UptimeFilter/UptimeFilterParams con `referenceWeek` y `temporalSection` (KNOWN_RANGE | REFERENCE_WEEK). Las rutas existentes summary/results/report comparten la resolución:

- Semanas OFFICIAL: solo precisión conocida y EXACT_WEEK válido; conservar from/to y reglas005.
- Semanal PROVISIONAL: referenceWeek explícita o última referencia global con incluidos, antes de filtros de atribución. Mantenerla al cambiar red/equipo/conector; si ese filtro no tiene datos, mostrar vacío en esa semana.
- referenceWeek y from/to reales no se mezclan en una selección. Ventana24h/30d y rango conocido conservan from/to y máximo366d. Buscar última referencia no inventa límites ni usa un cutoff arbitrario de366d.
- Sin referencia y sin ningún dato que permita resolverla, `basis=NONE`, bloques nulos y sin atribuir0 para esa selección; no caer a toda la historia. Históricos reales sin referencia siguen disponibles en su consulta real específica.
- En la vista semanal, el bloque conocido exige ROLLING_7D y no mezcla24h/30d ni EXACT_WEEK; esos modos conservan sus consultas separadas.
- En resultados paginados de una referencia se exige temporalSection para no fusionar dos poblaciones. El cliente envía la referencia que recibió del resumen; no recalcular latest por cada página.

`selection`: basis ACTUAL_RANGE | REFERENCE_WEEK | NONE; referenceWeek nullable, tipo, zona y criterios reales cuando se usen. `resolvedPeriod` conserva compatibilidad conceptual; límites null si no hay horario real, no epoch/lunes ficticio.

## 6. Resumen y resultados

Mantener `official` y `provisional` (este último corresponde a conocidos) con sus cálculos. Añadir `referenceReport`, `provisionalComposition`, `provisionalCoverage` y selección explícita. La cobertura antigua de cabecera se usa solo para sección oficial/conocida identificada, no como total combinado; puede ser nula en vista exclusivamente de referencia. La UI no convierte nula en0 de uptime.

`referenceReport` contiene: referenceWeek, aggregationMethod=REPORT_MEAN, uptimePercent nullable, equipmentEvaluated/equipmentWithoutData, connectorsIncluded/connectorsInScope, coverage y composition. No llenar evaluatedMinutes con0/10080; no hay minutos evaluados conocidos en esa sección. Porcentaje0 válido participa. KPI de conocidos identificado como WEIGHTED_MINUTES, sin nuevo promedio de ambos bloques.

Resultados por conector/equipo: metadata temporal, referencia, método, porcentaje y duraciones conocidas; nulos donde no se informaron. Ranking completo por apartado, empates a dos decimales y orden por nombre. Al elegir última captura conocida de una referencia: fin descendente, inicio descendente como desempate estable, sin fusionar identidades. Resultado de equipo en referencia: media de sus porcentajes incluidos rotulada como promedio del informe, no global del equipo.

Cobertura por apartado: conector en alcance, estados/denominador existentes y pendientes de conciliación debidamente atribuidos. REQUIRES_RECONCILIATION con referencia/apartado probados se cuenta en byState.pendingReview, no validada ni sin medición; agrupar colisiones por conector para no duplicar unidades. Si solo la referencia es cierta, publicar una cantidad separada temporalReconciliationCount de mediciones pendientes de clasificar, sin atribuirlas a ninguno de los bloques; no es el conteo pendingLink. Sin referencia cierta, el caso solo aparece en la cola operativa global. No exponer detalles/motivos a lectores. Un conector en dos apartados no autoriza sumar incluidos como KPI general. Operaciones se calculan sobre hechos seleccionados y solo se entregan a operativos. `pendingLink` usa la referencia/tipo/apartado solicitado y no filtros de atribución; nunca un OR period_end IS NULL para contar toda la historia. Casos sin referencia acreditada no se imputan a cada semana.

## 7. Composición de estados

`composition`: availableMinutes, occupiedMinutes, faultMinutes, offlineMinutes como long nullable; totalKnownMinutes, selectedMeasurements, completeMeasurements, knownMeasurementsByState y isComplete. Se obtiene de hechos incluidos del apartado completo, no de primera página/Top/ranking ni de evaluated-offline-fault.

Cada agregado de estado es null si nadie informó,0 si hubo valores explícitos cero, parcial si solo parte de mediciones aporta. Ejemplo: 2 seleccionadas,1 informó Ocupado=0 → occupiedMinutes0 con cobertura1/2, no cobertura completa. Mostrar sumas conocidas y ese denominador.

Detalle usa la misma revisión que el resto de campos según el rol; no consulta historial restringido para construir el gráfico. Dashboard usa incluidas/validadas. Orden fijo de leyenda, color fijo por estado. Parcial: Distribución de tiempos informados; porciones solo del total conocido. Todos nulos o total conocido0: leyenda y mensaje, sin círculo. No presentar la cuota como porcentaje de168h o de uptime.

## 8. Transición y conciliación (operativo)

Reutilizar página Revisión y patrón ImportJobs; sin módulo/ruta UI nueva. Cola adicional `TEMPORAL_RECONCILIATION` en `/measurements` solo operativa; devuelve motivos y metadata de evidencia permitida, no credenciales/original_ref.

Contrato propuesto para acciones dentro de UptimeMeasurementController:

- `POST /measurements/temporal-transition`: crea un trabajo persistente para items `{id,version}`, según 8.1; ejecución acotada/reintentable con resultados APPLIED/VERIFIED_KNOWN_RANGE/REQUIRES_RECONCILIATION/ALREADY_APPLIED/STALE_VERSION. El máximo500 por trabajo sigue como tamaño técnico de bloque actual, no límite funcional del total. El diagnóstico y la aplicación solo automáticos inequívocos; no pide validar valores otra vez.
- `POST /measurements/{id}/temporal-classification`: versión, precisión destino, referencia o límites realmente respaldados, motivo y evidencia. Solo la persona autorizada puede resolver lo ambiguo. No aceptar un rango de semana de referencia como prueba. Si compite con otra identidad canónica →409; no merge/last-wins. La exclusión existente permite decisiones explícitas sobre registros que no deban computar, conservándolos y su historia.
- Historial operativo incorpora eventos temporales con antes/después, revisiones afectadas, motivo, actor/proceso, instante y resultado; no modifica las revisiones de valores. Eventos de sistema se identifican como sistema, nunca se inventa un analista aprobador.

Procesar FILE/MANUAL/BLOCK de toda la cadena. Original fuera de transacción, checksum/hoja/fila verificados; confirmar versión al escribir. Inaccesible/ambiguo →conciliación, no error que clasifique automáticamente ni pérdida de trazabilidad. EXACT_WEEK con horario asignado pasa a efectivo ROLLING_7D/referencia y sale de oficiales nuevos; no cambia el documento antiguo. Una cadena con captura real verificada no se reclasifica por una condición FILE aislada.

### 8.1. Solicitud, consulta y reintento del trabajo (U2)

Mismo UptimeMeasurementController, permisos operativos en backend y página Revisión; sin módulo/ruta UI nueva. La persistencia y atomicidad están en data-model4.1, separadas del ejecutor ImportJobs.

| Operación | Petición | Respuesta |
|---|---|---|
| POST `/measurements/temporal-transition` | Header `Idempotency-Key` UUID, body `{items:[{id,version}]}` no vacío, sin id repetido, máximo500 por bloque | 202 para trabajo nuevo/en curso; snapshot con `workId,version,status,policyVersion,total,processed,counts,attempt`. Si la misma clave del actor y contenido ya terminó/falló/interrumpió,200 con el mismo trabajo, sin reiniciarlo |
| GET `/measurements/temporal-transition/{workId}` | UUID del trabajo | 200 con metadata/corte de lectura, código seguro de fallo si existe y resultados paginados mediante `page/size` del patrón PageQuery actual; cada resultado identifica medición/versión origen/resultado/eventId cuando exista |
| POST `/measurements/temporal-transition/{workId}/retry` | `VersionRequest {version}` y CSRF | 202 con snapshot del mismo trabajo reencolado; solo desde FAILED/INTERRUPTED, conserva solicitud/resultados/versiones origen;409 si versión o estado no permite reintentar |

Misma clave/actor con items o versiones distintos→409; clave inválida/lista vacía/duplicada o bloque excesivo→422. Orden distinto de los mismos items no crea otro trabajo. La respuesta nunca expone credenciales, original_ref, excepciones ni valores no autorizados; solo ADMINISTRADOR/ANALISTA, incluida unión, pueden crear/consultar/reintentar. Un operador autorizado puede reintentar un trabajo iniciado por otro; se conservan solicitante y actor del intento. Leer estados no aplica decisiones ni cambia datos.

Estados QUEUED/RUNNING/COMPLETED/FAILED/INTERRUPTED. Counts se derivan de results persistidos; processed equivale a su cantidad, nunca a una estimación del hilo. COMPLETED significa todos los items con resultado, pudiendo incluir conciliaciones/STALE_VERSION. QUEUED/RUNNING al reiniciar pasan a INTERRUPTED; retry explícito procesa solo items sin resultado con las versiones originales. El registro del trabajo existe incluso si el proceso cae antes de ejecutar el primer item.

Cola llena: ImportJobs.onRejected guarda FAILED/UPTIME_TRANSITION_QUEUE_FULL; el POST devuelve el trabajo durable (con la semántica de aceptación anterior), no un éxito de aplicación de mediciones. La página consulta y muestra el fallo/reintento; no espera un Runnable perdido. Una indisponibilidad para aceptar/consultar el propio trabajo usa503/ProblemDetail sin secretos y permite repetir la misma clave. No confundirlo con original inaccesible, cuyo resultado funcional es REQUIRES_RECONCILIATION.

La fachada de Revisión conserva workId/versión, consulta progreso paginado y detiene polling al estado terminal/fallo o al salir; recupera trabajos del contexto conservado sin mantener un proceso en memoria como única fuente. La cola de mediciones sin resolver sigue siendo reconstruible desde persistencia si el navegador pierde ese contexto; una nueva solicitud no repite decisiones terminales gracias a los eventos/idempotencia. No nueva pantalla de administración de trabajos.

## 9. Errores y armonía visual

ProblemDetail español con código/trace existente, 401/403/404 coherentes, 409 versión/identidad/metadata, 422 forma o regla y503 ante proveedor temporalmente no disponible sin exponer URLs/secretos. Borrador/formulario conservado ante error. Fallo automático de refresh mantiene datos previos y aviso/corte, sin presentarlos como actualizados.

Mismas cabeceras, chips/filtros, paneles, tablas/cards y diálogos Material. states-chart local usa shared ChartView y QuantityFormat; una cuarta serie en tokens del DS sin alterar los tres colores actuales. Leyenda/ARIA/texto accesibles, foco/teclado, temas, movimiento reducido y 5anchos. Sin paleta o layout de Enerlink ni rediseño genérico.

No se implementa snapshot histórico entre solicitudes: summary compone KPIs/dona/cobertura de un mismo snapshot y fija referencia para las páginas; cada exportación genera y publica su corte. Cotejo de igualdad con mismos datos/filtros/corte se hace en un snapshot común/fixture sin escrituras entre consultas, no comparando dos instantes distintos como si fueran uno.
