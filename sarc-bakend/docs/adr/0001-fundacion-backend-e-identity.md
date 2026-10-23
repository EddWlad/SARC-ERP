# ADR 0001 - Fundacion del backend y modulo identity

Estado: aceptada (22-oct-2026)

## Decisiones respecto al TRD v1.0

| Tema | TRD | Implementado | Motivo |
|------|-----|--------------|--------|
| Paquete base | `com.eostech.sarc` | `com.tidsec.sarc_bakend` | Decision del patrocinador al inicializar el proyecto. |
| Capas | `api / application / domain / infrastructure`, JPA pragmatico | Igual. `UserAccount` es entidad de dominio y JPA a la vez (solo `jakarta.persistence`) | Una frontera real no aporta aqui; ArchUnit impide dependencias de Spring en `domain`. |
| Casos de uso | Interfaces publicas por modulo | Servicios concretos inyectados en controladores | Una sola implementacion por caso de uso; las interfaces solo anadian ceremonia. Los puertos de salida (`UserRepository`, `UserSessionRegistry`) si son interfaces. |
| Roles en BD | `auth_role(id)` + FK `role_id` | `auth_role(code)` PK y FK `role_code` | El codigo es estable y evita un join al construir autoridades. |
| Hash de claves | Argon2id o BCrypt | BCrypt (fuerza 12, configurable) | Sin dependencias extra; limite de 72 bytes validado en `PasswordPolicy`. |
| Mapeo DTO | MapStruct | Metodos `from(...)` en los records | Pocos mapeos; se incorpora MapStruct cuando haya volumen. |
| Lombok | Opcional | Retirado | No se usaba. |
| Numeracion Flyway | V001 roles, V002 inventario... | V001 roles/usuarios, V002 auditoria, V003 spring_session | Se numera segun el orden real de entrega; los modulos siguientes continuan la secuencia. |

## Decisiones de diseno

- **Principal de sesion = id del usuario**: `AuthenticatedUser.getUsername()` devuelve el UUID, que es lo que indexa `spring_session.principal_name` (VARCHAR 100). Permite revocar sesiones por usuario sin depender del correo.
- **Cambio de clave obligatorio sin filtros propios**: mientras `mustChangePassword` sea true el principal solo tiene la autoridad `PASSWORD_CHANGE_REQUIRED`; la cadena de seguridad exige que cualquier ruta fuera de `/api/v1/auth/**` no la tenga.
- **Revocacion de sesiones** al desactivar, cambiar roles, restablecer o cambiar la clave. Se ejecuta tras confirmar la transaccion.
- **Credenciales invalidas, cuenta bloqueada o inactiva** responden igual (401 `INVALID_CREDENTIALS`) para no revelar que un correo existe.
- **Bloqueo temporal**: 5 intentos fallidos bloquean 15 min (configurable). Los intentos erroneos de "clave actual" en el cambio de clave tambien cuentan. Ver ADR 0003.
- **Sin cache de peticion** en la cadena de seguridad: evita crear una sesion en BD por cada peticion anonima rechazada.
- **CSRF** por cookie `XSRF-TOKEN` (legible por Angular). La SPA debe hacer un `GET /api/v1/auth/me` al arrancar para recibirla.
- **Admin inicial** por `sarc.bootstrap-admin.*` (solo si no hay usuarios); entra con clave temporal.

## Pendiente de fases siguientes

- Rate limiting por IP en Nginx (hoy el backend bloquea por cuenta). Se define junto con el despliegue.
- Despliegue (Nginx, systemd), backups y almacenamiento externo (fase 8 del TRD).
- Roles de PostgreSQL separados: dueno del esquema para Flyway y rol de la aplicacion solo con DML. La imagen de Docker crea `POSTGRES_USER` como superusuario; en produccion se crea el rol de la aplicacion con un script de inicializacion y `spring.flyway.user` apunta al dueno.
