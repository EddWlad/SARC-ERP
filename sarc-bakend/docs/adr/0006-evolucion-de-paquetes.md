# ADR 0006 - Evolucion de paquetes: shared y modelos de dominio

Estado: aceptada (23-oct-2026). Revisa la ADR 0002 con RF-001 a RF-003 ya construidos.

## Contexto

Con tres modulos, `shared` tiene 13 paquetes y el modelo de inventario 10 clases. Se pregunto si conviene agrupar por construccion de Java (carpetas de enums y records) y si `shared` necesita otra estructura.

## Decision

1. **Se mantiene la agrupacion por rol, nunca por construccion de Java** (ADR 0002). Un enum o un record vive con lo que describe: `UsageStatus` junto a `Equipment`, `ImportStatus` junto a `BaseImportBatch`. Separarlos por sintaxis aleja lo que cambia junto y no permite reglas de arquitectura.

2. **`domain/model` se parte por agregado cuando el modulo tiene mas de un agregado raiz** (antes: al superar ~10 clases). Cada subpaquete contiene la raiz, sus entidades internas, value objects y enums:
   - `inventory/domain/model/equipment` (Equipment, Connector, horario, estados), `connectortype`, `externalidentity`.
   - `consumption/domain/model/session`, `batch`.
   - `identity` tiene un solo agregado (`UserAccount`) y queda plano.

3. **`shared` se organiza por capacidad transversal**, como hoy (`audit`, `security`, `storage`, `importing`...). Crece agregando capacidades nuevas (notificaciones, outbox), no engordando las existentes. Reglas:
   - Solo entra lo que usan dos o mas modulos, o la infraestructura comun. La logica de negocio de un modulo nunca va a `shared`.
   - Un paquete de `shared` que supere ~10 clases se divide por subcapacidad.
   - Nucleo puro, sin Spring (ArchUnit `shared_kernel_is_free_of_spring`): `shared/domain` (vocabulario comun), `shared/catalog` (contratos publicos entre modulos) y `shared/pagination`. Los dominios de los modulos dependen de ellos.
   - Los DTOs web comunes (`VersionRequest`) y las etiquetas del vocabulario comun para reportes (`DomainLabels`) viven en `shared/web` y `shared/report`, en lugar de duplicarse en cada modulo.

## Consecuencias

- Mover un agregado de paquete solo cambia `package` e imports; no cambia tablas ni contrato REST.
- Cada modulo nuevo decide su estructura de modelo con la regla 2 desde el primer dia.
