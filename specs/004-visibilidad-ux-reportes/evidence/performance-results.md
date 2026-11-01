# Rendimiento 004

Fecha 2026-11-01. Portátil Windows, 8 hilos lógicos, 24 GB; backend, PostgreSQL y cliente en la misma máquina, por loopback. Sandbox con 250.000 sesiones en un mes y un equipo, `-Xmx2g`, runner `feature004-performance.mjs` como analista: 30 trabajadores, 2 calentamientos, 3 rondas; reportes de 2 días. No es prueba de capacidad del VPS.

| Medida | Meta | Antes | Después 1 (frío) | Después 2 |
|---|---|---:|---:|---:|
| Sesiones p95 | ≤2 s | 3.550 ms | 7.163 ms | 11.167 ms |
| Tablero p95 | ≤5 s | 20.857 ms | 43.494 ms | 27.556 ms |
| PDF | sin SLA | 1.500 ms, 102.660 B | 1.525 ms, 107.239 B | 973 ms, 107.238 B |
| XLSX | sin SLA | 3.342 ms, 930.307 B | 10.267 ms, 938.068 B | 3.252 ms, 938.063 B |

Un solo usuario con esos datos: sesiones 0,6–1,0 s, tablero 1,2–2,2 s. Memoria del proceso medido 467–554 MB. Sin OOM ni archivos dañados. Más de 100.000 filas: 422 esperado. CPU por proceso no se resumió con fiabilidad; tiempo y red local no se aislaron.

## Dictamen

**SC-010 no cumplido en consulta y tablero.** Antes tampoco cumplía. Las dos corridas posteriores son peores que la base, pero varían hasta 56% entre sí: no prueban ni descartan regresión de 004. Hay un cambio sospechoso: `summary` ahora mantiene una conexión en REPEATABLE_READ durante toda la consulta; con 30 concurrentes y pool limitado puede añadir espera. No se comparó en la misma sesión contra la base.

Inicial 341,67 kB, dentro de 500 kB.
