# Armonía visual de formularios y cabeceras (T044–T047)

Fecha: 2026-11-14 · Rama `feat/009-integridad-temporal-consumo` (sin commit). Pedido del responsable con capturas de Intermitencias (Registrar, Importaciones, Revisión, Ventanas) y del diálogo «Editar sesión»: campos de distinto alto, filas descuadradas, botón de cabecera mal ubicado y campos demasiado grandes desde el ingreso.

## Diagnóstico (medido en el navegador, sandboxes `Feature008BrowserSandbox` y `Feature009BrowserSandbox`, 1366×768)

| Hallazgo | Medida | Causa |
|---|---|---|
| Campos demasiado grandes en el ingreso, el cambio de clave y los diálogos con formulario (usuario, equipo, horario, importación, revisión de energía, editar sesión) | Contorno de 71px en el ingreso y de 66px en «Editar sesión», frente a los 56px de Material | `.sarc-stacked-fields` fija `min-height: 75px` al `mat-form-field`. El contorno (`.mat-mdc-text-field-wrapper`, `flex: 1 1 auto`) crecía con él. |
| Ventanas: «Primer día» y «Último día» más altos que «Punto de carga KIA» | Contornos de 91px frente a 56px | La ayuda de dos líneas de «Estado» alarga la fila de la rejilla, que estira a los demás campos, y el contorno crecía con ellos. |
| Etiquetas mezcladas: unas dentro del campo y otras sobre el borde | Visual (Registrar, Ventanas, diálogos) | Comportamiento por defecto de Material: los campos de fecha y hora siempre la suben y los de texto solo al enfocarlos. |
| «Nueva importación» de Intermitencias (y «Marcar prueba por punto») bajo la descripción y a la izquierda | Visual | `.page-header` con `flex-wrap`: una descripción larga empujaba las acciones a otra línea, alineadas a la izquierda. |
| Registrar intermitencias: margen inferior de cada ventana mayor que el superior | 44px abajo frente a 12px arriba | La región de duración y avisos reservaba 24px aunque estuviera vacía. |
| Consumo/Sesiones e Inventario: «Buscar» o «Estado de uso» solos en una segunda fila | Visual | La columna mínima de los filtros (150 y 160px) dejaba 6 columnas para 7 campos. |
| Botón «Ingresar» de 48px frente a los 40px del sistema | Medido | Alto fijo propio en `auth-form.scss`. |

## Ajustes

- `design-system/_base.scss`: `.mat-mdc-form-field > .mat-mdc-text-field-wrapper { flex: none; }`. Todos los contornos miden 56px y el espacio sobrante queda debajo. Así se corrigen a la vez el ingreso, los diálogos y las filas de filtros.
- Las 144 instancias de `mat-form-field` declaran `floatLabel="always"`. Se descartó `MAT_FORM_FIELD_DEFAULT_OPTIONS` en `app.config.ts`, porque subía el bundle inicial de 347,8 kB a 531 kB, por encima del límite de 500 kB.
- `shared/ui/_page-chrome.scss`: el bloque de título reserva 28rem y crece. Las acciones (`margin-inline-start: auto`) quedan a la derecha aunque pasen a otra línea, como en Inventario, donde las cinco acciones no caben junto al título. Esto aplica a las cabeceras de todos los módulos.
- `capture-page.ts`: la región de duración y avisos vacía no reserva espacio, pero sigue en el DOM para anunciar cambios.
- Filtros de Consumo, Uptime, Intermitencias e Inventario: la columna mínima pasa a 136px. A 1366px los siete filtros de Sesiones y los seis de Inventario caben en una fila.
- `auth-form.scss`: «Ingresar» con el alto del sistema (40px).
- Se quitaron dos `align-items: flex-start` redundantes (detalle de equipo y cabecera de lote).
- `DESIGN.md`: se documentan la regla de la etiqueta arriba, el alto fijo del contorno y la cabecera.

## Verificación

- Medición tras los ajustes en 17 rutas (inicio, inventario, tipos, cargas, consumo, uptime, intermitencias, reportes, usuarios): todos los contornos miden 56px. Las únicas excepciones son los selectores de «Filas por página» del paginador (41px, componente de Material). No hay desplazamiento horizontal y las acciones de cabecera quedan alineadas a la derecha (distancia 0).
- Recorrido visual en tema oscuro y claro a 1366×768: ingreso, Registrar, Importaciones, Revisión, Ventanas, Días revisados, Sesiones, Inventario, Usuarios y Reportes. También los diálogos «Nuevo usuario», «Nuevo equipo», «Corregir la ventana» y «Editar sesión», este con contornos de 56px.
- A 360px (tema claro): Importaciones y Sesiones sin desplazamiento horizontal. Las acciones de cabecera pasan bajo el título.
- `npm run lint` y `format:check` correctos; `npm test`, 90 archivos y 506 pruebas correctas. El build inicial queda en 347,80 kB.
- Presupuesto de estilos por componente: siguen tres advertencias que ya existían antes de este cambio (`measurement-page.ts`, `import-batch-page.ts` de intermitencias y `dashboard-page.scss` de consumo). Con el ajuste de la cabecera sumaron 77 bytes cada una. No aparece ninguna advertencia nueva.

## Registrar intermitencias: rejilla de cada ventana (2026-11-14, segunda revisión)

Hallazgo del responsable, con captura a 1366 px. Inicio, Fin y Comentario estaban en una fila, y «Fin en otra fecha» en otra fila junto a las casillas, con ancho propio. Por eso no se alineaba con ninguna columna. Punto y Día seguían otra proporción (2:1), y al marcar «Sin fin» el comentario se movía a la izquierda.

Ajuste (`capture-page.ts`):

- Una sola rejilla para Punto/Día y para cada ventana: Inicio | Fin | Fin en otra fecha | Comentario | quitar (1 : 1 : 1,25 : 2 : 40px). Punto cubre las tres primeras columnas y Día queda sobre Comentario. El encabezado se sangra lo mismo que el borde de la ventana.
- Cada campo tiene su celda: «Sin fin» oculta Fin y su fecha sin mover el comentario.
- Las casillas van en su propia fila, con el borde alineado al contorno de Inicio (se compensan los 11px del área táctil de Material).
- Consultas de contenedor según el ancho del formulario: 760px o menos, Comentario pasa a la segunda fila; 480px o menos, Inicio y Fin quedan arriba y la fecha y el comentario ocupan todo el ancho. Una ventana abierta no deja una fila vacía.
- Para respetar el presupuesto de 4 kB de estilos del componente, las áreas usan nombres de una letra, documentados en el código.

Verificación en el sandbox de Feature 008: a 1366 px, Punto 310–891 = Inicio…fecha, Día 903–1245 = Comentario, casilla en 310 y contornos de 56px. La ventana con «Sin fin» conserva Comentario en 903–1245. A 768 px (tema oscuro) y a 375 px (tema claro) los bordes coinciden, sin desplazamiento horizontal. `lint`, `format:check` y `ng test` (90 archivos) dan correcto. El build inicial queda en 347,80 kB, sin advertencias nuevas.

## Lote de intermitencias: filas, decisiones y motivos (2026-11-14)

Hallazgos del responsable al revisar `Intermitencia_copiamos_nube (2).xlsx`:

| Hallazgo | Causa | Ajuste |
|---|---|---|
| «Corregir» y «Resolver» pegados | `.decision` del parcial compartido no tiene separación | En el lote de intermitencias, los botones van apilados, con el mismo ancho y 8px de separación. En la tarjeta del teléfono quedan lado a lado, a todo el ancho. |
| La fila, la clasificación y los botones flotan en medio de filas altas | Celdas de Material centradas verticalmente | Las celdas se alinean arriba, junto al Par 1. |
| «Original: 1899-12-31 09:05:00» | Excel guarda una hora sin fecha como un instante de 1899, y el importador conserva ese texto | `sourceCellText` muestra solo la hora. Se aplica en el lote, el editor de filas y el panel de decisión. |
| «El nombre del archivo difiere del actual» no se entendía y aparecía en rojo | El aviso compara el nombre del archivo con el de Inventario, pero no bloquea, porque la fila se vincula por el código | Texto nuevo: «El nombre en el archivo no coincide con el de Inventario (se vincula por el código)», en color neutro. |
| Motivos sin tildes | La interfaz mostraba el `message` del servidor | `issueText` usa las etiquetas de la interfaz y recurre al servidor solo para códigos desconocidos. |

**Duplicado y solape: el diagnóstico es correcto.** En el archivo, las filas 19 y 20 repiten los dos puntos de las filas 16 y 17 con la **misma fecha, 13/11/2026**. Las ventanas son idénticas o difieren en 1 minuto (16:28 frente a 16:29, 06:53 frente a 06:54). Las filas del 12/11 (2 a 13) son de otros puntos. Si esas filas corresponden al 12/11, hay que corregir su fecha («Corregir») u omitirlas.

Verificación: sandbox de Feature 008 con un lote pegado que reproduce el caso (filas repetidas el 13/11 y un nombre distinto), a 1366px en tema oscuro y a 375px en tema claro, sin desplazamiento horizontal. Frontend: 509 pruebas (3 nuevas), `lint` y `format:check` correctos. El build inicial es de 347,80 kB. La advertencia de estilos de `import-batch-page.ts`, que ya existía, pasa de +291 a +525 bytes (el límite de error es 8 kB). Backend: `IntermittencyImportApiTest` 28/28, `IntermittencyImportConcurrencyTest` 2/2, `ImportRowInterpreterTest` 21/21 y `OpenApiContractTest` correctos.

## Filtros de período: campos desfasados por una ayuda (2026-11-14)

Hallazgo del responsable: en el tablero de Intermitencias, con «Rango personalizado», el contorno de «Último día (incluido)» quedaba 10px más arriba que «Primer día» y «Puntos KIA».

**Causa.** La fila de filtros usaba `align-items: center`. «Último día» lleva la ayuda «Hasta 366 días», así que su celda es más alta y el centrado subía el contorno. La misma regla estaba en las barras de filtros de Uptime y de Consumo.

**Ajuste.** En `intermittency-filter-bar`, `uptime-filter-bar` y `consumption-filter-bar`, los campos se alinean arriba (`align-items: start`). «Limpiar filtros» (40px) baja 8px para quedar centrado sobre el contorno de 56px.

**Verificación.** Auditoría automática en el sandbox de Feature 008, a 1366×768, en 15 rutas (Intermitencias, Uptime, Inventario, Consumo, Reportes y Usuarios) y en cada chip de período (35 estados en total). Revisa contornos de alto distinto de 56px, campos de una misma fila con desfase mayor de 1px y botones descentrados respecto de sus campos. Resultado: 0 hallazgos. Para validar la auditoría, se restableció `align-items: center` en el navegador y detectó el desfase de 10px entre «Primer día», «Último día» y «Puntos KIA». A 375px (tema claro) no hay hallazgos ni desplazamiento horizontal. Frontend: 509 pruebas, `lint` y `format:check` correctos, build inicial de 347,80 kB, sin advertencias nuevas.

## Pendiente menor

- A 360px, el último filtro de las barras con número impar de campos (por ejemplo «Buscar» en Sesiones) ocupa media fila. En el teléfono solo se consulta, y resolverlo exige estilos sobre contenido proyectado; queda anotado.
