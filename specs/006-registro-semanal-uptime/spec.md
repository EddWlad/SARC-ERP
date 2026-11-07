# Feature Specification: Registro semanal de uptime Enerlink

**Feature**: `006-registro-semanal-uptime`
**Feature Branch**: `codex/006-registro-semanal-uptime`
**Created**: 2026-11-04
**Status**: Especificación funcional aclarada y plan técnico elaborado el 2026-11-04. Pendientes tasks y analyze antes de implementar. Sin implementación.
**Baseline**: Feature-005 integrada en main, commit `9739aca`. Esta feature modifica únicamente el registro/consulta semanal de uptime y añade la composición visual de estados; no vuelve a implementar 005 ni incorpora sus ajustes de Consumo.
**Input**: Registrar cada lunes los «Últimos 7 días» que Enerlink muestra por conector, mediante copiar/pegar o el Excel semanal de dos hojas. Conservar los rangos reales cuando existen y declarar el horario desconocido cuando faltan, con semana de referencia independiente. Añadir pastel en detalle y dashboard, manteniendo el diseño y la arquitectura de SARC.

## Clarifications

### Session 2026-11-04

Decisiones ya respondidas por el responsable del producto antes de separar este alcance como Feature-006; se trasladan sin volver a preguntarlas.

- Q: Al copiar «Últimos 7 días», ¿podemos guardar el Desde/Hasta reales de cada conector? → A: Sí, fechas y horas reales por conector; la semana del informe es solo referencia de entrega.
- Q: El Excel no guarda las horas reales de consulta, ¿cómo se importa? → A: Conservar ventana de 7 días y semana de referencia, con horario real no informado.
- Q: ¿Dónde debe aparecer el pastel de estados? → A: En el detalle por conector y en el dashboard, indicando cobertura y datos no informados; no inventar Ocupado.
- Q: ¿Reimportar Excel del mismo conector y semana corresponde al mismo registro? → A: Sí; datos iguales se omiten y distintos requieren corrección justificada; capturas con fechas reales permanecen aparte.
- Q: ¿Qué resumen se usa para Excel sin horario real? → A: Promedio de porcentajes de conectores validados, rotulado «Promedio del informe»; sin ponderar por duración desconocida.
- Q: ¿Qué aparece al abrir la vista semanal PROVISIONAL con capturas y Excel? → A: La semana de referencia más reciente con datos incluidos, separando capturas con horario e informes sin horario. Las semanas oficiales exactas conservan sus reglas.

- Q: ¿Qué debe hacer Feature-006 con las mediciones de Excel semanales que 005 importó asignándoles fechas y horas ausentes en el archivo? → A: Pasarlas a «Informe sin horario real», conservando porcentajes, validaciones e historial y registrando el cambio; los conflictos se concilian explícitamente. Los reportes ya emitidos conservan su contenido.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Capturar la ventana real de cada conector (Priority: P1)

Como Administrador o Analista, quiero escoger un conector del inventario y pegar sus cuatro estados junto al Desde/Hasta de Enerlink, para registrar la consulta sin repetir el equipo ni imponer el horario de otro conector.

**Why this priority**: Es el recorrido semanal principal mientras el proveedor no permite un rango personalizado.
**Independent Test**: Capturar dos conectores de la misma electrolinera con horas distintas, guardar y avanzar, validar y consultar ambos resultados.

**Acceptance Scenarios**:

1. **Given** un conector y rango real 28-10-2026 08:06:20 a 04-11-2026 08:06:20, **When** se pegan Disponible 163h35m, Ocupado 0h31m, Con falla 0h y Fuera de línea 3h54m, **Then** conserva 9815/31/0/234 minutos, segundos de ambos límites y uptime 97,68 %, pendiente hasta validación.
2. **Given** Guardar y siguiente, **When** se abre otro conector, **Then** no arrastra minutos ni impone sus límites; se conserva únicamente el contexto de la semana de referencia.
3. **Given** una semana elegida y un rango móvil que no coincide con lunes 00:00, **When** se registra, **Then** la referencia no sustituye el rango y la medición continúa PROVISIONAL incluso después de validarla.
4. **Given** límites incompletos, futuros o invertidos, **When** se intenta guardar una captura, **Then** se rechaza con explicación en español y conserva lo digitado.

### User Story 2 - Importar el informe semanal sin inventar horarios (Priority: P1)

Como Administrador o Analista, quiero subir el Excel que envío cada lunes, con ambas hojas y sin agregar columnas, para conservar sus mediciones por conector aunque no contenga las horas reales de consulta ni Ocupado.

**Why this priority**: Evita reconstruir información que la fuente y el archivo no proporcionan.
**Independent Test**: Importar un modelo anonimizado de Electrolineras y Puntos de carga, elegir semana de referencia, confirmar, validar y reimportar.

**Acceptance Scenarios**:

1. **Given** las dos hojas del informe sin horarios ni Ocupado, **When** se importa con semana de referencia, **Then** conserva hoja/fila, identidad por conector, porcentaje y duraciones informadas, ventana de 7 días y «Horario real no informado», sin generar Desde/Hasta.
2. **Given** nombres repetidos en electrolineras, **When** se importan sus filas, **Then** cada conector mantiene resultado propio; identificación desconocida o ambigua queda pendiente de vinculación.
3. **Given** valores idénticos del mismo conector/fuente/semana, **When** se reimporta el archivo o se reordenan filas, **Then** no crea otra medición ni altera indicadores.
4. **Given** valores distintos de esa misma identidad, **When** se incorpora el cambio, **Then** exige corrección justificada; conserva la versión anterior y esta sigue vigente hasta validar la nueva.
5. **Given** una captura con horario del mismo conector y referencia, **When** llega el Excel, **Then** ambos se conservan separados, sin fusión automática.
6. **Given** cabeceras o nombre del archivo con fechas distintas, **When** se importa, **Then** no se usan para fabricar horarios reales; la semana elegida solo clasifica el informe.

### User Story 3 - Consultar la semana y comprender los estados (Priority: P1)

Como persona autorizada, quiero consultar la semana de referencia y sus dos clases de mediciones, con porcentajes y gráficos claros, para distinguir qué se conoce de la disponibilidad sin mezclar cálculos distintos.

**Why this priority**: Hace útil el histórico semanal sin presentar datos ausentes como hechos.
**Independent Test**: Preparar dos semanas con capturas y Excel validados, estados faltantes/0 y pendientes; consultar con filtros y cada rol.

**Acceptance Scenarios**:

1. **Given** dos semanas con mediciones incluidas, **When** se abre la vista semanal PROVISIONAL, **Then** selecciona la referencia más reciente y muestra apartados separados «Con horario real» e «Informe sin horario real».
2. **Given** porcentajes de 0 % y 100 % validados del Excel y otra fila pendiente, **When** se consulta esa referencia, **Then** muestra «Promedio del informe: 50,00 %» y no crea minutos evaluados ni incorpora la pendiente.
3. **Given** capturas con límites reales, **When** se consultan junto al informe, **Then** conservan el cálculo ponderado por minutos vigente y no se produce un porcentaje conjunto de ambos apartados.
4. **Given** los cuatro estados informados, **When** se consulta detalle o dashboard, **Then** presenta pastel/anillo con leyenda textual, duraciones y cobertura coherentes con los datos visibles.
5. **Given** el Excel sin Ocupado, **When** se muestra su gráfico, **Then** declara «Ocupado: No informado» y «Distribución de tiempos informados», sin completar el segmento por diferencia ni presentarlo como distribución de las 168 horas.
6. **Given** estados ausentes o suma conocida cero, **When** se consulta, **Then** conserva la leyenda y muestra un estado vacío explicativo, sin inventar círculo ni sustituir ausencia por cero.

### User Story 4 - Conservar reportes, seguridad e históricos (Priority: P2)

Como persona autorizada, quiero descargar reportes coherentes con el dashboard y seguir consultando el histórico, para compartir resultados trazables sin perder decisiones ni información previa.

**Why this priority**: Protege la continuidad de la entrega ya publicada y la lectura gerencial.
**Independent Test**: Emitir PDF/Excel de una referencia con ambas clases, contrastar cifras, corregir después y abrir la copia archivada anterior con los cinco roles.

**Acceptance Scenarios**:

1. **Given** los mismos datos, filtros y corte, **When** se emite PDF/Excel, **Then** conserva los dos apartados, sus métodos, cobertura y total sin atribuir; para el Excel sin horario identifica referencia y «Horario real no informado».
2. **Given** un reporte archivado, **When** se corrige o reclasifica información después, **Then** su contenido, identidad y fecha de corte permanecen intactos.
3. **Given** GERENTE, SUPERVISOR o ASISTENTE, **When** consulta por cualquier acceso, **Then** no modifica datos ni recibe valores pendientes, motivos operativos o versiones restringidas.
4. **Given** un informe semanal ya importado por 005 con fechas asignadas y mediciones validadas, **When** se habilita 006, **Then** aparece en «Informe sin horario real» con su referencia y porcentajes/validaciones conservados, queda trazado el cambio y los reportes archivados anteriores no cambian.
5. **Given** dos registros anteriores que compiten por la misma identidad de referencia o cuya procedencia es ambigua, **When** se procesa la transición, **Then** se informa el conflicto y se exige conciliación explícita, sin fusionarlos, borrar uno ni elegir el último automáticamente.
6. **Given** una medición de ese Excel tratada como exacta por horarios asignados, **When** se reclasifica, **Then** deja de computar como semana oficial en consultas nuevas y participa en el apartado del informe según su validación conservada; los documentos ya emitidos y las capturas con límites reales verificados permanecen intactos.

### Edge Cases

- Una electrolinera tiene nombres repetidos: se distingue por conector del inventario, no se fusiona por nombre.
- La misma referencia contiene captura y Excel del mismo conector: dos apartados, sin doble conteo en un KPI común.
- Dos cargas concurrentes iguales: una medición; diferencias producen conflicto controlado sin sobrescritura.
- Ocupado ausente frente a Ocupado 0: no informado frente a cero efectivamente proporcionado.
- Porcentajes con diferente precisión: promedio antes del redondeo de presentación; conservar el dato de fuente.
- Ningún conector incluido: sin promedio informado, nunca 0 % imputado.
- Referencia sin datos o filtros sin coincidencia: estado vacío; no contar pendientes de otras referencias ni toda la historia.
- Un conector retirado o equipo inactivo conserva su historial y las reglas de alcance/cobertura de 005.
- Dos registros anteriores compiten por la misma identidad de referencia: conflicto para conciliación explícita, sin escoger el último ni borrar uno.
- Una captura o aprobación exacta anterior tiene procedencia dudosa: no usar la coincidencia de fechas como prueba de observación real; requiere conciliación antes de atribuirle el tratamiento de una captura real o de un Excel con horario asignado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: DEBE usar el inventario existente: puntos JAC/CONECTZ y electrolineras JAC/KIA/CONECTZ visibles en Enerlink, con resultados independientes por conector. Los puntos KIA quedan fuera; no se crean equipos por importar uptime.
- **FR-002**: El flujo principal DEBE ofrecer «Últimos 7 días» por defecto y conservar consultas de 24 horas y 30 días como ventanas separadas, sin mezclarlas con el informe semanal.
- **FR-003**: La captura DEBE conservar los cuatro estados pegados, sus valores de origen y los límites reales de cada conector con fecha/hora/segundos, en la zona operativa existente.
- **FR-004**: La semana de referencia DEBE ser un contexto independiente del intervalo real y del momento de carga. Guardar y siguiente NO DEBE copiar límites ni valores del conector anterior.
- **FR-005**: DEBE mantener interpretación por etiqueta, cálculo por minutos y observaciones/validación expresa de 005 para capturas reales; una ventana móvil validada no se convierte por eso en semana calendario exacta.
- **FR-006**: DEBE importar el Excel semanal actual de dos hojas sin exigir nuevas columnas, conservando hoja/fila, porcentaje, horas originales y conversión al minuto más cercano según reglas existentes.
- **FR-007**: Sin límites reales en el Excel, DEBE conservar ventana de 7 días, semana de referencia y «Horario real no informado». NO DEBE generar horarios desde la referencia, archivo, cabecera, fecha de carga o una duración supuesta.
- **FR-008**: Sin Ocupado, DEBE conservar su ausencia y el porcentaje informado; NO DEBE deducir Ocupado, tiempo evaluado o desconexión a partir del porcentaje. Mantener validación de números/porcentaje aplicable sin fingir comprobar un intervalo desconocido.
- **FR-009**: El Excel sin horario DEBE identificarse por conector, fuente, ventana de 7 días y semana de referencia. Igual lectura no duplica; distinta exige corrección justificada y validación para sustituir la vigente.
- **FR-010**: Capturas con horario y Excel sin horario NO DEBEN fusionarse automáticamente, aunque coincidan conector y referencia. Las capturas conservan su identidad por conector/fuente/inicio/fin.
- **FR-011**: DEBE reutilizar identidades verificadas y alias aprobados; desconocidas/ambiguas permanecen pendientes con motivo y sin atribución arbitraria. Mantener la trazabilidad del lote y las decisiones humanas.
- **FR-012**: La vista semanal PROVISIONAL DEBE abrir en la referencia más reciente con mediciones validadas e incluidas, separando capturas con horario e informes sin horario. No cambia las reglas de semanas oficiales exactas existentes.
- **FR-013**: El bloque con horario DEBE conservar cálculo/ponderación por minutos de 005. El bloque sin horario DEBE mostrar promedio aritmético de porcentajes de conectores validados e incluidos, rotulado «Promedio del informe»; usar precisión de fuente y dos decimales al presentar. Un 0 % real participa; ausencia, pendiente y excluida no.
- **FR-014**: NO DEBE producir un KPI conjunto de ambos bloques ni inventar minutos para ponderar el informe. DEBE mostrar cobertura y cantidades con el mismo alcance de selección, conservando pendientes sin atribuir como total separado estable ante filtros de atribución.
- **FR-015**: DEBE añadir pastel/anillo de Disponible/Ocupado/Con falla/Fuera de línea en detalle y dashboard, con orden y leyenda textual consistentes, duraciones y cobertura. El dashboard usa solo mediciones incluidas; el detalle respeta la revisión visible según el rol.
- **FR-016**: Un pastel parcial DEBE rotular «Distribución de tiempos informados» y declarar los estados faltantes. Sus porciones representan solo el total conocido; NO DEBE presentarse como porcentaje de la ventana completa. Sin suma conocida positiva, mostrar explicación y leyenda sin círculo artificial.
- **FR-017**: DEBE conservar identidad visual, estructura de pantallas, colores, tipografías, temas y componentes de SARC; corregir únicamente lo necesario para el flujo aprobado, sin rediseñar módulos ajenos. Mantener accesibilidad WCAG 2.2 AA y los cinco anchos de referencia de 005.
- **FR-018**: PDF y Excel DEBEN usar los mismos datos/filtros/corte que el dashboard y distinguir métodos, referencia y horarios desconocidos. La petición de pastel en dashboard/detalle no exige añadir nuevos gráficos a las exportaciones.
- **FR-019**: DEBE mantener permisos y unión de roles de 005, auditoría, versiones, exclusiones reversibles y reportes archivados inmutables; no exponer UID/PUK, OCPP o valores operativos restringidos. Consumo y los demás módulos conservan su comportamiento.
- **FR-020**: Las mediciones procedentes de Excel semanales importados por 005 con fechas y horas asignadas que no estaban en el archivo DEBEN pasar a «Informe sin horario real», con su semana de referencia y el promedio del informe aplicable en consultas y reportes nuevos. DEBEN conservar porcentajes, validaciones, originales e historial y dejar trazado el cambio, sin exigir una nueva validación por el solo cambio de clasificación temporal. Los horarios asignados se conservan como antecedente, no como límites observados. Si esas mediciones se presentaban como semanas exactas por aquellos horarios asignados, DEBEN dejar de computar como semanas oficiales en las consultas nuevas. Esto no reclasifica capturas con límites realmente conocidos y verificados. Los reportes ya emitidos permanecen intactos. Ante identidad/procedencia ambigua o conflicto entre registros, DEBE requerirse conciliación explícita, sin fusionar, borrar ni elegir un registro automáticamente.

### Key Entities *(include if feature involves data)*

- **Medición con horario real**: consulta de un conector con sus límites observados y estados; mantiene referencia del informe independiente y la validación vigente.
- **Medición del informe semanal sin horario**: datos de un conector/fuente/ventana de 7 días/semana de referencia, porcentaje de fuente y estados conocidos; no afirma un intervalo observado.
- **Semana de referencia**: semana que clasifica el informe remitido los lunes, sin acreditar desde/hasta reales.
- **Composición de estados**: duraciones informadas y cobertura de información; distingue falta de datos y cero sin reconstruir lo ausente.
- **Promedio del informe**: media aritmética de porcentajes incluidos de sus conectores, distinta del agregado por minutos de capturas reales.
- **Corrección y conciliación**: nueva decisión trazada que conserva antecedentes y evita sobrescritura o atribución dudosa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El ejemplo de 9815/31/0/234 minutos presenta 97,68 % y conserva ambos límites hasta el segundo; capturar otro conector no altera esos datos.
- **SC-002**: En el 100 % de importaciones del modelo de dos hojas sin horarios, se conservan ambas hojas y cero límites reales inventados; Ocupado ausente nunca se presenta como 0.
- **SC-003**: Reimportar una lectura idéntica produce cero mediciones e indicadores duplicados; el 100 % de diferencias requiere corrección y conserva la vigente hasta validación.
- **SC-004**: El informe con porcentajes incluidos 0 y 100 presenta 50,00 %, sin minutos ficticios; añadir una pendiente no cambia el promedio.
- **SC-005**: Para todas las selecciones de prueba, las clases con/sin horario permanecen separadas y dashboard/PDF/Excel coinciden en cifras y cobertura del mismo corte.
- **SC-006**: Detalle y dashboard distinguen cuatro estados, nulos/0 y completitud; ninguna gráfica parcial afirma representar 168 horas completas.
- **SC-007**: Las pantallas afectadas mantienen legibilidad y acciones accesibles a 360, 390, 768, 1024 y 1440 píxeles en ambos temas, sin superposiciones ni dependencia exclusiva del color.
- **SC-008**: El 100 % de pruebas de cinco roles y combinación operativa conserva restricciones de lectura/escritura y los reportes previos mantienen contenido/corte tras cambios posteriores.
- **SC-009**: Las regresiones de 001–005 conservan sus resultados no afectados, sin cambios de reglas de Consumo, inventario o permisos.
- **SC-010**: El 100 % de mediciones identificadas inequívocamente como Excel de 005 con horario asignado pasa al apartado sin horario real, conservando porcentajes, validaciones y antecedentes; cero conflictos se resuelven por fusión/borrado/elección automática y cero reportes ya emitidos cambian. Sus horarios asignados no aportan a indicadores oficiales nuevos.

## Assumptions

- Dependencia: Feature-005 entregada, inventario y conectores existentes, identidades externas verificadas y modelo semanal de Excel ya contrastado. No se requieren accesos a Enerlink ni automatización del proveedor para definir esta feature.
- Enerlink sigue ofreciendo ventanas móviles 24 h/7 d/30 d y no confirma un filtro personalizado exacto. El futuro filtro, automatización o importación exclusiva de archivos requerirá otro cambio; no se implementa anticipadamente.
- Las capturas previas sin referencia conservan consulta por su intervalo real; no se les asigna una semana retroactiva por proximidad de fechas.
- Las restricciones temporales y de calidad vigentes siguen para datos que sí permiten comprobarlas; horario desconocido no es permiso para inventar un intervalo o un peso de cálculo.
- La conservación del diseño/arquitectura es una restricción explícita del usuario; esta especificación define comportamiento sin decidir tablas, migraciones, servicios, endpoints o librerías.
- T124 humana y los límites de rendimiento de 005 no se presentan como pruebas superadas ni se resuelven por cambiar la numeración.
- Constitución 1.2.0: principios I–III, D05/D06/D07/D09/D12 aplicables. La excepción D09 ya permite ambas hojas de este informe; no requiere ampliación. Las excepciones aprobadas de horario y promedio se registran en el control de cambios del PRD, con referencia a PRD v1.1 §8.1/8.2. La transición de FR-020 fue aprobada explícitamente en esta ronda de aclaración.
- Fuera de alcance: reimplementar 005, rediseño visual general, sincronización/control remoto de Enerlink, reporte mensual como fuente semanal exacta, cambios en Consumo, restaurar/sobrescribir informes archivados o implementar un filtro futuro del proveedor.
