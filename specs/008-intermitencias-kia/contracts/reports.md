# Contrato de informes PDF y Excel

Este contrato aplica FR-038 a FR-041, FR-046a, RF-RPT-07 a 11 y la investigación R11.

## Reglas comunes

- Se emiten con el `IntermittencyCalculator` y la `IntermittencyQuery` normalizada que usa `/summary`. El corte es el momento del cálculo.
- Se archivan con `ReportArchive.archive(IssuedReportFile(kind="INTERMITTENCY", scope, file, details, publication))`, que guarda la huella SHA-256 y una copia privada.
- `ReportPublication.criteria` contiene `from`, `to`, `equipmentIds` (si hay filtro), `periodPreset` y `timeZone=America/Guayaquil`.
- `profile` vale `WITH_OPERATIONS` si el informe incluye el anexo operativo o la disponibilidad, y `OFFICIAL_ONLY` en los demás casos.
- Los informes usan nombres actuales (D12) y no incluyen UID/PUK ni URL OCPP.
- Las duraciones se muestran en `h:mm`, con horas acumuladas (por ejemplo 36:42), y en minutos.
- Los informes no se envían por correo.
- **Alcance del encabezado** (FR-038): se archiva como `scope`. Sin filtro, «Todos los puntos KIA». Con 5 puntos o menos, cada uno con su nombre actual y su Código asignado, ordenados por nombre, solo si es un punto KIA en alcance; con más de 5, solo cuántos son. Un equipo fuera de alcance o inexistente se muestra «no disponible», sin su nombre ni su código.
- **Tope**: un informe admite hasta 50.000 ventanas confirmadas con tiempo en el rango (la hoja Ventanas del Excel tiene una fila por ventana). Por encima se rechaza con 422 `REPORT_TOO_LARGE` y no se genera ni se archiva nada (FR-041a).

## PDF (`CorporatePdf`)

1. Encabezado con los logos de EOS Tech Company y Autoeastern, el título «Informe gerencial de intermitencias · Red KIA», el tipo y el rango del período, la zona horaria y la fecha de corte.
2. Resumen ejecutivo en texto, calculado con los indicadores, con el modelo de la plantilla: «Durante el período se registraron N cargadores afectados en D días-equipo, con V ventanas completas y HH:MM de tiempo fuera de línea confirmado. R equipos recurrentes…». Si no hay datos: «No hay intermitencias confirmadas en el período».
3. Tarjetas de indicadores oficiales (FR-033).
4. Tendencia diaria: línea de horas y barras de ventanas. Si una ventana cuenta en más de un día, se agrega la nota «Una ventana que cruza la medianoche cuenta en cada día que toca» (FR-035).
5. Top 10 en tabla. Si hay empates más allá del décimo, se indica «incluye empates».
6. Los tres repartos de FR-036 como **barras de participación**, con leyenda textual, minutos y porcentaje. `CorporatePdf` no dibuja gráficos circulares y el PDF de Consumo tampoco los usa; en pantalla los repartos sí son pasteles (dona). Los valores y los porcentajes son los mismos (decisión de implementación registrada en CC-PRD-008, aclaración 16).
7. Notas de metodología: definiciones, unidades, período con fin exclusivo, franjas, empates, exclusiones y separación respecto de Uptime.
8. Apartado «Fuera de los indicadores oficiales», con los rótulos del dashboard: «Total por revisar» (ventanas por revisar + filas en conflicto + filas por vincular, FR-033), «Ventanas por revisar (4 h o más)», «Filas en conflicto», «Filas por vincular», «Eventos abiertos» (con sus minutos provisionales) y «Ventanas de prueba». Los editores ven además un anexo con el detalle.
9. Solo para el permiso D: apartado «Disponibilidad estimada (prueba)», con el KPI ponderado, la cobertura, la tabla de menor a mayor, las exclusiones, los días bloqueados y las limitaciones (FR-046a).

## Excel (`CorporateWorkbook`)

| Hoja | Contenido | Quién la recibe |
|------|-----------|-----------------|
| Resumen | Período, corte, indicadores, Top 10 y pasteles en forma de tabla | Todos |
| Por cargador | Código, nombre actual, días afectados, porcentaje de días del período (días afectados sobre los días calendario del rango hasta el día del corte, incluido hoy; en un período cerrado, todos sus días), ventanas, minutos y h:mm | Todos |
| Ventanas | Código, nombre actual, etiqueta del hecho, inicio, fin, duración total, minutos dentro del rango y franja | Todos (solo CONFIRMED) |
| Fuera de oficiales | Cantidades por estado, con los rótulos del apartado 8 del PDF, y la fila «Total por revisar» al final de las cantidades. La única diferencia de rótulo es «Eventos abiertos (duración provisional)», que en el Excel lleva ese sufijo porque la hoja trae también sus minutos. Los editores reciben además el detalle por ventana o fila | Cantidades y total: todos. Detalle: editores |
| Disponibilidad | Lo mismo que el apartado 9 del PDF | Solo con el permiso D |

**Comprobación**: la suma de «minutos dentro del rango» de la hoja Ventanas es igual al tiempo confirmado de Resumen, y lo mismo vale por cargador (FR-039, SC-006).

## Informes archivados

- Si `kind = INTERMITTENCY` y el perfil no es `OFFICIAL_ONLY`, un usuario sin `canReadOperational` (hoy, sin Administrador ni Analista) recibe un 403 con explicación y la opción de emitir un informe nuevo (Feature 004). El original no se modifica.
- Las correcciones posteriores no modifican los archivos ya emitidos (FR-040).
