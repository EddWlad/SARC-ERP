# Contrato: cambios REST

Regenerar `docs/api/openapi.json` y tipos del frontend. Permisos vigentes; errores en problem+json.

## Nuevos endpoints

### POST `/api/v1/energy/sessions/{id}/energy-review`

Rol: `ADMINISTRADOR` o `ANALISTA`.

```json
{ "decision": "APPROVED", "justification": "Lectura confirmada con el operador", "version": 7 }
```

| Campo | Regla |
| --- | --- |
| `decision` | `APPROVED` o `REJECTED` |
| `justification` | Obligatoria, 1 a 500 caracteres sin contar espacios exteriores |
| `version` | Versión revisada |

`version` es obligatoria y corresponde a concurrencia, no a revisión de lectura. Actor y fecha vienen del servidor. Cada decisión válida avanza versión aun si repite APPROVED/REJECTED; respuesta con versión actualizada. Versión consumida: 409 `CONCURRENT_MODIFICATION`, sin decisión ni auditoría parcial. Validación de cuerpo 400; negocio 422; sesión inexistente 404; permiso insuficiente 403.

Respuesta: `SessionResponse`.

| Error | Causa |
| --- | --- |
| `SESSION_NOT_FOUND` | Sesión inexistente |
| Conflicto de versión vigente | Revisión obsoleta |
| `SESSION_NOT_UNDER_ENERGY_REVIEW` | Sesión sin observación |
| Validación | Justificación vacía o larga |

### GET `/api/v1/energy/sessions/{id}/energy-reviews`

Lectura para todos los roles de consumo. Devuelve decisiones de más reciente a más antigua:

```json
[{ "decision": "REJECTED", "justification": "...", "energyKwh": 1500.000, "sessionRevision": 2,
   "sessionVersion": 7, "decidedBy": "uuid-o-null", "decidedAt": "2026-10-30T20:00:00Z" }]
```

## Cambios

| Tipo | Cambio |
| --- | --- |
| `SessionResponse` | Añade `energyReview` nullable |
| `SessionResponse.canApproveSource` | Booleano calculado por equipo/fuente/sourceApproved, independiente del estado; no concede permisos |
| `SessionResponse.state` | Añade `ENERGY_REVIEW`, `ENERGY_REJECTED` |
| `GET /energy/sessions` | Nuevo filtro opcional `energyReview` |
| `ImportBatchResponse` | Añade `energyReviewRows`: filas interpretables con energía normalizada >1.000, no sesiones pendientes de revisión |
| Listado de filas del lote | Nuevo filtro opcional `aboveThreshold` |
| `PendingLinkResponse` | Añade `awaitingEnergyReview` |
| `ConsumptionSummaryResponse` | Añade `energyReview`, `energyRejected`, `approvedAboveThreshold` |
| `Ranked` | Añade `position` |

`top` puede traer más de cinco elementos.

Historial por `sessionVersion` evaluada descendente, incluso con fechas iguales. `sessionRevision` mantiene revisión de lectura. Justificación recortada antes de validar 1–500 en backend y frontend. Corregir otro campo con iguales kWh normalizados conserva aprobación/rechazo sin nueva decisión; cambiar kWh a otro valor alto exige revisión aunque haya aprobación histórica de ese valor.

El filtro `energyReview` del histórico encuentra también TEST/PENDING_LINK. Resúmenes usan estado exclusivo y filtros compartidos de período/red/fuente/tipo/equipo, no la página filtrada de sesiones. `approvedAboveThreshold` es subconjunto oficial, nunca sumando adicional.

Conservar el contrato 002 `LINKABLE`/`linkableRows`. Ambos menús muestran aprobar fuente con `canApproveSource` y permiso vigente aun si prevalece revisión energética; mensajes respetan estado devuelto.

## Observaciones de fila

| Código | Mensaje | Efecto |
| --- | --- | --- |
| `ENERGY_ABOVE_THRESHOLD` | La energía supera 1.000 kWh; su cómputo depende de la revisión energética y de los demás controles | No invalida ni revoca decisiones existentes |
| `ENERGY_NOT_STORABLE` | La energía excede el valor admitido | Inválida |

`ENERGY_OUT_OF_RANGE` deja de emitirse. Sus filas históricas siguen legibles.

`energyReviewRows` se presenta como «Lecturas superiores a 1.000 kWh». Cuenta cada fila interpretable >1.000, incluso IDENTICAL o DUPLICATE si conserva lectura; una inválida sin lectura no cuenta. El filtro `aboveThreshold=true` selecciona el mismo universo; `false` selecciona las restantes y omitido no restringe. Es conteo transversal, no sumando de clasificaciones ni de sesiones. Se fija al preparar preview; no se recalcula por decisiones posteriores ni modifica lotes anteriores.

Mensajes según resultado vigente: nueva lectura alta o cambio de kWh alto requiere revisión antes de cómputo oficial; idéntica aprobada conserva aprobación y elegibilidad bajo los demás controles; idéntica rechazada conserva rechazo; idéntica pendiente sigue pendiente. El aviso neutral y el contador no crean otra decisión, no cambian estado ni prometen que una sesión ya aprobada «no sumará».

## Reportes

| Reporte | Cambio |
| --- | --- |
| PDF | Posiciones con empate; nota de más de cinco; por revisar y rechazadas fuera de indicadores; aprobadas sobre umbral en resumen |
| Excel | Posiciones con empate; `Excluidas` incluye los nuevos estados; columnas «Excepción de energía» y «Justificación» |

Reportes archivados intactos.
