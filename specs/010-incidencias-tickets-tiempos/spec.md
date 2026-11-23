# Feature Specification: Incidencias, tickets y tiempos

**Feature**: `010-incidencias-tickets-tiempos`

**Feature Branch**: `main` (rama existente; este comando no crea ni cambia ramas)

**Created**: 2026-11-20

**Status**: Aclarada y en planificación. Q1 a Q5 y las decisiones P-03 a P-16 se resolvieron el 2026-11-20. P-02 (versión vigente del protocolo: 2.0) y P-01 (número inicial: lo configura el Administrador en producción) se resolvieron el 2026-11-23. Plan técnico en `plan.md`, sin implementación.

**Input**: Módulo RF-006 de SARC: incidencias, tickets y tiempos. Centraliza el registro, la atención, el seguimiento y el cierre de incidencias de puntos de carga, electrolineras, el servicio de chatbot, barreras vehiculares y otros elementos del sitio. Ofrece trazabilidad por turnos, hitos T0–T4 verificables y un mensaje para copiar y enviar a mano por WhatsApp. Incluye dos reportes separados: «Control de tickets» y el reporte de gestión de incidencias. Evita digitar las 44 columnas del Excel actual.

**Fuentes revisadas**:

- Constitución 1.2.0.
- PRD v1.1: §2.5, §4, RF-006 (§10.1–10.5, CA-006-01 a CA-006-08), §11 (RF-RPT), §12 (DT-01 a DT-11, RNF-UX, RNF-SEG, RNF-NOT, RNF-DAT) y §13.
- Control de cambios del PRD.
- Especificaciones 001 a 009.
- Módulos actuales de Inventario, Consumo, Intermitencias, Uptime e Informes.
- Protocolo de monitoreo y respuesta operativa (PDF firmado, 23 páginas).
- Imagen de referencia del informe «Control de tickets».

No define tablas, migraciones, endpoints ni estructura de código.

## Convenciones de esta especificación

Cada regla indica su procedencia con una de estas etiquetas:

- **[Confirmado]**: decisión expresa del responsable del producto en la solicitud de esta feature (2026-11-20).
- **[PRD]**: requisito del PRD v1.1 vigente.
- **[Protocolo]**: referencia del protocolo operativo. No es SLA contractual ni regla aprobada del producto, salvo que el PRD o el responsable la adopten.
- **[Propuesta]**: valor por defecto razonable de esta especificación. Puede cambiarse en `/speckit-clarify` sin reabrir las demás reglas.
- **[Pendiente P-nn]**: decisión abierta. No se resuelve de forma implícita al implementar (Constitución, Principio II).

## Clarifications

### Session 2026-11-20

- Q: Con un período elegido en «Control de tickets», ¿qué tickets aparecen? → A: Los que estuvieron abiertos en algún momento del rango. Un ticket entra si alguno de sus episodios cumple dos condiciones: su T0 es anterior al fin del rango, y sigue abierto o su T4 no es anterior al inicio del rango (opción A).
- Q: Para una falla del propio servicio de chatbot, ¿qué datos se piden al crear y qué exige el cierre? → A: Al crear se piden tipo, descripción, origen y T0; el alcance (total o parcial) es opcional. Para cerrar hacen falta: el restablecimiento del servicio verificado (T3, o «No aplica» o «Sin dato» con motivo), T4, el resultado, la causa, y la comunicación registrada o una justificación. No se exige evidencia final si se justifica, y no hay referencias de tiempo (opción A).
- Q: ¿Cómo trata SARC la diferencia entre el mensaje de WhatsApp confirmado y el protocolo §4.1, que pide la hora de detección y las acciones? → A: El formato confirmado se usa por defecto. El analista tiene dos opciones, desactivadas al abrir el mensaje: «Incluir hora de detección (T0)» e «Incluir acciones realizadas». Al activarlas, esos datos se añaden al texto (opción B).
- Q: ¿La campana de notificaciones del PRD se construye en esta feature o en una feature transversal aparte? → A: En una feature transversal aparte. Esta feature deja registrados en la cronología los eventos que la alimentarán, y RF-006.22 queda pendiente de esa feature (opción A).
- Q: Si una misma falla afecta a varios equipos del mismo sitio, ¿un ticket o uno por equipo? → A: Un ticket por equipo. Los tickets de una falla común del sitio se relacionan entre sí, y cada uno ocupa su propia fila en los informes (opción A).

**Decisiones del responsable al iniciar el plan (2026-11-20)**:

- P-16: no se importan los tickets anteriores. SARC incluye solo los tickets creados en el sistema y el Excel histórico queda aparte.
- P-10: se admiten barreras vehiculares y otros elementos del sitio, sin equipos ficticios ni conectores o redes atribuidos.
  - Q: ¿Cómo se ubica una barrera u otro elemento del sitio? → A: Con un equipo de referencia del inventario del mismo sitio, que aporta solo su ubicación y su enlace de Maps; no aporta conectores ni red. Si el sitio no tiene equipos, la ubicación se escribe a mano y el enlace de Maps es opcional.
  - Q: ¿En qué red se cuentan esos tickets? → A: En el grupo «Sin red», igual que los del chatbot.
  - Q: ¿Qué clases ofrece el catálogo inicial? → A: «Barrera vehicular» y «Otro elemento del sitio». En el segundo, el analista escribe el nombre del elemento.
- P-12: en «Control de tickets» con un período, se muestra el estado que tenía el ticket al final del período. La regla de inclusión de Q1 se mantiene.
- P-03: las referencias de tiempo se conservan por versión, para que un cambio futuro no altere los reportes ya emitidos.
- P-04: un punto KIA se clasifica como caída persistente después de más de 30 minutos.
- P-05: la prioridad se propone según el protocolo y el analista puede ajustarla con motivo.
- P-06: la recuperación se mide hasta T3 y el cierre hasta T4, como indicadores separados.
- P-07: los gráficos se muestran sin ponderar hasta que se definan y aprueben los pesos.
- P-08: las visitas y el proveedor se registran a mano, sin asignación ni comunicación automática.
- P-11: el catálogo de tipos se mantiene con cambios controlados del sistema e incluye «Incidencia no catalogada».
- P-13: la disponibilidad y las intermitencias se consultan en sus módulos, sin recalcularlas aquí ni presentar estimaciones como cifras oficiales.
- P-14: cada ticket conserva la red que tenía el equipo en su T0.

### Ajustes del análisis de consistencia (2026-11-20)

El responsable aprobó las recomendaciones de `/speckit-analyze`:

- **Datos corregibles del ticket** (FR-036): la descripción, el origen del aviso, el tipo (reclasificación) y la prioridad se corrigen con justificación. El elemento afectado no se corrige: si se eligió mal, el ticket se cierra como «Registrado por error» y se crea otro.
- **Evidencia de T1** (FR-040): se recomienda adjuntar la primera evidencia al registrar T1, pero no es obligatoria.
- **Causa del cierre** (FR-047, FR-048, FR-048a): la descripción de la causa es obligatoria salvo si la causa no está determinada. Una causa probable exige al menos una evidencia del episodio que la respalde.
- **Escalamiento a técnicos** (FR-052): cada escalamiento registra su destino en una lista cerrada (técnicos propios o del proveedor, supervisión, soporte de plataforma u otro). Solo el destino técnico cuenta para la referencia de 60 min de la Espera de conexión.
- **Caída persistente KIA** (FR-051, P-04): «más de 30 min» se compara con la duración exacta, sin redondear a minutos.
- **SC-002**: la acción única se refiere a T1–T3. T4 se propone con la hora actual dentro del cierre.

Segunda ronda del análisis (2026-11-20):

- **Escalamiento después de la recuperación** (FR-052): un escalamiento técnico cuenta solo si ocurre antes de que termine la espera. Si la espera terminó en 60 min o menos, el episodio está dentro de la referencia, aunque después se escale.
- **Orden del listado** (FR-061): se ordena por número, estado, tipo, red, prioridad, responsable actual, antigüedad y última actualización. El elemento afectado se busca y se filtra, pero no se ordena, porque su etiqueta se compone con nombres actuales de varias fuentes.

## Cambios respecto del PRD (requieren registro en el control de cambios)

1. **[Confirmado]** El ticket tiene solo dos estados principales: **Abierto** y **Cerrado**. Esto sustituye los cinco estados de RF-006.9 (ABIERTO, EN_VALIDACION, EN_ATENCION, RECUPERADO_EN_OBSERVACION, CERRADO). Validación, atención, recuperación, observación, escalamiento, visitas, responsables y relevo de turno pasan a ser hitos o eventos de seguimiento. Por eso, el tablero de RF-006.15 deja de contar «en atención» y «observación» como estados. Los muestra como condiciones derivadas de los hitos (FR-062).
2. **[Confirmado]** El ticket puede referirse a:
   - una electrolinera completa, sin elegir conectores;
   - uno o varios conectores;
   - el servicio de chatbot, sin equipo físico;
   - una barrera vehicular u otro elemento del sitio, sin equipo ficticio.

   RF-006.1 exigía elegir un equipo en todos los casos.
3. **[Confirmado, aclarado Q3]** El mensaje para WhatsApp no incluye por defecto la acción ni la hora de detección como atributos separados. RF-006.4 pedía «acción y hora pertinente» y el protocolo §4.1 pide más contenido. Para cubrir esa diferencia, el analista puede incluir T0 y las acciones con dos opciones desactivadas por defecto (FR-056a).
4. **[Confirmado]** Se agrega un informe operativo «Control de tickets», separado del reporte gerencial de RF-006.19.

Estos cambios se registran como CC-PRD-010 en `documents/control-cambios-prd.md`. El PDF maestro no se modifica.

## Referencia visual: informe «Control de tickets»

La imagen de referencia es una hoja de cálculo corporativa. Esta descripción la sustituye, porque el archivo temporal puede desaparecer.

- **Encabezado**, en tres zonas sobre fondo blanco:
  - a la izquierda, el logo de EOS Tech Company;
  - al centro, el título «CONTROL DE TICKETS» en mayúsculas y negrita, con el subtítulo «Electromovilidad | Puntos de carga y electrolineras» en gris;
  - a la derecha, el logo de Autoeastern.
- **Banda** a todo el ancho, azul marino oscuro, con el texto blanco centrado «REGISTRO OPERATIVO».
- **Cabecera de tabla** oscura con texto blanco en mayúsculas y tres columnas:
  - «NÚMERO DE TICKET» (estrecha);
  - «CARGADOR O ELECTROLINERA» (la más ancha);
  - «ESTADO DEL TICKET» (estrecha).

  En la hoja, cada columna tiene un control de filtro.
- **Filas** con fondo claro azulado y separación sutil:
  - número de ticket centrado, con el formato `Ticket-006`;
  - nombre alineado a la izquierda;
  - estado centrado, con valores como «Abierto».
- **Orden**: ascendente por número de ticket.
- **Contenido observado de la segunda columna**:
  - a veces solo el nombre del sitio («SAN MARINO», «City Mall»);
  - a veces el sitio y un elemento o novedad separados por « / » («CC EL BOSQUE / Barrera vehicular», «AWKI PLAZA / barrera no funciona»);
  - a veces varios equipos o posiciones en una sola fila («Guayaquil Country Club #3 y #4», «Conectz TEDESA Machala - KIA TEDASA Machala»).

  En SARC, los varios equipos se registran como un ticket por equipo y se relacionan entre sí (Q5, FR-003a). Las barreras y otros elementos del sitio son una clase propia de elemento afectado (FR-001a). Se muestran con la forma «Sitio / Elemento», como en la imagen.

En SARC, el identificador sigue la forma confirmada `TICKET-086`, en mayúsculas. La imagen muestra `Ticket-006`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar una incidencia de equipo en tres datos (Priority: P1)

Como Analista o Administrador, quiero registrar una incidencia de un punto de carga o de una electrolinera indicando solo el equipo, el tipo de incidencia y una descripción breve. El resto de los datos debe salir del inventario, para no completar a mano las 44 columnas del Excel.

**Why this priority**: Es el núcleo del módulo. Sin tickets no hay seguimiento, tiempos ni reportes. El número único es lo que se cita en WhatsApp.

**Independent Test**: Crear tres tickets:

- una falla general de una electrolinera de dos conectores, sin elegir conectores;
- una falla de un conector de esa electrolinera;
- una falla de dos conectores de otra electrolinera.

Comprobar que se crean tres tickets, no cinco; que cada uno recibe un número consecutivo e inmutable con el formato configurado; y que red, ubicación, tipo de infraestructura, código y enlace de Google Maps provienen del inventario.

**Acceptance Scenarios**:

1. **Given** una electrolinera activa con dos conectores, **When** el analista crea un ticket con equipo, tipo «Desconexión» y descripción, sin elegir conectores, **Then** se crea un solo ticket que afecta a toda la electrolinera. El ticket queda Abierto, con número `TICKET-NNN`, T0 propuesto y los datos del inventario a la vista.
2. **Given** la misma electrolinera, **When** el analista elige los conectores 1 y 2 en un ticket de «Falla de conector», **Then** se crea un único ticket que menciona ambos conectores. No se crea un ticket por conector.
3. **Given** un punto de carga sin enlace de Google Maps en el inventario, **When** se crea el ticket, **Then** se crea sin error y el campo del enlace queda vacío.
4. **Given** una numeración configurada para comenzar en 86, **When** se crean dos tickets seguidos, **Then** reciben `TICKET-086` y `TICKET-087`. Ninguno de esos números se reasigna después.
5. **Given** dos analistas que crean tickets a la vez, **When** ambos confirman, **Then** cada ticket recibe un número distinto y no hay duplicados.
6. **Given** un equipo que ya tiene un ticket Abierto, **When** se intenta crear otro ticket para ese equipo, **Then** el sistema muestra el ticket abierto y ofrece agregar el seguimiento allí. Para crear el nuevo de todos modos hay que indicar un motivo.
7. **Given** un usuario Gerente, Supervisor o Asistente, **When** intenta crear un ticket desde la interfaz o por acceso directo, **Then** la operación se rechaza y no se consume ningún número.

---

### User Story 2 - Registrar hitos T0–T4 y el seguimiento por turnos (Priority: P1)

Como Analista, quiero registrar los hitos T1 a T4 y los eventos de seguimiento con acciones simples que capturen automáticamente mi usuario y la hora. Esos eventos son validación, acción, escalamiento, visita, observación y relevo de turno. Así queda una cronología verificable sin redactar planillas.

**Why this priority**: Los tiempos son el objetivo gerencial de RF-006. Sin una captura simple y fiable, los reportes no tienen sustento.

**Independent Test**: En un ticket, registrar T1 y T2 con un clic cada uno. Corregir T0 retroactivamente con justificación, marcar T3 como «No aplica» con motivo, registrar un relevo de turno y cerrar. Comprobar la cronología, la auditoría, los tiempos derivados y que el ticket nunca mostró un estado distinto de Abierto o Cerrado.

**Acceptance Scenarios**:

1. **Given** un ticket Abierto, **When** el analista pulsa «Registrar primera validación (T1)», **Then** se guarda T1 con su usuario y la hora actual, y se muestra T1−T0.
2. **Given** una validación ocurrida antes del registro en SARC, **When** el analista registra T1 con una hora anterior a la actual, **Then** el sistema exige una justificación y marca el hito como «registrado retroactivamente».
3. **Given** un T1 ya registrado, **When** se corrige su hora, **Then** la corrección exige justificación y conserva el valor anterior, el autor y el momento en la auditoría. Los tiempos derivados se recalculan.
4. **Given** una incidencia que se recuperó sola antes de cualquier acción, **When** el analista marca T2 como «No aplica» con el motivo «recuperación sin intervención», **Then** T2 queda sin hora. T2−T0 no se calcula para ese episodio y se cuenta aparte, nunca como cero.
5. **Given** una hora que se desconoce, **When** el analista marca el hito como «Sin dato» con motivo, **Then** el hito queda fuera de los promedios y se informa como faltante.
6. **Given** una hora anterior a T0 o posterior al momento actual, **When** se intenta registrar un hito, **Then** se rechaza con un mensaje que explica la causa.
7. **Given** un cambio de turno, **When** el analista saliente registra un relevo con el responsable entrante y los pendientes, **Then** el responsable actual del ticket cambia y el relevo queda en la cronología. No se crea un ticket nuevo.
8. **Given** un escalamiento posterior a T2, **When** se registra, **Then** queda como un evento independiente y T2 no se redefine.
9. **Given** una visita técnica, **When** se registran la solicitud, el proveedor, la confirmación y la ejecución o cancelación, **Then** cada paso queda en la cronología con su hora y su autor. Una cancelación conserva quién decidió, con qué evidencia y con qué condición de nueva escalada.

---

### User Story 3 - Registrar incidencias sin cargador: chatbot y elementos del sitio (Priority: P1)

Como Analista, quiero distinguir dónde se originó el aviso de qué elemento está afectado. Así, una falla de cargador reportada por chatbot queda asociada al cargador. Una falla del propio chatbot, de una barrera vehicular o de otro elemento del sitio queda registrada sin elegir un equipo inexistente.

**Why this priority**: Es un requisito confirmado. Sin él, los reportes mezclarían fallas de equipos con fallas del servicio de chatbot o forzarían datos falsos.

**Independent Test**: Crear dos tickets. Uno con origen «Chatbot» y elemento afectado «Punto de carga X». Otro con elemento afectado «Servicio de chatbot» y sin equipo. Comprobar que el primero hereda los datos del inventario y aplica las referencias de su tipo. Comprobar que el segundo se crea sin equipo, no recibe referencias de tiempo inventadas y se presenta en ambos reportes sin nombre de cargador. Crear además una barrera vehicular ubicada con un equipo de referencia y otra en un sitio sin equipos. Comprobar que ninguna hereda conectores ni red, que ambas se cuentan en «Sin red» y que se muestran como «Sitio / Barrera vehicular».

**Acceptance Scenarios**:

1. **Given** un aviso que llega por chatbot sobre un conector retenido, **When** se crea el ticket con origen «Chatbot» y el equipo afectado, **Then** el ticket es una incidencia de equipo de tipo «Conector retenido». No se trata como falla del chatbot.
2. **Given** una caída del servicio de chatbot, **When** se crea el ticket con elemento afectado «Servicio de chatbot», **Then** no se exige equipo, conector, ubicación ni enlace de Maps. El ticket se crea con el tipo «Falla del servicio de chatbot» y la descripción.
3. **Given** un ticket del servicio de chatbot, **When** se consulta su tiempo de validación o recuperación, **Then** no se compara con ninguna referencia del protocolo. En particular, no se aplican los cinco minutos de primera respuesta de consultas de chatbot. Se indica «sin referencia aprobada».
4. **Given** un ticket del servicio de chatbot, **When** se cierra, **Then** no se exigen confirmaciones propias de equipos físicos (estado en la plataforma de carga, sesión de carga sin control, prueba remota). Se aplican los criterios de cierre del chatbot (FR-048): restablecimiento del servicio verificado (T3, o «No aplica» o «Sin dato» con motivo), T4, resultado, causa, y la comunicación registrada o una justificación.
5. **Given** una barrera vehicular que no funciona en un centro comercial con puntos de carga, **When** el analista crea un ticket de clase «Barrera vehicular», elige como referencia un equipo de ese sitio, el tipo «Falla de elemento del sitio» y una descripción, **Then** el ticket toma la ubicación y el enlace de Maps del equipo de referencia, pero no sus conectores ni su red. Se muestra como «Nombre del sitio / Barrera vehicular».
6. **Given** un sitio sin equipos en el inventario, **When** se registra un «Otro elemento del sitio» con nombre de sitio, ubicación y nombre del elemento escritos a mano y sin enlace de Maps, **Then** el ticket se crea sin error, no crea ningún equipo y su mensaje deja vacía la línea de Maps.
7. **Given** un ticket de elemento del sitio, **When** se cierra, **Then** se aplican los criterios de FR-048a, sin confirmaciones de cargadores.

---

### User Story 4 - Preparar y confirmar el mensaje para WhatsApp (Priority: P1)

Como Analista, quiero que SARC prepare un texto normalizado con los datos del ticket, editarlo, copiarlo y pegarlo a mano en WhatsApp. Después quiero registrar en qué canal y a qué hora real lo publiqué.

**Why this priority**: WhatsApp es el canal externo vigente. El número de ticket en el mensaje es lo que une la conversación con el histórico.

**Independent Test**: Generar el mensaje de un ticket con enlace de Maps y el de otro sin enlace. Editar el texto, copiarlo y comprobar que el ticket no aparece como comunicado. Registrar después la comunicación con canal y hora real, y comprobar que aparece en la cronología.

**Acceptance Scenarios**:

1. **Given** un ticket de equipo con todos los datos del inventario, **When** se abre «Mensaje para WhatsApp», **Then** el texto incluye:
   - el número de ticket;
   - el nombre actual del cargador o electrolinera;
   - los conectores, si el ticket es específico;
   - la ubicación;
   - el enlace de Google Maps;
   - la novedad;
   - el estado (Abierto o Cerrado).

   Por defecto no incluye la acción ni la hora de detección.
2. **Given** el mensaje generado, **When** el analista activa «Incluir hora de detección (T0)» e «Incluir acciones realizadas», **Then** el texto añade T0 del episodio actual y las acciones y escalamientos registrados en él, cada uno con su hora. Al desactivarlas, esas líneas desaparecen. Cada vez que se abre el mensaje, ambas opciones empiezan desactivadas.
3. **Given** un equipo sin enlace de Maps, **When** se genera el mensaje, **Then** la línea del enlace queda vacía y se puede copiar sin bloqueo ni error.
4. **Given** el texto generado, **When** el analista lo edita y lo copia, **Then** se copia el texto editado. Los datos del ticket no cambian y no se registra ninguna comunicación.
5. **Given** un texto ya copiado, **When** el analista elige «Registrar comunicación» e indica el canal, la fecha y hora real y el tipo (inicial, actualización o cierre), **Then** la comunicación queda en la cronología con su autor. Si la hora indicada es anterior al registro, se señala como retroactiva.
6. **Given** un mensaje, **When** se genera para cualquier ticket, **Then** nunca contiene UID/PUK, URL OCPP ni otra información operativa restringida.
7. **Given** un ticket del servicio de chatbot, **When** se genera el mensaje, **Then** dice «Servicio de chatbot» en lugar del nombre de un cargador y no muestra líneas de ubicación ni de Maps.

---

### User Story 5 - Cerrar y reabrir conservando episodios (Priority: P1)

Como Analista, quiero cerrar un ticket cuando se cumplen los criterios aplicables, y reabrirlo si la misma falla continúa. Al reabrir, el ticket debe conservar su número y los tiempos del episodio anterior.

**Why this priority**: Sin cierre controlado, los tiempos T4 no son fiables. Sin reapertura por episodios, una reincidencia borraría la medición previa o duplicaría tickets.

**Independent Test**: Cerrar un ticket con T4, causa y evidencia final; reabrirlo con motivo; registrar T0–T4 del segundo episodio y cerrarlo de nuevo. Comprobar que el número no cambia, que existen dos episodios con sus tiempos y que el ticket cuenta como un ticket único con dos episodios.

**Acceptance Scenarios**:

1. **Given** un ticket de equipo Abierto, **When** el analista lo cierra, **Then** el sistema exige:
   - T4, propuesto con la hora actual;
   - T1, T2 y T3 en estado registrado, «No aplica» o «Sin dato», ninguno en blanco;
   - el resultado de cierre;
   - la causa (confirmada, probable con evidencia o no determinada);
   - una evidencia final o la justificación de su ausencia;
   - las confirmaciones de cierre aplicables (FR-047);
   - la comunicación de cierre registrada, o una justificación.
2. **Given** un ticket Cerrado, **When** un Analista lo reabre por continuación del mismo caso, con motivo y la hora de nueva detección, **Then** el ticket vuelve a Abierto con el mismo número y abre el episodio 2 con su propio T0. El episodio 1 conserva su T0–T4 intactos.
3. **Given** una falla nueva e independiente en un equipo con un ticket cerrado, **When** se registra, **Then** se crea un ticket nuevo relacionado con el anterior. No se reabre el anterior.
4. **Given** un ticket creado por error o duplicado, **When** se cierra con el resultado «Registrado por error» o «Duplicado de TICKET-NNN», **Then** conserva su número, no se borra y queda fuera de los indicadores de tiempo. Se informa por separado.
5. **Given** un Gerente, **When** intenta cerrar o reabrir por cualquier vía, **Then** la operación se rechaza.

---

### User Story 6 - Consultar y descargar el informe «Control de tickets» (Priority: P2)

Como cualquier rol autorizado, quiero ver y descargar en cualquier momento el informe «Control de tickets», con todos los tickets abiertos y cerrados, en el formato corporativo de referencia.

**Why this priority**: Reemplaza la hoja que hoy se comparte cada semana. Depende de que existan tickets (US1 a US5).

**Independent Test**: Con 30 tickets de prueba (abiertos, cerrados, reabiertos, del chatbot y de varios conectores), generar el informe sin filtro de período y con los filtros «Hoy», «Este mes» y un rango personalizado. Comprobar las filas, el orden, el texto de la segunda columna, el estado, el encabezado corporativo y la coincidencia entre la vista y las descargas.

**Acceptance Scenarios**:

1. **Given** tickets abiertos y cerrados, **When** se genera el informe sin filtro de período, **Then** aparecen todos los tickets del historial registrados en SARC, ordenados por número ascendente.
2. **Given** un filtro de período, **When** se aplica, **Then** el informe incluye todos los tickets que estuvieron abiertos en algún momento del rango (FR-073): los abiertos desde antes, los detectados dentro del rango y los cerrados dentro de él. Además rotula el período exacto, la zona horaria y la fecha de corte.
3. **Given** un ticket de los conectores 1 y 2 de una electrolinera, **When** se muestra, **Then** la segunda columna indica el nombre actual de la electrolinera y los conectores afectados.
4. **Given** un ticket del servicio de chatbot o de una barrera vehicular, **When** se muestra, **Then** la segunda columna dice «Servicio de chatbot» o «Sitio / Barrera vehicular». Nunca muestra un cargador inventado.
4a. **Given** un ticket abierto el 25 de octubre y cerrado el 3 de noviembre, **When** se genera el informe de octubre, **Then** el ticket aparece con estado **Abierto**, que era su estado al final de octubre. En el informe de noviembre aparece **Cerrado**, y sin período aparece con su estado actual.
4b. **Given** un ticket cerrado en octubre y reabierto en noviembre, **When** se genera el informe de octubre y el de noviembre, **Then** aparece una sola vez en cada uno: Cerrado al final de octubre, y con el estado de su segundo episodio al final de noviembre.
5. **Given** la vista en pantalla, **When** se descarga en PDF y en Excel, **Then** ambos archivos contienen las mismas filas y estados que la vista para el mismo corte y los mismos filtros. Ambos llevan el encabezado corporativo descrito en «Referencia visual».
6. **Given** un Gerente, un Supervisor o un Asistente, **When** abre el informe, **Then** puede verlo y descargarlo, pero no puede modificar ningún ticket desde él.

---

### User Story 7 - Consultar el tablero y descargar el reporte de gestión (Priority: P2)

Como Gerente, Supervisor, Asistente, Analista o Administrador, quiero un tablero y un reporte de gestión con el detalle T0–T4 de cada ticket y episodio. El reporte debe incluir los tiempos derivados con denominadores, los conteos por estado, tipo, red, equipo y prioridad, las visitas, las reaperturas y las desviaciones respecto de las referencias. Debe descargarse en PDF corporativo y en Excel con la bitácora.

**Why this priority**: Es el reporte gerencial previsto en RF-006. Solo tiene valor cuando la captura (P1) es fiable.

**Independent Test**: Cargar un conjunto con valores esperados calculados a mano. Debe incluir hitos «No aplica» y «Sin dato», episodios abiertos, reaperturas, tickets del chatbot, tickets cerrados por error y casos fuera de referencia. Cotejar cada promedio, denominador, porcentaje y conteo del tablero, del PDF y del Excel con el mismo corte.

**Acceptance Scenarios**:

1. **Given** episodios con T1 registrado y otros con T1 «Sin dato», **When** se calcula el promedio de T1−T0, **Then** solo cuentan los episodios con T0 y T1 registrados. Se muestra el denominador («n = 18 de 20 episodios») y los faltantes y no aplicables se cuentan aparte.
2. **Given** un ticket reabierto una vez, **When** se consulta el tablero, **Then** cuenta como 1 ticket único y 2 episodios, y los tiempos se calculan por episodio.
3. **Given** episodios con referencia de validación de cinco minutos, **When** se consulta «% validaciones ≤ 5 min», **Then** el porcentaje usa como denominador solo los episodios de tipos con esa referencia y con T1 registrado. Los tickets del chatbot no entran en el cálculo.
4. **Given** un episodio de «Falla de conector» cuyo T3−T0 supera las 3 horas, **When** se consulta, **Then** aparece como «fuera de referencia» con su justificación, si existe. El episodio no se presenta como incumplimiento de un SLA.
5. **Given** los mismos filtros y el mismo corte, **When** se descargan el PDF y el Excel, **Then** sus cifras coinciden con el tablero. El PDF lleva logos, resumen ejecutivo, indicadores, gráficos, notas de metodología y fecha de corte. El Excel contiene el detalle por ticket, por episodio y la bitácora.
6. **Given** un Gerente, **When** consulta un ticket desde el tablero, **Then** ve el estado actual, el tiempo transcurrido, el último seguimiento y la cronología, sin controles de edición.
7. **Given** un cambio confirmado por un analista, **When** otro usuario autorizado tiene abierto el tablero, **Then** el cambio aparece sin recargar la página, dentro del intervalo de actualización automática de los demás módulos, con la hora de última actualización visible.

---

### Edge Cases

- **Electrolinera con todos sus conectores afectados**: si se eligen todos, el ticket se presenta igual que uno de equipo completo, pero conserva la selección explícita. No se crea un ticket por conector.
- **Falla común a varios equipos del sitio** (por ejemplo, la caída del enlace en un centro comercial con dos puntos): se crea un ticket por equipo y se relacionan entre sí (FR-003a). El informe «Control de tickets» muestra una fila por ticket.
- **Conector retirado o equipo reemplazado después de abrir el ticket**: el ticket conserva la referencia al conector por posición (D06) y al equipo original (D07). Los reportes nuevos muestran el nombre actual (D12) y el detalle conserva la etiqueta registrada en la fecha del hecho.
- **Equipo inactivo**: no se puede elegir para un ticket nuevo. Sus tickets anteriores se conservan y se consultan normalmente.
- **Ticket de un equipo que luego cambia de red**: el ticket se agrupa por la red conservada en su T0 (FR-007). Así, mover un equipo no altera los reportes.
- **Barrera en un sitio sin equipos en el inventario**: se registra con un nombre de sitio y una ubicación escritos a mano, y un enlace de Maps opcional. El ticket no crea ningún equipo y se cuenta en «Sin red».
- **Equipo de referencia que luego se desactiva o cambia de nombre**: el ticket del elemento del sitio conserva la ubicación tomada en su T0. Los reportes nuevos muestran el nombre de sitio actual.
- **Hito exactamente en T0** (por ejemplo, una validación instantánea): se admite. La diferencia es 0 min y sí cuenta como dato. Solo un hito sin hora deja de contar como cero.
- **Hitos fuera de orden** (por ejemplo, T2 anterior a T1): se admiten con advertencia y justificación obligatoria. T3 y T4 nunca pueden ser anteriores a T0, y T4 no puede ser anterior a ningún hito registrado del episodio.
- **Hora futura**: se rechaza para cualquier hito o comunicación.
- **Medianoche y cambio de mes**: los hitos se guardan con fecha y hora completas. Los períodos usan el día operativo de Ecuador, con inicio inclusivo y fin exclusivo, igual que los demás módulos.
- **Primera numeración sin configurar**: en producción no se puede crear el primer ticket hasta configurar el número inicial (FR-012). El sistema no usa ningún número por defecto.
- **Ticket mencionado en WhatsApp con un número anterior al inicial**: pertenece a la secuencia externa previa. Al buscarlo, SARC responde que ese ticket no está registrado en el sistema.
- **Edición simultánea del mismo ticket**: el segundo guardado recibe un aviso de conflicto y debe recargar. No se sobrescribe en silencio.
- **Doble clic o reenvío al crear**: produce un solo ticket.
- **Evidencia de formato no admitido**: se rechaza (videos, ejecutables y otros). Solo se aceptan imágenes y PDF (RNF-DAT-01).
- **Capturas de WhatsApp con datos personales de terceros**: la pantalla de evidencia recuerda minimizar los datos personales (RF-006, §10.5).
- **Reapertura de un ticket cerrado como «Registrado por error»**: no se permite reabrirlo. Se crea un ticket nuevo.
- **Ticket abierto durante meses**: aparece en el tablero con su antigüedad. Su episodio abierto no entra en los promedios de T4−T0.
- **Rango personalizado mayor de 366 días**: se rechaza, igual que en Consumo. En «Control de tickets», la opción «Todo el historial» no tiene ese límite.
- **Informe sin tickets en el período**: se genera con el encabezado y el mensaje «Sin tickets en el período». No da error.
- **Cierre exactamente en el límite del período**: un ticket con T4 igual al fin exclusivo del rango (00:00 del día siguiente al último) sigue Abierto al final del período (FR-074).
- **Corrección de T4 después de emitir un informe**: el informe ya emitido conserva sus filas y su huella. Un informe nuevo del mismo período refleja el T4 corregido (FR-074a).
- **Período cuyo fin aún no llega** (por ejemplo, «Este mes»): el estado se calcula al momento del corte.

## Requirements *(mandatory)*

### Functional Requirements

#### Alcance, elemento afectado y origen

- **FR-001** [Confirmado, P-10]: El sistema DEBE admitir incidencias de cuatro clases de elemento afectado:
  - un **equipo** del inventario, sea punto de carga o electrolinera;
  - **uno o varios conectores** de un mismo equipo;
  - el **servicio de chatbot**, sin equipo;
  - un **elemento del sitio** que no es cargador: «Barrera vehicular» u «Otro elemento del sitio».
- **FR-001a** [Confirmado, P-10]: Un ticket de elemento del sitio DEBE registrar:
  - la clase del elemento;
  - el nombre del elemento, obligatorio para «Otro elemento del sitio» y opcional para la barrera, para distinguir barreras del mismo sitio;
  - la ubicación, de una de estas dos formas:
    - un **equipo de referencia** activo del inventario del mismo sitio, del que se toman solo el nombre del sitio, la ubicación y el enlace de Maps;
    - si el sitio no tiene equipos en el inventario, un nombre de sitio y una ubicación escritos a mano, con un enlace de Maps opcional.

  El ticket NO DEBE heredar conectores ni red del equipo de referencia, y NO DEBE crear un equipo en el inventario. En los repartos por red se cuenta en el grupo «Sin red». Para mostrarlo se usa la forma «Sitio / Elemento» (por ejemplo, «CC El Bosque / Barrera vehicular»).
- **FR-002** [Confirmado]: Un ticket de equipo sin conectores elegidos DEBE significar que la afectación es del equipo completo. Una falla general de una electrolinera de dos conectores DEBE registrarse como un solo ticket.
- **FR-003** [Confirmado, PRD §10.5]: Cuando la afectación corresponde a conectores concretos, el analista DEBE poder elegir uno o varios conectores activos del mismo equipo en el mismo ticket. El sistema NO DEBE crear un ticket por conector.
- **FR-003a** [Aclarado Q5]: Un ticket DEBE referirse a un solo elemento afectado: un equipo, el servicio de chatbot o un elemento del sitio. Cuando una falla común (enlace de internet, energía del sitio) afecta a varios equipos, se crea un ticket por equipo y se relacionan entre sí con FR-017. Cada uno conserva sus propios hitos y aparece en su propia fila de los informes.
- **FR-004** [Confirmado]: Cada ticket DEBE registrar el **origen del aviso** por separado del elemento afectado. Valores: Plataforma de monitoreo, Chatbot, Personal en sitio o seguridad, Cliente, Técnico o proveedor, Otro con detalle. Que el aviso llegue por chatbot NO DEBE convertir la incidencia en una falla del chatbot.
- **FR-005** [Confirmado]: Una incidencia del servicio de chatbot NO DEBE exigir equipo, conector, ubicación ni enlace de Maps, y NO DEBE asociarse a un cargador ficticio. Una incidencia de elemento del sitio NO DEBE exigir conectores ni red, y su equipo de referencia sirve solo para ubicarla (FR-001a).
- **FR-006** [PRD RF-006.2]: Para un ticket de equipo, el sistema DEBE tomar del inventario vigente la red, el nombre, el tipo de infraestructura, el código, la ubicación, el enlace de Google Maps y los conectores. Los datos que falten en el inventario quedan vacíos y no bloquean el registro. Solo pueden elegirse equipos activos.
- **FR-007** [PRD §13, Constitución D06, D07 y D12; P-14]: Cada ticket DEBE referirse al equipo, a los conectores y al equipo de referencia por su identidad interna. Además DEBE conservar, tal como estaban en T0 del primer episodio, la etiqueta del equipo y la **red** del equipo. Los reportes nuevos usan el nombre actual (D12) y agrupan por la red conservada, de modo que mover después el equipo a otra red no cambia sus tickets. Los tickets del chatbot y de elementos del sitio se agrupan en «Sin red».

#### Creación y numeración

- **FR-010** [Confirmado, PRD RF-006.1]: Crear un ticket de equipo DEBE requerir solo tres datos: equipo, tipo de incidencia y descripción breve. El origen del aviso DEBE proponerse como «Plataforma de monitoreo» y ser modificable. La evidencia inicial, los conectores y la prioridad son opcionales al crear.
- **FR-011** [Confirmado, aclarado Q2]: Crear un ticket del servicio de chatbot DEBE requerir el tipo «Falla del servicio de chatbot», la descripción breve, el origen y T0, que se propone igual que en FR-018. El alcance de la falla, total o parcial, es opcional. NO DEBE pedirse ningún otro dato obligatorio.
- **FR-012** [Confirmado]: Cada ticket DEBE recibir al crearse un identificador único e inmutable con el formato `TICKET-` seguido del número consecutivo, con al menos tres dígitos y ceros a la izquierda (`TICKET-086`, `TICKET-999`, `TICKET-1000`).
- **FR-013** [Confirmado]: El número inicial DEBE configurarse antes de crear el primer ticket de producción. El sistema NO DEBE usar un número de producción por defecto ni suponer que será 087. Mientras no esté configurado, en producción no se pueden crear tickets y se muestra la causa. Una vez creado el primer ticket, el número inicial no puede cambiarse.
- **FR-014** [Confirmado, Pendiente P-01]: El número inicial definitivo DEBE consultarse al responsable del producto justo antes de la puesta en marcha, porque la secuencia externa avanza durante el desarrollo. Los ambientes de prueba usan valores de prueba que no se trasladan a producción.
- **FR-015** [Confirmado]: Un número asignado NUNCA DEBE reasignarse, ni siquiera si el ticket se cerró por error. Las creaciones simultáneas y los reenvíos NO DEBEN producir números duplicados ni dos tickets por la misma acción.
- **FR-016** [PRD §10.2]: Al crear un ticket para un equipo, para el servicio de chatbot o para un elemento del sitio que ya tiene un ticket Abierto (un elemento del sitio coincide si comparten clase, sitio y nombre), el sistema DEBE mostrar los tickets abiertos y ofrecer agregar el seguimiento a uno de ellos. Para crear el nuevo de todos modos hay que indicar un motivo, que se audita.
- **FR-017** [PRD RF-006.13, aclarado Q5]: Un ticket DEBE poder relacionarse con uno o varios tickets en dos casos: una falla independiente sobre un equipo con tickets anteriores, o los tickets de otros equipos afectados por la misma falla común del sitio (FR-003a). La relación se muestra en ambos sentidos y no fusiona hitos ni conteos.
- **FR-018** [PRD RF-006.1]: T0 DEBE proponerse al crear el ticket con la hora actual. El analista puede indicar la hora real de detección o comunicación. Si difiere de la propuesta en más de un minuto, debe justificarla, y el registro queda marcado como retroactivo.
- **FR-019** [PRD RF-006.5]: En una urgencia, el analista PUEDE publicar primero en WhatsApp y registrar después el ticket con las horas reales justificadas. SARC NO DEBE fingir que publicó nada.

#### Catálogo de tipos de incidencia

- **FR-020** [Protocolo §1.2, §5, §7–§9 y §12.1; Propuesta]: El catálogo inicial de tipos DEBE incluir:
  - Espera de conexión;
  - Desconexión;
  - Falla de conector;
  - Paro de emergencia;
  - Conector retenido en el vehículo;
  - No permite detener la carga;
  - Punto KIA con caída persistente;
  - Intermitencia;
  - Novedad de instalación o conectividad;
  - Falla del servicio de chatbot;
  - Falla de elemento del sitio;
  - Incidencia no catalogada.

  [P-11] El catálogo se mantiene con cambios controlados del sistema: una actualización de esta especificación y una versión publicada. No se administra desde la interfaz. Cada tipo declara para qué clases de elemento afectado sirve.
- **FR-021** [Protocolo §12.1]: «Incidencia no catalogada» DEBE admitirse como categoría temporal, sin referencias de tiempo, y DEBE poder reclasificarse después con justificación.
- **FR-022** [Propuesta, P-10]: Cada clase de elemento afectado admite solo sus tipos:
  - el **servicio de chatbot** admite «Falla del servicio de chatbot» e «Incidencia no catalogada»;
  - un **elemento del sitio** admite «Falla de elemento del sitio» e «Incidencia no catalogada»;
  - **equipos y conectores** admiten los demás tipos e «Incidencia no catalogada».

  Ningún tipo de equipo DEBE ofrecerse para el chatbot ni para un elemento del sitio, ni al revés.
- **FR-023** [PRD RF-005; Propuesta]: Un ticket de tipo «Intermitencia» registra la gestión operativa del caso. NO DEBE sustituir ni duplicar las ventanas, el tiempo fuera de línea ni la disponibilidad estimada del módulo de Intermitencias (Feature 008).

#### Estados, eventos de seguimiento y cronología

- **FR-030** [Confirmado]: Los únicos estados principales del ticket DEBEN ser **Abierto** y **Cerrado**. Ningún hito ni evento DEBE añadir un estado principal.
- **FR-031** [Confirmado, PRD RF-006.10 y RF-006.14]: La cronología DEBE registrar como eventos fechados, cada uno con su autor:
  - hitos T1–T4;
  - validaciones adicionales;
  - acciones correctivas;
  - escalamientos (rol o proveedor, solicitud y compromiso informado);
  - observaciones;
  - cambios de prioridad;
  - asignación de responsable;
  - relevos de turno;
  - visitas;
  - comunicaciones;
  - evidencias;
  - reclasificaciones;
  - cierre y reapertura.
- **FR-032** [PRD RF-006.10]: Un escalamiento posterior a T2 DEBE quedar como evento independiente y NO DEBE redefinir T2.
- **FR-033** [PRD §10.5, Protocolo §6.1]: El relevo de turno DEBE registrar el responsable saliente, el entrante, el estado actual, los pendientes, la próxima verificación y el criterio previsto de cierre. Cambia el responsable actual y NO DEBE crear un ticket nuevo.
- **FR-034** [PRD RF-006.11]: La prioridad DEBE tomar uno de tres valores: Media, Alta o Crítica. [P-05] Se propone según el tipo, con la lectura de la tabla del protocolo:

  | Tipo | Prioridad propuesta |
  |------|---------------------|
  | Espera de conexión | Media |
  | Desconexión | Alta |
  | Falla de conector | Alta |
  | Conector retenido en el vehículo | Crítica |
  | No permite detener la carga | Crítica |
  | Intermitencia | Media |
  | Punto KIA con caída persistente | Media |
  | Paro de emergencia, novedad de instalación o conectividad, falla del servicio de chatbot, falla de elemento del sitio, incidencia no catalogada | Media, sin respaldo del protocolo |

  La propuesta va con la versión de referencias (FR-051a). El analista la cambia con motivo, por ejemplo por el puerto 9081, por ser una electrolinera, por recurrencia o por un usuario afectado. El cambio queda en la cronología. «Crítico» en el tablero significa prioridad Crítica.
- **FR-035** [Protocolo §9.4 y Anexo B; P-08]: Una visita técnica DEBE registrarse como un evento con estas etapas:
  - solicitada;
  - proveedor asignado (nombre del proveedor, sin cuenta en SARC);
  - confirmada por el proveedor;
  - cancelada o aplazada, con quién decidió, la evidencia y la condición de nueva escalada;
  - ejecutada;
  - informe de ejecución recibido.

  El proveedor se escribe a mano, con sugerencias de los nombres ya usados. SARC NO DEBE asignar proveedores automáticamente, validar la regla de asignación del Anexo B ni comunicarse con ellos.
- **FR-036** [PRD RF-006.10, CA-006-02]: Toda corrección de un hito, evento, clasificación o dato del ticket DEBE exigir justificación. Debe conservar el valor anterior, el nuevo, el autor y el momento. Ningún evento se borra: un evento erróneo se anula con motivo y sigue visible en la auditoría. Los datos corregibles del ticket son la descripción, el origen del aviso, el tipo (reclasificación) y la prioridad. El elemento afectado NO se corrige: si se eligió mal, el ticket se cierra como «Registrado por error» y se crea otro.
- **FR-037** [PRD RNF-DAT-01 y §10.5]: Cualquier evento DEBE admitir evidencias en formato de imagen o PDF, con su autor y momento. Se rechazan videos, ejecutables y otros formatos.
- **FR-038** [PRD RNF-SEG-07]: Las vistas, mensajes y reportes de tickets NO DEBEN mostrar UID/PUK, URL OCPP ni otra información operativa restringida.

#### Hitos T0–T4 y episodios

- **FR-040** [Confirmado, PRD §10.2, Protocolo §5.1]: Cada episodio DEBE tener estos hitos:
  - **T0**: momento en que la plataforma, el chatbot, el personal o el cliente informa la novedad;
  - **T1**: primera validación confirmada. Se recomienda adjuntar la primera evidencia, pero no es obligatoria para registrar T1;
  - **T2**: primera solicitud de apoyo o primera acción correctiva;
  - **T3**: recuperación de la comunicación o de la funcionalidad;
  - **T4**: cierre, después de comprobar la estabilidad y completar el registro.
- **FR-041** [Confirmado, PRD RF-006.7]: T1–T3 DEBEN registrarse con una acción simple que capture el usuario y la hora actual. T4 se registra dentro del cierre, que lo propone con la hora actual (FR-047). Para cualquier hito T1–T4 DEBE poder indicarse una hora anterior, con justificación, que queda marcada como retroactiva.
- **FR-042** [Confirmado, PRD §10.5]: Cada hito T1–T3 DEBE estar en uno de cuatro estados:
  - **Pendiente**, solo en un episodio abierto;
  - **Registrado**, con hora;
  - **No aplica**, con motivo;
  - **Sin dato**, con motivo, cuando la hora real se desconoce.

  El sistema NUNCA DEBE inventar una hora ni convertir un hito sin hora en cero.
- **FR-043** [Propuesta]: Reglas de orden:
  - ningún hito puede ser anterior a T0 ni posterior al momento del registro;
  - T4 no puede ser anterior a ningún hito registrado del episodio;
  - si T1, T2 y T3 no siguen ese orden, se admiten con advertencia y justificación.
- **FR-044** [PRD §10.5, CA-006-03]: Reabrir un ticket DEBE crear un nuevo episodio con su propio T0, igual a la hora de la nueva detección, y sus propios T1–T4. Los episodios anteriores DEBEN quedar intactos.
- **FR-045** [Confirmado, PRD RF-006.16; P-06]: Por cada episodio, el sistema DEBE calcular por separado:
  - primera validación (T1−T0);
  - primera acción (T2−T0);
  - **recuperación** (T3−T0);
  - **cierre** (T4−T0);
  - recuperación a cierre (T4−T3).

  Cada diferencia se calcula solo cuando ambos hitos están registrados. Recuperación y cierre son indicadores distintos y no se sustituyen el uno al otro.
- **FR-046** [PRD RF-006.17]: Los tiempos de un episodio abierto se DEBEN mostrar como tiempo transcurrido desde T0, aparte, y NO DEBEN entrar en los promedios de hitos que todavía no existen.

#### Cierre y reapertura

- **FR-047** [PRD RF-006.12, Protocolo §7.7 y §11.3; Propuesta]: Solo un Administrador o un Analista puede cerrar. Para cerrar un ticket de equipo o de conectores hacen falta:
  - T4, propuesto con la hora actual;
  - T1–T3 en estado Registrado, No aplica o Sin dato;
  - el **resultado de cierre**: Recuperado, Sin falla confirmada, Duplicado de otro ticket o Registrado por error;
  - la **causa**: confirmada, probable o no determinada. Su descripción es obligatoria salvo si no está determinada. Una causa probable exige al menos una evidencia del episodio que la respalde;
  - recomendaciones o pendientes, en texto opcional;
  - una evidencia final o una justificación de su ausencia;
  - la comunicación de cierre registrada, o una justificación;
  - la confirmación por el analista de los criterios del protocolo que aplican a los equipos: estado normal en la plataforma principal; estabilidad comprobada cuando aplique; ningún usuario afectado pendiente ni sesión sin control; prueba posterior realizada o no aplicable.
- **FR-048** [Confirmado, aclarado Q2]: El cierre de un ticket del servicio de chatbot NO DEBE exigir las confirmaciones de equipos físicos. Exige:
  - el restablecimiento del servicio verificado (T3 registrado, o «No aplica» o «Sin dato» con motivo);
  - T1 y T2 en estado Registrado, No aplica o Sin dato;
  - T4;
  - el resultado de cierre;
  - la causa, con las reglas de FR-047 (descripción salvo si no está determinada; evidencia si es probable);
  - una evidencia final o la justificación de su ausencia;
  - la comunicación de cierre registrada, o una justificación.
- **FR-048a** [P-10; Propuesta]: El cierre de un ticket de elemento del sitio sigue FR-048: no exige las confirmaciones de cargadores. Exige verificar que el elemento quedó operativo (T3, o «No aplica» o «Sin dato» con motivo), T1 y T2 en un estado explícito, T4, el resultado, la causa con las reglas de FR-047, una evidencia o su justificación, y la comunicación o su justificación.
- **FR-049** [PRD RF-006.13]: Solo un Administrador o un Analista puede reabrir un ticket Cerrado, cuando el caso continúa. Hacen falta el motivo y la hora de nueva detección. El ticket conserva su número. Un ticket cerrado como «Registrado por error» o «Duplicado» NO DEBE reabrirse.
- **FR-050** [Propuesta]: Los tickets cerrados como «Registrado por error» o «Duplicado» DEBEN quedar fuera de los indicadores de tiempo y de los conteos por tipo. Se informan en un apartado separado y siguen apareciendo en «Control de tickets» con estado Cerrado.

#### Referencias de tiempo (no SLA)

- **FR-051** [PRD §10.3, Protocolo §5; P-03, P-04]: El sistema DEBE asociar a cada tipo las referencias del protocolo que tienen sustento. DEBE rotularlas como «referencia operativa, no SLA contractual». La versión inicial es esta:

  | Tipo | Validación inicial (T1−T0) | Atención o recuperación referencial |
  |------|----------------------------|-------------------------------------|
  | Espera de conexión | ≤ 5 min | Escalamiento a técnicos ≤ 60 min de espera desde T0 |
  | Desconexión | ≤ 5 min | T3−T0 entre 5 min y 5 h, ampliable con justificación |
  | Falla de conector | ≤ 5 min | T3−T0 entre 5 min y 3 h |
  | Conector retenido en el vehículo | ≤ 5 min | T3−T0 entre 5 min y 2 h |
  | No permite detener la carga | Atención inmediata, ≤ 5 min | Sin máximo aprobado: no se marca desviación de recuperación |
  | Punto KIA con caída persistente | Vigilancia desde la detección | Se clasifica como caída persistente cuando lleva **más de 30 min** fuera de línea (P-04), con la duración exacta y sin redondear. Es una regla de clasificación, no un límite de recuperación |
  | Intermitencia | Registrar al detectar el ciclo | Consolidado al final del día (módulo de Intermitencias) |
  | Paro de emergencia, novedad de instalación o conectividad, falla de elemento del sitio, incidencia no catalogada | Sin referencia aprobada | Sin referencia aprobada |
  | Falla del servicio de chatbot | Sin referencia aprobada (no se aplican los 5 min de las consultas de chatbot) | Sin referencia aprobada |

- **FR-051a** [P-03, P-02]: Las referencias y prioridades propuestas DEBEN conservarse en **versiones** inmutables, cada una con su fecha de vigencia y su fuente. Cada episodio guarda la versión vigente en su T0, y sus juicios de referencia se calculan siempre con esa versión. Un cambio de referencias crea una versión nueva mediante un cambio controlado del sistema, sin modificar episodios ni reportes anteriores. La versión inicial se rotuló «Protocolo de monitoreo firmado (versión por confirmar)» hasta resolver P-02. El 2026-11-23 el responsable confirmó la versión 2.0 y la migración V022 cambió solo ese rótulo, a «Protocolo de monitoreo firmado, versión 2.0», sin tocar los valores.
- **FR-052** [Propuesta, P-06]: La medida que se compara con cada referencia es la de la tabla: la atención referencial se compara con la recuperación (T3−T0), nunca con el cierre (T4−T0). Un episodio es «fuera de referencia» cuando:
  - su diferencia registrada supera el límite superior;
  - o, en Espera de conexión, la espera supera 60 min sin un escalamiento con destino técnico (técnicos propios o del proveedor) hecho dentro de esos 60 min. La espera termina en T3 registrado o, si el episodio sigue abierto, en el momento del corte. Un escalamiento técnico posterior al fin de la espera no cuenta: si la espera terminó en 60 min o menos, el episodio está dentro de la referencia. Si el episodio está cerrado sin T3 registrado y sin escalamiento técnico, es «no evaluable».

  Terminar antes del límite inferior NO DEBE considerarse desviación.
- **FR-053** [PRD §10.3]: Cada desviación DEBE admitir una justificación, que se muestra en el detalle y en los reportes. Un episodio sin el hito necesario para comparar se informa como «no evaluable», nunca como «dentro de referencia».
- **FR-054** [Confirmado]: El sistema NO DEBE crear umbrales, reglas de cálculo ni tiempos que no estén en esta tabla. Para los tipos sin referencia, el tiempo se muestra sin juicio de cumplimiento.

#### Mensaje para WhatsApp y comunicaciones

- **FR-055** [Confirmado]: El sistema DEBE preparar un texto para copiar y pegar a mano. SARC NO DEBE enviar, publicar ni leer WhatsApp.
- **FR-056** [Confirmado]: El texto DEBE incluir:
  - el número de ticket;
  - el nombre actual del cargador o electrolinera, con los conectores cuando aplique;
  - la ubicación;
  - el enlace de Google Maps del inventario;
  - la novedad, tomada de la descripción breve y editable;
  - el estado, Abierto o Cerrado.

  Por defecto NO DEBE incluir la acción ni la hora de detección como atributos separados.
- **FR-056a** [Aclarado Q3, Protocolo §4.1]: El mensaje DEBE ofrecer dos opciones, desactivadas cada vez que se abre:
  - «Incluir hora de detección (T0)» añade T0 del episodio actual;
  - «Incluir acciones realizadas» añade las acciones correctivas y los escalamientos registrados en el episodio actual, cada uno con su hora y sin UID/PUK ni datos restringidos.

  Activarlas solo cambia el texto y no altera el ticket. SARC NO DEBE afirmar que el mensaje cumple todo el protocolo §4.1. Los demás elementos del aviso del protocolo los añade el analista editando el texto, si corresponde: fuente de validación, solicitud concreta y próxima verificación.
- **FR-057** [Confirmado]: Si falta el enlace de Maps, su campo DEBE quedar vacío. Copiar el mensaje no se bloquea ni produce error.
- **FR-058** [Confirmado]: El analista DEBE poder editar el texto antes de copiarlo. La edición no modifica los datos del ticket.
- **FR-059** [Confirmado, PRD RF-006.5, CA-006-04]: Copiar el texto NO DEBE registrar ninguna comunicación. «Registrar comunicación» guarda:
  - el canal: RedAE/Inpsercom, KIA Electrolineras, Cargadores eléctricos, Autoeastern/Enerlink, Grupo de directivos, Correo u Otro con detalle;
  - el tipo: inicial, actualización o cierre;
  - la fecha y hora real del envío, con justificación si es retroactiva;
  - la evidencia, opcional;
  - el autor.
- **FR-060** [Propuesta]: Para un ticket del servicio de chatbot, el texto DEBE sustituir el nombre del cargador por «Servicio de chatbot» y omitir la ubicación y el enlace de Maps. Para un elemento del sitio, el texto DEBE mostrar «Sitio / Elemento», la ubicación y el enlace de Maps conservados (FR-001a). Si no hay enlace, esa línea queda vacía sin bloquear la copia.

#### Tablero y vistas de tickets

- **FR-061** [PRD RF-006.15]: El listado de tickets DEBE permitir buscar, filtrar y paginar por los campos siguientes, y ordenar por todos ellos salvo el elemento afectado, que solo se busca y se filtra:
  - número;
  - estado;
  - tipo;
  - elemento afectado;
  - red;
  - prioridad;
  - responsable actual;
  - antigüedad;
  - última actualización.
- **FR-062** [Confirmado, PRD RF-006.15]: Sin agregar estados, el tablero DEBE mostrar:
  - abiertos;
  - cerrados;
  - críticos;
  - reabiertos;
  - abiertos pendientes de validación (sin T1);
  - abiertos en atención (con T2 y sin T3);
  - abiertos recuperados en observación (con T3);
  - antigüedad de los abiertos;
  - responsable;
  - última actualización.
- **FR-063** [PRD RF-006.20, RNF-NOT-04 y RNF-NOT-05]: El tablero, el listado y el detalle DEBEN reflejar los cambios confirmados sin recarga manual, con el mismo mecanismo de actualización automática de los demás módulos. Deben mostrar la hora de la última actualización y advertir de una pérdida de conectividad. NO DEBEN afirmar que WhatsApp ni ninguna plataforma externa se sincroniza.
- **FR-064** [PRD RNF-UX-01 a RNF-UX-05; Armonía UI]: Las pantallas DEBEN funcionar en computadora, tablet y celular. Deben seguir los patrones visuales de Consumo e Inventario y tener mensajes de estado que no dependan solo del color.

#### Informe «Control de tickets»

- **FR-070** [Confirmado]: El informe DEBE tener la presentación corporativa descrita en «Referencia visual»:
  - logo de EOS Tech Company a la izquierda y de Autoeastern a la derecha;
  - título «CONTROL DE TICKETS»;
  - subtítulo «Electromovilidad | Puntos de carga y electrolineras»;
  - banda «REGISTRO OPERATIVO»;
  - columnas «NÚMERO DE TICKET», «CARGADOR O ELECTROLINERA» y «ESTADO DEL TICKET».
- **FR-071** [Confirmado]: El informe DEBE incluir los tickets abiertos y cerrados, sin limitarse a los abiertos ni a los cerrados en una semana. Se ordena por número ascendente.
- **FR-072** [Confirmado]: Puede generarse en cualquier momento. DEBE ofrecer los períodos de los demás módulos (Hoy, Últimos 7 días, Este mes, Mes anterior, Este año y rango personalizado de hasta 366 días), más «Todo el historial». La opción inicial es «Todo el historial». Un día concreto se elige con un rango personalizado de un día.
- **FR-073** [Aclarado Q1]: Con un filtro de período, un ticket DEBE entrar en el informe si estuvo abierto en algún momento del rango. Eso ocurre cuando alguno de sus episodios tiene T0 anterior al fin del rango y además sigue abierto o tiene T4 igual o posterior al inicio del rango. El inicio del rango es inclusivo y el fin exclusivo. Así, el informe semanal muestra los tickets que siguen abiertos desde semanas anteriores y los cerrados durante la semana. Un ticket reabierto aparece una sola vez.
- **FR-074** [P-12]: El estado mostrado DEBE ser el que tenía el ticket **al final del período**, no su estado actual. El ticket está **Abierto** al final del período si alguno de sus episodios tiene T0 anterior al fin del rango (exclusivo) y, además, no tiene T4 o su T4 es igual o posterior a ese fin. En otro caso está **Cerrado**. Si el fin del rango es posterior al momento del corte, se usa el corte. Sin filtro de período («Todo el historial»), se muestra el estado al momento del corte. Un ticket reabierto aparece una sola vez, con el estado del episodio que estaba vigente al final.
- **FR-074a** [P-12]: El estado al final del período se calcula con los hitos vigentes al momento del corte, ya corregidos. Una corrección posterior de T0 o T4 cambia los informes que se generen después, pero NO DEBE alterar los informes ya emitidos. Cada informe emitido conserva su registro de emisión y la huella de sus filas (FR-086), para comprobar qué mostró.
- **FR-075** [Confirmado; Propuesta]: La segunda columna DEBE mostrar:
  - el nombre actual del equipo para un equipo completo;
  - el nombre y los conectores afectados para conectores concretos (por ejemplo, «Electrolinera X / Conectores 1 y 2»);
  - «Servicio de chatbot» para el servicio de chatbot;
  - «Sitio / Elemento» para un elemento del sitio (por ejemplo, «CC El Bosque / Barrera vehicular»), sin inventar ningún cargador.
- **FR-076** [PRD RF-RPT-01]: El informe DEBE indicar el período exacto o «Todo el historial», los filtros activos, la zona horaria y la fecha y hora de corte. Debe incluir un pie con el total de tickets, los abiertos y los cerrados.
- **FR-077** [Confirmado, PRD RF-RPT-10]: Todos los roles autorizados DEBEN poder verlo en pantalla y descargarlo en PDF y en Excel. Las tres versiones tienen las mismas filas para el mismo corte y los mismos filtros. Opcionalmente puede filtrarse por estado y por red.

#### Reporte de gestión de incidencias

- **FR-080** [Confirmado, PRD RF-006.16 a RF-006.19]: El reporte de gestión DEBE ser distinto de «Control de tickets» y DEBE incluir:
  - **resumen**: tickets únicos, episodios, abiertos, cerrados, reabiertos, críticos y cerrados por error o duplicado (aparte);
  - **promedios** de primera validación (T1−T0), primera acción (T2−T0), recuperación (T3−T0), cierre (T4−T0) y recuperación a cierre (T4−T3), cada uno con su denominador y con los episodios «No aplica», «Sin dato» y abiertos contados por separado;
  - **porcentaje de validaciones ≤ 5 min**, sobre los episodios de tipos con esa referencia y T1 registrado;
  - **episodios fuera de referencia y no evaluables**, por tipo, con sus justificaciones;
  - **repartos** por tipo, red (conservada en T0, con «Sin red» para chatbot y elementos del sitio), clase de elemento afectado, equipo, prioridad y origen del aviso;
  - **visitas**: solicitadas, confirmadas, canceladas o aplazadas, y ejecutadas;
  - **reaperturas** y **equipos repetidores**, ordenados por número de episodios en el período y sin etiqueta de «recurrente» (no existe un umbral aprobado, Protocolo §13);
  - **detalle por ticket y episodio**: T0, T1, T2, T3 y T4 con su estado (registrado, retroactivo, no aplica, sin dato), los tiempos derivados, la referencia aplicable y la desviación.
- **FR-081** [PRD RF-006.18]: El tablero y el PDF DEBEN incluir gráficos por fecha, red, tipo, prioridad, visitas, reincidencias, tiempos excesivos y equipos repetidores, siempre que haya datos. Siguen los patrones visuales de los módulos existentes.
- **FR-082** [Protocolo §12.3; P-07]: Los gráficos DEBEN mostrarse sin ponderación, con su denominador. El sistema NO DEBE inventar pesos. Los «gráficos ponderados por incidencia» del protocolo solo se agregarán cuando el responsable defina y apruebe los pesos, con un cambio de especificación.
- **FR-083** [Protocolo §12.3 b y c; PRD RF-004 y RF-005; P-13]: La tendencia de interrupciones de al menos 15 días y la disponibilidad por cargador NO DEBEN recalcularse en este módulo. El reporte DEBE remitir a los módulos de Uptime (disponibilidad oficial validada) e Intermitencias (tiempo fuera de línea y disponibilidad estimada de prueba). Debe conservar sus definiciones y permisos y no presentar una estimación como disponibilidad validada. La meta del 98 % del protocolo no se muestra como umbral oficial mientras no se apruebe.
- **FR-084** [Propuesta]: En el reporte de gestión, los indicadores de tiempo usan los episodios cuyo T0 cae en el período, con inicio inclusivo y fin exclusivo. El resumen informa además los tickets abiertos a la fecha de corte. El período inicial es «Este mes» (D13) y se ofrecen los mismos accesos rápidos y el rango personalizado de hasta 366 días.
- **FR-085** [PRD RF-006.19, RF-RPT-03 y RF-RPT-04]:
  - El **PDF corporativo** DEBE incluir logos, resumen ejecutivo, indicadores, gráficos, tablas, hallazgos, prioridades, notas de metodología (definición de cada medida, denominadores y exclusiones), el período y la fecha de corte.
  - El **Excel** DEBE incluir resumen, tickets, episodios con T0–T4, bitácora de eventos, comunicaciones y visitas, para que cada cifra se compruebe con una suma o un conteo.
- **FR-086** [PRD CA-006-06, RF-RPT-08]: Con los mismos filtros y el mismo corte, las cifras del tablero, el PDF y el Excel DEBEN coincidir. El sistema DEBE poder reconstruir qué datos soportaban un informe generado. Cada descarga DEBE registrar su emisión con el tipo de informe, los filtros, el período, el corte, el autor, el número de filas y la huella del contenido, sin modificarla después.

#### Permisos y auditoría

- **FR-090** [Confirmado, PRD §4]: Administrador y Analista DEBEN poder crear, registrar hitos y seguimiento, corregir con justificación, registrar comunicaciones y visitas, cerrar y reabrir.
- **FR-091** [Confirmado, PRD §4, CA-006-05]: Gerente, Supervisor y Asistente DEBEN poder consultar los tickets, su cronología y sus evidencias, ver el tablero y descargar ambos reportes, sin ninguna operación de modificación.
- **FR-092** [Confirmado, PRD RNF-SEG-05]: Los permisos DEBEN comprobarse en cada operación y consulta, igual en la interfaz, en los reportes y en el acceso directo. Ocultar un botón no basta.
- **FR-093** [PRD §13]: Toda creación, cambio, corrección, cierre, reapertura, comunicación y descarga de reporte DEBE quedar auditada con su actor, la operación, el momento y los datos anteriores y nuevos.

#### Límites

- **FR-095** [Confirmado, PRD §2.4]: Esta feature NO DEBE incluir:
  - control remoto de cargadores;
  - integración automática con WhatsApp, el chatbot, Enerlink, Wallbox, Kibana ni otras plataformas;
  - comunicación automática con proveedores;
  - creación automática de tickets a partir de telemetría;
  - importación de los tickets históricos del Excel (P-16): SARC contiene solo los tickets creados en el sistema.

  Cualquiera de ellas requiere una autorización y un alcance expresos.
- **FR-096** [PRD RF-006.22 y RNF-NOT-01 a RNF-NOT-03; aclarado Q4]: La campana de notificaciones y las alertas visuales quedan fuera de esta feature y se especificarán en una feature transversal aparte, común a todos los módulos. Esta feature DEBE registrar en la cronología, con su momento, su autor y el enlace al ticket, los eventos que la alimentarán: ticket nuevo, cierre, reapertura, escalamiento y paso a prioridad Crítica. Hasta que exista esa feature, RF-006.22 y CA-006-07 (en lo que toca a la alerta visual) quedan pendientes. La actualización automática sin recarga (FR-063) sí está dentro del alcance.

### Trazabilidad documental

| Tema | Fuente | Tratamiento |
|------|--------|-------------|
| Creación en 3 campos | PRD RF-006.1, CA-006-01 | FR-010, US1 |
| Datos desde el inventario | PRD RF-006.2 | FR-006, FR-007 |
| Número inmutable | PRD RF-006.3; Confirmado | FR-012 a FR-015 |
| Mensajes normalizados | PRD RF-006.4; Confirmado (difiere); Protocolo §4.1 | FR-055 a FR-060, FR-056a |
| Confirmación de comunicación | PRD RF-006.5, CA-006-04 | FR-059 |
| WhatsApp primero en urgencia | PRD RF-006.6 | FR-019 |
| Hitos y corrección retroactiva | PRD RF-006.7, CA-006-02 | FR-040 a FR-046, FR-036 |
| Escalamiento como evento | PRD RF-006.8 | FR-032 |
| Estados principales | PRD RF-006.9; **Confirmado: sustituido** | FR-030, FR-062 |
| Visitas, espera, prioridad y turno como atributos | PRD RF-006.10 | FR-031, FR-033 a FR-035 |
| Cierre controlado | PRD RF-006.11; Protocolo §11.3 | FR-047, FR-048 |
| Reapertura y ticket relacionado | PRD RF-006.12 y RF-006.13, CA-006-03, CA-006-08 | FR-044, FR-049, FR-017 |
| Técnicos sin cuenta | PRD RF-006.14 | FR-035 |
| Tablero | PRD RF-006.15 | FR-061 a FR-063 |
| Indicadores de tiempo y denominadores | PRD RF-006.16 y RF-006.17 | FR-045, FR-046, FR-080 |
| Gráficos | PRD RF-006.18 | FR-081, FR-082 |
| Permisos de consulta | PRD RF-006.19, CA-006-05 | FR-090 a FR-092 |
| PDF y Excel | PRD RF-006.19, CA-006-06 | FR-085, FR-086 |
| Actualización automática | PRD RF-006.20, CA-006-07 | FR-063 |
| Campana | PRD RF-006.22 | FR-096: feature transversal aparte (Q4) |
| Referencias de tiempo | PRD §10.3; Protocolo §5 | FR-051 a FR-054 |
| Control de tickets | Confirmado; P-12 | FR-070 a FR-077, FR-074a, US6 |
| Incidencias del chatbot | Confirmado; Protocolo §1.3 | FR-001, FR-004, FR-005, FR-011, FR-048 |
| Barreras y otros elementos del sitio | Confirmado (P-10) | FR-001, FR-001a, FR-005, FR-022, FR-048a, FR-060, FR-075, US3 |
| Referencias versionadas | Confirmado (P-03) | FR-051a |
| Red histórica | Confirmado (P-14) | FR-007 |
| Histórico del Excel | Confirmado (P-16) | FR-095 |

### Conformidad constitucional

- **Principio I**: esta especificación precede a cualquier cambio. No se implementa nada en esta solicitud.
- **Principio II**: todas las reglas de numeración, hitos, cierre, referencias y reportes están descritas aquí. P-01 y P-02 están resueltas (sección Decisiones), sin una regla implícita para ninguna de ellas.
- **Principio III**: no aplica en esta fase. El esquema se tratará en el plan con migraciones nuevas.
- **D06 y D07**: los tickets referencian conectores por posición y conservan el equipo original ante un reemplazo.
- **D09**: no hay importación de Excel en esta feature.
- **D12**: los reportes nuevos usan los nombres actuales, y el detalle conserva la etiqueta de la fecha del hecho.
- **D13**: el período inicial del tablero de gestión es el mes en curso. «Control de tickets» abre en «Todo el historial» por decisión confirmada; es un informe operativo, no el tablero de consumo al que se refiere D13.
- **Cambios al PRD**: los cuatro cambios de la sección correspondiente quedan registrados en el control de cambios (CC-PRD-010). No se modifica el PDF maestro.

### Key Entities *(include if feature involves data)*

- **Ticket**: caso único con número inmutable `TICKET-NNN`, estado (Abierto o Cerrado), elemento afectado, tipo, origen del aviso, descripción, prioridad, responsable actual, relaciones con otros tickets y episodios.
- **Elemento afectado**: equipo completo, conectores concretos de un equipo, servicio de chatbot o elemento del sitio (clase, nombre, equipo de referencia o sitio escrito a mano). Del equipo conserva su identidad, su etiqueta y su red en T0.
- **Episodio**: ciclo de atención dentro de un ticket. El primero se abre al crear el ticket y cada reapertura abre otro. Contiene T0–T4, el resultado y la causa de cierre.
- **Hito**: T0 a T4 de un episodio, con su estado (pendiente, registrado, no aplica, sin dato), hora, motivo, autor y marca de retroactivo.
- **Evento de seguimiento**: entrada fechada de la cronología, como validación, acción, escalamiento, observación, cambio de prioridad, asignación, relevo de turno, reclasificación, cierre o reapertura. Tiene autor, puede tener evidencias y se anula sin borrarse.
- **Comunicación**: registro manual de un mensaje publicado, con su canal, tipo, hora real, evidencia y autor. No equivale a un mensaje copiado.
- **Visita técnica**: evento con etapas (solicitada, asignada, confirmada, cancelada o aplazada, ejecutada, informe recibido) y el nombre del proveedor.
- **Evidencia**: imagen o PDF vinculado a un evento, con autor y momento.
- **Tipo de incidencia**: catálogo con las clases de elemento que admite y su prioridad propuesta.
- **Versión de referencias**: conjunto fechado e inmutable de referencias de tiempo por tipo. Cada episodio guarda la versión vigente en su T0.
- **Configuración de numeración**: número inicial, fijado una sola vez antes del primer ticket de producción.
- **Corrección**: registro de auditoría con el valor anterior, el nuevo, la justificación, el autor y el momento.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un analista registra un ticket de equipo en menos de 1 minuto, con solo tres datos introducidos por él. Los datos de inventario aparecen sin escribirse.
- **SC-002**: Registrar cada hito T1–T3 a la hora actual toma una sola acción. T4 se propone con la hora actual dentro del cierre, sin escribirla.
- **SC-003**: En una prueba de 50 creaciones simultáneas no hay ningún número duplicado ni reutilizado. Toda la secuencia respeta el número inicial configurado.
- **SC-004**: El 100 % de los episodios con un hito «No aplica», «Sin dato» o abierto queda fuera del promedio correspondiente. Cada promedio muestra su denominador, y ningún hito sin hora aparece como 0 min.
- **SC-005**: Con los mismos filtros y el mismo corte, el 100 % de las cifras del tablero de gestión coincide con el PDF y el Excel. El 100 % de las filas de «Control de tickets» coincide entre la vista, el PDF y el Excel.
- **SC-006**: Un cambio confirmado por un analista aparece en la pantalla de otro usuario autorizado, sin recarga manual, en 1 minuto o menos.
- **SC-007**: El mensaje para WhatsApp se obtiene y se copia en no más de dos acciones, también si falta el enlace de Maps, sin errores.
- **SC-008**: En la matriz de permisos, el 100 % de los intentos de modificar por parte de Gerente, Supervisor y Asistente se rechaza, tanto desde la interfaz como por acceso directo.
- **SC-009**: «Control de tickets» con todo el historial y 5.000 tickets se muestra y descarga en 10 segundos o menos.
- **SC-010**: Ninguna falla de una electrolinera o de varios conectores de un mismo equipo produce más de un ticket en las pruebas de aceptación de US1.
- **SC-011**: Ningún ticket del servicio de chatbot ni de un elemento del sitio aparece asociado a un cargador, a conectores o a una red, ni evaluado contra una referencia de tiempo del protocolo.
- **SC-012**: Para un conjunto de prueba con reaperturas, cierres en los límites y correcciones de T4, el 100 % de los estados al final de cada período coincide con el valor esperado calculado a mano. Un informe emitido antes de una corrección conserva su huella original.

## Decisiones

Las decisiones P-03 a P-16 quedaron resueltas por el responsable del producto el 2026-11-20 (ver Clarifications). P-01 y P-02 se resolvieron el 2026-11-23 (ver la tabla).

### Pendientes

| ID | Tema | Situación | Mientras tanto |
|----|------|-----------|----------------|
| P-01 | Número inicial | Debe consultarse al responsable justo antes de la puesta en marcha (FR-014), porque la secuencia externa sigue avanzando. | Sin valor de producción: en producción la creación queda bloqueada hasta configurarlo. Los ambientes de prueba usan números ficticios. **Resuelta el 2026-11-23**: el responsable decidió que ningún valor sale del desarrollo y que el Administrador configura el número inicial en producción, según la secuencia externa de ese momento. |
| P-02 | Versión del protocolo | La portada dice versión 2.0 (7 oct 2026, «Aprobado por gerencia»). Las páginas interiores y el control documental dicen versión 1.0 (10 feb 2026, «Versión inicial para aprobación gerencial»). | La versión de referencias que se carga al inicio se rotula «Protocolo de monitoreo firmado (versión por confirmar)». Al confirmarse, se corrige solo el rótulo de esa versión de referencias (FR-051a). **Resuelta el 2026-11-23**: protocolo aprobado y final, versión 2.0; la migración V022 cambió solo el rótulo. |

### Resueltas

| ID | Tema | Decisión | Requisitos |
|----|------|----------|------------|
| P-03 | Referencias de tiempo | Se conservan por versión. Cada episodio guarda la versión vigente en su T0, y un cambio futuro crea una versión nueva sin alterar los reportes ya emitidos. | FR-051, FR-051a |
| P-04 | Caída persistente KIA | Más de 30 minutos. | FR-051 |
| P-05 | Prioridad | Se propone según el protocolo y el analista la ajusta con motivo. Lectura de la tabla del protocolo: Espera de conexión, Media; Desconexión, Alta; Falla de conector, Alta; Conector retenido, Crítica; Intermitencia, Media; Punto KIA con caída persistente, Media; No permite detener la carga, Crítica. Las alzas por puerto 9081, electrolinera o recurrencia las hace el analista con motivo. | FR-034 |
| P-06 | Medición | La recuperación (T3−T0) y el cierre (T4−T0) son indicadores separados. Las referencias de atención se comparan con T3−T0. | FR-045, FR-052 |
| P-07 | Gráficos ponderados | Sin ponderación hasta que se definan y aprueben los pesos. | FR-082 |
| P-08 | Visitas | Registro manual de la visita y del nombre del proveedor, sin asignación ni comunicación automática. | FR-035 |
| P-09 | Varios equipos | Un ticket por equipo, relacionados entre sí (Q5). | FR-003a, FR-017 |
| P-10 | Elementos del sitio | Se admiten la barrera vehicular y «Otro elemento del sitio», ubicados con un equipo de referencia o con una ubicación escrita a mano. Se cuentan en el grupo «Sin red». | FR-001, FR-001a, FR-005 |
| P-11 | Catálogo de tipos | Se mantiene con cambios controlados del sistema, con migración y especificación. Incluye «Incidencia no catalogada». | FR-020 |
| P-12 | Estado en un período pasado | Se muestra el estado al final del período. | FR-074 |
| P-13 | Disponibilidad e intermitencias | Se consultan en sus módulos, sin recalcularlas aquí. | FR-083 |
| P-14 | Red histórica | Cada ticket conserva la red del equipo en su T0. | FR-007 |
| P-15 | Campana | Feature transversal aparte (Q4). | FR-096 |
| P-16 | Histórico del Excel | No se importa. SARC contiene solo los tickets creados en el sistema. | FR-095 |

## Assumptions

- Los usuarios, roles y la autenticación de RF-001 se reutilizan sin cambios.
- El inventario (RF-002) es la única fuente de nombre, red, ubicación, enlace de Maps y conectores. Esta feature no corrige el inventario.
- Los técnicos y proveedores no tienen cuenta en SARC. Su participación se registra como eventos (RF-006.14).
- Zona horaria y día operativo: Ecuador, igual que en los demás módulos. Los períodos tienen inicio inclusivo y fin exclusivo.
- La actualización automática reutiliza el comportamiento vigente de los demás módulos: refresco periódico mientras la pantalla está a la vista, que se detiene cuando la persona está inactiva para no impedir el cierre por inactividad.
- Las evidencias siguen las reglas y el almacenamiento vigentes para imágenes y PDF. Los límites exactos se definen en el plan técnico, conforme al TRD.
- Los logos corporativos son los de `documents/logos` (EOS Tech Company y Autoeastern), ya incorporados como recursos de los informes en `sarc-bakend/src/main/resources/reports/logos/`.
- El Excel actual de 44 columnas no se importa (P-16). SARC contiene solo los tickets creados en el sistema y el Excel histórico queda aparte.
- Los nombres de personas del protocolo no forman parte de las reglas del producto. Los responsables se tratan por rol.
