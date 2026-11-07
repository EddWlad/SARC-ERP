# Feature Specification: Visibilidad, UX/UI y reportes gerenciales

**Feature**: `004-visibilidad-ux-reportes`

**Feature Branch**: No creada en esta fase; la especificación es independiente de la rama de trabajo.

**Created**: 2026-10-31

**Status**: Cerrada el 2026-11-07: entregada y en producción con datos reales; cierre confirmado por el responsable del producto. Los cambios nuevos se gestionan desde Feature 008.

**Input**: Restringir la información de «Fuera de los indicadores oficiales» a Administrador y Analista de Monitoreo, también en consultas y exportaciones, conservando los resultados gerenciales autorizados. Corregir superposiciones de «Nuevo tipo de conector» y defectos visuales confirmados en las pantallas existentes. Incorporar gráficos gerenciales en PDF y evaluar gráficos en Excel, preservando identidad corporativa, WCAG 2.2 AA, temas claro/oscuro, rendimiento y experiencia responsive. No definir tablas, migraciones, endpoints ni soluciones de implementación.

## Clarifications

### Session 2026-10-31

- Q: ¿Qué pueden hacer Gerente, Supervisor y Asistente con PDF/Excel ya archivados que contienen información operativa restringida? → A: Bloquear el original restringido, explicar el motivo y permitir emitir un nuevo informe gerencial con su propia fecha de corte. El original se conserva intacto.
- Q: ¿En qué aplicación deben funcionar los Excel de SARC para aceptar los gráficos incorporados? → A: Excel de escritorio para Windows. La versión no fue indicada; se registrará la versión disponible al ejecutar las pruebas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar y exportar según el rol (Priority: P1)

Como persona que consulta consumo, quiero recibir únicamente la información autorizada para mis roles, en pantalla y en documentos, sin que cambien los resultados oficiales.

**Why this priority**: Ocultar una tarjeta no protege la información si sigue disponible al consultar datos o descargar un documento por otro acceso.

**Independent Test**: Preparar un período con sesiones oficiales y las cinco categorías operativas. Consultar y exportar con cada rol, con combinaciones de roles y mediante accesos directos; comparar los resultados oficiales y la ausencia de información restringida para quien no esté autorizado.

**Acceptance Scenarios**:

1. **Given** un Administrador o Analista de Monitoreo y datos de las cinco categorías operativas, **When** consulta consumo, **Then** ve la tarjeta completa con pendientes de vincular, por aprobar, energía por revisar, energía rechazada y pruebas, con sus cantidades y energía; ninguna engrosa los indicadores oficiales.
2. **Given** una persona con roles únicamente de Gerente, Supervisor o Asistente, individualmente o combinados, **When** consulta el mismo período, **Then** no ve la tarjeta ni recibe sus agregados o detalles restringidos y mantiene los mismos indicadores oficiales, tendencias y resultados gerenciales autorizados.
3. **Given** los mismos datos, corte, período y filtros, **When** cada rol autorizado emite PDF o Excel, **Then** los resultados oficiales coinciden; Administrador/Analista pueden recibir el apartado operativo separado y los otros roles no reciben esa información en tablas, gráficos, anexos, hojas ocultas ni datos de soporte del documento.
4. **Given** una persona sin acceso operativo que conoce un acceso directo o una referencia de informe, **When** intenta obtener información restringida sin pasar por la tarjeta, **Then** el sistema no la entrega ni permite eludir la restricción cambiando filtros, formato o modo de acceso.
5. **Given** una cuenta con Analista de Monitoreo y Gerente, o con Administrador y cualquier otro rol, **When** consulta o exporta, **Then** conserva acceso operativo por su rol autorizado; una combinación formada solo por Gerente, Supervisor y Asistente no lo concede. Ninguna combinación obtiene permisos de edición nuevos por esta feature.
6. **Given** un original archivado que contiene información operativa restringida, **When** Gerente, Supervisor o Asistente intenta abrirlo o descargarlo, **Then** se bloquea el original con una explicación en español y puede emitir un nuevo informe gerencial autorizado con fecha de corte propia; el original permanece intacto y el nuevo no se presenta como aquella emisión histórica.
7. **Given** un informe archivado sin información restringida para su solicitante, **When** lo consulta, **Then** mantiene el acceso autorizado a la copia original sin modificaciones.
8. **Given** una persona que deja de tener Administrador o Analista de Monitoreo, **When** realiza una nueva consulta o descarga con sus permisos vigentes, **Then** ya no obtiene información operativa, aunque hubiera accedido antes con otro rol.

---

### User Story 2 - Leer y operar sin obstáculos visuales (Priority: P1)

Como usuario, quiero leer etiquetas, ayudas, errores y resultados completos y utilizar las acciones autorizadas en computadora, tablet y celular, sin superposiciones ni pérdida de contexto.

**Why this priority**: Una ayuda superpuesta puede impedir completar un campo o interpretar un dato. Corregir defectos comprobados no debe convertirse en un rediseño general.

**Independent Test**: Recorrer «Nuevo tipo de conector» y las pantallas auditadas en los cinco anchos y ambos temas, con contenido normal, textos largos y errores. Comparar evidencia anterior/posterior y completar acciones autorizadas con teclado.

**Acceptance Scenarios**:

1. **Given** «Nuevo tipo de conector», **When** se abre, se completan campos y se muestran ayudas o errores, **Then** cada etiqueta, campo y explicación permanece legible sin solaparse ni ocultar las acciones de guardar y cancelar.
2. **Given** las pantallas auditadas a 360, 390, 768, 1024 y 1440 píxeles de ancho, **When** se recorren en claro y oscuro, **Then** no se pierden controles, datos esenciales ni mensajes por superposición, desbordamiento o cortes de texto sin acceso al contenido completo.
3. **Given** ayudas de varias líneas, identificadores largos y errores, **When** aparecen en formularios, diálogos, tablas o tarjetas, **Then** se pueden leer y utilizar sin reducirlos a un tamaño ilegible; el desplazamiento necesario de una tabla no desborda toda la página ni impide las acciones.
4. **Given** una persona que usa teclado o amplía la presentación, **When** navega por los flujos auditados, **Then** el foco, las etiquetas, los mensajes y las acciones cumplen los criterios aplicables de WCAG 2.2 AA sin depender solo del color o de pasar el ratón.
5. **Given** el registro de auditoría visual, **When** se revisa una corrección, **Then** se identifica pantalla, estado, ancho, tema, defecto, impacto y evidencia anterior/posterior; lo no comprobado o no reproducido no se declara corregido.

---

### User Story 3 - Presentar los gráficos oficiales en PDF (Priority: P1)

Como usuario autorizado para informes, quiero que el PDF incluya los gráficos gerenciales de consumo, para presentar tendencias y distribuciones sin reconstruir el tablero fuera de SARC.

**Why this priority**: El informe es un entregable gerencial y debe permitir interpretar los mismos resultados que el tablero, con presentación corporativa y permisos coherentes.

**Independent Test**: Emitir PDF de un conjunto conocido con filtros, energía ausente, ceros reales y empates; cotejar gráficos y cifras con el tablero del mismo corte y revisar todas las páginas.

**Acceptance Scenarios**:

1. **Given** consumo oficial de un período y filtros concretos, **When** se emite el PDF, **Then** incluye energía diaria, sesiones diarias, Top 5 por energía con empates y distribuciones de energía por red, plataforma y tipo de infraestructura; conserva el resumen y las tablas para comprobarlos.
2. **Given** los mismos datos oficiales, corte, período y filtros, **When** se cotejan tablero e informe, **Then** coinciden los valores normalizados y las cifras con la precisión de presentación acordada; ningún gráfico incorpora registros no oficiales para completar una serie.
3. **Given** seis o más equipos empatados con el quinto lugar, nombres largos y muchas fechas, **When** se revisa el documento completo, **Then** aparecen todos los equipos elegibles, sus posiciones compartidas y la nota de empate; títulos, ejes, unidades, leyendas y valores relevantes son legibles y no se recortan.
4. **Given** un período sin sesiones oficiales o con sesiones sin energía conocida, **When** se emite el informe, **Then** se explica la ausencia sin inventar energía, porcentajes o gráficos engañosos; un cero informado sigue distinguiéndose de un dato ausente.
5. **Given** nuevos datos posteriores a una emisión, **When** se compara el documento con el tablero actualizado, **Then** el informe conserva su fecha de corte y sus cifras de emisión, sin aparentar representar el estado posterior.

---

### User Story 4 - Conservar un Excel útil y evaluar sus gráficos (Priority: P2)

Como usuario autorizado, quiero un Excel íntegro, verificable y reutilizable, con gráficos cuando aporten valor sin dañar sus datos ni sus funciones.

**Why this priority**: Los gráficos son complementarios; la integridad y utilidad del desglose tienen prioridad.

**Independent Test**: Registrar la evaluación de gráficos, abrir archivos representativos en el programa de hoja de cálculo acordado y cotejar celdas, tipos de datos, fórmulas existentes, filtros, totales y visibilidad.

**Acceptance Scenarios**:

1. **Given** una evaluación en Excel de escritorio para Windows que demuestra compatibilidad y utilidad sin comprometer integridad, legibilidad, rendimiento o permisos, **When** se exporta Excel, **Then** incluye los gráficos gerenciales pertinentes con los mismos datos oficiales de sus tablas, sin sustituir el desglose por imágenes ni exponer información restringida.
2. **Given** una limitación reproducible que hace inconveniente incorporarlos, **When** se registra la evaluación, **Then** se explican la limitación, su impacto y las comprobaciones realizadas, y se conserva un Excel tabular íntegro; no se omiten gráficos sin justificación.
3. **Given** un archivo con o sin gráficos, **When** se abre y se utilizan sus datos y fórmulas existentes, **Then** no solicita reparación ni presenta referencias rotas, errores nuevos, cifras alteradas o pérdida de columnas autorizadas.

### Edge Cases

- Las cinco categorías operativas están en cero: Administrador/Analista mantienen la tarjeta completa y los otros roles no reciben el apartado; una restricción no se presenta como cero.
- Una sesión reúne varias condiciones no oficiales: mantiene la precedencia y el conteo excluyente aprobados en 003; cambia visibilidad, no clasificación.
- Una lectura superior a 1.000 kWh ya fue aprobada y satisface todos los controles: continúa siendo oficial con el tratamiento gerencial de 003, sin sumarse dos veces.
- Acceso directo, referencia compartida de archivo, cambio de formato o pérdida de rol operativo: no evaden la autorización vigente.
- Un original anterior incluye información restringida en una hoja, gráfico o anexo aunque su primera página parezca gerencial: se restringe el archivo completo para el receptor no autorizado.
- Un documento fue descargado antes del cambio de permisos: esta feature controla accesos futuros dentro de SARC; no puede retirar copias que ya están fuera del sistema.
- Ayudas y errores simultáneos, cambio de tema, texto ampliado y contenido largo: no cubren controles ni vuelven inaccesibles las acciones.
- Estados de carga, vacío, error, permisos limitados, confirmación y resultados extensos: se auditan, no solo las pantallas con datos breves y sin errores.
- No hay energía conocida para una distribución o su total conocido es cero: se distingue ausencia de cero y no se inventan participaciones.
- El Top ampliado no cabe en un bloque: se conserva todo mediante una presentación legible, sin excluir empatados para ajustarlo.
- Un gráfico de Excel depende de datos que el receptor no puede consultar: tampoco se incluyen ocultos como soporte del gráfico.

## Requirements *(mandatory)*

### Functional Requirements

#### Visibilidad y conservación

- **FR-001**: La tarjeta operativa DEBE mostrarse únicamente a quienes tengan Administrador o Analista de Monitoreo. Gerente, Supervisor y Asistente sin alguno de esos roles NO DEBEN visualizarla. Se mantiene la unión de roles autorizados del PRD y los permisos de edición existentes.
- **FR-002**: La información operativa restringida comprende cantidades, energía, desgloses y detalles no oficiales de pendientes de vincular, por aprobar, energía por revisar, energía rechazada y pruebas. Su protección DEBE ser efectiva en todas las consultas y formas de entrega correspondientes, no solo visualmente.
- **FR-003**: PDF, Excel, vistas previas y accesos a reportes DEBEN aplicar la misma autorización vigente. Un receptor sin acceso operativo NO DEBE recibir información restringida en contenido visible, anexos, hojas ocultas, datos de soporte de gráficos ni otros contenidos del archivo.
- **FR-004**: Los cinco roles DEBEN conservar la consulta y exportación gerencial autorizada de indicadores, sesiones oficiales, tendencias, rankings y distribuciones. Para los mismos datos, corte, período y filtros, los resultados oficiales DEBEN coincidir entre roles; restringir información NO DEBE cambiar las reglas de cómputo.
- **FR-005**: La feature NO DEBE borrar ni reclasificar sesiones, alterar decisiones energéticas, cambiar importaciones, crear duplicados, modificar permisos de edición ni reescribir históricos. La trazabilidad operativa DEBE seguir disponible para los roles autorizados.
- **FR-006**: Las sesiones aprobadas sobre el umbral que sean oficiales DEBEN conservar el tratamiento de 003: inclusión una sola vez y distinción de su subconjunto y justificación vigente donde corresponda. Su energía no se convierte en no oficial por el solo hecho de superar el umbral.
- **FR-007**: Los informes emitidos y archivados DEBEN conservar contenido, identificación y fecha de corte originales. Un documento anterior sin información restringida para su solicitante DEBE seguir consultable según los permisos vigentes.
- **FR-008**: Si un original archivado contiene información operativa restringida, Gerente, Supervisor y Asistente sin rol operativo NO DEBEN poder abrirlo ni descargarlo. El sistema DEBE explicar el motivo en español y permitir emitir un nuevo informe gerencial autorizado del período y filtros solicitados, con fecha de corte propia e identificación diferenciada. NO DEBE sustituir el original ni atribuir al nuevo su corte o sus cifras históricas.
- **FR-009**: Toda nueva consulta o entrega DEBE respetar los roles vigentes de quien la solicita, aunque conociera el acceso directo o hubiera tenido otro rol. No se introduce segmentación por red ni se amplían permisos de otros módulos.

#### Auditoría visual y experiencia de uso

- **FR-010**: «Nuevo tipo de conector» DEBE permitir leer etiquetas, ayudas y errores, introducir datos y acceder a sus acciones sin superposiciones ni cortes que impidan comprender o completar la operación.
- **FR-011**: La auditoría DEBE cubrir las pantallas existentes de acceso y cambio de contraseña, Inicio, Usuarios, Inventario —detalle, formularios, conectores y carga masiva—, Consumo —tablero, sesiones, importaciones y revisión energética— y Reportes emitidos, incluyendo navegación y diálogos asociados. Se utilizan los roles autorizados sin habilitar funciones nuevas.
- **FR-012**: Cada pantalla y estado aplicable auditado DEBE comprobarse a 360, 390, 768, 1024 y 1440 píxeles de ancho, en claro y oscuro. Se DEBEN corregir los defectos confirmados de superposición, desbordamiento, contenido esencial inaccesible, alineación que confunda asociaciones y comportamiento responsive que impida una acción autorizada. Es admisible el desplazamiento localizado necesario de tablas densas si conserva contexto y operabilidad.
- **FR-013**: La auditoría DEBE entregar un inventario de pantallas y estados revisados y un registro de hallazgos con identificador, impacto, reproducción, ancho, tema, evidencia y estado final. Cada corrección DEBE poder comprobarse con evidencia anterior/posterior; las exclusiones justificadas, limitaciones de prueba y casos no reproducidos DEBEN declararse.
- **FR-014**: Las pantallas y componentes revisados DEBEN cumplir los criterios aplicables de WCAG 2.2 AA: contraste, etiquetas y asociaciones de ayuda/error, acceso sin depender solo de color o hover, recorrido por teclado, foco visible y no oculto, lectura y reflujo con ampliación y objetivos de interacción accesibles. Las comprobaciones automáticas NO sustituyen la verificación manual de los flujos relevantes.
- **FR-015**: Se DEBEN preservar identidad corporativa, colores, tipografías, vocabulario, componentes y comportamiento de SARC, salvo ajustes necesarios para un defecto confirmado o para accesibilidad. La revisión y mejora visual DEBEN utilizar Impeccable si está disponible; su uso no autoriza rediseñar otras funcionalidades, inventar información ni añadir decoraciones o dependencias innecesarias.

#### Reportes y gráficos gerenciales

- **FR-016**: El PDF de consumo DEBE incorporar seis representaciones: energía diaria, sesiones diarias, Top 5 por energía con empates, y energía por red, plataforma y tipo de infraestructura. Se conservan los indicadores, tablas y notas autorizadas que permiten comprobarlas. No se requieren gráficos nuevos de módulos todavía no implementados.
- **FR-017**: Gráficos y tablas DEBEN corresponder al mismo período, filtros, conjunto oficial y corte de emisión; DEBEN indicar alcance, unidades, zona horaria operativa y fecha de corte cuando corresponda. Los cambios posteriores del tablero NO DEBEN alterar el documento. Se preservan D10–D14 y las reglas de 002/003 sobre redondeo por lectura, ausencia frente a cero, sesiones iniciadas y empates.
- **FR-018**: Los gráficos PDF DEBEN ser legibles en todas las páginas, con títulos, ejes o referencias equivalentes, unidades, leyendas y categorías identificables sin depender solo del color. Los nombres largos, períodos extensos y empates NO DEBEN causar pérdidas, solapamientos ni recortes. Los resultados DEBEN poder comprobarse con datos tabulares o equivalentes textuales autorizados.
- **FR-019**: La ausencia de datos, energía ausente y cero conocido DEBEN distinguirse en tablero e informes. NO se DEBEN inventar lecturas, fechas, porcentajes, duraciones o tendencias para rellenar gráficos.
- **FR-020**: Se DEBE evaluar y documentar la incorporación de gráficos gerenciales pertinentes en Excel de escritorio para Windows, registrando la versión utilizada, mediante comprobaciones de compatibilidad, legibilidad, integridad de datos y fórmulas existentes, utilidad, permisos y rendimiento. Si se satisfacen, se DEBEN incluir; si un criterio se incumple de forma demostrable y no puede resolverse dentro del alcance, se DEBE documentar la limitación y conservar íntegros los datos tabulares autorizados. No se exige crear fórmulas donde no existan.
- **FR-021**: Las exportaciones DEBEN abrirse sin reparación y conservar tipos de datos, columnas, hojas y fórmulas autorizadas que sigan aplicando, sin referencias rotas ni errores nuevos. Los gráficos NO DEBEN sustituir el detalle verificable ni exigir que el destinatario utilice otra plataforma para acceder a sus datos autorizados.
- **FR-022**: Las mejoras DEBEN respetar los presupuestos de rendimiento vigentes y conservar la consulta, filtrado y emisión sin bloqueos ni pérdida de funciones esenciales. Toda dependencia adicional propuesta DEBE justificarse en planificación por una necesidad del alcance; esta especificación no prescribe librerías ni otras soluciones.

### Key Entities *(include if feature involves data)*

- **Perfil de acceso efectivo**: roles vigentes y alcance de información que puede recibir una persona; distingue consulta gerencial y operativa sin redefinir la edición.
- **Indicadores oficiales**: resultados de sesiones que cumplen las condiciones aprobadas, ligados a período, filtros y corte; no cambian por ocultar información operativa.
- **Información operativa restringida**: agregados y detalles de las cinco categorías no oficiales reservados a Administrador y Analista de Monitoreo.
- **Informe emitido**: documento con identificación, alcance, datos y fecha de corte propios; distinto del nuevo informe gerencial emitido cuando no se permite acceder a un original restringido.
- **Gráfico gerencial**: representación de datos oficiales autorizados con unidades y contexto verificable mediante tablas o descripción equivalente.
- **Hallazgo visual**: defecto confirmado en una pantalla y estado concretos, con reproducción, impacto, evidencia y resultado de corrección.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100 % de los casos de cinco roles y combinaciones de US1, solo Administrador/Analista reciben información operativa; cero exposición por los accesos de consulta y descarga comprobados.
- **SC-002**: En el 100 % de los conjuntos con idénticos datos, corte, filtros y período, los resultados oficiales coinciden entre roles y entre tablero, PDF y Excel, sin cambios respecto de las reglas de cálculo aprobadas.
- **SC-003**: En el 100 % de las pruebas de originales archivados restringidos, los roles gerenciales no reciben el archivo y disponen de explicación y emisión gerencial con corte propio. Ningún original cambia por esta feature.
- **SC-004**: «Nuevo tipo de conector» puede completarse sin superposiciones, pérdida de mensajes ni acciones inaccesibles en las diez combinaciones de ancho/tema, con datos válidos, ayudas y errores visibles.
- **SC-005**: El inventario de auditoría cubre el 100 % de las pantallas y estados aplicables de FR-011/013. Cada defecto confirmado dentro del alcance queda corregido y verificado antes del cierre; cada exclusión o limitación se justifica y no se presenta como cumplimiento probado.
- **SC-006**: Las pantallas auditadas y los componentes corregidos superan las comprobaciones automáticas y manuales aplicables de WCAG 2.2 AA en ambos temas, sin incumplimientos confirmados pendientes en la entrega. No se declara conformidad global por una prueba parcial.
- **SC-007**: El 100 % de los PDF de prueba incluye las seis representaciones de FR-016 —o un estado explícito de ausencia de datos cuando no sean representables—, cifras oficiales coincidentes, empates completos y cero páginas con gráficos o textos esenciales recortados o superpuestos.
- **SC-008**: El 100 % de los Excel de prueba abre sin reparación en Excel de escritorio para Windows, conserva datos y fórmulas autorizadas y cumple la matriz de visibilidad. La inclusión de gráficos o su excepción documentada dispone de resultados verificables de evaluación y registra la versión utilizada.
- **SC-009**: Las regresiones de permisos de edición, importaciones, idempotencia, sesiones incompletas, revisión energética, históricos y empates conservan el 100 % de sus resultados esperados; no se introducen cambios funcionales ajenos al alcance.
- **SC-010**: La carga inicial mantiene el presupuesto vigente de 500 kB. Los flujos de consulta y emisión siguen cumpliendo los límites de rendimiento aprobados, documentados en planificación. Ninguna dependencia nueva carece de justificación vinculada a esta feature.

## Assumptions

- Se utilizan los nombres funcionales Administrador, Analista de Monitoreo, Gerente de Electromovilidad, Supervisor de Electromovilidad y Asistente de Electromovilidad. «Gerente/Supervisor/Asistente» designa usuarios sin Administrador o Analista, salvo combinación explícita. Se mantiene el modelo multirol y el acceso total del Administrador definidos en el PRD.
- La restricción alcanza la información de las cinco categorías no oficiales indicadas por el usuario. No restringe por sí sola todo el inventario o historial ni retira el tratamiento gerencial de excepciones aprobadas que son oficiales en 003.
- Se auditan módulos y pantallas existentes. Los gráficos nuevos de los informes se acotan a los seis gráficos de consumo ya disponibles; no se crean dashboards o gráficos de uptime, intermitencias o tickets pendientes de desarrollo.
- Se conservan filtros autorizados, las tres redes, zona horaria y reglas de período y fecha de inicio vigentes. Cotejar tablero e informe presupone el mismo corte, no solo las mismas fechas del período.
- La corrección visual se limita al defecto reproducido y su componente compartido cuando corresponda. Preferencias estéticas sin defecto funcional, de coherencia o accesibilidad no justifican un rediseño general.
- Excel de escritorio para Windows es la aplicación de referencia para aceptar datos, fórmulas y gráficos. La versión no fue indicada por el usuario; se registrará la versión disponible al ejecutar las pruebas. Excel web y LibreOffice no forman parte de la aceptación de esta feature. No se promete compatibilidad con toda versión histórica de cualquier visor; cualquier límite se explicita antes de aceptar el resultado.
- Impeccable está disponible y su contexto se consultó como referencia al especificar. Esto no constituye auditoría renderizada, certificación de cumplimiento ni corrección de pantallas en esta fase.
- Se respeta la constitución 1.0.0 sin reinterpretar D01–D14, cambiar cálculos o alterar copias emitidas. Esta feature particulariza por rol la visibilidad del apartado separado de pendientes/excluidos del PRD y de 003; conserva su trazabilidad y consulta autorizada.
- Esta fase entrega especificación y checklist. No implementa ni define tablas, migraciones, endpoints, librerías o mecanismos. La auditoría visual, evaluación de Excel y mediciones de aceptación se ejecutarán después con evidencia real.
