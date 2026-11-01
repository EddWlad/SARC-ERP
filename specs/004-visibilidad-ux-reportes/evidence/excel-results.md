# Resultados Excel 004

Dictamen: **NATIVO confirmado**, con una corrección de presentación hallada al ver los gráficos.

| Comprobación | Resultado |
|---|---|
| Cifras oficiales octubre, API | 2.240 kWh, 10 sesiones, 7 equipos, 248,889 kWh/sesión, idénticas a resumen y PDF para gerente y analista |
| Datos de fixture API | `feature004-api-gerente.xlsx`, `feature004-api-analista.xlsx` |
| Hojas ocultas | Ninguna |
| Fórmulas | 0 en todas las hojas |
| Filtros | Presentes en hojas con filas; ausentes en hojas sin filas y en `Gráficos` |
| Tipos | Celdas numéricas contadas por Excel en cada hoja |
| Permisos | Gerente sin `Excluidas`; partes OPC cubiertas por pruebas |

**Defecto corregido en `CorporateWorkbook`:** las líneas salían suavizadas, con curva por debajo de 0, y con un color por punto; el Top aparecía con el primer lugar abajo. Ahora: línea recta de un color, eje desde 0, marcador circular y ranking de arriba abajo. Capturas `excel-linea-antes.png`, `excel-linea-despues.png`, `excel-top-despues.png`. El tamaño de marcador no se fija: `poi-ooxml-lite` no incluye ese esquema y fallaba al generar.

Los archivos de fixture API se generaron antes de esta corrección de estilo; los gráficos corregidos se vieron con la fixture de 366 días de `ConsumptionExcelRendererTest`.
