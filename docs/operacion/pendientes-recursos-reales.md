# Verificaciones pendientes de recursos reales

Lo que **no puede comprobarse** hasta que existan el VPS, los accesos autorizados o los servicios externos. Todo lo demás de la guía se ensayó con datos sintéticos, en CI o en una VM desechable. Hasta que cada fila de esta tabla esté cerrada, SARC **no** se presenta como listo para producción en ese aspecto.

**Responsable**: responsable del producto. **Estado de este documento**: ninguna verificación cerrada. Al cerrar una, anotar fecha y evidencia (sin secretos).

| Verificación | Depende de | Responsable | Condición de cierre | Estado |
|---|---|---|---|---|
| Emisión y renovación del certificado para la IP | VPS con IP pública, puerto 80 accesible | Responsable técnico | Certificado reconocido y renovación forzada exitosa (`certificado.md`) | Pendiente |
| Puertos expuestos desde Internet | VPS | Responsable técnico | Escaneo externo: solo 22/80/443 (`acceso-y-exposicion.md`) | Pendiente |
| Alcance real del Auto Backup (puntos, restauración) | Contratación | Responsable del producto | El panel muestra puntos diarios | Pendiente |
| Backup de Cloudinary: alcance y cuota en la cuenta compartida | Acceso autorizado a Cloudinary | Responsable del producto | Activado para `sarc-prod` y efecto en la cuota medido | Pendiente |
| Recepción de cada aviso por ambos | Cuentas de GitHub y correo | Responsable y suplente | Registro de la prueba de recepción (`avisos.md`) | Pendiente |
| Detección de caída total del VPS | VPS y Healthchecks.io | Responsable técnico | Correo recibido en menos de 15 min tras apagar el VPS en ventana acordada | Pendiente |
| Rendimiento con 30 usuarios | VPS | Responsable técnico | Medición registrada frente a los objetivos del TRD (p95 2 s) | Pendiente |
| Descarga desde el computador local real | Computador designado | Responsable del producto | Siete días seguidos con recibo | Pendiente |

También dependen de recursos reales, aunque no son verificaciones de un servicio: la protección de `main`, el entorno `production` con sus revisores y las protecciones de *Releases* o *tags* inmutables en la configuración de GitHub, la primera publicación aprobada y el primer ensayo de restauración con tiempo medido.
