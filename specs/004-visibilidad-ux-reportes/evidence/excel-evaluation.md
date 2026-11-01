# Evaluación Excel 004

**Dictamen: NATIVO** (XDDF de Apache POI actual, sin dependencias nuevas). IMAGEN y TABULAR_CON_EXCEPCION no se evaluaron ni implementaron porque NATIVO cumple sus criterios.

| Criterio FR-020 | Resultado | Evidencia |
|---|---|---|
| Compatibilidad | PASS | Excel 16.0 build 20326 abrió ambos archivos sin reparación ([excel-windows-results.md](excel-windows-results.md)) |
| Legibilidad | PASS tras corrección | Vistos por exportación PNG con Excel visible; ver [excel-results.md](excel-results.md) |
| Integridad | PASS | Hojas previas intactas; hoja nueva `Gráficos`; `ConsumptionExcelRendererTest` |
| Utilidad | PASS | Seis representaciones con rangos locales editables |
| Permisos | PASS | Inspección de todas las partes OPC, incluidos cachés: sin contenido operativo para gerenciales (`ConsumptionReportPrivacyTest`, `ConsumptionExcelRendererTest`) |
| Rendimiento | PASS en fixture | 366 puntos sin releer filas: rangos y caché salen de los arrays del informe. Sin medición de carga 250.000 (T065 pendiente) |

Decisiones técnicas:

- Los datos viven en `Gráficos`; la caché se llena desde los mismos valores, sin leer filas expulsadas por SXSSF.
- `CorporateWorkbook` omite la celda de un valor nulo en vez de escribir `""`: Excel trataría el texto vacío como 0 en una línea. Efecto colateral: toda celda nula del libro ahora queda en blanco real.
- `dispBlanksAs=gap`: Excel reporta `DisplayBlanksAs=1` (xlNotPlotted).
- Sin fórmulas nuevas. T056 se resolvió sin puente adicional en `CorporateWorkbook`: el dibujo se obtiene del `XSSFWorkbook` subyacente.
