# Rendimiento (T123)

Medido el 2026-11-01 con `Feature005PerformanceBench` (`-Dsarc.qa.performance=true`). PostgreSQL 18 en Testcontainers sobre Windows 11 y Docker local, peticiones secuenciales por MockMvc con la pila Spring completa. Datos R-18: 104 semanas y 3 ventanas móviles por semana y conector. Sin red, sin concurrencia y sin 30 usuarios: no demuestra los objetivos del TRD bajo carga.

## Resultado final, p95

| Operación | 300 conectores (124.800 mediciones) | 1.000 conectores (416.000 mediciones) | Referencia TRD |
|---|---|---|---|
| Dashboard, apertura | 1.035 ms | 2.370 ms | 5 s |
| Dashboard, por red | 879 ms | 2.246 ms | 5 s |
| Ranking por conector | 888 ms | 2.446 ms | 2 s |
| Resumen por equipo | 879 ms | 2.126 ms | 2 s |
| Grilla de captura | 92 ms | 125 ms | 2 s |
| Reporte PDF (5 rondas) | 1.666 ms | 2.564 ms | Sin objetivo |
| Reporte Excel (5 rondas) | 3.036 ms | 2.661 ms | Sin objetivo |

Con 1.000 conectores, ranking y resumen por equipo superan 2 s en secuencial. El dashboard queda dentro de 5 s.

Importación de 2.000 filas: vista previa 7,4 s; confirmación 52,8 s, con 2.000 mediciones creadas. Es trabajo en segundo plano con progreso, unas 26 ms por fila.

## Defecto corregido

Primera medición: apertura p95 6,1 s con 300 conectores y 10,7 s con 1.000; resumen por equipo 17,4 s. Causa: `findFacts` carga hasta 366 días y usaba proyección JPA por interfaz. Se reemplazó por mapeo JDBC en `MeasurementRepositoryAdapter`, misma consulta y sin migración. Mejora de unas 5 veces.

Cada lectura sigue cargando todo el período y filtrando en memoria. Si el volumen real se acerca a 1.000 conectores, conviene filtrar el alcance en SQL antes de plantear agregados persistidos.
