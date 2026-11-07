# Decisiones previas a producción (etapa E0)

**Responsable**: responsable del producto. **Requisitos previos**: ninguno; este registro es el punto de partida.

Registro de las decisiones que el responsable del producto debe tomar **antes** de cualquier acción sobre recursos reales. Mientras una decisión figure como *pendiente*, la etapa E7 (VPS real) no se inicia.

Estado de este registro: **decisiones parciales registradas**. Se rellena con fecha y nombre al aprobarse cada una. No escribir aquí valores secretos, direcciones de correo ni IP.

## Aprobación para iniciar E7

| Campo | Valor |
|---|---|
| Aprobación escrita del responsable del producto para iniciar E7 | *pendiente* |
| Fecha | — |
| Quién aprueba | — |

Sin esta aprobación no se ejecuta ninguna tarea de producción real (contratar, configurar GitHub, tocar el VPS, Healthchecks.io o Cloudinary).

## Decisiones

| # | Decisión | Opciones y criterio | Estado | Resuelta por / fecha |
|---|---|---|---|---|
| 1 | Repositorio público o privado (R11) | Mantener público. Los avisos, registros y Releases excluyen datos sensibles. | Decidida | EddWlad / 2026-11-07 |
| 2 | Computador local designado | Este PC Windows, con copias cifradas. Comprobar cifrado del destino, acceso restringido, espacio y disponibilidad diaria antes de registrar la tarea. | Decidida | EddWlad / 2026-11-07 |
| 3 | Responsable y suplente | Responsable `EddWlad`. Suplente por designar. Excepción temporal para primera instalación y su verificación: EddWlad confirma la recepción de todos los avisos. Para publicaciones normales se exige suplente y nueva prueba de recepción de ambos. | Decidida | EddWlad / 2026-11-07 |
| 4 | Cuenta de Healthchecks.io | Confirmar que el plan elegido permite el uso comercial. Verificar los destinatarios de correo según la decisión 3. | *Pendiente* | — |
| 5 | Backup de Cloudinary: alcance y cuota | La cuenta de Cloudinary es **compartida** con otro proyecto: medir el efecto del Backup en la cuota antes de activarlo para `sarc-prod`. | *Pendiente* | — |
| 6 | Rol de la cuenta de verificación posterior | Propuesta técnica: `ASISTENTE` con un reporte de verificación de indicadores oficiales. Confirmar la decisión y probar las lecturas de los cuatro módulos y la descarga. | *Pendiente* | — |
| 7 | «Prevent self-review» en el entorno `production` | Excepción temporal para que EddWlad pueda aprobar la primera instalación y su verificación con revisión obligatoria. Al designar suplente, activarlo y confirmar la revisión independiente antes de la primera publicación normal. | Decidida | EddWlad / 2026-11-07 |
| 8 | Datos de la primera instalación | Base de producción nueva. Flyway crea esquema, roles y catálogo base de conectores. Se da de alta el administrador inicial. Equipos, consumos, mediciones y demás datos operativos se crean en producción. | Decidida | EddWlad / 2026-11-07 |

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
