# Feature Specification: Código asignado según estado de uso KIA

**Feature Branch**: `main` (rama existente; este comando no crea ni cambia ramas)

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "Feature 001 — Código asignado según estado de uso KIA.
LIBRE: Código asignado opcional. BLOQUEADO: obligatorio. Conservar el código al pasar de
BLOQUEADO a LIBRE; exigir uno válido al pasar de LIBRE a BLOQUEADO. Mantener formato y unicidad,
con validación consistente en todas las operaciones autorizadas. Sin alterar otros estados,
redes, módulos ni registros históricos."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Crear un punto KIA según su estado de uso (Priority: P1)

Como administrador o analista autorizado, quiero registrar un punto de carga KIA LIBRE sin
inventar un Código asignado, y disponer de esa validación cuando el punto sea BLOQUEADO.

**Why this priority**: Resuelve la necesidad principal de registrar cargadores que no requieren
código para su uso, preservando el control sobre los que sí lo necesitan.

**Independent Test**: Crear puntos KIA con los mismos datos válidos y variar únicamente estado
de uso y código; verificar aceptación o rechazo y consultar el resultado guardado.

**Acceptance Scenarios**:

1. **Given** un alta válida de un punto KIA LIBRE sin Código asignado,
   **When** una persona autorizada confirma el alta,
   **Then** se registra sin código y sin exigir un valor de reemplazo.
2. **Given** un alta de un punto KIA BLOQUEADO sin Código asignado,
   **When** una persona autorizada intenta guardarla,
   **Then** no se registra y se informa en español que el código es obligatorio.
3. **Given** un alta válida de un punto KIA LIBRE o BLOQUEADO con código `654321` no utilizado,
   **When** se confirma el alta,
   **Then** se registra conservando exactamente ese código.
4. **Given** un alta LIBRE con un código informado inválido o duplicado,
   **When** se intenta guardar,
   **Then** se rechaza ese código: opcional no significa exento de validación.
5. **Given** dos puntos KIA LIBRES distintos, con sus demás identificadores válidos y únicos,
   **When** se registran sin Código asignado,
   **Then** ambos se aceptan; la ausencia de código no constituye un duplicado.

---

### User Story 2 - Cambiar el estado sin perder datos del equipo (Priority: P1)

Como administrador o analista autorizado, quiero cambiar el estado de uso de un punto KIA
conservando su identidad y sus datos, y completar un código únicamente cuando sea necesario.

**Why this priority**: Hace coherente la nueva regla durante la vida del cargador y evita perder
un código útil al habilitar el uso libre.

**Independent Test**: Usar un punto BLOQUEADO con código y otro LIBRE sin código; ejecutar cada
transición de forma independiente y comparar sus datos antes y después.

**Acceptance Scenarios**:

1. **Given** un punto KIA BLOQUEADO con código `654321`,
   **When** se cambia únicamente su estado de uso a LIBRE,
   **Then** se conserva `654321`, la identidad del equipo y todos los demás datos de negocio.
2. **Given** un punto KIA LIBRE sin código,
   **When** se intenta cambiarlo a BLOQUEADO sin proporcionar uno,
   **Then** se rechaza el cambio con mensaje claro en español y el equipo permanece LIBRE.
3. **Given** un punto KIA LIBRE sin código,
   **When** se cambia a BLOQUEADO proporcionando `654321`, válido y no utilizado,
   **Then** se guardan el nuevo estado y ese código, sin modificar otros datos no solicitados.
4. **Given** un punto KIA LIBRE que ya conserva `654321`,
   **When** se cambia únicamente su estado a BLOQUEADO,
   **Then** se utiliza el código existente sin obligar a escribirlo de nuevo.
5. **Given** un punto KIA LIBRE sin código,
   **When** se editan otros datos permitidos sin cambiar su estado,
   **Then** la ausencia de código no impide guardar esos cambios.
6. **Given** un punto KIA LIBRE sin código,
   **When** se intenta pasar a BLOQUEADO con código inválido o duplicado,
   **Then** se rechaza el cambio y no se guarda ni el nuevo estado ni el código rechazado.
7. **Given** una corrección explícita del código de un punto KIA ya admitida por las reglas
   anteriores, sin cambiar su estado de uso,
   **When** una persona autorizada informa un nuevo código válido y no utilizado,
   **Then** la corrección conserva su tratamiento previo; esta feature no congela el código
   existente ni elimina las validaciones o la auditoría de esa operación.

---

### User Story 3 - Obtener la misma regla por cualquier operación autorizada (Priority: P1)

Como responsable del inventario, quiero que el resultado dependa de las reglas del equipo
y de la autorización de la persona, independientemente de cómo se solicite la operación.

**Why this priority**: El requisito incluye altas y cambios realizados por carga masiva u otras
vías autorizadas; la regla no puede depender exclusivamente de una pantalla.

**Independent Test**: Repetir los casos de alta y transición por registro individual,
importación y solicitudes autorizadas fuera del formulario, comparando los resultados.

**Acceptance Scenarios**:

1. **Given** los mismos datos de un punto KIA LIBRE sin código,
   **When** se solicita su creación por cada vía autorizada de registro,
   **Then** todas permiten registrarlo sin código.
2. **Given** los mismos datos de un punto KIA BLOQUEADO sin código,
   **When** se solicita su creación o un cambio a ese estado por cada vía autorizada,
   **Then** ninguna guarda el resultado inválido y todas explican en español el motivo.
3. **Given** una importación que propone cambiar únicamente un punto BLOQUEADO con código
   `654321` a LIBRE sin informar un código nuevo,
   **When** se aprueba y aplica la modificación conforme a las reglas de importación,
   **Then** el equipo queda LIBRE y conserva `654321`.
4. **Given** puntos de otras redes, electrolineras KIA o puntos KIA DESCONECTADOS,
   **When** se realizan las operaciones de registro y edición autorizadas,
   **Then** la obligatoriedad y las demás reglas aplicables siguen siendo las previas a esta feature.
5. **Given** una persona sin autorización para gestionar equipos,
   **When** intenta aprovechar la nueva opcionalidad para crear o editar un cargador,
   **Then** la operación continúa denegada, tenga o no tenga código.
6. **Given** un punto KIA LIBRE sin código con sesiones de consumo vinculadas,
   **When** se consulta, se selecciona ese equipo o se genera un informe de consumo,
   **Then** la ausencia se representa sin inventar un código y se mantienen identidad,
   conciliación, métricas, filtros y permisos; los históricos y archivos ya emitidos no cambian.

### Edge Cases

- Ausencia, texto vacío y un valor formado solo por espacios cuentan como código no informado.
  En un alta LIBRE se permiten; en un alta BLOQUEADO se rechazan.
- Un código informado debe tener seis dígitos y comenzar entre 1 y 9. Son inválidos
  `012345`, `000000`, `12345`, `1234567` y `12345A`. `100000` y `999999`
  cumplen el formato y se aceptan si no están utilizados.
- Un código utilizado por otro equipo continúa duplicado aunque ese equipo esté inactivo o
  eliminado. Guardar el código del propio equipo sin cambiarlo no constituye duplicidad.
- La transición BLOQUEADO a LIBRE no borra el código por haberse vuelto opcional; la omisión de
  un código nuevo no es una solicitud de eliminación.
- Si una importación omite el código de un equipo existente, conserva el código anterior
  según las reglas de actualización vigentes. Si el resultado sería BLOQUEADO sin código,
  esa modificación no se admite.
- Pasar un KIA LIBRE sin código a DESCONECTADO sigue sujeto a la obligatoriedad anterior:
  esta feature no extiende la excepción a DESCONECTADO.
- Ante un rechazo, no se guarda parcialmente el cambio del equipo afectado. Esto no redefine
  el tratamiento existente de otras filas válidas en una importación.
- Registrar un nuevo punto LIBRE sin código no borra ni modifica códigos de equipos ya existentes.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La excepción de obligatoriedad DEBE aplicarse exclusivamente a puntos de carga
  de la red KIA en estado de uso LIBRE.
- **FR-002**: El sistema DEBE permitir crear un punto KIA LIBRE sin Código asignado y mantenerlo
  sin código, sin generar ni exigir un valor sustituto.
- **FR-003**: El sistema DEBE exigir un Código asignado válido para crear un punto KIA BLOQUEADO
  o para guardar una modificación cuyo resultado sea BLOQUEADO.
- **FR-004**: Al cambiar únicamente un punto KIA de BLOQUEADO a LIBRE, el sistema DEBE conservar
  el código existente y los demás datos de negocio del equipo. Los registros normales de
  auditoría del cambio se mantienen.
- **FR-005**: Al pasar de LIBRE sin código a BLOQUEADO, el sistema DEBE impedir guardar hasta
  que se proporcione un código válido. Un código válido ya existente DEBE poder reutilizarse
  en la transición sin volver a introducirlo.
- **FR-006**: Si se informa un Código asignado, incluso para un punto LIBRE, DEBE contener
  exactamente seis dígitos, no comenzar por cero y conservar las reglas actuales de unicidad
  global, incluidos equipos inactivos o eliminados.
- **FR-007**: La obligatoriedad DEBE evaluarse sobre el estado de uso y el código que quedarían
  guardados tras la operación, considerando los valores existentes que deban conservarse.
- **FR-008**: Las reglas DEBEN aplicarse con resultados equivalentes en creación, edición,
  cambio de estado de uso e importación, y en cualquier otra vía autorizada que realice
  esas operaciones, incluidas solicitudes fuera del formulario.
- **FR-009**: Los rechazos DEBEN explicar en español si falta el código, no cumple el formato o
  está utilizado por otro equipo. La explicación DEBE identificar el campo que debe corregirse.
- **FR-010**: Una operación rechazada NO DEBE crear el equipo ni dejar parcialmente guardados
  el nuevo estado o los datos rechazados del equipo afectado.
- **FR-011**: La modificación NO DEBE alterar permisos, reglas de otras redes, electrolineras,
  otros estados de uso ni registros históricos. En RF-003 se permiten exclusivamente las
  adaptaciones de lectura, selección de equipo y presentación en consultas e informes necesarias
  para admitir un Código asignado ausente. Estas adaptaciones NO DEBEN cambiar identidad,
  conciliación, métricas, filtros, permisos ni archivos ya emitidos, ni añadir funcionalidades
  de negocio de consumo. Los demás módulos quedan fuera del alcance.
- **FR-012**: Aplicar esta feature, cambiar a LIBRE u omitir el código en una actualización
  NO DEBE borrar, reasignar ni regenerar automáticamente los códigos existentes. Esta protección
  NO DEBE bloquear las correcciones explícitas autorizadas del código ya admitidas antes de la
  feature; conservan sus reglas de formato, unicidad y auditoría. No se habilita ninguna nueva
  operación de borrado o reasignación.

### Key Entities *(include if feature involves data)*

- **Punto de carga KIA**: equipo del inventario al que se aplica la excepción; conserva su
  identidad y sus datos al cambiar de estado de uso.
- **Estado de uso**: condición LIBRE, BLOQUEADO o DESCONECTADO; determina si se aplica la
  excepción. No equivale al estado administrativo activo, inactivo o eliminado.
- **Código asignado**: identificador opcional para un punto KIA LIBRE y obligatorio para uno
  BLOQUEADO; si existe, conserva formato y unicidad. No es el UID/PUK ni la serie del equipo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las altas válidas de puntos KIA LIBRES sin código de los escenarios
  de aceptación se completa sin pedir un código ficticio o intervención adicional.
- **SC-002**: El 100 % de los intentos de guardar puntos KIA BLOQUEADOS sin código, con formato
  inválido o con código de otro equipo se rechaza con un motivo identificable en español.
- **SC-003**: En el 100 % de las transiciones BLOQUEADO a LIBRE que cambian únicamente el estado,
  se conserva exactamente el código anterior y no cambia ningún otro dato de negocio del equipo.
- **SC-004**: Los mismos casos de entrada producen la misma aceptación o rechazo en el 100 %
  de las vías autorizadas cubiertas: registro individual, actualización, importación y
  solicitudes fuera del formulario.
- **SC-005**: Las comprobaciones de no regresión sobre otros estados, redes y tipos de equipo
  conservan sus resultados previos; ningún registro histórico ni código existente se altera
  por aplicar esta nueva regla.

## Assumptions

- Los demás datos del equipo cumplen sus reglas vigentes; esta feature no flexibiliza serie,
  UID/PUK, conectores, red, tipo ni autorización.
- Los roles autorizados siguen siendo Administrador y Analista, conforme a los permisos del
  inventario ya definidos. No se crean roles ni operaciones de gestión nuevos.
- DESCONECTADO conserva la obligatoriedad previa del código en puntos KIA; la única excepción
  nueva corresponde a LIBRE. Las electrolineras KIA y las otras redes quedan fuera de esa excepción.
- La modificación se aplica a altas y operaciones posteriores; no solicita cambiar retrospectivamente
  equipos ni históricos. Un punto LIBRE con código puede seguir conservándolo.
- La eliminación explícita de un código ya guardado no se añade ni redefine en esta feature.
  La transición de BLOQUEADO a LIBRE y las actualizaciones que omiten el campo lo conservan.
  La edición explícita ya autorizada conserva su comportamiento previo; preservar automáticamente
  un código no significa convertirlo en inmutable.
- El alcance sobre RF-003 se limita a la compatibilidad de sus lectores con la ausencia de
  código definida en FR-011; no modifica reglas de consumo ni sus resultados numéricos.
- Las importaciones conservan sus reglas vigentes de vista previa, aprobación, aplicación por
  filas y conservación de valores omitidos; esta feature solo cambia la validación del
  Código asignado según el estado de uso.
- Esta especificación reemplaza, únicamente para puntos KIA LIBRES, la obligatoriedad general
  anterior descrita en PRD RF-002 y su tabla de restricciones. Las demás reglas siguen vigentes.
- Se cumplen los principios I y II de la constitución al expresar aquí comportamiento, límites
  y escenarios. La exigencia de conservar históricos permanece aplicable. Las decisiones
  técnicas y la implementación quedan para fases posteriores y no forman parte de este documento.
