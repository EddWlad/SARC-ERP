# Contraste de muestra real Enerlink (DP-04b y DP-05)

**Estado**: contrastada el 2026-11-02 (T091, T095, T134). No se copian nombres ni cifras de equipos.

## Archivos recibidos

En `documents/informes_de_enerlink_wallbox/`:

| Archivo | Qué es |
|---|---|
| `uptime_10-2026_11-2026_emite_enerlink.xlsx` | Exportación del módulo Análisis de Enerlink, reporte «Uptime y Uso». Solo por mes. |
| `Modelo_Informe_UpTime-Electrolineras-Ptos.Carga_19-10-2026_25-10-2026.xlsx` | Informe semanal armado a mano a partir del anterior, con valores tomados uno por uno de la vista «últimos 7 días» de cada cargador. Es el que se envía cada lunes. |

Enerlink no ofrece hoy una consulta de uptime por rango personalizado; ya fue solicitada. Los cargadores KIA que solo están en Wallbox no tienen uptime en Enerlink y quedan fuera de esta importación.

## Estructura

| Dato | Reporte mensual | Informe semanal |
|------|-----------------|-----------------|
| Hojas | 1 | 2 (Electrolineras y Ptos. Carga). Se leen ambas (D09, Constitución 1.2.0) |
| Fila de período | Fila 1, celda combinada: `01-10-2026 00:00:00 01-11-2026 23:59:59` | Fila 1, mismo formato; cada hoja con un período distinto y distinto del nombre del archivo |
| Encabezados | Fila 3 | Fila 3 |
| Columnas | Cargador, Conector, Activo, Grupo de cargadores, Uptime (%), Fuera de linea (hrs), Transacciones, Energía cargada (kWh), Disponible (hrs), Cargando (hrs), En falla (hrs) | Cargador, Conector, Uptime (%), Fuera de linea (hrs), Disponible (hrs), En falla (hrs) y una nota libre |
| Identificación | Activo (sitio) y Cargador | Solo Cargador |
| Etiqueta de conector | `Conector N - tipo`, con variantes de espacios y guiones; también `Conector 1` sin tipo | Igual; además `Conector A - PUESTO 4 - CCS2` |
| Zona horaria | No declarada; se toma America/Guayaquil | Igual |
| Unidad de duración | Horas | Horas |
| Precisión de duración | 2 decimales (36 s) | 2 decimales |
| Porcentaje | Texto con 2 decimales | Número con hasta 2 decimales |

## Decisiones

- Se importa el **informe semanal tal como se envía por correo**, sin agregarle columnas. El reporte mensual no se importa por ahora; el informe que Enerlink emita con filtro personalizado se cargará por la misma vía.
- Hojas: todas, en orden; cada fila conserva hoja y número de fila. Una hoja vacía se ignora; una sin las columnas requeridas detiene la carga nombrando la hoja.
- Período: la semana (lunes a domingo, ya terminada) que se elige al cargar; la fila 1 se ignora porque no corresponde a la semana enviada. Sin semana elegida, el período sale de las columnas Desde y Hasta.
- Uptime: sin columna Cargando/Ocupado se guarda el porcentaje del informe (`REPORTED_PERCENT`). Disponible, Con falla y Fuera de línea se guardan; si los tres superan la duración más el margen, o todos son cero, la medición queda observada.
- Horas a minutos: al minuto más cercano; las horas originales quedan en los valores de la fuente (`availableHours`, etc.).
- Identidad: sin Activo, el equipo se resuelve por Cargador si una sola identidad verificada de Enerlink lo usa; si hay varias o ninguna, la fila queda pendiente de vincular.
- Etiqueta de D12: `source_charger` (Cargador).
- Conector: el número se lee también tras la palabra «Conector». Las letras (`Conector A - PUESTO 4`) quedan pendientes de vincular y se resuelven con alias.
- T092, equipo de un solo conector sin etiqueta: los archivos siempre traen etiqueta; no se agrega regla.
- Ventana: el informe sale de la vista «últimos 7 días» de cada cargador; entra como 7 días y PROVISIONAL. Solo si quien carga afirma que los valores son del lunes al lunes se confirma semana exacta.

## Medición con el informe semanal real (T134)

127 filas en 2 hojas, leídas con `TabularReader.readSheets` y `EnerlinkUptimeParser` con la semana del 19-10-2026, y evaluadas con `MeasurementRules`:

| Comprobación | Resultado |
|---|---|
| Filas inválidas | 0 |
| Sin observaciones, aptas para validar | 122 |
| Estados que superan 168 h (dato del informe a revisar) | 4 |
| Fila retirada con todo en cero | 1 (`SUM_ZERO`) |
| Identidad por Cargador contra la base de desarrollo | 93 de 97 nombres con una sola identidad verificada; el resto queda pendiente de vincular |

## Medición previa con el reporte mensual (T095)

61 filas, ninguna inválida. Diferencia máxima entre porcentaje informado y calculado: 0,0065 puntos, por lo que DP-05 (0,01 puntos) se mantiene. Con margen de 0,02 % en períodos de más de 7 días, 0 filas observadas por suma.

## Pruebas

- `EnerlinkUptimeParserTest`: modelo de dos hojas con semana elegida, hoja vacía ignorada, horas al minuto, reporte con Cargando, período ausente y columna faltante con nombre de hoja.
- `MeasurementRulesTest`: margen de 1 minuto hasta 7 días y 0,02 % después; uptime informado, estados sobre la semana, fuera de rango y porcentaje obligatorio.
- `UptimeCalculatorTest`: el uptime informado pesa la semana completa.
- `UptimeImportApiTest.weeklyModelIsImportedFromEverySheetWithTheChosenWeekAndItsUptime`: carga multipart con `weekStart`, ambas hojas, identidad por Cargador, alias pendiente, revisión `REPORTED_PERCENT` y corrección que conserva el porcentaje.
- `UptimeImportApiTest.weekMustStartOnMondayAndBeOver`: semana que no empieza en lunes o que no ha terminado.
