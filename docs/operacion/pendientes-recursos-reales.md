# Verificaciones pendientes de recursos reales

Esta tabla recoge las comprobaciones que requieren el VPS, los accesos autorizados o los servicios externos. La guía de ensayo sigue siendo el criterio para las pruebas en VM desechable.

**Responsable**: responsable del producto. **Estado de este documento**: ninguna verificación cerrada. Al cerrar una, anotar fecha y evidencia (sin secretos).

| Verificación | Depende de | Responsable | Condición de cierre | Estado |
|---|---|---|---|---|
| Emisión y renovación del certificado para la IP | VPS con IP pública, puerto 80 accesible | Responsable técnico | Certificado reconocido y renovación forzada exitosa (`certificado.md`) | Pendiente |
| Puertos expuestos desde Internet | VPS | Responsable técnico | Escaneo externo: solo 22/80/443 (`acceso-y-exposicion.md`) | Pendiente |
| Alcance real del Auto Backup (puntos, restauración) | Contratación | Responsable del producto | El panel muestra puntos diarios | Pendiente |
| Backup de Cloudinary: alcance y cuota en la cuenta compartida | Acceso autorizado a Cloudinary | Responsable del producto | Activado para `sarc-prod` y efecto en la cuota medido | Pendiente |
| Recepción de cada aviso | Cuentas de GitHub y correo | Responsable y suplente según E0 | Registro de la prueba de recepción (`avisos.md`). La excepción de la primera instalación permite solo al responsable. | Pendiente |
| Detección de caída total del VPS | VPS y Healthchecks.io | Responsable técnico | Correo recibido en menos de 15 min tras apagar el VPS en ventana acordada | Pendiente |
| Rendimiento con 30 usuarios | VPS | Responsable técnico | Medición registrada frente a los objetivos del TRD (p95 2 s) | Pendiente |
| Descarga desde el computador local real | Computador designado | Responsable del producto | Siete días seguidos con recibo | Pendiente |

También dependen de recursos reales, aunque no son verificaciones de un servicio: la protección de `main`, el entorno `production` con sus revisores y las protecciones de *Releases* o *tags* inmutables en la configuración de GitHub, la primera publicación aprobada y el primer ensayo de restauración con tiempo medido.

## Evidencia aislada de recuperación

El 2026-11-07 se verificaron en Docker el arranque de Nginx con la configuración de primera emisión, el 503 externo durante las comprobaciones HTTPS locales y el reinicio automático al detener su proceso. SARC conservó su frontend tras sustituir el índice de bienvenida de prueba y al reinstalar y recargar Nginx 1.24.0 en Ubuntu 24.04. PostgreSQL 18 recuperó una fila confirmada tras una parada inmediata y un reinicio explícito, usando un volumen desechable. Sigue pendiente el ensayo en VM con systemd, reinicio del sistema y cambio de versión del paquete de Nginx.

El JAR con perfil `prod` escuchó solo en loopback. La base de prueba quedó con 16 migraciones aplicadas, un usuario administrador, cinco roles en el catálogo y cero equipos. Al detener PostgreSQL, readiness respondió `DOWN` en 30,1 segundos. Al volver a iniciarlo, el mismo proceso Java respondió `UP` en 4,9 segundos. Tras detener y volver a arrancar Java, readiness respondió `UP` en 28,4 segundos y se conservaron el administrador y las migraciones.
