# Publicar una versión en producción

Cómo solicitar, aprobar y comprobar una publicación de SARC, y qué hacer cuando falla. Una publicación **nunca** ocurre sola: requiere una versión candidata, una solicitud manual y la aprobación explícita de responsable o suplente.

**Responsable**: quien solicita, responsable técnico; quien aprueba, responsable del producto o su suplente. **Requisitos previos**:

- La versión candidata existe y su CI fue verde (ver `version-candidata.md`).
- La **prueba de recepción de avisos** está vigente (ver `avisos.md`); si no, la publicación se bloquea con el código 18.
- El computador local descargó una copia verificada hace **menos de 24 h** (excepto en la primera instalación).
- El certificado HTTPS es válido y le quedan al menos 72 h (ver `certificado.md`).
- Entorno `production` de GitHub configurado con los revisores y los secretos (ver `inventario-variables.md`).

## 1. Solicitar la publicación

1. En GitHub, pestaña **Actions** → flujo **Publicar** → **Run workflow**.
2. **version**: la versión candidata (por ejemplo `2026.11.20-57-e85c910`, ficticia).
3. **modo**:
   - `normal`: publicación habitual.
   - `primera-instalacion`: solo con la base vacía (primera vez); ver «Primera instalación».
   - `verificar`: cierra una primera instalación pendiente.
4. Pulsar **Run workflow**. El trabajo `preparar` comprueba (sin secretos) que el Release existe, que su CI fue exitoso y que las sumas coinciden. Si falla, no se pide aprobación.

## 2. Aprobar

El trabajo `desplegar` queda en espera. GitHub notifica a los revisores configurados en el entorno `production`.

1. El revisor abre la ejecución y pulsa **Review deployments**, marca `production` y **Approve and deploy** (o **Reject**).
2. Aprueba el responsable del producto o su suplente. La excepción registrada en `decisiones-previas.md` permite que EddWlad apruebe su propia solicitud solo para la primera instalación y su verificación. Antes de una publicación normal, designar al suplente y activar «prevent self-review».
3. La aprobación vale **una sola ejecución**; no se reutiliza.
4. Sin aprobación no se ejecuta ningún paso sobre el VPS. Un rechazo o un tiempo vencido cancela la solicitud.

## 3. Leer el resultado

El resumen de la ejecución indica versión, modo, quién aprobó y el resultado:

| Resultado | Significado |
|---|---|
| `COMPLETADA` | Todas las comprobaciones posteriores pasaron; el servicio está activo. |
| «Pendiente de verificación» (`EN_CURSO`) | Primera instalación hecha; falta crear la cuenta y el reporte de verificación y ejecutar el modo `verificar`. |
| `FALLIDA` | Un paso falló; ver el código y la tabla de abajo. |

El registro completo vive en el VPS: `/var/lib/sarc/deploy-history.jsonl` (una línea por intento, con `migrationsApplied`, copia previa y comprobaciones). Si la publicación aplicó migraciones, se abre solo un *issue* `ensayo-restauracion` `pendiente` (cambio crítico): ver `restaurar.md`.

## 4. Códigos de salida y qué hacer

**Producción sin cambios** (códigos 2 y 10–18): nada se instaló ni se detuvo; se puede corregir la causa y repetir.
**Producción en mantenimiento** (códigos 20–21): el servicio está detenido o con la nueva versión sin verificar; seguir `recuperar.md`.

| Código | Paso | Qué significa | Qué hacer |
|---|---|---|---|
| 2 | Argumentos | La entrada `modo` o `version` no tiene el formato esperado | Revisar la entrada del flujo y repetir |
| 10 | 0 | Otra publicación está en curso, o hay una primera instalación sin verificar | Esperar a que termine la otra o ejecutar el modo `verificar` |
| 11 | 1 | Falta el Release o una suma no coincide | No usar esa versión; generar otra |
| 12 | 2 | Falta o está sin definir una variable de configuración (se nombra solo el nombre) | Completar `/etc/sarc/sarc.env` y repetir |
| 13 | 3 | Certificado inválido o con menos de 72 h | Ver `certificado.md` |
| 14 | 4 | El computador local no tiene una copia verificada de menos de 24 h | Encender el computador local, forzar la descarga (`avisos.md`/`mantener.md`) y repetir |
| 15 | 5 | El historial de migraciones no coincide con la versión (aplicada desconocida, suma distinta, fila fallida o pendiente anterior) | No publicar; avisar al responsable técnico. No se edita ninguna migración aplicada |
| 16 | 6 | Falló la copia previa | Revisar espacio y contenedor de la base (`recuperar.md`) y repetir |
| 17 | 8 | No se pudo instalar la versión (por ejemplo disco lleno) | Liberar espacio sin tocar datos (`mantener.md`) y repetir |
| 18 | 2b | Falta la prueba de recepción de avisos o ya no es vigente | **Repetir la prueba de recepción** (`avisos.md`) |
| 20 | 9 | La aplicación no llegó a `UP` en 3 min (migración fallida o arranque) | Mantenimiento activo; **no** se reactiva la versión anterior. Ver `recuperar.md` |
| 21 | 10 | Falló una comprobación posterior | Mantenimiento activo; ver qué comprobación falló y `recuperar.md` |

Cuando una publicación falla, el flujo abre (o comenta) un *issue* `alerta-operativa` que menciona a responsable y suplente y envía una señal a Healthchecks.io. El *issue* contiene solo la versión, el modo y el enlace a la ejecución.

## 5. Primera instalación

Solo cuando la base está vacía:

1. Definir en `/etc/sarc/sarc.env` los datos del administrador inicial (`SARC_BOOTSTRAP_ADMIN_*`) y el resto de la configuración.
2. Solicitar la publicación con `modo: primera-instalacion`. No exige recibo de copia ni copia previa (no hay datos que perder), pero sí la prueba de recepción, el certificado y la configuración.
3. Resultado esperado: «pendiente de verificación» (`EN_CURSO`), con el mantenimiento retirado para que el administrador pueda entrar. Solo se comprobaron disponibilidad, base de datos y versión.
4. El administrador ingresa, cambia su clave, se retiran las variables `SARC_BOOTSTRAP_ADMIN_*` y se reinicia (ver `entornos-y-secretos.md`).
5. Se crea la cuenta de verificación (rol decidido en `decisiones-previas.md`, nunca ADMINISTRADOR) y su credencial se guarda en `/etc/sarc/verificacion.env` (0600).
6. Se emite con SARC un reporte de verificación sin datos sensibles y su identificador se guarda en `SARC_VERIFY_REPORT_ID`.
7. Solicitar la publicación con `modo: verificar` y la misma versión: ejecuta todas las comprobaciones y deja la publicación `COMPLETADA` (o `FALLIDA` con el servicio en mantenimiento).
8. Mientras la primera instalación esté pendiente, ninguna otra publicación se admite (código 10). Antes de la segunda publicación deben estar activos `sarc-backup.timer` y la descarga del computador local, para que haya un recibo de menos de 24 h.

Si la primera instalación termina `FALLIDA`, la base ya tiene tablas y el modo se rechaza: ver el procedimiento manual de `recuperar.md`.

## 6. Cambiar al responsable o al suplente

Un cambio de persona exige actualizar, en este orden:

1. **Revisores del entorno `production`** en GitHub (Settings → Environments).
2. **Variables de repositorio** `SARC_RESPONSABLE` y `SARC_SUPLENTE`.
3. **Destinatarios en Healthchecks.io** de todas las comprobaciones.
4. **`/etc/sarc/avisos.conf`** en el VPS (destinatarios y canales vigentes).
5. **Nueva prueba de recepción** con la persona nueva (`avisos.md`): al cambiar `avisos.conf` su huella ya no coincide y `sarc-deploy` falla con el código 18 hasta que se repita.
6. Retirar accesos de la persona saliente (`acceso-y-exposicion.md`).

## Si no puedes continuar

Detén el procedimiento y avisa al responsable del producto. No reintentes en bucle una publicación fallida: cada intento queda registrado y puede dejar producción en mantenimiento.
