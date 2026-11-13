# Contrato de formato de importación

Este contrato aplica FR-022 a FR-027 y la investigación R4.

## Fuentes

| Fuente | Lectura |
|--------|---------|
| XLSX | Solo la primera hoja (D09), leída con `TabularReader.read` |
| CSV | UTF-8 (con o sin BOM), con Windows-1252 como alternativa si el archivo no es UTF-8 válido, igual que `TabularReader` (FR-022). El separador puede ser `,` o `;` y se detecta en la primera línea no vacía |
| Pegado | Texto con columnas separadas por tabulador, copiado del Excel, leído con `TabularReader.readText` |

El límite es de 20 MiB y 50.000 filas.

## Columnas

Los encabezados se reconocen por su nombre: sin tildes, sin distinguir mayúsculas y con espacios simples. El orden de las columnas no importa.

| Columna | Obligatoria | Interpretación |
|---------|-------------|----------------|
| Fecha | Sí | Celda de fecha de Excel, `dd/MM/yyyy`, `yyyy-MM-dd` o un número de serie. Es el día de la fila |
| Código cargador | Sí | Se compara en forma literal con el Código asignado, sin espacios exteriores |
| Nombre cargador | No | Solo se muestra y sirve como sugerencia; nunca vincula |
| Comentario | No | Hasta 500 caracteres; se conserva tal cual |
| Inicio N y Fin N (N ≥ 1, sin límite) | Al menos un par | Hora de Excel, fracción del día o `H:mm`/`HH:mm` |
| Total inactivo | No | Solo se usa para comparar (FR-027) |

Si **no hay fila de encabezados**, el orden esperado es el de la plantilla operativa: Fecha, Código, Nombre, Comentario, los pares Inicio y Fin, y al final Total inactivo. Las columnas posteriores a la cuarta se interpretan así, según su cantidad `k` (contando hasta la última columna con algún valor en el archivo):

- `k` impar: la última es Total inactivo y las `k − 1` anteriores forman pares Inicio/Fin. La plantilla de 15 columnas da `k = 11`: 5 pares y el Total.
- `k` par: todas forman pares Inicio/Fin y no hay Total inactivo (`TOTAL_MISSING`).
- `k = 0`: no hay pares; el archivo se rechaza con `IMPORT_HEADER_MISSING` («Inicio 1»).

## Reglas de interpretación

1. Inicio = fecha de la fila + hora de Inicio N. Fin = fecha de la fila + hora de Fin N.
2. Si el fin es anterior o igual al inicio, la ventana queda INVALID con `END_BEFORE_START` (y `ZERO_DURATION` si son iguales). **Nunca** se suma un día de forma automática. El analista puede:
   - marcar `endsNextDay` (el fin pasa a ser la fecha + 1 + hora);
   - corregir la hora;
   - editar el fin con fecha y hora completas, para una caída de varios días.
3. Segundos: si `|s − 60·round(s/60)| < 1`, se usa el minuto redondeado; si no, queda `SECONDS_AMBIGUOUS`.
4. Un par con Inicio y sin Fin, o al revés, queda `INCOMPLETE_PAIR`. Si falta solo el Fin, el par admite la decisión `OPEN`. Una ventana abierta ocupa desde su inicio hasta que se cierra (FR-014), así que al elegir `OPEN` se vuelve a clasificar contra las ventanas posteriores del mismo punto: las del mismo lote, en esa fila o en filas de días siguientes, quedan `OVERLAP_IN_BATCH`, y las ya guardadas dan `OVERLAPS_EXISTING`. Todo se señala en la vista previa antes de confirmar. Para resolverlo, el analista cierra el par, lo omite u omite o corrige las posteriores.
5. Un instante posterior al minuto en curso queda `FUTURE_INSTANT` (FR-021b). Esta regla se aplica al interpretar, al editar y al confirmar.
6. Una ventana cerrada de 240 min o más queda en REVIEW con `LONG_WINDOW` (FR-021a).
7. Total recalculado = suma de los minutos de las ventanas válidas de la fila que no tienen la decisión OMIT. Mientras haya ventanas idénticas sin resolver, se muestran además el total con el duplicado y sin él. La comparación con Total inactivo se repite después de cada edición o decisión. Si la diferencia es de 1 minuto o más, la fila queda en REVIEW con `TOTAL_MISMATCH` y se resuelve con `acknowledgeTotal` o con una corrección (FR-027).
8. Total inactivo: si es una fracción de 1 o más, o una celda `[h]:mm`, se interpreta como días y horas.

## Ejemplos esperados (copia sintética de las filas reales)

| Caso | Entrada | Interpretación esperada |
|------|---------|-------------------------|
| Fila 3 | 12:35–13:02, 16:49–17:19, 17:55–18:21, 17:55–18:21; total 01:49 | 4 ventanas; la 3 y la 4 quedan `DUPLICATE_IN_BATCH`; se muestran 109 min con el duplicado y 83 sin él. Antes de decidir no hay `TOTAL_MISMATCH` (109 = 109), pero el duplicado obliga a decidir. Al omitir la ventana 4, el total pasa a 83 min y aparece `TOTAL_MISMATCH` (83 frente a 109), que exige `acknowledgeTotal` |
| Sin encabezado, 15 columnas | Orden de la plantilla | `k = 11`: 5 pares y Total inactivo |
| Sin encabezado, 18 columnas | 4 fijas y 14 más | `k = 14`, par: 7 pares y sin Total (`TOTAL_MISSING`) |
| Fila 61 | Par 2: 19:41–19:26 | `END_BEFORE_START`; no se calcula como 1.425 min |
| Fila 99 | 17:07–17:59, 18:04–18:50; total 22:11 | 52 + 46 = 98 min; `TOTAL_MISMATCH` (1.331 frente a 98) |
| Fila 117 | Inicio 3 16:01, sin Fin | `INCOMPLETE_PAIR`; admite `OPEN`. Si el mismo punto tiene en el lote ventanas posteriores a las 16:01 (por ejemplo, una fila del día siguiente), al elegir `OPEN` esas ventanas quedan `OVERLAP_IN_BATCH` |
| Fila 163 | 20:03–12:22 y `endsNextDay` | 979 min (237 el día 1 y 742 el día 2); queda en REVIEW por `LONG_WINDOW` |
| Fila 228 | 02:24–02:30 y 02:25–03:11 | `OVERLAP_IN_BATCH`; con MERGE queda 02:24–03:11 (47 min) |
| Seis o más pares | Inicio 6 y Fin 6, Inicio 7 y Fin 7 | Se conservan todas las ventanas (FR-010) |
