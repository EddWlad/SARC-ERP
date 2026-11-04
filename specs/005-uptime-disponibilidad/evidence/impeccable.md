# Impeccable (T122)

Estado: cerrada con límites, el 2026-11-01.

## Revisión

Comparación de las pantallas de Uptime contra Consumo e Inventario, en navegador y sobre código, a pedido del responsable de producto. Defectos hallados:

| Defecto | Ajuste |
|---|---|
| Campos con apariencia `fill`; el resto usa `outline` | `outline` en filtros y diálogos |
| Pestañas `mat-tab-nav-bar` sin iconos | Enlaces con icono; estilo común en `shared/ui/_section-tabs.scss` |
| Diez filtros con botón Aplicar y acciones mezcladas | Chips de período y cuatro campos, como Consumo; Conector aparece al elegir equipo; aplicación inmediata |
| Sin cabecera de página y sin subtítulo en Tablero, Captura y Revisión | `page-header` con acciones alineadas e iconos |
| Listas propias en lugar de tablas | `mat-table` en escritorio y tarjetas en teléfono, con paginación |
| Estados en texto simple | Pastillas `badge` y estados vacío o error comunes |
| Cobertura en lista corrida; tablero sin gráfica de ranking | Paneles con `metric-row`; gráfica de conectores con menor uptime |
| Detalle de medición con datos sueltos | Tiles KPI, cabecera con acciones e historial en tarjetas |

Nada cambia en cálculos ni contratos: `from` y `to` conservan su semántica; el rango visible incluye el último día y se envía el siguiente como exclusivo.

## Detector

Modo fuente sobre `features/uptime` y `consumption/.../dashboard-page`, 38 archivos: 0 antipatrones. Tres avisos `design-system-color` en `h1` y `section`, `rgb(0, 0, 0)`: falso positivo, el modo fuente no resuelve el tema.

## Límites

- El detector en modo fuente no evalúa contraste ni tamaño de texto. Las rutas de Uptime exigen sesión, así que el detector no las escanea renderizadas.
- La revisión fue comparativa contra los módulos existentes; no se siguió el guion formal de puntuación de `critique`.
