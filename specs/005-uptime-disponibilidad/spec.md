# Feature Specification: Uptime y disponibilidad, con ajustes de presentación en Consumo

**Feature**: `005-uptime-disponibilidad`

**Feature Branch**: `codex/005-uptime-disponibilidad`, integración autorizada a `main` el 2026-11-04.

**Created**: 2026-11-01

**Status**: Entrega técnica de Feature-005 en cierre; validación humana T124 pendiente. Los ajustes del registro semanal y pasteles se tramitan aparte como Feature-006.

**Input**: Incorporar RF-004 del PRD, Uptime y disponibilidad de puntos de carga y electrolineras, reutilizando equipos, conectores y redes del inventario: registro por conector, período real, ventanas móviles provisionales frente a períodos calendario exactos, minutos por estado, estados especiales, duplicados, historial, validación, dashboards y reportes PDF/Excel. Mejorar la presentación de la información secundaria del dashboard de Consumo con referencia a Inventario, conservar «Fuera de los indicadores oficiales» para Administrador y Analista, y retirar «Aprobadas sobre umbral (incluidas en oficiales)» del PDF de los roles de consulta. No definir tablas, migraciones, endpoints ni estructura de código.

## Clarifications

### Session 2026-11-01

- Q: ¿Cómo debe abrir el dashboard de uptime si todavía no hay semana oficial exacta validada? → A: Abre en la ventana provisional validada más reciente, con rótulo PROVISIONAL y aviso de que no hay cifra oficial.
- Q: ¿Toda medición debe validarse a mano, o las coherentes pueden quedar validadas al guardarse? → A: Toda medición queda pendiente hasta su validación expresa, disponible también por lote para las coherentes.
- Q: ¿Dónde debe dejar de aparecer «Aprobadas sobre umbral» para Gerente, Supervisor y Asistente? → A: En el PDF y en las filas de resumen del Excel. El Excel conserva su desglose por sesión y el dashboard conserva su nota.
- Q: ¿Qué diferencia aceptamos entre la suma de los cuatro estados y la duración real del período sin marcar la medición como observada? → A: Hasta 1 minuto; una diferencia mayor queda observada.
- Q: ¿Qué información de calidad deben recibir Gerente, Supervisor y Asistente en dashboards y reportes de uptime? → A: Cobertura y cantidades por estado, incluidas pendientes, sin valores sin validar, observaciones ni justificaciones.
- Q: Sin semana oficial y con varias ventanas provisionales validadas, ¿cuál abre el dashboard? → A: La de 7 días; si no existe, la más reciente.
- Q: ¿Contra qué valor se aplica el margen de 1 minuto? → A: Contra la duración real del período.
- Q: ¿Cómo se trata una ventana móvil cuya duración difiere de la nominal en más de 1 minuto? → A: Queda observada y debe corregirse antes de validar.
- Q: ¿Cómo se definen equipo evaluado y equipo sin datos? → A: Evaluado: al menos un conector con medición incluida. Sin datos: ninguno incluido y al menos uno sin datos o sin medición.
- Q: ¿Un equipo inactivo cuenta en la cobertura? → A: Solo si tiene medición en el período.
- Q: ¿Qué muestra la tendencia cuando solo hay ventanas provisionales? → A: No se dibuja; un aviso explica que requiere semanas oficiales.
- Q: ¿Se limita el rango consultado? → A: Sí, a 366 días, igual que Consumo.
- Q: ¿Cómo aparecen las filas pendientes de vincular, que no tienen red ni equipo? → A: Como total aparte del período, rotulado «sin atribuir», que no cambia con filtros.
- Q: ¿La medición manual admite adjuntar evidencia? → A: No; se conservan origen, autor, momentos, lote, texto pegado y valores crudos.

### Session 2026-11-02

- Q: ¿Qué archivo se importa mientras Enerlink no ofrezca un filtro personalizado de uptime? → A: El informe semanal tal como se envía por correo cada lunes (modelo `Modelo_Informe_UpTime-…`), sin agregarle columnas. El reporte mensual de Enerlink no se importa por ahora; el informe que emita Enerlink con filtro personalizado se cargará después por la misma vía.
- Q: Enerlink entrega las duraciones en horas con dos decimales; ¿cómo se guardan? → A: Se convierten al minuto entero más cercano y las horas originales se conservan como valores de la fuente.
- Q: ¿Qué margen se acepta en la suma de estados para períodos de más de 7 días? → A: El mayor entre 1 minuto y el 0,02 % de la duración real, redondeado hacia arriba.
- Q: ¿Cómo se resuelve una etiqueta de conector que no indica su posición? → A: El analista vincula una vez y el sistema recuerda esa etiqueta para ese equipo y fuente, sin crear un identificador de conector.
- Q: ¿Se registra la fecha de retiro del conector? → A: No. El denominador usa conectores activos y los que tengan medición en el período.
- Q: Al abrir en provisional, ¿qué ventanas entran en la cifra? → A: Las del tipo elegido cuyo fin cae dentro de su duración nominal antes de la ventana más reciente.
- Q: El informe semanal no trae Ocupado; ¿qué uptime se guarda? → A: El porcentaje del informe tal cual. Sin Ocupado no hay tiempo evaluado que recalcular; Disponible, Con falla y Fuera de línea se guardan como llegan.
- Q: El informe semanal trae una hoja por grupo (Electrolineras y Puntos de carga); ¿cómo se cumple D09? → A: Se enmienda D09 (Constitución 1.2.0): solo este informe lee todas sus hojas, en orden, y cada fila conserva hoja y número de fila.
- Q: El período de la fila 1 del informe no corresponde a la semana enviada; ¿de dónde sale el período? → A: Se elige la semana (lunes a domingo) al cargar el archivo; la fila 1 se ignora.
- Q: Al consultar varias semanas oficiales, ¿cómo se calcula la cobertura? → A: Por pares conector-semana validados sobre pares en alcance; el ranking usa los minutos sumados de cada conector.
- Q: ¿Qué valor de duración es error de captura y cuál queda observado? → A: Sin número interpretable es error de captura o fila inválida; número negativo o no entero queda observado y debe corregirse.
- Q: ¿Cómo afecta una medición excluida a cobertura y consulta? → A: Su conector sigue en el denominador sin contar como cubierto; los roles de consulta solo reciben la cantidad.
- Q: Con cifra oficial y sin tipo elegido, ¿qué muestra el apartado provisional? → A: Aplica la misma preferencia de apertura.
- Q: ¿Qué unidad usan las cantidades por estado? → A: Pares conector-semana en oficial y un estado por conector en provisional.
- Q: ¿Una semana aún abierta cuenta en la cobertura de un rango oficial? → A: No; solo cuentan semanas cerradas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar la medición de un conector (Priority: P1)

Como Analista de Monitoreo, quiero registrar lo que Enerlink informa para cada conector, eligiéndolo del inventario, para dejar de repetir datos y conservar el período real consultado y los minutos por estado.

**Why this priority**: Sin mediciones fiables por conector no existe ningún indicador de uptime. Es el núcleo mínimo del módulo.

**Independent Test**: Registrar manualmente mediciones de dos conectores de una electrolinera JAC y comprobar cálculo, período, tipo de período e independencia de sus resultados.

**Acceptance Scenarios**:

1. **Given** un conector en alcance y un período de 1.440 minutos, **When** se registran Disponible 1296, Ocupado 105, Con falla 0 y Fuera de línea 39, **Then** el sistema calcula y presenta 97,29 % y conserva los cuatro tiempos.
2. **Given** una electrolinera con dos conectores, **When** se registran resultados distintos para cada uno, **Then** cada conector conserva su porcentaje e historial, y consultar uno no muestra ni copia la medición del otro.
3. **Given** una consulta «Últimos 7 días» de Enerlink sin confirmación de semana calendario exacta, **When** se registra, **Then** queda identificada como ventana móvil PROVISIONAL con sus fechas y horas reales.
4. **Given** la grilla de un período, **When** el analista usa «Guardar y siguiente», **Then** avanza al siguiente conector sin repetir nombre ni ubicación y sin arrastrar valores del anterior.
5. **Given** un punto de carga KIA, **When** se intenta registrar una medición, **Then** no se admite porque está fuera del alcance de RF-004.
6. **Given** una medición con 9907 % o con 1.440 horas fuera de línea en una semana, **When** se registra, **Then** queda observada con su motivo y no llega a indicadores oficiales.

---

### User Story 2 - Validar, corregir y evitar duplicados (Priority: P1)

Como Administrador o Analista, quiero validar mediciones, tratar repeticiones y corregir errores sin perder la versión anterior, para que solo lleguen a dirección datos revisados y trazables.

**Why this priority**: Un dato sin validar o sobrescrito en silencio invalida los indicadores, y las correcciones son parte habitual de la operación.

**Independent Test**: Registrar, validar, repetir y corregir una medición, comprobando en cada paso los indicadores, el historial y la auditoría.

**Acceptance Scenarios**:

1. **Given** una medición recién registrada, **When** se consulta el dashboard, **Then** no forma parte de ningún indicador hasta que una persona autorizada la valide.
2. **Given** una medición existente, **When** se registra otra idéntica para el mismo conector, fuente y período, **Then** no se duplica y se informa que ya existe.
3. **Given** una medición existente, **When** llega otra con el mismo conector, fuente y período y valores distintos, **Then** no sustituye a la anterior y se tramita como corrección controlada.
4. **Given** una medición validada, **When** se corrige con justificación, **Then** la versión anterior se conserva y sigue vigente hasta validar la nueva; autor, fecha, motivo y valores anteriores y nuevos quedan registrados.
5. **Given** un equipo con un conector sin medición, **When** se consulta el período, **Then** el conector aparece como sin medición y nunca como 0 %.
6. **Given** un conector con 0 % validado y evaluable, **When** se consultan indicadores, **Then** ese 0 % se incluye como dato real.
7. **Given** un conector marcado Sin datos, Fuera de alcance o Retirado, **When** se valida ese estado, **Then** se conserva con su justificación, no entra en el promedio y se refleja en cobertura o exclusiones.
8. **Given** una medición validada con error de origen, **When** se excluye con justificación, **Then** deja de computar en consultas y reportes nuevos, se conserva y la exclusión puede revertirse.

---

### User Story 3 - Consultar disponibilidad en dashboards (Priority: P1)

Como Gerente, Supervisor, Asistente, Administrador o Analista, quiero consultar el uptime por red, equipo, conector y período, para entender la disponibilidad real y cuánto de ella está respaldado por datos.

**Why this priority**: Es el valor gerencial del módulo y sustituye la consolidación manual.

**Independent Test**: Preparar mediciones validadas, pendientes, provisionales y exclusiones; filtrar con cada rol y cotejar indicadores, cobertura y detalle con los valores esperados.

**Acceptance Scenarios**:

1. **Given** semanas exactas validadas y ventanas móviles, **When** se consulta el dashboard, **Then** el indicador oficial usa solo semanas exactas validadas y las ventanas móviles aparecen aparte, rotuladas PROVISIONAL y con su rango real.
2. **Given** que no existen semanas exactas validadas, **When** se abre el dashboard, **Then** muestra la ventana provisional validada más reciente, rotulada PROVISIONAL, con aviso de que no hay cifra oficial; nunca 0 %.
3. **Given** filtros por red, marca, tipo, equipo, conector y período, **When** se aplican, **Then** indicadores, gráficos y detalle responden al mismo alcance.
4. **Given** un equipo con varios conectores, **When** se consulta su resumen, **Then** se presenta rotulado como promedio de conectores con su cobertura, y no como uptime global del equipo.
5. **Given** dos conectores con el mismo uptime en el ranking de menor uptime, **When** se presenta, **Then** comparten posición y ninguno queda excluido por un identificador interno.
6. **Given** una persona con rol Gerente, Supervisor o Asistente, **When** usa el módulo, **Then** consulta y descarga, pero no dispone de operaciones de registro, corrección, validación o exclusión, tampoco mediante accesos directos.
7. **Given** mediciones pendientes y observadas en el alcance, **When** Gerente, Supervisor o Asistente consulta o descarga, **Then** recibe la cobertura y la cantidad por estado, incluidas pendientes, sin sus valores, observaciones ni justificaciones.

---

### User Story 4 - Cargar mediciones por pegado o Excel (Priority: P2)

Como Analista, quiero pegar el bloque de Enerlink o importar un Excel, para reducir la captura repetitiva sin aceptar datos dudosos a ciegas.

**Why this priority**: Reduce el trabajo semanal, pero el módulo ya aporta valor con el registro manual.

**Independent Test**: Pegar un bloque válido y otro ambiguo; importar un Excel con filas nuevas, idénticas, en conflicto, inválidas y desconocidas, y confirmar solo las admisibles.

**Acceptance Scenarios**:

1. **Given** un bloque con los cuatro estados y sus minutos, en cualquier orden, **When** se pega para un conector elegido, **Then** se interpretan los valores por su etiqueta; un bloque ambiguo o incompleto se rechaza sin adivinar.
2. **Given** un Excel de mediciones, **When** se analiza, **Then** una vista previa muestra filas nuevas, idénticas, en conflicto, inválidas y pendientes de vincular antes de confirmar.
3. **Given** el mismo archivo reimportado, **When** se confirma, **Then** no se crean mediciones duplicadas.
4. **Given** una fila con un identificador desconocido o ambiguo, **When** se procesa, **Then** queda pendiente de vincular, sin asignarle red ni equipo arbitrariamente y sin llegar a indicadores.
5. **Given** el informe semanal con una hoja por grupo, **When** se importa eligiendo su semana, **Then** se leen todas sus hojas, cada fila indica su hoja y número, y el período de todas es la semana elegida.
6. **Given** un archivo cuyo nombre menciona una semana, **When** se importa sin confirmación de período exacto, **Then** sus filas quedan como provisionales.
7. **Given** una fuente semanal verificable habilitada en el futuro, **When** se incorporan sus datos, **Then** se vinculan a los mismos equipos y conectores sin perder el historial.

---

### User Story 5 - Descargar reportes de uptime (Priority: P2)

Como persona autorizada, quiero descargar reportes PDF y Excel coherentes con el dashboard, para compartir resultados validados sin reconstruirlos fuera de SARC.

**Why this priority**: Es un entregable gerencial, y depende de que existan mediciones y dashboard.

**Independent Test**: Emitir PDF y Excel con filtros conocidos y cotejar cifras, tipo de período, fecha de corte y separación de datos con el dashboard del mismo corte.

**Acceptance Scenarios**:

1. **Given** los mismos datos, filtros y corte, **When** se emite PDF o Excel, **Then** las cifras coinciden con el dashboard y solo usan información validada.
2. **Given** mediciones provisionales y exactas, **When** se revisa el reporte, **Then** ambas se distinguen expresamente y ninguna provisional sustituye un KPI semanal oficial.
3. **Given** cualquier reporte de uptime, **When** se inspecciona, **Then** no contiene UID/PUK ni URL OCPP y muestra zona horaria, rango real, fecha de corte y tipo de período.
4. **Given** un reporte ya emitido y mediciones corregidas después, **When** se consulta la copia archivada, **Then** conserva su contenido y fecha de corte.

---

### User Story 6 - Leer mejor Consumo y su PDF gerencial (Priority: P3)

Como persona que usa Consumo, quiero una información secundaria más ordenada y un PDF gerencial sin detalles internos innecesarios, sin que cambie ningún resultado.

**Why this priority**: Es una mejora de presentación independiente de RF-004 y no altera datos.

**Independent Test**: Comparar dashboard y PDF antes y después, con los mismos datos y corte, por rol, a los cinco anchos y en ambos temas.

**Acceptance Scenarios**:

1. **Given** un Administrador o Analista, **When** abre el dashboard de Consumo, **Then** la información bajo las cartillas se presenta en bloques ordenados y titulados, siguiendo el patrón visual de Inventario.
2. **Given** un Administrador o Analista, **When** consulta el dashboard, **Then** sigue viendo «Fuera de los indicadores oficiales» con sus cinco categorías, cantidades y energía.
3. **Given** un Gerente, Supervisor o Asistente sin rol operativo, **When** emite PDF o Excel de Consumo, **Then** el PDF y el resumen del Excel no muestran «Aprobadas sobre umbral», y se conservan los indicadores oficiales y el desglose por sesión del Excel.
4. **Given** un Administrador o Analista, incluso combinado con otro rol, **When** emite PDF o Excel, **Then** conserva ese dato y su apartado operativo.
5. **Given** los mismos datos, filtros y corte, **When** se comparan resultados antes y después del cambio, **Then** coinciden totales, sesiones, rankings, importaciones e indicadores oficiales.

### Edge Cases

- Los cuatro tiempos difieren hasta 1 minuto de la duración real: la medición se considera coherente; el uptime se calcula sobre el tiempo evaluado.
- Los cuatro tiempos suman más de 1 minuto por debajo de la duración real: la medición queda observada y solo puede validarse con justificación.
- Los cuatro tiempos suman más de 1 minuto por encima de la duración real, o suman cero: queda observada y debe corregirse; no puede validarse ni produce porcentaje.
- El porcentaje informado por Enerlink difiere del calculado: se conservan ambos, prevalece el calculado y la medición queda observada.
- Un conector tiene Con falla mayor que cero: esos minutos se muestran, pero no reducen el uptime; uptime de conectividad no equivale a funcionamiento real.
- Ventanas móviles solapadas del mismo conector: se conservan todas; no se suman entre sí ni se combinan para deducir una semana.
- Una ventana de 7 días coincide por fechas con lunes a lunes: sigue provisional hasta confirmar expresamente que los datos están alineados y verificados.
- Se pide más de una semana oficial: el resultado se calcula con los minutos de esas semanas, no promediando porcentajes redondeados.
- Provisionales de 7 y 30 días en la misma consulta: no se mezclan en una misma cifra.
- Un conector se retiró a mitad del período: conserva su historial; las mediciones de períodos anteriores siguen consultables y los períodos posteriores no exigen medición.
- Se reincorpora una posición retirada o cambia el tipo de conector: continúa el mismo historial por posición, conforme a D06.
- Un equipo se reemplaza físicamente: el equipo nuevo inicia su historial propio y el anterior conserva el suyo, conforme a D07.
- Cambia el nombre o código de un equipo: las mediciones se conservan, los reportes nuevos usan el nombre actual y el detalle distingue la etiqueta de la fecha del hecho, conforme a D12.
- Una medición validada se excluye después: deja de computar en consultas y reportes nuevos, sin borrarse; los reportes ya emitidos no cambian.
- Importación parcial: se conservan resultados válidos, conflictos y lote de procedencia.
- Período con fin futuro o fin anterior al inicio: se rechaza con un mensaje comprensible, sin perder lo digitado.
- Todos los conectores filtrados están fuera de alcance o retirados: se explica que no hay conectores evaluables; no se presenta 0 % ni cobertura engañosa.
- Período de Consumo sin aprobadas sobre umbral: PDF y resumen Excel de los roles de consulta no muestran el dato, igual que cuando sí existen.

## Requirements *(mandatory)*

### Functional Requirements

#### Alcance y registro

- **FR-001**: El sistema DEBE cubrir electrolineras KIA y puntos de carga y electrolineras de JAC y CONECTZ, usando Enerlink como fuente principal. Los puntos de carga KIA quedan fuera de RF-004.
- **FR-002**: Cada medición DEBE pertenecer a un conector de un equipo existente en el inventario; las personas eligen equipo y conector, sin capturar de nuevo nombre, ubicación ni red. Un estado especial marcado sobre un equipo DEBE aplicarse a sus conectores en alcance y registrarse por conector.
- **FR-003**: El sistema DEBE ofrecer una grilla por período con los conectores en alcance y su situación, con registro secuencial mediante «Guardar y siguiente».
- **FR-004**: Cada medición DEBE conservar conector, fuente, inicio y fin reales con fecha y hora, zona horaria, tipo de período, minutos enteros de Disponible, Ocupado, Con falla y Fuera de línea, porcentaje informado por la fuente cuando exista, estado, autor y momentos de ingreso y validación. También DEBE conservar la etiqueta de la fecha del hecho: nombre y código del equipo y tipo de conector vigentes en el primer ingreso o, si la medición se importó, la etiqueta de la fuente como nombre. Las correcciones NO DEBEN cambiarla. Una medición manual ingresada tras un renombre conserva el nombre vigente al ingresar, sin reconstruir nombres anteriores. Un archivo con duraciones en horas se convierte al minuto entero más cercano y conserva las horas originales. Una medición del informe semanal sin Ocupado conserva Ocupado vacío y el porcentaje informado, y queda marcada como uptime informado.
- **FR-005**: Los períodos DEBEN usar inicio inclusivo y fin exclusivo. El fin DEBE ser posterior al inicio y no futuro.
- **FR-006**: El tiempo evaluado DEBE ser la suma de los cuatro estados y el uptime DEBE calcularse como (tiempo evaluado − Fuera de línea) / tiempo evaluado × 100. Ocupado y Con falla NO DEBEN descontarse. El cálculo DEBE usar los minutos; la presentación usa dos decimales. Única excepción: una medición del informe semanal sin Ocupado usa como uptime el porcentaje informado; al agregar, pesa la duración real del período y descuenta la parte que ese porcentaje deja fuera.
- **FR-007**: El sistema DEBE dejar observada, con motivo, toda medición con porcentaje fuera de 0–100, suma que difiera de la duración real en más de 1 minuto o, en períodos de más de 7 días, en más del 0,02 % de esa duración redondeado hacia arriba si es mayor, porcentaje informado que difiera del calculado en más de 0,01 puntos, duración numérica negativa o no entera, o ventana móvil cuya duración real difiera de la nominal en más de 1 minuto. En una medición con uptime informado no se compara el porcentaje: se observa si los tres estados recibidos superan la duración real más el margen, si todos son cero o si el porcentaje está fuera de 0–100. Un valor sin número interpretable, un dato obligatorio ausente o un período inválido es error de captura: no crea medición, conserva lo digitado y, en importación, deja la fila inválida con su motivo. Una medición sin observaciones queda apta para validar. Toda medición DEBE conservar los valores tal como llegaron, también en ingreso manual.
- **FR-008**: Una observación por suma inferior a la duración real en más del margen de FR-007 o por diferencia de porcentaje PUEDE validarse con justificación. Las demás observaciones DEBEN corregirse antes de validar.

#### Tipos de período

- **FR-009**: El sistema DEBE distinguir ventana móvil (24 horas, 7 días, 30 días) y período calendario exacto. Toda ventana móvil DEBE rotularse PROVISIONAL y mostrar su rango real.
- **FR-010**: La semana oficial DEBE ir de lunes 00:00 a lunes siguiente 00:00, hora de Ecuador. Una medición solo se considera período exacto con límites alineados y confirmación expresa, trazable, de que los datos están verificados.
- **FR-011**: Las mediciones provisionales NO DEBEN incluirse en la cifra semanal oficial ni presentarse como semana exacta. El sistema NO DEBE deducir semanas exactas a partir de ventanas móviles o datos mensuales.
- **FR-012**: Ninguna cifra agregada DEBE combinar tipos o duraciones de período distintos.
- **FR-013**: Si Enerlink habilita una consulta semanal verificable, el sistema DEBE poder admitir esa fuente sin reconstruir identidades ni perder información.

#### Estados especiales

- **FR-014**: El sistema DEBE admitir SIN_DATOS, FUERA_DE_ALCANCE, RETIRADO, PENDIENTE_REVISION y PENDIENTE_VINCULACION, con justificación o motivo visible para quienes operan el módulo.
- **FR-015**: Sin datos, Fuera de alcance y Retirado NO DEBEN llevar porcentaje ni imputar cero, y NO DEBEN entrar en el promedio, ni antes ni después de validarse. Su validación DEBE confirmar la clasificación.
- **FR-016**: Un conector en alcance sin medición registrada DEBE presentarse como sin medición, nunca como 0 %. Un 0 % validado de un conector evaluable DEBE incluirse en indicadores.
- **FR-017**: La cobertura DEBE ser conectores con medición validada sobre conectores en alcance del período. Sin datos, sin medición y pendientes permanecen en el denominador; Fuera de alcance y Retirado salen de él y se muestran como exclusiones. Un conector inactivo o de un equipo inactivo solo entra en el denominador si tiene medición en el período; no se registra fecha de retiro. Un conector retirado del inventario sin medición en el período DEBE listarse en exclusiones como Retirado. Una medición excluida mantiene su conector en el denominador, sin cubrirlo. Al consultar varias semanas oficiales, la cobertura DEBE calcularse con pares conector-semana validados sobre pares en alcance, solo de semanas cerradas. Las cantidades por estado usan esa unidad en oficial y un estado por conector en provisional, con precedencia excluida, especial vigente, validada y pendiente.
- **FR-018**: Un conector retirado DEBE conservar su historial y continuidad por posición. Un equipo eliminado o inactivo NO DEBE aparecer por defecto en la grilla, y sus mediciones DEBEN conservarse.

#### Validación, duplicados e historial

- **FR-019**: Toda medición nueva DEBE quedar fuera de indicadores hasta su validación expresa por Administrador o Analista, individual o por lote. La validación por lote NO DEBE incluir mediciones observadas.
- **FR-020**: Una medición DEBE identificarse por conector, fuente, inicio y fin. La comparación DEBE incluir los valores originales que no quepan en minutos enteros. Registrar valores idénticos NO DEBE duplicarla; valores distintos NO DEBEN sobrescribirla y DEBEN tramitarse como corrección.
- **FR-021**: Una corrección DEBE exigir justificación y crear una nueva versión. La versión validada anterior DEBE conservarse y seguir vigente hasta validar la nueva.
- **FR-022**: Administrador y Analista DEBEN poder excluir una medición con justificación y revertir la exclusión. No habrá eliminación física ordinaria.
- **FR-023**: Toda medición, versión y lote DEBE persistir sin eliminación automática; nuevas cargas NO DEBEN reemplazar períodos anteriores.
- **FR-024**: Registro, validación, corrección, exclusión, cambio de estado e importación DEBEN auditar actor, momento, operación, referencia y valores anteriores y nuevos cuando corresponda.

#### Pegado e importación

- **FR-025**: El sistema DEBE interpretar el bloque textual de Enerlink por sus etiquetas, sin depender del orden, y rechazar bloques ambiguos o incompletos.
- **FR-026**: El sistema DEBE importar Excel de mediciones tabulares, validando columnas, equipo, conector y período. Solo DEBE usarse la primera hoja, conforme a D09, salvo el informe semanal, que lee todas sus hojas por la excepción de D09 (Constitución 1.2.0). Al cargar el informe semanal, la persona DEBE elegir la semana (lunes a domingo, ya terminada) que reporta; ese es el período de todas sus filas. Sin columna Activo, el equipo se identifica por Cargador solo si una única identidad verificada lo usa; si no, la fila queda pendiente de vincular.
- **FR-027**: Antes de confirmar, una vista previa DEBE contar filas nuevas, idénticas, en conflicto, inválidas y pendientes de vincular. Las personas eligen qué incorporar y el lote queda trazable con usuario, fecha, hora y resultado.
- **FR-028**: La vinculación DEBE usar identidades externas verificadas y la equivalencia de D05. Coincidencias ambiguas o identificadores desconocidos DEBEN quedar en PENDIENTE_VINCULACION, sin atribución arbitraria y fuera de indicadores hasta conciliarse. Sin alias, una etiqueta solo se resuelve sola si su número inicial coincide con una posición activa y el tipo no la contradice. Un alias que apunte a un conector inactivo deja la fila en PENDIENTE_VINCULACION. Al vincular, Administrador o Analista PUEDE aprobar la etiqueta de conector de la fuente como alias de ese conector, para ese equipo y fuente; el alias se aplica en cargas posteriores, es auditable y corregible, y NO DEBE crear un identificador de conector ni cambiar mediciones existentes.
- **FR-029**: El tipo de período NO DEBE deducirse del nombre del archivo. Al cargar, la persona DEBE declarar el tipo de ventana móvil del lote. A falta de confirmación de período exacto, las filas importadas se tratan como provisionales de ese tipo. La confirmación de período exacto aplica solo a filas alineadas y no observadas.

#### Dashboards y consulta

- **FR-030**: Los dashboards DEBEN filtrar por red, tipo de período, semana o ventana, marca, tipo de infraestructura, equipo y conector. DEBEN abrir en la semana oficial completa más reciente; si no existe semana oficial validada, en la ventana provisional validada más reciente, con rótulo PROVISIONAL y aviso de que no hay cifra oficial, prefiriendo 7 días frente a 24 horas y 30 días cuando coexistan; sin ventana de 7 días, la más reciente y, a igual fin, la de menor duración. Dashboards y reportes NO DEBEN aceptar rangos mayores que 366 días.
- **FR-031**: Los dashboards DEBEN mostrar uptime rotulado, horas fuera de línea, horas con falla, equipos evaluados, equipos sin datos, cobertura, tendencia, ranking de menor uptime, resultados por conector y detalle de exclusiones. Un equipo evaluado tiene al menos un conector con medición incluida; un equipo sin datos no tiene ninguno y tiene al menos uno sin datos o sin medición. La tendencia usa solo semanas oficiales; sin ellas no se dibuja y un aviso lo explica. El detalle de exclusiones DEBE listar conector y estado Sin datos, Fuera de alcance o Retirado, sin motivo para roles de consulta; las mediciones excluidas se detallan solo a Administrador y Analista.
- **FR-032**: Conforme a FR-011 y FR-012, la cifra oficial DEBE usar solo semanas exactas validadas. Las ventanas provisionales validadas DEBEN presentarse en un apartado separado; si hay varias en el alcance, se usa la más reciente de cada conector. Si no se elige tipo, el apartado provisional aplica la preferencia de FR-030. Al abrir sin rango elegido, solo entran ventanas del tipo elegido cuyo fin caiga dentro de su duración nominal antes de la ventana más reciente; un conector sin ventana en ese rango queda sin medición en esa vista. El rango mostrado DEBE ir del inicio más antiguo al fin más reciente incluidos.
- **FR-033**: El resumen por equipo y los agregados DEBEN calcularse con los minutos de las mediciones incluidas, rotularse como promedio de conectores y mostrar conectores incluidos sobre conectores en alcance, en pares conector-semana si hay varias semanas oficiales. Ese promedio se pondera por minutos evaluados; no promedia porcentajes.
- **FR-034**: El ranking de menor uptime DEBE ordenar de menor a mayor, compartir posición en empates sobre el valor presentado a dos decimales, resolver el orden visible por nombre e incluir todos los conectores evaluados del alcance. Con varias semanas, cada conector se ordena por sus minutos sumados.
- **FR-035**: Los dashboards DEBEN mostrar zona horaria, rango real y última actualización, reflejar operaciones confirmadas sin recarga manual y advertir cuando falte la semana oficial cerrada más reciente o falle la actualización automática.
- **FR-036**: El módulo DEBE funcionar en computadora, tablet y celular, en temas claro y oscuro, con los criterios aplicables de WCAG 2.2 AA y sin depender solo del color para distinguir estados.

#### Reportes

- **FR-037**: Los reportes PDF y Excel DEBEN aplicar filtros, permisos, exclusiones y corte equivalentes al dashboard, y presentar solo datos validados en sus indicadores, con exactas y provisionales separadas.
- **FR-038**: Los reportes DEBEN incluir identidad corporativa, zona horaria, rango real, tipo de período, fecha de corte y un apartado separado con cantidades de cobertura y exclusiones.
- **FR-039**: Los reportes NO DEBEN incluir UID/PUK ni URL OCPP. Los reportes nuevos DEBEN usar nombres actuales de equipos, conforme a D12. El detalle y el historial de una medición DEBEN mostrar la identificación actual y, cuando difiera, la etiqueta conservada de la fecha del hecho.
- **FR-040**: Los reportes emitidos DEBEN archivarse con su fecha de corte, permitir reconstruir sus datos de soporte y no cambiar por correcciones posteriores.

#### Permisos

- **FR-041**: Administrador y Analista DEBEN poder registrar, editar, corregir, validar, excluir e importar. Gerente, Supervisor y Asistente DEBEN poder consultar y descargar, sin esas operaciones. Se aplica la unión de roles y la autorización DEBE ser efectiva también ante accesos directos.
- **FR-042**: Quienes no tengan Administrador o Analista DEBEN recibir indicadores validados, ventanas provisionales validadas, cobertura y cantidades por estado, incluidas pendientes. NO DEBEN recibir valores sin validar, observaciones, justificaciones, versiones ni detalle de pendientes o de mediciones excluidas. Las filas pendientes de vincular DEBEN contarse como total aparte del período, rotulado «sin atribuir», que no cambia con filtros de red, equipo o conector.

#### Ajustes de Consumo

- **FR-043**: La información bajo las cartillas del dashboard de Consumo DEBE reorganizarse en bloques titulados con filas de métrica, siguiendo el patrón de información secundaria de Inventario y el estilo vigente, sin añadir decoración.
- **FR-044**: «Fuera de los indicadores oficiales» DEBE seguir visible para Administrador y Analista con sus cinco categorías, cantidades y energía, conservando la restricción de 004.
- **FR-045**: El PDF de Consumo y las filas de resumen del Excel emitidos sin acceso operativo NO DEBEN mostrar «Aprobadas sobre umbral» y DEBEN conservar su enfoque gerencial. Los reportes con acceso operativo DEBEN conservar ese dato.
- **FR-046**: Los ajustes de Consumo NO DEBEN alterar cálculos, persistencia, reglas de importación, indicadores oficiales, justificaciones de D10 ni reportes ya emitidos. Las sesiones aprobadas sobre umbral siguen computando una sola vez.
- **FR-047**: FR-045 sustituye, solo para PDF y resumen Excel sin acceso operativo, lo previsto en 003 y en FR-006 de 004 sobre mostrar ese subconjunto. El desglose por sesión del Excel, incluidas sus columnas de excepción y justificación, y la nota del dashboard no cambian de contenido.

### Trazabilidad documental

- **Constitución**: versión 1.2.0. Se aplican los principios I–III. Decisiones aplicables: D05 (vinculación), D06 (continuidad por posición), D07 (reemplazo), D09 (primera hoja, salvo el informe semanal de uptime) y D12 (nombres actuales). D10 y D14 aplican solo a los ajustes de Consumo; D11, D13 y D14 no se extienden a uptime.
- **PRD v1.1**: RF-004.1 a RF-004.16, CA-004-01 a CA-004-06, §3.1, §3.2, §3.3, §4, RN-G, RF-003.15, DT-08 y DT-09.
- **TRD v1.0**: §10 como referencia. «Medición por conector físico» se aplica mediante la identidad por posición de D06, que prevalece como decisión ratificada; TRD no sustituye al PRD.
- **Criterios de la solicitud**: 1 en US1-1 · 2 en US1-3 · 3 en US2-5 · 4 en US1-2 · 5 en US2-2 y US2-3 · 6 en US3-6 · 7 en US3-3 · 8 en US5-1 · 9 en US6-1 · 10 en US6-2 · 11 en US6-3 · 12 en US6-5.

### Key Entities *(include if feature involves data)*

- **Medición de uptime**: resultado de un conector para una fuente y un período real; conserva minutos por estado, porcentaje informado, tipo de período, estado y etiqueta de la fecha del hecho. El uptime se deriva de sus minutos.
- **Versión de medición** (`revisión` en el diseño técnico): valores de una medición en un momento, con autor y justificación; solo una versión validada está vigente.
- **Período de medición**: inicio y fin reales con zona horaria; es ventana móvil provisional o período calendario exacto.
- **Estado de medición**: situación de calidad y publicación; determina si computa, se excluye o queda pendiente.
- **Lote de uptime**: pegado o archivo cargado, con usuario, fecha, resumen y filas observadas.
- **Alias de conector**: etiqueta de una fuente aprobada para un conector de un equipo; no sustituye la identidad por posición.
- **Resumen por equipo**: promedio de conectores rotulado, con su cobertura; nunca un uptime global inferido.
- **Cobertura**: relación entre conectores con medición validada y conectores en alcance de un período, o pares conector-semana con varias semanas oficiales.
- **Reporte de uptime emitido**: documento con alcance, datos y corte propios; no cambia por correcciones posteriores.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un analista registra la medición de un conector en menos de 1 minuto y completa con «Guardar y siguiente» una electrolinera de 3 conectores en menos de 3 minutos.
- **SC-002**: El 100 % de los casos de cálculo conocidos, incluido 97,29 % con 1296/105/0/39, coincide con el valor esperado.
- **SC-003**: Cero conectores sin datos o sin medición aparecen como 0 % en dashboards, PDF o Excel.
- **SC-004**: Cero mediciones provisionales, pendientes, observadas o excluidas entran en la cifra semanal oficial, en el 100 % de los escenarios probados.
- **SC-005**: Reimportar el mismo contenido crea cero mediciones nuevas, y el 100 % de las diferencias se tramita sin perder la versión anterior.
- **SC-006**: Para iguales datos, filtros y corte, dashboard, PDF y Excel coinciden en el 100 % de las cifras comparadas.
- **SC-007**: En el 100 % de las pruebas por rol, Gerente, Supervisor y Asistente sin rol operativo no pueden modificar datos ni recibir información restringida, incluso por acceso directo.
- **SC-008**: Una persona identifica en menos de 10 segundos si una cifra es oficial o provisional y su rango real.
- **SC-009**: El 100 % de las correcciones permite reconstruir autor, momento, motivo y valores anteriores y nuevos.
- **SC-010**: En Consumo, totales, sesiones, rankings y conteos de importación coinciden al 100 % antes y después del cambio, y cero PDF o resumen Excel de roles de consulta contiene «Aprobadas sobre umbral».
- **SC-011**: Las pantallas nuevas y el dashboard de Consumo ajustado no presentan superposición, desbordamiento ni acciones inaccesibles a 360, 390, 768, 1024 y 1440 píxeles en ambos temas, y cumplen las comprobaciones aplicables de WCAG 2.2 AA.
- **SC-012**: Las regresiones de usuarios, inventario, consumo e informes conservan el 100 % de sus resultados esperados.

## Assumptions

- PRD es la fuente principal. Por ello se incluyen PENDIENTE_REVISION y PENDIENTE_VINCULACION además de los tres estados pedidos, y pegado y Excel son obligatorios, aunque la solicitud los condicione a su viabilidad.
- «Hasta validación» en los estados especiales se interpreta como confirmación de la clasificación. Como no tienen minutos válidos, nunca aportan valor al promedio, en línea con PRD §3.3.
- Toda medición se registra por conector, porque RF-004.5 lo exige y todo equipo del inventario tiene conectores. «Cuando aplique» se resuelve con marcas sobre el equipo que se registran por conector.
- La tolerancia de 0,01 puntos y el margen de 1 minuto son valores iniciales, porque PRD no los fija. Se contrastaron el 2026-11-02 con el reporte mensual real de Enerlink (61 conectores): la tolerancia de 0,01 puntos se mantiene y el margen de períodos largos se amplió como consta en FR-007. Detalle en `evidence/enerlink-sample.md`.
- El informe semanal se arma a mano desde la vista «últimos 7 días» de cada cargador; por eso entra como ventana de 7 días (PROVISIONAL) y solo se confirma como semana exacta si quien lo carga lo afirma. Los cargadores KIA que solo están en Wallbox no tienen uptime en Enerlink y quedan fuera.
- Son límites técnicos, no reglas de negocio: hasta 500 mediciones por validación por lote y porcentaje informado de hasta 8 enteros y 4 decimales; superarlo es error de captura.
- La medición manual no admite adjuntos: la spec no los requiere y la trazabilidad se apoya en origen, autor, momentos, lote, texto pegado y valores crudos.
- El redondeo de presentación a dos decimales es una convención de esta feature. D11 aplica solo a energía.
- Una misma persona con rol autorizado puede registrar y validar, porque PRD no exige separación; la autoría queda trazada.
- La regla de FR-042 aplica a uptime; no cambia la restricción operativa de Consumo definida en 004.
- El retiro de «Aprobadas sobre umbral» es una decisión de enfoque, no una restricción de seguridad: el Excel conserva su desglose por RF-003.16 y el dashboard conserva su nota.
- Si solo existen ventanas provisionales, la cifra oficial permanece vacía. Es el resultado esperado de la brecha abierta del PRD §8.2, no un error.
- Quedan fuera: sincronización automática con Enerlink, control de cargadores, RF-005, RF-006, alertas de campana de RNF-NOT-03 (la aplicación aún no tiene campana) y períodos oficiales distintos de la semana calendario.
- Los ajustes aprobados el 2026-11-04 para registro semanal con horario desconocido, media del informe y pasteles pertenecen a Feature-006. Esta especificación documenta el alcance entregado de 005; no acredita esos ajustes como implementados. La limitación de fechas declaradas del Excel se mantiene identificada para su corrección en 006.
- Esta especificación no define tablas, migraciones, endpoints ni estructura de código; eso corresponde a los artefactos de diseño.
