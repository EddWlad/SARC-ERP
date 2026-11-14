# Data Model: Feature-009

Diseño de fase 1. Los nombres nuevos describen el modelo propuesto. Validar esquema y tipos con la base anterior al implementar. Véanse [plan.md](plan.md) y [research.md](research.md).

## Sesión de carga (`energy_session`)

| Dato | Regla |
| --- | --- |
| Identidad | UUID interno y clave natural única `(source, source_scope, external_session_id)`. La importación conserva la misma identidad incluso si la sesión tiene baja lógica. Los comandos individuales no cambian estos valores. |
| Equipo | Referencia al ID físico validado por Inventario. La red y el tipo efectivos se obtienen según las reglas vigentes. Reasignar requiere reevaluar la fuente preferente y conservar la etiqueta y referencia anteriores en la revisión. |
| Instantes | `started_at` obligatorio y `ended_at` opcional, en UTC como instantes. Validar `fin >= inicio` cuando hay fin. La fecha de atribución puede cambiar sin que cambien los instantes. |
| Fecha de atribución | `start_date` sigue siendo `DATE` e indexada. Enerlink usa la fecha local de inicio vigente en la base temporal de origen. Wallbox conserva el cálculo vigente en la zona operativa. Se compara con límites de calendario `[from, to)`. |
| Presentación y procedencia | Valores de inicio y fin en hora local de origen, desplazamiento o zona de cada extremo, origen declarado o aplicado y estado de verificación. Cuando existe un valor original verificado, guardar o referenciar de forma inmutable la evidencia correspondiente. Cuando hay corrección, diferenciar valores efectivos y originales. |
| Energía y calidad | Mantener kWh normalizado `HALF_UP` a tres decimales, con `null` distinto de cero. Reusar estado de calidad, aprobación de fuente y flujo D10 para energía superior al umbral. |
| Baja lógica | Marca con instante, actor y motivo en su revisión. Se conserva el estado de calidad previo. Es terminal para las mutaciones y reimportaciones de esta entrega. |
| Control de concurrencia | Mantener `version` optimista para cualquier cambio y `revision` para el historial de datos. Los comandos y las filas de lote que mutan una sesión existente comparan la versión efectiva. |

### Procedencia temporal y registros anteriores

- Una lectura de Enerlink nueva conserva el instante y el par fecha/hora local con su desplazamiento. Si inicio y fin traen desplazamientos distintos comprobables, cada instante se valida con el suyo. Wallbox conserva la zona aplicable de Ecuador y su precisión de presentación.
- Separar original y vigente. Una diferencia de hora declarada que produce el mismo instante puede cambiar el día de atribución. Comparar esta procedencia como parte de la decisión de importación, sin sustituir silenciosamente el valor vigente ni crear una sesión nueva.
- Los registros anteriores pueden iniciar con procedencia sin verificar y con el `start_date` existente. Un dato indeterminado conserva estado de calidad, elegibilidad, fecha operativa e instantes. Si era elegible, aporta al total oficial una sola vez en esa fecha. Los agregados calculan con los mismos filtros un desglose de sesiones y kWh conocidos heredados dentro del total, con `null` para energía desconocida. Una corrección autorizada con evidencia puede cambiar la atribución y retira el dato del desglose. El diagnóstico registra cobertura y casos sin evidencia para la revisión de publicación.
- Las reglas de preferencia, pruebas, pendientes y revisión energética siguen aplicando. El predicado oficial común requiere una sesión elegible y sin baja lógica. El lookup por identidad incluye las bajas. El acceso por ID y las conciliaciones operativas aplican su filtro de autorización correspondiente.

## Evidencia de origen

Reusar lotes, filas y almacenamiento privado. La referencia conserva tipo de entrada, lote, huella de contenido, fila o posición, fuente y alcance. Registrar la lectura declarada asociada a la fila que se aplicó, no solo a la primera coincidencia de identificador. Las decisiones y reimportaciones posteriores pueden añadir evidencia. La evidencia original conserva su contenido y no se sobrescribe al editar ni al resolver una discrepancia.

## Revisión de sesión (`energy_session_revision`)

Ampliar el historial existente para distinguir revisión de importación, edición individual, corrección histórica y baja lógica. Una revisión guarda identidad de sesión, versiones, actor, instante, motivo y los campos o estados antes y después necesarios para reconstruir la decisión. La revisión de importación conserva sus referencias de lote. Para una revisión sin lote sucesor, el tipo y la referencia de edición o propuesta reemplazan esa procedencia. No crear un lote ficticio. La escritura de sesión, revisión y auditoría forma una sola unidad transaccional.

| Operación | Condición y transición |
| --- | --- |
| Editar | Usuario editor, versión vigente, sesión activa, valores autorizados, motivo y validación. Guarda revisión y pasa al estado que indiquen las políticas de Consumo. Una revisión energética pendiente impide el cómputo oficial. |
| Eliminar | Usuario editor, versión vigente, sesión activa y motivo. Conserva identidad y datos, registra la baja y excluye de resultados oficiales. |
| Reimportar sin cambios | Conserva la sesión y sus decisiones. No agrega otra revisión efectiva ni altera cifras. |
| Reimportar con discrepancia | Sigue revisión explícita, conserva correcciones aprobadas y protege las bajas en previsualización, confirmación y reintento. |
| Conflicto de versión | Se rechaza la mutación sobre datos que cambiaron. La persona revisa el estado vigente antes de volver a intentar. |

## Propuesta de conciliación histórica

Artefacto operativo en almacenamiento controlado. Contiene identificador y huella, conjunto y corte del inventario, fuentes verificadas, clasificación e impacto. Cada candidato incluye identidad natural e interna, versión esperada, referencia y huella de evidencia, valores antes/después, razón y clasificación. La revisión aplicada referencia propuesta y candidato. Esa referencia sirve de clave de idempotencia, incluso si hay reintentos tras una aplicación parcial.

La **constancia de aprobación histórica** es un registro de Consumo controlado por el servidor. Contiene ID de propuesta, huella del contenido aprobado, ID del Administrador autenticado y momento de aprobación, con una constancia inmutable por pareja propuesta y huella. Un cambio en el manifiesto exige otra constancia. El comando consulta el registro persistido y comprueba la huella recalculada del manifiesto contra él. No acepta una identidad o constancia aportada por el archivo como autorización.

Las clases son **coincide**, **cambia solo atribución**, **error de instante comprobado** e **indeterminado**. Solo se aplican candidatos aprobados y con evidencia y versión vigentes. Si cambia la versión o la propuesta, se genera un resultado de conflicto y la corrección requiere nueva revisión. La conciliación por día, mes, fuente y filtros seleccionados demuestra la conservación del total de sesiones y kWh cuando la corrección es exclusivamente temporal y la elegibilidad es la misma.

## Informe emitido

El modelo de publicación conserva identificación, filtros, corte, permisos y bytes. Los nuevos informes describen el criterio de fecha de inicio según la fuente en el contenido, diferenciado de la zona operativa del corte. Incorporar una clave de metadata de Consumo solo si el contrato compartido lo admite y tras verificar compatibilidad. Los informes archivados continúan usando sus metadatos originales y el control de acceso vigente.

## Evolución del esquema

La migración nueva amplía procedencia temporal, baja, revisiones y control de versión esperado en filas de importación cuando sea necesario. Añade almacenamiento restringido para las constancias de aprobación. Los registros de la versión anterior siguen legibles con valores anteriores y campos nuevos opcionales. Validar los índices de `start_date`, clave natural y elegibilidad con un conjunto de volumen representativo. Reservar numeración solo tras integrar el historial Flyway pertinente. El relleno de evidencia y la corrección histórica son operaciones autorizadas por separado de la migración de esquema. Verificar restauración y actualización desde el esquema anterior con PostgreSQL 18.
