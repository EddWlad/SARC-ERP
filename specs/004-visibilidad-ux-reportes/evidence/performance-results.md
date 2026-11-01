# Rendimiento 004

Fecha 2026-11-01. Portátil Windows, 8 hilos lógicos, 24 GB; backend, PostgreSQL y cliente en la misma máquina, por loopback. Sandbox con 250.000 sesiones en un mes y un equipo, `-Xmx2g`, runner `feature004-performance.mjs` como analista: 30 trabajadores, 2 calentamientos, 3 rondas; reportes de 2 días. No es prueba de capacidad del VPS.

| Medida | Meta | Antes | Después 1 (frío) | Después 2 |
|---|---|---:|---:|---:|
| Sesiones p95 | ≤2 s | 3.550 ms | 7.163 ms | 11.167 ms |
| Tablero p95 | ≤5 s | 20.857 ms | 43.494 ms | 27.556 ms |
| PDF | sin SLA | 1.500 ms, 102.660 B | 1.525 ms, 107.239 B | 973 ms, 107.238 B |
| XLSX | sin SLA | 3.342 ms, 930.307 B | 10.267 ms, 938.068 B | 3.252 ms, 938.063 B |

Un solo usuario con esos datos: sesiones 0,6–1,0 s, tablero 1,2–2,2 s. Memoria del proceso medido 467–554 MB. Sin OOM ni archivos dañados. Más de 100.000 filas: 422 esperado. CPU por proceso no se resumió con fiabilidad; tiempo y red local no se aislaron.

## Optimización y remedición, 2026-11-01

Cambios: tablero con una consulta agrupada oficial y una por estados, en lugar de unas 14; ranking del tablero consulta el directorio solo para equipos dentro del corte; conteo del listado por índice de periodo (`count(1)`), sin leer filas; V012 añade `ix_energy_session_started`.

Misma máquina y runner. Corrida 1 inmediatamente después de arrancar; 2 y 3 sobre el mismo proceso. Sobre el sandbox de 500 equipos: mismas 250.000 sesiones repartidas por SQL en la base desechable, con `VACUUM ANALYZE`, 1.508 grupos diarios; sin reinicio, por eso no hay corrida en frío de ese conjunto.

| Conjunto | Corrida | Sesiones p95 | Tablero p95 | PDF | XLSX |
|---|---|---:|---:|---:|---:|
| 1 equipo | 1 (frío) | 4.855 ms | 4.334 ms | 1.433 ms | 4.516 ms |
| 1 equipo | 2 | 650 ms | 4.954 ms | 849 ms | 1.951 ms |
| 1 equipo | 3 | 595 ms | 4.936 ms | 712 ms | 2.492 ms |
| 500 equipos | 1 | 689 ms | 4.277 ms | 790 ms | 1.727 ms |
| 500 equipos | 2 | 390 ms | 4.259 ms | 709 ms | 1.524 ms |
| 500 equipos | 3 | 451 ms | 4.064 ms | 705 ms | 1.769 ms |

Sin consultar el directorio solo para el corte, 500 equipos daba tablero 6.017–14.362 ms: el fixture de un equipo ocultaba ese coste.

## Dictamen

**SC-010 cumplido en este portátil tras calentar el proceso.** Sesiones holgado; tablero dentro de meta con margen corto: 4.954 ms contra 5.000 ms. **Primera carga tras arrancar:** sesiones 4.855 ms, sobre la meta de 2 s; causa no aislada (JVM fría o estadísticas sin analizar tras la carga masiva). Hikari sigue en 10 conexiones.

No medido: CPU, RAM y red en esta remedición, ni más de 500 equipos, ni periodos largos bajo carga. No es prueba de capacidad del VPS; ahí sigue sin demostrarse.

Inicial 341,67 kB, dentro de 500 kB.
