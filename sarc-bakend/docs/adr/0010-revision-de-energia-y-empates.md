# ADR 0010 — Revisión de energía y empates

Fecha: 2026-10-30. Estado: aceptada para Feature 003.

## Contexto

FR-001–017 y SC-001–007 requieren conservar lecturas altas y revisar su elegibilidad, sin alterar identidades, sesiones incompletas ni reimportaciones de la Feature 002.

## Decisión

- Se normaliza cada lectura a tres decimales HALF_UP. Más de 1.000 kWh genera `ENERGY_ABOVE_THRESHOLD`, aviso conservado en preview, no límite físico. Un valor no representable en NUMERIC(12,3) sigue siendo inválido (`ENERGY_NOT_STORABLE`). `ParsedSession` exige lectura presente y solo permite ese aviso no bloqueante.
- `energyReview` es independiente: nulo, PENDING, APPROVED o REJECTED. Precedencia principal: TEST > PENDING_LINK > ENERGY_REJECTED > ENERGY_REVIEW > AWAITING_APPROVAL > VALIDATED. Solo VALIDATED es oficial. El filtro energético consulta la condición sin alterar el universo del resumen.
- Cada decisión exige editor, versión actual y justificación recortada de 1–500 caracteres. Se inserta persona/hora del servidor, kWh, revisión de lectura y versión de concurrencia evaluadas. Sesión, decisión y auditoría comparten transacción; las decisiones anteriores no se modifican.
- El adaptador hace flush y CAS de versión, incluso al repetir un estado. El historial se ordena por versión evaluada, no por timestamps potencialmente iguales. La respuesta contiene la versión persistida. Un conflicto no se reintenta automáticamente.
- Corregir otros campos con iguales kWh normalizados conserva PENDING, APPROVED y REJECTED. Una lectura alta diferente vuelve a PENDING; volver a un número aprobado antiguamente no restaura su decisión. Una lectura no superior o ausente limpia la condición y conserva historial. Sigue aplicándose la aprobación ordinaria de correcciones.
- `energyReviewRows` cuenta lecturas altas interpretables del lote, incluidas idénticas y duplicadas. Es transversal a clasificaciones y no representa pendientes. El aviso neutral no promete reiniciar decisiones al reimportar.
- La aprobación de fuente usa capacidad propia y permisos, aunque prevalezca otro estado. Ambos órdenes son válidos; aprobar un control no evita los demás.
- Categorías principales excluyentes; aprobadas sobre umbral es subconjunto informativo de oficiales. Informes leen datos y justificaciones por conjuntos en REPEATABLE_READ; renderizado y archivo ocurren después. No se reescriben informes emitidos.
- El ranking obtiene todos los equipos con energía oficial conocida antes del corte. Orden: energía descendente, nombre, UUID solo entre nombres iguales. Igualdad por `BigDecimal.compareTo`, posiciones compartidas con salto. Top usa posición <= 5, incluyendo todos los empatados, sin truncar backend, gráfico ni informes.

## Persistencia y despliegue

V010 añade columnas, restricciones e historial sin UPDATE de sesiones o lotes anteriores. V001–V009 permanecen intactas. Despliegue coordinado de backend/frontend por nuevos estados y DTOs. Tipos Angular generados desde OpenAPI. Sin MapStruct, Spring Batch ni dependencias productivas nuevas.

## Verificación

Dominio, parsers, PostgreSQL 18, API/roles/CSRF/concurrencia, ranking, exportaciones y Angular. `Feature003BrowserSandbox` optativo usa Testcontainers y FileStorage simulado. Resultados y límites de QA en el quickstart de la feature.
