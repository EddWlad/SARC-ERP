# Cierre técnico Feature-005 — 2026-11-04

Autorización del usuario: commits agrupados por funcionalidad, integración a main, push y eliminación de rama 005. No incluye implementación de Feature-006 ni modificaciones de BD dev. Los borradores 006 se preservan localmente sin publicar.

## Correcciones QA

| Hallazgo | Reproducción previa | Corrección | Prueba |
|---|---|---|---|
| Sin atribuir cuenta otros períodos si el rango queda vacío | Rango 19–26 octubre devuelve 2 cuando corresponde 1 | Usar rango solicitado; apertura independiente de filtros de atribución y fallback acotado | UptimeClosureRegressionTest.emptyChosenRangeDoesNotCountOtherPeriods / officialOpeningKeepsUnattributedPeriodWhenNetworkHasNoData |
| PDF/Excel omiten sin atribuir | Etiqueta/cantidad ausentes | Pasar pendingLink al modelo de reporte y ambos renderizadores | UptimeClosureRegressionTest.bothReportsIncludeUnattributedCount |
| Etiqueta numérica desbordada aborta lote | NumberFormatException provoca FAILED y timeout de espera READY | Clasificar fila pendiente con motivo sin afectar otras filas | UptimeImportApiTest.oversizedConnectorNumberStaysPendingWithoutFailingOtherRows |

Regresiones escritas antes de corregir: 4 casos, 3 fallos de aserción y 1 timeout provocado por lote fallido. Después: 51 pruebas enfocadas aprobadas. Logs de ejecución local en target/feature005-close-red.log y feature005-close-green.log (no se versionan).

## Puertas finales

La suite Java completa se ejecutó en Testcontainers PostgreSQL 18 y con almacenamiento simulado: BUILD SUCCESS, sin fallos/errores. Resultado agregado exacto registrado en final-gates.md. Frontend: 50 archivos y 205 pruebas aprobadas. Concurrencia de Vitest limitada a 2 workers mediante configuración temporal, sin aumentar timeouts ni modificar pruebas de producto; el archivo temporal se elimina tras verificar. Lint correcto, Prettier con --end-of-line auto correcto; el format:check estricto del checkout Windows conserva la advertencia CRLF ya documentada, no se reformatea el proyecto ajeno. Build correcto: inicial 341,77 kB / 95,02 kB estimados de transferencia.

V001–V012 coinciden con todos los hashes de baseline.md. V013–V015 son nuevas. git diff --check sin errores. Sin cambio de contrato público en los tres fixes de cierre: OpenApiContractTest incluido en la suite completa.

## Estado honesto de cierre

Entrega técnica publicable; no equivale a 135/135 tareas ni aceptación humana completa. T124 sigue pendiente: no se cronometra una persona con un agente. Los seis escenarios parciales de quickstart-results.md siguen identificados como parciales, no se cambia su evidencia histórica. Bench secuencial no acredita 30 usuarios y documenta consultas >2 s a 1000 conectores.

La implementación 005 asigna al Excel la semana declarada; no representa horario desconocido independiente ni incluye pasteles nuevos. Esas decisiones, junto con la media del informe y captura por conector, se tramitan como Feature-006 después del cierre. No se introduce V016 en estos commits.
