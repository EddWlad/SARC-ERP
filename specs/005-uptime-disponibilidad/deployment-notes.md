# Notas de despliegue — Feature 005

## Orden

1. Respaldar la base.
2. Desplegar backend: Flyway aplica las nuevas V013, V014 y V015 de uptime; no modifica V001–V012.
3. Desplegar frontend, generado con el contrato `docs/api/openapi.json` actual.

Consumo (US6) no requiere migración y puede publicarse aparte.

## Verificar tras desplegar

- Abrir `/uptime/tablero` con rol operativo y de consulta.
- Registrar y validar una medición de prueba solo si hay ambiente de pruebas.
- Emitir un reporte y comprobarlo en `/reportes`.
- En Consumo, confirmar que un rol de consulta no recibe «Aprobadas sobre umbral» en PDF ni en Resumen de Excel.

## Limitaciones vigentes

- Carga de archivo activa para el informe semanal (XLSX, XLS o CSV) tal como se envía por correo: se leen todas sus hojas y se elige la semana (lunes a domingo, ya terminada) al cargar; la fila 1 se ignora. Columnas: Cargador, Conector, Uptime (%), Fuera de linea, Disponible y En falla (hrs); Activo y Cargando son opcionales. Sin Cargando se guarda el uptime del informe. Queda como ventana de 7 días (PROVISIONAL) salvo que se confirme semana exacta.
- Migración V014: `uptime_basis` en revisiones y filas (por defecto `STATES`), hoja y fila de origen en filas, y semana declarada en lotes. Solo agrega columnas con valor por defecto o nulas.
- Migración V015: `suggested_connector_id` nulo en filas de importación. Las filas ya pendientes no tienen propuesta; aparece al volver a cargar el informe.
- Rendimiento medido en secuencial; falta prueba con 30 usuarios.
- `Feature005BrowserSandbox`, `Feature005ConsumptionBaseline` y `Feature005PerformanceBench` son pruebas opcionales: nunca se ejecutan sin su propiedad.

## Separación de alcance al cierre 2026-11-04

Esta entrega integra únicamente 005 y sus tres correcciones QA. El rediseño del período de captura semanal, el Excel con horario desconocido, el promedio del informe y los pasteles se tratan en Feature-006; no hay V016 ni código 006 en estos commits. T124 humana y los límites de rendimiento declarados continúan pendientes. La asignación de semana declarada del Excel de 005 no acredita límites reales observados en Enerlink.
