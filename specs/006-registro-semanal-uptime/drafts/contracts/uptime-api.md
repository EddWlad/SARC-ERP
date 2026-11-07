# Contrato: ajuste incremental de API/UI uptime

**Diseño 2026-11-04.** Prefijo existente `/api/v1/uptime`, sesión/CSRF/ProblemDetail vigentes. No nuevos módulos ni endpoints paralelos. Los nombres de campos nuevos aquí propuestos deben cerrarse en OpenAPI y tipos generados al implementar. El contrato anterior de límites siempre presentes se sustituye por nulabilidad explícita.

## Autorización conservada

Consulta: ADMINISTRADOR, ANALISTA, GERENTE, SUPERVISOR, ASISTENTE. Escritura, importación, grilla y revisiones: ADMINISTRADOR o ANALISTA; unión de roles. Ninguna respuesta nueva añade UID/PUK, OCPP, referencias privadas o valores de revisiones pendientes a roles de consulta. Las porciones y cobertura del dashboard usan solo datos incluidos. En detalle se aplica la misma revisión visible que a los demás valores.

## Metadata temporal común

- `periodPrecision`: KNOWN_RANGE | REFERENCE_WEEK | LEGACY_DECLARED.
- `referenceWeek`: fecha lunes o null, identificada como referencia del informe.
- `periodStart`, `periodEnd`: límites realmente conocidos; null en REFERENCE_WEEK, con zona explícita.
- Los valores originales de fecha heredada solo se presentan como declarados, no observados; el detalle operativo y auditoría conservan procedencia. No devolver como rango real un límite legado cuya procedencia sea asignación semanal.
- PeriodType y UptimeBasis se mantienen. Referencia sin horario siempre ROLLING_7D, nunca EXACT_WEEK.

La nueva nulabilidad requiere desplegar backend/frontend coordinados. DTOs `record` con mapeos existentes; no deserializar respuestas como Date sin comprobar precisión/nulos.

## Captura

- `GET /grid`: admitir referencia semanal para el flujo nuevo sin exigir un único rango observado de todos los conectores. Mantener consulta real antigua cuando se proporcionen ambos límites. La situación por referencia resume registros de esa referencia e identifica el modo; no reutilizar findByPeriod con fechas inventadas. Si hay varias capturas reales, mostrar última incluida/pendiente y enlace a consulta, sin ocultar históricos.
- `POST /measurements/parse`: sin cambios de etiquetas, unidades ni persistencia; conserva bloque original y errores claros. La respuesta no inventa fechas del bloque.
- `POST /measurements`: KNOWN_RANGE, referenceWeek para el nuevo contexto semanal y límites propios por conector con segundos. La semana no modifica esos instantes. 201 creada, 200 idéntica, 409 lectura distinta; validación backend existente.
- `POST /measurements/classifications`, correcciones/validate/validation/exclude/restore: mismos permisos/versiones y semántica. Corrección de referencia conserva su identidad/referenceWeek. EXACT_WEEK exige KNOWN_RANGE, alineación y confirmación verificable, nunca solo una casilla en un Excel sin horario.

Grilla/captura: 7 días por defecto, opciones 24 h y 30 d disponibles. Semana del informe en la grilla; Desde/Hasta reales dentro del borrador de cada conector. Guardar y siguiente no arrastra ni minutos ni límites. No derivar horas de la semana.

## Importación

Conservar `POST /imports` multipart, `/paste`, historial, filas, decisiones, confirmación, retry y discard.

Para el Excel semanal actual, `weekStart` significa semana de referencia; UI lo rotula «Semana de referencia del informe». No se convierte en período real. `exactConfirmed=true` sin límites reales se rechaza con 422 claro, por ejemplo `UPTIME_REAL_RANGE_REQUIRED`, y no escribe ni promueve filas. La UI no ofrece esa acción para esos lotes.

Lote y fila exponen periodPrecision/referenceWeek y límites nullable, hoja/fila, clasificaciones/observaciones/outcome como hoy. Se conservan originales y horas de fuente; Ocupado ausente sigue null. Las dos hojas se leen en orden por excepción D09; otras importaciones no cambian.

Identidad sin horario: conector+fuente+ROLLING_7D+referenceWeek. Igual lectura en otro archivo: IDENTICAL. Distinta: CONFLICT y aprobación justificada como corrección, sin sobrescribir vigente. Un archivo reordenado no altera la identidad. El vínculo posterior usa exactamente la misma comparación.

Legacy del mismo conector/referencia: conflicto explícito `UPTIME_LEGACY_RECONCILIATION_REQUIRED` (409 o issue de fila según operación), sin duplicar ni inferir la equivalencia. Etiqueta numérica fuera de rango: PENDING_LINK con motivo, no fallo global.

## Consulta y dashboard

Conservar `/summary`, `/results/connectors`, `/results/equipment`, `/results/exclusions`, `/measurements`, detalle e historial. Añadir `referenceWeek` y selección de apartado (`temporalScope=KNOWN_RANGE|REFERENCE_WEEK`) a los filtros compartidos, sin duplicar rutas. El backend resuelve el período antes de paginar.

- Oficial exacto: cálculo, reglas y prioridad existentes.
- Vista semanal provisional por referencia: última semana con datos validados e incluidos; dos apartados independientes. Consulta de referencia concreta permite ver semana sin datos; no caer en pendientes de toda la historia.
- `KNOWN_RANGE`: cálculo ponderado y límites reales existentes, últimas incluidas por conector en la referencia seleccionada.
- `REFERENCE_WEEK`: Promedio del informe, media de porcentajes incluidos y cobertura de esa referencia, sin peso real.
- 24 h/30 d, rango real y registros previos sin referencia conservan su consulta específica. No asignar referencia retroactiva por proximidad de fechas.
- Nunca un KPI conjunto de ambos apartados. Mismo conector en ambos no se cuenta como dos conectores de un total general.

La respuesta de resumen conserva campos oficiales/real existentes y añade apartado `referenceReport` con `referenceWeek`, `aggregationMethod: REPORT_MEAN`, `uptimePercent` decimal texto nullable, cobertura, composición y cantidades. Su `evaluatedMinutes` es null/no informado, no 0/10080. El apartado de capturas se etiqueta WEIGHTED_MINUTES; no forzar ambos modelos a primitivas que inventen valores.

Cada resultado por conector incluye la precisión, referencia, límites reales nullable, método y porcentaje; ranking por apartado. Empates comparten posición sobre dos decimales, sin cortar conectores. El promedio por equipo del informe es de porcentajes de sus conectores incluidos, rotulado como promedio del informe.

`coverage.pendingLink` se cuenta por el período/referencia solicitado y apartado pertinente, nunca por límites min/max de las filas visibles; no cambia por red/equipo/conector. Cobertura de datos temporales y completitud de estados son dimensiones distintas, ambas se rotulan.

## Composición de estados

Backend proyecta `states`: cuatro minutos nullable, `totalKnownMinutes`, `selectedMeasurements`, `completeMeasurements`, `knownMeasurementsByState`, `isComplete`. Solo suma valores realmente informados de las mediciones que alimentan ese apartado y corte. El detalle usa la revisión visible conforme al rol; el dashboard solo validadas/incluidas.

Angular adapta los números al wrapper ChartView. Leyenda siempre de cuatro estados, con 0 distinto de «No informado». Completa: «Estados del conector»/«Estados de las mediciones». Parcial: «Distribución de tiempos informados» y cobertura explícita; las porciones no son porcentajes de las 168 horas ni del uptime. Sin total conocido positivo no se dibuja pastel. No interpolar faltantes ni usar el porcentaje de uptime para completar duraciones.

## Diseño y accesibilidad

Mismas rutas, Material, tipografía, tokens, diálogos, grids, tablas/cards, temas y foco. Componente local states-chart, sin importar un componente privado de Consumo. Ampliar solo la cuarta serie en el sistema existente; leyenda y alternativa textual para no depender del color. Matriz de 5 anchos × 2 temas. No importar HttpClient en presentación ni escribir tipos generados a mano.

## Errores y comprobaciones

Mantener ProblemDetail/códigos/versiones actuales. Datos no interpretables: 422; conflicto identidad/versiones: 409; autorización: 401/403; consultas no visibles: mismo 404 que hoy. Mensajes claros en español. Revalidar alcance y versión al confirmar. Fechas parciales inválidas; referencia no lunes o futura/semana no cerrada inválida; REFERENCE_WEEK no admite límites reales en el mismo contrato.

Regenerar OpenAPI con su test solo tras cambios intencionales y `npm run api:types` después. Probar API con cinco roles, rol combinado y acceso directo, incluyendo resultados/gráficas/reportes y ausencia de campos restringidos.
