# Contrato: salud y avisos

## Salud

| Ruta | Acceso desde Internet | Respuesta |
|---|---|---|
| `/actuator/health/readiness` | Sí (Nginx) | `{"status":"UP"}` o `{"status":"DOWN"}` con 200/503; incluye base de datos; sin detalles |
| `/actuator/health/liveness` | No (solo `127.0.0.1`) | Estado |
| `/actuator/info`, `/actuator/metrics`, resto | No: Nginx responde 404; la aplicación exige `ADMINISTRADOR` | — |
| `/v3/api-docs`, Swagger | No: desactivados en `prod` | — |

## Avisos

| Comprobación | Origen | Canal | Periodo / gracia | Condición de aviso |
|---|---|---|---|---|
| `ci-fallo-pr` | Trabajo `avisar-fallo` de `ci.yml` | Comentario en el PR que menciona a responsable y suplente (notificación de GitHub por mención), además de la notificación nativa al autor | — | Comprobación roja en un PR del propio repositorio; en un PR desde un *fork* (token sin escritura) solo la comprobación visible en el PR |
| `ci-fallo-main` | Trabajo `avisar-fallo` de `ci.yml` | *Issue* `alerta-operativa` «CI fallido en main» (menciona y asigna a ambos) | — | Cualquier trabajo rojo en `push` a `main` |
| `ensayo-vencido` | Flujo `control-ensayos` | *Issue* `alerta-operativa` (menciona y asigna a ambos) | Diario | Último ensayo aprobado (o, si aún no hay ninguno, la fecha de `SARC_PRODUCCION_DESDE`) con 28 días (por vencer) o 31 días (vencido); *issue* `ensayo-restauracion` `pendiente` abierto más de 7 días |
| `publicacion-fallida` | Flujo `publicar` | *Issue* `alerta-operativa` (menciona y asigna a ambos) + correo Healthchecks.io | — | Trabajo falla o se cancela |
| `aprobacion-pendiente` | Entorno `production` | Notificación nativa a revisores | — | Publicación esperando aprobación |
| `vps-latido` | Temporizador del VPS | Correo Healthchecks.io | 5 min / 5 min | Falta latido (VPS caído o sin red) |
| `servicio` | Temporizador del VPS (readiness local) | Correo | 5 min / 5 min | `DOWN` o sin respuesta |
| `sonda-externa` | Flujo programado de GitHub (TLS validado) | *Issue* + correo | 15 min | Falla HTTPS desde Internet |
| `copia-nocturna` | Script de copia | Correo | 24 h / 2 h | Falta o `/fail` |
| `descarga-local` | Tarea del computador local | Correo | 24 h / 2 h | Falta o `/fail` |
| `certificado` | Comprobación diaria | Correo | 24 h / 2 h | Vigencia < 72 h, renovación fallida o falta |
| `disco` | Comprobación horaria | Correo | 1 h / 30 min | Uso > 80 % o falta |

Reglas:

- El responsable y el suplente reciben todas las comprobaciones de Healthchecks.io y menciones en los *issues*. La excepción temporal de FR-038a permite registrar solo al responsable para la primera instalación y su verificación.
- Contenido: nombre de la comprobación, estado y hora. Nunca valores de configuración, rutas con datos, salida de comandos ni datos personales. Los *issues* son públicos mientras el repositorio lo sea.
- Plazo (SC-017): con periodo de 5 min y gracia de 5 min, Healthchecks.io avisa como máximo 10 min después del último latido recibido; el margen restante hasta 15 min cubre el envío del correo. Los avisos de `servicio` usan una señal `/fail` inmediata cuando el servidor sigue vivo, sin esperar la gracia.
- Prueba de recepción (FR-038a): antes de la primera publicación y tras cambiar destinatarios o canal, se dispara cada tipo con una comprobación de prueba; cada persona confirma en el *issue* `prueba-recepcion`. Con ambas confirmaciones se escribe `/var/lib/sarc/prueba-recepcion.ok` con la fecha y la huella SHA-256 de `/etc/sarc/avisos.conf` (destinatarios y canales). `sarc-deploy` falla con código 18 si el archivo falta o la huella ya no coincide.
