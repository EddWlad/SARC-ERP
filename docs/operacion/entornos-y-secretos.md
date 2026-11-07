# Entornos y secretos

Cómo se separan desarrollo, pruebas y producción, dónde vive cada secreto y qué hacer al rotarlo o si se expone.

**Responsable**: responsable técnico. **Requisitos previos**: acceso administrativo al VPS (cuenta personal con `sudo`) y a la configuración de GitHub. Los valores nunca se escriben en este repositorio (es público); las plantillas están en `deploy/env/` y la lista de variables en `inventario-variables.md`.

## Entornos

| Entorno | Dónde | Base de datos | Archivos (Cloudinary) | Configuración |
|---|---|---|---|---|
| Desarrollo | Equipo del desarrollador | Compose local `pg_sarc` | Carpeta `sarc` o sin credenciales | `.env` ignorado por Git, perfil `dev` |
| Pruebas | CI (Testcontainers) y VM o contenedores desechables | Desechable, datos sintéticos | Sin credenciales, o carpeta `sarc-test` con clave propia | Variables del ensayo; nunca las de producción |
| Producción | VPS | Volumen `sarc_pgdata` | Carpeta (o entorno de producto) `sarc-prod` con clave propia | `/etc/sarc/sarc.env`, perfil `prod` |

Reglas: cada entorno tiene base, carpeta de archivos y credenciales propias; ningún entorno apunta a otro por omisión; un ensayo nunca usa la base local `pg_sarc` ni datos reales.

En producción la aplicación **no arranca** si falta alguna credencial de Cloudinary: el mensaje de error nombra solo las propiedades ausentes (por ejemplo `sarc.storage.cloudinary.api-secret`) y nunca valores. En desarrollo y pruebas el almacenamiento sigue siendo opcional y las subidas responden 503.

## Dónde vive cada secreto

| Lugar | Qué contiene | Permisos |
|---|---|---|
| `/etc/sarc/sarc.env` (VPS) | Base de datos, Cloudinary de producción, carpeta `sarc-prod`, latidos del VPS, administrador inicial (solo primer arranque) | `0640 root:sarc`; lo carga `sarc.service` |
| `/etc/sarc/verificacion.env` (VPS) | Credencial de la cuenta de verificación posterior a publicar | `0600`, solo `root` (quien ejecuta `sarc-deploy`); `sarc.service` **nunca** lo carga |
| Secretos de GitHub | Entorno `production`: clave SSH de despliegue y `known_hosts` del VPS. Repositorio: URL de latido de `publicacion` y de `sonda` (sus trabajos no declaran entorno para no pedir otra aprobación) | Los del entorno, solo para trabajos aprobados; los de repositorio, solo flujos del repositorio |
| Computador local | Clave privada `age`, clave SSH de `sarc-backup`, clave de API de exportación de Cloudinary, URL de latido de `descarga-local` | Usuario restringido, disco cifrado |

La credencial de la cuenta de verificación vive solo en el VPS: no viaja por SSH, no se guarda en GitHub ni aparece en el manifiesto, en avisos o en registros.

## Administrador inicial (primer arranque)

Pasos (resultado esperado entre paréntesis):

1. Definir `SARC_BOOTSTRAP_ADMIN_EMAIL` y `SARC_BOOTSTRAP_ADMIN_PASSWORD` en `/etc/sarc/sarc.env` antes de la primera instalación (el archivo tiene ambas variables).
2. Ejecutar la primera instalación (ver `publicar.md`, modo `primera-instalacion`). En el primer arranque SARC crea el administrador (SARC está en `EN_CURSO`, sin mantenimiento).
3. Ingresar con esa cuenta: el sistema obliga a **cambiar la clave** (si no la pide, detenerse y avisar al responsable).
4. Borrar **ambas** variables `SARC_BOOTSTRAP_ADMIN_*` de `/etc/sarc/sarc.env` y reiniciar `sarc.service` (`sudo systemctl restart sarc`).
5. Comprobar que la clave inicial ya no permite ingresar (debe fallar).

Si falla: no repetir con los mismos valores; ver `recuperar.md` (primera instalación fallida).

## Rotación periódica

1. Generar el secreto nuevo en su origen (Cloudinary, base de datos, Healthchecks.io, clave SSH…).
2. Actualizar el lugar donde vive (tabla anterior).
3. Reiniciar o recargar lo que lo usa (`sudo systemctl restart sarc` para `sarc.env`).
4. Revocar el anterior y **comprobar que ya no funciona** (columna «Cómo verificar que la anterior quedó revocada» de `inventario-variables.md`).
5. Anotar la fecha de rotación en la bitácora del responsable (sin el valor).

Para la contraseña de la base: cambiarla en PostgreSQL, actualizar a la vez `SARC_DB_PASSWORD` y `POSTGRES_PASSWORD`, reiniciar el servicio y comprobar la conexión.

## Si un secreto se expone

Ejemplos: aparece en un registro, en un *issue*, en un commit o en un mensaje.

1. **Revocar** el secreto en su origen de inmediato.
2. **Rotarlo** (pasos anteriores) y actualizar el lugar donde vive.
3. **Confirmar** que el valor anterior ya falla.
4. **Revisar los registros** (journald, Nginx, ejecuciones de GitHub Actions) desde la fecha estimada de exposición por usos indebidos.
5. Si estaba en el repositorio: el escaneo de secretos de CI (`gitleaks`) lo detecta; borrar el historial no basta, el secreto ya se considera comprometido.
6. Abrir un *issue* `alerta-operativa` **sin** el valor, indicando qué secreto, cuándo y qué se hizo.

## Si no puedes continuar

Detén el procedimiento y avisa al responsable del producto: no improvises con secretos reales.
