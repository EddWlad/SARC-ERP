# SARC backend

Spring Boot 4 / Java 25 / PostgreSQL 18. Especificacion funcional: `../documents` (PRD v1.1 manda en reglas de negocio; TRD v1.0 en tecnica).

## Comandos

- `./mvnw test` : pruebas unitarias, ArchUnit e integracion (Testcontainers; requiere Docker en marcha).
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` : levanta PG18 con `compose.yaml` (necesita `.env`, ver `.env.example`) y crea el admin con `SARC_BOOTSTRAP_ADMIN_*` de `.env`.
- Swagger UI y `/v3/api-docs` solo para ADMINISTRADOR y desactivados en `prod`.

## Estructura

```
shared/            kernel compartido: error, audit, security, persistence, pagination, time, web,
                   domain (tipos comunes), storage (archivos), report (PDF/Excel corporativos)
modules/<modulo>/
  api/             controller/  request/  response/
  application/     service/  command/  result/  port/
  domain/          model/  policy/  repository/
  infrastructure/  persistence/  security/  config/  bootstrap/  report/
```

Dependencias: `api -> application -> domain <- infrastructure`.

- `domain`: reglas y puertos. Sin `org.springframework` (solo anotaciones `jakarta.persistence`). ArchUnit lo exige.
- `application`: casos de uso, transacciones (`@Transactional`) y autorizacion (`@PreAuthorize`). Conoce puertos, no adaptadores.
- `infrastructure`: adaptadores (JPA, Spring Session, Spring Security).
- `api`: controladores y DTOs `record`. Nunca devuelve entidades JPA.
- Un modulo no importa clases de otro modulo; se comunican por puertos publicos o eventos.

## Organizacion de paquetes

- Dentro de cada capa se agrupa por **rol**, nunca por construccion de Java: no hay carpetas `records/` ni `enums/`. Un enum va junto al modelo que describe (`Role`, `UserStatus` en `domain/model`); un record va donde su rol (`request`, `response`, `command`, value object en `model`, `*Properties` en `config`).
- Cada rol tiene un subpaquete fijo y se crea al aparecer su primera clase: `command` (entrada de un caso de uso), `result` (salida), `port` (interfaz de salida hacia el exterior), `model` (entidades, value objects y enums), `policy` (reglas parametrizables sin identidad), `repository` (puerto de persistencia y sus criterios). No se inventan roles nuevos sin ADR.
- Cuando `domain/model` supere ~10 clases, se parte por agregado (`model/equipment/`), no por tipo.
- Visibilidad minima: lo que cruza paquetes (DTOs, comandos, puertos) es `public`; controladores, servicios auxiliares y adaptadores quedan package-private.
- Los controladores viven en `api.controller` (ArchUnit lo verifica).

## Reglas

- Cambio de esquema = nueva migracion Flyway `V###__nombre.sql`; nunca editar una ya aplicada. `ddl-auto=validate`.
- Errores: lanzar `NotFoundException` / `ConflictException` / `BusinessRuleException` con codigo estable; `GlobalExceptionHandler` los traduce a problem+json. No devolver ni registrar secretos.
- Todo endpoint nuevo bajo `/api/v1` queda autenticado por defecto; declarar el rol con `@PreAuthorize` en el caso de uso.
- Cada cambio de datos relevante llama a `AuditTrail.record(...)` dentro de la misma transaccion.
- Sin codigo muerto ni dependencias sin uso. Cada requisito nuevo trae pruebas.
- Contrato REST: `docs/api/openapi.json`. Si cambia un endpoint o DTO, `OpenApiContractTest` falla: regenerarlo con `./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true` y luego `npm run api:types` en el frontend. Campos de respuesta opcionales: `@Schema(nullable = true)`.
- Decisiones que se apartan del TRD: `docs/adr/`.
