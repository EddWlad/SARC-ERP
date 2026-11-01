# Implementation Plan: Visibilidad, UX/UI y reportes gerenciales

**Branch**: `main` (Git real). Selector Spec Kit: `004-visibilidad-ux-reportes`. No se crea ni cambia de rama en este plan.
**Date**: 2026-10-31 | **Spec**: [spec.md](spec.md) | **Baseline**: `7354d6e`, 003 integrada sobre 002.
**Estado**: diseño técnico, sin implementación, pruebas de producto, servicios ni migraciones ejecutados. El esquema estudiado es V001–V010 y JPA, no un catálogo dev vivo. Prevalecen las dos aclaraciones aprobadas de 004.

## Summary

Aplicar una política de lectura común para separar información oficial y operativa en API, sesiones, PDF/Excel y archivo de reportes. Conservar cálculo, estados, revisión energética, idempotencia e históricos de 002/003. Entregar documentos desde una proyección autorizada, sin datos restringidos ocultos.

Reutilizar OpenPDF/POI para seis gráficos; confirmar defectos visuales en navegador y corregirlos mediante APIs públicas de Material y patrones compartidos. Una V011 aditiva conserva perfil de contenido, corte y filtros de nuevas emisiones. Los originales anteriores permanecen intactos; el acceso restringido ofrece emitir otro informe con filtros confirmados, UUID y corte propios.

## Estado de los criterios de aceptación

Lectura de código; no certificación de ejecución. AC corresponde a los diez criterios del pedido.

| AC | Estado | Trabajo y evidencia |
|---|---|---|
| 1: operadores ven cinco categorías | Ya cumplido como base | dashboard-page.html:88–132; conservar las cinco incluso en cero y probar multirol. |
| 2: gerenciales sin datos operativos | Requiere corrección | DTO, sesiones, renderers y descarga no seleccionan audiencia. Proteger backend completo. |
| 3: indicadores oficiales | Ya cumplido como base | ConsumptionCalculator usa VALIDATED; preservar valores y verificar instantánea de resumen bajo concurrencia. |
| 4: nuevo conector sin superposición | Requiere corrección | Ayuda multilínea + subscript fijo + gap8: causa probable, pendiente reproducción renderizada. |
| 5/6: auditoría responsive trazable | Nuevo | Hay breakpoints, no matriz completa verificable de pantallas/estados × diez combinaciones. |
| 7: seis gráficos PDF | Nuevo | PDF actual tiene tablas/KPI, no gráficos. |
| 8: Excel íntegro con gráficos viables | Parcial/nuevo | Detalle SXSSF existente; faltan gráficos y prueba real en Excel Windows, con versión. |
| 9: reglas e históricos intactos | Ya cumplido como base | Conservar regresiones001–003 y archivo/hash; cambiar actor de pruebas operativas, no sus reglas. |
| 10: rendimiento/accesibilidad/dependencias | Parcial | Verificar AA en navegador y límite500kB explícito: el build solo falla a1MB. |

## Technical Context

**Language/Version**: Java25, Spring Boot4.1.1; Angular22, TypeScript~6.0.2, Material/CDK22.2.4 declarados.
**Primary Dependencies**: Spring MVC/Security, JPA/Hibernate, Jakarta Validation, Flyway; OpenPDF3.0.5, POI6.0.0; Signals, Signal Forms, Sass, Chart.js^4.6.1.
**Discrepancias resueltas**: adjunto/TRD menciona ECharts y MapStruct; código usa Chart.js y mapeos explícitos from/toCommand. Conservarlos. Tampoco sustituir ImportJobs por Spring Batch en004.
**Storage**: PostgreSQL18; V011 nueva para metadatos mínimos de report_issued; proveedor privado existente detrás de FileStorage.
**Testing**: JUnit, MockMvc, Testcontainers PostgreSQL18, ArchUnit/OpenAPI; Angular/Vitest, lint/build; navegador, Impeccable y QA manual. Herramientas QA adicionales, si necesarias, solo desarrollo y justificadas.
**Target Platform**: SPA web; cinco roles actuales; Excel de escritorio para Windows como aceptación XLSX.
**Project Type**: monolito modular api→application→domain←infrastructure y frontend por funcionalidades.
**Performance Goals**: TRD p.2/19: consultas usuales p95≤2s y dashboard p95≤5s,30 usuarios,250.000 registros/mes como carga objetivo. Comparar antes/después con entorno y datos idénticos. No son resultados demostrados.
**Constraints**: carga inicial≤500kB; máximos existentes366 días y100.000 sesiones/reporte; render fuera de transacción. Medir tiempo/memoria de emisión: no existe SLA numérico de exportación en TRD, no inventarlo ni confundirlo con consulta interactiva.
**Scale/Scope**: pantallas existentes de acceso, Inicio, Usuarios, Inventario, Consumo, Reportes y diálogos. Nuevos gráficos solo consumo; sin RF004–006, rediseño general ni segmentación por red.

## Constitution Check

PASS significa conformidad del diseño, no implementación probada. Evaluación antes de investigación y después del diseño.

| Regla | Control | Antes | Después |
|---|---|---|---|
| I: especificación primero | Spec004 aclarada, tasks/analyze antes de implementar | PASS | PASS |
| II: reglas explícitas | Roles, categorías, originales y Excel decididos en spec | PASS | PASS |
| III: migraciones inmutables | Solo V011; no editar V001–V010 | PASS | PASS |
| D01–D09 | Cuentas, identificadores, conectores, reemplazos, horarios y hojas importadas intactos | PASS | PASS |
| D10 | Revisión/aprobación>1.000 y justificación vigente oficial conservadas | PASS | PASS |
| D11 | HALF_UP a3 decimales por lectura; gráficos no recalculan | PASS | PASS |
| D12 | Nombres actuales en nuevos informes; originales inmutables | PASS | PASS |
| D13 | Mes corriente, America/Guayaquil y fin exclusivo conservados | PASS | PASS |
| D14 | Todos los empatados, posiciones y nota; segmentar sin excluir | PASS | PASS |

Sin excepciones constitucionales ni decisiones funcionales abiertas. Versión de Excel y capacidad del entorno se registran durante QA.

## Project Structure

### Documentation (this feature)

- plan.md, research.md, data-model.md, contracts/api-ui-reporting.md, quickstart.md, visual-audit-plan.md.
- tasks.md lo genera después speckit-tasks, no este comando.

### Source Code (repository root)

- sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/{api,application,domain,infrastructure}/.
- Mismo prefijo: modules/reporting/ y shared/{security,catalog,storage,report,error}/.
- sarc-bakend/src/main/resources/db/migration/ y src/test/java/com/tidsec/sarc_bakend/.
- sarc-bakend/docs/api/openapi.json.
- sarc-frontend/src/app/core/{auth,api/generated}/, features/{consumption,inventory,reports}/, shared/{ui,reports}/ y design-system/.

**Structure Decision**: ampliar servicios/puertos/DTO existentes. Consumo publica metadatos por shared/catalog/ReportArchive, sin importar entidades internas de reporting. No nuevo framework genérico ni sustitución de mappers. La grafía real del backend es sarc-bakend.

## Phase 0 — Investigación

Completada en [research.md](research.md). Resueltas autorización, legado, descarga privada, bibliotecas reales, snapshot, SXSSF, nulos/cero y metodología visual. Evidencia de PRD/TRD, código, esquema reproducible y fuentes oficiales. No quedan incógnitas técnicas bloqueantes.

## Phase 1 — Diseño

### P1. Política de audiencia, consultas y Angular

- Reutilizar ADMINISTRADOR/ANALISTA como operadores junto a ConsumptionPermissions. Nombres funcionales largos del spec corresponden a GERENTE/SUPERVISOR/ASISTENTE existentes; no crear roles. Unión OR, sin cambiar permisos de escritura.
- La autenticación actual decide audiencia, nunca un parámetro del cliente. Proyección en aplicación/API; cálculo interno y trazabilidad permanecen.
- ConsumptionSummaryResponse omite pendingLink,awaitingApproval,test,energyReview,energyRejected para gerenciales. Operadores reciben los cinco aun en cero. approvedAboveThreshold y los campos oficiales siguen para todos. No simular ceros por restricciones.
- SessionQueryService.search aplica alcance VALIDATED antes de contar/paginar. Intersectar filtros, no usar withState de forma ciega: state=TEST no debe convertirse en oficiales. Filtro operativo explícito de gerencial→403 según contrato; texto/equipo/energía no amplían alcance.
- Historial energético: operadores conservan todo. Gerenciales solo evidencia APPROVED vigente de sesión actualmente oficial; nunca rechazos/pendientes anteriores. Sesión no oficial/inexistente→404 uniforme; oficial ordinaria→lista vacía. Conservar selección de justificación vigente de003.
- Resumen bajo lectura REPEATABLE_READ para sus múltiples agregados, manteniendo reglas numéricas. Informes conservan snapshot actual. No se crea API as-of para reproducir cualquier corte histórico.
- Angular conserva ruta de sesiones para todos los lectores autorizados; roles/presencia autorizada controlan tarjeta, filtros y acciones. Adaptar dominio/mappers a campos omitidos, limpiar estado al perder sesión/rol y diferenciar evidencia oficial de historial operativo.

### P2. Modelo de documento y archivo

- ConsumptionReportService usa mismo calculator y snapshot, con proyección inmutable autorizada antes del renderer y sección operativa opcional. En gerencial no materializar excluded ni sus revisiones; no pasar datos restringidos al modelo del documento.
- Tope100.000 filas autorizadas: oficiales+excluidas para operadores como hoy, solo oficiales para gerenciales. Errores de tamaño gerenciales no dependen de cantidades restringidas. Mantener366 días y no cambiar validez de sesiones.
- Conservar ranking completo tabular, Top con empates, subconjunto aprobado sin duplicar y justificación vigente en Excel.
- ReportArchive transporta perfil de contenido, corte exacto report.cutOff y criterios estructurados; constructor/adaptación compatible con emisor de inventario. V011 aditiva, descrita en data-model.md, sin tablas de sesiones nuevas.
- Consumo legado sin perfil se restringe: renderers anteriores incluyen operativas incluso en cero. Inventario mantiene acceso. No inferir contenido de primera página/nombre/métricas actuales. No reescribir bytes,checksum,fileRef,ID,fechas ni corte original.
- Listado muestra acceso/motivo neutro sin cantidades internas. Nuevo OFFICIAL_ONLY consultable por todos los roles existentes. Generación devuelve headers de ID/corte de nueva emisión; nuevo UUID ya lo crea el archivo. No sustituir original.
- Reemisión con filtros estructurados→precargar y confirmar; legado→mostrar scope informativo y pedir período/filtros confirmados. No parsear texto truncado para inventar UUID/alcance exacto. Usar generación existente y refrescar listado, con corte nuevo.
- En Angular, el formulario de Reportes usa exclusivamente IssuedReportsFacade; esta delega en IssuedReportsApi.generateConsumptionReport, que llama al endpoint /energy/report existente. Tipos desde core/api/generated o dominio propio de Reportes; auxiliares neutrales compartidos solo cuando corresponda. No importar ConsumptionApi ni domain/application/data-access/presentation internos de Consumo desde Reportes, ni saltarse la fachada desde la vista. Se reutiliza el contrato HTTP y las reglas del servidor, no servicios privados de otra feature. Conservar las reglas ESLint, sin excepciones para este flujo.

### P3. Descarga privada y excepciones

- Cambiar /reports/issued/{id}/download de JSON con URL a binario autenticado. Actualizar OpenAPI y Angular juntos. Transporte compartido también adapta descargas de inventario, conservando datos/permisos/función.
- Añadir lectura de documento a FileStorage. URL firmada permanece en adaptador servidor; sin redirects ni URL/fileRef públicos. Referencia solo del repositorio, no URL arbitraria del cliente. Reutilizar SDK/cliente HTTP instalado, timeout, límite y cierre de recursos; sin I/O externo en transacción JPA larga.
- Autorizar antes de abrir almacenamiento; Cache-Control private,no-store y Content-Type/Disposition válidos. Reutilizar sesión JDBC y revocación existente a cada petición futura.
- 403 REPORT_OPERATIONAL_CONTENT_RESTRICTED en español con acción de nueva emisión, sin regeneración automática;404 inexistente/sin copia;503 almacenamiento ausente/no disponible. Reutilizar ProblemDetail,traceId y saneamiento de errores; sin credenciales/URLs en respuesta/logs.
- URLs previas duran5min: cortar emisión antigua y comprobar expiración/caché antes de declarar protección completa. No rotar secretos globales ni prometer retirar archivos ya descargados.

### P4. Gráficos y energía desconocida

- Modelo de series pequeño derivado del ConsumptionReport autorizado, sin consultas/agregaciones paralelas. BigDecimal hasta coordenadas; mantener números/tablas como fuente verificable.
- PDF: energía diaria y sesiones diarias en líneas; ranking y tres distribuciones en barras horizontales. OpenPDF PdfTemplate/canvas y helper acotado en CorporatePdf; sin navegador headless, motor ECharts ni dependencia nueva.
- Mantener A4 horizontal, logos, fuentes y colores corporativos. Bloques con altura conocida, saltos de página y segmentación de tendencias/Top extenso con escala consistente y todos los valores/nombres accesibles. No recortar empates para caber. Títulos,unidades,leyendas,período,zona y corte.
- Nulo energético→hueco/«Sin datos», cero informado→0. Días sin sesiones conservan semántica EnergyTotals.EMPTY. En distribuciones distinguir ninguna lectura, cero conocido y ninguna sesión; no inventar porcentajes si total0. Mantener conteo de inicios.
- Dashboard hoy usa energyKwh??0: cambiar solo presentación con number|null en ChartSeries y estados de ShareChart. No tocar valores del servidor, fórmulas, clasificación o ranking.
- Excel: evaluar prototipos de nativos XDDF con POI actual y detalle SXSSF antes de integrar la solución. Registrar una ruta técnica: NATIVO, IMAGEN o TABULAR_CON_EXCEPCION; son resultados de evaluación, no estados de negocio persistidos ni opciones nuevas para el usuario. La integración de dibujo XSSF sobre SXSSF y cachés desde arrays oficiales solo corresponde a NATIVO. Nunca releer filas expulsadas: ventana200 frente a366 días. Conservar columnas/tipos/hojas autorizadas y fórmulas existentes en todas las rutas; no inventar fórmulas.
- Si NATIVO satisface criterios, integrarlo y probar rangos/cachés/blancos/0. Si falla un criterio reproducible, evaluar IMAGEN con POI; si esta satisface criterios, integrar únicamente esa salida gráfica. Si ninguna alternativa resulta conveniente dentro del alcance, TABULAR_CON_EXCEPCION exige evidencia de ambas alternativas y conserva datos/fórmulas sin exigir gráficos, dibujos o cachés nativas. No implementar ni mantener rutas descartadas solo para completar tareas.
- Pruebas comunes en las tres rutas: privacidad, roundtrip, datos/tipos/fórmulas, blancos frente a0,366 días y todos los empates. Pruebas específicas: NATIVO comprueba charts/rangos/cachés; IMAGEN comprueba imágenes/legibilidad y su paridad con las tablas; TABULAR_CON_EXCEPCION comprueba integridad sin gráficos ni soportes residuales de alternativas descartadas y justificación documentada. Apertura real en Excel Windows con versión en todos los casos. Si esta invalida la selección preliminar, reevaluar alternativas y repetir validación; falta de acceso no demuestra inviabilidad ni permite cerrar aceptación.

### P5. Auditoría y corrección visual

[visual-audit-plan.md](visual-audit-plan.md) fija cobertura y evidencia. Impeccable disponible; contexto/metadatos ya sincronizados, sin regenerarlos. Detector/audit seguido de confirmación en navegador, sin puntuación ficticia.

Primero reproducir connector-type-dialog: ayuda larga,subscript fijo,gap8 y ancho. Si se confirma, API pública subscriptSizing=dynamic o defaults acotados, espaciado por tokens; sin selectores MDC privados ni defaults globales que carguen overlay en bundle inicial. Aplicar patrón compartido solo donde el mismo defecto se confirme, incluyendo identidad externa/formulario equipo si corresponde.

Revisar _dialog-form.scss,_list-page.scss,shell,tablas/gráficas y diálogos; preservar dirty guard, foco,acciones,carga y errores. La diferencia entre doctrina de elevación y tarjetas actuales documentada en DESIGN.md no autoriza elevar toda la app. Preferencias estéticas sin defecto quedan fuera.

### P6. Contratos y regresión

[contracts/api-ui-reporting.md](contracts/api-ui-reporting.md) define el contrato. Actualizar openapi.json y regenerar tipos TS, sin any. Las pruebas002/003 que consultan operativas con cookie manager pasan esa inspección a analyst/admin conservando expectativas de negocio; añadir pruebas gerenciales. No borrar regresiones ni retener expectativas de visibilidad sustituidas por004.

## Secuencia para speckit-tasks

| Incremento | Resultado verificable | Dependencias |
|---|---|---|
| A | Fixtures aisladas, inventario de accesos y medición base | Plan y análisis de coherencia |
| B | Política backend, proyecciones, sesiones/historial y snapshot | A |
| C | V011, metadatos, archivo/descarga y errores | A/B |
| D | Angular tarjeta,filtros,emitidos y nueva emisión; tipos regenerados | B/C |
| E | Series y seis gráficos PDF paginados/tablas | B |
| F | Evaluación Excel y gráficos o excepción demostrada | B/E |
| G | Auditoría completa, conector, corrección compartida, reauditoría | A; revisar D/E cuando existan |
| H | QA por rol/exportaciones/Excel/AA/rendimiento y evidencia | B–G |

No se crea tasks.md en este comando. Trabajo visual opcional sin defecto confirmado no amplía el incremento obligatorio.

## Trazabilidad

| Requisitos | Incrementos | Prueba |
|---|---|---|
| FR-001, FR-002, FR-004, FR-009; SC-001, SC-002 | B/D/H | Cinco roles, multirol, oficiales iguales, paginación y accesos directos |
| FR-003, FR-007, FR-008; SC-003 | B/C/D/H | Archivos completos, legado, descarga, reemisión y hash intacto |
| FR-005, FR-006; SC-009 | B–F/H | Regresión001–003 y justificación oficial vigente |
| FR-010, FR-011, FR-012, FR-013, FR-014, FR-015; SC-004, SC-005, SC-006 | G/H | Rutas/estados, diez combinaciones, AA y antes/después |
| FR-016, FR-017, FR-018, FR-019; SC-007 | D/E/H | Seis gráficos, corte/filtros, nulos/0,366 días y empates |
| FR-020, FR-021; SC-008 | F/H | Estructura XLSX, Excel Windows y versión |
| FR-022; SC-010 | A/E/F/H | ≤500kB, p95 consultas/dashboard y tiempo/memoria de emisión |

## Archivos afectados en implementación

Rutas backend siguientes relativas a `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/`; no son archivos modificados en esta planificación.

| Grupo | Archivos existentes y ampliaciones acotadas |
|---|---|
| Política/consultas | modules/consumption/application/service/ConsumptionPermissions.java, SessionQueryService.java; política/proyección de lectura nueva junto a esos servicios; domain/repository/SessionCriteria.java y predicados solo si hace falta expresar intersección segura |
| API consumo | modules/consumption/api/controller/ConsumptionController.java, EnergySessionController.java; api/response/ConsumptionSummaryResponse.java y proyección de EnergyReviewResponse según audiencia |
| Reporte consumo | modules/consumption/application/service/ConsumptionReportService.java; application/result/ConsumptionReport.java; infrastructure/report/ConsumptionPdfRenderer.java, ConsumptionExcelRenderer.java y helper de series/dibujo acotado |
| Archivo | modules/reporting/domain/model/IssuedReport.java; application/service/ReportArchiveService.java, IssuedReportService.java; api/controller/IssuedReportController.java, api/response/IssuedReportResponse.java; adapter JPA solo para metadatos/consultas necesarios |
| Compartidos | shared/catalog/ReportArchive.java; shared/storage/FileStorage.java, CloudinaryFileStorage.java; shared/report/CorporatePdf.java, CorporateWorkbook.java; shared/error/GlobalExceptionHandler.java y excepción de acceso específica |
| Persistencia/contrato | sarc-bakend/src/main/resources/db/migration/V011__visibilidad_reportes.sql nueva; sarc-bakend/docs/api/openapi.json; tipos TS regenerados |
| Angular consumo | features/consumption/domain/consumption.ts, data-access/consumption-api.ts y mappers existentes; application/sessions-facade.ts; presentation/dashboard-page/, sessions-page/, energy-review-dialog/, share-chart/; shared/ui/chart/chart-view.ts |
| Angular emitidos | features/reports/data-access/issued-reports-api.ts (generateConsumptionReport vía endpoint existente), application/issued-reports-facade.ts, domain/issued-report.ts, presentation/issued-reports-page/ y formulario de nueva emisión acotado; sin imports privados de Consumo, con tipos generados/core o propios de Reportes y reutilización de toProblemFromBlob existente |
| Formularios/visual | features/inventory/presentation/connector-type-dialog/connector-type-dialog.ts; shared/ui/_dialog-form.scss y consumidores únicamente con defecto confirmado; demás rutas/componentes según evidencia de auditoría |
| QA | ConsumptionApiTest, ConsumptionPdfRendererTest, pruebas de XLSX/archivo/metadatos/política; Feature004BrowserSandbox opt-in nueva; specs Angular correspondientes y evidencia visual/exportaciones |

No modificar parsers, import processors, reglas de dominio energético, identidades, mappers de inventario ni migraciones previas por conveniencia de este incremento. Si una corrección visual afecta un componente compartido, probar sus consumidores y documentar la necesidad.

## Riesgos y controles

- Filtrar solo UI deja API/archivos abiertos: proyección/consulta backend y revisión de todas las partes del documento.
- Sesión oficial con rechazos antiguos: entregar solo evidencia aprobada vigente a lectores.
- URL previa compartida: transporte autenticado más expiración real de enlaces antiguos.
- Scope legado incompleto: confirmación de filtros, sin reconstrucción inventada.
- Romper002/003 al adaptar DTO/tests: conservar reglas/fixtures, cambiar solo actor de lectura operativa.
- SXSSF/Top/documento grande: arrays autorizados,segmentación,streaming,límites y mediciones.
- Arreglo CSS sin causa: reproducción anterior y revalidación del componente compartido.
- Build/axe aprobados como falsa garantía: gate500kB explícito y auditoría manual/renderizada.

## Validation Strategy

[quickstart.md](quickstart.md): comandos,aislamiento y escenarios. PostgreSQL18 desechable,storage simulado,puertos alternos; nunca migrar/sembrar BD local. Pruebas de proveedor y Excel real separadas de mocks. No hubo ejecución de QA en esta planificación.

## Complexity Tracking

Sin violaciones constitucionales. V011 y lectura privada se justifican para autorizar originales sin alterarlos; gráficos con bibliotecas existentes. No nueva arquitectura ni servicio aparte de exportación.
