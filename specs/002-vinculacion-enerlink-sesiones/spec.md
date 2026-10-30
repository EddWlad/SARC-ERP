# Feature Specification: Vinculación Enerlink y sesiones incompletas

**Feature**: `002-vinculacion-enerlink-sesiones`

**Created**: 2026-10-30

**Status**: Draft — revisión documental completa; pendiente de aprobación funcional y planificación.

**Input**: Feature 002 solicitada por el responsable de SARC: registrar identidades Enerlink desde la carga masiva de inventario, vincular sesiones por coincidencia única verificada y admitir sesiones iniciadas sin energía o término informados, conservando unicidad, trazabilidad e históricos.

## Clarifications

### Session 2026-10-30

- Q: Si un equipo ya tiene identidad Enerlink verificada y la plantilla trae otro par ACTIVO/CARGADOR distinto para ese equipo, ¿se agrega como segunda identidad o se trata como conflicto? → A: Conflicto de fila: se informa el motivo y el par nuevo no se verifica; agregarlo exige una acción explícita en «Identidades externas».
- Q: Cuando una fila de inventario tiene par Enerlink incompleto o en conflicto, ¿se bloquea toda la fila o se aplican sus demás datos y solo se omite la identidad? → A: Se bloquea la fila completa: no se crea ni actualiza el equipo, la fila queda marcada con su motivo y las demás filas del lote continúan.
- Q: Una sesión Enerlink con inicio válido pero sin ACTIVO y sin CARGADOR, o sin CARGADOR, ¿debe quedar pendiente de vinculación o rechazarse como inválida? → A: Pendiente en todo caso (par parcial o ambos vacíos), con motivo «Identidad Enerlink incompleta».
- Q: En el ranking Top 5 por energía, ¿qué pasa con un equipo cuyas sesiones del período no tienen ninguna energía informada? → A: Se excluye del ranking y se muestra una nota con cuántos equipos quedaron fuera por no tener energía informada.
- Q: Si una sesión ya pendiente se reimporta y su par ahora corresponde a un único equipo verificado, ¿se vincula al confirmar ese lote o sigue pendiente hasta ejecutar «vincular pendientes»? → A: Se vincula al confirmar el lote: la vista previa lo muestra, queda trazado y respeta aprobación de fuente, pruebas y observaciones.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar y completar identidades Enerlink (Priority: P1)

Como persona autorizada para importar inventario, quiero que «Enerlink activo» y «Enerlink cargador» registren la identidad externa del equipo, para consultarla en «Identidades externas» y utilizarla en consumo.

**Why this priority**: Una identidad confiable permite atribuir consumos al equipo correcto.

**Independent Test**: Importar un equipo nuevo y completar otro existente; comprobar la identidad visible y la conservación de información.

**Acceptance Scenarios**:

1. **Given** un equipo nuevo con ambos valores `JAC PUMA TUMBACO`, **When** se confirma su carga masiva válida sin conflictos, **Then** queda una identidad Enerlink verificada y visible, con ACTIVO y CARGADOR asociados al equipo creado.
2. **Given** un equipo existente identificado inequívocamente por las reglas de inventario, **When** se confirma una importación que únicamente completa su identidad Enerlink, **Then** se conserva el mismo equipo, código, serie, conectores, demás datos y sesiones históricas; no se crea otro equipo.
3. **Given** una identidad ya asociada al mismo equipo, **When** se importa nuevamente el mismo par, **Then** no se generan identidades duplicadas.
4. **Given** un par con un solo componente, que entra en conflicto con otro equipo o distinto de la identidad Enerlink verificada del propio equipo, **When** se revisa la importación, **Then** se informa la fila, los campos y el motivo; esa fila no crea ni actualiza el equipo, no se verifica ni reasigna esa identidad y las demás filas del lote continúan.
5. **Given** un equipo con identidades registradas, **When** ambas columnas Enerlink llegan vacías, **Then** no se eliminan ni desverifican sus identidades existentes.

---

### User Story 2 - Vincular sesiones por identidad confiable (Priority: P1)

Como analista autorizado, quiero que una identidad verificada y única vincule automáticamente las sesiones y que los casos sin resolución expliquen su motivo.

**Why this priority**: Reduce conciliaciones manuales sin atribuir consumos arbitrariamente.

**Independent Test**: Preparar identidades verificadas e importar cinco sesiones, además de casos sin coincidencia, no verificados y ambiguos.

**Acceptance Scenarios**:

1. **Given** un único equipo con identidad verificada `JAC PUMA TUMBACO` / `JAC PUMA TUMBACO`, **When** se importan y confirman cinco sesiones distintas con ese par y demás datos válidos, **Then** las cinco se vinculan al mismo equipo sin selección manual del cargador.
2. **Given** esa identidad, **When** la escritura difiere solo en mayúsculas, tildes o espacios repetidos o exteriores, **Then** se vincula automáticamente si la coincidencia normalizada sigue siendo única.
3. **Given** dos equipos candidatos para el mismo par normalizado, **When** se importa una sesión, **Then** queda pendiente sin atribución automática y muestra «Identidad Enerlink ambigua», con información suficiente para conciliar.
4. **Given** una identidad no registrada, no verificada o un par incompleto (un componente o ambos vacíos), **When** se importa una sesión por lo demás válida, **Then** queda pendiente con el motivo específico; no se elige equipo por parecido ni por un solo componente.
5. **Given** sesiones pendientes y una identidad posteriormente completada y verificada, **When** se ejecuta la operación autorizada de vincular pendientes, **Then** se vinculan las coincidencias únicas, permanecen las ambiguas y se respetan las aprobaciones de fuente antes de sumar indicadores oficiales.

---

### User Story 3 - Contabilizar sesiones iniciadas con datos incompletos (Priority: P1)

Como analista, quiero registrar sesiones con inicio válido aunque falten energía o término, distinguiendo información ausente y mediciones conocidas.

**Why this priority**: Una medición final ausente no implica que la sesión no existió ni que consumió cero.

**Independent Test**: Importar esta matriz con sesiones distintas, inicio válido en el mismo período, identidad verificada y requisitos restantes satisfechos.

| Caso | Energía informada | Término informado | Resultado esperado |
| --- | --- | --- | --- |
| A | Vacía | Vacío | Una sesión; energía y término no informados; duración no calculada |
| B | Vacía | Posterior al inicio | Una sesión; energía no informada; término conservado y duración calculable entre fechas |
| C | 0 kWh | Vacío | Una sesión; energía conocida de 0 kWh; sin término ni duración calculada |
| D | 2,500 kWh | Vacío | Una sesión; energía conocida de 2,500 kWh; sin término ni duración calculada |
| E | 1,250 kWh | Posterior al inicio | Una sesión; energía conocida de 1,250 kWh y término conservado |

**Acceptance Scenarios**:

1. **Given** la matriz A–E, **When** se confirma, **Then** se aceptan cinco sesiones, dos con energía no informada y tres con energía conocida, incluida la de cero; la suma conocida es 3,750 kWh.
2. **Given** esas sesiones elegibles para indicadores oficiales, **When** se consulta el mismo período y filtros en tablero, histórico e informes nuevos, **Then** figuran cinco sesiones y 3,750 kWh conocidos; se distingue cero de ausencia y se informa que dos sesiones no tienen medición energética.
3. **Given** un período con únicamente sesiones elegibles sin energía informada, **When** se consulta o exporta, **Then** se informa el número de sesiones y energía «Sin datos», en lugar de presentar 0 kWh como medición completa.
4. **Given** una fila con inicio ausente o ilegible, energía informada inválida o término anterior al inicio, **When** se importa, **Then** se informa su motivo de invalidez; tolerar vacíos no autoriza datos contradictorios.
5. **Given** una sesión sin término, **When** se consulta, **Then** se indica «No informado» sin presentarla como finalizada ni presumir que sigue activa en tiempo real.

---

### User Story 4 - Reimportar y corregir con trazabilidad (Priority: P1)

Como analista, quiero completar sesiones sin duplicarlas ni cambiar su equipo silenciosamente, conservando las versiones anteriores.

**Why this priority**: Las importaciones sucesivas no deben inflar consumo ni perder evidencia.

**Independent Test**: Reimportar la matriz, completar una sesión y comprobar indicadores e historial; intentar un cambio de cargador y una revisión obsoleta.

**Acceptance Scenarios**:

1. **Given** cinco sesiones registradas, **When** se reimporta el mismo contenido una o varias veces, **Then** siguen existiendo cinco sesiones y los mismos indicadores; los vacíos no generan otra sesión. La única excepción es una sesión pendiente cuya identidad ya resuelva un único equipo verificado, que se vincula según FR-007 sin crear otra sesión.
2. **Given** una sesión sin energía ni término, **When** llega con la misma identidad de sesión y datos completos, **Then** se presenta como corrección del mismo registro y, tras la aprobación vigente, incorpora los datos, conserva la versión anterior y actualiza los indicadores afectados.
3. **Given** una sesión con energía o término conocidos, **When** una reimportación propone vaciarlos, **Then** se muestra el cambio y se exige el flujo de corrección aprobado; no se pierden silenciosamente y se conserva el antes y el después.
4. **Given** el mismo identificador de sesión de origen pero un par ACTIVO/CARGADOR que propone otro equipo, **When** se reimporta, **Then** una aprobación ordinaria no cambia el equipo: se exige conciliación y reasignación explícita con trazabilidad.
5. **Given** una corrección revisada y una modificación posterior de esa sesión, **When** se intenta aplicar la revisión anterior, **Then** no sobrescribe la versión posterior y se informa que la revisión quedó obsoleta.
6. **Given** reportes emitidos y sesiones históricas, **When** solo se completa la identidad Enerlink del equipo, **Then** permanecen intactos los registros históricos y las copias archivadas.

### Edge Cases

- Un mismo ACTIVO con distintos CARGADORES, o viceversa, no hace equivalentes las identidades: se evalúa el par completo.
- Dos pares distintos que coinciden tras normalizar producen conflicto entre equipos; no gana el primero encontrado.
- «Enerlink activo» representa el nombre del activo en Enerlink, no el estado activo/inactivo del equipo.
- Vacío o solo espacios significa no informado. Texto no numérico, energía negativa o fecha ilegible no se convierten en vacío ni cero.
- Un término anterior al inicio es incoherente. Igual al inicio no se rechaza solo por duración cero si satisface las demás reglas vigentes.
- Inicio y energía sin término no permiten deducir duración, potencia media o fecha final.
- Una sesión con inicio válido pero pendiente de identidad se conserva en su categoría y no suma como oficial hasta satisfacer vinculación y aprobaciones.
- Una lectura superior a 1.000 kWh conserva su tratamiento vigente, sin revisión humana hasta la feature 003; la tolerancia a vacíos no evita las demás validaciones.
- Repeticiones dentro de un lote o entre lotes respetan la identidad de sesión, aunque energía y término estén vacíos.
- Si el inventario cambia entre revisión y confirmación, no se aplica una vinculación que haya dejado de ser única y verificada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La carga masiva DEBE incorporar «Enerlink activo» / «Enerlink cargador» como identidad externa Enerlink visible del equipo tras confirmar una fila válida sin conflictos. La confirmación autorizada verifica esa identidad; la vista previa no lo hace. Una fila que solo completa identidad no exige aprobación individual. Una fila que además cambia otros datos del equipo se aprueba completa: sin aprobación no aplica ninguno de sus cambios, tampoco la identidad, y esa consecuencia DEBE advertirse antes de confirmar.
- **FR-002**: Completar una identidad DEBE identificar inequívocamente al equipo por las reglas de inventario y conservarlo sin duplicación. Si solo se completa esa identidad, NO DEBEN cambiar código, serie, conectores, demás datos ni históricos. Repetir el mismo par en el mismo equipo DEBE ser idempotente.
- **FR-003**: La identidad DEBE usar ambos componentes, conservar los originales y comparar ignorando mayúsculas, tildes y espacios repetidos o exteriores. NO DEBE vincularse por semejanza aproximada ni por un solo componente.
- **FR-004**: Pares con un solo componente o conflictivos DEBEN señalar fila y motivo en español antes de confirmar y bloquear la fila completa: esta NO DEBE crear ni actualizar el equipo; las demás filas del lote continúan. Ambos componentes vacíos NO DEBEN bloquear la fila ni borrar identidades. Un par nuevo NO DEBE retirar, sustituir o transferir silenciosamente una identidad existente. Si el equipo ya tiene una identidad Enerlink verificada y la fila trae un par normalizado distinto, DEBE tratarse como conflicto: el par nuevo no se verifica y agregarlo exige una acción explícita en «Identidades externas».
- **FR-005**: Una sesión DEBE vincularse automáticamente si el par completo corresponde a un único equipo mediante identidad verificada y sigue siendo válido al aplicar la importación. Las diferencias normalizables NO DEBEN exigir conciliación manual.
- **FR-006**: Sin coincidencia, sin verificación, con ambigüedad o con par incompleto (un componente o ambos vacíos, motivo «Identidad Enerlink incompleta»), una sesión por lo demás válida DEBE quedar pendiente con motivo específico y valores de origen disponibles para conciliar; NO DEBE invalidarse por esa sola causa ni atribuirse por orden de lectura.
- **FR-007**: Las sesiones pendientes DEBEN poder reconsiderarse por el flujo autorizado de vinculación tras completar identidades. Una sesión pendiente reimportada cuyo par corresponda a un único equipo verificado DEBE mostrarse en la vista previa y vincularse al confirmar ese lote, en el mismo registro y con trazabilidad, aunque el resto de su contenido no cambie. Vincular NO DEBE eludir permisos, aprobación de fuente no preferente, marca de prueba ni revisión de observaciones.
- **FR-008**: Una sesión con inicio válido y los demás requisitos satisfechos DEBE admitirse aunque energía, término o ambos no estén informados. Su ausencia NO DEBE ser por sí sola motivo de invalidez.
- **FR-009**: Energía ausente y energía conocida de 0 kWh DEBEN distinguirse en importación, consulta, corrección y exportación. NO DEBEN generarse lecturas sustitutivas.
- **FR-010**: Un término ausente DEBE mantenerse ausente. NO DEBE sustituirse por el inicio, la importación u otra fecha, ni deducirse duración. Con ambas fechas válidas informadas puede calcularse su diferencia según las reglas temporales vigentes.
- **FR-011**: Los valores informados DEBEN cumplir identificación, coherencia temporal y reglas energéticas vigentes. Cada lectura conocida DEBE redondearse a tres decimales de kWh mediante HALF_UP antes de comparar y sumar. Las lecturas superiores a 1.000 kWh conservan su tratamiento vigente; esta feature no incorpora la revisión humana de D10. Ausencia NO DEBE operar numéricamente como cero.
- **FR-012**: Cada sesión oficial elegible DEBE contarse una vez por su fecha operativa de inicio, incluso sin energía o término. Pendientes, pruebas y registros por aprobar conservan su tratamiento diferenciado y exclusiones oficiales.
- **FR-013**: Tablero, histórico e informes nuevos DEBEN usar el mismo universo y filtros: contar sesiones elegibles y sumar solo energía conocida, informando cuántas sesiones no tienen energía. Sin lecturas conocidas DEBEN indicar «Sin datos»; con lecturas conocidas de cero, 0 kWh. Un total parcial DEBE identificarse como energía conocida.
- **FR-014**: Indicadores derivados que necesiten energía o término NO DEBEN inventar valores ni usar ausencias como cero. DEBEN indicar su cobertura o «Sin datos» cuando falten entradas suficientes; la sesión conserva su conteo de inicio. El ranking por energía (D14) DEBE excluir equipos sin ninguna energía informada en el período e indicar cuántos quedaron fuera; un equipo con lecturas conocidas, incluso de 0 kWh, participa con su energía conocida.
- **FR-015**: Reimportaciones idénticas, reintentos y filas repetidas DEBEN preservar la prevención de duplicados por identidad de sesión de origen y ámbito vigente, sin usar energía ni término para crear otra identidad.
- **FR-016**: Completar o cambiar energía o término DEBE seguir la corrección y aprobación vigente, preservar versiones y evitar sobrescribir con revisiones obsoletas. Quitar un dato conocido DEBE ser un cambio explícito revisable.
- **FR-017**: Cambiar el equipo propuesto para una sesión existente DEBE exigir conciliación y reasignación explícita; una corrección ordinaria no lo autoriza. Se conserva el vínculo anterior en la trazabilidad.
- **FR-018**: Identidades, vinculaciones y correcciones DEBEN conservar quién actuó, cuándo, origen y valores o vínculos anteriores y posteriores según corresponda. NO DEBEN ampliarse los permisos vigentes.
- **FR-019**: La mejora DEBE limitarse a identidades e importaciones Enerlink y su presentación coherente en consumo. NO DEBE modificar identificación ni admisibilidad de otras fuentes, códigos KIA ni reglas de otras redes o módulos. Los reportes archivados NO DEBEN reescribirse.
- **FR-020**: La carga de inventario DEBE usar solo la primera hoja. Los resultados DEBEN distinguir aceptadas, pendientes, inválidas y duplicadas con sus motivos; las cifras del incidente NO DEBEN justificar aceptar filas sin revisar sus condiciones.

### Key Entities *(include if feature involves data)*

- **Equipo**: Unidad del inventario con identidad, código cuando corresponda, serie, conectores e histórico; completar identificación externa no crea otro equipo.
- **Identidad Enerlink**: Par original ACTIVO/CARGADOR, condición de verificación, equipo asociado y procedencia trazable.
- **Sesión**: Sesión identificada por origen y ámbito, con inicio, energía y término cuando estén informados, vínculo al equipo y condiciones de aprobación.
- **Resultado de importación**: Clasificación y motivos de cada fila, relacionada con un registro nuevo, existente o pendiente.
- **Corrección o conciliación**: Decisión autorizada que conserva antecedentes de cambios de datos o reasignaciones.
- **Indicador o informe**: Conteo de sesiones y valores conocidos del universo elegible, con cobertura explícita y copias emitidas preservadas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las filas de inventario aplicadas con par completo y sin conflictos, sin contar las modificadas que no se aprobaron, deja identidad verificada y visible en el equipo correcto, sin equipos adicionales al completar existentes (US1; FR-001–004).
- **SC-002**: Cinco sesiones del ejemplo se vinculan al mismo equipo sin selección manual; el 100 % de casos ambiguos de prueba permanece pendiente con motivo comprensible (US2; FR-003–007).
- **SC-003**: La matriz A–E produce cinco sesiones elegibles, tres lecturas conocidas, dos ausentes y 3,750 kWh conocidos, iguales en tablero e informes con los mismos filtros (US3; FR-008–014).
- **SC-004**: Reimportar el conjunto sin cambios dos veces adicionales produce cero sesiones, energía o indicadores adicionales, salvo sesiones pendientes cuya identidad ya resuelva un único equipo verificado, que se vinculan según FR-007 sin crear otra sesión. Completar una sesión actualiza el mismo registro con versión anterior consultable (US4; FR-015–018).
- **SC-005**: Completar solo identidades deja intactos el 100 % de códigos, series, conectores, sesiones históricas y copias archivadas de prueba (US1/US4; FR-002, FR-018–019).
- **SC-006**: El 100 % de casos de energía ausente, cero y término ausente se distingue en consulta y exportación, sin kWh, términos ni duraciones inventados; los valores inválidos conservan motivos específicos (US3 y casos límite; FR-008–014).
- **SC-007**: En una reevaluación de 1.724 filas como el incidente, todas tienen resultado explicable; ninguna se rechaza únicamente por energía o término vacíos ni queda pendiente únicamente porque no se aprovechó un par único verificado. No se exige aceptar todas sin conocer sus demás condiciones (US2/US3; FR-005–008, FR-020).

## Assumptions

- **Verificación**: Se considera confiable el par completo aportado por una persona autorizada y confirmado sin conflictos mediante carga masiva. No se requiere consultar un servicio remoto de Enerlink ni se considera verificado por simple parecido de nombres.
- **Par externo**: ACTIVO/CARGADOR no equivale a serie, código ni estado de uso. Se conservan originales y se aplica D05 a la comparación.
- **Equipos existentes**: Se reutilizan identificación y permisos vigentes. Se permite completar identidades, sin autorizar fusiones ni transferencias sin conciliación.
- **Inventario previo**: Puede completarse reimportando la plantilla y, después, ejecutando la vinculación de pendientes o reimportando las sesiones. No se presupone corregir históricos automáticamente ni recuperar filas rechazadas sin reimportar la fuente.
- **Fechas**: Se conservan zona horaria, día operativo y filtros vigentes. No se incorpora seguimiento en tiempo real ni estimación de finalización.
- **Cobertura**: Se informa cuántas sesiones carecen de energía; indicadores derivados explican su cobertura. Energía conocida no equivale a medición completa de todas las sesiones.
- **Incidente**: Las cifras de 1.583 pendientes y 83 inválidas son diagnóstico aportado por el usuario, no resultados corroborados ni clasificación obligatoria tras la mejora.
- **Dependencias y límites**: Se reutilizan revisión, aprobación de fuentes, tratamiento de pruebas, conciliación, auditoría y reportes emitidos. No se define todavía diseño técnico, almacenamiento ni interfaces.
- **Constitución**: Conformidad con I–II, D04–D05 (originales y equivalencia), D09 (primera hoja), D11 (redondeo), D12 (nombres actuales en informes nuevos, identificación histórica distinguible y copias preservadas) y D13 (período inicial). Esta feature no redefine esas reglas ni certifica su implementación actual. No requiere enmienda constitucional.
- **Pendientes constitucionales**: D10 no se cumple: una lectura superior a 1.000 kWh se rechaza, sin revisión humana. D14 no se cumple: el ranking puede excluir un empatado del quinto lugar usando un identificador interno. Esta feature no corrige ni agrava ninguna; ambas quedan como trabajo pendiente en `specs/003-revision-energia-ranking-empates/spec.md`.
