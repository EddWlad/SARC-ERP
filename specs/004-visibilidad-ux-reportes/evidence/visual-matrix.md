# Matriz visual 004

Repetición T043, 2026-10-31. Cobertura automática en 10 combinaciones (360/390/768/1024/1440 × claro/oscuro), salvo donde se indica. Cada celda mide scroll horizontal, fuera de pantalla, recortes, etiquetas flotantes, solapes, objetivos bajo 24px y axe-core WCAG A/AA. Método y límites en [visual-audit.md](visual-audit.md). PASS = sin defecto abierto.

## Por rol y ruta

| Familia/ruta | Rol | Estados | Resultado |
|---|---|---|---|
| /login | público | normal, validación | PASS |
| /cambiar-clave | temporal | normal, validación | PASS |
| /inicio y shell | los 5 roles | normal | PASS |
| /usuarios | admin | normal, validación, editar, confirmar restablecimiento | PASS tras V-08 |
| /usuarios | asistente | restringido: redirige a /inicio | PASS |
| /inventario | los 5 roles | normal | PASS |
| Nuevo equipo | admin | normal, validación | PASS |
| Detalle de equipo | los 5 roles | normal | PASS |
| Detalle: editar, horario, identidad, confirmar inactivación | admin | diálogos | PASS tras V-08 |
| Detalle inexistente | admin | error 404 | PASS: alerta visible, sin `h1` |
| /inventario/tipos-de-conector | admin | normal, validación, ocupado | PASS |
| /inventario/tipos-de-conector | asistente | restringido: redirige a /inicio | PASS |
| /inventario/importaciones | admin | normal, diálogo | PASS |
| /consumo/tablero | los 5 roles | normal | PASS |
| /consumo/sesiones | los 5 roles | normal | PASS |
| Revisión energética | admin | diálogo | PASS. A 360/390 repetido con un clic; 768–1440 sin defecto |
| /consumo/importaciones | admin, analista | normal, diálogo | PASS |
| /consumo/importaciones | asistente | restringido: redirige a /inicio | PASS |
| /reportes | los 5 roles | normal | PASS |
| /reportes original restringido, emisión gerencial | gerente | diálogo, emisión completa | PASS |

## Estados simulados, admin

| Estado | Rutas | Resultado |
|---|---|---|
| Carga | usuarios, inventario, detalle, tipos, cargas masivas, tablero, sesiones, importaciones, reportes | PASS. Petición retenida, medido a 1,2s |
| 5xx | Las mismas 9 | PASS sin defecto medido. Alerta con Reintentar leída en 7 rutas y sesiones hasta 768; en importaciones, reportes y sesiones 1024–1440 no se leyó el texto |
| Vacío | usuarios, inventario, tipos, cargas masivas, sesiones, importaciones | PASS. Mensaje leído en 768 salvo tipos, leído en las 10 |
| Ocupado | Nuevo tipo de conector | PASS: «Guardando...» y botones deshabilitados |

## Limitaciones

- Ocupado solo en un diálogo; comparten patrón, no se recorrieron los demás.
- La primera vuelta admin registró 199 de 200 combinaciones al copiar resultados: falta el detalle 404 en 1440 oscuro. El tope de solapes de esa vuelta se resolvió repitiendo 13 diálogos en 130 combinaciones.
- La vuelta de carga, 5xx y confirmación se leyó en 220 de 230; las 10 restantes, identidad, se repitieron aparte.
- El rol asistente no se repitió en revisión de solo lectura; se recorrió en la pasada anterior, sin cambios después salvo foco y etiqueta.
- Tema oscuro forzado por clase en iframe. `prefers-color-scheme` real solo en tablero.
- A-03: Tab real en /usuarios con anillo de 2px, y medido antes en tablero y sesiones; no en cada ruta. A-04: sin etiquetas flotantes recortadas en toda la matriz.
- Lector de pantalla y dispositivo físico: no probados.

## Reauditoría final T064, 2026-11-01

Detector Impeccable sobre `src/app`: 24 notas consultivas, sin bloqueos, sin cambios frente a la auditoría anterior. Cambio posterior a T043, alerta de equipo no elegido en emisión gerencial: 10 combinaciones como gerente, sin defecto medido y un solo diálogo. No se repitió toda la matriz: desde T043 el frontend solo cambió ese diálogo. Sin captura antes/después de este estado.

## Repetición completa T064, 2026-11-01

Edge sin interfaz, sandbox con PostgreSQL desechable, arnés de `visual-audit.md` por rol. Sustituye el alcance parcial de la reauditoría anterior.

| Vuelta | Combinaciones | Scroll horizontal | Violaciones axe | Resultado |
|---|---:|---:|---:|---|
| Rutas y diálogos: público, temporal, 5 roles, restringidos | 570 | 0 | 0 | PASS tras V-09 |
| Carga, 5xx y vacío, 9 rutas | 250 | 0 | 0 | PASS: alertas y Reintentar leídos |
| Tablero y sesiones con datos, 5 roles, revisión y justificación | 130 | 0 | 0 | PASS |
| Repeticiones de primeras cargas y de V-09 | 180 | 0 | 0 | PASS |

La primera vuelta abrió tablero y sesiones en noviembre, sin datos del fixture de octubre: por eso existe la vuelta «con datos», que cubre gráficas y tablas. Ninguna combinación quedó sin medir: la primera de cada proceso daba «no asentada» por arranque en frío, y `/inicio` en segunda posición a 360 salió limpio. Detalle 404 sin `h1`: alerta visible, esperado. `simCARGA` con poco texto: esperado.

Capturas actuales en `captures/t064/`. Detector Impeccable: 24 notas consultivas, sin bloqueos.

Excepciones aceptadas por el responsable, 2026-11-01: lector de pantalla y dispositivo físico no obligatorios; capturas «antes» solo de V-08. V-09 y V-10 tampoco tienen captura previa: su verificación es medición.
