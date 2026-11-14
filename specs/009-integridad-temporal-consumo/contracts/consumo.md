# Contratos propuestos de Consumo · Feature-009

Diseño de fase 1. Se ampliará el OpenAPI generado al implementar. Los campos nuevos son aditivos cuando corresponda y el contrato final debe pasar el verificador existente.

## HTTP y presentación de fechas

Mantener `GET /api/v1/energy/sessions`, `GET /api/v1/energy/summary` y `GET /api/v1/energy/report` con sus filtros actuales. Las fechas ISO `from` y `to` son límites de calendario, con `from` inclusivo y `to` exclusivo. Una consulta mixta usa la fecha de inicio según la fuente. Seleccionar una fuente reduce el conjunto sin mover una sesión a otra fecha. En la interfaz, el calendario inicial y los accesos rápidos conservan D13.

| Dato de sesión | Contrato |
| --- | --- |
| `startedAt`, `endedAt` | Instantes ISO 8601 existentes. Fin opcional. Usados para orden cronológico y equivalencias, cuando correspondan. |
| `startDate` | Fecha de atribución de inicio efectiva. Se usa también en los agregados y las exportaciones. |
| Inicio y fin de origen | Fecha/hora local ISO 8601 sin conversión, precisión original disponible y desplazamiento o zona como campo explícito. Pueden faltar en una sesión anterior con evidencia indeterminada. Angular muestra los campos de Enerlink sin interpretarlos como un nuevo instante. |
| Base temporal y verificación | Criterio de fuente para las lecturas verificadas. El histórico identifica la fila indeterminada como «Fecha operativa heredada». Si la sesión era elegible, conserva su aporte oficial en la fecha anterior. El resumen y los informes muestran el desglose heredado dentro del total para los mismos filtros. Sus kWh conocidos usan las reglas vigentes y se indican como desconocidos si ninguna de esas sesiones tiene medición. |
| `version` | Versión esperada para la siguiente mutación autorizada. |

## Operaciones por sesión

| Operación propuesta | Entrada y resultado | Permiso |
| --- | --- | --- |
| `GET /api/v1/energy/sessions/{id}` | Devuelve datos de sesión, origen, equipo, fechas, zona, energía y estado. Incluye evidencia original, baja y revisiones solo si son visibles para la cuenta. Paginar revisiones si el historial excede una página. | Roles autenticados que pueden ver esa sesión según los permisos efectivos de Consumo. |
| `PATCH /api/v1/energy/sessions/{id}` | Incluye versión, motivo y campos de edición permitidos. Campo ausente conserva el dato. Fin o energía explícitamente nulos representan dato no informado. El inicio, si cambia, incluye fecha/hora y su contexto de zona inequívoco. Devuelve la representación vigente tras aplicar las validaciones y el estado resultante. | Administrador o Analista autorizado. |
| `POST /api/v1/energy/sessions/{id}/deletion` | Acción de baja lógica con versión y motivo. La interfaz exige confirmación antes de enviar. Devuelve la representación autorizada con estado de baja. | Administrador o Analista autorizado. |

Reutilizar las respuestas de error de SARC para datos inválidos y conflictos de negocio. Sin permiso de edición, `403`. Un ID inexistente o una sesión fuera del alcance de lectura conserva la semántica `404` aplicada por Consumo. Una versión obsoleta devuelve `409` con detalle que indique volver a cargar. Una segunda baja o un reintento después de éxito conserva el resultado o informa el estado vigente, sin crear otra revisión de baja. Rechazar cambios de identidad de origen e impedir la modificación de una baja por los comandos existentes de prueba, aprobación, corrección o enlace. Los permisos son efectivos en servidor y en solicitudes directas, con la misma unión de roles vigente.

### Importación y correcciones

Las rutas de importación existentes conservan formatos y etapas. Una fila que coincide por clave natural con una baja se clasifica sin aplicación y conserva su explicación. Una fila que difiere de una corrección autorizada o cambiaría la fecha de atribución requiere revisión explícita. La confirmación comprueba estado y versión antes de aplicar. Para lotes en vuelo sin versión esperada, revalidar las filas que intentan modificar sesiones existentes antes de aceptar decisiones antiguas. Se conservan las reglas y límites de cada formato, la primera hoja y la evidencia original.

| Encabezados de Enerlink | Resultado esperado para una fila que supera las demás validaciones |
| --- | --- |
| Inicio sin declaración de desplazamiento | Aplicar `America/Guayaquil` y registrarla como zona aplicada. Una declaración de Fin no modifica la zona resuelta para Inicio. |
| Fin sin declaración de desplazamiento | Aplicar la zona o desplazamiento resuelto para Inicio y registrar la procedencia aplicada. Si no hay fecha/hora de fin, el fin permanece ausente. |
| Desplazamientos válidos y distintos en Inicio y Fin | Interpretar cada extremo con su desplazamiento, conservar ambos como declarados y validar `fin >= inicio` por instantes. La fecha de atribución sale de Inicio. |
| Declaración presente ilegible, fuera de rango o contradictoria en un encabezado | Clasificar la fila como inválida para revisión, indicando el encabezado afectado. La fila no se incorpora con una zona asumida. |

La política Wallbox se conserva. Estas reglas de entrada no se usan para rellenar evidencia histórica ausente.

## Contrato de interfaz

- La tabla del histórico y la tarjeta móvil muestran «Ver». Presentan «Editar» y «Eliminar» cuando el permiso y el estado de la sesión lo permiten. Reusar el patrón de acciones del histórico y los diálogos de revisión e Inventario. Los controles incluyen nombre accesible e interacción por teclado.
- El detalle diferencia fecha/hora de origen, fecha de atribución e instante cuando están autorizados. La edición muestra motivo, campos permitidos y resultados de validación. La eliminación confirma que la sesión se excluirá de los indicadores. En conflicto se conserva el contexto y se ofrece recargar antes de confirmar otra vez.
- En el dashboard y el histórico, una recarga en segundo plano conserva el estado compatible con la misma consulta. Una respuesta de otro período, filtro o página no se presenta como resultado actual. Los cambios reales se muestran sin recargar la ruta y con foco y desplazamiento estables donde sea posible.

## Contrato de informes y conciliación histórica

- PDF, Excel y dashboard comparten criterio de atribución, filtros y elegibilidad. Con las mismas revisiones, filtros y permisos sus cifras comunes coinciden. El dashboard muestra `generatedAt` como fecha de cálculo. Cada exportación muestra su propio corte y lo devuelve en `X-Report-Cut-Off`; si hubo una edición entre consultas, el nuevo informe refleja los datos de su emisión. La fecha de cálculo no funciona como identificador de versión. El informe nuevo incluye rango, criterio «Fecha de inicio según la fuente», desglose heredado cuando aplica, zona operativa del corte y fecha/hora y zona de origen en el detalle autorizado. Los documentos anteriores conservan sus bytes, huella y permisos de archivo.
- La auditoría de solo lectura entrega universo, cobertura y hallazgos, con diferencias de fecha, sesiones y kWh por fuente, período y filtro. Para revisar la publicación del histórico se exige el diagnóstico de una copia autorizada del conjunto pertinente, con identificación y corte del conjunto. Un ensayo con datos sintéticos verifica el mecanismo.
- La propuesta de aplicación identifica evidencia, valores, versiones, alcance y huella. `POST /api/v1/energy/temporal-reconciliation/proposals/{id}/approval` registra únicamente la aprobación, con la huella revisada. Solo el Administrador autenticado puede invocarla. El servidor toma su identidad de la sesión, contrasta la huella con el manifiesto controlado y persiste la constancia de propuesta, huella, aprobador y momento. Para una solicitud sin permiso se devuelve `403` y para una huella que ya no coincide se informa conflicto sin registrar aprobación.
- La aplicación histórica es un comando operativo explícito separado. Consulta la constancia persistida, verifica propuesta y huella antes de escribir y rechaza una constancia ausente, inválida o ajena al conjunto. El archivo de entrada no otorga autorización. El resultado por candidato indica aplicado, ya aplicado, conflicto o indeterminado y permite repetir el proceso sin duplicar sesiones ni decisiones.
