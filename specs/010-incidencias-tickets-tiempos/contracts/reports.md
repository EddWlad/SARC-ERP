# Contrato: Informes de incidencias

**Feature**: `010-incidencias-tickets-tiempos` | **Requisitos**: FR-070 a FR-086 | **Endpoints**: `/api/v1/incidents/reports/*` ([incidents-api.md](incidents-api.md))

Ambos informes se generan con la infraestructura compartida:

- `CorporatePdf` (OpenPDF), con los logos `reports/logos/eos-tech.png` a la izquierda y `reports/logos/autoeastern.jpg` a la derecha;
- `CorporateWorkbook` (POI);
- `ReportArchive.archive(IssuedReportFile)`, que guarda la copia privada del archivo entregado, su SHA-256 y la auditoría `{KIND}_REPORT_GENERATED`.

El informe archivado es el registro inmutable de lo que se mostró (FR-074a, FR-086). Las correcciones posteriores no lo modifican.

## Datos comunes

| Dato | Regla |
|------|-------|
| Corte (`cutOff`) | Un solo `Instant` tomado al iniciar la generación. Todas las consultas usan los hitos con `recorded_at ≤ cutOff`, en una misma transacción de solo lectura `REPEATABLE READ`, para que el PDF, el Excel y la pantalla coincidan |
| Rango | `[S, E)`: `S` es el inicio del día `from`, `E` el inicio del día siguiente a `to` (`America/Guayaquil`). Si `E > cutOff`, se usa `E = cutOff` para el estado (FR-074) |
| Encabezado | Período exacto o «Todo el historial», filtros activos, «Zona horaria: America/Guayaquil», «Corte: dd/MM/yyyy HH:mm» |
| Nombres | Nombre actual del equipo (D12). Red conservada en T0. «Sin red» para chatbot y elementos del sitio |
| Archivo | `control-tickets_{yyyyMMdd}-{yyyyMMdd}.pdf` o `control-tickets_todo-el-historial_{yyyyMMdd-HHmm}.pdf`; `gestion-incidencias_{yyyyMMdd}-{yyyyMMdd}.xlsx` |
| `IssuedReportFile.kind` | `INCIDENT_CONTROL` o `INCIDENT_MANAGEMENT` (nuevos en `IssuedReportKinds`, no restringibles: los cinco roles los descargan) |
| `ReportPublication` | Criterios `{selectionBasis: ACTUAL_RANGE (con `from` y `to`, este con fin exclusivo, como los demás informes)\|ALL_HISTORY, from?, to?, ticketState?, network?, elementClass?, incidentType?, priority?}`, más `rowCount` y `cutOff` en `details` |

## 1. Control de tickets (FR-070 a FR-077)

**Selección**:

- Sin período: todos los tickets creados en SARC (P-16).
- Con período: los tickets incluidos según FR-073, es decir, los que tienen algún episodio con `T0 < E` y (`T4` ausente o `T4 ≥ S`).
- Filtros opcionales: estado al final del período y red.
- Cada ticket aparece una sola vez.

**Orden**: número ascendente.

**PDF** (A4 vertical), de arriba abajo:

1. Fila de logos: EOS Tech Company a la izquierda y Autoeastern a la derecha.
2. Título «CONTROL DE TICKETS».
3. Subtítulo «Electromovilidad | Puntos de carga y electrolineras».
4. Banda «REGISTRO OPERATIVO».
5. Línea de metadatos: período, filtros, zona horaria y corte.
6. Tabla de tres columnas:

   | NÚMERO DE TICKET | CARGADOR O ELECTROLINERA | ESTADO DEL TICKET |
   |---|---|---|
   | TICKET-086 | Electrolinera X / Conectores 1 y 2 | Abierto |
   | TICKET-087 | Servicio de chatbot | Cerrado |
   | TICKET-088 | CC El Bosque / Barrera vehicular | Abierto |

   - Segunda columna según FR-075.
   - El estado se calcula al final del período con `TicketStateAt(E)`; sin período, al corte.
   - El estado va como texto («Abierto» o «Cerrado»), con un relleno suave de la paleta corporativa (no el único indicador, por accesibilidad).
7. Pie: «Total: N · Abiertos: A · Cerrados: C», y paginación «Página x de y».
8. Sin tickets: el encabezado completo y el mensaje «Sin tickets en el período».

**Excel**: una hoja `Control` con el mismo encabezado en las filas 1 a 5, las mismas tres columnas y las mismas filas en el mismo orden, y los totales al final. Una hoja `Criterios` repite período, filtros, zona, corte y huella de selección.

**Pantalla**: la vista «Control de tickets» muestra la misma tabla con `list-page`. Usa el mismo endpoint de lista con `from`/`to` y la columna `stateAtPeriodEnd`, y lleva el pie de totales.

## 2. Gestión de incidencias (FR-080 a FR-086)

**Selección**:

- Los indicadores de tiempo usan los episodios con `T0 ∈ [S, E)` (FR-084).
- El resumen agrega «abiertos al corte».
- El período inicial es «Este mes» (D13).
- Filtros: red, clase de elemento, tipo y prioridad.

**PDF corporativo** (A4 horizontal), por secciones:

1. Portada con logos, título «Gestión de incidencias», período y corte.
2. **Resumen ejecutivo**:
   - tickets únicos, episodios, abiertos, cerrados, reabiertos y críticos;
   - «Cerrados por duplicado o error», que se informa aparte y queda fuera de los promedios.
3. **Indicadores de tiempo**. Una fila por medida: T1−T0, T2−T0, T3−T0 (recuperación), T4−T0 (cierre) y T4−T3. Columnas:
   - promedio en minutos;
   - `n` (denominador);
   - excluidos por «No aplica», por «Sin dato» y por episodio abierto.

   Debajo, el «% de validaciones ≤ 5 min», con su denominador.
4. **Referencias operativas**, con la leyenda «Referencia operativa, no SLA contractual» y la versión de referencias. Por medida y tipo, se cuentan:
   - dentro de referencia;
   - fuera de referencia;
   - no evaluables;
   - sin referencia.

   Los episodios fuera de referencia se listan con su justificación.
5. **Gráficos** sin ponderar (P-07), cada uno con su `n`. Se omiten si no hay datos. Son barras o columnas por:
   - fecha (T0 por día o semana);
   - red;
   - tipo;
   - prioridad;
   - visitas por etapa;
   - reaperturas;
   - tiempos excesivos;
   - equipos repetidores (los 10 primeros por episodios, sin etiqueta de «recurrente»).
6. **Repartos**, en tablas por tipo, red, clase de elemento, equipo, prioridad y origen.
7. **Visitas**: solicitadas, confirmadas, canceladas o aplazadas, y ejecutadas.
8. **Detalle por ticket y episodio**. Columnas:
   - código, episodio, elemento, tipo, prioridad;
   - T0 a T4, cada uno con su marca: `R` registrado, `R*` retroactivo, `N/A` no aplica, `S/D` sin dato, `—` pendiente;
   - tiempos derivados (vacío, nunca 0, si faltan hitos);
   - referencia aplicable, resultado y desviación.
9. **Fuentes externas** (P-13): un recuadro con enlaces textuales a Uptime (disponibilidad oficial validada) y a Intermitencias (tiempo fuera de línea y disponibilidad estimada «de prueba»). Este informe no muestra cifras de esos módulos, para no presentar estimaciones como oficiales.
10. **Metodología**:
    - definición de cada medida;
    - reglas de inclusión y exclusión;
    - redondeo de minutos hacia abajo;
    - inicio del rango inclusivo y fin exclusivo;
    - versión de referencias y rótulo P-02.

**Excel**: una hoja por bloque, con encabezados en la fila 1, filtros automáticos e instantes como fecha y hora de Excel en zona local. Cada hoja lleva identificadores para cruzar filas entre hojas.

| Hoja | Una fila por | Columnas clave |
|------|--------------|----------------|
| `Resumen` | Indicador | indicador, valor, denominador, excluidos, nota |
| `Tickets` | Ticket | código, estado actual, estado al final del período, clase, elemento, red en T0, tipo, prioridad, origen, responsable, episodios |
| `Episodios` | Episodio | código, ordinal, estado, T0 a T4 (valor y marca), cinco duraciones, versión de referencias, resultado de referencia por medida, desviación, resultado de cierre, causa |
| `Bitácora` | Evento | código, episodio, tipo, ocurrió, registrado, autor, retroactivo, anulado, texto |
| `Comunicaciones` | Comunicación | código, canal, tipo, enviado, registrado, autor, retroactiva |
| `Visitas` | Etapa de visita | código, proveedor, etapa, hora, nota |
| `Metodología` | Regla | texto |

Cada cifra de `Resumen` se puede reproducir con un conteo o un promedio sobre `Episodios` (FR-085). Una prueba lo comprueba.

Las columnas «responsable» y «autor» muestran el nombre actual del usuario al momento del corte (`IncidentStaffDirectory.namesOf`), igual que el tablero y el detalle. Si un usuario cambia de nombre, los informes ya emitidos conservan su contenido y su huella, y solo los nuevos muestran el nombre nuevo.

## Coherencia (FR-086)

- El tablero (`GET /summary`), el PDF y el Excel usan el mismo `IncidentReportQuery` y los mismos objetos de dominio (`IncidentKpiCalculator`, `TicketStateAt`). No hay cálculos en SQL que dupliquen las reglas.
- Una prueba de integración genera los tres con los mismos filtros y el mismo corte simulado, y compara las cifras una por una.
- La reconstrucción de un informe emitido se hace en dos niveles:
  - **Prueba de fidelidad**: se descarga la copia archivada (`report_issued`, con su SHA-256).
  - **Prueba de reglas**: `MilestonesAsKnownAt(cutOff)` regenera las filas, y su huella de filas (SHA-256 de un CSV canónico de filas con identificadores, estados, instantes y minutos, sin nombres visibles, que pueden cambiar después por D12; se guarda en `details.rowsSha256`) debe coincidir con la del informe emitido. Esto demuestra que una corrección posterior a la emisión no cambia lo que el informe mostró.
