# Auditoría de decisiones de producto implícitas

Fecha: 29 de octubre de 2026. Código base: `41ffb3fa1073bc749d6a139a0ed4a3b11c8a9751`, con los cambios locales existentes al iniciar la revisión.

## Propósito y criterio

Esta auditoría identifica **14 decisiones de producto materializadas en el código cuyo alcance concreto no encontré aprobado explícitamente en los documentos disponibles**. No es una búsqueda de bugs, una evaluación de vulnerabilidades ni una propuesta de refactorización.

Se contrastaron backend Java 25/Spring Boot 4, frontend Angular 22, migraciones PostgreSQL, PRD v1.1, TRD v1.0 y ADR 0001–0008. El alcance funcional es lo implementado: RF-001, RF-002, RF-003 y sus capacidades transversales. Los módulos futuros no se valoran como funcionalidades ausentes.

Una regla escrita en un comentario o una prueba demuestra qué comportamiento se implementó; por sí sola no demuestra que producto haya elegido esa política. Una regla descrita expresamente en PRD, TRD o ADR sí se considera documentada. Cuando un documento fija la regla general pero no sus casos límite, se identifica precisamente el detalle pendiente.

**Límite de la conclusión:** «sin decisión explícita localizada» no significa que nunca se haya conversado. No se revisaron acuerdos externos al material disponible. Los supuestos siguientes son inferencias sobre el comportamiento, no afirmaciones sobre las intenciones de quien programó.

Se realizó lectura estática. No se ejecutaron importaciones, consultas sobre la base local ni pruebas funcionales para esta auditoría. El único entregable es este documento; no se modificaron fuentes ni reglas.

## Decisiones para validar

### D01. El correo de una cuenta no puede cambiar

- **Dónde vive:** [UserAccount.java:35][d01-modelo] declara `email` con `updatable = false`. [UpdateUserRequest.java:14][d01-api] permite editar nombre, roles y estado, pero no correo.
- **Decisión implementada:** el correo elegido al crear la cuenta queda fijo durante toda su vida.
- **Supuesto inferido:** los cambios de dirección corporativa y los errores de captura se resolverán fuera de la edición ordinaria; no hace falta una operación para cambiar el correo conservando la cuenta.
- **Qué está documentado y qué falta:** PRD RF-001.1 y RF-001.2 exigen correo único y edición administrativa. No concretan si el correo es una identidad inmutable o una credencial reemplazable. El UUID como identidad estable sí está documentado en ADR 0001, pero eso no obliga a inmovilizar el correo.
- **Otra opción razonable:** permitir al administrador cambiarlo conservando UUID, autoría e históricos, con comprobación de unicidad, auditoría y revocación de sesiones.
- **Pregunta de producto:** si una persona cambia de correo, ¿continúa con la misma cuenta o debe abrirse otra?

### D02. «Contraseña temporal» significa cambio obligatorio, sin plazo propio de vencimiento

- **Dónde vive:** [UserAccount.java:118][d02-modelo] y [V001__init_roles_usuarios.sql:15][d02-sql]. La temporalidad se representa con `mustChangePassword`; el modelo no guarda una fecha de expiración de esa credencial.
- **Decisión implementada:** mientras no sea sustituida, restablecida de nuevo o se desactive la cuenta, la contraseña temporal no tiene una caducidad independiente por antigüedad.
- **Supuesto inferido:** basta con restringir el acceso hasta el cambio de contraseña; no es necesario limitar cuánto tiempo puede permanecer pendiente la entrega o el primer ingreso.
- **Qué está documentado y qué falta:** PRD RF-001.8 y RNF-SEG-03, y TRD §07/§16, establecen restablecimiento administrativo y cambio obligatorio. No definen una duración máxima de la credencial temporal. La expiración de sesión de 60 minutos es una política distinta.
- **Otra opción razonable:** hacer que la credencial expire después de un plazo aprobado; tras vencer, otro restablecimiento administrativo sería necesario.
- **Pregunta de producto:** ¿cuánto tiempo puede permanecer utilizable una contraseña temporal que todavía no se ha cambiado?

### D03. Un administrador siempre necesita a otro para desactivarse o quitarse su rol

- **Dónde vive:** [UserManagementService.java:106][d03-roles] y [UserManagementService.java:117][d03-estado].
- **Decisión implementada:** se impide retirar el propio rol `ADMINISTRADOR` y desactivar la propia cuenta, aunque existan otros administradores activos.
- **Supuesto inferido:** la separación entre quien solicita y quien ejecuta su propia salida administrativa debe ser obligatoria en todos los casos.
- **Qué está documentado y qué falta:** el PRD autoriza al administrador a gestionar roles y estados. No especifica una regla de autogestión ni si la protección debe aplicarse a cualquier administrador o únicamente al último disponible.
- **Otra opción razonable:** permitir la autodesactivación o renuncia al rol cuando otro administrador activo conserve el acceso, con una confirmación explícita; bloquear únicamente la pérdida del último administrador.
- **Pregunta de producto:** ¿se exige intervención de otra persona o solo garantizar que quede administración disponible?

### D04. Serie y UID/PUK se consideran equivalentes sin distinguir mayúsculas

- **Dónde vive:** [Equipment.java:264][d04-normalizacion]. `normalizeIdentifier` recorta espacios de los extremos y convierte a mayúsculas; `applyDetails` utiliza ese resultado para serie y UID/PUK.
- **Decisión implementada:** `ab-12` y `AB-12` representan el mismo identificador; además, se conserva su forma normalizada en el equipo.
- **Supuesto inferido:** las mayúsculas no tienen significado para ninguno de los fabricantes ni para los valores agrupados en el campo UID/PUK.
- **Qué está documentado y qué falta:** PRD RF-002.2 y TRD §06.3 establecen unicidad global. No fijan la equivalencia entre mayúsculas y minúsculas ni si debe conservarse la escritura exacta del fabricante. La normalización del correo sí se prescribe aparte en TRD §07.
- **Otra opción razonable:** conservar el valor original y aplicar una comparación definida por clase de identificador o proveedor; por ejemplo, serie sin distinción de mayúsculas y credencial UID/PUK literal cuando corresponda.
- **Pregunta de producto:** ¿qué identificadores son códigos legibles normalizables y cuáles deben conservarse literalmente?

### D05. Las identidades externas se concilian ignorando tildes y espacios repetidos

- **Dónde vive:** [ExternalKey.java:18][d05-clave] y [EquipmentDirectoryService.java:58][d05-conciliacion].
- **Decisión implementada:** para vincular equipos se comparan ámbito e ID externo sin distinguir mayúsculas, marcas diacríticas ni espacios consecutivos. Por ejemplo, `ESTACIÓN 4` y `Estacion 4` resultan equivalentes.
- **Supuesto inferido:** las diferencias de escritura de esas claves son variaciones de presentación, no identidades distintas de la plataforma.
- **Qué está documentado y qué falta:** PRD RF-002.10 y RF-003.7 exigen identidades confiables y conciliación sin atribución arbitraria. ADR 0005 define las claves de búsqueda, pero no esta equivalencia lingüística. No se cuestiona la verificación previa de la identidad, sino cómo se decide que la fila importada coincide con ella.
- **Otra opción razonable:** usar la equivalencia flexible solo para sugerir candidatos y exigir confirmación si la clave literal no coincide; registrar después un alias verificado.
- **Pregunta de producto:** ¿una coincidencia por normalización autoriza la vinculación automática o solo una sugerencia?

### D06. La posición del conector determina su continuidad histórica, incluso al cambiar tipo o reactivarse

- **Dónde vive:** [Equipment.java:185][d06-equipo], [Connector.java:49][d06-conector] y [V004__inventario_equipos.sql:83][d06-sql].
- **Decisión implementada:** la posición existente conserva su UUID. Asignarle otro tipo o volver a incluir una posición retirada reactiva el mismo conector; no distingue esas acciones de un reemplazo físico.
- **Supuesto inferido:** una posición de la electrolinera representa una identidad estable suficiente para el historial, aunque cambie la pieza o su tipo.
- **Qué está documentado y qué falta:** PRD RF-002.4 exige registrar cada conector físico; TRD fija ordinales y ADR 0004 conserva conectores retirados. Falta precisar cuándo un cambio de tipo o una reincorporación es una corrección del mismo conector y cuándo representa otra pieza física.
- **Otra opción razonable:** separar corrección de tipo, reactivación y reemplazo físico; este último crea un UUID nuevo con referencia al anterior y vigencia por posición.
- **Pregunta de producto:** ¿el histórico pertenece a la posición del equipo o a la pieza física instalada en cada etapa?

### D07. La relación de reemplazo permite varios sucesores para el mismo equipo

- **Dónde vive:** [EquipmentService.java:66][d07-servicio] registra el equipo nuevo con el antecedente indicado. [V004__inventario_equipos.sql:43][d07-sql] guarda una FK `replaces_equipment_id` sin unicidad sobre el antecedente.
- **Decisión implementada:** cada equipo nuevo puede indicar un antecesor, pero el modelo no limita a uno los equipos que declaran reemplazar al mismo antecesor. El alta tampoco exige que el antecesor carezca de sucesores.
- **Supuesto inferido:** el enlace describe una procedencia que puede ramificarse, o no es necesario imponer que la cadena de reemplazos sea lineal.
- **Qué está documentado y qué falta:** PRD RF-002.9 y ADR 0004 ya deciden que un reemplazo físico crea otro equipo y conserva el anterior. No establecen la cardinalidad de esa relación ni si se permite que una instalación sea sustituida por varias.
- **Otra opción razonable:** exigir una cadena lineal de sustituciones; si existen divisiones o ampliaciones, representarlas con una operación diferente y una relación específica.
- **Pregunta de producto:** ¿«reemplazar» es siempre una sustitución uno a uno o puede representar una transformación uno a varios?

### D08. Una excepción de calendario permite cerrar el día o definir un único intervalo

- **Dónde vive:** [ScheduleException.java:13][d08-modelo], [Equipment.java:232][d08-regla] y [V004__inventario_equipos.sql:109][d08-sql].
- **Decisión implementada:** solo existe una excepción por equipo y fecha, con un único par apertura/cierre o la marca de cerrado. No puede expresarse en esa excepción una jornada partida, por ejemplo 08:00–12:00 y 14:00–18:00.
- **Supuesto inferido:** los días especiales necesitan un calendario más simple que los días de la semana ordinaria.
- **Qué está documentado y qué falta:** PRD RF-002.5 y TRD §08 definen varios intervalos y excepciones que prevalecen. No aclaran si las excepciones heredan toda la capacidad de intervalos del horario semanal o un subconjunto.
- **Otra opción razonable:** que una excepción sustituya el día por una lista de intervalos, incluida una lista vacía para cierre completo.
- **Pregunta de producto:** ¿los feriados, mantenimientos y aperturas especiales pueden necesitar varios tramos en la misma fecha?

### D09. En un Excel, solo la primera hoja cuenta como entrada

- **Dónde vive:** [TabularReader.java:107][d09-lector], mediante `workbook.getSheetAt(0)`. Es el lector compartido por importación de equipos y consumos.
- **Decisión implementada:** las demás hojas no participan en la carga; tampoco se solicita al usuario elegir una hoja.
- **Supuesto inferido:** los datos relevantes siempre estarán en la primera pestaña. Las otras serán instrucciones, catálogos u hojas ajenas a la importación.
- **Qué está documentado y qué falta:** PRD RF-003.1 admite archivos originales; ADR 0007 decide usar una plantilla de equipos. Ninguno establece una política general para libros con varias hojas. Ubicar columnas por encabezado no equivale a elegir la hoja que contiene esos encabezados.
- **Otra opción razonable:** localizar hojas compatibles y pedir elección cuando exista más de una; para equipos, identificar expresamente la hoja de datos de la plantilla.
- **Pregunta de producto:** ¿se importa una hoja por archivo, todas las compatibles o la seleccionada por la persona?

### D10. Una sesión de más de 1.000 kWh se rechaza como dato inválido

- **Dónde vive:** [SessionRules.java:18][d10-limite] y [SessionRules.java:31][d10-validacion]. El límite produce `ENERGY_OUT_OF_RANGE`.
- **Decisión implementada:** la energía normalizada superior a 1.000 kWh no pasa la validación normal, independientemente de fuente, duración o equipo. No existe una aprobación de excepción para superar este techo. La normalización previa se describe en D11.
- **Supuesto inferido:** todo valor superior a ese umbral necesariamente es un error del origen; la explicación del comentario lo refiere a una carga de un vehículo.
- **Qué está documentado y qué falta:** el PRD define unidad, interpretación y validación, pero no ese máximo. TRD §09 tampoco establece un umbral de negocio de 1.000 kWh. La capacidad del tipo `NUMERIC` no obliga a elegirlo.
- **Otra opción razonable:** convertirlo en observación revisable o aplicar umbrales configurables por clase de equipo, dejando trazabilidad de las excepciones admitidas.
- **Pregunta de producto:** ¿es un límite contractual de las fuentes actuales o una heurística que debe permitir revisión humana?

### D11. Se redondea cada lectura antes de comparar correcciones y sumar indicadores

- **Dónde vive:** [SessionReading.java:19][d11-redondeo] aplica `HALF_UP` a tres decimales al construir la lectura; [SessionReading.java:27][d11-comparacion] compara posteriormente esos valores. La persistencia usa `NUMERIC(12,3)` en [V005__consumo_energia.sql:87][d11-sql].
- **Decisión implementada:** el instante y método del redondeo forman parte de la identidad numérica de la lectura. Dos valores originales distintos que redondean igual pueden considerarse iguales; los totales suman las lecturas ya redondeadas.
- **Supuesto inferido:** la resolución de 0,001 kWh es suficiente también para decidir si una reimportación contiene una corrección, y es preferible redondear por sesión antes que al cerrar el agregado.
- **Qué está documentado y qué falta:** **la escala de tres decimales sí tiene respaldo en TRD §03.3, p. 4**, que propone `NUMERIC(14,3)` o precisión confirmada con archivos reales. El detalle no localizado es elegir `HALF_UP`, aplicarlo antes de comparar y usar la suma de redondeos como total; no se presenta la escala por sí sola como decisión ausente.
- **Otra opción razonable:** conservar precisión adicional para comparación y cálculo, y redondear a tres decimales en la presentación o cierre aprobado del agregado; documentar una tolerancia de equivalencia para correcciones.
- **Ejemplo de la elección:** dos lecturas de `0,0006 kWh` dan `0,002 kWh` al redondear cada una; la suma exacta redondeada al final da `0,001 kWh`. Es una diferencia de convención, no una afirmación de fallo aritmético.
- **Pregunta de producto:** ¿con qué precisión se acepta una corrección y en qué nivel se redondea el dato oficial?

### D12. El histórico vivo muestra el nombre y código actuales del equipo

- **Dónde vive:** [SessionQueryService.java:36][d12-consulta] resuelve los equipos durante la consulta. [EquipmentDirectoryService.java:90][d12-directorio] construye la referencia con `getName()` y `getAssignedCode()` vigentes. El ranking utiliza el mismo directorio.
- **Decisión implementada:** cambiar nombre o código del equipo cambia las etiquetas con las que se consultan hoy sesiones de períodos anteriores. No se muestra automáticamente la etiqueta vigente cuando ocurrió la sesión.
- **Supuesto inferido:** la navegación histórica debe usar el catálogo actual para facilitar reconocimiento y búsqueda de los equipos.
- **Qué está documentado y qué falta:** PRD RF-002.9 y RF-003.15, y ADR 0004/0005, fijan identidad estable y métricas por UUID. No determinan si la etiqueta histórica debe ser contemporánea al hecho o actual. **Esto no afecta la copia de un reporte ya archivado**, cuya conservación está decidida en ADR 0008.
- **Otra opción razonable:** mostrar etiqueta de la fecha del hecho junto al nombre actual, o permitir escoger entre vista histórica y catálogo vigente.
- **Pregunta de producto:** ¿un informe nuevo sobre un mes pasado debe usar los nombres actuales o los que tenía el inventario al cierre de ese mes?

### D13. El período ejecutivo inicial es el mes en curso, incluido hoy

- **Dónde vive:** [consumption-dashboard-facade.ts:26][d13-tablero] y [sessions-facade.ts:27][d13-sesiones] inicializan `thisMonth`. [period.ts:36][d13-periodo] lo calcula desde el primer día hasta el día siguiente a hoy, con fin exclusivo.
- **Decisión implementada:** al entrar en el tablero o en sesiones se consulta inicialmente el mes corriente, incluyendo el día todavía en curso. Los accesos rápidos se recalculan cuando cambia el día operativo.
- **Supuesto inferido:** la primera necesidad gerencial es seguimiento del mes abierto; es aceptable que el período inicial incluya información del día que aún puede seguir llegando.
- **Qué está documentado y qué falta:** PRD RF-003.13 exige selección de período y los documentos definen rangos inclusivo/exclusivo. No eligen el período inicial ni si el seguimiento ejecutivo debe empezar por períodos cerrados o abiertos.
- **Otra opción razonable:** iniciar en el último mes cerrado, en los últimos siete días completos o en un período predeterminado por usuario, mostrando explícitamente si el período sigue abierto.
- **Pregunta de producto:** ¿la primera vista debe responder «cómo vamos este mes» o «cómo cerró el último período completo»?

### D14. El Top 5 corta los empates usando el UUID del equipo

- **Dónde vive:** [EnergySessionRepositoryAdapter.java:178][d14-orden] ordena por energía descendente y UUID ascendente; [EnergySessionRepositoryAdapter.java:181][d14-tope] limita el número de resultados. `ConsumptionCalculator.TOP_SIZE` vale cinco.
- **Decisión implementada:** se muestran como máximo cinco equipos; si varios empatan en energía en el límite, entran los primeros según un identificador interno sin significado operativo.
- **Supuesto inferido:** mantener cinco filas y un orden determinista es más importante que representar conjuntamente a todos los equipos empatados.
- **Qué está documentado y qué falta:** PRD RF-003.14 y TRD §09.2 exigen Top 5 por energía. No definen el desempate ni el tratamiento de un empate en la quinta posición.
- **Otra opción razonable:** incluir todos los empatados en el quinto puesto, indicando que hay más de cinco equipos; o aprobar un desempate con significado, como número de sesiones y después código visible.
- **Pregunta de producto:** ¿el ranking tiene cinco filas estrictas o cinco posiciones que pueden contener empates?

## Reglas explícitas que se descartaron como hallazgos de esta auditoría

Estas elecciones ya tienen respaldo escrito y no deben volver a presentarse como decisiones implícitas:

| Regla | Respaldo localizado |
| --- | --- |
| Contar una sesión por su fecha de inicio, incluso si termina otro día; contar 0 kWh válidos | PRD RF-003.10; ADR 0005 |
| Fuente preferente según red y tipo; datos pendientes y de prueba separados del oficial | PRD §7.2 y RF-003.17; ADR 0005/0008 |
| Exigir reasignación explícita cuando cambia cargador y omitir una revisión ya obsoleta | ADR 0008, decisiones 1 y 2; la conciliación explícita también fue confirmada en esta conversación |
| Procesar válidas y preservar resultados parciales en importaciones | PRD §3.1 y §13.2; TRD §09.3; ADR 0005 |
| Una celda vacía conserva el dato existente; horario e imagen fuera de la plantilla | ADR 0007, decisiones 4 y 5 |
| Verificar identidades externas al confirmar la carga de equipos | ADR 0007, decisión 6 |
| Red y tipo del equipo inmutables; nuevo equipo para un reemplazo físico | ADR 0005; PRD RF-002.9 y ADR 0004 |
| Límites de 5.000 equipos por importación/reporte, 100.000 sesiones por reporte y períodos de hasta 366 días | ADR 0004, 0005 y 0007 |
| Archivar reportes, leerlos con instantánea consistente y detener la emisión ante falla del almacenamiento configurado | ADR 0008, decisiones 4 y 5, en su versión actual |
| Consultas automáticas condicionadas a interacción reciente | ADR 0008, consecuencias, en su versión actual |
| Serializar filtros en query params | TRD §05, p. 7. Su existencia como requisito no permite tratar una implementación distinta como ausencia de decisión |

## Uso del documento

Todas las entradas D01–D14 quedan **pendientes de validación de producto**, sin calificación de gravedad técnica. Las alternativas muestran que existía otra elección razonable; no constituyen instrucciones para implementarla.

Para resolver cada entrada, registrar la opción aprobada, alcance, fecha y motivo en un requisito o ADR. Si se conserva el comportamiento actual, también conviene expresarlo con un ejemplo de aceptación. Antes de cambiar una política de identidad o precisión, considerar los datos históricos que ya dependan de ella.

Fuentes documentales: [PRD v1.1][prd], [TRD v1.0][trd] y [ADR del backend][adrs]. Las referencias a código apuntan a las líneas verificadas durante esta revisión.

[d01-modelo]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/identity/domain/model/UserAccount.java:35
[d01-api]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/identity/api/request/UpdateUserRequest.java:14
[d02-modelo]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/identity/domain/model/UserAccount.java:118
[d02-sql]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/resources/db/migration/V001__init_roles_usuarios.sql:15
[d03-roles]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/identity/application/service/UserManagementService.java:106
[d03-estado]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/identity/application/service/UserManagementService.java:117
[d04-normalizacion]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/domain/model/equipment/Equipment.java:264
[d05-clave]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/shared/catalog/ExternalKey.java:18
[d05-conciliacion]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/EquipmentDirectoryService.java:58
[d06-equipo]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/domain/model/equipment/Equipment.java:185
[d06-conector]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/domain/model/equipment/Connector.java:49
[d06-sql]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/resources/db/migration/V004__inventario_equipos.sql:83
[d07-servicio]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/EquipmentService.java:66
[d07-sql]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/resources/db/migration/V004__inventario_equipos.sql:43
[d08-modelo]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/domain/model/equipment/ScheduleException.java:13
[d08-regla]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/domain/model/equipment/Equipment.java:232
[d08-sql]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/resources/db/migration/V004__inventario_equipos.sql:109
[d09-lector]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/shared/importing/reading/TabularReader.java:107
[d10-limite]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/policy/SessionRules.java:18
[d10-validacion]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/policy/SessionRules.java:31
[d11-redondeo]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/model/session/SessionReading.java:19
[d11-comparacion]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/model/session/SessionReading.java:27
[d11-sql]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/resources/db/migration/V005__consumo_energia.sql:87
[d12-consulta]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/SessionQueryService.java:36
[d12-directorio]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/EquipmentDirectoryService.java:90
[d13-tablero]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-frontend/src/app/features/consumption/application/consumption-dashboard-facade.ts:26
[d13-sesiones]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-frontend/src/app/features/consumption/application/sessions-facade.ts:27
[d13-periodo]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-frontend/src/app/features/consumption/domain/period.ts:36
[d14-orden]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/EnergySessionRepositoryAdapter.java:178
[d14-tope]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/EnergySessionRepositoryAdapter.java:181
[prd]: C:/Users/Admin/Desktop/Proyectos/SARC/documents/PRD_Sistema_Administrativo_Reportes_Carga_v1_1_DEFINITIVO.pdf
[trd]: C:/Users/Admin/Desktop/Proyectos/SARC/documents/TRD_Sistema_Administrativo_Reportes_Carga_v1_0_REVISION_TECNICA-1.pdf
[adrs]: C:/Users/Admin/Desktop/Proyectos/SARC/sarc-bakend/docs/adr
