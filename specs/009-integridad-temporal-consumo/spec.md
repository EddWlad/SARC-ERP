# Feature Specification: Integridad temporal, gestión de sesiones y estabilidad visual

**Feature**: `009-integridad-temporal-consumo`

**Feature Branch**: `feat/009-integridad-temporal-consumo`

**Created**: 2026-11-11

**Status**: En implementación. El hotfix de la fecha de origen Enerlink (Fase H) está publicado desde el 2026-11-14 (`2026.11.14-41-793e92f`). US2 y FR-006a quedaron cerrados o retirados por la aclaración del 2026-11-13.

**Input**: Solicitud de Feature-009 para RF-003. Alinear las fechas de Consumo con los archivos de origen, auditar los históricos, incorporar acciones por sesión y corregir los parpadeos reproducibles. Preservar la continuidad de los módulos existentes y de la Feature-008, que está en pruebas.

## Clarifications

### Session 2026-11-11

- Q: ¿En qué fecha debe contar la sesión Enerlink 1212 cuando el tablero o informe combina Enerlink y Wallbox? → A: Opción A. Cada sesión se atribuye a la fecha de inicio que corresponde a su fuente, también en el consolidado. Enerlink 1212 cuenta el 11/11/2026 con o sin filtro por plataforma. El rango inicial sigue basándose en el día operativo de Ecuador, conforme a D13.

### Session 2026-11-13 (después del hotfix H001–H007)

- Q: ¿Se construye el subsistema de auditoría y corrección del histórico de US2 (propuestas con huella, constancias de aprobación y comando de aplicación)? → A: No. US2 queda cubierta por H006 y H007. Se leyó el encabezado de los 24 archivos Enerlink originales de producción, V019 asignó el desplazamiento por lote, el ensayo sobre la copia del 2026-11-13 demostró la conservación de sesiones y kWh, y producción quedó sin sesiones sin evidencia (`evidence/hotfix.md`). Las discrepancias futuras se resuelven por la reimportación con revisión explícita de US3 (FR-011–016).
- Q: ¿Se implementa la «Fecha operativa heredada» y su desglose (FR-006a)? → A: No, se retira. Después de V019 todas las sesiones tienen desplazamiento verificado, y toda lectura nueva lo trae (declarado, o el de Ecuador por defecto). El desglose estaría siempre vacío.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar Consumo con la fecha de la fuente (Priority: P1)

Como usuario de Consumo, quiero que el histórico, el tablero y los informes ubiquen las sesiones en la fecha de inicio que corresponde a su fuente para cotejar las cifras con los archivos de operación.

**Why this priority**: Un desplazamiento al día anterior cambia los totales diarios y puede cambiar los resultados de un mes, aunque el instante registrado sea correcto.

**Independent Test**: Usar sesiones Enerlink y Wallbox con inicios a ambos lados de la medianoche y cotejar histórico, filtros, tendencia, PDF y Excel con los mismos filtros y corte.

**Acceptance Scenarios**:

1. **Dado** el archivo Enerlink con encabezado UTC+00:00 y la sesión 1212 iniciada el 11/11/2026 a las 01:36:15, **cuando** se consulta Enerlink, **entonces** aparece con esa fecha y hora, se atribuye al 11/11 y el instante real sigue siendo 01:36:15 UTC. Su equivalencia en Ecuador, 10/11 a las 20:36:15, se puede consultar en el detalle autorizado.
2. **Dada** la sesión Enerlink 1211 del 11/11/2026 a las 01:01:56 en la misma fuente, **cuando** se filtra el 11/11, **entonces** se incluye en ese día. Al consultar solo el 10/11 queda fuera del resultado Enerlink.
3. **Dada** una sesión Wallbox cuyo archivo indica `2026-11-10 23:59:11`, **cuando** se consulta, **entonces** conserva la atribución al 10/11 y la presentación vigente de `10/11/2026 23:59` en la lista. El detalle autorizado conserva la precisión de origen.
4. **Dada** una sesión elegible que termina en otro día, mes o año, **cuando** se consulta el rango que contiene su fecha de inicio, **entonces** cuenta una sola vez y su término no la traslada de período.
5. **Dados** filtros, permisos y revisiones de datos equivalentes, **cuando** se cotejan el histórico, el tablero, la tendencia, el PDF y el Excel, **entonces** las fechas, sesiones y cifras de energía coinciden en los valores comunes que cada vista está autorizada a mostrar.
6. **Dadas** únicamente dos sesiones elegibles, Enerlink 1212 del 11/11/2026 a las 01:36:15 UTC con 8 kWh y Wallbox del 10/11/2026 a las 23:59:11 de Ecuador con 5 kWh, **cuando** se consulta y exporta el consolidado, **entonces** el 10/11 cuenta una sesión y 5 kWh, el 11/11 cuenta una sesión y 8 kWh y el rango de ambos días totaliza dos sesiones y 13 kWh. Los filtros diarios, la tendencia, el PDF y el Excel aplican ese reparto. Filtrar solo Enerlink deja la sesión 1212 y sus 8 kWh en el 11/11.
7. **Dado** que es 30/11/2026 a las 20:30 en Ecuador y ya es 01/12/2026 en UTC, **cuando** se abre el tablero o el histórico de Consumo, **entonces** el rango inicial abarca del 01/11 al 01/12 con fin exclusivo. Una sesión Enerlink cuya fecha de inicio declarada es 01/12 queda fuera de ese rango inicial y aparece al seleccionar el 01/12.
8. **Dadas** las dos sesiones del escenario 6 y una sesión histórica oficial de 2 kWh atribuida previamente al 10/11 cuya fecha de origen no puede verificarse, **cuando** se consulta el mismo rango sin cambios entre lecturas, **entonces** el histórico identifica esa fila como «Fecha operativa heredada». El dashboard, el PDF y el Excel presentan 3 sesiones y 15 kWh en total, incluidos 1 sesión y 2 kWh de atribución heredada. En el 10/11 se computan 2 sesiones y 7 kWh y en el 11/11 se mantienen 1 sesión y 8 kWh.
9. **Dado** que una corrección autorizada cambia los datos después de consultar el dashboard y antes de exportar, **cuando** se genera un informe, **entonces** el dashboard identifica su fecha y hora de cálculo y el informe identifica su propio corte. El informe usa las revisiones vigentes en su corte y la consulta actualizada coincide con él si no cambian nuevamente las revisiones, filtros o permisos.

### User Story 2 - Auditar y corregir el histórico con control de impacto (Priority: P1)

> **Cerrada por H006–H007** (aclaración 2026-11-13). Los escenarios se acreditaron una vez, sobre producción, con la evidencia de `evidence/hotfix.md`: clasificación por lote según el archivo original (escenario 1), impacto por día y mes antes de aplicar (2), conservación de sesiones y kWh (3), copia previa y emisiones archivadas intactas (4), aprobación explícita del responsable antes de publicar (5) y diagnóstico sobre la copia autorizada (6). No se construye el subsistema genérico.

Como responsable de Consumo, quiero distinguir las sesiones cuyo instante se interpretó mal de aquellas cuyo período debe cambiar por la nueva regla, para aprobar solo correcciones verificables.

**Why this priority**: Una conversión válida a Ecuador puede explicar la diferencia visual sin que exista un error en el instante real. Corregir ambos casos del mismo modo pondría en riesgo los históricos.

**Independent Test**: Auditar una muestra con una sesión correcta, una reclasificación de período, un instante incorrecto demostrable y una sesión sin evidencia suficiente. Revisar el impacto y ensayar el mismo conjunto aprobado dos veces.

**Acceptance Scenarios**:

1. **Dado** un histórico importado, **cuando** se compara con la evidencia de origen, **entonces** la revisión distingue sesiones correctas, cambios de atribución, errores comprobados de instante y casos indeterminados. Las conclusiones indican el motivo y la evidencia disponible.
2. **Dado** un conjunto candidato a corrección masiva, **cuando** el responsable revisa la propuesta, **entonces** puede ver las sesiones afectadas, el antes y después y el efecto en filtros, conteos y energía por período. La revisión por sí sola deja intactos los datos.
3. **Dada** una corrección autorizada, **cuando** se aplica y luego se reintenta sobre el mismo conjunto, **entonces** el reintento crea cero duplicados y no vuelve a aplicar cambios. Las sesiones y la energía del conjunto se conservan cuando el cambio es exclusivamente temporal, aunque varíen sus subtotales por período.
4. **Dado** un informe emitido antes de una corrección, **cuando** se consulta junto con un informe nuevo del mismo rango, **entonces** se conserva el original con su corte y contenido, y el nuevo refleja la información vigente con su propio corte y los permisos aplicables.
5. **Dada** una propuesta con una huella válida, **cuando** falta la aprobación registrada por el sistema, un usuario sin rol Administrador intenta aprobarla o la constancia corresponde a otra propuesta o huella, **entonces** no se aplican cambios ni se registran revisiones como aplicadas.
6. **Dada** una copia autorizada del histórico pertinente, **cuando** se prepara la revisión de publicación, **entonces** se presenta la cobertura e impacto por fuente y período sobre esa copia. Si falta el diagnóstico, la revisión del alcance histórico queda pendiente.

### User Story 3 - Ver, editar y eliminar lógicamente una sesión (Priority: P1)

Como usuario de Consumo, quiero consultar una sesión desde el histórico. Como Administrador o Analista autorizado, quiero corregirla o retirarla de los indicadores con una decisión trazable.

**Why this priority**: Un error comprobado debe poder resolverse desde el histórico sin perder la sesión original ni depender de otra importación.

**Independent Test**: Consultar la misma sesión como usuario de lectura y como editor. Corregirla, probar una modificación inválida, eliminarla con motivo y reimportar sus datos de origen.

**Acceptance Scenarios**:

1. **Dada** una sesión visible, **cuando** se selecciona «Ver», **entonces** se abre el detalle con los campos permitidos para el rol, incluidos origen, equipo, inicio, fin si existe, zona horaria, energía y estado. La evidencia y auditoría operativas quedan disponibles para los roles autorizados.
2. **Dada** una sesión con un dato corregible, **cuando** un editor autorizado guarda una corrección válida con justificación y confirmación, **entonces** se registra el valor anterior y el nuevo, el autor, la fecha y el motivo. Las vistas futuras aplican las reglas de elegibilidad vigentes.
3. **Dada** una corrección con fechas incoherentes, energía inválida o identidad de equipo no resuelta, **cuando** se intenta confirmar, **entonces** se aplica la validación correspondiente y el sistema informa cómo resolverla. Si la energía supera el umbral constitucional, se conserva el flujo de revisión humana antes de computarla.
4. **Dada** una sesión oficial, **cuando** un editor confirma su eliminación con un motivo, **entonces** deja de aportar sesiones y energía a las cifras oficiales. Su identidad, evidencia e historial siguen disponibles para auditoría autorizada.
5. **Dada** una sesión corregida o eliminada lógicamente, **cuando** se vuelve a importar su registro, incluso si el contenido de origen difiere, **entonces** no se crea otra sesión ni se revierte o reactiva automáticamente la decisión. Las discrepancias se presentan para resolución autorizada.
6. **Dado** un usuario con permisos exclusivamente de consulta, **cuando** abre el histórico o intenta una acción de modificación, **entonces** puede ver el detalle autorizado y se rechazan la edición y la eliminación.

### User Story 4 - Trabajar con vistas estables durante las actualizaciones (Priority: P2)

Como usuario de SARC, quiero que las actualizaciones legítimas del tablero y las vistas afectadas mantengan visibles los datos y mi contexto, para poder trabajar sin interrupciones visuales.

**Why this priority**: Los parpadeos dificultan leer gráficos, comparar filas y completar formularios, aunque los resultados sean correctos.

**Independent Test**: Reproducir el comportamiento informado, conservar evidencia inicial y repetir los mismos recorridos después de corregirlo, con y sin datos nuevos.

**Acceptance Scenarios**:

1. **Dado** un tablero cargado con filtros activos, **cuando** se produce una actualización automática sin cambios en los datos, **entonces** tarjetas, gráficos y tablas permanecen visibles y no repiten sus animaciones de entrada.
2. **Dada** una actualización válida con datos nuevos, **cuando** se completa, **entonces** se muestran las cifras actualizadas y se conservan los filtros, la ubicación y el estado de interacción que sigan siendo válidos.
3. **Dado** un usuario que completa un formulario, navega por teclado o abre el detalle de una sesión, **cuando** se actualiza la vista en segundo plano, **entonces** se conservan los valores en edición, el foco y el contexto que aún sean aplicables.
4. **Dado** un defecto equivalente en otra vista, **cuando** la auditoría verifica que tiene la misma causa, **entonces** se registra y se verifica su corrección dentro del alcance autorizado. Si aparece en la Feature-008 durante sus pruebas, se documenta para coordinarlo con su responsable.
5. **Dados** un móvil, una tablet y un escritorio en ambos temas, **cuando** se operan las acciones y diálogos, **entonces** los controles, ayudas y mensajes de validación permanecen legibles y accesibles, sin superposiciones y con foco visible.

### Edge Cases

- **Cambio de día, mes o año**. La sesión cuenta por el inicio de acuerdo con su fuente. Un inicio en el límite exclusivo pertenece al período siguiente.
- **Enerlink con desplazamiento ausente, inválido o diferente entre inicio y fin**. Si Inicio no declara desplazamiento, se aplica `America/Guayaquil` y se registra como zona aplicada. Si Fin no declara uno, usa el desplazamiento o zona resuelta para Inicio. Si ambos declaran desplazamientos válidos, se usa el de cada extremo aunque difieran, y se valida que el fin no sea anterior al inicio por sus instantes. Una declaración presente ilegible, fuera de rango o internamente contradictoria deja la fila inválida para revisión y sin incorporación hasta corregirla. Estas reglas de importación no deducen zonas de históricos sin evidencia suficiente.
- **Archivos Enerlink con distintos desplazamientos declarados**. La fecha y hora de cada registro se interpretan con el encabezado que le corresponde. Dos horas de origen iguales con desplazamientos distintos pueden representar instantes distintos.
- **Fin o energía ausentes**. Se preserva la elegibilidad vigente. Una lectura conocida de 0 kWh se distingue de una lectura sin informar.
- **Edición simultánea, eliminación ya aplicada o cancelación**. El usuario recibe el estado vigente antes de confirmar un cambio sobre datos modificados. Cancelar deja los datos como estaban.
- **Primera carga, error de actualización o cambio intencional de filtros**. Se muestran estados claros. La estabilidad visual no permite presentar datos de un filtro previo como si correspondieran al nuevo.

## Requirements *(mandatory)*

### Functional Requirements

#### Fechas, períodos y consistencia de Consumo

- **FR-001**. El equipo debe comprobar, con evidencia de origen y ejemplos reproducibles, cómo interpreta las fechas cada importador y dónde se determina la fecha que ve el usuario. El diagnóstico debe distinguir un error de interpretación de una diferencia válida de presentación o atribución.
- **FR-002**. En Consumo, Enerlink debe mostrar la fecha y hora declaradas en su archivo, incluidos los segundos informados, y atribuir la sesión a la fecha declarada de inicio. Si una corrección autorizada acredita otros valores, las consultas nuevas usan los valores vigentes en la base temporal de origen y el detalle autorizado conserva los originales. Se debe identificar la zona o el desplazamiento de origen y conservar las fechas originales de inicio y fin y su correspondencia con los instantes reales. La presentación y la clasificación no deben aplicar una segunda conversión.
- **FR-003**. Wallbox debe conservar su política vigente de interpretación, presentación y atribución, incluidos los casos que ya coinciden con el archivo. La política temporal de Enerlink no se extiende a otras fuentes ni a otros módulos.
- **FR-004**. El sistema debe distinguir el instante real, la fecha y hora de origen, la zona declarada o aplicable y la fecha de atribución. Debe conservar evidencia original y distinguirla de cualquier dato corregido. El orden real y la coherencia entre inicio y fin se verifican con sus instantes. En la importación Enerlink se deben aplicar los casos de desplazamiento ausente, inválido o distinto entre extremos descritos en esta especificación.
- **FR-005**. El histórico, los filtros, el tablero, las tendencias y los informes nuevos PDF y Excel deben aplicar una regla de atribución coherente, incluso al consultar históricos. El período se interpreta con inicio inclusivo y fin exclusivo, y cada sesión elegible se cuenta una vez por su inicio. Las vistas y los informes deben identificar el rango y la base temporal aplicable. El tablero identifica su fecha y hora de cálculo y cada informe nuevo identifica el corte de sus propios datos. La concordancia de cifras se verifica con el mismo conjunto de revisiones, filtros y permisos. El período inicial de mes en curso se conserva conforme a D13.
- **FR-006**. El consolidado debe agrupar las sesiones elegibles por la fecha de atribución de inicio que corresponde a cada fuente, bajo una misma etiqueta de calendario. Enerlink aplica FR-002, Wallbox aplica FR-003 y los casos históricos indeterminados aplican FR-006a. El histórico, los filtros, el tablero, la tendencia y los informes nuevos PDF y Excel deben usar esa misma atribución. Activar o quitar un filtro de plataforma cambia el conjunto de sesiones, sin cambiar la fecha de las que permanecen. El consolidado y sus exportaciones deben rotular el criterio como «Fecha de inicio según la fuente», acompañado del desglose de FR-006a cuando corresponda. El rango inicial y los accesos rápidos se calculan a partir del día operativo de Ecuador conforme a D13, y sus límites se aplican a las fechas de atribución.
- **FR-006a**. *(Retirado por la aclaración del 2026-11-13: después de V019 no hay sesiones con fecha de origen indeterminada.)* Una sesión histórica cuya fecha de origen sea indeterminada conserva su fecha operativa previa, sus instantes y su elegibilidad. Si ya es elegible, cuenta una sola vez en los filtros, totales, tendencias y ranking de esa fecha. El histórico identifica la fila como «Fecha operativa heredada» y el dashboard, PDF y Excel muestran, cuando el resultado contiene sesiones oficiales en esa situación, cuántas sesiones y cuánta energía conocida del total corresponden a ese criterio. El desglose es parte del total y conserva la diferencia entre energía desconocida y cero. Cuando se acredita y aprueba la fecha de origen, la sesión pasa al criterio verificado y deja de integrar el desglose heredado.

#### Auditoría y corrección histórica

- **FR-007**. *(Acreditado por H006–H007; ver US2.)* La auditoría debe identificar el universo revisado, la evidencia disponible y los registros afectados. Debe distinguir los cambios de fecha de atribución debidos a la nueva regla de los errores demostrables de instante o zona. Debe señalar los casos sin evidencia suficiente sin modificarlos automáticamente. Antes de revisar la publicación del histórico con el nuevo criterio, debe existir un diagnóstico de solo lectura sobre una copia autorizada del conjunto histórico pertinente, con corte, alcance, cobertura, casos indeterminados e impacto por fuente y período. Si esa evidencia falta, la publicación del histórico queda pendiente.
- **FR-008**. *(Acreditado por H006–H007; ver US2.)* Antes de cualquier corrección masiva, se debe presentar al responsable autorizado la relación de sesiones, motivo, valores originales y propuestos y el efecto en filtros, conteos y kWh de los períodos afectados. Solo un Administrador autenticado puede registrar la aprobación explícita en el sistema. La constancia vincula la identidad del aprobador, el momento, la propuesta y la huella exacta del alcance revisado. El aplicador debe comprobar la constancia de una fuente controlada por el sistema. Si falta, es inválida, o no coincide con la propuesta y huella, se rechaza el conjunto antes de escribir. Si cambia el alcance o los valores, se exige una nueva aprobación.
- **FR-009**. *(Acreditado por H006–H007; ver US2.)* Las correcciones autorizadas deben conservar identidad, valores previos, autor, fecha y motivo. Una corrección exclusivamente temporal puede redistribuir cifras entre períodos y debe conservar el conteo y la energía total del conjunto afectado cuando se compara con igual elegibilidad. Reintentar un conjunto ya aplicado debe dejar el mismo resultado, sin duplicar sesiones.
- **FR-010**. Los informes ya emitidos deben conservar contenido, identificación, corte y controles de acceso. Los informes nuevos deben aplicar el criterio vigente, identificarse como una nueva emisión y mantener la trazabilidad del corte, conforme a D12 y la Feature-004.

#### Acciones por sesión y controles vigentes

- **FR-011**. El histórico debe ofrecer una columna de acciones por sesión con «Ver», «Editar» y «Eliminar» según los permisos efectivos. «Ver» debe mostrar los datos de la sesión, origen, equipo, fechas, zona, energía y estado. El alcance de los datos operativos y de auditoría debe respetar las restricciones vigentes, también al acceder directamente al detalle.
- **FR-012**. Los Administradores y Analistas con permiso de modificación deben poder editar datos operativos erróneos, incluidos inicio, fin, equipo vinculado y energía, con justificación obligatoria, confirmación y conservación de versiones. La identidad de la sesión, el identificador y plataforma de origen y la evidencia importada se conservan. Los demás roles mantienen su acceso de consulta.
- **FR-013**. Las ediciones deben aplicar las reglas vigentes de vinculación, coherencia temporal, validación, energía conocida o desconocida, normalización por lectura y revisión de energía superior al umbral. Una corrección que requiere una nueva revisión queda fuera de cifras oficiales hasta la decisión correspondiente. El sistema debe advertir si otra operación cambió la sesión desde que se abrió la edición.
- **FR-014**. Los editores autorizados deben poder eliminar lógicamente una sesión mediante confirmación y motivo obligatorio. El sistema debe excluirla de indicadores e informes oficiales futuros y conservar el registro, su identidad y auditoría para consulta autorizada. Una cancelación o acción no autorizada no debe cambiar su estado.
- **FR-015**. Una reimportación debe respetar la identidad existente, las correcciones aprobadas y las eliminaciones lógicas. Los datos idénticos no crean otra sesión y los discrepantes requieren resolución explícita según las reglas vigentes, sin modificar ni reactivar automáticamente el registro.
- **FR-016**. Toda edición, eliminación lógica, corrección histórica y aprobación aplicable debe poder reconstruirse por sesión, con autor, momento, motivo y valores o estados antes y después. Los usuarios sin permiso de modificación no deben poder ejecutar esas acciones desde ninguna vía disponible.

#### Estabilidad visual y experiencia de uso

- **FR-017**. Se deben reproducir y documentar los parpadeos observados, incluyendo pantalla, condiciones, acción o actualización que los dispara, evidencia y causa confirmada. El diagnóstico empieza en los dashboards y se amplía a vistas con síntomas equivalentes. Debe distinguir hallazgos confirmados de casos sin reproducción.
- **FR-018**. Las actualizaciones legítimas deben seguir entregando datos recientes. Con el mismo contexto, deben conservar la continuidad visual de tarjetas, gráficos, tablas y formularios, sin recargas completas, reinicios visibles de animación o desapariciones del contenido que no se deban a una acción o cambio real. Se conservan los filtros, la navegación y el estado de interacción que sigan siendo válidos.
- **FR-019**. Los defectos equivalentes en otras pantallas se incluyen cuando la auditoría demuestra una causa compartida. Los cambios que interfieran con las pruebas en curso de Feature-008 deben quedar coordinados con su responsable antes de intervenir sus archivos o flujos. Los casos corregidos deben contar con evidencia anterior y posterior.
- **FR-020**. Las acciones, formularios y diálogos deben seguir los colores, la estructura, la tipografía y los componentes existentes de SARC. Deben tener etiquetas y mensajes claros, controles accesibles por teclado, foco visible y contenido sin superposiciones en móvil, tablet y escritorio, tanto en tema claro como oscuro.

#### Trazabilidad documental y compatibilidad

- **FR-021**. El cambio funcional aprobado de fecha de origen para Enerlink debe registrarse en el control de cambios del PRD. Se deben identificar las secciones del PRD, TRD y las especificaciones dependientes que requieran conciliación y resolver cualquier incompatibilidad con la Constitución vigente antes de implementar el comportamiento afectado.
- **FR-022**. La aceptación debe verificar las regresiones aplicables de RF-001, RF-002, RF-003 y Feature-008. Se deben conservar los comportamientos de Consumo ajenos a la modificación aprobada, incluidos la vinculación, los indicadores de energía y cobertura, el orden del ranking y sus empates y la autorización de informes archivados.

### Trazabilidad documental

| Fuente | Aplicación a Feature-009 |
| --- | --- |
| PRD v1.1, §3.2, §7.1–7.3 y §11 | Revisar las definiciones de período, interpretación de origen, atribución por inicio de RF-003.10, filtros e informes. FR-002 y FR-006 precisan la fecha de inicio que corresponde a Enerlink y el criterio del consolidado. |
| Constitución vigente 1.2.0, principios I–III, D05 y D09–D14 | Mantener reglas explícitas y continuidad de identidad, primera hoja, revisión de energía, redondeo por lectura, nombres actuales en nuevos informes y Top 5 con empates. D13 fija el período inicial y los accesos rápidos según el día operativo de Ecuador. FR-006 fija la atribución de las sesiones dentro de esos rangos. |
| TRD v1.0, pp. 4, 11 y 15 | Revisar la vista en America/Guayaquil frente a la presentación de Enerlink, conservar la representación del instante real y alinear el diccionario de indicadores y períodos. |
| Feature-002, FR-012, y ADR 0005 de Consumo | Revisar la atribución previa al día operativo en Consumo y precisar la excepción Enerlink. Mantener la distinción entre energía ausente y cero y las reglas de elegibilidad y conciliación. |
| Features 003 y 004 | Preservar revisión energética, redondeo, empates y permisos de acceso a información operativa e informes archivados. Usar el patrón de auditoría visual de la Feature-004. |

### Key Entities *(include if feature involves data)*

- **Sesión de carga**. Registro de una fuente con identidad estable, equipo, inicio y fin si existe, energía, estado y fecha de atribución para Consumo.
- **Evidencia de origen**. Archivo o datos originales, referencia de importación, identificador externo, fechas y zona declarada o aplicable.
- **Revisión de sesión**. Cambio o decisión con autor, momento, justificación y valores o estados anteriores y posteriores.
- **Propuesta de corrección histórica**. Conjunto identificable de sesiones, evidencia, impacto por período y autorización correspondiente.
- **Informe emitido**. Copia conservada con alcance, filtros, base temporal, fecha de corte y permisos de acceso aplicables.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**. Enerlink 1212 y 1211 coinciden con su fecha y hora de origen y se atribuyen al 11/11/2026 sin cambiar sus instantes reales. El caso Wallbox conserva la fecha operativa del 10/11/2026 y su presentación vigente.
- **SC-002**. El 100 % de las sesiones del conjunto de aceptación, incluidos los límites de día y mes, se atribuye al período esperado. En el conjunto mixto de US1 se obtienen 5 kWh el 10/11, 8 kWh el 11/11 y 13 kWh en el rango completo. Con la sesión indeterminada adicional se obtienen 15 kWh en total, incluidos 2 kWh heredados. Histórico, tablero, tendencia, PDF y Excel coinciden en los valores comunes si las revisiones, filtros y permisos son los mismos. Una edición entre consultas se refleja en los datos del corte correspondiente y cada salida identifica su propio momento.
- **SC-003**. El 100 % de los candidatos a corrección histórica tiene evidencia, clasificación e impacto revisable o se identifica como indeterminado. El diagnóstico del conjunto histórico pertinente queda disponible antes de revisar su publicación. Las correcciones exclusivamente temporales provocan cero cambios injustificados de kWh o conteo total, y un reintento crea cero duplicados. Un conjunto sin aprobación válida produce cero cambios.
- **SC-004**. En el 100 % de las pruebas de roles, los editores autorizados pueden completar las acciones válidas y las cuentas de consulta reciben cero permisos de modificación. Toda edición o eliminación completada deja una justificación y un historial consultable por roles autorizados.
- **SC-005**. En las pruebas de reimportación de sesiones editadas y eliminadas, se crean cero duplicados, se revierten cero correcciones aprobadas y se producen cero reactivaciones automáticas. Los informes archivados de la muestra conservan su contenido y corte.
- **SC-006**. Los recorridos que reproducen el parpadeo muestran cero recargas completas o reinicios de animación innecesarios después de la corrección. Las pruebas con datos nuevos confirman su actualización y la conservación del contexto válido.
- **SC-007**. Una persona autorizada puede completar «Ver», «Editar» y «Eliminar» desde la fila correspondiente. Los recorridos en móvil, tablet y escritorio, en tema claro y oscuro, completan las acciones y muestran sus mensajes sin superposiciones ni pérdida de acceso por teclado.
- **SC-008**. El 100 % de las verificaciones de regresión seleccionadas para RF-001, RF-002, RF-003 y Feature-008 conserva sus resultados esperados, salvo las diferencias temporales de RF-003 aprobadas para esta entrega.

## Assumptions

- «Sesiones» se refiere a sesiones de carga. El alcance de edición operativa se explicita en FR-012 y conserva las decisiones de identidad y auditoría vigentes. Restaurar sesiones eliminadas queda fuera de esta entrega.
- Para Wallbox se conserva la precisión de la presentación vigente en la lista y se ofrece la precisión disponible de origen en el detalle autorizado.
- La corrección histórica se delimita con la evidencia que se pueda recuperar. No se presume que todo desplazamiento a Ecuador represente un error de datos.
- En la revisión visual se podrá usar Impeccable cuando esté disponible y aporte valor. La aceptación se basa en evidencia del comportamiento y en la coherencia con SARC.
- Esta fase genera la especificación y su checklist. La revisión de código, las propuestas de diseño y la ejecución de correcciones sobre datos corresponden a fases posteriores.
