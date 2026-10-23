# ADR 0003 - Intentos de ingreso atomicos, rotacion CSRF y contrato OpenAPI

Estado: aceptada (22-oct-2026). Sale de la revision de codigo de la fase 0 + RF-001.

## Intentos fallidos de ingreso

**Problema.** El contador de intentos y el bloqueo se guardaban modificando la entidad `UserAccount`, que tiene bloqueo optimista (`@Version`) y campos de auditoria. Con intentos en paralelo varias transacciones leian la misma version: la primera confirmaba y las demas fallaban al confirmar (409 en vez de 401, y el intento no se contaba). Ademas cada intento cambiaba la version y `updated_at`/`updated_by`, asi que un administrador que editaba al usuario recibia conflictos falsos.

**Decision.** El repositorio expone `registerFailedLogin` y `clearFailedLogins`, implementados con `UPDATE` masivos de JPQL. PostgreSQL bloquea la fila y evalua el contador vigente, por lo que todos los intentos cuentan; los `UPDATE` masivos no pasan por la version ni por la auditoria de Spring Data. La regla (bloquear al llegar al maximo, reiniciar el contador, no contar mientras esta bloqueada) queda en esa consulta y la duracion en `LoginPolicy.lockUntil`. Restablecer o cambiar la clave sigue limpiando el bloqueo desde la entidad, porque es un cambio administrativo real.

**Prueba.** `IdentityApiTest.parallelFailedLoginsAreAllCountedWithoutConflicts` y `loginAttemptsDoNotChangeTheAccountVersionSoAdminEditsKeepWorking`.

## Rotacion del token CSRF

El login manual solo cambiaba el id de sesion. Ahora la estrategia de autenticacion tambien aplica `CsrfAuthenticationStrategy` y emite el token nuevo en la misma respuesta, para que la SPA siga operando sin recargar.

## Contrato OpenAPI

- `docs/api/openapi.json` es el contrato publicado (OpenAPI 3.1). `OpenApiContractTest` falla si el codigo cambia sin actualizarlo: `./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true`.
- Los DTOs de respuesta se publican con todos sus campos obligatorios, salvo los marcados `@Schema(nullable = true)` (`OpenApiConfig`). Asi el cliente no recibe tipos opcionales falsos.
- El frontend genera sus tipos con `npm run api:types` (`@hey-api/openapi-ts`, solo tipos) en `src/app/core/api/generated`. El CI regenera y falla si difieren. Se eligio esta herramienta porque `openapi-typescript` 7 no admite TypeScript 6.
