# Guía de operación de SARC

Cómo preparar una publicación, verificarla, reconocer un fallo y recuperar el servicio. Está escrita para una persona que **no participó** en el desarrollo. Todo se describe sin valores reales: el repositorio es público.

**Estado**: SARC está **en producción** desde el 2026-11-07 (primera publicación verificada). Los controles operativos que siguen abiertos (renovación real del certificado, recibos diarios de copia, Auto Backup, caída total del VPS) están en `pendientes-recursos-reales.md`.

## Flujo de trabajo desde ahora

1. **Desarrollar en local** (base `pg_sarc`, perfil `dev`), con pruebas del backend y del frontend.
2. **Integrar por GitHub**: rama o PR hacia `main`; el CI (`ci.yml`) debe quedar verde.
3. Cada integración en `main` con CI verde genera una **versión candidata** inmutable (`version-candidata.yml`).
4. **Publicar** con el flujo `Publicar` y la aprobación del entorno `production` (`publicar.md`); después **verificar** el resultado.
5. Los cambios de esquema entran como migración nueva (`V0xx`); nunca se edita una aplicada.

## Entornos

| Entorno | Para qué sirve | Datos |
|---|---|---|
| Desarrollo | Trabajo diario en el equipo del desarrollador | Base local `pg_sarc`; nunca se toca en ensayos |
| Pruebas | CI y VM o contenedores desechables | Sintéticos |
| Producción | VPS de Contabo, acceso por IP pública con HTTPS | Reales |

## A quién llamar

- **Responsable del producto**: autoriza publicaciones, restauraciones reales y decisiones previas.
- **Responsable técnico**: opera el VPS, las copias y los ensayos.
- **Suplente**: recibe los mismos avisos y puede aprobar publicaciones.

Los nombres y contactos viven fuera del repositorio (`/etc/sarc/avisos.conf` y variables de GitHub).

## Procedimientos

| Quiero… | Documento |
|---|---|
| Entender qué decisiones faltan antes de producción | `decisiones-previas.md` |
| Localizar y comprobar una versión candidata | `version-candidata.md` |
| Publicar una versión y leer el resultado | `publicar.md` |
| Qué hacer si una publicación o el servicio fallan | `recuperar.md` |
| Ensayar o hacer una restauración | `restaurar.md` |
| Mantener el VPS (actualizar, disco, cambio de equipo o dominio) | `mantener.md` |
| Configuración y secretos, rotación y secreto expuesto | `entornos-y-secretos.md`, `inventario-variables.md` |
| Accesos, cortafuegos y registros | `acceso-y-exposicion.md` |
| Certificado HTTPS | `certificado.md` |
| Avisos, latidos y prueba de recepción | `avisos.md` |
| Qué no se puede comprobar todavía | `pendientes-recursos-reales.md` |

## Glosario

- **Versión candidata**: resultado de una integración en `main` con CI verde; Release *pre-release* inmutable con manifiesto y sumas.
- **Publicación**: instalar una versión candidata en producción. Requiere solicitud manual y aprobación en el entorno `production`.
- **Primera instalación**: publicación sobre una base vacía; queda «pendiente de verificación» hasta el modo `verificar`.
- **Copia previa**: copia cifrada de la base que se hace justo antes de publicar.
- **Recibo**: archivo que deja el computador local en el VPS al descargar y verificar una copia. Prueba que hay una copia reciente fuera del servidor.
- **Ensayo de restauración**: restaurar una copia en un entorno aislado y desechable para comprobar que sirve. No toca producción.
- **Restauración real**: sustituir los datos de producción por los de una copia. Excepcional, con autorización y registro.
- **Cambio crítico**: publicación que aplicó migraciones, cambio del computador local o de proveedor; obliga a un ensayo de restauración.
- **Mantenimiento**: el servicio responde una página «SARC está en mantenimiento» mientras se publica o se recupera.
- **Prueba de recepción**: confirmación de que responsable y suplente reciben cada tipo de aviso; sin ella no se publica.

## Reglas generales

- Nunca se escriben secretos, correos ni IP reales en este repositorio, en *issues*, avisos o registros.
- Ante cualquier duda, **detente y avisa** al responsable del producto.
- Las migraciones aplicadas no se editan: un cambio de esquema es una migración nueva.
