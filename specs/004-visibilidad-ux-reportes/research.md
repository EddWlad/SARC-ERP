# Investigación — Feature 004

Fecha: 2026-10-31. Baseline `7354d6e`. Investigación de solo lectura de producto/código; no pruebas ejecutadas ni auditoría visual renderizada. Las decisiones de este archivo son técnicas y se subordinan a `spec.md`.

## Fuentes y límites

- Constitución 1.0.0, especialmente principios I–III y D10–D14; aclaraciones de 004 sobre originales y Excel Windows.
- PRD v1.1: p.6 multirol y permisos, p.11 consumo, pp.17–19 reportes/responsive, p.24 cuestiones técnicas delegadas al TRD.
- TRD revisión técnica v1.0: p.2 objetivos p95/capacidad; pp.3–7 stack/DTO/UX; pp.8–10 esquema y seguridad; p.15 snapshot/reporting; pp.18–21 pruebas y límites operativos. Los objetivos requieren evidencia, no son resultados actuales.
- V001–V010 y mappings JPA. No se consultó catálogo PostgreSQL vivo ni historial Flyway dev. La nueva migración debe probarse en PostgreSQL 18 desechable.
- Rutas de backend bajo `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/`; frontend bajo `sarc-frontend/src/app/`, salvo indicación.

## R01 — Mantener stack real

**Decisión**: Chart.js existente, OpenPDF 3.0.5 y POI 6.0.0; conservar DTO records/mapeos explícitos. No incorporar ECharts, MapStruct ni Spring Batch para esta feature.

**Motivo/evidencia**: `sarc-frontend/package.json` y `shared/ui/chart/chart-view.ts:13–28`; `sarc-bakend/pom.xml:21–22,69–83`. El adjunto/TRD describe tecnologías que no están implementadas en estos puntos. No confundir intención histórica con dependencia instalada.

**Alternativas**: migrar gráficos o mappers elevaría alcance, regresión y bundle sin resolver un requisito adicional.

## R02 — Política común de audiencia, sin alterar estado oficial

**Decisión**: operadores son `ADMINISTRADOR` o `ANALISTA`; demás roles actuales reciben solo oficiales. Proyectar/omitir cinco totales operativos al entregar, sin modificar `ConsumptionCalculator` ni fingir ceros. Mantener `approvedAboveThreshold`.

**Motivo/evidencia**: `modules/identity/domain/model/Role.java:4`; `ConsumptionPermissions.java:10`, `SessionService.java:33`, `SessionImportService.java:48`. `ConsumptionSummaryResponse.java:22–39,75–90` entrega siempre las cinco categorías; `ConsumptionCalculator.java:57–72` ya separa oficiales y subconjunto aprobado. `SessionStore.hasAnyRole` frontend implementa unión OR.

**Alternativas**: ocultar tarjeta solamente incumple FR-002/003; crear nuevos roles o alterar fórmulas incumple alcance.

## R03 — Restringir consulta antes de contar/paginar

**Decisión**: alcance oficial obligatorio en búsqueda gerencial, filtros operativos explícitos rechazados; historial gerencial solo evidencia APPROVED vigente de sesión oficial, conservando toda la historia para operadores.

**Motivo/evidencia**: `SessionQueryService.java:42–56` no limita audiencia y `EnergySessionController.java:62–103` expone filtros/historial; `SessionCriteria.java:30` reemplaza estado al usar `withState`. El filtro debe intersectarse, no reemplazarse silenciosamente. Una sesión hoy oficial puede tener revisiones antes rechazadas: no entregar esa historia operativa completa al lector.

**Alternativas**: filtrar una página en Angular expone totales y produce paginación incorrecta; cerrar toda la ruta de sesiones elimina consulta oficial autorizada.

## R04 — Instante consistente, sin inventar consultas históricas

**Decisión**: conservar `ConsumptionReportService` REPEATABLE_READ y adoptar lectura consistente para resumen. Persistir el corte del propio informe nuevo, no sustituirlo por `createdAt` del archivo ni por la fecha actual al descargar.

**Motivo/evidencia**: `ConsumptionReportService.java:81–98,102–124` lee una instantánea y renderiza fuera; `SessionQueryService.java:26,55` usa solo readOnly predeterminado. El cálculo usa varias consultas, susceptibles de observar commits diferentes con READ_COMMITTED.

**Alternativas**: capturar pantallas como fuente del informe o consultar de nuevo por gráfica introduce incoherencias. Igual período no garantiza igual corte si se confirmó una importación entre consultas. No se añade infraestructura de replay/as-of.

## R05 — Metadatos nuevos, originales inmutables

**Decisión**: V011 aditiva con perfil de contenido, corte y criterios estructurados para nuevas emisiones; legado de consumo sin perfil restringido, inventario conserva acceso. Sin backfill de supuestos cortes/filtros ni modificación de bytes.

**Motivo/evidencia**: V007 `report_issued:38–57`; `IssuedReport.java:35–45,54–63` no conserva audiencia/corte/filtros y trunca scope a500 caracteres. PDF `ConsumptionPdfRenderer.java:49–58` y XLSX `ConsumptionExcelRenderer.java:68,81–89` anteriores incluyen operativas aun en cero. `ReportArchiveService.java:42–63` ya crea UUID, checksum y copia privada.

**Alternativas**: inspeccionar solo la primera página no prueba ausencia de restricciones; parsear nombres de scope no recupera UUID fiables; regenerar sobre el original viola D12 y la aclaración aprobada. Metadatos generales para módulos futuros quedan fuera.

## R06 — Autorización de cada entrega, sin enlace externo reutilizable

**Decisión**: el endpoint de descarga entrega bytes mediante SARC autenticado, manteniendo URL firmada del lado servidor; extender FileStorage con lectura de documento. Autorizar antes de transferir; no caché pública ni redirect.

**Motivo/evidencia**: `IssuedReportService.java:39–49`, `IssuedReportController.java:26–28,54–56` retornan URL; `CloudinaryFileStorage.java:24,96–102` da vigencia de cinco minutos. El port actual ofrece URL, no lectura. `UserManagementService.java:75–86` ya revoca sesiones tras cambio de roles; reutilizarlo no reconstruir autenticación.

**Alternativas**: una URL firmada más corta aún permite acceso compartido/retiro de rol durante su vigencia; hacer público el archivo no sirve. El endpoint es compartido: adaptar transporte de inventario sin alterar su acceso ni contenido. La ventana de URLs previas debe agotarse y comprobarse antes de declarar completo el despliegue.

## R07 — Series derivadas de datos autorizados; PDF sin dependencias nuevas

**Decisión**: helper pequeño de series/bloques vectoriales OpenPDF, alimentado por el informe autorizado ya leído; seis representaciones, con tablas/texto de respaldo. Segmentar para legibilidad, sin perder empates/nombres/días.

**Motivo/evidencia**: `ConsumptionPdfRenderer.java:38–71` solo tiene tablas/KPI; `shared/report/CorporatePdf.java:48,92–116` ofrece A4 horizontal, logos y tablas repetibles. `ConsumptionCalculator.java:45–63,80–104,129–139` entrega series, Top con empates y rango máximo366. Es suficiente una capa de dibujo, no una nueva agregación.

**Alternativas**: ECharts/headless en backend o JFreeChart añaden runtime/dependencias; imágenes del dashboard mezclarían permisos/cortes y no son requisito. Un generador vectorial acotado evita esas dependencias; su paginación, fuentes y escala requieren pruebas reales.

## R08 — Gráficos Excel con SXSSF conservado

**Decisión**: evaluar primero prototipos XDDF de POI. Elegir NATIVO si satisface los criterios, IMAGEN si falla el nativo y la alternativa cumple, o TABULAR_CON_EXCEPCION si ambas alternativas presentan limitaciones demostradas no resolubles convenientemente dentro del alcance. Solo NATIVO necesita puente de dibujo XSSFSheet/cachés de arrays autorizados y referencias a rangos locales. Integrar únicamente la ruta elegida; no exigir implementación nativa para una entrega de imágenes o una excepción tabular válida. Estos nombres son dictámenes QA, no estados persistidos ni opciones de producto.

**Motivo/evidencia**: `CorporateWorkbook.java:28–31,60–106` usa `SXSSFWorkbook(200)`. No se pueden releer todas las filas después de expulsarlas. Fuentes locales POI6: `SXSSFWorkbook.getXSSFWorkbook():593`, `XSSFDrawing.createChart():233`, `XDDFDataSourcesFactory.fromArray(...):340–352`; SXSSFDrawing no crea gráficos directamente. Una tendencia366 días debe probar este caso. `ConsumptionExcelRenderer.java:51–69` tiene seis hojas, detalle y Excluidas, sin gráficos actuales.

**Alternativas**: convertir todas las transacciones a XSSFWorkbook perjudica memoria; declarar inviabilidad por no disponer de createChart en SXSSFDrawing es prematuro. Roundtrip POI y XML no sustituyen apertura/legibilidad en Excel Windows real. La versión se registra al probar; Excel web/LibreOffice fuera de aceptación acordada.

**Verificación por ruta**: todas conservan privacidad, valores oficiales, tipos/datos/fórmulas, blancos/0 y apertura real en Windows. NATIVO prueba charts/rangos/cachés; IMAGEN prueba imágenes legibles y paridad; TABULAR_CON_EXCEPCION prueba integridad sin gráficos/soportes residuales y evidencia de alternativas. Una apertura real fallida obliga a revisar el dictamen, no a conservar una ruta inválida ni declarar falta de acceso como inviabilidad.

## R09 — Ausencia frente a cero: presentación mínima

**Decisión**: puntos nulos/huecos y estados de distribución diferenciados, conservando agregados originales.

**Motivo/evidencia**: `EnergyTotals.java:17–29`: sesiones sin ninguna lectura→null; conjunto sin sesiones→0; lectura0→0 conocido. Frontend `dashboard-page.ts:59–61` usa `??0`, `chart-view.ts:50–52` admite solo number[], `share-chart.ts:43–46` no diferencia vacíos. El ajuste de presentación está dentro de FR-019 y no cambia002.

**Alternativas**: imputar0 crea energía inexistente; eliminar sesiones sin energía rompe002. Una proporción con total conocido0 es indefinida, no un reparto de0% inventado.

## R10 — Conector: hipótesis fundada, aún no defecto medido

**Decisión**: reproducir ayuda/errores multilínea en diez combinaciones; si se confirma, usar subscriptSizing dynamic/API pública y tokens, con reutilización acotada.

**Motivo/evidencia**: `features/inventory/presentation/connector-type-dialog/connector-type-dialog.ts:58–62,87–92` contiene ayuda multilínea y fields gap8/min-width; `features/inventory/presentation/external-identity-dialog/external-identity-dialog.ts:80–81,113–114` y formulario de equipo tienen patrón parecido. Material instalado `_form-field-chunk.mjs:603,684–690` usa default fixed; app.config no configura defaults. La reserva de una línea es una causa probable, no demostración renderizada.

**Alternativas**: offsets mágicos/overrides MDC privados son frágiles; defaults globales/rediseñar formularios no está justificado. Comprobar efectos sobre foco/guardado y demás consumidores del patrón compartido.

## R11 — Auditoría completa y regresiones conservadas

**Decisión**: metodología Impeccable audit/detect, verificación contextual y manual, matriz de visual-audit-plan.md; sin score o certificación inventados. Reutilizar pruebas existentes y ampliar por roles/archivos.

**Motivo/evidencia**: `DESIGN.md` y `.impeccable/design.json` sincronizados; `reference/audit.md` establece cinco dimensiones. `angular.json`: warning500kB/error1MB; hay que medir ≤500kB explícitamente. Vitest/jsdom no valida geometría; no hay configuración/dependencia Playwright/axe incorporada en package.json. Varias comprobaciones operativas de ConsumptionApiTest usan manager; trasladar actor a analyst sin perder comportamiento002/003.

**Alternativas**: refactorizar toda la elevación por doctrina DESIGN, tomar exit0 de build/axe como conformidad o declarar corregido algo no reproducido excede alcance. WCAG AA objetivo24px o excepción válida, no convertir recomendación44px en obligación universal.

## R12 — Reemisión desde Reportes sin imports privados de Consumo

**Decisión**: formulario/página de Reportes → IssuedReportsFacade → IssuedReportsApi.generateConsumptionReport → endpoint GET /energy/report existente. Tipos desde core/api/generated o dominio propio de Reportes; sin importar ConsumptionApi ni otros internos de Consumo. Reutilizar toProblemFromBlob existente para errores binarios.

**Motivo/evidencia**: `sarc-frontend/eslint.config.js:14–17,112–124` prohíbe imports al interior de otra feature y acceso directo de presentation a data-access; las fachadas y clientes HTTP propios ya existen en Reportes. Se reutiliza la API backend y su fuente única de cálculos, manteniendo separación de capas.

**Alternativas**: inyectar ConsumptionApi en el diálogo o incluso en la fachada de Reportes incumple la frontera entre features. Añadir un endpoint duplicado, relajar lint o mover servicios completos a shared no es necesario: basta un método de emisión en el cliente de Reportes, con su fachada y pruebas del mismo contrato HTTP.

## Referencias técnicas oficiales consultadas

- [Ejemplos oficiales Apache POI](https://poi.apache.org/components/spreadsheet/examples.html): gráficos XSSF y ejemplos de imágenes. Viabilidad final condicionada al caso SXSSF y Excel Windows.
- [LineChart del proyecto Apache POI](https://github.com/apache/poi/blob/trunk/poi-examples/src/main/java/org/apache/poi/examples/xssf/usermodel/LineChart.java): uso de XDDF. Las APIs concretas se contrastaron con fuentes locales6.0.0, sin actualizar versión.
- [OpenPDF: licencia](https://github.com/LibrePDF/OpenPDF/blob/master/LICENSE.md): elección MPL2.0 o LGPL2.1+. Se conserva biblioteca instalada, sin copiar ni modificar su código.
- [API OpenPDF3.0.5 PdfContentByte](https://javadoc.io/static/com.github.librepdf/openpdf/3.0.5/com.github.librepdf.openpdf/org/openpdf/text/pdf/PdfContentByte.html): primitivas de dibujo.
- [Angular Material Form Field API](https://material.angular.dev/components/form-field/api): API pública; comportamiento fixed confirmado en paquete instalado.
- Licencias en POM/paquetes locales: POI Apache2.0; Chart.js MIT; Angular/Material MIT. Registrar avisos existentes y revisar cualquier dependencia QA nueva; no se propone actualización.
