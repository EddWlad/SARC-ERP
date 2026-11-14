# US4 · Resultados (T035–T039)

Fecha: 2026-11-14 · Rama `feat/009-integridad-temporal-consumo` (sin commit). Línea base: [us4-baseline.md](us4-baseline.md).

## Ajustes

| Hallazgo | Ajuste | Archivos |
|---|---|---|
| B1 · filas reemplazadas en cada refresco | `trackBy` por `id`: la tabla conserva las filas y solo actualiza sus datos. Se aplica igual en Inventario (misma causa). | `sessions-page.html/.ts`, `inventory-page.html/.ts` |
| B2/B3 · un refresco fallido vacía la vista | `retainedQuery` recuerda la consulta de cada valor. Si falla el refresco de la misma consulta, las cifras siguen a la vista y aparece el aviso `refresh-alert` con «Reintentar». Si falla una consulta nueva (otro filtro, período o página), el error ocupa la vista, porque el resultado anterior es de otra consulta y no es vigente. | `core/http/retained-value.ts`, `consumption-dashboard-facade.ts`, `sessions-facade.ts`, `dashboard-page.html`, `sessions-page.html`, `shared/ui/_list-page.scss` |
| Gráficos | Sin cambios (T038): con datos equivalentes no hay animación visible. No se toca `chart-view` (Feature 008). | — |

Ajuste de US3 hallado en la misma revisión: a 768 px, la columna de acciones con «Ver» ensanchaba la tabla y recortaba la columna Inicio. Por debajo de 1200 px las acciones se apilan, y la tabla se desplaza en lugar de ocultar contenido (`sessions-page.scss`). Con eso la tabla mide lo mismo que su contenedor a 768, 1024 y 1280 px.

## Antes / después en el navegador (mismo sandbox y recorrido de T034)

| Recorrido | Antes | Después |
|---|---|---|
| Sesiones: refresco con datos idénticos y foco en «Ver» | 0/15 filas conservadas, foco en `BODY` | 15/15 filas conservadas, foco conservado («Ver la sesión SIN-ENERGIA») |
| Sesiones: refresco que falla (503) y luego se recupera | La tabla se reemplaza por «No se pudo cargar el histórico» (lectura de código) | 15/15 filas visibles, aviso «No se pudo actualizar el histórico. Se muestran los resultados anteriores.», que desaparece al recuperarse |
| Tablero: refresco que falla (503) y luego se recupera | KPI y 0/6 lienzos conservados; al recuperarse, 6 lienzos nuevos con animación de entrada | KPI conservados; 6/6 lienzos durante el fallo y después; aviso «No se pudieron actualizar las cifras; se muestran las anteriores. Servicio no disponible.» |
| Tablero: refresco con datos idénticos | 6/6 lienzos, un `update()` y un `draw()` por gráfico | Igual (sin cambios) |
| Respuesta tardía de otro filtro | Cancelada por `httpResource` | Igual; cubierta por prueba |

Contraste del aviso en tema oscuro: texto y botón `rgb(255, 218, 214)` sobre `rgb(147, 0, 10)`, por encima de AA. El botón usa el color del texto, porque el primario no contrasta sobre el contenedor de error.

## Revisión visual de US3 y US4

- 1440 px, tema oscuro: diálogos Ver, Editar y Eliminar. La edición de 8 a 9 kWh de la sesión 1211 llevó el total de 119,50 a 120,50 kWh; la baja de QA-13 lo dejó en 15 sesiones y 109,50 kWh. Se verificaron la cancelación, el aviso de cambios sin guardar y el menú operable por teclado.
- 768 px, tema oscuro: tabla sin recorte y diálogo Ver con «Versiones anteriores» (Edición, motivo, revisión 1 con 8,00 kWh).
- 360 px, tema claro: tarjetas con Ver, Editar y Eliminar, sin desplazamiento horizontal. El diálogo de edición (9–351 px) muestra los campos apilados, cada extremo con su zona («Inicio (UTC+00:00)», «Término (UTC-05:00)») y el foco visible.
- La matriz de roles (Gerente solo «Ver») se verificó con pruebas de componente y API. No se repitió en el navegador.

## Pruebas automatizadas

- `retained-value.spec.ts`: vigencia por consulta, refresco fallido, consulta nueva y clave equivalente.
- `consumption-dashboard-facade.spec.ts` (nuevo, 6 pruebas): datos equivalentes y nuevos, refresco fallido, consulta nueva fallida, respuesta tardía descartada, y refresco con persona activa que se detiene tras el corte por inactividad.
- `sessions-facade.spec.ts`: refresco fallido de la misma consulta frente a una consulta nueva.
- `sessions-page.spec.ts`: filas y foco conservados con un refresco, y lista visible con el aviso.
- `dashboard-page.spec.ts`: KPI y gráficos conservados con el aviso de refresco.
- Frontend: 90 archivos y 506 pruebas correctas; `format:check` correcto; build inicial de 347,75 kB.

## Pendientes

- SC-006 queda acreditado para los hallazgos reproducidos (B1–B3). El síntoma original de producción no tenía una descripción visual precisa. Si persiste algún parpadeo distinto, se debe registrar con su pantalla y su acción.
- Uptime (`dashboard-results` sin `trackBy` con refresco automático) e Intermitencias (Feature 008) muestran el mismo patrón de error que B2. Se registran para coordinarlos con sus responsables; no se modificaron.
