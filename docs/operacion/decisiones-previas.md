# Decisiones previas a producción (etapa E0)

**Responsable**: responsable del producto. **Requisitos previos**: ninguno; este registro es el punto de partida.

Registro de las decisiones que el responsable del producto debe tomar **antes** de cualquier acción sobre recursos reales. Mientras una decisión figure como *pendiente*, la etapa E7 (VPS real) no se inicia.

Estado de este registro: **E7 autorizada para la primera publicación**. No escribir aquí valores secretos, direcciones de correo ni IP.

## Aprobación para iniciar E7

| Campo | Valor |
|---|---|
| Aprobación escrita del responsable del producto para iniciar E7 | EddWlad confirmó que realizó los ensayos pendientes y pidió cerrar la feature y publicar. El catálogo base se conserva por su instrucción posterior. |
| Fecha | 2026-11-07 |
| Quién aprueba | EddWlad |

La aprobación habilita la configuración de producción en GitHub y en el VPS. Las pruebas de proveedor autorizadas por el titular quedan como evidencia previa para E0. El mismo día, EddWlad autorizó la cuenta personal SSH por llave, el retiro del acceso directo de `root` tras probarla y la verificación final de la primera publicación. Las nuevas integraciones y los siguientes despliegues se gestionarán desde la Feature 008.

## Decisiones

| # | Decisión | Opciones y criterio | Estado | Resuelta por / fecha |
|---|---|---|---|---|
| 1 | Repositorio público o privado (R11) | Mantener público. Los avisos, registros y Releases excluyen datos sensibles. | Decidida | EddWlad / 2026-11-07 |
| 2 | Computador local designado | Este PC Windows, con copias cifradas. Comprobar cifrado del destino, acceso restringido, espacio y disponibilidad diaria antes de registrar la tarea. | Decidida | EddWlad / 2026-11-07 |
| 3 | Responsable y suplente | Responsable `EddWlad`. Suplente por designar. Excepción temporal para primera instalación y su verificación: EddWlad confirma la recepción de todos los avisos. Para publicaciones normales se exige suplente y nueva prueba de recepción de ambos. | Decidida | EddWlad / 2026-11-07 |
| 4 | Cuenta de Healthchecks.io | Cuenta gratuita del titular. Ocho comprobaciones de SARC configuradas con el correo del titular. Se verificó la recepción de las dos alertas de ensayo. La prueba de GitHub se registra por separado. | Decidida | EddWlad / 2026-11-07 |
| 5 | Backup de Cloudinary: alcance y cuota | SARC solicitará backup en cada subida a `sarc-prod` con `SARC_STORAGE_CLOUDINARY_BACKUP_UPLOADS=true`. Se verificaron una imagen y un archivo privado en `sarc-qa/backup`. Se confirmó la eliminación de ambos originales y de sus versiones. El ajuste global de la cuenta compartida se conserva. | Decidida | EddWlad / 2026-11-07 |
| 6 | Rol de la cuenta de verificación posterior | `ASISTENTE` con un reporte de verificación de indicadores oficiales. En QA se comprobó el acceso a los cuatro módulos y que la gestión de usuarios queda restringida. La descarga del reporte real se comprueba después del arranque. | Decidida | Verificación técnica / 2026-11-07 |
| 7 | «Prevent self-review» en el entorno `production` | Excepción temporal para que EddWlad pueda aprobar la primera instalación y su verificación con revisión obligatoria. Al designar suplente, activarlo y confirmar la revisión independiente antes de la primera publicación normal. | Decidida | EddWlad / 2026-11-07 |
| 9 | Un solo responsable en publicaciones normales | El producto lo desarrolla y opera una sola persona (EddWlad) con asistencia de Claude, sin una segunda persona que pueda actuar como suplente. Se ratifica de forma permanente la excepción `excepcion_sin_suplente=un-solo-responsable`: EddWlad confirma la recepción de todos los avisos y aprueba las publicaciones, y «Prevent self-review» queda desactivado en el entorno `production`. Se asume el riesgo de no tener revisión independiente: se compensa con CI obligatorio en `main`, el respaldo previo con recibo y la verificación posterior. Se revisará al incorporar a una segunda persona. Enmienda FR-038a y reemplaza las condiciones de las decisiones 3 y 7. | Decidida | EddWlad / 2026-11-13 |
| 8 | Datos de la primera instalación | Base de producción nueva con el administrador inicial, los roles y los seis tipos de conector de V004. Los equipos, consumos y demás datos operativos empiezan vacíos. La cuenta y el reporte técnicos de la verificación posterior se tratan aparte del primer arranque. | Decidida | EddWlad / 2026-11-07 |

## Requisitos del computador local

Decidir y registrar antes de designarlo:

- **Sistema operativo**. Si es Windows, se usan los scripts PowerShell de `deploy/computador-local/`. Si no lo es, esos scripts se reescriben en Bash con el mismo contrato (`contracts/backup-artifacts.md`).
- **Docker** o **cliente PostgreSQL 18** para ejecutar `pg_restore --list` y el ensayo de restauración.
- **Disco cifrado** y usuario restringido.
- **Espacio** para 30 copias diarias + 12 mensuales + la exportación de Cloudinary. Se mide con el tamaño de la primera copia real y se reserva con margen.
- **Clave privada `age`** con respaldo offline custodiado por el responsable.

## Cómo se cierra una decisión

(Resultado esperado: la fila queda en estado *Decidida*, con fecha y nombre.)

1. El responsable del producto elige y lo escribe en la fila (estado *Decidida*, fecha y nombre).
2. Si cambia una tarea, un flujo o la guía, se actualiza el documento afectado.
3. Cuando todas están decididas y existe la aprobación escrita, se habilita E7.

**Si no se puede decidir**: la decisión queda *Pendiente* y la etapa E7 no empieza; no se contrata ni se configura nada con valores provisionales.
