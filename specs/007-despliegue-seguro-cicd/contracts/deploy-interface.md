# Contrato: flujo `publicar` y comando `sarc-deploy`

## Flujo de GitHub `publicar`

| Elemento | Valor |
|---|---|
| Disparo | `workflow_dispatch`, entradas `version` (obligatoria) y `modo` (`normal` por omisión, `primera-instalacion` o `verificar`); los tres modos pasan por la misma aprobación |
| Trabajo `preparar` | Sin entorno ni secretos. Valida Release, CI exitoso, sumas. |
| Trabajo `desplegar` | `environment: production` (revisores obligatorios según las decisiones de E0 y la excepción temporal de FR-038a; sin *bypass* de administradores; solo `main`/tags de versión). `concurrency: production`, sin cancelación. |
| Trabajo `avisar` | `if: failure() \|\| cancelled()`: abre o comenta *issue* `alerta-operativa` mencionando a ambos (logins en las variables de repositorio `SARC_RESPONSABLE` y `SARC_SUPLENTE`; el trabajo no declara entorno); `/fail` a Healthchecks.io |
| Salida | Resumen con versión, aprobador, pasos y resultado; enlace al registro |

## Comando del VPS `sarc-deploy <version>`

Se invoca por SSH con la clave de despliegue (comando forzado que solo acepta `deploy <version>`, `deploy --primera-instalacion <version>` y `deploy --verificar <version>`; cualquier otra entrada se rechaza). La cuenta `sarc-deploy` no tiene privilegios: el comando forzado ejecuta `sudo -n /usr/local/sbin/sarc-deploy …`, permitido por una única regla `sudoers`; el script, sus bibliotecas y `/etc/sarc/*.env` pertenecen a root (`verificacion.env` 0600) y la cuenta no puede modificarlos. Como defensa adicional, `sarc-deploy` vuelve a validar sus argumentos y termina con código 2 (uso incorrecto) si no coinciden con una de las tres formas. Escribe una línea en `/var/lib/sarc/deploy-history.jsonl`.

| Paso | Comprobación / acción | Falla si |
|---|---|---|
| 0 | `flock` exclusivo; la última publicación no está `EN_CURSO` (salvo en modo `verificar`) | Otra publicación en curso o una primera instalación sin verificar (código 10) |
| 1 | Descarga Release y verifica `SHA256SUMS` y manifiesto | Suma distinta o Release ausente (11) |
| 2 | Configuración obligatoria presente (`/etc/sarc/sarc.env`), sin imprimir valores | Falta una variable (12) |
| 2b | Prueba de recepción vigente (`/var/lib/sarc/prueba-recepcion.ok` con la huella actual de `/etc/sarc/avisos.conf` y confirmación de ambos) | Falta o está desactualizada (18) |
| 3 | Certificado válido (cadena contra el almacén del sistema; en `SARC_DEPLOY_ENV=ensayo`, contra la CA de prueba de la VM) y con ≥ 72 h | Inválido o próximo a vencer (13) |
| 4 | Recibo de copia local verificada < 24 h | Sin copia reciente (14) |
| 5 | Esquema: historial Flyway coherente con el manifiesto | Migración desconocida, suma distinta o fila fallida (15) |
| 6 | Copia previa (`pg_dump`) y verificación | Falla la copia (16) |
| 7 | Página de mantenimiento; detener `sarc.service` | — |
| 8 | Instala en `releases/<version>`, cambia `current` | Error de disco (17) |
| 9 | Arranca; Flyway migra; espera readiness ≤ 3 min | No llega a `UP` (20): **no** reactiva la anterior si hubo migraciones |
| 10 | Comprobaciones posteriores | Alguna falla (21) |
| 11 | Retira mantenimiento; registra `COMPLETADA` | — |

Códigos 2 (argumentos no válidos) y 10–18: nada cambió en producción. Códigos 20–21: producción en mantenimiento; la salida indica la última migración aplicada y el procedimiento de la guía.

Las comprobaciones del paso 10 usan HTTPS por loopback con el certificado y la IP pública de SARC. Nginx mantiene el 503 para conexiones externas hasta retirar el indicador de mantenimiento. La sonda programada comprueba el acceso desde Internet.

## Comprobaciones posteriores (FR-015)

| Comprobación | Cómo | Éxito |
|---|---|---|
| Disponibilidad | `GET https://<IP>/` y readiness | 200 y `UP` |
| Base de datos | readiness incluye `db` | `UP` |
| Ingreso | Login con la cuenta de verificación (credencial en `/etc/sarc/verificacion.env` del VPS; nunca en GitHub ni en la salida) | Sesión válida |
| Usuarios (identidad) | Consulta de la sesión propia (`/api/v1/auth/...`) | 200 con la cuenta de verificación |
| Inventario, consumo, uptime, reportes | Una lectura paginada por módulo permitida al rol de la cuenta | 200 |
| Archivos | Solicitud del enlace firmado del reporte de verificación (`SARC_VERIFY_REPORT_ID`) y descarga | Enlace emitido y archivo descargado; sin `SARC_VERIFY_REPORT_ID` el resultado es `PENDIENTE` solo con `--solo-basicas` (primera instalación); en cualquier otro caso es un fallo (21) |
| Versión | `version` de `META-INF/build-info.properties` dentro del JAR enlazado por `/opt/sarc/current` (`unzip -p`) y `manifest.json` de la versión; sin `/actuator/info`, que exige ADMINISTRADOR | Ambos coinciden con `version` |

## Primera instalación

`sarc-deploy --primera-instalacion <version>` solo se acepta si la base no tiene tablas de SARC. Omite el paso 4 (recibo de copia local < 24 h) y el paso 6 (copia previa), porque no hay datos que perder, y la comparación con un esquema anterior del paso 5; aplica las migraciones desde cero. Los pasos 0–3, incluida la prueba de recepción (código 18), se aplican igual. La primera instalación no cuenta como cambio crítico aunque aplique todas las migraciones, porque parte de una base vacía: el primer ensayo de restauración se hace tras siete días de recibos.

En este modo solo son obligatorias las comprobaciones posteriores de disponibilidad, base de datos y versión; ingreso, usuarios, módulos y archivos quedan `PENDIENTE`, porque la cuenta y el reporte de verificación aún no existen. Si las obligatorias pasan, `sarc-deploy` registra `EN_CURSO`, retira el mantenimiento para que el administrador pueda entrar, libera el bloqueo y termina con código 0; `publicar` lo resume como «pendiente de verificación». Si alguna obligatoria falla, da `FALLIDA` (21), como en una publicación normal.

Después, el administrador inicial cambia su clave, se retiran `SARC_BOOTSTRAP_ADMIN_*`, se crea la cuenta de verificación, cuya credencial se guarda entonces en `/etc/sarc/verificacion.env` del VPS, y se emite con las funciones normales de SARC un reporte de verificación sin datos sensibles; su identificador se guarda en `SARC_VERIFY_REPORT_ID`. Ese reporte se conserva como cualquier reporte emitido.

`sarc-deploy --verificar <version>` (vía `publicar` con `modo: verificar`) solo se acepta si la última línea del registro es `EN_CURSO` para esa versión y `current` apunta a ella: toma el bloqueo, ejecuta `sarc-post-checks.sh` completo, con todas las comprobaciones obligatorias y sin reinicio, y añade una línea `COMPLETADA`; si alguna falla, activa el mantenimiento, registra `FALLIDA` y devuelve 21, como en una publicación normal. Desde la segunda publicación el paso 4 es obligatorio, por lo que antes de ella deben estar activos `sarc-backup.timer` y la descarga del computador local.

Si la primera instalación termina en `FALLIDA`, la base ya tiene tablas de SARC y `--primera-instalacion` se rechaza. Como todavía no hay datos reales, la recuperación es manual, fuera de los scripts y con registro: el responsable confirma por escrito en el *issue* `alerta-operativa` que no hay datos de usuarios, detiene `sarc.service`, recrea el volumen `sarc_pgdata`, vuelve a definir `SARC_BOOTSTRAP_ADMIN_*` si ya se habían retirado, anota en el registro cualquier reporte de verificación que haya quedado huérfano en `sarc-prod` (sin borrarlo) y repite la primera instalación. No es efecto de publicar ni de probar (FR-022) y nunca se aplica después de una publicación `COMPLETADA`.

## Cuenta de verificación

La cuenta de verificación es una cuenta normal de SARC creada en E7 por el administrador. SARC no tiene un rol de «solo lectura» (roles: ADMINISTRADOR, GERENTE, SUPERVISOR, ASISTENTE, ANALISTA); en E0 se elige el rol con menos privilegios que pueda leer los cuatro módulos según la matriz del PRD, **nunca** ADMINISTRADOR. Las comprobaciones solo leen. Sus accesos quedan en la auditoría como cualquier otro.
