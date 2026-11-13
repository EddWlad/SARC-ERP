# Feature Specification: Intermitencias de puntos de carga KIA

**Feature**: `008-intermitencias-kia`

**Feature Branch**: `main` (rama existente; este comando no crea ni cambia ramas)

**Created**: 2026-11-10

**Status**: En implementación (US1 a US6 implementadas y verificadas con pruebas; ensayo de la migración sobre la última copia de producción, revisión en navegador y ensayo de restauración con originales hechos el 2026-11-13; falta la publicación y la medición con una persona de SC-001, SC-008 y SC-010). Las quince decisiones de negocio fueron resueltas por el responsable del producto el 2026-11-10 (ver Clarifications). Seis decisiones de implementación (la forma de los repartos en el PDF, el tope de 50.000 ventanas por informe, el alcance impreso y el total por revisar de los informes, el alcance de la disponibilidad desde la fecha de alta y el marcado de días históricos) quedaron registradas como aclaraciones 16 a 21 de CC-PRD-008 y ratificadas por el responsable el 2026-11-13.

**Input**: Módulo RF-005 de intermitencias de puntos de carga KIA. Al terminar su turno, los analistas registran las ventanas de intermitencia de cada día, por importación o captura manual, para reemplazar progresivamente el Excel operativo. El módulo ofrece seguimiento, dashboard e informes gerenciales PDF/Excel. El indicador principal es el tiempo acumulado fuera de línea; la cantidad de ventanas es secundaria. Se agrega una disponibilidad estimada de prueba con criterio Enerlink, visible solo para Analistas (y Administradores, según la aclaración Q1) hasta nueva orden. Fuentes revisadas: Constitución 1.2.0, PRD v1.1 (§3, §4, §6.3, RF-005, §11, §16), control de cambios (CC-PRD-008-20261110), especificaciones 001–007, módulos de Inventario, Consumo, Uptime e Informes, y las plantillas `Intermitencia_copiamos_nube.xlsx` e `Informe_Intermitencias_01-10-2026_15-10-2026.xlsx`. No define tablas, migraciones, endpoints ni estructura de código.

## Clarifications

### Session 2026-11-10

- Q: ¿Un Administrador sin rol Analista ve la disponibilidad estimada? → A: Sí. La ven quienes tengan el rol Analista o el rol Administrador. Ningún otro rol, solo o combinado, la ve.
- Q: ¿Cada día cubierto aporta 24 horas o solo el horario de atención del inventario? → A: 24 horas (1.440 minutos) por día cubierto, como la medición Enerlink de CA-004-01.
- Q: ¿Cómo se acredita un día revisado? → A: Al cerrar su turno, un Analista o Administrador marca el día como «revisado» para toda la red KIA. Los puntos en alcance sin ventanas ese día cuentan con 0 minutos fuera de línea; los días sin esa marca quedan «sin datos».
- Q: ¿Una ventana de intermitencia puede durar más de 24 horas? → A: Sí, sin límite. Toda ventana de 4 horas o más queda «Por revisar» hasta que un editor confirme que es real.
- Q: ¿Quién puede confirmar que una ventana «Por revisar» es real? → A: Cualquier Administrador o Analista, incluso quien la registró, con justificación obligatoria.
- Q: ¿Se admiten ventanas con instantes futuros? → A: No. Se rechazan las ventanas cuyo inicio o fin sea posterior al momento en que se guardan, se validan en la vista previa, se cierran o se confirman (FR-021b).
- Q: Una fila pendiente de vincular tiene ventanas en un día marcado como revisado, sin que se sepa qué punto afecta. ¿Cómo afecta la cobertura? → A: El día deja de contar como cubierto para todos los puntos hasta que se resuelva el vínculo.
- Q: En el pastel por duración, ¿en qué franja cae una ventana que atraviesa el límite del período y qué tiempo aporta? → A: La franja se decide por la duración total de la ventana; solo suma los minutos dentro del período.
- Q: ¿Cómo se presenta la disponibilidad estimada? → A: En una sección propia, «Disponibilidad estimada (prueba)», con KPI agregado y cobertura, tabla por punto de menor a mayor disponibilidad y exclusiones. En PDF y Excel va como apartado separado.
- Q: En la barra de ventanas por día, ¿cómo se cuenta una ventana que cruza la medianoche? → A: En cada día que toca, igual que «ventanas completas» por rango. La suma de las barras puede superar el total de ventanas del período, y el gráfico lo indica.
- Q: ¿Qué significa marcar un punto como prueba? → A: Es una operación sobre un rango de fechas: marca como Prueba las ventanas de ese punto en el rango, con justificación, y puede deshacerse sobre el mismo rango. No es una marca permanente del punto; las ventanas posteriores se registran normalmente.
- Q: ¿El día en curso cuenta en el denominador de la cobertura de disponibilidad? → A: No. Los días en alcance son los días del rango ya terminados hasta el corte. Hoy y los días futuros no cuentan como cubiertos ni como sin datos.
- Q: En el Excel, ¿sobre qué días se calcula el «% de días del período» de cada cargador? → A: Sobre los días calendario del rango hasta el día del corte, incluido hoy. En un período cerrado, son todos sus días.
- Q: En la vista previa, ¿puede una ventana sin fin marcarse como prueba y registrarse abierta? → A: No. Una ventana Prueba siempre está cerrada. Se rechaza la combinación con su motivo; el analista la cierra antes o la marca como prueba después de cerrarla.
- Q: ¿Qué estados participan en la regla de no solapamiento, y qué pasa con lo registrado después de una ventana abierta? → A: Todos los estados salvo Anulada. Una ventana abierta ocupa desde su inicio hasta que se cierra; una ventana nueva del mismo punto que empiece después de su inicio se rechaza en la captura o queda en conflicto en la importación, hasta cerrar la abierta o corregir la nueva (FR-014).

## Hallazgos en las plantillas operativas

El registro operativo (`Intermitencia_copiamos_nube.xlsx`, hoja `Intermintecias`) tiene 248 filas del 1 al 18 de octubre de 2026 y 76 puntos KIA con códigos de seis dígitos. Usa las columnas Fecha, Código cargador, Nombre cargador, Comentario (siempre «Intermitencia»), Inicio 1–5, Fin 1–5 y Total inactivo. La fecha es un día de calendario y las horas no llevan fecha; por eso cada instante se forma con la fecha de la fila y la hora de la ventana. Se encontraron estos casos, que la especificación convierte en criterios de aceptación:

Los nombres y los Códigos asignados de los puntos se omiten a propósito: el repositorio es público y esos códigos autorizan la carga. Los puntos se identifican con letras; la correspondencia con el archivo real queda solo en la plantilla local, que no se versiona.

| Fila | Punto | Situación | Total inactivo del archivo | Recalculado desde las ventanas |
|------|-------|-----------|----------------------------|--------------------------------|
| 3 | Punto A, 01/10/2026 | Ventanas 3 y 4 idénticas: 17:55–18:21 | 109 min, cuenta dos veces la ventana | 83 min, si se omite el duplicado |
| 57 | Punto B, 04/10/2026 | Fin 1 (07:05) es anterior a Inicio 1 (17:00) | 846 min | Requiere confirmar que cruza medianoche |
| 61 | Punto C, 04/10/2026 | Fin 2 (19:26) es anterior a Inicio 2 (19:41), probable error de tipeo | 1433 min (casi 24 h) | Sin confirmar no se calcula |
| 94, 240 | Puntos D y E | Fin pocos minutos antes de Inicio | 1441 min | Sin confirmar no se calcula |
| 99 | Punto F, 07/10/2026 | Total no coincide con las ventanas | 1331 min | 98 min |
| 117 | Punto G, 07/10/2026 | Inicio 3 (16:01) sin Fin | 25 min, ignora la ventana | Par incompleto: queda abierto u omitido |
| 155 | Punto H, 12/10/2026 | Fin 4 anterior a Inicio 4; Inicio 5 sin Fin | 1505 min | Por revisar |
| 163 | Punto I, 12/10/2026 | 20:03 a 12:22 del día siguiente | 979 min | 979 min, si se confirma el cruce: 237 min el 12/10 y 742 min el 13/10 |
| 228 | Punto J, 17/10/2026 | Solapamiento: 02:24–02:30 y 02:25–03:11 | 52 min, suma ambas ventanas | 47 min solo tras resolverlo |

El informe gerencial de Excel ordena su «Top 5» por cantidad de ventanas y presenta las horas en decimales (por ejemplo 16,27 h). El PRD (RF-005.10, CA-005-01) y esta solicitud exigen un Top 10 por duración acumulada. Por eso ese criterio de la plantilla **no** se adopta. Del informe sí se conservan sus indicadores (cargadores afectados, días-equipo, ventanas completas, equipos recurrentes desde 3 días, registros a revisar), su resumen ejecutivo en texto y su detalle por cargador. Su hoja REGISTRO ya había eliminado el duplicado de la fila 3 (3 ventanas, 83 min) sin dejar rastro. SARC debe hacer esa misma corrección de forma explícita y trazable.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar manualmente las ventanas de un punto KIA (Priority: P1)

Como Analista de Monitoreo, al terminar mi turno quiero elegir un punto de carga KIA del inventario y registrar todas sus ventanas de intermitencia del día, sin un límite de cinco. Así dejo de usar el Excel y conservo los instantes completos de cada ventana.

**Why this priority**: Sin ventanas fiables no existe ningún indicador. La captura manual es el núcleo mínimo del módulo y no depende de archivos externos.

**Independent Test**: Registrar seis ventanas para un punto KIA en un día, una que cruce la medianoche y otra abierta. Comprobar la duración de cada una, el total diario de cada fecha y que la ventana abierta queda fuera del tiempo confirmado.

**Acceptance Scenarios**:

1. **Given** un punto de carga KIA activo, **When** el analista registra seis ventanas cerradas de un mismo día y guarda, **Then** se guardan seis ventanas confirmadas. Cada una muestra sus minutos y su duración en h:mm, y el total diario es la suma de las seis.
2. **Given** el selector de puntos, **When** el analista busca por código o nombre, **Then** solo aparecen puntos de carga de la red KIA. No aparecen electrolineras ni equipos de JAC o CONECTZ.
3. **Given** una ventana del 15/10/2026 23:50 al 16/10/2026 00:20, **When** se guarda, **Then** existe un solo evento de 30 minutos. Asigna 10 minutos al 15/10 y a la primera quincena, y 20 minutos al 16/10 y a la segunda quincena.
4. **Given** una ventana sin fin, **When** se guarda como abierta, **Then** queda en estado Abierta. Su duración provisional hasta la fecha de corte se muestra aparte y no suma al tiempo confirmado ni al Top 10.
5. **Given** una ventana abierta, **When** un editor registra su fin, **Then** pasa a confirmada con su duración definitiva, o a «Por revisar» si dura 240 minutos o más (FR-012, FR-021a), y conserva el historial de su apertura y su cierre.
6. **Given** una ventana nueva que se solapa con otra del mismo punto, o que es idéntica a otra existente, **When** se intenta guardar en la captura manual, **Then** el sistema la rechaza, explica el conflicto y no suma dos veces el mismo tiempo. En la importación, la misma situación deja la ventana en conflicto o duplicada en la vista previa (FR-014, FR-015).
7. **Given** un fin anterior o igual al inicio, **When** se intenta guardar, **Then** se rechaza con un mensaje comprensible, sin intercambiar las horas ni suponer otro día.
8. **Given** una ventana cuyo inicio o fin es posterior al momento actual, **When** se intenta guardar, **Then** se rechaza indicando el instante futuro.

---

### User Story 2 - Importar el registro operativo con vista previa corregible (Priority: P1)

Como Analista de Monitoreo, quiero cargar el XLSX del registro operativo, un CSV o un bloque pegado. Quiero ver cada ventana interpretada, corregir en la vista previa los datos inválidos y confirmar solo lo correcto, para incorporar un turno completo sin rehacer el trabajo.

**Why this priority**: Es el reemplazo directo del flujo actual y la vía para cargar el histórico existente. Sin ella la adopción depende de recapturarlo todo.

**Independent Test**: Importar el registro operativo y comprobar que sus 248 filas se presentan con las clasificaciones y los casos de la tabla de hallazgos. Las pruebas automatizadas usan una copia sintética con las mismas horas, totales y anomalías, y códigos ficticios. El archivo real `Intermitencia_copiamos_nube.xlsx` solo se usa en una verificación local que no se versiona. Corregir las filas 3, 61 y 228 en la vista previa, confirmar y verificar los totales. Reimportar el mismo archivo y verificar que no se crea ninguna ventana adicional.

**Acceptance Scenarios**:

1. **Given** el XLSX operativo con sus encabezados reales, **When** se carga, **Then** cada par Inicio/Fin con valores se convierte en una ventana. La vista previa identifica archivo, fila, número de par, campo, valor original, interpretación (fecha y hora completas, minutos), clasificación, error y motivo.
2. **Given** la fila 3 del archivo operativo, **When** se muestra la vista previa, **Then** las ventanas 3 y 4 aparecen marcadas como idénticas para revisión, y se muestran el total con el duplicado (109 min, igual al Total inactivo del archivo) y sin él (83 min). Ninguna de las dos se incorpora hasta que el analista decida explícitamente omitir una. Al omitirla, el total recalculado pasa a 83 min y la diferencia con el archivo (109 min) se señala y debe reconocerse antes de confirmar (FR-027).
3. **Given** la fila 61 (Fin 19:26 anterior a Inicio 19:41), **When** se muestra la vista previa, **Then** la ventana queda inválida por posible inversión o cruce de medianoche. No se calcula como 23 h 45 min ni se corrige sola. El analista puede corregir la hora o confirmar expresamente que termina al día siguiente.
4. **Given** la fila 163 (20:03 a 12:22), **When** el analista confirma que termina el día siguiente, **Then** se interpreta una ventana de 979 minutos: 237 minutos para el 12/10/2026 y 742 para el 13/10/2026. Por durar 4 horas o más, queda «Por revisar» hasta que un editor confirme que es real; solo entonces computa.
5. **Given** un dato inválido en la vista previa, **When** el analista edita fecha, código, punto, inicio, fin o comentario y vuelve a validar, **Then** la fila se reclasifica. Se conservan el valor original, el valor corregido, el autor y el momento, y la corrección queda identificada como manual.
6. **Given** una fila con un código inexistente, perteneciente a una electrolinera o a otra red, o asociado a más de un equipo, **When** se muestra la vista previa, **Then** queda pendiente de vincular o fuera de alcance, con su motivo. No se asigna a ningún punto ni suma a las métricas oficiales hasta que un editor elija un punto KIA de forma explícita.
7. **Given** filas válidas y filas con problemas, **When** el analista confirma, **Then** se incorporan únicamente las ventanas válidas y resueltas. Las demás quedan omitidas con su motivo o pendientes para revisar después, con procedencia de archivo, fila y par.
8. **Given** un archivo ya confirmado, **When** se vuelve a importar idéntico, **Then** se crean cero ventanas nuevas y los totales oficiales no cambian. Una ventana del mismo punto que difiere de una existente o se solapa con ella queda en conflicto y exige una resolución explícita.
9. **Given** un bloque pegado desde el Excel o un CSV con las mismas columnas, **When** se carga, **Then** recibe la misma interpretación, vista previa y reglas que el XLSX. Una fila con seis o más pares de Inicio/Fin conserva todas sus ventanas.

---

### User Story 3 - Revisar, corregir y conservar la trazabilidad (Priority: P1)

Como Administrador o Analista, quiero ver los registros por revisar, los abiertos, los de prueba y los pendientes de vincular, y corregir o anular ventanas con justificación. Así las cifras oficiales solo incluyen datos confirmados y siempre puedo reconstruir qué cambió.

**Why this priority**: El PRD exige auditoría de correcciones, duplicados y reimportaciones. Sin esta revisión, los errores del Excel pasarían a los indicadores.

**Independent Test**: Corregir una ventana confirmada, anular otra, marcar un punto de prueba y resolver una pendiente de vincular. Comprobar el historial, el cambio de totales y la exclusión de la prueba.

**Acceptance Scenarios**:

1. **Given** una ventana confirmada, **When** un editor cambia su inicio o su fin con justificación, **Then** se conserva la versión anterior con su autor, momento y motivo. Los totales usan solo la versión vigente.
2. **Given** una ventana registrada por error, **When** un editor la anula con justificación, **Then** deja de computar sin borrarse físicamente y sigue visible en el historial.
3. **Given** ventanas del punto «PRUEBAS CDA» marcadas como prueba, **When** se consulta el dashboard, el Top 10 o la tendencia, **Then** no aparecen en los indicadores oficiales. Se conservan y se cuentan aparte.
4. **Given** una fila pendiente de vincular, **When** un editor elige un punto KIA y confirma, **Then** sus ventanas se validan con las mismas reglas de duplicado y solapamiento y solo entonces pueden confirmarse.
5. **Given** dos editores que corrigen a la vez la misma ventana, **When** el segundo guarda, **Then** recibe un aviso de cambio concurrente y no sobrescribe el trabajo del primero sin volver a revisarlo.
6. **Given** un equipo que cambia de nombre o se desactiva, **When** se consultan sus ventanas anteriores, **Then** se conservan con su identidad. Los informes nuevos usan el nombre actual y el detalle distingue la etiqueta registrada en la fecha del hecho.
7. **Given** una persona sin rol Administrador o Analista, **When** intenta crear, importar, corregir, cerrar, anular o resolver, incluso mediante acceso directo, **Then** la operación se rechaza.

---

### User Story 4 - Consultar el dashboard de intermitencias (Priority: P2)

Como Gerente, Supervisor, Asistente o Analista, quiero un dashboard con el tiempo confirmado fuera de línea, los afectados, la recurrencia, la tendencia y el Top 10 por duración, por quincena o rango. Así entiendo qué puntos KIA se interrumpen más y si la situación mejora.

**Why this priority**: Es el objetivo de seguimiento gerencial, pero depende de que existan ventanas confirmadas (US1 a US3).

**Independent Test**: Cargar un conjunto de prueba con valores esperados calculados a mano, que incluya cruces de quincena, abiertos, pruebas y pendientes. Comparar cada indicador, el Top 10 y los gráficos en las cuatro opciones de período.

**Acceptance Scenarios**:

1. **Given** ventanas confirmadas en el período, **When** se abre el dashboard, **Then** muestra al menos estos indicadores: tiempo confirmado fuera de línea (h:mm y minutos), cargadores afectados, ventanas completas, días-equipo, equipos recurrentes, promedio por ventana, registros por revisar y eventos abiertos con su duración provisional aparte.
2. **Given** un equipo A con 36:42 de intermitencia en 4 ventanas y un equipo B con 12 ventanas que suman menos tiempo, **When** se consulta el Top 10, **Then** A queda por encima de B (CA-005-01). Las ventanas y los días afectados se muestran como columnas complementarias.
3. **Given** tres puntos empatados en duración en el décimo lugar, **When** se consulta el Top 10, **Then** se incluyen los tres y se indica que el ranking tiene más de diez puntos por empate.
4. **Given** un punto con ventanas en 3 o más días calendario distintos del período, **When** se consulta, **Then** cuenta como recurrente. Con 2 días no cuenta.
5. **Given** la elección de primera quincena, segunda quincena, últimos 15 días móviles o rango personalizado, **When** se aplica, **Then** el dashboard rotula el tipo y el rango real, con inicio inclusivo y fin exclusivo, y asigna a cada período solo su porción de tiempo.
6. **Given** un período sin ventanas confirmadas, **When** se consulta, **Then** se informa que no hay intermitencias confirmadas en el período. No se dibujan gráficos con ceros que sugieran una medición completa, y los conteos por revisar o abiertos se siguen mostrando.
7. **Given** una corrección confirmada, **When** se vuelve a consultar el dashboard, **Then** las cifras la reflejan y la fecha de corte o cálculo es visible.
8. **Given** el dashboard en pantallas de 360 a 1440 píxeles, en tema claro u oscuro, **When** se usa, **Then** conserva la estructura, los colores, la tipografía y los componentes de los dashboards de Consumo y Uptime, sin elementos que parezcan de otra aplicación.

---

### User Story 5 - Descargar informes gerenciales PDF y Excel (Priority: P2)

Como persona autorizada a consultar, quiero descargar un PDF corporativo y un Excel con los mismos filtros y cifras del dashboard, para enviarlos por correo a Gerencia.

**Why this priority**: Reemplaza el informe quincenal de Excel, pero solo sirve si los datos y el dashboard ya son fiables.

**Independent Test**: Para varias selecciones de período y equipo, descargar el PDF y el Excel y cotejar el 100 % de sus cifras con el dashboard del mismo corte.

**Acceptance Scenarios**:

1. **Given** unos filtros y un corte, **When** se descarga el PDF, **Then** incluye los logos de EOS Tech Company y Autoeastern, el tipo y el rango del período, la zona horaria, la fecha de corte, un resumen ejecutivo, los indicadores, la tendencia, los tres repartos (barras de participación con leyenda, FR-036), el Top 10, notas de metodología y un apartado separado con abiertos, ventanas por revisar, filas en conflicto, filas por vincular, prueba y el total de registros por revisar.
2. **Given** los mismos filtros y corte, **When** se descarga el Excel, **Then** contiene un resumen, el detalle por cargador y el detalle por ventana, con su porción de minutos dentro del rango, para que cada total pueda comprobarse con una suma.
3. **Given** un Gerente, Supervisor o Asistente sin Administrador ni Analista, **When** descarga un informe, **Then** recibe las cifras oficiales y las cantidades de los apartados no oficiales, sin detalle operativo, observaciones ni justificaciones.
4. **Given** un informe descargado, **When** se necesita enviarlo, **Then** el usuario lo adjunta por su cuenta. SARC no envía correos en esta feature.

---

### User Story 6 - Explorar la disponibilidad estimada de prueba (Priority: P3)

Como Analista de Monitoreo o Administrador, quiero ver una disponibilidad estimada por punto KIA, calculada con el criterio de Enerlink a partir de las intermitencias registradas, para explorar cómo se vería en puntos que no la tienen en Enerlink. Su cobertura y sus limitaciones deben ser visibles.

**Why this priority**: Lo pidió el responsable como prueba restringida. No es un indicador oficial y depende de que los editores marquen los días revisados (FR-044).

**Independent Test**: Preparar días marcados como revisados con y sin ventanas, días sin marca y un evento abierto. Verificar el porcentaje esperado, la cobertura declarada, que un día sin marca nunca cuente como 100 % y que ningún rol distinto de Analista o Administrador reciba el indicador.

**Acceptance Scenarios**:

1. **Given** un punto con días cubiertos que suman un tiempo evaluado T (1.440 minutos por día) y X minutos confirmados fuera de línea en esos días, **When** un Analista o Administrador consulta, **Then** ve la disponibilidad estimada (T − X) / T × 100 con dos decimales, rotulada «Disponibilidad estimada por intermitencias (prueba)», con su cobertura, su origen y sus limitaciones.
2. **Given** un período sin días cubiertos para un punto, **When** se consulta, **Then** se muestra «Sin datos» y nunca 100 %.
3. **Given** un usuario sin rol Analista ni Administrador, con uno o varios de Gerente, Supervisor o Asistente, **When** consulta el dashboard, pide los datos directamente o descarga un PDF o un Excel, **Then** no recibe la disponibilidad estimada, su cobertura ni sus valores por punto. El resto del módulo conserva sus permisos.
4. **Given** el módulo de Uptime, **When** se consultan sus dashboards o informes, **Then** sus cifras, rankings y coberturas son idénticos antes y después de esta feature, y la disponibilidad estimada no aparece en ellos.
5. **Given** 10 puntos KIA en alcance y un día marcado como revisado, con un único punto que tuvo 72 minutos confirmados fuera de línea, **When** se consulta ese día, **Then** ese punto muestra 95,00 % ((1.440 − 72) / 1.440), los otros nueve muestran 100,00 % y el agregado muestra 99,50 % (14.328 / 14.400 minutos), con cobertura de 1 de 1 días.
6. **Given** un día del rango sin marca de revisado, **When** se consulta, **Then** ese día no suma tiempo evaluado, la cobertura lo cuenta como no cubierto y ningún punto muestra 100 % por ese día.

---

### Edge Cases

- **Ventanas idénticas en una fila** (fila 3): se detectan y se conservan para revisión. El total recalculado se muestra con y sin el duplicado, y se incorpora una sola ventana solo por decisión explícita.
- **Ventana idéntica en otra fila o en otra importación** del mismo punto y día: se clasifica como duplicada y no se crea otra ventana.
- **Solapamiento parcial** (fila 228): no se suman ambas ventanas. Antes de confirmar, el analista debe unirlas en una ventana, corregir una u omitirla. El tiempo oficial nunca cuenta dos veces el mismo minuto de un punto.
- **Fin anterior al inicio**: nunca se supone un cruce de medianoche. Se exige corregir la hora o confirmar que el fin es del día siguiente.
- **Ventana de 4 horas o más** (filas 57 y 163, o una caída de varios días): no tiene duración máxima. Queda «Por revisar» y no computa hasta que un editor confirme que es real. Una fila importada solo puede expresar un fin en el mismo día o en el siguiente; una caída más larga se registra con fecha y hora de fin completas en la captura manual o editando la ventana en la vista previa.
- **Fin igual al inicio** (0 minutos): inválido; no forma una ventana.
- **Par incompleto** (filas 117 y 155): no se completa con valores inventados. El analista registra la ventana como abierta, corrige el dato que falta u omite el par.
- **Total inactivo distinto del recalculado**: es una observación, no la fuente de verdad. El sistema siempre calcula desde las ventanas; el analista debe reconocer la diferencia o corregir las ventanas antes de confirmar.
- **Total inactivo vacío**: no impide la importación; se informa que no hay valor para comparar.
- **Hora con segundos**: la resolución es el minuto. Un valor de Excel que difiere de un minuto exacto en menos de 1 segundo (representación decimal) se interpreta como ese minuto. Una diferencia mayor queda ambigua para revisión.
- **Fecha ambigua o inválida**: se aceptan la fecha nativa de Excel, `dd/mm/aaaa` y `aaaa-mm-dd`. Cualquier otro formato, o una fecha imposible, es inválido; no se intenta otro orden de día y mes.
- **Encabezados faltantes, renombrados o en otro orden**: si falta Fecha, Código cargador o un Inicio sin su Fin correspondiente, el archivo se rechaza con el nombre de la columna. El orden de las columnas no importa.
- **Libro con varias hojas**: solo se lee la primera hoja (D09).
- **Código válido con nombre distinto del actual**: se vincula por código y se muestra la diferencia de nombre sin bloquear (D12).
- **Nombre que coincide pero código desconocido**: queda pendiente de vincular; el nombre se muestra como sugerencia, nunca se asigna solo.
- **Punto KIA LIBRE sin Código asignado**: en importación no puede vincularse por código y queda pendiente de vincular, resoluble eligiendo el punto. En captura manual se elige directamente.
- **Equipo inactivo**: admite registrar ventanas de fechas pasadas, con observación visible. **Equipo eliminado lógicamente**: queda pendiente hasta recuperarlo en Inventario; no se crea otra identidad.
- **Punto DESCONECTADO (instalado sin servicio)**: sus ventanas pueden registrarse y computan en los indicadores de intermitencia; queda excluido del alcance de la disponibilidad estimada.
- **Ventana posterior a un evento abierto**: mientras un punto tenga una ventana abierta, cualquier ventana nueva de ese punto que empiece después de su inicio se solapa con ella (FR-014). Se rechaza en la captura y queda en conflicto en la importación, hasta cerrar la abierta o corregir la nueva.
- **Evento abierto que cruza varias quincenas**: muestra su duración provisional hasta la fecha de corte, separada; al cerrarse se reparte entre los períodos que toca.
- **Una ventana en dos quincenas**: su tiempo se reparte. En cada consulta cuyo rango comparte tiempo con ella, la ventana se cuenta una vez.
- **Rango personalizado mayor a 366 días**: se rechaza con explicación, igual que en Consumo.
- **Días futuros dentro de la quincena en curso**: no se consideran días sin datos ni días cubiertos; el rango real informa el corte.
- **Archivo vacío o sin filas válidas**: se informa sin crear ventanas; el lote queda trazado.
- **Inicio o fin en el futuro** (respecto del momento de registro, de la validación en vista previa, del cierre o de la confirmación): se rechaza en captura y queda inválido en la vista previa, con su motivo. Un evento abierto solo puede tener inicio pasado.
- **Fila de otro día cargada con la plantilla de hoy**: la fecha de la fila manda; nunca se usa la fecha de carga.

## Requirements *(mandatory)*

### Functional Requirements

#### Alcance, identidad y permisos

- **FR-001**: El módulo DEBE admitir solo equipos del Inventario de la red KIA con tipo de infraestructura «punto de carga». Las electrolineras y los equipos JAC o CONECTZ DEBEN quedar fuera de alcance tanto en captura como en importación.
- **FR-002**: Cada ventana DEBE referirse a la identidad interna del equipo, no a su nombre ni a su código. Un cambio de nombre, de código o de estado administrativo NO DEBE romper, duplicar ni reasignar ventanas existentes. La desactivación o la eliminación lógica conservan el historial (PRD §6.3).
- **FR-003**: La vinculación por importación DEBE hacerse por el Código asignado del punto KIA, comparando el texto literal sin espacios exteriores. Esta es la política de comparación de la clase «Código asignado» que exige D04. Es equivalente a la normalización de D05: V008 solo admite códigos de seis dígitos (`^[1-9][0-9]{5}$`), sin letras, tildes ni espacios, así que ignorar mayúsculas, tildes y espacios repetidos no cambia ninguna coincidencia. Si un código no existe, pertenece a un equipo fuera de alcance, a un equipo eliminado o a más de una identidad, la fila DEBE quedar pendiente de vincular o fuera de alcance, con su motivo. NO DEBE asignarse por nombre ni por similitud.
- **FR-004**: Administrador y Analista DEBEN poder capturar, importar, corregir, cerrar, anular, marcar como prueba y resolver. Todos los roles autenticados DEBEN poder consultar los indicadores oficiales, el historial de ventanas confirmadas y los informes. La autorización DEBE aplicarse en cada operación y consulta, no solo ocultando botones (PRD §4).
- **FR-005**: El detalle operativo (lotes, filas observadas, motivos, justificaciones, versiones y detalle de pendientes, abiertos y prueba) DEBE mostrarse solo a quien tenga Administrador o Analista. Las personas solo con Gerente, Supervisor o Asistente, en cualquier combinación, DEBEN recibir las cifras oficiales y las cantidades de cada apartado no oficial (como en las features 004 y 005).
- **FR-006**: Durante la prueba, la disponibilidad estimada (FR-042 a FR-047), su cobertura y sus valores por punto DEBEN entregarse solo a usuarios que tengan el rol Analista o el rol Administrador, en dashboard, consultas, PDF y Excel. Basta con tener uno de esos dos roles, combinado o no con otros. Una cuenta con solo Gerente, Supervisor o Asistente, en cualquier combinación, NO DEBE recibirla de ninguna forma, ni mediante acceso directo a los datos. La restricción se mantiene hasta que el responsable del producto la cambie mediante el control de cambios.
- **FR-007**: Los permisos efectivos DEBEN calcularse sobre los roles vigentes en cada consulta o descarga. Perder un rol retira su acceso en la siguiente operación.

#### Ventana de intermitencia, cálculo y estados

- **FR-008**: Cada ventana DEBE guardar el punto, el inicio y el fin con fecha, hora y zona horaria (America/Guayaquil), la duración en minutos enteros, el comentario (hasta 500 caracteres), la fuente (captura manual, XLSX, CSV o pegado), la procedencia (lote, fila, par), el autor, el momento y el estado.
- **FR-009**: La duración DEBE calcularse como la diferencia entre los instantes de fin e inicio, en minutos y con resolución de minuto. Se DEBE presentar en minutos y en h:mm, y los totales mayores de 24 horas en horas acumuladas (por ejemplo 36:42).
- **FR-010**: El sistema NO DEBE limitar la cantidad de ventanas por punto y día.
- **FR-011**: Los estados de una ventana DEBEN ser: **Confirmada** (cerrada, válida, vinculada y no de prueba, computa en lo oficial), **Abierta** (sin fin; duración provisional aparte), **Por revisar** (con una observación sin resolver), **Prueba** y **Anulada**. **Pendiente de vincular** es el estado de un registro importado cuyo punto no se conoce: no existe todavía como ventana de un punto y se informa junto a los demás registros no oficiales hasta que se vincula u omite. Solo Confirmada computa en los indicadores, el Top 10, la tendencia y los informes oficiales. Las demás se conservan y se informan aparte (PRD §3.3).
- **FR-012**: Una ventana abierta DEBE mostrar su duración provisional hasta la fecha de corte, separada del tiempo confirmado. Al cerrarse DEBE conservar su historial y quedar Confirmada si cumple las validaciones. Si la ventana cerrada dura 240 minutos o más, queda «Por revisar» según FR-021a. Mientras sigue abierta, nunca pasa a «Por revisar» por su duración.
- **FR-013**: El tiempo de cada ventana DEBE asignarse a cada día, quincena y rango según su porción, con inicio inclusivo y fin exclusivo, sin duplicar el evento. La ventana se registra como un solo evento.
- **FR-014**: Para un mismo punto, ninguna ventana no anulada, sea cual sea su estado (Confirmada, Abierta, Por revisar o Prueba), DEBE solaparse con otra. Una ventana abierta ocupa desde su inicio hasta que se cierra: mientras siga abierta, una ventana nueva del mismo punto que empiece después de su inicio se solapa con ella. En la captura manual se rechaza; en la importación queda en conflicto. El analista cierra antes la ventana abierta o corrige la nueva. Una ventana que se solape con otra existente, en el lote o fuera de él, DEBE quedar en conflicto hasta unirla, corregirla u omitirla de forma explícita. Las anuladas no participan en esta regla.
- **FR-015**: Dos ventanas del mismo punto con igual inicio e igual fin son la misma ventana: el sistema NO DEBE crear la segunda.
- **FR-016**: Una ventana confirmada o por revisar puede marcarse como Prueba, y quitarse esa marca, con justificación y trazabilidad; una ventana por revisar no necesita confirmarse antes, para que nunca compute en lo oficial. Marcar un punto como prueba es una operación sobre un rango de fechas: marca como Prueba las ventanas confirmadas o por revisar de ese punto en el rango, con una justificación única, y puede deshacerse sobre el mismo rango. No es una marca permanente del punto: las ventanas registradas después se tratan normalmente. En la vista previa de importación, una ventana marcada como prueba se incorpora directamente como Prueba al confirmar (FR-026). Una ventana Prueba siempre está cerrada. Por eso la vista previa NO DEBE admitir que una ventana sin fin se marque como prueba y se registre a la vez como abierta: se rechaza con su motivo, y el analista la cierra antes o la marca como prueba después de cerrarla. Las ventanas de prueba DEBEN excluirse del dashboard oficial, el Top 10, la tendencia y la disponibilidad estimada (RF-005.9, CA-005-02).
- **FR-017**: Toda corrección, cierre, anulación, marca de prueba o resolución de vínculo DEBE conservar la versión anterior, el autor, el momento y la justificación. NO DEBE haber borrado físico de ventanas (RF-005.8).
- **FR-018**: Las ediciones concurrentes de la misma ventana DEBEN detectarse. Un cambio NO DEBE sobrescribir otro sin advertirlo.

#### Captura manual

- **FR-019**: La captura DEBE permitir buscar un punto KIA por código o nombre, elegir la fecha y añadir o quitar tantas ventanas como sean necesarias, cada una con inicio, fin opcional y comentario. Mientras se edita, DEBE mostrar la duración de cada ventana y el total del día.
- **FR-020**: Para una ventana que termina el día siguiente, la captura DEBE exigir indicarlo de forma explícita. Un fin anterior o igual al inicio sin esa indicación DEBE rechazarse.
- **FR-021**: La captura DEBE aplicar las mismas validaciones que la importación: alcance, duplicado, solapamiento y revisión de ventanas largas (FR-021a). DEBE ofrecer guardar y registrar otro punto sin perder el contexto de la fecha.

- **FR-021a**: Una ventana NO DEBE tener duración máxima. Toda ventana cerrada de 240 minutos (4 horas) o más DEBE quedar «Por revisar» y fuera de los indicadores oficiales hasta que un editor confirme expresamente que es real. Puede confirmarla cualquier Administrador o Analista, incluso quien la registró; no se exige un segundo revisor. La confirmación DEBE llevar justificación obligatoria y queda registrada con autor, momento y justificación. La misma regla aplica a resolver cualquier otra observación «Por revisar». Cerrar un evento abierto que alcanza esa duración aplica la misma regla. Durante la revisión, la duración se muestra junto a sus instantes completos para detectar errores de tipeo.

- **FR-021b**: Ninguna ventana DEBE tener inicio o fin posterior al momento en que se guarda, se valida en la vista previa o se cierra, en la zona horaria operativa. En la captura manual, el registro DEBE rechazarse con un mensaje que indique el instante futuro. En la importación, la ventana DEBE quedar inválida y puede corregirse en la vista previa. Un evento abierto solo admite un inicio ya ocurrido. Como la resolución es el minuto, un instante dentro del minuto en curso se admite y uno del minuto siguiente o posterior se considera futuro. Si una vista previa validada se confirma después, la regla vuelve a aplicarse en el momento de la confirmación.

#### Importación y vista previa

- **FR-022**: La importación DEBE aceptar XLSX (solo la primera hoja, D09), CSV UTF-8 (o Windows-1252 si el archivo no es UTF-8 válido, como en las importaciones existentes) con separador coma o punto y coma detectado en el encabezado, y un bloque pegado con columnas separadas por tabulador, con o sin fila de encabezado. Si no hay encabezado, se usa el orden de la plantilla operativa.
- **FR-023**: Las columnas DEBEN reconocerse por su nombre, sin distinguir mayúsculas ni tildes: Fecha, Código cargador, Nombre cargador, Comentario, pares «Inicio N» y «Fin N» con N ≥ 1, y Total inactivo. Fecha, Código cargador y al menos un par son obligatorios. Los pares pueden ser más de cinco.
- **FR-024**: Cada instante DEBE formarse con la fecha de la fila y la hora del campo. Las horas pueden ser horas nativas de Excel, como fracción del día, o texto `HH:mm`. La regla de segundos de los casos límite DEBE aplicarse sin otras correcciones automáticas.
- **FR-025**: La vista previa DEBE clasificar cada fila y cada ventana como Nueva, Duplicada, En conflicto, Inválida, Pendiente de vincular, Fuera de alcance o Por revisar. DEBE mostrar archivo, fila, par, campo, valor original, interpretación, minutos, error y motivo en lenguaje comprensible, con conteos por clasificación (PRD §3.1).
- **FR-026**: La vista previa DEBE permitir editar fecha, código o punto, inicio, fin, la indicación de día siguiente, comentario y marca de prueba, y omitir o añadir ventanas en una fila. La interpretación inicial del archivo (FR-024) solo forma fines en el mismo día de la fila o, con indicación explícita, en el siguiente. Para una caída de varios días, el analista DEBE poder editar el fin con fecha y hora completas; esa edición queda registrada como corrección manual. Tras cada edición DEBE volver a validar. Cada cambio conserva el valor original y queda identificado como corrección manual, con su autor.
- **FR-027**: El sistema DEBE recalcular el total de cada fila desde sus ventanas válidas no omitidas y compararlo con Total inactivo, y DEBE volver a compararlo después de cada edición o decisión. Mientras una fila tenga ventanas idénticas sin resolver, DEBE mostrar el total con y sin el duplicado. Una diferencia de 1 minuto o más DEBE señalarse con ambos valores y no puede confirmarse hasta que el analista corrija las ventanas o reconozca la diferencia. Total inactivo NUNCA DEBE usarse como duración.
- **FR-028**: Al confirmar, DEBEN incorporarse solo las ventanas nuevas y válidas, más las resueltas. Las filas omitidas DEBEN conservar su motivo. Las pendientes y en conflicto DEBEN seguir disponibles para resolver después, sin computar.
- **FR-029**: Reimportar contenido idéntico NO DEBE crear ventanas ni cambiar totales (CA-005-04, CA-G-003). Una importación NO DEBE anular ni modificar ventanas existentes que el archivo no contenga. Una diferencia con una ventana existente DEBE requerir resolución explícita: conservar la existente o sustituirla como corrección justificada.
- **FR-030**: Cada lote DEBE registrar usuario, momento, origen, nombre y huella del archivo, resumen de resultados y decisiones. El archivo DEBE conservarse para auditoría, siguiendo la práctica de las importaciones existentes.

#### Dashboard

- **FR-031**: El dashboard DEBE ofrecer primera quincena (días 1 a 15), segunda quincena (días 16 al fin de mes) de un mes elegido, últimos 15 días móviles (hoy y los 14 anteriores) y rango personalizado de hasta 366 días. DEBE abrir en la quincena en curso, con corte en hoy, y rotular siempre el tipo de período y su rango real.
- **FR-032**: Los filtros DEBEN incluir período y uno o varios puntos KIA. Todos los indicadores, gráficos, rankings, detalles e informes DEBEN responder a los mismos filtros.
- **FR-033**: Los indicadores oficiales DEBEN ser:
  - tiempo confirmado fuera de línea;
  - cargadores afectados: puntos con tiempo confirmado en el rango;
  - ventanas completas: ventanas confirmadas con tiempo en el rango;
  - días-equipo: pares punto-día calendario con tiempo confirmado;
  - equipos recurrentes: puntos con tiempo confirmado en 3 o más días calendario distintos (RF-005.10);
  - promedio por ventana: tiempo confirmado sobre ventanas completas.

  Fuera de lo oficial, DEBEN mostrarse aparte:
  - registros por revisar: por revisar, en conflicto y pendientes de vincular;
  - eventos abiertos, con su duración provisional;
  - ventanas de prueba.
- **FR-034**: El Top 10 DEBE ordenar los puntos de mayor a menor tiempo confirmado y mostrar código, nombre actual, tiempo (h:mm), ventanas y días afectados. DEBE incluir a todos los empatados en tiempo con el décimo puesto e indicar que el ranking supera los diez por empate. Entre puntos con el mismo tiempo, el orden de presentación es: más ventanas, más días afectados y nombre actual alfabético. Ese orden no excluye a nadie. Con menos de diez afectados, se muestran los disponibles.
- **FR-035**: DEBE mostrarse una gráfica de línea del tiempo confirmado diario en horas, con el patrón de «Energía diaria» de Consumo. Como complemento, una gráfica de barras con las ventanas diarias. Una ventana cuenta en cada día en que tiene tiempo confirmado; por eso una ventana que cruza la medianoche cuenta en ambos días y la suma de las barras puede superar el total de ventanas del período, lo que el gráfico DEBE indicar en una nota. Los días sin ventanas confirmadas aparecen en cero solo dentro del rango consultado y hasta la fecha de corte.
- **FR-036**: DEBEN incluirse estos pasteles, cada uno con título, unidad (minutos u h:mm), leyenda textual, valores y porcentaje:
  - **Tiempo por duración de ventana**, en franjas por duración total en minutos: [0, 5), [5, 15), [15, 60), [60, 240) y 240 o más. Las etiquetas son «Menos de 5 min», «5 a 14 min», «15 a 59 min», «1 h a menos de 4 h» y «4 h o más». Distingue muchas microcaídas de pocas caídas largas. La franja se decide por la duración total de la ventana, aunque atraviese el límite del período, y la ventana solo aporta los minutos que caen dentro del período. Así, la suma del pastel coincide con el tiempo confirmado.
  - **Concentración del tiempo**: Top 10 frente al resto de cargadores afectados.
  - **Tiempo de equipos recurrentes frente a no recurrentes.**

  Todos usan solo tiempo confirmado y no se dibujan sin tiempo confirmado. NO DEBEN incluirse pasteles por red (solo existe KIA), por comentario (hoy siempre «Intermitencia»), por ubicación (el inventario la guarda como texto libre) ni por cantidad de ventanas como medida principal.

  En pantalla los repartos son pasteles (dona). En el PDF se dibujan como barras de participación con la misma leyenda, valores y porcentajes, porque el generador corporativo no dibuja gráficos circulares (aclaración 16 de CC-PRD-008).
- **FR-037**: El dashboard DEBE mostrar la fecha y hora de cálculo. Tras un cambio confirmado, la siguiente consulta DEBE reflejarlo, sin cifras desactualizadas del período.

#### Informes

- **FR-038**: El PDF DEBE contener:
  - identidad corporativa;
  - tipo y rango del período, zona horaria y fecha de corte;
  - resumen ejecutivo en texto con los indicadores del período;
  - indicadores, tendencia, los tres repartos de FR-036 (como barras de participación) y Top 10;
  - notas de metodología: definiciones, unidades, exclusiones y la regla de empates;
  - un apartado separado con las cantidades de abiertos (y su duración provisional), ventanas por revisar, filas en conflicto, filas por vincular y prueba, y el total de registros por revisar de FR-033 (ventanas por revisar + filas en conflicto + filas por vincular), con los mismos rótulos del dashboard.

  El alcance impreso en el encabezado, que también se archiva (FR-040), nombra los puntos elegidos con su nombre actual y su Código asignado solo si son puntos KIA en alcance y son 5 o menos. Con más de 5 solo indica cuántos son. Un equipo que no es un punto KIA en alcance, o que ya no existe, se muestra como «no disponible»: nunca su nombre ni su código. Sin filtro de puntos dice «Todos los puntos KIA».

  NO DEBE incluir UID/PUK ni URL OCPP (RF-RPT-07 a 09).
- **FR-039**: El Excel DEBE contener:
  - un resumen;
  - el detalle por cargador: código, nombre actual, días afectados, porcentaje de días del período (días afectados sobre los días calendario del rango hasta el día del corte, incluido hoy; en un período cerrado, todos sus días), ventanas, minutos y h:mm;
  - el detalle de ventanas confirmadas: inicio, fin, duración total y porción dentro del rango;
  - un anexo con lo no oficial, según los permisos, con las cantidades, el total de registros por revisar y los rótulos de FR-038.

  El encabezado del Excel lleva el mismo alcance impreso que el PDF, con la regla de nombres de FR-038. Para unos mismos filtros y corte, dashboard, PDF y Excel DEBEN coincidir en todas las cifras.
- **FR-040**: Cada informe emitido DEBE archivarse con su alcance, su corte y su huella, siguiendo la práctica vigente. Las correcciones posteriores NO DEBEN modificarlo. Un original con información restringida para quien lo solicita, incluida la disponibilidad estimada, DEBE bloquearse para esa persona, con una explicación y la opción de emitir uno nuevo autorizado (Feature 004).
- **FR-041**: Los informes DEBEN poder descargarse para enviarlos por correo. Esta feature NO DEBE incluir envío automático ni integración de correo.
- **FR-041a**: Un informe DEBE rechazarse, sin generarlo ni archivarlo, cuando tendría más de 50.000 ventanas confirmadas con tiempo en el rango. La respuesta es 422 `REPORT_TOO_LARGE`, con un mensaje que pide acortar el período o elegir menos puntos, igual que los informes de Consumo e Inventario. El dashboard no tiene este tope.

#### Disponibilidad estimada de prueba (solo Analista y Administrador)

- **FR-042**: La disponibilidad estimada de un punto DEBE calcularse con el criterio de Enerlink ya aplicado en Uptime (FR-006 de la spec 005): (tiempo evaluado − tiempo fuera de línea confirmado) / tiempo evaluado × 100. El cálculo usa minutos y se presenta con dos decimales. El agregado de varios puntos o días DEBE ponderarse por minutos y NO DEBE promediar porcentajes. Ocupado y Con falla no se observan en este registro ni se descuentan, igual que en Enerlink.
- **FR-043**: El tiempo evaluado de un punto DEBE ser 1.440 minutos (24 horas) por cada día cubierto en el rango, como la medición Enerlink de CA-004-01. El horario de atención del inventario NO DEBE reducir el tiempo evaluado. Un día solo puede estar cubierto después de terminar: el día en curso y los futuros no cuentan.
- **FR-044**: Un día cuenta como cubierto solo si un Analista o Administrador lo marca como «revisado» para toda la red KIA, normalmente al cerrar su turno. La marca DEBE guardar día, autor y momento. Con ella, cada punto en alcance sin ventanas confirmadas ese día cuenta con 0 minutos fuera de línea. Un día sin marca es «sin datos»: NO DEBE sumar tiempo evaluado ni presentarse como 100 %. Una ventana registrada después en un día ya revisado reduce su disponibilidad sin quitarle la marca. Un editor DEBE poder retirar una marca equivocada con justificación, y el retiro queda trazado. Solo se marca un día ya terminado, pero puede ser de cualquier fecha pasada: el panel muestra los últimos 31 días y ofrece un selector de mes para marcar el histórico (aclaración 21 de CC-PRD-008).
- **FR-045**: Un punto-día con ventanas abiertas, por revisar o en conflicto NO DEBE contar como cubierto hasta resolverlas. Como una ventana abierta ocupa desde su inicio hasta que se cierra (FR-014), deja sin cubrir para su punto cada día desde su día de inicio hasta el día del corte; al cerrarla, esos días se evalúan con su duración definitiva. Una fila pendiente de vincular, sin punto conocido, DEBE dejar sin cubrir para todos los puntos cada día que tocan sus ventanas interpretadas, o el día de la fila si no se pudo interpretar ninguna. La cobertura se recupera al vincularla, omitirla o anularla. Solo cuentan las filas de lotes confirmados; un lote en vista previa sin confirmar no afecta la cobertura. Al marcar un día como revisado, DEBE avisarse si existen lotes sin confirmar con filas de ese día. Las ventanas de prueba no descuentan tiempo. Un punto solo se evalúa desde el primer día completo en que ya existía (el día de su alta si se creó a las 00:00 exactas y, si no, el siguiente): antes de su alta no cuenta, ni como 100 %, y no pesa en el agregado (aclaración 20 de CC-PRD-008). Los puntos DESCONECTADOS, y los inactivos o eliminados sin ventanas en el rango, quedan fuera del alcance y se listan como exclusiones.
- **FR-046**: Junto a cada cifra DEBEN mostrarse la cobertura (días cubiertos sobre días en alcance; los días en alcance son los días del rango ya terminados hasta el corte, sin contar hoy ni los días futuros), el rótulo «estimada», «prueba», el origen «Registro manual de intermitencias (Kibana)» y sus limitaciones. Las limitaciones son: no observa Ocupado ni Con falla, la resolución es el minuto y no hay medición fuera de los días revisados. NO DEBE presentarse como uptime ni como medición de Enerlink. Si no hay tiempo evaluado, se muestra «Sin datos».
- **FR-046a**: La disponibilidad estimada DEBE presentarse en una sección propia, rotulada «Disponibilidad estimada (prueba)» y separada de los indicadores oficiales de intermitencia. La sección contiene:
  - un KPI agregado ponderado por minutos, con su cobertura (días cubiertos sobre días en alcance);
  - una tabla por punto ordenada de menor a mayor disponibilidad, con código, nombre actual, disponibilidad, tiempo evaluado, minutos confirmados fuera de línea y días cubiertos. A igual disponibilidad, se ordena por más minutos fuera de línea y luego por nombre actual alfabético. Los puntos sin días cubiertos aparecen al final como «Sin datos»;
  - la lista de exclusiones con su motivo: DESCONECTADO, o inactivo o eliminado sin ventanas.

  El PDF y el Excel DEBEN incluir esta sección como apartado separado con el mismo contenido y la nota de limitaciones de FR-046. Esto vale solo para informes emitidos por quien puede ver la disponibilidad (FR-006). La sección NO DEBE mezclarse con el Top 10 ni con el detalle por cargador.
- **FR-047**: La disponibilidad estimada NO DEBE alterar, alimentar ni mostrarse en los indicadores, rankings, coberturas, dashboards o informes del módulo de Uptime, ni combinarse con ellos en un KPI común.

#### Diseño, accesibilidad y producción

- **FR-048**: Las pantallas del módulo DEBEN reutilizar la navegación, el encabezado con acciones PDF y Excel, la barra de filtros, las tarjetas de indicadores, los paneles, los gráficos, las tablas, las insignias de estado, los diálogos y las páginas de lote de importación de Consumo, Inventario y Uptime. DEBEN usar los mismos colores, tipografía, espaciado, temas claro y oscuro e iconografía, sin estilos ni colores propios. El módulo DEBE verse como parte de SARC.
- **FR-049**: Las pantallas DEBEN cumplir los criterios aplicables de WCAG 2.2 AA, con los mismos criterios que las features 004 y 005 (FR-036 y SC-011 de la spec 005) y las reglas del frontend. Esto incluye: ninguna violación en las comprobaciones AXE; nombres accesibles; foco visible; operación con teclado; contraste de al menos 4,5:1 en texto y 3:1 en componentes y gráficos; no depender solo del color (leyenda textual con valor y porcentaje); y gráficos con alternativa textual. Su diseño responsive DEBE funcionar a 360, 390, 768, 1024 y 1440 píxeles, con tarjetas en móvil donde Consumo las usa. Los estados vacíos, de carga y de error DEBEN mostrar mensajes en español con una acción de reintento.
- **FR-050**: La feature DEBE ser aditiva. NO DEBE modificar datos, cálculos, permisos ni informes existentes de usuarios, Inventario, Consumo, Uptime o informes emitidos. Los cambios de esquema DEBEN ser migraciones nuevas, compatibles con los datos de producción (principio III). DEBE publicarse por el flujo vigente de verificación, versión candidata y publicación aprobada de la Feature 007.
- **FR-051**: Los datos y archivos del módulo DEBEN quedar cubiertos por los procedimientos vigentes de respaldo y restauración de PostgreSQL y de archivos externos. La restauración ensayada DEBE incluir datos de intermitencias antes de dar la feature por cerrada.
- **FR-052**: Cada creación, importación, corrección, cierre, anulación, marca de prueba, resolución y emisión de informe DEBE quedar en la auditoría del sistema, sin registrar secretos.

### Trazabilidad documental

- **Constitución 1.2.0**: principios I–III. Decisiones aplicables:
  - D04: política de comparación del Código asignado, literal sin espacios exteriores (FR-003);
  - D05: su normalización es equivalente a esa comparación para códigos de seis dígitos, y los códigos ambiguos se concilian de forma explícita (FR-003);
  - D07: los reemplazos conservan identidades;
  - D09: solo la primera hoja (FR-022);
  - D12: nombres actuales en informes nuevos (US3-6, FR-034).

  D14 se ratificó para el Top 5 de Consumo. Aquí se adopta por analogía su regla de empates para el Top 10 (FR-034), por decisión de esta especificación, sin extender ni enmendar D14. D10, D11 y D13 no se aplican. No se requiere enmienda constitucional.
- **PRD v1.1**: RF-005.1 a RF-005.13, CA-005-01 a CA-005-05, §3.1, §3.2, §3.3, §4, §6.3, RF-RPT-07 a RF-RPT-11, §11.1, CA-G-001, CA-G-002, CA-G-003, CA-G-008 y CA-G-012.
- **Diferencias con el PRD**, registradas en CC-PRD-008-20261110:
  - disponibilidad estimada de prueba en un módulo RF-005, no previsto por el PRD;
  - restricción de esa disponibilidad a los roles Analista y Administrador, como excepción de visibilidad permitida por el PRD §4;
  - marca diaria de «día revisado» para la red KIA, que es nueva en el PRD;
  - el Top 5 por ventanas de la plantilla no se adopta.

### Key Entities *(include if feature involves data)*

- **Punto KIA**: equipo del Inventario de la red KIA de tipo punto de carga. Se referencia por su identidad interna. Su código, nombre y estado son atributos actuales.
- **Ventana de intermitencia** (EventoIntermitencia del PRD): intervalo fuera de línea de un punto. Tiene inicio, fin opcional, duración en minutos, comentario, fuente, procedencia, estado y etiqueta del punto en la fecha del hecho.
- **Versión de ventana**: valores de una ventana en un momento dado, con autor y justificación. Solo una está vigente.
- **Porción de período**: parte de la duración de una ventana que cae en un día, una quincena o un rango. No es un evento propio.
- **Lote de intermitencias**: XLSX, CSV o pegado. Guarda usuario, momento, archivo y huella, filas, ventanas interpretadas, correcciones manuales, clasificaciones y decisiones.
- **Día revisado**: marca de que un día terminado se revisó para toda la red KIA, con autor, momento y, si se retira, justificación. Es la base de la cobertura de la disponibilidad estimada.
- **Disponibilidad estimada**: resultado derivado por punto y rango, con tiempo evaluado de 1.440 minutos por día revisado, tiempo confirmado, cobertura y exclusiones. Solo la ven Analistas y Administradores, y no es una medición de Uptime.
- **Informe de intermitencias emitido**: documento archivado con alcance, filtros, corte y huella. No cambia con correcciones posteriores.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un analista registra un punto con cinco ventanas en menos de 2 minutos, y con diez ventanas en menos de 3 minutos.
- **SC-002**: Importar el archivo operativo de 248 filas (o su copia sintética equivalente en las pruebas automatizadas) muestra la vista previa completa en menos de 30 segundos, y el 100 % de los casos de la tabla de hallazgos queda señalado con su motivo correcto.
- **SC-003**: Cero ventanas se crean o cambian de forma automática por pares invertidos, incompletos, idénticos o solapados: el 100 % requiere una decisión registrada.
- **SC-004**: Reimportar un archivo ya confirmado crea cero ventanas y cambia cero totales oficiales.
- **SC-005**: En el conjunto de prueba (cruces de medianoche y quincena, abiertos, pruebas, pendientes y empates), el 100 % de los indicadores, el Top 10 y las porciones por período coinciden con los valores calculados a mano. Esto incluye el ejemplo 23:50–00:20 (10 + 20 minutos) y la fila 163 (237 + 742 minutos).
- **SC-006**: Con iguales filtros y corte, dashboard, PDF y Excel coinciden en el 100 % de las cifras comparadas.
- **SC-007**: En el 100 % de las pruebas por rol, quienes no tienen Administrador ni Analista no pueden modificar datos ni recibir detalle operativo, y quienes no tienen Analista ni Administrador no reciben la disponibilidad estimada, ni siquiera por acceso directo o informes.
- **SC-008**: Cero días sin marca de «revisado» aparecen como 100 % de disponibilidad. Marcar un día como revisado toma menos de 30 segundos. Cero cifras de Uptime cambian antes y después de la feature.
- **SC-009**: El 100 % de las correcciones, anulaciones y resoluciones permite reconstruir autor, momento, motivo y valores anteriores y nuevos.
- **SC-010**: Una persona distingue en menos de 10 segundos qué cifras son oficiales, cuáles son provisionales y cuál es el rango del período.
- **SC-011**: Las pantallas del módulo no presentan superposición, desbordamiento ni acciones inaccesibles en los cinco anchos y los dos temas. Una revisión visual frente a Consumo no encuentra colores, componentes ni estilos ajenos al sistema de diseño.
- **SC-012**: Las pruebas de regresión de autenticación, usuarios, Inventario, Consumo, Uptime e informes conservan el 100 % de sus resultados esperados, y la publicación pasa por el flujo aprobado sin pérdida de datos de producción.

## Assumptions

- El estado de un punto (inactivo, desconectado o eliminado) se evalúa al consultar la disponibilidad estimada: desactivarlo o desconectarlo después no cambia los días ya revisados de un punto con ventanas. La fecha de alta sí se respeta: un punto no se evalúa antes de existir (FR-045, aclaración 20 de CC-PRD-008).
- Un punto de carga KIA tiene un solo conector (PRD §6). Por eso la ventana se registra por punto, sin distinguir conector, y es comparable con la unidad por conector de Enerlink.
- La fuente de observación es Kibana, revisada por los analistas. SARC no se integra con Kibana ni con Enerlink en esta feature.
- La zona horaria operativa es America/Guayaquil, sin horario de verano. Un día se mide de 00:00 a 00:00 del día siguiente, con fin exclusivo.
- La ventana confirmada no exige una validación adicional: guardarla o confirmar el lote sin observaciones equivale a validarla, como en las importaciones de Consumo. Lo observado queda por revisar.
- El umbral de recurrencia es fijo en 3 días calendario (RF-005.10). La plantilla lo permitía configurar, pero no se hace configurable.
- El historial de octubre de 2026 puede cargarse con el mismo importador. Sus filas inválidas quedan pendientes; no se omiten en silencio.
- La disponibilidad estimada considera todos los puntos KIA en alcance, tengan o no medición en Enerlink. No se compara punto a punto con Uptime en esta feature.
- La restauración con los originales importados se ensayó el 2026-11-13 con un archivo sintético en una carpeta propia de Cloudinary (FR-051). La clave de API dedicada a la exportación y el Backup de Cloudinary de `sarc-prod` siguen en la tarea T083 de la Feature 007; se necesitan para la primera exportación mensual real, no para cerrar esta feature.
- Quedan fuera de alcance el envío de correos, las alertas automáticas, el análisis cruzado con tickets o uptime y las integraciones automáticas con Kibana.
