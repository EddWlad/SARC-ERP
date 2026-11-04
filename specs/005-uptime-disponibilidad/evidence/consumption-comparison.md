# Comparación de Consumo con la línea base (T119)

Contrasta el estado posterior a US6 con `consumption-baseline.md`. Mismos datos, mismos filtros y misma base desechable de Testcontainers. La base de Compose no se tocó.

## Cifras y exportaciones

- Comando: `./mvnw test -Dtest=Feature005ConsumptionBaseline -Dsarc.qa.baseline=after`.
- Comparación: `target/consumption-before.txt` frente a `target/consumption-after.txt`, con líneas ordenadas y sin «Fecha de corte». El orden entre filas de Transacciones con igual inicio no es determinista; dos ejecuciones sin cambios ya diferían solo en ese orden y en la fecha de corte.

| Dato | Antes | Después |
|---|---|---|
| Sesiones oficiales | 10 | 10 |
| Energía oficial | 2.240,000 kWh | 2.240,000 kWh |
| Equipos con actividad | 7 | 7 |
| Sesiones sin energía | 1 | 1 |
| Promedio por sesión | 248,889 kWh | 248,889 kWh |
| Resumen JSON de ambos roles | — | Idéntico |
| Ranking y Top 5 | — | Idénticos |
| Hojas de detalle y Transacciones | — | Idénticas |

Únicas diferencias del volcado:

| Rol | Salida | Cambio |
|---|---|---|
| `GERENTE` | PDF | Desaparecen la etiqueta «Aprobadas sobre umbral (incluidas en oficiales)» y su valor «1 (1.700,00 kWh)» |
| `GERENTE` | Excel, hoja Resumen | Desaparecen las dos filas «Aprobadas sobre umbral» |
| `ANALISTA` | Todas | Sin cambios |

El desglose por sesión del Excel conserva las columnas de excepción y justificación. SC-010 se cumple: el cambio retira presentación y no altera cálculos, registros ni aprobaciones.

## Dashboard

Arnés `Feature004BrowserSandbox`, rol `ANALISTA`, período «Mes anterior» (octubre de 2026), como la línea base. Cinco categorías con sus valores: pendientes 1 · 35,00 kWh; por aprobar 1 · 36,00 kWh; pruebas 1 · 37,00 kWh; por revisar 1 · 1.500,00 kWh; rechazada 1 · 1.600,00 kWh.

| Ancho | Bloques por fila | Bloque (px) | Filas (px de alto) | Scroll horizontal |
|---|---|---|---|---|
| 360 | 1 | 328 × 455 | 40, 65, 41, 40, 41 | No |
| 390 | 1 | 358 × 455 | 40, 65, 41, 40, 41 | No |
| 768 | 2 | 705 × 276 | 40, 65, 41, 40, 41 | No |
| 1024 | 2 | 705 × 276 | 40, 65, 41, 40, 41 | No |
| 1440 | 2 | 1121 × 252 con menú abierto; 1232 × 252 con menú cerrado | 40, 41, 41, 40, 41 | No |

Con datos se midieron 768, 1024, 1440 y 360 en tema oscuro, y 360 en tema claro con igual geometría; el resto de combinaciones claras se revisó con el período vacío, que usa el mismo marcado.

## Defectos de la línea base

| Defecto | Estado |
|---|---|
| Lista plana sin agrupación | Corregido: «Vinculación y aprobación» y «Revisión energética» |
| Cuatro columnas y fila huérfana a 1440 | Corregido: dos bloques, cinco filas sin partir cantidad |
| Cantidad y energía separadas a 768 | Corregido: `.quantity` no se parte; solo la etiqueta larga pasa a dos líneas |
| Energía con marcado desigual | Corregido: cinco filas con `strong` y `.muted` |

## Visibilidad

`dashboard-page.spec.ts` comprueba que el bloque sigue visible para operativos y ausente para consulta. La nota «Aprobadas sobre umbral, incluidas en los indicadores oficiales» se mantiene en el dashboard.

## Observación fuera de alcance

Si la ventana se reduce desde escritorio hasta 360 px sin recargar, el menú lateral queda abierto y comprime el contenido. Al cargar en ese ancho o al reducir desde 390 no ocurre. Es comportamiento previo del `layout`, no introducido por esta feature.
