# Auditoría visual y accesibilidad — T076 (casos 45–46)

Fecha: 2026-11-06. Navegador integrado contra el sandbox opt-in `Feature006BrowserSandbox` (PostgreSQL 18 desechable, almacenamiento simulado, cuenta sintética ANALISTA). Frontend en el puerto 4203 con proxy propio; nunca la BD dev. Datos sintéticos QA005/QA006. Revisión focalizada, no certificación externa WCAG 2.2 AA.

## Método

Para cada ancho (360, 390, 768, 1024, 1440) y cada tema (claro y oscuro emulados con `prefers-color-scheme`) se recorrieron las cinco pantallas de Uptime (Tablero, Captura, Revisión, Importaciones, Vinculación) y se midió por script, sobre el contenido de `main`:

- desborde horizontal de la página y de elementos fuera del ancho visible (excepto la barra de pestañas, que se desplaza por diseño);
- controles interactivos menores de 24 × 24 px (WCAG 2.2, 2.5.8), sin contar el `input` interno de `mat-form-field`, cuya área clicable es el campo completo;
- contraste de todo texto visible contra su fondo efectivo (4,5:1; 3:1 para texto grande), sin contar controles deshabilitados.

Resultado: **50 combinaciones (5 anchos × 2 temas × 5 pantallas), 0 desbordes, 0 controles pequeños, 0 textos bajo contraste.** El tema oscuro se confirmó por el fondo computado (`rgb(18, 19, 22)`).

Corrida invalidada: dos pasadas intermedias (claro 1024 y 768) repitieron la misma pantalla en las cinco filas porque el navegador no había terminado de renderizar; se descartaron y se repitieron con arranque limpio. El primer intento con `iframe` tampoco se tomó en cuenta (no cargaba la aplicación).

## Tablero, formulario, tabla, dona parcial y textos largos

- 360 px: el nombre largo de equipo se ajusta sin desbordar; la dona parcial muestra "Ocupado: No informado" con cobertura `0/1` y conserva el cero real de "Con falla". Capturas: `dashboard-light-*.jpg` y `dashboard-dark-*.jpg` (cinco anchos cada una) y `capture-light-*.jpg`.
- El diálogo "Registrar medición" a 360 px mide 328 px de ancho y su contenido no se desplaza horizontalmente (`scrollWidth == clientWidth`). La barra horizontal que aparece en `capture-light-360.jpg` no se reprodujo en esta revisión.
- Pegado de Enerlink en el diálogo, en el navegador: "163 hrs 35 mins - Disponible / 0 hrs 31 mins - Ocupado / 0 hrs 0 mins - Con falla / 3 hrs 54 mins - Fuera de línea" se interpreta como 9815, 31, 0 y 234 minutos, sin error.

## Teclado, foco, ARIA y movimiento

- Tablero: sin botones, enlaces ni pestañas sin nombre accesible; todos los campos con etiqueta; `lang="es-EC"`.
- Las cuatro gráficas son `canvas` con `role="img"` y texto alternativo descriptivo; los encabezados siguen H1 → H2 → H3 sin saltos; hay 18 regiones `aria-live`/`status`/`alert`.
- Foco por teclado con Tab: los enlaces muestran contorno sólido de 2–3 px (`rgb(0, 92, 187)`) y quedan en pantalla. El `mat-select` no pinta contorno propio: su foco lo muestra el borde del campo de Angular Material (comportamiento heredado, no modificado).
- Hay reglas `prefers-reduced-motion` en las hojas de estilo; no se midió la animación de las gráficas con esa preferencia activa.

## Defectos y correcciones

Ninguno propio de 006 que corregir en esta revisión. El defecto encontrado fue funcional, no visual: el pegado con duración antes de la etiqueta se rechazaba; se corrigió en `EnerlinkBlockParser` (ver `implementation-progress.md`).

## Límites

- Todo con datos sintéticos; no certifica el proveedor real ni el archivo real del usuario.
- No se capturó de nuevo la matriz completa de imágenes: se conservan las 15 de Codex y la verificación de esta sesión es por mediciones.
- En la primera pasada no hubo lector de pantalla ni axe, y el contraste se calculó con la fórmula WCAG; axe e Impeccable `detect` se ejecutaron después (sección "Segunda pasada"). Sigue sin haber prueba con lector de pantalla.
- El detalle de medición y el diálogo de conciliación temporal se recorrieron después en el navegador (ver "Recorrido adicional" en `acceptance.md`); en esa pasada solo se revisó funcionamiento, sin repetir la matriz de anchos y temas.

## Segunda pasada: axe, campos de formulario y fechas (2026-11-06)

Sandbox desechable, axe-core 4.14.0 (etiquetas wcag2a/2aa/21a/21aa/22aa y best-practice) inyectado desde un servidor local temporal; sin dependencias nuevas en el proyecto. Impeccable (`detect`) sobre `features/uptime`: solo avisos de tipo `design-system-color` (`rgb(0, 0, 0)` sobre `h1` y `section`, color de analisis estatico, no del producto); sin hallazgos de severidad mayor.

Hallazgos de axe y correcciones:
- `landmark-unique` (3 nodos, Tablero): el bloque de resultados aparece dos veces (oficial e informe sin horario) con los mismos ids y nombres. `DashboardResults` recibe `scope` y `scopeLabel`; ids y nombres accesibles ya no se repiten y no quedan ids duplicados.
- `region` (shell): el nombre de marca quedaba fuera de un landmark; ahora esta dentro de `nav` y la barra superior tiene `role="banner"`. Afecta al shell de todos los modulos, solo marcado.
- `heading-order` (detalle de medicion): `StatesChart` acepta `level`; en el detalle va como nivel 2.
- Resultado final: 0 violaciones en Tablero, Captura (con lista y con dialogo), Revision, Importaciones (con dialogo), Vinculacion, detalle de medicion y dialogo "Conciliar periodo", en oscuro a 1440 y claro a 360 en Captura.

Campos de formulario colapsados:
- "Registrar medicion": los campos de fecha eran de 56 px y el resto de 75 px por el espacio de ayuda reservado; ahora todos son dinamicos (56 px) y alinean.
- "Conciliar periodo": los campos quedaban pegados (la etiqueta del siguiente tocaba al anterior); el contenido pasa a columna con separacion del sistema.
- Captura: los controles arrancan arriba (`align-items: start`) con la altura de un campo, la ayuda se acorta ("Elija el lunes de la semana.") y las columnas minimas suben a 240 px para que "Ultimos 7 dias (provisional)" no se corte. El texto de la etiqueta del bloque pasa a "Bloque de Enerlink (opcional)" para no cortarse a 360 px.
- Defecto funcional de paso: interpretar el bloque borraba las fechas ya digitadas, porque el borrador de la fachada reiniciaba el formulario. `onParse` ahora entrega primero lo digitado; probado en la prueba de componente y en el navegador.

Registro con fechas: Desde/Hasta del conector son fechas sin hora; se guardan como 00:00 de Ecuador al inicio y 00:00 del dia siguiente al fin (Hasta inclusivo). En el navegador, 26/10 a 01/11 con el bloque 163 hrs 35 mins... guardo `2026-10-26T05:00:00Z` a `2026-11-02T05:00:00Z`, `KNOWN_RANGE`, 9815 disponible, 234 fuera de linea, 97,68 %, pendiente de validacion.

Limites: axe solo corre en el DOM renderizado de las pantallas recorridas; no reemplaza una prueba con lector de pantalla. No se repitio la matriz de cinco anchos con axe (solo 1440 oscuro y 360 claro).

## Tercera pasada: colapsos de tablero y diálogos (2026-11-06)

Sandbox desechable (sin BD dev), navegador integrado a 1100 y 360 px, tema oscuro. Capturas del usuario: diálogo "Registrar medición" y leyenda de estados del Tablero.

- Leyenda de `StatesChart`: la cobertura ("1/1 mediciones") caía a una línea aparte con `flex-basis: 100%` y la fila quedaba despegada de su cifra. Ahora cada estado es una grilla de tres columnas (marca, nombre con cobertura debajo, cifra a la derecha) con separadores; altura de fila 53–54 px en los cuatro estados, con y sin "No informado".
- Tablero: los cinco indicadores caben en una fila (mínimo 128 px en lugar de 160 px, antes 4 + 1 huérfano); los tres grupos de cobertura caben en una fila (240 px); los títulos "Capturas con horario real" e "Informe sin horario real" tienen tipografía y separación propias (antes encabezado y nota pegados al panel); el bloque del informe es una columna con separación uniforme y el título "Menor porcentaje del informe" ya no hereda márgenes del navegador.
- "Registrar medición": el bloque de Enerlink tocaba la fila de fechas; ahora hay 16 px entre ambos y 16 px entre filas de campos. "Corregir medición" estaba sin `subscriptSizing="dynamic"` (campos de alturas distintas); corregido con el mismo espaciado.
- Captura: el botón "Consultar" ya no se estira a toda la fila cuando queda solo (ancho mínimo 160 px, alineado a la izquierda); las etiquetas "Validada" e "Informe sin horario registrado" van en una fila con separación y salto de línea en vez de pegadas.
- A 360 px: sin desborde horizontal (0 px), indicadores en dos columnas, leyenda legible.
- Presupuesto de estilos (4 kB por componente): `dashboard-page.scss` quedó dentro del límite sin avisos de compilación.

Pruebas: 54 archivos / 221 pruebas, lint y `format:check` sin hallazgos; build 342,14 kB / 94,99 kB.

Límites: no se repitió axe después de esta pasada (los cambios son de espaciado y disposición, sin cambios de marcado salvo un contenedor `div` para las etiquetas); sigue sin haber prueba con lector de pantalla.

## Cuarta pasada: parpadeo al filtrar y ayudas de campo colapsadas (2026-11-06)

Alcance: todo el frontend, no solo Uptime. Verificado en el sandbox desechable con cuenta sintética de administrador.

- Causa del parpadeo y del salto de desplazamiento: `httpResource` vacía su valor cuando cambian los parámetros (texto, selector, página). Mientras llegaba la respuesta, el listado quedaba en `[]` y los bloques de indicadores desaparecían (`@if (facade.summary())`); la página se encogía, parpadeaba y el contenedor volvía hacia arriba. Nuevo `core/http/retained-value.ts` (`retainedValue`, con prueba) conserva el último valor hasta que llega el nuevo; aplicado a Inventario, Consumo (sesiones, indicadores, opciones de equipo), Reportes emitidos, Usuarios y los lotes de importación (historial y filas). Los facades de Uptime ya no vaciaban datos al recargar.
- Medición en Inventario al escribir en el buscador y borrarlo cuatro veces: los cinco indicadores nunca desaparecieron, las filas nunca llegaron a 0 y la posición de desplazamiento se mantuvo.
- La barra de carga de los listados (`.results > mat-progress-bar`) se superpone al borde superior en vez de insertarse y empujar el contenido 4 px; regla global en `design-system/_base.scss`.
- Ayudas de campo colapsadas: 25 `mat-form-field` de 7 plantillas (importación, equipo, horarios, usuario, revisión de energía, ingreso y cambio de contraseña) seguían con tamaño fijo, de modo que una ayuda de dos líneas se superponía al control siguiente (captura "Nueva importación"). Ahora todos son dinámicos (ya no queda ninguno fijo) y los formularios apilados llevan `sarc-stacked-fields`, que conserva la altura de 75 px por campo del diseño anterior; la ayuda larga crece en vez de superponerse (hueco de 12 px hasta el control siguiente en "Nueva importación").
- Descartado: un `MAT_FORM_FIELD_DEFAULT_OPTIONS` global en `app.config.ts` subía el paquete inicial de 342 a 524 kB (arrastra todo el módulo de campos); se dejó el atributo por campo.

Pruebas: 55 archivos / 224 pruebas, lint y `format:check` sin hallazgos, build 342,42 kB / 95,05 kB sin avisos.

Límites: no se repitió axe; el parpadeo se midió con respuestas rápidas del sandbox (el efecto es mayor con red lenta, pero la causa quedó eliminada); no se recorrieron a mano todos los diálogos, solo importación, equipo y usuario.

## Quinta pasada: indicadores compartidos, axe y diálogos pendientes (2026-11-06)

- Indicadores (`.kpi-grid` compartido): el mínimo por tarjeta baja de 160 px a 128 px. Medido en navegador a 1100 px: Inventario 5 tarjetas en 1 fila (antes 4 + 1), Uptime Tablero 5/1, Consumo 4/1, detalle de medición 4/1. Se retiró la regla local que Uptime tenía para esto.
- axe-core 4.14.0 (wcag2a/2aa/21a/21aa/22aa y best-practice) sobre el DOM renderizado, **0 violaciones**, en: ingreso; Inventario, tipos de conector y detalle de equipo; Usuarios; Consumo (tablero, sesiones, importaciones); Reportes emitidos; Uptime (captura, revisión, importaciones, vinculación) y detalle de medición. Diálogos con axe en 0: nuevo usuario, nuevo equipo, editar equipo, horario, identidad externa, nuevo tipo de conector, excluir medición y conciliar período. Alturas de campo medidas en los diálogos apilados: 75 px (más si la ayuda ocupa dos líneas), sin desborde horizontal.
- No se pudieron abrir en el sandbox (sin datos): diálogo de revisión de energía (no hay sesiones de consumo) y "Corregir medición" (botón deshabilitado en una medición por conciliar). Ambos llevan el mismo cambio (campos dinámicos y, el primero, `sarc-stacked-fields`), sin recorrido visual ni axe.
- Algunas páginas se midieron solo tras activar el render del panel del navegador; las primeras corridas vacías (sin `h1`) se descartaron y se repitieron.
- Siguen sin hacerse: prueba con lector de pantalla y la matriz completa de cinco anchos con axe (en esta pasada, 1100 px, tema oscuro).

Pruebas: 55 archivos / 224 pruebas, lint, `format:check` y build (342,42 kB / 95,05 kB) sin avisos.

## Sexta pasada: identificadores internos y códigos crudos (2026-11-06)

Revisión transversal de lo que ve la persona (plantillas, avisos, alcance de reportes, Excel). Decisiones del responsable: ocultar la huella SHA-256 de la tabla de Reportes emitidos (se ve a pedido con "Ver huella") y quitar la columna "Id interno" de los Excel.

- Alcance de reportes (Consumo, Uptime, Inventario): mostraba el código interno del tipo (`tipo CHARGING_STATION`); ahora usa `DomainLabels` (`tipo Electrolinera`). El respaldo cuando un equipo ya no se puede consultar imprimía su UUID en el alcance (Consumo y Uptime); ahora dice "equipo no disponible".
- Reportes emitidos: la huella (hexadecimal, no un UUID) salía truncada en la tabla y completa en tarjetas; ahora hay un botón "Ver huella"/"Ocultar huella" por fila (`aria-expanded`). El aviso al emitir un informe gerencial incluía el UUID del informe; ahora dice "Informe gerencial emitido con corte …".
- Diálogo de informe gerencial: si el equipo del filtro ya no se puede consultar, el campo mostraba su UUID; ahora "Equipo no disponible".
- Excel: se quitó la columna "Id interno" (UUID del equipo) de la hoja "Equipos" de Inventario y de "Por equipo" de Consumo.
- Revisado y sin cambios: los identificadores de sesión y de identidad externa (datos de Enerlink/Wallbox, no UUID internos) y los enlaces de ruta (`/inventario/<id>`), que no se muestran como texto. Quedan códigos de estado sin traducir en el historial de clasificación temporal de la medición (`outcome`, `precision`) y marca/estado/uso en el alcance de reportes de Inventario; no son identificadores y no se tocaron.

Pruebas: frontend 55 archivos / 225 pruebas, lint, `format:check` y build (342,42 kB) sin avisos; backend: pruebas de reportes, arquitectura, Inventario y Consumo en verde, más una prueba nueva del alcance. En el navegador (sandbox): la fila muestra "Filtros: red KIA, tipo Electrolinera" y la huella aparece solo tras pulsar "Ver huella".
