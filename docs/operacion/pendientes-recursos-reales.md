# Verificaciones pendientes de recursos reales

## Primera publicación en Contabo

El 2026-11-07 se publicó `2026.11.07-29-296602d` mediante el flujo [Publicar](https://github.com/EddWlad/SARC-ERP/actions/runs/42397127831). Flyway aplicó V001 a V017 en una base nueva. Se comprobaron un administrador, cinco roles, los seis tipos de V004 y cero equipos. HTTPS y readiness respondieron 200 desde fuera del VPS.

La [verificación oficial](https://github.com/EddWlad/SARC-ERP/actions/runs/42398427677) cerró la publicación como `COMPLETADA`. Se comprobó el cambio de la clave inicial del administrador, se retiraron las variables de arranque y se reinició SARC. La cuenta técnica tiene rol `ASISTENTE` y pudo consultar los módulos y descargar el PDF de indicadores oficiales emitido para la prueba. El acceso administrativo personal por llave y `sudo` con contraseña se verificaron antes y después de deshabilitar el ingreso SSH directo de `root` y la autenticación SSH por contraseña.

La primera copia de PostgreSQL se descargó cifrada al computador autorizado y coincidió su SHA-256. El temporizador nocturno está activo. Antes de la siguiente publicación se debe habilitar la descarga automática de Windows y obtener un recibo verificado con menos de 24 horas de antigüedad. Las siguientes integraciones y publicaciones se gestionarán desde la Feature 008.

La tabla registra la evidencia de las verificaciones que dependen del VPS, los accesos autorizados y los servicios externos. Los controles operativos parciales requieren su evidencia específica. La guía de ensayo sigue siendo el criterio para las pruebas en VM desechable.

| Verificación | Depende de | Responsable | Condición de cierre | Estado |
|---|---|---|---|---|
| Emisión y renovación del certificado para la IP | VPS con IP pública, puerto 80 accesible | Responsable técnico | Certificado reconocido y renovación comprobada (`certificado.md`) | Emisión reconocida, temporizador activo y simulación exitosa el 2026-11-07. Renovación real pendiente de observar. |
| Puertos expuestos desde Internet | VPS | Responsable técnico | Escaneo externo: solo 22/80/443 (`acceso-y-exposicion.md`) | Verificado el 2026-11-07. |
| Alcance real del Auto Backup (puntos, restauración) | Contratación | Responsable del producto | El panel muestra puntos diarios | Pendiente |
| Backup de Cloudinary: alcance y cuota en la cuenta compartida | Acceso autorizado a Cloudinary | Responsable del producto | Activado para `sarc-prod` y efecto en la cuota medido | Respaldo por carga y restauración de muestras verificados. Exportación con credencial dedicada y medición de cuota pendientes. |
| Recepción de cada aviso | Cuentas de GitHub y correo | Responsable y suplente según E0 | Registro de la prueba de recepción (`avisos.md`). La excepción de la primera instalación permite solo al responsable. | EddWlad confirmó los dos correos de prueba. Prueba de recepción vigente en el VPS. Falta el registro en el issue de GitHub. |
| Detección de caída total del VPS | VPS y Healthchecks.io | Responsable técnico | Correo recibido en menos de 15 min tras apagar el VPS en ventana acordada | Pendiente |
| Rendimiento con 30 usuarios | VPS | Responsable técnico | Medición registrada frente a los objetivos del TRD (p95 2 s) | Pendiente |
| Descarga desde el computador local real | Computador designado | Responsable del producto | Siete días seguidos con recibo | El 2026-11-13 se instaló la cuenta `sarc-backup` (SFTP enjaulado) y una descarga manual verificó 7 copias y subió sus recibos; la puerta de frescura del servidor pasó. La tarea programada diaria quedó registrada el mismo día (03:30 hora local y al iniciar sesión; prueba manual con resultado 0). El latido `descarga-local` de Healthchecks quedó configurado y probado el mismo día (comprobación en estado «up», periodo de 1 día, gracia de 2 h, aviso por correo). Faltan los siete días seguidos con recibo. |

La protección de `main`, el entorno `production` y las versiones inmutables quedaron configurados en GitHub. El ensayo de restauración con tiempo medido se mantiene pendiente junto con los siete recibos diarios.

## Verificación del servidor (2026-11-13)

Lectura del VPS con acceso autorizado del responsable: `sarc`, `sarc-postgres` y Nginx activos; readiness `UP`; sin errores de nivel `err` en 24 h; copias nocturnas, latidos y comprobaciones de disco y certificado con resultado correcto; certificado de Let's Encrypt renovado el 2026-11-11 (vigente hasta 2026-11-17) y solo 22/80/443 abiertos al exterior. Dos unidades del sistema operativo (`cloud-init`, `systemd-networkd-wait-online`) figuran fallidas sin afectar a SARC.

Se encontró y corrigió que la cuenta `sarc-backup` no estaba instalada (sin jaula SFTP ni `received/`) y que `Descargar-CopiaSarc.ps1` tenía un error de argumentos que impedía su ejecución. Las dos puertas de publicación quedaron resueltas ese mismo día: el responsable del producto ratificó la excepción permanente de un solo responsable (decisión 9 de `decisiones-previas.md`), confirmó que recibe los avisos de GitHub y de Healthchecks, y la puerta de avisos pasa en modo normal, primera y verificar; el recibo de copia local se renueva con la tarea programada diaria. Para publicar queda además el certificado: la comprobación previa exige más de 72 h de vigencia y el actual vence el 2026-11-17, así que conviene publicar tras su renovación (hacia el 15 o 16). El historial de publicaciones tiene `runId` y `approvedBy` vacíos en las dos entradas.

## Evidencia aislada de recuperación

El 2026-11-07 se verificaron en Docker el arranque de Nginx con la configuración de primera emisión, el 503 externo durante las comprobaciones HTTPS locales y el reinicio automático al detener su proceso. SARC conservó su frontend tras sustituir el índice de bienvenida de prueba y al reinstalar y recargar Nginx 1.24.0 en Ubuntu 24.04. PostgreSQL 18 recuperó una fila confirmada tras una parada inmediata y un reinicio explícito, usando un volumen desechable. Sigue pendiente el ensayo en VM con systemd, reinicio del sistema y cambio de versión del paquete de Nginx.

El JAR con perfil `prod` escuchó solo en loopback. La base de prueba quedó con 16 migraciones aplicadas, un usuario administrador, cinco roles en el catálogo y cero equipos. Al detener PostgreSQL, readiness respondió `DOWN` en 30,1 segundos. Al volver a iniciarlo, el mismo proceso Java respondió `UP` en 4,9 segundos. Tras detener y volver a arrancar Java, readiness respondió `UP` en 28,4 segundos y se conservaron el administrador y las migraciones.

## Feature 008 (intermitencias): publicada el 2026-11-13

La versión `2026.11.13-35-9937edb` se publicó el 2026-11-13 con la aprobación del responsable y quedó `COMPLETADA` (ver «Publicación» en `specs/008-intermitencias-kia/quickstart.md`). Lo que sigue abierto: el ensayo de restauración oficial que pide el *issue* `ensayo-restauracion` (7 días), los siete días de recibos de descarga y la medición con una persona de SC-001, SC-008 y SC-010. Las viñetas siguientes son el historial de lo que faltaba antes de publicar.

## Feature 008 (intermitencias): lo que faltaba con recursos reales

- **Ensayo de V018 sobre una copia restaurada de producción**: hecho el 2026-11-13 con la copia manual del 2026-11-07 (V017, 29 tablas con el mismo conteo que los metadatos) en un PostgreSQL 18 desechable y un rol de aplicación no superusuario. V018 se aplicó en menos de 1 s, creó `btree_gist` y las tablas `itm_` sin cambiar las existentes. Ya no bloquea la primera versión candidata. La clave `age` se guarda protegida con DPAPI en el computador local autorizado, fuera del control de versiones.
- **Restauración con datos de intermitencias y sus originales**: hecha el 2026-11-13 con datos sintéticos. La base se restauró con los mismos conteos y un original sintético de `imports/intermittency/` en una carpeta propia de Cloudinary salió en la exportación con su SHA-256 y en la comprobación de referencias. Falta, en la tarea T083 de la Feature 007, crear la clave de API dedicada a la exportación y activar el Backup de Cloudinary para `sarc-prod`.
- **Publicación**: solo con la autorización del responsable, el respaldo previo con recibo de menos de 24 h y `ci.yml`, `version-candidata.yml` y `publicar.yml` con la aprobación de `production`.

