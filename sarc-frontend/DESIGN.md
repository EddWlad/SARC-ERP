---
name: SARC
description: Sala de control del consumo de carga: cifras grandes, estado siempre visible, color reservado para lo que exige atención.
colors:
  primary: "#005cbb"
  on-primary: "#ffffff"
  primary-container: "#d7e3ff"
  on-primary-container: "#00458f"
  secondary-container: "#dae2f9"
  on-secondary-container: "#3e4759"
  tertiary: "#343dff"
  tertiary-container: "#e0e0ff"
  on-tertiary-container: "#0000ef"
  error: "#ba1a1a"
  error-container: "#ffdad6"
  on-error-container: "#93000a"
  surface: "#faf9fd"
  surface-container-lowest: "#ffffff"
  surface-container-low: "#f4f3f6"
  surface-container: "#efedf0"
  surface-container-high: "#e9e7eb"
  surface-container-highest: "#e3e2e6"
  on-surface: "#1a1b1f"
  on-surface-variant: "#44474e"
  outline: "#74777f"
  outline-variant: "#c4c6d0"
  series-1: "#2a78d6"
  series-2: "#eb6834"
  series-3: "#1baf7a"
typography:
  display:
    fontFamily: "Segoe UI, Roboto, Helvetica Neue, Arial, system-ui, sans-serif"
    fontSize: "2.25rem"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "0"
  headline:
    fontFamily: "Segoe UI, Roboto, Helvetica Neue, Arial, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 400
    lineHeight: "2rem"
    letterSpacing: "0"
  title:
    fontFamily: "Segoe UI, Roboto, Helvetica Neue, Arial, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 500
    lineHeight: "1.5rem"
    letterSpacing: "0.009rem"
  body:
    fontFamily: "Segoe UI, Roboto, Helvetica Neue, Arial, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: "1.25rem"
    letterSpacing: "0.016rem"
  label:
    fontFamily: "Segoe UI, Roboto, Helvetica Neue, Arial, system-ui, sans-serif"
    fontSize: "0.688rem"
    fontWeight: 500
    lineHeight: "1rem"
    letterSpacing: "0.031rem"
  mono:
    fontFamily: "ui-monospace, Cascadia Mono, Consolas, monospace"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: "1.25rem"
    letterSpacing: "0"
rounded:
  sm: "8px"
  md: "12px"
  lg: "16px"
spacing:
  1: "4px"
  2: "8px"
  3: "12px"
  4: "16px"
  5: "24px"
  6: "32px"
components:
  button-filled:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label}"
    rounded: "999px"
    height: "40px"
  button-tonal:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    typography: "{typography.label}"
    rounded: "999px"
    height: "40px"
  button-outlined:
    backgroundColor: "transparent"
    textColor: "{colors.primary}"
    typography: "{typography.label}"
    rounded: "999px"
    height: "40px"
  badge:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.label}"
    rounded: "999px"
    padding: "2px 8px"
  badge-warn:
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.on-error-container}"
    typography: "{typography.label}"
    rounded: "999px"
    padding: "2px 8px"
  badge-off:
    backgroundColor: "{colors.surface-container-highest}"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.label}"
    rounded: "999px"
    padding: "2px 8px"
  kpi-tile:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.display}"
    rounded: "{rounded.md}"
    padding: "16px"
  panel:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "16px"
  results-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.md}"
  nav-link:
    backgroundColor: "transparent"
    textColor: "{colors.on-surface-variant}"
    typography: "{typography.title}"
    rounded: "{rounded.lg}"
    padding: "12px 16px"
  nav-link-active:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.on-secondary-container}"
    rounded: "{rounded.lg}"
    padding: "12px 16px"
  form-alert:
    backgroundColor: "{colors.error-container}"
    textColor: "{colors.on-error-container}"
    rounded: "{rounded.sm}"
    padding: "12px"
---

# Design System: SARC

## Overview

**Creative North Star: "La Sala de Control"**

SARC se lee de un vistazo o no sirve. Quien abre el tablero de consumo necesita saber, en el primer
segundo, cuánta energía es oficial, cuántas sesiones hay detrás y qué quedó fuera de los
indicadores. De ahí la cifra grande en contenedor de marca, el estado del sistema siempre presente
y una regla de color estricta: el azul de marca y el rojo de error no decoran, señalan. Todo lo que
no es cifra ni estado baja de tono.

La densidad es alta pero jerárquica. La aplicación es una herramienta de trabajo diario sobre
laptops corporativas de 1366×768, con sesiones largas de importación y revisión; cada pantalla
sigue la misma estructura — cabecera, filtros, resultados, estados vacío y error — para que el
usuario aprenda la forma una vez y la reconozca en los seis módulos. Los colores no se escriben a
mano: salen de los roles `--mat-sys-*` que genera `mat.theme()` en
`src/app/design-system/_theme.scss`, y el espaciado, los radios y las medidas de layout salen de
los tokens `--sarc-*` en `_tokens.scss`. Un valor literal en un componente es una fuga del sistema,
no una excepción.

El tema claro y el oscuro son el mismo diseño: `theme-type: color-scheme` resuelve cada rol con
`light-dark()`, así que el modo se cambia con la propiedad `color-scheme` del `<html>` y ningún
componente duplica reglas por tema. Un color que no pase por un rol de Material se rompe en modo
oscuro.

**Key Characteristics:**

- Cifra primero: el indicador es el objeto más grande de la pantalla, no el título.
- Estado etiquetado: toda sesión, lote o equipo lleva su píldora de estado con texto, nunca solo color.
- Estructura repetida: cabecera, filtros, resultados y estados en el mismo orden en cada listado.
- Color escaso: azul de marca para cifras y enlaces, rojo para error, gris para lo accesorio.
- Un solo juego de roles: `--mat-sys-*` para color, `--sarc-*` para espacio, radio, motion y series.
- Claro y oscuro desde el mismo CSS, por `light-dark()`.

## Colors

Paleta azure de Material 3 con tercearios azul-violeta, aplicada por roles semánticos y resuelta en
claro y oscuro con `light-dark()`. El frontmatter registra los valores del modo claro; los del modo
oscuro viven en `.impeccable/design.json` (`extensions.colorMeta[*].darkValue`).

### Primary

- **Azul Operación** (`--mat-sys-primary`): la marca. Cifra de la columna fuerte en un panel
  (`.metric-row strong`), total de energía de un ranking, nombre de archivo enlazado en el historial
  de importaciones, logotipo «SARC» de la barra lateral y de la tarjeta de ingreso, anillo de foco.
- **Azul Tablero** (`--mat-sys-primary-container` / `--mat-sys-on-primary-container`): fondo de la
  baldosa de indicador (`.kpi-tile`) y de la píldora de estado neutra. Es el único bloque de color
  saturado que ocupa área grande en la aplicación.

### Secondary

- **Azul Acero** (`--mat-sys-secondary-container` / `--mat-sys-on-secondary-container`): ítem activo
  del menú lateral, botones `tonal` (la acción secundaria más usada: reintentar, abrir diálogo) y
  píldora `badge-info` de un lote listo.

### Tertiary

- **Índigo Señal** (`--mat-sys-tertiary-container` / `--mat-sys-on-tertiary-container`): píldora
  `badge-accent`, reservada a dos estados concretos: lote completado y sesión que espera aprobación
  de fuente. Distingue «terminó bien» de «falta algo» sin usar el rojo.

### Neutral

- **Superficie** (`--mat-sys-surface`): fondo de la aplicación y de la tarjeta de resultados.
- **Superficie Hundida** (`--mat-sys-surface-container-low`): barra lateral y panel de tablero. Un
  nivel por debajo de la superficie: así el panel se percibe como un contenedor, no como una tarjeta
  flotante.
- **Superficie Alta** (`--mat-sys-surface-container-high`): hover de un ítem de menú.
- **Superficie Máxima** (`--mat-sys-surface-container-highest`): fondo de la píldora apagada
  (`badge-off`) de un estado inactivo, descartado o de prueba.
- **Tinta** (`--mat-sys-on-surface`): texto principal.
- **Tinta Atenuada** (`--mat-sys-on-surface-variant`): texto secundario, título de panel, texto de
  estado vacío, nota aclaratoria, detalle de incidencia en una fila importada. Es el color de todo
  lo que acompaña a la cifra.
- **Trazo** (`--mat-sys-outline-variant`): borde de 1px de tarjeta de resultados, panel, separador
  entre filas de un ranking y entre tarjetas de un listado en teléfono.

### Error

- **Rojo Rechazo** (`--mat-sys-error-container` / `--mat-sys-on-error-container`): alerta de error
  del backend dentro de un formulario (`.form-alert`), píldora `badge-warn` de lote fallido, de
  sesión pendiente de vincular y de energía por revisar. `--mat-sys-error` queda para el texto de
  error de un campo.

### Series de gráficas

- **Azul Serie** (`--sarc-series-1`), **Naranja Serie** (`--sarc-series-2`), **Verde Serie**
  (`--sarc-series-3`): paleta categórica validada para daltonismo y contraste en claro y oscuro. El
  orden es fijo: cada categoría conserva su color entre gráficas y entre pantallas.

### Named Rules

**La Regla de la Señal.** El azul de marca marca cifras, enlaces y foco; nunca rellena un área
decorativa. La baldosa de indicador es la única excepción autorizada, y lo es porque la cifra *es*
el contenido.

**La Regla del Par.** Un color de fondo solo se usa junto a su `on-*` del mismo rol
(`primary-container` con `on-primary-container`). Mezclar pares rompe el contraste en modo oscuro
sin avisar en claro.

**La Regla del Estado Escrito.** Ningún estado se comunica solo con color: la píldora lleva su
texto en español y, cuando hace falta, su `title` explicativo. El color refuerza, no informa.

**La Regla de la Serie Fija.** Una categoría conserva su `--sarc-series-*` en todas las gráficas.
Reasignar colores por posición en el conjunto de datos hace que dos gráficas de la misma pantalla
se contradigan.

## Typography

**Familia única:** Segoe UI con Roboto, Helvetica Neue, Arial y `system-ui` de respaldo
(`--sarc-font-family`), aplicada a la vez como `plain-family` y `brand-family` del tema. Una sola
familia: SARC no tiene voz tipográfica de marca propia todavía y la fuente del sistema es la que
mejor rinde en equipos corporativos sin descarga.

**Monoespaciada:** `ui-monospace`, Cascadia Mono, Consolas, para identificadores literales —serie,
UID/PUK, clave externa de una fila importada—, donde distinguir `0` de `O` es parte del trabajo.

**Carácter:** neutra y de oficina, sin aspiración editorial. El peso hace la jerarquía; el tamaño
la escala. El sistema no usa mayúsculas forzadas ni `letter-spacing` expresivo: las que hay son las
del `typescale` de Material 3.

### Hierarchy

- **Display** (700, 2.25rem/1): cifra de indicador en el tablero (`.kpi-value`). Peso 700 sobre el
  regular de Material: a esta escala el 400 se lee frágil junto al rótulo. Baja a 1.75rem en
  teléfono (≤599.98px) para que la cifra no se parta en dos columnas.
- **Headline** (400, 1.5rem/2rem): `h1` de pantalla y cifra fuerte de una fila de panel
  (`.metric-row strong`, en peso 700). Es el techo tipográfico dentro de la aplicación.
- **Title** (500, 1rem/1.5rem): título de la barra superior, acceso directo de inicio, unidad al
  lado de la cifra (`.kpi-unit`), total de un ranking. El `title-large` (1.375rem) queda para el
  logotipo «SARC».
- **Title Small** (500, 0.875rem/1.25rem): rótulo de indicador (`.kpi-label`) y encabezado `h2` de
  panel, este último en tinta atenuada. Un panel se titula en pequeño: el contenido manda.
- **Body** (400, 0.875rem/1.25rem): texto corriente, nota aclaratoria, datos de usuario.
  `body-large` (1rem) para la fila etiqueta-cifra de un panel; `body-small` (0.75rem) para el
  detalle atenuado: incidencia de una fila, roles del usuario, dato secundario de una tarjeta.
- **Label** (500, 0.688rem/1rem): texto de la píldora de estado. `label-large` (0.875rem) para el
  ítem de menú y el texto de botón.

### Named Rules

**La Regla del `font` Compuesto.** La tipografía se aplica con la propiedad abreviada y un token
completo (`font: var(--mat-sys-title-small)`), no con `font-size` y `font-weight` separados. Un solo
token trae tamaño, peso, interlínea y tracking coherentes; partirlo los desincroniza.

**La Regla del Literal Monoespaciado.** Serie, UID/PUK y clave externa van en monoespaciada con
`overflow-wrap: anywhere`. Son datos que se comparan carácter por carácter y que llegan sin límite
de longitud desde un Excel ajeno.

## Layout

Shell fijo: barra lateral de 256px (`--sarc-sidebar-width`) con borde derecho de 1px, barra superior
pegajosa de 64px (`--sarc-topbar-height`) y contenido centrado con ancho máximo de 1280px
(`--sarc-content-max-width`) y relleno de 24px, que baja a 16px en teléfono. Un enlace de salto
(`.skip-link`) precede al contenido y aparece al recibir foco.

Ritmo de espaciado en una escala de seis pasos (4, 8, 12, 16, 24, 32px). El uso real es estrecho y
vale como convención: 24px separa la cabecera de pantalla del contenido, 16px separa bloques dentro
de una pantalla, 12px separa controles de filtro entre sí, 8px separa acciones adyacentes, 4px
separa una cifra de su rótulo.

Cada listado se compone con el mismo parcial (`shared/ui/_list-page.scss`): `.page-header` con
título, descripción y acciones a la derecha (el título reserva 28rem; si las acciones no caben, pasan
a la línea siguiente sin dejar de estar a la derecha); `.filters` en una rejilla cuyas columnas define cada
pantalla con `--sarc-filter-columns`; `.results` como tarjeta con borde; `.state` para vacío y
error, centrado con su icono y su acción de reintento.

Tres puntos de quiebre, todos descendentes:

- **≤1023.98px** — la rejilla de gráficas del tablero pasa de tres columnas a dos.
- **≤767.98px** — ancho de teléfono (`injectIsHandset`, `shared/ui/handset.ts`): los filtros se
  apilan en una columna, las tablas de listado se sustituyen por tarjetas (`.cards` / `.card`) y las
  gráficas quedan en una columna. Es el único quiebre que cambia el componente, no solo la rejilla.
- **≤599.98px** — relleno de página de 24px a 16px, título de barra superior a `title-small`, cifra
  de indicador a `headline-medium`, y la columna de sesiones del ranking se oculta.

### Named Rules

**La Regla de la Tabla que no se Aprieta.** Una tabla con muchas columnas no se comprime: se
desplaza. La vista previa de un lote fija `min-width: 860px` dentro de un contenedor
`overflow-x: auto`. Reducir columnas o tipografía para que quepa falsea la lectura del dato.

**La Regla de la Tarjeta en Teléfono.** Por debajo de 768px un listado deja de ser tabla y pasa a
tarjetas con cabecera, cuerpo y pie. No hay tabla responsiva intermedia.

**La Regla de la Rejilla Declarada.** Las columnas de filtros se declaran por pantalla en
`--sarc-filter-columns`; el parcial no las adivina. En teléfono siempre colapsan a una.

## Elevation & Depth

La jerarquía se construye con elevación: un contenedor que agrupa dato se levanta de la superficie
para que el ojo lo separe del fondo antes de leerlo. La escala es la de Material
(`--mat-sys-level0` a `--mat-sys-level5`), y el nivel comunica distancia al fondo, no importancia
retórica: `level1` para contenedores de contenido en reposo, `level2` para una superficie que flota
sobre el fondo de la pantalla, `level3` y superiores solo para overlays de Material (diálogo, menú,
snackbar), que los traen puestos.

Estado actual de la implementación: hoy solo la tarjeta de ingreso (`auth-card`) usa sombra
(`--mat-sys-level2`); la tarjeta de resultados, el panel de tablero y las tarjetas de listado se
separan con borde de 1px en `--mat-sys-outline-variant` y con un escalón de superficie tonal
(`surface-container-low` bajo `surface`). Esa doble vía —borde en la aplicación, sombra en el
ingreso— es inconsistente con la doctrina de arriba y el sistema la resuelve hacia la elevación.

El escalón tonal se mantiene en cualquier caso: en modo oscuro la sombra casi no se ve, y el
contraste entre `surface` y `surface-container-low` es lo que sostiene la separación.

### Shadow Vocabulary

- **Contenedor en reposo** (`box-shadow: var(--mat-sys-level1)`): tarjeta de resultados, panel de
  tablero, tarjeta de listado en teléfono. Nivel más bajo que admita separación.
- **Superficie flotante** (`box-shadow: var(--mat-sys-level2)`, compuesta:
  `0 3px 3px -2px rgba(0,0,0,.2), 0 3px 4px 0 rgba(0,0,0,.14), 0 1px 8px 0 rgba(0,0,0,.12)`):
  tarjeta de ingreso y cambio de clave, sobre `surface-container`.
- **Overlay** (`--mat-sys-level3` y superiores): lo aplica Material en diálogo, menú y snackbar. No
  se replica a mano.

### Named Rules

**La Regla del Nivel Único.** Un contenedor tiene un nivel, no una sombra propia. Si hace falta una
`box-shadow` literal en un componente, falta un nivel en la escala o el componente está en el nivel
equivocado.

**La Regla del Tono Primero.** La elevación acompaña al escalón tonal, no lo sustituye. Un panel
elevado sigue estando en `surface-container-low`: en modo oscuro el tono es lo único que separa.

**La Regla de la Baldosa Plana.** La baldosa de indicador (`.kpi-tile`) no se eleva ni se bordea.
Su contenedor de color ya la separa del fondo; añadir sombra la convierte en botón.

## Shapes

Tres radios (`--sarc-radius-sm` 8px, `md` 12px, `lg` 16px) y una píldora literal (999px). La
asignación es por función, no por tamaño: 8px para piezas insertadas dentro de un bloque (alerta de
formulario, enlace de salto), 12px para contenedores de datos (tarjeta de resultados, panel,
baldosa de indicador), 16px para el ítem de menú lateral, y píldora para todo lo que señala estado
(`.badge`) o dispara una acción (los botones de Material 3, que ya vienen en píldora).

El trazo es siempre de 1px en `--mat-sys-outline-variant`, y se usa tanto para cerrar un contenedor
como para separar filas hermanas (`& + .metric-row`, `& + li`, `.card`). No hay bordes de otro
grosor ni separadores de otro color.

Los campos de formulario son `appearance="outline"` sin excepción: 60 instancias en el código, cero
en `fill`. El contorno del campo es parte de la forma del sistema.

### Named Rules

**La Regla del Trazo Único.** 1px, `outline-variant`, siempre. Un borde más grueso o más oscuro
grita sin informar.

**La Regla del Radio por Función.** El radio dice qué es la pieza: insertada (8px), contenedora
(12px), navegable (16px), de estado o de acción (píldora). No se elige por el tamaño del elemento.

## Components

### Buttons

- **Forma:** píldora de Material 3, 40px de alto.
- **Filled** (`matButton="filled"`): acción principal de un formulario o diálogo — ingresar,
  guardar, confirmar. Una sola por vista.
- **Tonal** (`matButton="tonal"`): la variante más usada del sistema (26 instancias). Acción
  secundaria con peso: reintentar tras un error, abrir un diálogo, aplicar una decisión en lote.
- **Outlined** (`matButton="outlined"`): acción de descarga o salida de la pantalla — Reporte PDF,
  Excel. Vive en `.header-actions`, a la derecha de la cabecera.
- **Icon** (`matIconButton`): acciones de fila y sufijos de campo (mostrar contraseña). Siempre con
  `aria-label` y, si alterna, `aria-pressed`.
- **Icono + texto:** `.mdc-button__label` se fuerza a `inline-flex` centrado con 8px de separación;
  un SVG en línea se alinearía a la línea base y quedaría desplazado.
- **Estado ocupado:** el botón no se reemplaza por un spinner. Se deshabilita y su texto cambia
  («Generando...», «Ingresando...»), para que la acción siga siendo legible.

### Badges (píldoras de estado)

- **Estilo:** píldora de 999px, relleno `2px 8px`, `label-small`, `white-space: nowrap`.
- **Variantes por significado**, siempre en pares contenedor/`on-`: neutra
  (`primary-container`, estado por defecto), `badge-info` (`secondary-container`, en curso o listo),
  `badge-accent` (`tertiary-container`, completado o esperando aprobación de fuente), `badge-warn`
  (`error-container`, fallido, pendiente de vincular, energía por revisar), `badge-off`
  (`surface-container-highest`, inactivo, descartado, de prueba, energía rechazada).
- **Comportamiento:** el componente que la usa (`sarc-session-state-badge`,
  `sarc-import-status-badge`) mapea el estado del dominio a la variante y añade el `title` con la
  explicación. La píldora nunca se escribe suelta en una plantilla.

### Cards / Containers

- **Tarjeta de resultados** (`.results`): radio 12px, fondo `surface`, borde 1px, `overflow: hidden`
  para que la tabla no se escape del radio. Envuelve la tabla o la lista de tarjetas.
- **Panel de tablero** (`.panel`): radio 12px, fondo `surface-container-low`, borde 1px, relleno
  16px, `min-width: 0` para poder encogerse dentro de una rejilla. Su `h2` va en `title-small`
  atenuado.
- **Tarjeta de listado en teléfono** (`.card`): sin radio propio; se apila con separador inferior de
  1px. Cabecera con nombre y estado, cuerpo con datos, pie con la cifra en azul de marca.

### Baldosa de indicador (`.kpi-tile`)

El componente firma del sistema. Rejilla `auto-fit` con mínimo de 160px, radio 12px, relleno 16px,
fondo `primary-container`. Dentro: la cifra en `display-small` peso 700 e interlínea 1, su unidad al
lado en `title-medium`, y el rótulo debajo en `title-small`. `overflow-wrap: anywhere` en la cifra y
reducción a `headline-medium` en teléfono: una cifra de energía puede tener siete dígitos y no debe
partirse ni desbordar. Sin sombra y sin borde.

### Inputs / Fields

- **Estilo:** `mat-form-field appearance="outline"`, con `mat-label` siempre presente. Icono de
  prefijo con 8px a la derecha y 12px a la izquierda (`.prefix-icon`).
- **Etiqueta siempre arriba:** todo campo declara `floatLabel="always"`. Los campos de fecha y hora
  la muestran arriba por su marcador nativo; mezclar etiquetas dentro y fuera del contorno descuadra
  las filas. No se usa `MAT_FORM_FIELD_DEFAULT_OPTIONS` en `app.config.ts`: arrastra el módulo de
  campos al bundle inicial (+180 kB).
- **Alto fijo:** el contorno mide siempre 56px. `_base.scss` impide que el contorno crezca cuando una
  rejilla o un alto mínimo estiran el `mat-form-field`; el espacio sobrante (una ayuda de dos líneas
  en el campo vecino) queda debajo.
- **Error:** `mat-error` por cada error del campo, recorriendo `field().errors()` de Signal Forms.
  El mensaje va en español y describe qué corregir.
- **Error del servidor:** no se inyecta en un campo. Va en `.form-alert` al inicio del formulario:
  radio 8px, fondo `error-container`, icono de alerta y `role="alert"`.

### Navigation

- **Barra lateral:** 256px, fondo `surface-container-low`, borde derecho 1px. Logotipo «SARC» con
  icono en azul de marca a la altura de la barra superior (64px).
- **Ítem:** radio 16px, relleno `12px 16px`, `label-large` en tinta atenuada, icono y texto con 12px
  de separación. Hover a `surface-container-high` con transición de 150ms; activo a
  `secondary-container` con su `on-`, sin negrita añadida — el fondo ya lo distingue.
- **Barra superior:** pegajosa, 64px, fondo `surface`, borde inferior 1px. Título truncado con
  elipsis y una sola línea; a la derecha, la ficha de usuario con nombre, correo atenuado y roles en
  `body-small`.
- **Teléfono:** la barra lateral se repliega tras el botón de menú de la barra superior.

### Gráficas (`sarc-chart-view`)

Una serie por gráfica, nunca doble eje: dos medidas de escala distinta van en gráficas separadas.
Altura por `--sarc-chart-height` (260px por defecto, 220px en el ranking de equipos). El lienzo
lleva `role="img"` y `aria-label` descriptivo. Los colores se resuelven leyendo los tokens del DOM
con un elemento sonda, porque `light-dark()` solo lo resuelve el navegador, y se redibujan al
cambiar el tema. Con `prefers-reduced-motion` la gráfica no se anima.

### Diálogos

Material 3 limita el diálogo a 560px; los formularios de varias columnas usan `WIDE_DIALOG`
(760px, `maxWidth: 95vw`). Un `<form>` que envuelve contenido y acciones se declara
`display: contents` para no romper la columna flex: solo el contenido se desplaza y las acciones
quedan siempre visibles en pantallas bajas. Todo diálogo con formulario pasa por
`guardUnsavedChanges`: Esc, clic fuera y Cancelar piden confirmación si hay cambios.

## Do's and Don'ts

### Do:

- **Do** tomar el color de un rol `--mat-sys-*` y el espacio, radio, motion o color de serie de un
  token `--sarc-*`.
- **Do** aplicar tipografía con la abreviada y un token completo:
  `font: var(--mat-sys-body-medium)`.
- **Do** usar un fondo junto a su `on-*` del mismo rol, para que el contraste se sostenga en claro y
  en oscuro.
- **Do** componer un listado nuevo con `@use '../../shared/ui/list-page'` y declarar solo lo propio
  de la pantalla, incluidas sus `--sarc-filter-columns`.
- **Do** acompañar cada estado de una píldora con su texto en español y, si hace falta, su `title`.
- **Do** desplazar horizontalmente una tabla ancha dentro de `overflow-x: auto` con su `min-width`.
- **Do** dar a cada gráfica una sola serie y dejar las cifras visibles en la leyenda o en una tabla,
  no solo en el tooltip.
- **Do** verificar toda pantalla nueva en claro y en oscuro, a 1366×768 y a 360px de ancho.

### Don't:

- **Don't** escribir un color literal —hex, `rgb()` o nombre— en un componente. Si falta un rol,
  falta en `_theme.scss`.
- **Don't** usar `ngClass` ni `ngStyle`: enlaces `class` y `style`.
- **Don't** separar `font-size` de `font-weight` cuando existe un token de `typescale` que trae ambos.
- **Don't** comunicar un estado solo con color, ni asignar colores de serie por posición en el
  conjunto de datos.
- **Don't** poner dos medidas de escala distinta en una gráfica ni usar doble eje.
- **Don't** añadir una `box-shadow` literal a un componente: se usa un nivel
  (`--mat-sys-level1`…`level5`).
- **Don't** elevar ni bordear la baldosa de indicador.
- **Don't** apretar una tabla reduciendo columnas o tipografía para que quepa en una pantalla
  angosta.
- **Don't** usar `appearance="fill"` en un campo: el sistema es `outline` en sus 60 instancias.
- **Don't** importar un módulo de Material con overlay, ni un pipe de `@angular/common`, desde el
  bundle inicial: el presupuesto es de 500 kB y ambos arrastran el CDK o el chunk compartido.

### Composición de uptime (Feature-006)

Cuarta serie categórica: --sarc-series-4, violeta #8259b5 en claro y #bb9bdc en oscuro. Las tres series existentes se conservan. Orden fijo: Disponible (1), Ocupado (2), Con falla (3), Fuera de línea (4). Leyenda textual con minutos y cobertura por estado; el color no es la única identificación. Ausencia no es cero; sin tiempos positivos no se dibuja dona.
