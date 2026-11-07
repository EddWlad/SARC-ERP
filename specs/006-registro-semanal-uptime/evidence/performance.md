# Rendimiento006 — medición ejecutada

2026-11-06, Windows, Intel Core i5-1135G7 (4núcleos/8hilos), Java25.0.3, PostgreSQL18 Testcontainers/Docker, MockMvc de la aplicación real, FileStorage simulado. No se usó dev. Datos sintéticos; cifras son muestras locales, no un SLA ni una prueba de proveedor.

[Salida completa con EXPLAIN ANALYZE BUFFERS](performance-raw.txt).

| Volumen | Resumen semanal p95 | Ranking informe p95 | PDF semanal p95 | XLSX semanal p95 |
|---|---:|---:|---:|---:|
|300 conectores,104 semanas|239 ms|134 ms|1679 ms|2809 ms|
|1000 conectores,104 semanas|581 ms|197 ms|872 ms|629 ms|

20 rondas de consultas,5 de reportes; incluyen calentamiento, sin descartar muestras. Semilla final156.000/520.000 cabeceras (104 semanas ×4 ventanas conocidas +1 referencia por conector). Las líneas de volumen del raw se escriben antes de añadir referencias:124.800 y447.200 (esta última ya incluye31.200 referencias de la fase300); no confundirlas con el volumen final.

- EXPLAIN representativo de la proyección semanal: 5,434 ms a1000; filtrado por referencia y acceso por identidad/revisión. Es una consulta representativa, no se declara que cubra todos los planes SQL del endpoint. No se añadió índice preventivo.
- Importación2000 filas conocidas: vista previa7866 ms, confirmación59447 ms,2000 APPLIED. Es asíncrona y supera objetivo5s; no certificar rendimiento del proveedor ni ocultar la duración.
-30 solicitudes de summary semanales simultáneas (pool30, barrera común): p95 2424 ms,max2455 ms. Misma sesión QA, no30 usuarios independientes; no certifica concurrencia multiusuario.
- Camino general de historial conocido: a1000, summary p95 5879 ms,red30777 ms,ranking7348 ms,equipo6067 ms,PDF5138 ms,XLSX5250 ms. Supera objetivo2s/5s; proyección completa e historial grande requieren una optimización separada y medición controlada sin tareas competidoras. No cambiar cálculos ni ampliar esta feature para esconder el resultado.
- Durante parte del benchmark se ejecutaron tests/build Angular en el mismo equipo; esto limita la atribución de picos. No comparar p95 como mejora/regresión estadística contra005 sin condiciones idénticas.
- Frontend producción: bundle inicial342,14 kB,transferencia95,01 kB, por debajo500 kB.

Transición durable: Feature006TransitionPerformanceBench ejecutó3bloques de500 candidatos (1500decisiones conservadoras persistidas),18484/19298/24355ms; p95 de3muestras es el máximo24355ms, no una estimación poblacional. [Salida](transition-performance-raw.txt). ImportJobs síncrono en este arnés, sin IO de originales; supera5s por bloque y se mantiene como procesamiento asíncrono visible en la UI. No se usa su latencia local para prometer velocidad de lectura de originales en almacenamiento real.
