# Contratos — Feature 001

Diseño de adaptación de interfaces existentes, sin rutas nuevas.

## Operaciones de inventario

- POST /api/v1/equipment: conservar cuerpo y autorización; assignedCode omitido/null/vacío
  se admite solo para KIA CHARGING_POINT FREE. Respuesta 201 con assignedCode null.
- PUT /api/v1/equipment/{id}: conservar version y datos requeridos actuales. Resolver código
  KIA omitido/null/vacío usando el valor existente antes de evaluar uso final. No interpretar
  el PUT completo como una mezcla indiscriminada de campos. Respuesta 200.
  Si se informa explícitamente un código nuevo en una corrección ya autorizada, conservar su
  tratamiento previo con formato, unicidad y auditoría. La conservación por omisión no lo
  convierte en inmutable ni habilita nuevas operaciones de borrado o reasignación.
- PATCH /api/v1/equipment/{id}/status: sigue cambiando estado administrativo. NO utilizarlo
  ni redefinirlo como cambio de uso; FREE/BLOCKED se editan por PUT existente.
- GET detalle/listado: assignedCode puede ser null; mismo UUID y campos restantes.
- Importación FILE/PASTE, vista previa, decisión y confirmación: reutilizar rutas/ciclo actuales.
  FREE nuevo sin código es aplicable si el resto es válido; BLOCKED sin código no.
  MODIFIED conserva aprobación y combinación de celdas vacías con inventario.

## Validación y errores

Seguir application/problem+json y códigos existentes, con detalle español que distinga
obligatoriedad y formato. INVALID_ASSIGNED_CODE puede conservarse para ambos motivos;
el detalle para ausencia debe decir que el Código asignado es obligatorio para ese estado.
Conflictos por identificador conservan EQUIPMENT_IDENTIFIER_IN_USE,
EQUIPMENT_DELETED_EXISTS y conflicto de varias identidades, sin filtrar secretos.

Las respuestas de vista previa incluyen RowIssue legible; no convertir errores de validación
en fallo de todo el lote. No se altera el tratamiento de carreras/errores inesperados.
Peticiones no autorizadas siguen denegadas y versiones obsoletas mantienen conflicto.

## Esquemas publicados y cliente

Marcar assignedCode nullable en EquipmentResponse, EquipmentSummaryResponse,
EquipmentRefResponse; revisar EquipmentImportRowResponse, que ya lo declara nullable.
Regenerar docs/api/openapi.json mediante OpenApiContractTest y luego tipos Angular.
El cliente usa los alias existentes. No cambia el tipo de UUID ni el formato de versión.

## Formulario y lectores

Campo de código visible para KIA punto. Obligatorio en BLOCKED/DISCONNECTED y opcional en FREE.
Cuando está informado, siempre aplicar patrón. Cambiar uso no debe vaciar el modelo.
Importación debe explicar esta condición en instrucciones sin cambiar columnas.

UI, PDF y Excel deben representar ausencia sin textos “null/undefined”, sin generar un código.
La selección del equipo en consumo sigue por UUID. Totales, filtros temporales, fuentes y
sesiones no cambian. Los documentos ya archivados se conservan byte por byte.
Esta compatibilidad de lectores RF-003 está autorizada por FR-011 y no permite modificar
conciliación, identidad, filtros o permisos ni añadir funcionalidades de negocio de consumo.
