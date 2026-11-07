# Contrato: reportes de uptime y conservación de Consumo

**Revisión incremental 2026-11-04.** Conserva `GET /api/v1/uptime/report?format=PDF|XLSX`, filtros comunes y descarga autenticada. Sigue kind UPTIME, perfil OFFICIAL_ONLY y mismo contenido permitido para los cinco roles. El contenido operativo restringido no se incorpora a las gráficas.

## Snapshot y archivo

UptimeQueryService/UptimeCalculator y proyecciones de referencia alimentan un UptimeReport común en REPEATABLE READ. Render fuera de transacción; ReportArchive guarda nueva copia, corte e identidad. Respuesta con X-Report-Id, X-Report-Cut-Off y private/no-store. Corregir después no modifica ID/bytes/hash/ref/corte anteriores.

ReportPublication admite referenceWeek y temporalScope como claves de uptime validadas. No inventar from/to reales para cumplir un constructor: conservar criterios de fecha de consulta con significado explícito y límites reales nullable en el contenido. Una semana de referencia se almacena como tal. Valores y claves de Consumo/Inventario mantienen sus reglas.

## Contenido

1. Identidad corporativa, zona, corte y filtros; rango real solo cuando se conoce.
2. Semanas oficiales exactas, si existen, sin cambio de cálculo.
3. Capturas PROVISIONALES con horario real: período, agregados por minutos y resultados.
4. Informe PROVISIONAL sin horario: semana de referencia, «Horario real no informado», «Promedio del informe» y método de media por conectores. Sin minutos evaluados o desconexión deducidos de porcentajes.
5. Ranking y equipos dentro de cada apartado, con cobertura; no mezcla de poblaciones ni KPI conjunto.
6. Tendencia oficial existente con huecos reales; no construir tendencia oficial desde referencias sin horario.
7. Cobertura y cantidades por estado, incluido el total independiente «Pendientes de vincular (sin atribuir)» para la selección, estable ante filtros de atribución.
8. Tiempos de estados efectivamente informados y su completitud, cuando se representen; nunca inventar Ocupado. Mantener datos tabulares accesibles. El requisito nuevo de pastel es de dashboard/detalle; no amplía automáticamente a nuevos gráficos de PDF/Excel.

Formato PDF: reutilizar CorporatePdf, Kpi y helpers existentes; separar y rotular apartados. Excel: mantener las siete hojas existentes — Resumen, Semanas oficiales, Provisionales, Por conector, Por equipo, Cobertura y exclusiones, Gráficos — añadiendo columnas/referencia/método donde corresponda, sin romper estructura/fórmulas/celdas numéricas ni representación de null frente a cero. Sin nuevas librerías.

En ambos, un porcentaje o duración ausentes se imprimen como no informados. Capturas con horarios distintos conservan su rango; informe sin horario no se presenta como lunes 00:00 a lunes siguiente. Sin suma válida no crear porcentajes visuales artificiales. Los datos de las dos fuentes no se suman como si fueran sesiones distintas del inventario.

## Consumo y reporting general

Se conserva la entrega US6 anterior: PDF y resumen Excel sin acceso operativo no muestran Aprobadas sobre umbral; detalle Excel y nota de dashboard permanecen. Este ajuste no cambia cálculos, importación, energía nula/0, aprobación sobre umbral, empates, permisos ni archivos históricos de Consumo.

Reportes archivados UPTIME mantienen etiqueta/filtro propios; no ofrecerles diálogo de regeneración con parámetros de Consumo. Ningún DTO expone URL reutilizable, firma, UID/PUK u OCPP.

## Pruebas de contrato

Igualdad de cifras del dashboard y documentos en un snapshot: conocidos ponderados; informe con 0/100 → 50%; mezcla visible en apartados separados sin agregado conjunto; nulos reales; cobertura/sin atribuir; nombres actuales y etiqueta D12; cinco roles; archivo inmutable tras corrección; consulta vacía; 366/367 días; legacy declarado sin presentarlo como observado nuevo. Abrir Excel con el entorno Windows definido por el proyecto y registrar evidencia, sin prometer capacidades no comprobadas.
