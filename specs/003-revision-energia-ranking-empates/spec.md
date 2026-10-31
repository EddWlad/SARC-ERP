# Feature Specification: Revisión de energía alta y empates del ranking

**Feature**: `003-revision-energia-ranking-empates`

**Created**: 2026-10-30

**Status**: Draft — pendiente de aprobación funcional y planificación.

**Input**: Corregir dos diferencias entre consumo y la constitución de SARC. D10: una energía superior a 1.000 kWh por sesión debe ser observación revisable con decisión humana trazada, justificación y aprobación antes de computarse, no rechazo definitivo; aplica a Enerlink y Wallbox. D14: el ranking Top 5 por energía debe incluir todos los equipos empatados con el quinto lugar, indicarlo en la presentación y no usar el UUID para excluir un empatado.

## Clarifications

### Session 2026-10-30

- Q: Una sesión con más de 1.000 kWh, ¿se decide después de guardarla o durante la vista previa del lote? → A: Después: la fila se confirma, la sesión queda guardada «Energía por revisar» fuera de indicadores y la decisión se toma en el histórico.
- Q: Si una lectura se aprobó o rechazó por error, ¿puede una persona autorizada cambiar esa decisión sin reimportar? → A: Sí: mediante una nueva decisión con justificación, en cualquier sentido; las anteriores se conservan como historial.
- Q: En el Top 5 y en el ranking completo de los informes, ¿qué número de posición llevan los equipos empatados? → A: Misma posición y salto: 1, 2, 3, 4, 4, 4, 7.
- Q: En los informes nuevos, ¿una sesión aprobada sobre 1.000 kWh debe distinguirse de las demás sesiones oficiales? → A: Sí: el PDF indica cantidad y energía de aprobadas sobre el umbral en su resumen, y el Excel marca cada sesión con su justificación vigente.
- Q: Si una sesión de 1.500 kWh fue aprobada o rechazada y después se corrige otro dato sin cambiar los kWh, ¿debe conservarse la decisión energética? → A: Conservar aprobación o rechazo si los kWh normalizados no cambian; los demás controles se vuelven a validar.
- Q: Si una sesión está pendiente de vincular y también tiene energía por revisar, ¿cómo debe contarse en los resúmenes del tablero y los informes? → A: Solo en su estado principal, con categorías excluyentes: primero prueba, después pendiente de vínculo, luego revisión energética y finalmente aprobación de fuente. El filtro energético permite encontrarla aunque su estado principal sea otro.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Revisar lecturas superiores al umbral (Priority: P1)

Como analista autorizado, quiero que una sesión con más de 1.000 kWh quede registrada como observación revisable, para decidir con justificación si se computa, sin perder el dato por un rechazo automático.

**Why this priority**: Hoy el dato se pierde por superar un umbral. Un valor alto puede ser real o erróneo, y solo una persona con contexto puede decidirlo.

**Independent Test**: Importar una sesión de 1.500 kWh por cada fuente; comprobar que queda registrada fuera de los indicadores, aprobarla con justificación y verificar que entonces suma una sola vez.

**Acceptance Scenarios**:

1. **Given** una fila de Enerlink o Wallbox con 1.500 kWh y demás datos válidos, **When** se importa y confirma, **Then** la sesión queda registrada como «Energía por revisar», visible con su valor y su motivo, sin sumar en indicadores oficiales.
2. **Given** una sesión por revisar, **When** una persona autorizada la aprueba con una justificación, **Then** queda elegible para indicadores y se conservan quién decidió, cuándo, la justificación y el valor aprobado.
3. **Given** una sesión por revisar, **When** se intenta aprobar sin justificación, **Then** la operación se rechaza con un motivo en español y la sesión no cambia.
4. **Given** una sesión por revisar, **When** una persona autorizada la rechaza con una justificación, **Then** sigue consultable como «Energía rechazada», no suma y conserva la decisión.
5. **Given** una lectura de exactamente 1.000,000 kWh, **When** se importa, **Then** no se observa. **Given** una de 1.000,001 kWh, **When** se importa, **Then** se observa.
6. **Given** una sesión aprobada sobre el umbral, **When** se reimporta con el mismo contenido, **Then** no se duplica ni pierde su aprobación ni exige otra revisión.
7. **Given** una sesión aprobada de 1.500 kWh, **When** una corrección propone 1.800 kWh, **Then** tras la aprobación vigente de la corrección vuelve a quedar por revisar y deja de sumar hasta una nueva decisión; la versión anterior y su decisión se conservan.
8. **Given** una sesión aprobada o rechazada por error, **When** una persona autorizada registra una nueva decisión con justificación, **Then** rige la nueva decisión, los indicadores nuevos la reflejan y la anterior sigue en el historial.
9. **Given** una sesión de 1.500 kWh aprobada o rechazada, **When** se aplica una corrección de otro dato sin cambiar los kWh normalizados, **Then** conserva la aprobación o el rechazo vigente sin exigir otra decisión energética ni crear una decisión adicional; se vuelven a validar las demás condiciones oficiales y se conserva la trazabilidad de la corrección.

---

### User Story 2 - Respetar las demás reglas al revisar (Priority: P1)

Como responsable del consumo, quiero que aprobar una lectura alta no sustituya ningún otro control, para que los indicadores oficiales sigan siendo confiables.

**Why this priority**: D10 prohíbe que la aprobación omita las reglas de coherencia.

**Independent Test**: Combinar lecturas altas con equipo pendiente, fuente no preferente, marca de prueba y datos incoherentes; comprobar que ninguna suma hasta cumplir todos los requisitos.

**Acceptance Scenarios**:

1. **Given** una fila sobre el umbral con término anterior al inicio, energía negativa o texto no numérico, **When** se importa, **Then** es inválida con su motivo y no llega a revisión.
2. **Given** una sesión sobre el umbral sin equipo identificado, **When** se aprueba su energía, **Then** sigue sin sumar hasta vincularse.
3. **Given** una sesión sobre el umbral de fuente no preferente, **When** se aprueba su energía, **Then** sigue sin sumar hasta aprobar también la fuente, y viceversa.
4. **Given** una sesión aprobada, **When** se marca como prueba, **Then** se excluye de indicadores; al reincorporarla conserva su decisión.
5. **Given** sesiones por revisar y rechazadas, **When** se consulta tablero, histórico o informe nuevo, **Then** se informan aparte con cantidad y energía, sin sumar a los totales oficiales.
6. **Given** una sesión aprobada sobre el umbral y elegible, **When** se genera un informe nuevo, **Then** suma en los totales, el PDF indica cantidad y energía de esas excepciones y el Excel marca la sesión con su justificación vigente.
7. **Given** una sesión sin equipo con energía por revisar, **When** se consultan los resúmenes del tablero o los informes, **Then** su cantidad y energía aparecen solo en pendientes de vínculo, no también en energía por revisar; el filtro energético del histórico sí permite encontrarla. Después de vincularla, pasa a la categoría energética que corresponda sin duplicar su conteo.
8. **Given** una sesión marcada como prueba que además está sin equipo o tiene revisión energética, **When** se consultan los resúmenes, **Then** aparece únicamente en pruebas; al reincorporarla se recalcula su estado principal y se conservan su condición y decisiones energéticas.

---

### User Story 3 - Ver todos los empatados en el Top 5 (Priority: P1)

Como lector del tablero o del informe gerencial, quiero que el Top 5 incluya a todos los equipos empatados con el quinto lugar, para que ningún equipo quede fuera por un criterio sin significado.

**Why this priority**: Hoy un identificador interno decide quién aparece, lo que da una lectura arbitraria del ranking.

**Independent Test**: Preparar tres equipos empatados en el quinto lugar y comprobar que aparecen los siete, con su indicación, en tablero y PDF.

**Acceptance Scenarios**:

1. **Given** siete equipos cuyo cuarto, quinto y sexto lugar empatan en energía, con el séptimo por debajo, **When** se consulta el Top 5, **Then** figuran seis equipos con posiciones 1, 2, 3, 4, 4 y 4, se indica que incluye más de cinco por empate y el séptimo no aparece.
2. **Given** cinco o más equipos sin empate en el quinto lugar, **When** se consulta, **Then** figuran exactamente cinco, sin indicación de empate.
3. **Given** un empate que no alcanza el quinto lugar, **When** se consulta, **Then** figuran exactamente cinco.
4. **Given** menos de cinco equipos, **When** se consulta, **Then** figuran los disponibles, sin indicación de empate.
5. **Given** el mismo período y filtros, **When** se comparan tablero e informes nuevos, **Then** muestran los mismos equipos, cifras e indicación.
6. **Given** equipos empatados, **When** se repite la consulta, **Then** mantienen el mismo orden visible, por nombre del equipo.

### Edge Cases

- El umbral se evalúa sobre la lectura normalizada a tres decimales con HALF_UP: 1.000,0004 kWh no se observa; 1.000,0005 kWh sí.
- Una energía no informada, cuando la fuente la admita, no se observa: no hay lectura que revisar.
- Una energía igual o superior a 1.000.000.000 kWh excede lo que SARC puede guardar: la fila es inválida con su motivo, sin revisión, y el lote continúa.
- Corregir una sesión por revisar a un valor no superior al umbral elimina la observación mediante el flujo de corrección vigente; la observación y la corrección quedan en su historial.
- Una lectura superior al umbral repetida dentro de un lote o entre lotes respeta la identidad de sesión y no crea otra.
- El aviso de lectura superior al umbral no revoca una decisión existente. Al reimportar una sesión idéntica aprobada o rechazada se conserva esa decisión; el mensaje no debe anunciar que volverá a revisión. El conteo de filas con lecturas altas debe identificarse como tal y no presentarse como cantidad de sesiones pendientes de decisión ni sumarse a las clasificaciones del lote.
- Una revisión basada en una versión ya modificada de la sesión no se aplica y se informa que quedó obsoleta.
- Equipos que parecen empatados al mostrarse con dos decimales pero difieren en el tercero no están empatados.
- Si todos los equipos empatan, incluso con 0 kWh conocidos, todos aparecen.
- Aprobar o rechazar lecturas cambia los totales y puede cambiar el ranking de consultas e informes nuevos, no los ya emitidos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Una lectura normalizada superior a 1.000 kWh por sesión DEBE registrarse como observación revisable, no como rechazo definitivo basado solo en ese umbral, para Enerlink y Wallbox.
- **FR-002**: Una sesión observada DEBE conservarse con su lectura original y motivo, y NO DEBE sumar en indicadores oficiales hasta su aprobación.
- **FR-003**: Una persona con los permisos vigentes de edición de consumo DEBE poder aprobar o rechazar una sesión observada desde el histórico de sesiones; la vista previa no exige ni registra esa decisión. Cada decisión DEBE exigir una justificación no vacía de hasta 500 caracteres.
- **FR-004**: Cada decisión DEBE conservar persona, fecha, decisión, justificación y lectura evaluada, y mantenerse consultable tras correcciones posteriores.
- **FR-005**: Una decisión NO DEBE aplicarse sobre una versión de la sesión distinta de la revisada; DEBE informarse que la revisión quedó obsoleta.
- **FR-006**: La aprobación NO DEBE omitir coherencia temporal, formato, identidad de sesión, vinculación, aprobación de fuente ni marca de prueba. Una sesión aprobada solo suma si cumple todas las condiciones oficiales.
- **FR-007**: Una sesión rechazada DEBE permanecer consultable y excluida de indicadores oficiales. Una persona autorizada DEBE poder sustituir una decisión, aprobada o rechazada, por otra nueva con justificación, sin reimportar; la vigente es la más reciente y las anteriores NO DEBEN modificarse ni eliminarse.
- **FR-008**: Reimportar el mismo contenido NO DEBE duplicar la sesión ni alterar su decisión. Una corrección de otros datos que conserve los kWh normalizados DEBE mantener la aprobación o el rechazo vigente sin exigir ni generar otra decisión energética; las demás condiciones oficiales DEBEN volver a validarse. Si cambia la lectura normalizada y el nuevo valor supera el umbral, DEBE quedar por revisar y exigir una nueva decisión; una aprobación o un rechazo de otra lectura NO DEBE aplicarse automáticamente al valor corregido. Las versiones y decisiones anteriores DEBEN conservarse.
- **FR-009**: Tablero, histórico e informes nuevos DEBEN informar aparte, con cantidad y energía, las sesiones cuyo estado principal sea energía por revisar o energía rechazada, usando los mismos filtros y sin incluirlas en totales oficiales. Los resúmenes DEBEN usar categorías excluyentes: prueba prevalece sobre pendiente de vínculo; pendiente de vínculo prevalece sobre energía rechazada o por revisar; la condición energética pendiente o rechazada prevalece sobre aprobación de fuente pendiente; solo después de satisfacer todos los controles corresponde el estado oficial. Una sesión DEBE contarse en una sola categoría principal, conservando sus otras condiciones para consulta. Las aprobadas sobre el umbral que cumplan todos los controles suman y DEBEN distinguirse como subconjunto de las oficiales en informes nuevos: el PDF DEBE indicar su cantidad y energía en el resumen, y el Excel DEBE marcar cada sesión con su justificación vigente; ese subtotal NO DEBE sumarse nuevamente al total oficial.
- **FR-010**: Vista previa e histórico DEBEN permitir identificar y filtrar las sesiones por revisar y mostrar su motivo en español. En el histórico, el filtro energético DEBE operar sobre la condición energética, independientemente del estado principal, de modo que también encuentre sesiones por revisar que estén en pruebas o pendientes de vínculo, sin modificar su categoría de resumen ni su elegibilidad oficial.
- **FR-011**: Los rechazos existentes por incoherencia, formato, negativos o duplicados NO DEBEN cambiar. La comparación con el umbral y las sumas DEBEN usar valores redondeados por lectura (D11).
- **FR-012**: El Top 5 DEBE ordenarse por energía oficial e incluir a los cinco primeros y a todos los empatados con el que ocupa el quinto lugar, según energía normalizada.
- **FR-013**: Cuando el Top 5 contenga más de cinco equipos DEBE indicar que los incluye por empate en el quinto lugar, en tablero y en informes nuevos.
- **FR-014**: Un identificador interno NO DEBE decidir qué equipo empatado entra o sale del ranking. Los empatados DEBEN compartir número de posición y mostrarse en un orden estable por nombre del equipo; el siguiente equipo toma la posición que le corresponde tras el salto (1, 2, 3, 4, 4, 4, 7), también en el ranking completo de los informes.
- **FR-015**: Tablero e informes nuevos DEBEN mostrar el mismo conjunto de equipos con los mismos filtros. El ranking completo de los informes conserva todos los equipos y el mismo orden por energía.
- **FR-016**: Con menos de cinco equipos DEBEN mostrarse los disponibles. Los equipos excluidos del ranking por otras reglas vigentes permanecen fuera antes de determinar el quinto lugar.
- **FR-017**: La mejora NO DEBE ampliar permisos ni reescribir informes emitidos, sesiones históricas ni filas de lotes anteriores. Las filas ya rechazadas por el umbral solo se recuperan reimportando la fuente.

### Key Entities *(include if feature involves data)*

- **Sesión observada**: Sesión con lectura superior al umbral, cuya condición es por revisar, aprobada o rechazada, independiente de su vinculación, fuente y marca de prueba.
- **Decisión de revisión**: Aprobación o rechazo de una lectura concreta, con persona, fecha, justificación y versión de la sesión evaluada. Una sesión puede tener varias; la más reciente es la vigente.
- **Ranking Top 5**: Lista ordenada por energía oficial con cinco posiciones que pueden contener más de cinco equipos por empate en la quinta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las lecturas de prueba superiores a 1.000 kWh, por lo demás válidas, de ambas fuentes queda registrado y revisable; ninguna se pierde por el umbral.
- **SC-002**: Ninguna sesión por revisar o rechazada suma en indicadores oficiales en el 100 % de los casos de prueba; una aprobada que cumpla las demás condiciones suma exactamente una vez.
- **SC-003**: El 100 % de las decisiones de prueba muestra persona, fecha, justificación y lectura evaluada, incluso después de corregir la sesión.
- **SC-004**: Reimportar dos veces adicionales un conjunto con sesiones aprobadas produce cero sesiones, decisiones o cambios de indicadores adicionales.
- **SC-005**: En los casos de empate de prueba, el 100 % de los equipos empatados con el quinto lugar aparece, y sus cifras coinciden en tablero e informes con los mismos filtros.
- **SC-006**: En el 100 % de los casos con más de cinco equipos la indicación es visible, y no aparece en ningún caso de cinco o menos.
- **SC-007**: Las lecturas no superiores al umbral y los rankings sin empate en el quinto lugar mantienen sus resultados actuales en el 100 % de los casos de regresión.

## Assumptions

- **Rechazo**: Rechazar no elimina la sesión; D10 exige trazabilidad y SARC no borra históricos de consumo.
- **Revisor**: Una misma persona con permiso puede importar y decidir. D10 no exige separación de funciones.
- **Umbral**: 1.000 kWh es fijo para ambas fuentes y todo tipo de equipo, como ratifica D10. No se incorporan umbrales configurables.
- **Empates**: D14 ratifica incluirlos; no se aprueba un desempate por número de sesiones. El nombre solo fija el orden visible.
- **Dependencia**: La feature `002-vinculacion-enerlink-sesiones` introduce energía no informada y exclusión de equipos sin energía del ranking, y toca las mismas áreas. Conviene implementarla antes; esta especificación es compatible con ambos órdenes.
- **Datos previos**: La base no admite hoy sesiones sobre el umbral, por lo que no se espera migrar decisiones. No se verificó en esta redacción cuántas filas de lotes anteriores fueron rechazadas por ese motivo.
- **Constitución**: Implementa D10 y D14; conforme con I–II, D11 y D12. No requiere enmienda constitucional.
