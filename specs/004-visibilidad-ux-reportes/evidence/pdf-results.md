# Resultados PDF 004

Fixture sintética de 366 días con nulo, cero, día sin sesiones, nombres largos y empate 4-4-4. Render con Windows.Data.Pdf: páginas 1, 2, 3, 4 y 6 inspeccionadas en la versión gerencial (19 páginas en total).

| Comprobación | Resultado |
|---|---|
| Seis representaciones con título y unidad | PASS, texto extraído y render |
| 366 días partidos en 6 tramos, misma escala | PASS |
| Energía nula como hueco, cero como 0 | PASS, visible en tramo 1 |
| Sin datos / Sin sesiones en barras | PASS |
| Empates con posición repetida | PASS |
| Sin solapes ni recortes en páginas vistas | PASS |
| Contenido operativo ausente para gerencial | PASS por texto extraído |
| Período, zona y corte | PASS: nota de gráficos y pie de cada página |


Segunda ronda: las 19 páginas de ambas audiencias inspeccionadas en hojas de contacto, sin recortes ni solapes. Tablas partidas repiten cabecera.

Cotejo con API, 2026-10-31, octubre: PDF, Excel y resumen coinciden en 2.240,00 kWh, 10 sesiones, 7 equipos y 248,89 kWh/sesión, para gerente y analista, 1 aprobada sobre umbral (1.700 kWh). `to` del API es exclusivo; el informe muestra último día incluido, por eso `to=31` imprime 30/10.

Corregido: título «Gráficos oficiales» huérfano en operador (`ensureSpace`, captura `captures/pdf-operador-p1-despues.png`) y concordancia de «1 sesión oficial».

Límite: cotejo con el resumen del API y con el tablero que muestra esos mismos datos, a corte distinto de pocos segundos. Las hojas de contacto se vieron reducidas: sirven para maquetación, no para leer cada cifra.
