# Data model — Feature 001

## Equipo existente, sin nueva entidad

asset_equipment conserva id UUID, red, tipo, serie, UID/PUK, uso, estado administrativo,
conectores, auditoría y versión. Solo assigned_code pasa de presencia universal a condicional.
Ausencia se representa con null, nunca con cadena vacía, serie ficticia ni código autogenerado KIA.

| Red/tipo/uso final | Código ausente | Código informado |
| --- | --- | --- |
| KIA punto FREE | Permitido | Seis dígitos, primero 1–9, único |
| KIA punto BLOCKED | Rechazado | Mismo formato y unicidad |
| KIA punto DISCONNECTED | Rechazado | Mismo formato y unicidad |
| Otro punto / cualquier electrolinera | No se flexibiliza: valor resultante obligatorio | Sigue siendo serie |

El estado de uso sigue obligatorio para puntos e inaplicable para estaciones.
Los valores presentes se recortan conforme a normalización vigente; espacios solos son ausencia.

## Transiciones

- BLOCKED con 654321 → FREE: conservar 654321 incluso si no se envía código nuevo.
- FREE sin código → BLOCKED: rechazar sin código; aceptar con válido disponible.
- FREE con código → BLOCKED: reutilizarlo sin volver a introducirlo.
- FREE sin código → DISCONNECTED: exigir código por la regla anterior.
- FREE sin código, editando otro dato: seguir sin código.
- La eliminación explícita no se añade ni redefine; omisión/vacío no elimina un código KIA existente.
- Ninguna transición modifica otras propiedades de negocio no solicitadas; auditoría/versionado sí
  reflejan las operaciones existentes.

## Persistencia propuesta, no ejecutada

Nueva V008__codigo_kia_segun_uso.sql, sujeto al siguiente número libre al implementar:
retirar NOT NULL de assigned_code y reemplazar su CHECK para expresar la matriz.
Mantener UNIQUE global y todas las FK/UUID existentes. Cada rama obligatoria debe comprobar
IS NOT NULL; no confiar en la evaluación UNKNOWN de comparaciones con null.
Solo la rama KIA/punto/FREE puede admitir null. No usar NULLS NOT DISTINCT.

No UPDATE ni backfill: se conservan literalmente los códigos y el contenido histórico.
Verificar migración sobre esquema V007 con muestras de cada red/uso/estado administrativo.
Probar rechazos directos en PostgreSQL dentro de entorno desechable.

## Contrato lógico e integridad

DTOs de respuesta y referencia compartida admiten código ausente, sin cambiar id, red o tipo.
No se guarda un marcador visual como código. La unicidad excluye al propio equipo pero mantiene
reservas de inactivos y eliminados. Dos códigos presentes iguales no pueden confirmarse, incluso
en concurrencia. Varias ausencias sí son válidas.

Sesiones, revisiones, identidades externas y conectores conservan sus referencias. No se cambian
las tablas de consumo ni las reglas de conciliación por serie/identidad verificada.
