# Contrato API y UI — Feature-005

Prefijo /api/v1/uptime, sesión/CSRF/ProblemDetail actuales. OpenAPI generado en sarc-bakend/docs/api/openapi.json es el contrato ejecutable de esta entrega; tipos Angular se derivan de él. No contiene la semántica nueva de referencia sin horario ni los gráficos de 006.

## Lectura

GET /summary, /results/connectors, /results/equipment, /results/exclusions, /measurements y /measurements/{id}; GET /measurements/{id}/revisions solo operativo. GET /scope/connectors y /report?format=PDF|XLSX.

Filtros comunes: periodKind OFFICIAL/PROVISIONAL, periodType, from/to fechas con to exclusivo, network/type/brand/equipmentId/connectorId. Máximo 366 días; oficial alineado lunes y semanas cerradas; provisional tipo requerido cuando se solicita explícitamente. Apertura oficial más reciente o provisional preferida de 7 días. Resultados paginados sin cortar empates del ranking.

Summary: resolvedPeriod (tipo/límites/zona), official/provisional nullable, coverage con estados y pendingLink, tendencia oficial, notices, lastValidatedAt/cutOff y operations solo operativo. Nulo no equivale a 0. El total sin atribuir corresponde al rango solicitado; sin rango se resuelve independientemente de los filtros de atribución. Un rango vacío no cuenta toda la historia.

## Escritura y captura

GET /grid por intervalos; POST /measurements/parse interpreta las cuatro etiquetas sin guardar; POST /measurements crea/identifica idéntica; /classifications crea marcas especiales. POST /measurements/{id}/corrections, /validate, /exclude, /restore y /measurements/validation por lote con versión y justificación donde corresponda.

Misma identidad: 200 idéntica sin escribir; 409 diferente o versión obsoleta; 422 errores de captura y reglas; 201 nueva. Todo queda pendiente hasta validación. La corrección conserva vigente anterior; exacta solo con alineación/confirmación. Grilla y formulario actuales emplean un período de captura compartido; sus ajustes por conector se planifican en 006.

## Importación y vinculación

POST /imports multipart con weekStart lunes ya terminado y archivo semanal de dos hojas; POST /imports/paste con tipo móvil declarado y encabezados. GET lotes/filas, PUT rows/decision y POST confirm/retry/discard. Pendientes GET /pending-links, POST /pending-links/{rowId}/link; alias GET/PUT /connector-aliases.

En 005 weekStart asigna período declarado del informe. Se conserva procedencia, hoja/fila y horas crudas; falta de Ocupado usa REPORTED_PERCENT. Eso no verifica el intervalo real consultado en Enerlink. Número inicial fuera de rango se clasifica pendiente con motivo; no falla el lote entero. Identidad desconocida/ambigua no se atribuye arbitrariamente. Propuesta de conector requiere confirmación humana.

## Roles y seguridad

ADMINISTRADOR/ANALISTA escriben y ven historial operativo; cinco roles consultan/descargan. Unión de roles. El backend protege accesos directos y proyecta solo valores vigentes permitidos, sin motivos/versiones/valores pendientes a consulta. Ningún DTO expone UID/PUK, URL OCPP, firmas o referencias privadas. Backend 401/403/404 coherentes y ProblemDetail en español.

## Interfaz

Mismas rutas /uptime/tablero, captura, revision, mediciones/:id, importaciones, importaciones/:id y vinculacion. Navegación y presentación Material/Signals, temas, responsive y DESIGN existente. HTTP solo data-access y facades para operaciones. Esta entrega no añade pastel ni otra biblioteca.
