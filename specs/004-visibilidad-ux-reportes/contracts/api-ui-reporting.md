# Contratos API, interfaz y documentos — Feature 004

Diseño para implementación posterior. Base `/api/v1`; sesión JDBC/CSRF y contratos de escritura vigentes. La audiencia se resuelve en servidor: no es un filtro ni un rol nuevo. Códigos reales: ADMINISTRADOR, ANALISTA, GERENTE, SUPERVISOR, ASISTENTE.

## 1. Matriz de lectura

| Capacidad | Admin o Analista, solos/combinados | Solo Gerente/Supervisor/Asistente, solos/combinados |
|---|---|---|
| Indicadores/gráficos oficiales | Sí | Sí |
| Cinco categorías operativas y detalle | Sí | No |
| Sesiones | Alcance actual con filtros | Solo VALIDATED dentro de filtros |
| Evidencia energética | Historial actual completo | Solo APPROVED vigente de sesión actualmente oficial |
| Original de consumo WITH_OPERATIONS/legado | Sí, si existe copia | No; explicación/nueva emisión |
| Original OFFICIAL_ONLY | Sí | Sí |
| Edición/importación | Permisos actuales | Permisos actuales, sin ampliación |

## 2. GET /energy/summary

Conservar from/to/network/type/source/equipmentId, rangos y tipos actuales. Conservar official, averageKwhPerSession, top, equipmentWithoutEnergy, daily, byNetwork, bySource, byType, approvedAboveThreshold y generatedAt.

Las propiedades `pendingLink`, `awaitingApproval`, `test`, `energyReview`, `energyRejected`:

- Operador: presentes todas, aunque sus cantidades sean cero.
- Gerencial: ausentes en JSON, no cero ni un resumen indirecto. Documentarlas opcionales según audiencia en OpenAPI y tipos regenerados.

No omitir datos oficiales por superar umbral una vez aprobados. Un cambio posterior al corte no cambia un archivo emitido. No aceptar un corte arbitrario como promesa de reconstrucción histórica.

## 3. GET /energy/sessions y /energy/sessions/{id}/energy-reviews

Conservar parámetros de búsqueda actuales, orden y paginación. Gerencial sin estado o con VALIDATED: intersectar filtros con VALIDATED antes de consultar/contar. Nunca devolver totales de operativas ni filtrar una página ya cargada.

Gerencial con state distinto de VALIDATED, o energyReview PENDING/REJECTED explícito: 403 `CONSUMPTION_OPERATIONAL_ACCESS_DENIED`, mensaje español. Sin filtro energyReview, o con APPROVED, solo dentro de oficiales. El enum actual no tiene NONE: la ausencia de condición energética es null, no un valor nuevo de filtro. Operadores conservan todos los filtros. Enum/formato inválido mantiene 400 existente, no cambia a una consulta por defecto.

Historial: operadores conservan lista completa y sus controles; gerencial con sesión oficial aprobada recibe solo la aprobación vigente/justificación correspondiente a la lectura actual. Sesión oficial sin aprobación excepcional devuelve `[]`. Sesión no oficial o inexistente: 404 sin revelar datos/revisiones. No incluir una decisión anterior rechazada ni decisiones de otra lectura.

Angular mantiene consulta de sesiones oficiales para lectores y no presenta filtros/acciones restringidos. Etiquetar evidencia gerencial como justificación aprobada, sin prometer historial completo. Las APIs de decisión, test, vínculo, aprobación de fuente e importación conservan permisos/versiones actuales.

## 4. GET /energy/report

Mismos parámetros/formatos PDF/XLSX. La audiencia viene del usuario actual. Modelo autorizado antes del renderer: ninguna hoja/anexo/caché de gráfica contiene información restringida para el receptor.

Respuesta: binario como hoy y Content-Disposition; añadir `X-Report-Id` y `X-Report-Cut-Off` del registro nuevo/corte del documento. Cache-Control privado sin almacenamiento. Mantener nombre/formato y tratamiento de errores. Cada llamada autorizada de emisión genera otro UUID, incluso si período/filtros coinciden con un original.

Conservar comportamiento de archivo existente: sin almacenamiento configurado puede entregarse el informe recién generado y registrarse su huella con archived=false; el listado no promete una copia que no existe. Si el proveedor configurado falla al archivar, conservar el error actual, sin simular éxito. Esto se distingue de descargar un original, que requiere una copia existente y almacenamiento disponible.

Tope100.000 filas autorizadas; máximo366 días. `REPORT_TOO_LARGE` conserva422 y texto que pide acortar/aplicar filtros, sin revelar conteos operativos a gerenciales. El límite no invalida ni elimina datos. No aplicar formato distinto para eludir audiencia.

## 5. GET /reports/issued

Conservar filtros kind/page/size y campos actuales permitidos del listado. Añadir para cada elemento:

| Campo | Semántica |
|---|---|
| canDownload | Calculado por copia disponible y autorización actual |
| accessReason | Código/motivo neutro cuando no puede descargarse; no cantidades internas |
| canIssueManagerial | Permite presentar acción de nueva emisión de consumo |
| cutOff | Corte persistido para nuevas emisiones; opcional/desconocido en legado, no createdAt inventado |
| criteria | Filtros estructurados autorizados, si existen; opcional en legado |

Conservar diferencia entre fecha de emisión y corte. No exponer fileRef, URL firmada ni contenido operativo en metadatos. Los elementos restringidos pueden mostrar identificación, formato, alcance legible y motivo necesario para explicar la denegación; no un desglose restringido.

## 6. GET /reports/issued/{id}/download

Cambio coordinado: deja de devolver `{url}` y devuelve el documento binario autenticado. Aplica a transporte compartido de emitidos; inventario conserva su autorización/contenido. No hay redirect a Cloudinary ni enlace externo en payload. Angular consume blob/headers y dispone de un enlace local temporal, liberado al terminar.

1. Comprobar sesión/usuario y recuperar metadatos.
2. Autorizar por kind/perfil y roles vigentes antes de leer storage.
3. Leer documento privado mediante adaptador servidor, fuera de transacción de consulta prolongada.
4. Entregar Content-Type correcto, Content-Disposition seguro y `Cache-Control: private, no-store`.

Sin sesión→401; acceso operativo restringido→403; reporte/copia inexistente→404; almacenamiento no configurado/caído→503. No incluir una URL del proveedor dentro del error. Pruebas negativas verifican que no se llamó al puerto de lectura cuando se denegó.

El contrato no puede retirar bytes ya descargados. URLs externas emitidas por la versión antigua deben expirar antes de declarar cierre del despliegue; nuevas entregas ya no las exponen.

## 7. Errores de autorización y nueva emisión

Usar `application/problem+json`, español y traceId conforme al manejo global. La denegación de original incluye:

- status403, code `REPORT_OPERATIONAL_CONTENT_RESTRICTED`.
- detail: «Este informe contiene información operativa restringida para tus roles. Puedes emitir un nuevo informe gerencial con su propia fecha de corte».
- Acción declarativa `ISSUE_MANAGERIAL_REPORT`, sin URL externa, filas ni cantidades restringidas.

El manejador actual de BusinessRuleException produce422; no usarlo para esta denegación. Añadir excepción de acceso específica compatible con AccessDeniedException y traducción centralizada que preserve403/código/acción, sin cambiar errores de otros módulos.

Flujo UI: explicar original bloqueado → abrir formulario de período/filtros → precargar solo criterios fiables si disponibles → confirmar alcance → generar mediante /energy/report → descargar nuevo documento/actualizar listado. Mostrar su ID/corte propio; nunca descargar silenciosamente un reemplazo bajo el ID/corte original. Con legado, confirmar manualmente sin inferencias de scope.

Fronteras Angular: managerial-report-dialog/issued-reports-page → IssuedReportsFacade → IssuedReportsApi.generateConsumptionReport → GET /energy/report. El acceso HTTP y construcción de parámetros pertenecen a data-access de Reportes; la fachada coordina estado, errores, descarga y refresco. La vista no inyecta HttpClient ni ConsumptionApi. Reportes no importa el interior de Consumo en ninguna capa: utiliza tipos de core/api/generated o su propio dominio y mantiene las reglas ESLint existentes. No se crea otro endpoint ni se duplican cálculos de consumo.

Errores binarios pueden llegar como Blob de ProblemDetail a Angular: adaptar la traducción de descarga para conservar el mensaje/acción y no mostrar únicamente «error desconocido».

## 8. Contrato de gráficos PDF/Excel

| Representación | Datos autorizados existentes | Tratamiento |
|---|---|---|
| Energía diaria | daily.energyKwh, fecha y sesiones sin lectura | Línea con huecos para ausencia, cero real conservado |
| Sesiones diarias | daily.sessions | Línea de sesiones iniciadas |
| Top5 con empates | summary.top y position | Barras; todos los empatados y nota; tabla completa se conserva |
| Energía por red | byNetwork | Barras/unidades, sin porcentajes inventados |
| Energía por plataforma | bySource | Mismo criterio |
| Energía por tipo | byType | Mismo criterio |

Mismo conjunto oficial, filtros, rango, zona y corte que las tablas. Mantener precisión/normalización del backend. Etiquetas/nombres accesibles, no solo color/tooltips; segmentación legible en páginas conserva todos los datos.

Excel nativo o imagen: datos/cachés/rangos locales solo autorizados, sin vínculos externos ni datos ocultos operativos. Fórmulas existentes conservadas; no se exige crear nuevas. Ninguna lectura nula se convierte en0. Evaluación verificable y apertura en Excel de escritorio Windows con versión registrada; excepción tabular requiere limitación reproducible, impacto y alternativas probadas.

La evaluación selecciona una ruta de entrega, sin selector nuevo de producto:

| Ruta técnica | Condición | Comprobación específica |
|---|---|---|
| NATIVO | XDDF satisface los seis criterios de FR-020 | Gráficos, rangos y cachés autorizados, blancos/0, legibilidad y paridad con tablas |
| IMAGEN | Nativos incumplen un criterio reproducible y la alternativa de imagen satisface los seis | Imágenes legibles y fieles a las tablas, sin exigir charts/cachés nativos |
| TABULAR_CON_EXCEPCION | Ambas alternativas tienen limitaciones demostradas no resolubles convenientemente dentro del alcance | Datos/fórmulas autorizados íntegros, sin exigir gráficos ni dejar soportes de soluciones descartadas; evaluación documentada |

Integridad, permisos, valores oficiales, ausencia frente a0, tipos/columnas/fórmulas y apertura real sin reparación en Excel Windows son obligatorios en las tres rutas. Los casos de prueba se condicionan a la ruta elegida: una excepción tabular válida no falla por carecer de gráficos/rangos/cachés nativos. No implementar rutas descartadas para satisfacer una prueba. Si la apertura real cambia el dictamen preliminar, actualizar selección/pruebas y repetir apertura; no aceptar una ruta que falle sus criterios ni usar falta de acceso como prueba de inviabilidad.

## 9. Archivos y comprobación de contrato

Actualizar `sarc-bakend/docs/api/openapi.json`, respuestas/controllers y `OpenApiContractTest`; ejecutar `npm run api:types` en frontend. Ajustar `ConsumptionApi` únicamente dentro de Consumo y `IssuedReportsApi`/facade/página dentro de Reportes, sin importar servicios privados entre features. No editar tipos generados a mano. Probar ambos clientes de descargas (consumo inmediato y emitidos, incluido inventario), la reemisión a través de la fachada de Reportes y lint sin excepciones nuevas.
