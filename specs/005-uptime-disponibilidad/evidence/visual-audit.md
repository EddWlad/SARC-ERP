# Auditoría visual (T120)

Ejecutada el 2026-11-01 con `Feature005BrowserSandbox`, base desechable y datos sembrados: semana oficial, ventanas provisionales, 0 %, sin medición, tres conectores y nombre largo. La base de Compose no se tocó.

## Barrido automático

Rutas: `/uptime/tablero`, `captura`, `revision`, `importaciones`, `importaciones/:id`, `vinculacion`, `mediciones/:id`, `/reportes` y `/consumo/tablero`. Por ruta se midió desborde horizontal, controles sin nombre, un solo `h1`, orden de encabezados y contraste de texto.

| Pasada | Anchos | Temas | Resultado |
|---|---|---|---|
| Antes de corregir (sin `importaciones/:id`) | 360, 390, 768, 1024, 1440 | Ambos | Sin desborde ni controles sin nombre; `h1` ausente en dos pantallas |
| Después de corregir | 360, 390 | Ambos | Sin desborde, sin controles sin nombre, un `h1` por pantalla |

El contraste bajo señalado en pestañas y botones (1,08 a 1,70) fue artefacto de medir durante la transición de tema. Con espera de 3 s dio cero hallazgos en ambos temas.

## Defectos corregidos

| Defecto | Evidencia | Corrección |
|---|---|---|
| Porcentajes y horas con punto (`81.58`), contra la spec (`97,29 %`) | Tablero, revisión y detalle | `QuantityFormat.decimal`, `percentage` y `measure`, con prueba |
| Minutos sin separador (`10080`) | Ranking y detalle | `QuantityFormat.measure` |
| Importaciones y Vinculación sin `h1` | Barrido | Encabezado cambiado a `h1` |
| Foco en `body` tras «Guardar y siguiente» | Recorrido con teclado | Foco al primer campo, con prueba roja y verde |
| Botones «Registrar» iguales para lector de pantalla | Revisión de la grilla | `aria-label` con equipo y conector |
| Filtros del tablero a 360 sin usar el ancho; KPI muy abajo | Captura a 360 | Dos columnas bajo 768 px, como Consumo; primer KPI a 730 px |
| Mensajes del backend sin tildes | Vista previa de lote | Tildes en los textos de uptime |

## Comprobaciones propias

- Rótulos OFICIAL y PROVISIONAL en texto, no solo por color.
- 0,00 % validado en ranking; sin medición se muestra como raya y fuera del ranking.
- `GERENTE`: solo pestaña Tablero, sin Trabajo operativo, 403 en importaciones y vinculación, 403 al registrar, y `measurements` devuelve solo validadas, sin observaciones ni justificaciones.
- Consumo: ver `consumption-comparison.md`.

## Límites

- Tras las correcciones, 768, 1024 y 1440 se revisaron en tablero, sin repetir el barrido completo.
- Estados de carga, error de servidor y resultados extensos no se forzaron en navegador; los cubren pruebas de componente donde existen.
- Diálogos de corrección, exclusión y justificación no se midieron a cada ancho. El de captura se revisó a 1440.
- `ASISTENTE` y `SUPERVISOR` se cubren por pruebas, no en navegador.
- Reducir de escritorio a 360 sin recargar deja abierto el menú lateral. Es comportamiento previo del `layout`.

## Rediseño de armonía con Consumo (2026-11-01)

Las pantallas de Uptime se rehicieron con los patrones de Consumo e Inventario; ver `impeccable.md`. Con `Feature005BrowserSandbox` y datos sembrados por API:

- 1440, tema oscuro: Tablero, Captura, Revisión, Importaciones, Lote, Vinculación y Medición revisados por captura.
- 375, tema claro: las siete rutas sin desborde de página; tarjetas en lugar de tablas. Pestañas desplazables.
- 1024, tema claro: filtros, chips, Limpiar filtros, búsqueda de equipo, Conector condicional y diálogo de registro con seis campos `outline`. Corregido desborde del formulario de Captura.
- Consumo e Inventario: pestañas, cabecera y resultados intactos tras extraer los parciales comunes.

No repetido tras el rediseño: 360, 390 y 768 en cada ruta, y estados de error forzados. Vinculación se vio sin filas pendientes confirmadas.
