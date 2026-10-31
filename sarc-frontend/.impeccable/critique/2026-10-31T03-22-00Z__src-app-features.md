---
target: home page, users, inventory, consumption and reports
total_score: 26
max_score: 40
na_heuristics:
p0_count: 0
p1_count: 5
target_identity: "file:C:\\Users\\Admin\\Desktop\\Proyectos\\SARC\\sarc-frontend\\src\\app\\features"
timestamp: 2026-10-31T03-22-00Z
slug: src-app-features
---
`Method: dual-agent (A: revisión de diseño · B: detector + evidencia determinista)`

# Critique — Inicio, Usuarios, Inventario, Consumo, Reportes

## Design Health Score

| # | Heurística | Puntaje | Problema clave |
|---|-----------|-------|-----------|
| 1 | Visibilidad del estado | **3** | `dashboard-page.html:41` evalúa `facade.error()` antes de `facade.summary()`: un fallo transitorio del refresco reemplaza el tablero ya pintado por un bloque de error. El Gerente pierde la pantalla completa en el cierre. |
| 2 | Correspondencia con el mundo real | **3** | `energy-review-dialog.html:43` imprime «Revisión {{ sessionRevision }}» — versionado interno de fila vendido como vocabulario de auditoría. `import-batch-page.html:79` dice «conteo independiente de las clasificaciones»: lenguaje de especificación. |
| 3 | Control y libertad | **2** | `energy-review-dialog` es el único diálogo con formulario sin `guardUnsavedChanges`: Esc destruye una justificación de 500 caracteres sin preguntar. Cero `queryParams` en todo `features/`: volver atrás desde una ficha de equipo destruye el facade y reinicia los seis filtros. |
| 4 | Consistencia y estándares | **3** | `equipment-detail-page.scss:46` redefine `.panel` con `surface` + `title-medium` contra el `surface-container-low` + `title-small` de `_dashboard.scss`: dos vocabularios de panel con la misma clase. `connector-types-page` sin estado vacío ni rama de tarjetas. `users-page` es la única lista sin «Limpiar filtros». |
| 5 | Prevención de errores | **3** | `energy-review-dialog.ts:50` preselecciona `decision: 'APPROVED'`. La opción auditada y consecuente es el valor por defecto. |
| 6 | Reconocer antes que recordar | **2** | El confirmar de importación recita las reglas en prosa en vez de mostrar los conteos que el usuario acaba de producir. La decisión energética se toma sin equipo, fecha, fuente ni umbral a la vista. |
| 7 | Flexibilidad y eficiencia | **2** | `MatSort` no se importa en ninguna parte: siete tablas, cero columnas ordenables. La aprobación masiva es de alcance página (`import-batch-page.ts:111`). Ninguna vista filtrada es enlazable. |
| 8 | Diseño estético y minimalista | **3** | `import-batch-page.html:78-92`: un `<p>` huérfano con `batch.energyReviewRows` en crudo, seguido de un `mat-form-field` cuyo `mat-label` repite ese párrafo palabra por palabra. |
| 9 | Recuperación de errores | **3** | Tras `CONCURRENT_MODIFICATION`, `energy-review-dialog.ts:96` deja el envío deshabilitado de forma permanente sin ruta de recarga dentro del diálogo. Cerrar pierde la justificación. |
| 10 | Ayuda y documentación | **2** | La capa de ayuda es sustanciosa — `CLASSIFICATION_HINTS`, `USAGE_STATUS_HINTS`, `SOURCE_COLUMNS`, los textos de confirmación de las reglas D — y se entrega casi toda por `title` en hover. El detector contó **8 sitios donde `title` es la única explicación, todos sobre `<span>` o `<li>` no enfocables**: inalcanzable por teclado y por táctil. Contenido de 4, entrega de 1. |
| **Total** | | **26/40** | **Aceptable — mejoras significativas antes de que los usuarios estén contentos** |

Ayuda bajó de 3 a 2 respecto de la revisión de diseño: la evidencia determinista confirmó que el conocimiento de dominio del producto vive en un atributo que un tercio de sus usuarios no puede alcanzar.

## Design Specificity Verdict

**Lenguaje de dominio autorado sobre andamio Material genérico, con exactamente una pantalla de verdad con forma de producto.**

**Evaluación de diseño.** Lo autorado y no trasplantable: el **vocabulario del no-contar** (`SESSION_STATE_LABELS` + `_HINTS`, `LINK_ISSUE_LABELS`, `CLASSIFICATION_LABELS` + `_HINTS`, `OUTCOME_LABELS`) y sobre todo `shared/format/quantity-format.ts`, donde la diferencia entre «0 kWh», `Sin datos` y `No informado` se resuelve una sola vez. El panel «Fuera de los indicadores oficiales» (`dashboard-page.html:88`) es el principio 2 de PRODUCT.md dibujado. La regla de empates del Top 5 honrada en presentación, incluida la frase que la declara. `SOURCE_COLUMNS` en el diálogo de importación existe solo porque alguien abrió los Excel reales.

Y la **vista previa del lote** (`import-batch-page.html`): decisión de tres vías por fila, chips de clasificación con conteos, confirmación que recita el destino de cada clasificación. Es una interfaz de conciliación real. Nada más en la app lo es.

Lo genérico: el shell de `_list-page.scss` instanciado cinco veces cambiando solo las cadenas. `users-page.html` indistinguible de cualquier CRUD; `issued-reports-page.html` una lista de archivos con checksum; `inventory-page.html` un catálogo filtrado con seis selects. `mat-table` + `mat-paginator` + kebab en las siete tablas, sin interacción propia del producto: sin orden, sin selección de fila, sin edición en línea, sin modelo de teclado.

El pensamiento del producto vive en sus cadenas y en una pantalla. Su composición todavía no expresa conciliación de sesiones de carga: quien abra Usuarios o Reportes no sabría para qué sirve SARC.

**Escaneo determinista.** Seis invocaciones de `impeccable detect --json`, las seis con exit 0. 22 hallazgos, todos `advisory`. Tras abrir cada línea citada: **2 reales**.

- `users/presentation/temporary-password-dialog/temporary-password-dialog.ts:54` — `font-size: 1.25rem` en el `<code>`, fuera de la rampa (2.25 / 1.5 / 1.375 / 1 / 0.875 / 0.75 / 0.688rem) y contra la Regla del `font` Compuesto. Lo cazó el detector y lo perdió la revisión de diseño.
- `consumption/presentation/share-chart/share-chart.ts:70` — `border-radius: 3px` en `.swatch`, fuera de la escala 8/12/16/999.

**19 falsos positivos con una sola causa**, verificada: `design-system-color` reporta `rgb(0,0,0)` en `line 0` de cada fragmento HTML porque lo parsea aislado, sin la hoja global; el color real lo pone `_base.scss:17` (`color: var(--mat-sys-on-surface)`). Comprobado contra `inventory-page.html:3`. Un FP más: el `border-radius: 999px` de `_badges.scss:7` — la escala del detector omite la píldora que DESIGN.md documenta.

**Límite del escaneo, declarado por la herramienta:** solo regex sobre `.ts` y `.scss`, así que contraste, texto diminuto y área de toque no corrieron en ninguna superficie (140 archivos). `src/app/features/home` es un único `.ts` con plantilla y estilos en línea: su cero no vale nada.

**Superposiciones visuales:** ninguna. Sin automatización de navegador en la sesión y sin servidor de desarrollo corriendo; no se arrancó ninguno. No existe evidencia renderizada para esta corrida.

## Overall Impression

La disciplina de sistema es casi perfecta y está medida: cero colores literales en `features/` y `shared/` (dos coincidencias, ambas en `chart-view.ts`, ambas derivadas de un token); 11 de 11 botones de solo icono con nombre accesible; las 10 tablas con celdas de encabezado; breakpoints consistentes en 599.98 / 767.98 / 1023.98; cero tildes faltantes en texto visible tras un barrido de 304 candidatos; cero TODO, cero relleno.

Lo que falla no es el sistema: el producto se detiene justo antes de su propio final. PRODUCT.md dice que SARC termina en un informe emitido, archivado y atribuible. En la interfaz termina en dos botones `outlined` — la variante de menor énfasis del sistema, reservada por DESIGN.md a «salida de la pantalla» — llamados «Reporte PDF» y «Excel», cuyo texto cambia a «Generando...» y después nada: ni confirmación, ni huella, ni enlace al archivo creado. Y la pantalla de informes emitidos no muestra `issuedBy`, aunque el contrato lo trae (`types.gen.ts:410`).

Oportunidad única más grande: que el tablero deje de ser un reporte y empiece a ser una cola de trabajo. Los cinco números que bloquean el informe están ahí, exactos, y son texto inerte.

## What's Working

**1. El vocabulario del no-contar es un sistema, no una lista de etiquetas.** La ambigüedad más peligrosa del producto — «cero» contra «desconocido» — se resuelve en `quantity-format.ts` con cuatro métodos y no se puede reintroducir desde una plantilla. `dashboard-page.html:58` cambia el rótulo del KPI de «Energía entregada» a «Energía conocida» cuando `sessionsWithoutEnergy > 0`: regla de producto expresada como decisión de presentación.

**2. El ciclo compartido de importaciones impide que dos importaciones muy distintas divergan.** `import-batch-state.ts` sondea a 1,5 s a partir del objeto del lote y no de `inProgress`, con su razón documentada; `canConfirm` exige filas realmente cargadas; las decisiones de fila se parchean sin refetch. Consumo y carga masiva tienen dominios, clasificaciones y semánticas de confirmación distintas, y quien aprendió una aprendió la otra.

**3. La accesibilidad de gráficas y cifras está resuelta en el sistema, no por gráfica.** `share-chart.ts` arma una `description` que enumera cada categoría con su porcentaje para el `role="img"`; la leyenda trae muestra + nombre + kWh + porcentaje; `daily-table.ts` pone las mismas cifras en una `<table>` real con `scope="col"` tras un `<details>`; `chart-view.ts` registra solo los controladores en uso, resuelve los tokens `light-dark()` por sonda del DOM, redibuja al cambiar tema y apaga la animación bajo `prefers-reduced-motion`. Una gráfica nueva no puede hacer regresión de esto.

## Priority Issues

### [P1] La capa que explica el producto se entrega por `title`

**Qué.** Ambas evaluaciones convergen y la determinista lo cuantificó: 8 sitios donde `title` es la única explicación de un estado, todos sobre `<span>` o `<li>` no enfocables — `session-state-badge.ts:14` (`SESSION_STATE_HINTS`), `equipment-badges.ts:27`, `inventory-kpis.html:16`, `import-batch-page.html:231`, `equipment-import-batch-page.html:194`, `external-identities.ts:36` (fecha de verificación, que no aparece en otro lado), `issued-reports-page.html:81` (checksum completo tras truncado a 16) y `:127` (única explicación de «Sin copia»). Además: `aria-describedby` 0 veces en todo el proyecto, `aria-live` 1 vez, y 65 de 75 `<svg>` sin `aria-hidden`.

**Por qué importa.** La frase que responde «¿por qué no suma esta sesión?» es el conocimiento de dominio de SARC, entregado por la afordancia menos accesible de HTML: inalcanzable por teclado, no expuesta en táctil, inconsistente sobre `role=generic`. DESIGN.md la sanciona («y, cuando hace falta, su `title`»), así que el sistema de diseño es la fuente del defecto.

**Arreglo.** Para los estados bloqueantes únicamente, la explicación pasa a contenido de la celda y la píldora queda como taquigrafía: «Pendiente de vincular · identidad Enerlink no verificada · no suma hasta vincularse». Para el resto, mover la pista a `aria-describedby` sobre un elemento enfocable, o a `matTooltip`, que responde a foco. `aria-hidden="true"` a los 65 SVG decorativos. Corregir la regla en DESIGN.md en la misma pasada.

**Comando sugerido:** `/impeccable audit`, después `/impeccable clarify`

### [P1] Los números que bloquean el informe son un callejón sin salida

**Qué.** `dashboard-page.html:88-132` presenta cinco conteos exactos — Pendientes de vincular, Por aprobar, Energía por revisar, Energía rechazada, Pruebas — como texto inerte. Ninguno es enlace, y no hay mecanismo para que lo sea: `queryParams` no aparece en ningún archivo de `features/`. Todo el estado de lista vive en facades provistos por componente, así que se destruye en cada navegación.

**Por qué importa.** Principio 1 de PRODUCT.md fallando en la pantalla estrella. La Analista lee «14 pendientes de vincular» y tiene que ir a Sesiones, reelegir el período (el facade arranca en `thisMonth`), reaplicar red/plataforma/tipo y poner Estado = Pendiente de vincular. A mano, después de cada lote. El Gerente puede leer el bloqueo pero no actuar ni enviar el enlace. Agravante: abrir un equipo desde una búsqueda con seis filtros y pulsar Atrás reinicia los seis y el índice de página.

**Arreglo.** Sincronizar filtros con `queryParamMap` en `SessionsFacade`, `InventoryFacade`, `EnergyImportsFacade` e `IssuedReportsFacade` (leer al iniciar, escribir con `replaceUrl: true`). Cada rótulo de `.metric-row` del panel como `routerLink` a `/consumo/sesiones` con el `estado` o `revisionEnergetica` correspondiente, arrastrando período y filtros vigentes. En el banner `COMPLETED`, el mismo enlace cuando `pendingLinkRows > 0`.

**Comando sugerido:** `/impeccable shape`

### [P1] `energy-review-dialog` es la pantalla más consecuente del producto y la menos terminada

**Qué.** Sin `guardUnsavedChanges` — único diálogo con formulario que no lo tiene: Esc o clic fuera destruyen una justificación de 500 caracteres sin preguntar. `decision: 'APPROVED'` preseleccionado. `{{ data.session.energyKwh }} kWh` esquiva `QuantityFormat`, así que la cifra sale con punto decimal en una interfaz es-EC, sin equipo, sin fecha, sin fuente y sin decir el umbral ni el exceso. El error en un `<p role="alert">` pelado en vez de `.form-alert`. `gap: 16px` y `margin-block: 12px` literales (líneas 38 y 42, confirmadas por el escaneo) donde existen `--sarc-space-4` y `--sarc-space-3`. Tras `CONCURRENT_MODIFICATION`, envío deshabilitado para siempre. El límite de 500 caracteres sin contador ni `maxlength`. Y `sessions-page.ts:103` abre con el `autoFocus: 'first-tabbable'` por defecto, que es el select Decisión: el usuario de lector de pantalla queda sobre el control que aprueba 1.400 kWh antes de que nada le haya leído a qué sesión pertenece.

**Por qué importa.** Este diálogo es D10 y la spec 003. Cada defecto cae sobre la única interacción que la especificación señala como «requiere juicio humano con justificación». Produce la peor combinación emocional posible: una decisión reversible, auditada e historiada presentada con el perfil de ansiedad de una irreversible — el diálogo renderiza el historial que prueba que se puede deshacer, y nunca lo dice.

**Arreglo.** `guardUnsavedChanges(() => this.reviewForm().dirty())`. Decisión sin preselección, que `required(path.decision)` haga su trabajo. Línea 4 a resumen de dos columnas: nombre y código del equipo, fecha y hora de inicio, fuente e id externo, y `quantities.reportedEnergy(energyKwh)` contra el umbral de 1.000 kWh con el exceso. Error a `.form-alert` con `@use '../../../../shared/ui/dialog-form'`. Literales a tokens. En `stale`, botón tonal «Recargar la sesión» que conserve la justificación. Contador de caracteres vivo. `autoFocus` al encabezado del resumen.

**Comando sugerido:** `/impeccable harden`

### [P1] La decisión humana auditada no se ve donde se ve el dato

**Qué.** Tres fallos del principio 4. `issued-reports-page.html` nunca renderiza `issuedBy`, aunque `IssuedReportResponse` lo trae — la superficie cuyo propósito es «archivados y atribuibles» no muestra autor. Las filas de `sessions-page.html` con energía aprobada o rechazada muestran solo una píldora: quién, cuándo y por qué quedan a dos clics. Y `energy-review-dialog.html:42` renderiza `decidedBy ?? 'No disponible'` junto a «Revisión 3», así que el rastro puede salir como «Persona: No disponible · Revisión 3».

**Por qué importa.** La defensa del informe ante auditoría es la razón declarada de existir del producto. Un auditor que mire una sesión de 1.400 kWh que sí cuenta, o un PDF emitido, no encuentra atribución en pantalla.

**Arreglo.** Línea «Emitido por» bajo la celda Tipo en reportes, y en la tarjeta de teléfono. En sesiones, cuando `energyReview` sea `APPROVED`/`REJECTED`, línea `muted block` bajo los kWh: «Aprobada por {nombre} el {fecha}» — requiere `energyDecidedBy` y `energyDecidedAt` en `SessionResponse`, que se pide contra el contrato OpenAPI, no se rodea. Quitar `sessionRevision` del diálogo y cambiar `'No disponible'` por «Usuario eliminado» o «Decisión del sistema».

**Comando sugerido:** `/impeccable clarify`

### [P1] Confirmar una importación pide un compromiso sin mostrar la aritmética

**Qué.** `import-batch-header.ts:62` pasa la prosa de `confirmMessage` al `ConfirmDialog` genérico, cuyo cuerpo entero es `<p>{{ data.message }}</p>`. El usuario está a 48 palabras de reglas de aplicar un lote de 400 filas y no ve un conteo, teniendo `batch.*Rows` disponible ahí mismo. Los banners masivos dicen «filas visibles» y cubren la página de 50; Confirmar cubre el lote, y nada reconcilia los dos alcances. El escaneo encontró además tres botones deshabilitados sin explicación adyacente alguna (`import-batch-header.html:13`, `:18`, `:26`): «Confirmar importación» queda muerto también mientras las filas cargan o fallaron en cargar, sin razón en pantalla.

**Por qué importa.** Se le pide a la analista responder por un resultado que no puede ver. Lecturas sobre el umbral, filas inválidas y modificaciones no aprobadas tienen destinos distintos. La tranquilidad correcta ya está escrita — «nada se incorpora hasta que la revise y confirme», `import-dialog.html:68` — pero está en el paso de subir, el de menor riesgo, y falta en el de confirmar.

**Arreglo.** Diálogo propio del lote con tres grupos leídos de `batch`: «Se incorporarán: N nuevas + M vinculadas + K corregidas aprobadas + J reasignadas»; «No se tocan: idénticas, duplicadas, inválidas»; «Quedan pendientes: sin equipo, sobre el umbral». Decir que el lote queda archivado y no se puede des-confirmar, solo reintentar. Renombrar los botones masivos a su alcance: «Aprobar las N de esta página». Dar razón a los tres deshabilitados.

**Comando sugerido:** `/impeccable harden`

## Persona Red Flags

**Alex (power user impaciente)** — *400 filas de «llegaron» a «conciliadas» de una sentada.*
- Cero orden en siete tablas. `MatSort` no se importa en ningún archivo.
- Aprobación masiva de página. `import-batch-page.ts:111` filtra `facade.rows()`: con `pageSize` 50 y 400 filas son ocho ciclos, y «visibles» en el banner es la única pista de que no aprobó todo.
- Atrás destruye su superficie de trabajo: facade provisto por componente reinicia seis filtros, búsqueda y página.
- Nada es marcable. No puede fijar ni compartir «Sesiones, este mes, Enerlink, pendientes de vincular».
- La búsqueda no da señal durante su debounce de 300 ms (`sessions-facade.ts:38`): teclear rápido se siente muerto.

**Sam (dependiente de accesibilidad)** — *averiguar por qué una sesión no cuenta, y actuar.*
- Toda la capa explicativa en `title`: los 8 sitios del primer P1.
- `aria-label` sobre un `<code>` (`temporary-password-dialog.ts:25`): rol `generic`, la etiqueta se ignora por ARIA. La contraseña se lee corrida, y el «Copiada» no tiene región viva.
- El foco aterriza en la decisión antes que en el sujeto.
- Resultados asíncronos solo por snackbar: `linkPending()` (`sessions-page.ts:135`) arma una frase asertiva con cuatro números, anunciada una vez en 4-8 s, sin persistencia en página. La tabla se recarga en silencio debajo.
- El indicador de carga del tablero está oculto para él: `dashboard-page.html:35` es `aria-hidden="true"`. Las ramas de error y vacío no señalan ocupado.
- La píldora mide 11px (`label-small`, 0.688rem) y carga el estado. Los pares de tokens pasan contraste de sobra (`on-error-container` sobre `error-container` supera 7:1), pero 11px para la única forma escrita de un estado es un problema de legibilidad aparte.

**Analista de Monitoreo (persona del proyecto)** — *importar, confirmar, resolver pendientes, decidir energías, emitir informe. Horas, escritorio.*
- El ciclo se rompe justo después del punto más alto: el banner `COMPLETED` dice cuántas se aplicaron, no cuántas quedaron pendientes, y no ofrece ruta.
- Su corrida de vinculación no deja registro: cuatro números en un snackbar de 4 segundos, sin bitácora de lo que hizo la vinculación automática.
- El mismo hecho contado de tres maneras: rótulo de `metric-row` en el tablero, `title` en hover sobre la píldora, y línea de `LINK_ISSUE_LABELS` en la columna Equipo.
- «Energía por revisar» no existe como cola de trabajo: es un valor de filtro que reconstruye por período, sin conteo de espera ni orden.
- Emitir el informe es indistinguible de descargar un archivo.

## Minor Observations

- `equipment-detail-page.scss:19` pinta el banner DELETED en `error-container` con `role="status"`. Borrado lógico recuperable que preserva histórico, en el color de alarma. DESIGN.md reserva Rojo Rechazo para fallos.
- `temporary-password-dialog` omite la validez de 60 minutos de D02: el administrador entrega una credencial sin saber que caduca. También `font-size: 1.25rem` fuera de la rampa, línea 54.
- `user-form-dialog.html:52`: la pista de INACTIVO no menciona D03.
- `issued-reports-page.ts:74` reusa `quantities.kwh` (formateador de energía, 2 decimales) para megabytes.
- `issued-reports-page.ts:84` usa `document.location.assign(signedUrl)`: si la URL firmada expiró, el usuario aterriza fuera de SARC sin vuelta más que Atrás.
- `issued-reports-page.html:11` es el único `.filters` sin `aria-label`.
- Sin `refreshWhileVisible` en `IssuedReportsFacade` ni `UsersFacade`, pese a ser pantallas de consulta bajo uso concurrente según el README.
- `import-batch-page.html:119` renderiza `{{ energyOf(item) }} kWh` en tarjetas; `energyOf` devuelve `'—'` para filas inválidas: «— kWh».
- `share-chart.ts:105` cicla `SLOT_COLORS` con `index % 3`: con más de tres categorías dos comparten color y la Regla de la Serie Fija se rompe en silencio. `byNetwork` ya tiene tres miembros.
- `top-equipment.ts:22` hace `track` por `item.equipment?.id`, `undefined` en entradas sin nombre: claves duplicadas si aparece más de una.
- `connector-type-dialog.ts:110` acepta espacios en `pattern(/^[A-Za-z0-9_ ]+$/)` mientras el mensaje dice «Solo letras, números y guion bajo».
- `_list-page.scss:56` fija `.actions-cell` en 56px; la celda de Sesiones lleva botón de texto más kebab, que no caben.
- `period-filter.ts:118` interpola `366 días` sin pasar por `QuantityFormat`.
- Inicio no tiene ningún breakpoint: `src/app/features/home` no tiene `.scss` y el bloque en línea de `home-page.ts:27-67` no tiene `@media` ni `@use`. Único mecanismo responsivo: `minmax(200px, 1fr)` en la línea 40. Única superficie sin breakpoint de teléfono, y única donde el detector no ejecutó ninguna regla de elemento.
- `connector-types-page.html` no tiene estado vacío: en una instalación nueva el Supervisor ve cuatro encabezados y nada, en la pantalla que hay que poblar antes de crear un equipo. Tampoco rama de tarjetas en teléfono ni `.table-scroll`. Y `users-page.html:52`, `inventory-page.html:138` y `sessions-page.html:88` culpan a los filtros aunque no haya ninguno puesto; solo los dos historiales de importación distinguen primera vez de búsqueda vacía.

## Questions to Consider

1. Si el producto termina en un informe emitido, ¿por qué «Reporte PDF» es la variante de menor énfasis del tablero, se llama como una exportación, y nunca se conecta con el archivo que crea? ¿Qué pasaría si la acción principal fuera «Emitir informe del período», y al pulsarla primero mostrara los bloqueos que arrastra y pidiera reconocerlos?
2. ¿Debería el tablero poder mostrar un total oficial limpio mientras existen bloqueos? ¿Y si la baldosa misma dijera «con 14 sesiones aún sin contar»? Eso haría del principio 2 una propiedad del número y no de un panel vecino.
3. D10 dice que la decisión energética es reversible e historiada. El diálogo renderiza ese historial y sigue sintiéndose como un disparo único. ¿Y si abriera sobre el historial y la espera, con el formulario debajo y sin decisión preseleccionada?
4. Las siete tablas no se pueden ordenar y ninguna vista filtrada se puede enlazar. ¿Es omisión, o decisión de que el trabajo se hace en una sola sesión larga y nunca se comparte? Si es lo segundo, la Analista trabaja contra la herramienta ocho veces por lote.
