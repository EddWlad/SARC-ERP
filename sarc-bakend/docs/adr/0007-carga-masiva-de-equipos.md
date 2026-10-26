# ADR 0007 - Carga masiva de equipos y motor de importacion compartido

Estado: aceptada (26-oct-2026). Extiende la ADR 0005 (importacion de consumos) y aplica la regla de division de `shared` de la ADR 0006.

## Contexto

Registrar el inventario equipo por equipo es lento. Se pidio cargarlo desde un Excel o pegado, con el mismo control que la importacion de consumos: vista previa, observaciones por fila, aprobacion y confirmacion en segundo plano.

## Decision

1. **Plantilla de SARC, no formato libre.** `GET /api/v1/equipment/imports/template` entrega un XLSX con las columnas que lee el sistema (`EquipmentSheetColumns`), listas desplegables generadas desde el mismo vocabulario que usa el lector (`EquipmentSheetVocabulary`) e instrucciones. El lector ubica las columnas por nombre, sin importar orden, mayusculas ni tildes.

2. **Mismas reglas que el alta manual.** Cada fila se valida construyendo un borrador del equipo con `Equipment.draft` (las reglas de PRD 6.2) y la unicidad global con `EquipmentIdentifiers`, extraido de `EquipmentService` para que el alta manual y la carga masiva no dupliquen la regla.

3. **Clasificacion por serie.**
   - Serie nueva: `NEW`, se registra.
   - Serie existente con los mismos datos: `IDENTICAL`. Con datos distintos: `MODIFIED`, que solo se aplica si el analista lo aprueba y muestra que campos cambian.
   - Serie de un equipo eliminado: `DELETED_EXISTS`; se recupera desde el inventario (PRD 6.3).
   - Identificadores o identidades que pertenecen a otro equipo, o cambio de red/tipo: `CONFLICT`. Nunca se fusionan identidades.
   - Problemas de formato o reglas: `INVALID`. Repetidos dentro del archivo: `DUPLICATE`.

4. **Una celda vacia nunca borra datos.** En un equipo existente conserva el valor actual; vaciar un campo se hace en la edicion manual.

5. **Horario e imagen quedan fuera de la plantilla.** Se completan despues en cada equipo.

6. **Identidades externas verificadas al confirmar.** Las columnas de Enerlink, Wallbox y OCPP crean identidades verificadas por quien confirma el lote (rol editor). Asi "Vincular pendientes" en consumo las usa de inmediato.

7. **Se reevalua al aplicar.** Al confirmar, cada fila se evalua otra vez. Si el inventario cambio desde la vista previa (otra version del equipo, o una serie o identidad tomada por otro), la fila queda `SKIPPED` con el motivo en lugar de escribir algo no revisado.

8. **Motor de importacion compartido.** El ciclo de vida del lote se extrae a `shared/importing/batch`: `BaseImportBatch` (interpretar, revisar, confirmar, reintentar y descartar), `ImportStatus`, `ImportOrigin`, `RowIssue`, `ImportFailure`, `ImportFiles` e `ImportJobs`. Consumo e inventario solo aportan lo propio: la fuente, las clasificaciones y sus conteos. La lectura tabular pasa a `shared/importing/reading`, porque `importing` superaba las ~10 clases (ADR 0006). `RowIssue` deja de vivir junto a `ImportRow` porque ya lo usan dos modulos.

9. **Limite de 5 000 equipos por archivo.** Cada fila consulta el inventario; el limite mantiene acotado el tiempo de la vista previa.

## Consecuencias

- Migracion `V006`: tablas `asset_import_batch` y `asset_import_row`, con las mismas columnas comunes que `energy_import_batch`.
- Un modulo nuevo que importe datos extiende `BaseImportBatch` y reutiliza `ImportJobs`, sin copiar el ciclo de vida.
