# Auditoría visual 004

Fecha 2026-10-31. Chromium del panel de Claude Desktop, sandbox `Feature004BrowserSandbox` (PostgreSQL desechable, storage simulado).

**Método.** Arnés propio: cada ruta se cargó en un iframe del mismo origen por ancho (360, 390, 768, 1024, 1440) y tema (`theme-light`/`theme-dark`). En cada una se midió scroll horizontal, elementos fuera de pantalla, textos recortados, solapes entre bloques y objetivos bajo 24px, y se ejecutó axe-core 4.14 con etiquetas WCAG 2.0/2.1/2.2 A y AA. Detector Impeccable sobre `src/app`: sin hallazgos de bloqueo; 24 notas consultivas de tokens fuera de DESIGN.md, sin defecto visible asociado. El detector corrió por regex, sin reglas de elementos.

Una primera pasada se descartó: el arnés auditaba páginas aún en blanco. Se repitió exigiendo encabezado y contenido pintado.

## Hallazgos

| ID | Ruta / rol | Viewport | Estado | Causa | Resolución |
|---|---|---|---|---|---|
| V-01 | Identidad externa / admin | 360, 390, ambos temas | detectado → reproducido → corregido → verificado | Ayuda de dos líneas con `subscriptSizing` fijo | `subscriptSizing="dynamic"` y gap `space-3` en `external-identity-dialog.ts`, spec nueva |
| V-02 | Detalle de equipo / admin | Todos | detectado → corregido → verificado | `input type=file` sin nombre accesible | `aria-label` y `tabindex=-1` en `equipment-image.ts`, spec nueva |
| V-03 | Nueva importación, inventario y consumo / admin | Todos | detectado → corregido → verificado | Mismo patrón en `import-dialog.html` | `aria-label` y `tabindex=-1` |
| V-04 | Tablero, ranking / todos | 360 a 1024 | detectado → corregido → verificado | Nombre con elipsis y sin alternativa, ilegible sin hover | Ajuste de línea en `top-equipment.ts`; empates completos |
| V-05 | Barra superior / todos | 360, 390 | detectado → corregido → verificado | Título largo cortado | «SARC» bajo 480px en `shell.html`/`shell.scss` |
| V-06 | Justificación aprobada / lector | 360 | detectado → corregido → verificado | axe `scrollable-region-focusable`: región con scroll sin foco para lector | `tabindex=0` si no edita en `energy-review-dialog.html`, spec nueva |
| V-07 | Conector / admin | 375 | corregido antes de esta ronda | Ver `visibility-results.md` | `subscriptSizing="dynamic"` |
| V-08 | Editar usuario (Roles) y equipo (Tipos de conector) / admin | 360 y 390 según diálogo | detectado → reproducido → corregido → verificado | Ayuda de dos líneas sobre el campo siguiente: a 360 terminaba en y=413,5 y Ubicación empezaba en 405,5. La primera matriz lo ocultaba: el arnés truncaba a 6 solapes | `subscriptSizing="dynamic"` en ambos, y preventivo en Código asignado; spec en `equipment-form-dialog.spec.ts`. Después: 8px libres. Capturas `captures/V-08-{antes,despues}-{equipo,usuario}-360.png` |

## No son defecto

- Solape de campos con Cancelar/Registrar en diálogos largos: el campo se desplaza bajo el pie. Medido en Nuevo equipo a 360: `mat-dialog-content` con scroll termina en y=640, acciones empiezan en y=640. Revisado con captura. Mismo patrón señalado en revisión energética y emisión gerencial, no medido aparte.
- `.sarc-visually-hidden` «recortado»: es su diseño. Etiquetas flotantes: artefacto de medir `scrollWidth` sin su escala, salvo «Revisión energética», defecto real corregido (A-04 en accessibility-results.md).
- `a.link` de fecha en importaciones, 16px de alto: enlace en celda con filas más altas, cubierto por excepción de espaciado de WCAG 2.5.8.

- Dos diálogos de revisión energética apilados a 360/390: artefacto del arnés, que hizo un segundo clic programático tras el fondo. Con un clic: un diálogo, sin solapes.
- Aviso «Cerrar» sobre la tabla tras emitir: snackbar temporal esperado.

## Observaciones sin corregir

- Nombre de archivo de reporte con hora UTC: a las 19:46 de Ecuador muestra `consumo-20261101-0046`. Es decisión explícita de `FILE_STAMP` en `ConsumptionReportService`, no defecto visual.
- Carga masiva de equipos ante 5xx dice «Se perdió la conexión con el servidor». Hay alerta y reintento; el texto es impreciso, no bloquea.

## Repetición T043

Mismo arnés, ampliado: sin tope de solapes, excluye solo contenido contra pie de diálogo, y simula carga, 5xx y vacío interceptando `fetch`. Resultado por estado en [visual-matrix.md](visual-matrix.md). Capturas V-08 con Edge headless por CDP.

## Hallazgos T064

| ID | Ruta / rol | Viewport | Estado | Causa | Resolución |
|---|---|---|---|---|---|
| V-09 | Importaciones de consumo e inventario / admin, analista | 360, 390, 1440 | detectado → corregido → verificado | Enlace del lote con 16px de alto sin nombre de archivo, bajo el mínimo de 24px | `inline-block` y `min-height: 24px` en `_import-history.scss` |
| V-10 | Inactivar equipo, editar y restablecer usuario / admin | Todos | detectado → reproducido → corregido → verificado | Diálogo abierto desde menú: al cerrar, el foco caía en `body` porque el ítem ya no existía | La acción espera a `menuClosed`; foco vuelve al disparador. Spec en `user-actions-menu.spec.ts`, repetido con teclas reales |

## Límites

- Capturas antes/después solo de V-08. V-01 a V-07 y A-01 a A-04 se corrigieron antes de esta repetición y no tienen captura anterior: su verificación es medición y spec. T043 se cerró con este límite por aceptación expresa del responsable, 2026-10-31.
- La detección es automática. Solo se miraron capturas de algunos casos, no cada combinación.
- Contraste de `canvas` (gráficos) fuera del alcance de axe.
- Tema oscuro forzado con clase en iframe; `prefers-color-scheme` real solo en tablero.
- Página de error sin `h1`: muestra alerta, sin violación axe A/AA.
- Capturas en `captures/`.
