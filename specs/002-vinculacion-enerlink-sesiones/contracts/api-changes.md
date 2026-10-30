# Contrato: cambios REST

No se crean endpoints ni cambian permisos. `docs/api/openapi.json` se regenera y `npm run api:types`
actualiza los tipos. Códigos de error y formato problem+json se mantienen.

## Sesiones

`SessionResponse`

| Campo | Cambio |
| --- | --- |
| `endedAt` | `nullable` |
| `energyKwh` | `nullable` |
| `linkIssue` | Nuevo, `nullable`: `IDENTITY_INCOMPLETE`, `IDENTITY_NOT_REGISTERED`, `IDENTITY_NOT_VERIFIED`, `IDENTITY_AMBIGUOUS` |

## Importación de sesiones

| Tipo | Cambio |
| --- | --- |
| `ImportRowResponse.classification` | Añade `LINKABLE` |
| `ImportRowResponse.outcome` | Añade `LINKED` |
| `ImportBatchResponse` | Añade `linkableRows` |

Códigos de observación por fila:

| Código | Mensaje |
| --- | --- |
| `IDENTITY_INCOMPLETE` | Identidad Enerlink incompleta |
| `IDENTITY_NOT_REGISTERED` | Identidad Enerlink no registrada |
| `IDENTITY_NOT_VERIFIED` | Identidad Enerlink no verificada |
| `IDENTITY_AMBIGUOUS` | Identidad Enerlink ambigua |
| `SESSION_VALUE_REMOVED` | La reimportación quita energía o término ya conocidos |

En Enerlink reemplazan a `EQUIPMENT_NOT_IDENTIFIED`. Wallbox lo conserva.

## Importación de equipos

| Tipo | Cambio |
| --- | --- |
| `EquipmentImportRowResponse.classification` | Añade `IDENTITY_COMPLETED` |
| `EquipmentImportRowResponse.outcome` | Añade `IDENTITY_ADDED` |
| `EquipmentImportBatchResponse` | Añade `identityCompletedRows` |

Nuevos códigos: `ENERLINK_SCOPE_REQUIRED`, `ENERLINK_IDENTITY_DIFFERS`.

## Identidades externas

`POST` de registro existente: en Enerlink exige ámbito y rechaza con `EXTERNAL_IDENTITY_IN_USE` un par
equivalente D05 de otro equipo. Sigue permitiendo agregar otro par al mismo equipo: es la acción
explícita de la Clarificación 1.

## Resumen de consumo

`ConsumptionSummaryResponse`

| Campo | Cambio |
| --- | --- |
| `Totals.energyKwh` | `nullable`; nulo = «Sin datos» |
| `Totals.sessionsWithoutEnergy` | Nuevo |
| `Day.energyKwh` | `nullable` |
| `Day.sessionsWithoutEnergy` | Nuevo |
| `NetworkShare`, `SourceShare`, `TypeShare` `.energyKwh` | `nullable` |
| `averageKwhPerSession` | `nullable` |
| `equipmentWithoutEnergy` | Nuevo |

## Reportes PDF y Excel

| Dato | Presentación |
| --- | --- |
| Energía de sesión ausente | `No informado` (Excel: texto, no celda numérica) |
| Término ausente | `No informado` |
| Total sin lecturas | `Sin datos` |
| Total parcial | Etiqueta «Energía conocida» y número de sesiones sin energía |
| Ranking | Nota con equipos excluidos por no tener energía informada |

Los reportes ya archivados no se regeneran.
