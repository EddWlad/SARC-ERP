# US4 · Línea base del parpadeo (T034)

Fecha: 2026-11-14 · Código antes del ajuste de US4, rama `feat/009-integridad-temporal-consumo`.

## Condiciones

- Sandbox aislado `Feature009BrowserSandbox` (`-Dsarc.qa.browser009=true`): PostgreSQL desechable de Testcontainers, backend en un puerto aleatorio de 127.0.0.1 y frontend `sarc-frontend-qa009` en 127.0.0.1:4205 con proxy al sandbox. No se tocó la base de compose ni producción.
- Datos: 16 sesiones validadas del mes en curso. Incluyen la Enerlink 1211 (inicio UTC+00:00, fin UTC-05:00), una sin energía y una Wallbox.
- Navegador del panel de Claude a 1440×900, tema oscuro, con la preferencia del sistema «reducir movimiento» activa. Para observar la animación de Chart.js se desactivó esa preferencia dentro de la aplicación (`ChartTheme.prefersReducedMotion`, modo desarrollo).
- Refresco reproducido con el evento `visibilitychange`: es la misma ruta que «volver a la pestaña» de `refreshWhileVisible` y equivale al ciclo de 60 s, pero sin esperar. Cada recorrido registró nodos del DOM (`MutationObserver`), foco, lienzos y llamadas a `Chart.update()` y `Chart.draw()`.

## Hallazgos confirmados

| # | Vista | Disparador | Observación | Causa |
|---|---|---|---|---|
| B1 | Sesiones (tabla de escritorio) | Refresco con datos idénticos | Las 15 filas se reemplazan (`rowsKept 0/15`). El foco pasa del botón «Ver» a `BODY`, y un menú «Acciones» abierto se cierra solo. Durante el recorrido de US3, una referencia al botón quedó obsoleta entre dos clics. | `mat-table` sin `trackBy`: cada respuesta trae objetos nuevos y la tabla destruye y recrea las filas. |
| B2 | Tablero de Consumo | Un refresco de fondo que falla (503 simulado una vez) | Desaparecen los KPI y los 6 gráficos (`canvasesKept 0/6`) y queda solo la alerta. Con el siguiente refresco correcto vuelven 6 lienzos nuevos (`sameCanvasNodes 0`), con la animación de entrada. | La plantilla da prioridad a `error()` sobre el último resultado válido de la misma consulta. |
| B3 | Sesiones | Igual que B2 | Por lectura del código: `failed()` reemplaza la tabla por «No se pudo cargar el histórico» aunque haya resultados válidos de la misma consulta. | Igual que B2. |

## Casos sin parpadeo visible

- Gráficos del tablero ante un refresco con datos idénticos: los 6 lienzos y sus nodos se conservan (31/31 nodos de KPI, secciones y tablas). Cada gráfico hace un `update()` con los mismos datos y un único `draw()`, sin animación de varios cuadros. No se justifica tocar el componente compartido `chart-view` (Feature 008).
- Barra de progreso: aparece en cada refresco, pero superpuesta (`.results > mat-progress-bar` absoluta o contenedor `.progress` reservado), así que no desplaza el contenido. Se mantiene como aviso legítimo de consulta.
- Respuesta tardía de otro filtro: `httpResource` cancela la petición anterior al cambiar la clave (se observan peticiones `ERR_ABORTED`), así que no se muestra como vigente.

## Vistas equivalentes (FR-019)

- `inventory-page` refresca cada 60 s con `mat-table` sin `trackBy`: es la misma causa que B1. Se incluye en el ajuste porque pertenece a Inventario y el cambio es local.
- `uptime/dashboard-results`, `intermittency/dashboard-facade` y `availability-facade` también refrescan solos. Feature 008 (Intermitencias) está en pruebas: se documenta para coordinarlo con su responsable y no se intervienen sus archivos. Uptime queda registrado para una revisión posterior con su propia evidencia.
