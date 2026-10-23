# ADR 0002 - Organizacion de paquetes dentro de cada capa

Estado: aceptada (22-oct-2026). Complementa la ADR 0001. La regla de particion del modelo y la organizacion de `shared` se precisan en la ADR 0006.

## Contexto

Las cuatro capas de cada modulo (`api`, `application`, `domain`, `infrastructure`) estaban en paquetes planos: 8 a 10 clases de roles distintos juntas (controladores con DTOs, entidad con enums y puertos, adaptadores con propiedades de configuracion). Con RF-002 a RF-006 cada capa multiplicaria su tamano y buscar una clase por rol seria cada vez mas lento.

Se evaluo agrupar por construccion de Java (carpetas `records/` y `enums/` por capa) y se descarto.

## Decision

Subpaquetes por **rol arquitectonico**:

```
api/             controller  request  response
application/     service  command  result  port
domain/          model  policy  repository
infrastructure/  persistence  security  config  bootstrap
```

## Por que no `records/` ni `enums/`

- `record` y `enum` son sintaxis, no responsabilidades. Un record puede ser un request, un response, un comando, un value object o unas propiedades de configuracion; cada uno pertenece a un lugar distinto de la arquitectura.
- Las clases que cambian juntas deben vivir juntas: `Role` y `UserStatus` describen a `UserAccount`; separarlos en `enums/` los aleja de lo que modelan.
- Un paquete por rol permite reglas de ArchUnit (por ejemplo, los controladores solo en `api.controller`); un paquete por sintaxis no permite ninguna.
- Obliga a exponer como `public` clases que solo usa su vecino de paquete.

Visualmente se obtiene lo mismo que buscaba la propuesta: los requests, responses y comandos quedan juntos y son todos records, porque ese es su rol.

## Consecuencias

- Lo que cruza paquetes pasa a ser `public` (DTOs y sus metodos `from` / `toCommand`); servicios, controladores y adaptadores siguen package-private.
- Las pruebas replican la estructura (`application/service/UserManagementServiceTest`, `domain/model/EmailTest`...).
- Las propiedades `@ConfigurationProperties` se registran con `@ConfigurationPropertiesScan` en la clase principal; cada modulo declara las suyas sin tocarla.
- `shared/` no se subdivide: sus paquetes ya son por responsabilidad y tienen entre 1 y 5 clases.
- Cuando `domain/model` supere ~10 clases se parte por agregado (`model/equipment/`), no por tipo.
