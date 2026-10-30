# Research — Feature 001

Fecha: 2026-10-29. Investigación estática, sin ejecutar pruebas ni consultar la base local.
La fuente de comportamiento objetivo es spec.md; la evidencia siguiente describe obstáculos
y capacidades del código, no modifica criterios funcionales.

## R1. Regla única en dominio

**Decision:** extender EquipmentClassification.resolveAssignedCode con el estado final;
Equipment aplica la política también mediante draft.
**Rationale:** domain/policy/EquipmentClassification.java:36 rechaza hoy cualquier ausencia;
domain/model/equipment/Equipment.java:164 centraliza applyDetails. EquipmentRowEvaluator:80
combina propuesta y valores actuales y construye draft.
**Alternatives considered:** solo Angular, validación solo en DTO o política duplicada de importación:
rechazadas por FR-008 y riesgo de divergencia.

## R2. Ausencia real y migración nueva

**Decision:** null como ausencia, nueva migración, conservar UNIQUE.
**Rationale:** V004__inventario_equipos.sql:33 exige NOT NULL y :62 exige patrón para todos
los KIA punto. Equipment.java:63 repite nulabilidad. V007 es la última migración observada.
**Alternatives considered:** cadena vacía, código sintético, usar serie para KIA: no representan
ausencia y contradicen el alcance. Editar V004 contradice constitución III.

## R3. Conservar código antes de validar

**Decision:** en edición KIA, una entrada sin código nuevo conserva el anterior; se valida
el resultado combinado. Ausencia previa más estado obligatorio produce rechazo.
**Rationale:** EquipmentService.update llama applyDetails directamente; ProposedEquipment.detailsOver
ya implementa conservación para importaciones. PUT no es PATCH; no extender conservación
de campos a datos ajenos al código.
**Alternatives considered:** limpiar al pasar a FREE o generar código al bloquear: contradicen spec.

## R4. Unicidad y auditoría

**Decision:** omitir predicado de código null, conservar validación por otros identificadores
y cierre UNIQUE. Hacer auditoría de alta tolerante a ausencia sin perder el evento.
**Rationale:** EquipmentRepositoryAdapter.java:88 añade igualdad de código incondicional.
EquipmentService.java:72 y EquipmentImportProcessor.java:235 usan Map.of con código.
EquipmentChanges.diff ya admite valores ausentes.
**Alternatives considered:** buscar todos los null como duplicados o usar índices parciales por
equipo activo: cambian las reglas de unicidad. Eliminar auditoría para altas sin código: rechazado.

## R5. DTOs y mapeos reales

**Decision:** conservar toCommand/from; publicar nulabilidad y regenerar contrato/tipos.
**Rationale:** CreateEquipmentRequest:28 y UpdateEquipmentRequest:22 ya aceptan ausencia.
EquipmentResponse:27, EquipmentSummaryResponse:20 y EquipmentRefResponse:10 no documentan
nullable para código. Búsqueda de MapStruct/@Mapper y pom no encontró configuración;
ADR 0001 elige mapeos locales en records.
**Alternatives considered:** introducir MapStruct en todo RF-002 excede esta feature.

## R6. Reutilización Angular

**Decision:** helper contextual en equipment-rules.ts y validators reactivos existentes.
**Rationale:** equipment-form-dialog.ts:136–143 condiciona required/pattern solo a ownCode;
equipment-form.ts:43,69–71,87 ya precarga y omite vacío en payload.
**Alternatives considered:** formularios distintos para FREE/BLOCKED o efectos que vacían el campo:
duplicación y riesgo de pérdida.

## R7. Efecto en consumidores

**Decision:** adaptar únicamente contrato/presentación para ausencia.
**Rationale:** renderers de inventario construyen List.of con código; ConsumptionLabels.code:86
retorna assignedCode; ConsumptionExcelRenderer:90 y :101 construyen listas inmutables.
ConsumptionReportService:128 concatena código en alcance. equipment-filter.ts:83 lo concatena
en etiqueta. EquipmentReference mantiene UUID: no cambia el vínculo de la sesión.
**Alternatives considered:** copiar código en sesiones o usar nombre como identidad: innecesario.

## R8. Pruebas existentes y decisiones resueltas

EquipmentClassificationTest:26–42 rechaza vacío sin contexto de estado; EquipmentTest:40 crea
FREE con código. InventoryApiTest ya cubre formato, duplicados, baja, permisos, versiones y
reportes. EquipmentImportApiTest:123–154 considera inválida una fila FREE sin código; su
fixture debe actualizarse y conservar la cobertura de estados obligatorios. :159 cubre
conservación de vacíos con código previo, no FREE nuevo sin código.

No quedan decisiones técnicas bloqueantes: la nulabilidad es necesaria, el motor y rutas se
reutilizan, y las reglas de otros estados ya están delimitadas. La siguiente fase descompone
este diseño en tareas; ninguna solución descrita se ejecutó aquí.

Rutas de clases abreviadas respecto de
`sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/`, salvo clases de consumo,
shared y frontend indicadas por su nombre/contexto.
