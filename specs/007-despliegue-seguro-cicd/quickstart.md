# Quickstart: validar la preparación de despliegue

**Feature**: `007-despliegue-seguro-cicd` | **Plan**: [plan.md](plan.md)

Guía de validación para cuando las etapas E1–E6 estén implementadas. Nada de esto se ejecutó todavía. Los escenarios marcados **[R]** requieren VPS, IP pública, cuentas o servicios reales y solo se ejecutan en E7 con autorización explícita.

## Requisitos previos

- Repositorio con los flujos `ci`, `version-candidata`, `publicar` y `sonda-externa`, y el entorno `production` con revisores (responsable y suplente).
- Una **VM Ubuntu desechable** (Hyper-V, WSL2 o Multipass) que haga de «VPS de ensayo», con Docker, Nginx y Java 25, creada solo para estos ensayos. Como Let's Encrypt no emite para una IP privada, la VM usa `SARC_DEPLOY_ENV=ensayo` y un certificado de una CA de prueba propia; ese modo se rechaza en producción.
- Datos sintéticos (semilla de pruebas del proyecto). Nunca la base local `pg_sarc` ni datos de producción.
- Carpeta de Cloudinary `sarc-test` con su propia clave (o sin credenciales).
- Una comprobación de prueba en Healthchecks.io para los ensayos.
- En la VM, un `/etc/sarc/avisos.conf` ficticio y su `prueba-recepcion.ok` con la huella correspondiente, y un `/etc/sarc/verificacion.env` (0600 root) con la credencial de una cuenta de verificación de ensayo, que se rellena en el escenario 5b tras crear esa cuenta.

Antes de empezar, anotar los conteos de la base local de desarrollo (SC-006) para compararlos al final.

## Escenarios

| # | Escenario | Pasos | Resultado esperado | Ref. |
|---|---|---|---|---|
| 1 | CI verde | Abrir PR sin cambios funcionales | Todas las comprobaciones en verde | SC-001 |
| 2 | CI rojo | PRs de ensayo: prueba rota; `V001` modificada; dependencia con vulnerabilidad alta; secreto ficticio | Cada uno rojo con la comprobación nombrada; ningún Release | SC-002, FR-018 |
| 2b | Aviso de CI | Un PR rojo del escenario 2 desde una rama del propio repositorio; en un repositorio de ensayo, un `push` rojo a `main` | Comentario en el PR e *issue* `alerta-operativa` que mencionan a ambos, sin secretos ni salida de comandos | FR-038 |
| 3 | Versión candidata | Integrar en `main` | Release pre-release con manifiesto y `SHA256SUMS`; sumas iguales al recalcular | SC-003 |
| 4 | Sin aprobación | Lanzar `publicar` contra la VM y no aprobar | `desplegar` nunca corre; la VM no cambia | SC-004 |
| 5 | Con aprobación | Aprobar como suplente | Publicación `COMPLETADA`; aprobador y versión en el historial | SC-004, SC-011 |
| 5b | Primera instalación | VM con base vacía; `publicar` con `modo: primera-instalacion`; crear cuenta y reporte de verificación; `publicar` con `modo: verificar` | `EN_CURSO` (código 0, sin mantenimiento, con ingreso, módulos y archivos `PENDIENTE`) y una publicación normal rechazada con 10; tras `verificar`, `COMPLETADA`; un `verificar` con la credencial errónea da `FALLIDA` (21) con mantenimiento | FR-015, FR-016 |
| 6 | Concurrencia | Lanzar dos `publicar` | La segunda espera o falla con código 10 | FR-014 |
| 7 | Configuración ausente | Quitar `CLOUDINARY_API_SECRET` en la VM y publicar | Falla en paso 2 (código 12) sin mostrar valores | FR-025 |
| 8 | Copia vieja | Retrasar el recibo > 24 h | Falla en paso 4 (código 14) | FR-043 |
| 8b | Prueba de recepción vencida | Cambiar `avisos.conf` sin repetir la prueba de recepción | Falla en paso 2b (código 18); nada cambia | FR-038a |
| 9 | Migración fallida | Versión de ensayo (rama desechable) con migración que falla | `FALLIDA` (código 20), mantenimiento activo, informa última migración; recuperar con copia previa según la guía | SC-008 |
| 10 | Copia y descarga | Ejecutar copia nocturna y la tarea del computador local | Archivo cifrado, suma, metadatos, recibo y latido | FR-039a |
| 11 | Computador local apagado | No ejecutar la descarga | Aviso `descarga-local` en la comprobación de prueba | FR-038 |
| 12 | Restauración aislada | Seguir [backup-artifacts.md](contracts/backup-artifacts.md#ensayo-de-restauración) | 100 % de comprobaciones; tiempo registrado | SC-009, SC-013 |
| 13 | Archivos | En `sarc-test`: subir, exportar, borrar una imagen y restaurarla | Referencia resuelve tras restaurar | SC-010 |
| 13b | Control de ensayos | En un repositorio de ensayo, *issue* `ensayo-restauracion` cerrado con `aprobado`; ejecutar `control-ensayos` por `workflow_dispatch` con umbrales de ensayo de 0 días | *Issue* «Ensayo de restauración vencido» que menciona a ambos; sin umbrales de ensayo, ningún aviso | SC-018 |
| 14 | Exposición | Escanear la VM desde otra máquina | Solo 22/80/443; `/actuator/**` 404 salvo readiness | SC-007 |
| 15 | Regresión | CI completo | Suites 001–006 sin cambios | SC-015 |
| 16 | Persona ajena | Entregar la guía a alguien sin contexto y pedir escenarios 5, 9 y 12 | Completa sin ayuda en una jornada | SC-012 |
| 17 **[R]** | Certificado | Staging, luego real; forzar renovación; simular vigencia < 72 h | Reconocido sin advertencias; aviso y bloqueo | SC-016 |
| 18 **[R]** | Caída | Detener servicio; luego apagar el VPS en ventana acordada | Correo y *issue* a ambos en < 15 min | SC-017 |
| 19 **[R]** | Recepción de avisos | Disparar cada tipo | Ambos confirman en `prueba-recepcion` | FR-038a |
| 20 **[R]** | Auto Backup y Backup de Cloudinary | Revisar panel de Contabo y configuración de Cloudinary | Puntos diarios visibles; Backup activo y cuota medida | FR-039, FR-041 |

Al terminar: comparar los conteos de la base local (sin cambios), destruir la VM y guardar la evidencia en `specs/007-despliegue-seguro-cicd/evidence/`, sin secretos.
