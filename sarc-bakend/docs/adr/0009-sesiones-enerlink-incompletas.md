# ADR 0009 - Sesiones Enerlink incompletas y vinculacion por identidad

Estado: aceptada (30-oct-2026). Implementa `specs/002-vinculacion-enerlink-sesiones/spec.md`. Modifica las ADR 0005 (consumo e importacion) y 0007 (carga masiva de equipos).

## Contexto

Un archivo Enerlink de 1.724 sesiones dejo 1.583 pendientes y 83 invalidas. La plantilla de inventario traia los pares ACTIVO/CARGADOR, pero completar la identidad de un equipo existente se trataba como una modificacion que exige aprobacion fila por fila: sin aprobarla, confirmar la omitia. Ademas, el importador exigia energia y termino, y Enerlink reporta sesiones iniciadas sin esos datos.

## Decision

1. **Completar identidad no exige aprobacion por fila.** Nueva clasificacion `IDENTITY_COMPLETED`: el unico cambio del equipo es registrar o verificar su identidad Enerlink. Se aplica al confirmar y no escribe el equipo. Una fila que ademas cambia otros datos sigue `MODIFIED`: se aprueba completa y, sin aprobar, tampoco registra la identidad. Las identidades de otras plataformas conservan la aprobacion.

2. **El par Enerlink se compara por su forma normalizada en toda decision.** `ExternalKey.comparable()` es la unica comparacion, sin columna normalizada ni indice unico: PostgreSQL no replica la regla sin `unaccent`, y la ambiguedad debe seguir siendo un estado detectable. Un par incompleto invalida la fila de inventario; un par distinto al verificado del equipo es conflicto y exige agregarlo a mano en Identidades externas.

3. **Una sesion pendiente guarda su motivo.** `link_issue` distingue identidad incompleta, no registrada, no verificada y ambigua. Se recalcula al importar y al vincular pendientes. Una sesion Enerlink sin par no es invalida: queda pendiente.

4. **Reimportar puede vincular.** Nueva clasificacion `LINKABLE`: sesion pendiente con la misma lectura cuyo par ya resuelve un unico equipo. Se vincula al confirmar el lote. Si la lectura difiere es `MODIFIED`, exige aprobacion y vincula al aplicar. `EQUIPMENT_CHANGED` queda solo para sesiones con equipo. Los pares se revalidan al aplicar: un equipo revisado que dejo de ser la coincidencia unica no se aplica.

5. **Ausente es `NULL`, nunca cero ni una fecha sustituta.** Solo el parser de Enerlink admite energia y termino vacios; Wallbox los sigue exigiendo. Los agregados suman energia conocida: `energyKwh` es nulo si hay sesiones sin lecturas y sigue en 0 sin sesiones. El promedio divide entre sesiones con lectura y el ranking excluye equipos sin energia informada, contandolos aparte.

## Pendientes

D10 y D14 no se resuelven aqui: siguen en `specs/003-revision-energia-ranking-empates/spec.md`.

## Consecuencias

- Revertir V009 despues de guardar sesiones sin energia o termino exige tratar antes esas filas.
- Los lotes anteriores no se reescriben: las filas invalidas no guardaron su lectura y se recuperan reimportando el archivo.
- Los informes emitidos no cambian; los nuevos distinguen energia conocida, "Sin datos" y "No informado".
