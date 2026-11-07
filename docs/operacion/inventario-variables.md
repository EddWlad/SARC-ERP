# Inventario de variables y secretos

Lista de todo valor de configuración o secreto que necesita SARC en producción. **Aquí nunca se escriben valores.** El repositorio es público.

Plantillas con marcadores: `deploy/env/sarc.env.example` y `deploy/env/verificacion.env.example`.

Leyenda de «Dónde vive»: **VPS** = `/etc/sarc/sarc.env` (0640 root:sarc); **VPS-verif** = `/etc/sarc/verificacion.env` (0600, solo root; el servicio no lo carga); **GitHub** = secreto del entorno `production`; **GitHub-var** = variable de repositorio (no secreta); **Local** = computador local autorizado.

## Variables de la aplicación y del VPS

| Nombre | Propósito | Obligatoria en prod | Dónde vive | Responsable | Rotación | Cómo verificar que la anterior quedó revocada |
|---|---|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil de la aplicación (`prod`) | Sí | VPS | Responsable técnico | No rota | — |
| `SARC_DB_URL` | URL JDBC de PostgreSQL (`127.0.0.1`) | Sí | VPS | Responsable técnico | Solo si cambia el host | — |
| `SARC_DB_USER` | Usuario de la base (fijo `sarc`, no secreto; coincide con el Compose de producción) | Sí | VPS | Responsable técnico | No rota | — |
| `SARC_DB_PASSWORD` | Contraseña del usuario de la base | Sí | VPS | Responsable técnico | Anual o ante exposición | Conectar con la anterior: `password authentication failed` |
| `POSTGRES_PASSWORD` | Contraseña con la que el contenedor crea el usuario | Sí | VPS | Responsable técnico | Junto con `SARC_DB_PASSWORD` | Igual que la fila anterior |
| `CLOUDINARY_CLOUD_NAME` | Cuenta de Cloudinary | Sí | VPS | Responsable del producto | No rota | — |
| `CLOUDINARY_API_KEY` | Clave de API de Cloudinary de producción | Sí | VPS | Responsable del producto | Anual o ante exposición | La clave anterior, usada contra la API, devuelve 401 |
| `CLOUDINARY_API_SECRET` | Secreto de la clave de Cloudinary | Sí | VPS | Responsable del producto | Junto con la clave | Igual que la fila anterior |
| `SARC_STORAGE_CLOUDINARY_ROOT_FOLDER` | Carpeta raíz de archivos (`sarc-prod`) | Sí | VPS | Responsable del producto | No rota | — |
| `SARC_STORAGE_CLOUDINARY_BACKUP_UPLOADS` | Solicita backup en cada subida de SARC. Activar en producción tras probar el alcance y la cuota de Cloudinary | Sí, tras verificación del proveedor | VPS | Responsable del producto | No rota | — |
| `SARC_DEPLOY_ENV` | `produccion` o `ensayo` (solo VM de ensayo) | Sí | VPS | Responsable técnico | No rota | — |
| `SARC_GITHUB_REPO` | Repositorio público de las versiones candidatas (`propietario/repositorio`) | Sí | VPS | Responsable técnico | Si cambia el repositorio | — |
| `SARC_PUBLIC_IP` | IP pública del VPS (no secreta); ruta del certificado y verificación de la IP en `sarc-check-cert` | Sí | VPS | Responsable técnico | Si cambia la IP | — |
| `SARC_VERIFY_REPORT_ID` | Identificador del reporte de verificación | Tras la primera instalación | VPS | Responsable técnico | Si se reemplaza el reporte | — |
| `SARC_BOOTSTRAP_ADMIN_EMAIL` | Correo del administrador inicial | Solo primer arranque | VPS | Responsable técnico | Se borra tras el primer arranque | Confirmar que ya no está en el archivo |
| `SARC_BOOTSTRAP_ADMIN_PASSWORD` | Clave inicial del administrador | Solo primer arranque | VPS | Responsable técnico | Se borra tras el primer arranque | Confirmar que ya no está en el archivo y que la clave inicial ya no ingresa |
| `SARC_VERIFY_USER` | Cuenta de verificación posterior a publicar | Desde la verificación de la primera instalación | VPS-verif | Responsable técnico | Ante exposición | La cuenta anterior, desactivada en SARC, no ingresa |
| `SARC_VERIFY_PASSWORD` | Clave de la cuenta de verificación | Igual | VPS-verif | Responsable técnico | Anual o ante exposición | La anterior no ingresa |
| `HC_URL_VPS_LATIDO`, `HC_URL_SERVICIO`, `HC_URL_COPIA_NOCTURNA`, `HC_URL_CERTIFICADO`, `HC_URL_DISCO` | URL de latido de cada comprobación de Healthchecks.io | Sí | VPS | Responsable técnico | Ante exposición (regenerar la comprobación) | La URL anterior deja de actualizar la comprobación |

## Archivos de configuración fuera del repositorio

| Archivo | Propósito | Dónde vive | Responsable | Rotación |
|---|---|---|---|---|
| `/etc/sarc/avisos.conf` | Destinatarios y canales de aviso vigentes (su huella SHA-256 liga la prueba de recepción) | VPS | Responsable del producto | Al cambiar responsable o suplente |
| `/etc/sarc/backup-recipient.pub` | Clave pública `age` del computador local (cifra las copias) | VPS | Responsable técnico | Al reemplazar el computador local |
| `/var/lib/sarc/prueba-recepcion.ok` | Resultado vigente de la prueba de recepción de avisos | VPS | Responsable del producto | Cada vez que cambien destinatarios o canales |

## Secretos de GitHub

`DEPLOY_SSH_KEY` y `DEPLOY_KNOWN_HOSTS` son secretos del entorno `production`. `HC_URL_PUBLICACION` y `HC_URL_SONDA` son secretos **de repositorio**, porque los trabajos que los usan (`avisar` y `sonda-externa`) no declaran entorno para no pedir otra aprobación.

| Nombre | Propósito | Dónde vive | Responsable | Rotación | Verificación de revocación |
|---|---|---|---|---|---|
| `DEPLOY_SSH_KEY` | Clave SSH privada de la cuenta de despliegue | GitHub | Responsable técnico | Anual o ante exposición | Quitar la clave pública anterior de `authorized_keys` y comprobar que el ingreso con ella falla |
| `DEPLOY_KNOWN_HOSTS` | Huella del VPS para validar el servidor SSH | GitHub | Responsable técnico | Si se reinstala el VPS | — |
| `HC_URL_PUBLICACION` | Latido de la comprobación `publicacion-fallida` (secreto de repositorio) | GitHub | Responsable técnico | Ante exposición | La URL anterior deja de actualizar la comprobación |
| `HC_URL_SONDA` | Latido de la comprobación `sonda-externa` (secreto de repositorio) | GitHub | Responsable técnico | Ante exposición | Ídem |

## Variables de repositorio de GitHub (no secretas)

| Nombre | Propósito | Responsable |
|---|---|---|
| `SARC_RESPONSABLE` | Usuario de GitHub del responsable (se menciona y asigna en las alertas) | Responsable del producto |
| `SARC_SUPLENTE` | Usuario de GitHub del suplente | Responsable del producto |
| `SARC_PUBLIC_URL` | Dirección pública del servicio (IP o dominio, sin esquema) que consulta la sonda externa | Responsable técnico |
| `SARC_PRODUCCION_DESDE` | Fecha de la primera publicación `COMPLETADA`; base del control de ensayos hasta el primer ensayo aprobado | Responsable técnico |

## Computador local autorizado

| Nombre | Propósito | Responsable | Rotación |
|---|---|---|---|
| Clave privada `age` | Descifrar las copias; con respaldo offline custodiado por el responsable | Responsable del producto | Al reemplazar el computador local |
| Clave SSH de `sarc-backup` | Descargar copias por SFTP y subir recibos | Responsable técnico | Anual o al reemplazar el computador local |
| Clave de API de exportación de Cloudinary | Exportar archivos de `sarc-prod` (solo lectura lógica) | Responsable del producto | Anual o ante exposición |
| `HC_URL_DESCARGA_LOCAL` | Latido de la comprobación `descarga-local` | Responsable técnico | Ante exposición |

## Reglas

- Cada entorno usa claves y carpeta de archivos distintas (`sarc-prod` y `sarc-test`); ningún entorno apunta a otro por omisión.
- Un secreto expuesto se revoca, se rota, se confirma que el anterior falla y se revisan los registros (ver `entornos-y-secretos.md`).
- Ningún valor se escribe en este repositorio, en avisos, en registros ni en `issues`.
