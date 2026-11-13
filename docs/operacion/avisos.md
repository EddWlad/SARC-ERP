# Avisos, latidos y prueba de recepción

Qué avisos existen, cómo llegan al **responsable y al suplente** y cómo se comprueba que realmente los reciben. Referencia de contrato: `specs/007-despliegue-seguro-cicd/contracts/health-and-alerts.md`.

**Responsable**: responsable del producto (destinatarios) y responsable técnico (comprobaciones). **Requisitos previos**: cuenta de Healthchecks.io del proyecto, cuentas de GitHub y correo del responsable y del suplente. Nunca se escriben en este repositorio direcciones de correo, URL de latido ni valores secretos.

## Tabla de avisos

| Comprobación | Origen | Canal | Periodo / gracia | Condición |
|---|---|---|---|---|
| `ci-fallo-pr` | Trabajo `avisar-fallo` de CI | Comentario en el PR que menciona a ambos | — | Comprobación roja en un PR del propio repositorio (en un PR desde un *fork* solo la comprobación visible) |
| `ci-fallo-main` | Trabajo `avisar-fallo` de CI | *Issue* `alerta-operativa` «CI fallido en main» (menciona y asigna a ambos) | — | Cualquier trabajo rojo en `main` |
| `ensayo-vencido` | Flujo `control-ensayos` | *Issue* `alerta-operativa` | Diario | Último ensayo aprobado (o `SARC_PRODUCCION_DESDE`) con 28 días (por vencer) o 31 días (vencido); ensayo `pendiente` abierto más de 7 días |
| `publicacion-fallida` | Flujo `publicar` | *Issue* `alerta-operativa` + correo | — | Trabajo falla o se cancela |
| `aprobacion-pendiente` | Entorno `production` | Notificación de GitHub a los revisores | — | Publicación esperando aprobación |
| `vps-latido` | Temporizador del VPS | Correo (Healthchecks.io) | 5 min / 5 min | Falta el latido: VPS caído o sin red |
| `servicio` | Temporizador del VPS (readiness local) | Correo | 5 min / 5 min | `DOWN` o sin respuesta |
| `sonda-externa` | Flujo programado de GitHub (TLS validado) | *Issue* + correo | 15 min | Falla HTTPS desde Internet |
| `copia-nocturna` | Script de copia | Correo | 24 h / 2 h | Falta o `/fail` |
| `descarga-local` | Tarea del computador local | Correo | 24 h / 2 h | Falta o `/fail` |
| `certificado` | Comprobación diaria | Correo | 24 h / 2 h | Vigencia < 72 h, renovación fallida o falta |
| `disco` | Comprobación horaria | Correo | 1 h / 30 min | Uso > 80 % o falta |

**Plazo (SC-017)**: con periodo de 5 min y gracia de 5 min para `vps-latido` y `servicio`, Healthchecks.io avisa como máximo **10 min después del último latido** recibido; el margen restante hasta 15 min cubre el envío del correo. Cuando el servidor sigue vivo, `servicio` envía `/fail` inmediato sin esperar la gracia.

**Contenido de los avisos**: nombre de la comprobación, estado y hora. Nunca valores de configuración, salidas de comandos, rutas con datos ni datos personales: los *issues* son públicos mientras el repositorio lo sea.

## Configurar responsable y suplente

Para el primer arranque y su verificación se admite la excepción temporal registrada en `decisiones-previas.md`. El titular conserva la aprobación obligatoria y confirma todos los avisos. `SARC_SUPLENTE` queda vacío hasta designar a la segunda persona. Las publicaciones normales requieren las dos confirmaciones, salvo la excepción permanente `un-solo-responsable` ratificada el 2026-11-13 (decisión 9 de `decisiones-previas.md`) mientras no exista una segunda persona.

1. **Variables de repositorio de GitHub**: `SARC_RESPONSABLE` y `SARC_SUPLENTE` con sus usuarios de GitHub (sin `@`). Los flujos los mencionan y asignan en los *issues*.
2. **Healthchecks.io**: en cada comprobación de la tabla, añadir a ambas personas como destinatarias del correo (integración de correo). Definir el periodo y la gracia indicados.
3. **GitHub**: ambas personas deben tener activadas las notificaciones por correo *participating* (menciones y asignaciones). Es la forma de que un *issue* o comentario llegue por correo.
4. **`/etc/sarc/avisos.conf`** (en el VPS, fuera del repositorio): lista los destinatarios y los canales vigentes. Su huella SHA-256 liga la prueba de recepción: si cambia, la prueba deja de ser vigente.

En `avisos.conf`, declarar `responsable_github=<login>` y `suplente_github=<login>`. Durante la excepción, dejar `suplente_github=` y añadir `excepcion_sin_suplente=primera-instalacion`. Con la excepción permanente de un solo responsable, dejar `suplente_github=` y declarar `excepcion_sin_suplente=un-solo-responsable`. Al incorporar al suplente, asignar su login, retirar la excepción, activar «Prevent self-review» y repetir la prueba con ambos.

Resultado esperado: cualquier fallo de la tabla notifica a las dos personas por al menos un canal en el plazo indicado.

## Prueba de recepción (FR-038a)

La publicación **no** se permite sin una prueba de recepción vigente: `sarc-deploy` falla con el código **18** en el paso 2b si `/var/lib/sarc/prueba-recepcion.ok` no existe, su huella no coincide con `/etc/sarc/avisos.conf` o falta la confirmación de alguna persona.

El 2026-11-07, EddWlad confirmó por escrito la recepción de los dos correos de ensayo de Healthchecks.io, por fallo explícito y por falta de latido. El proveedor detectó ambos fallos y las dos comprobaciones de ensayo quedaron pausadas. Siguen pendientes la prueba del canal de GitHub, el registro formal conjunto y la instalación del comprobante en el VPS.

Se hace antes de la primera publicación y cada vez que cambian los destinatarios o los canales:

1. Abrir un *issue* `prueba-recepcion` en el repositorio y mencionar a los destinatarios vigentes.
2. Disparar **cada tipo de aviso** con una comprobación de prueba: un *issue* de alerta de prueba (GitHub), un `/fail` manual de una comprobación de Healthchecks.io de prueba y el envío de latido `vps-latido` detenido (correo).
3. Cada persona confirma **por escrito en el *issue*** qué avisos recibió (sin copiar contenido sensible).
4. Con las confirmaciones requeridas, en el VPS crear `/var/lib/sarc/prueba-recepcion.ok` con la fecha, la huella SHA-256 actual de `/etc/sarc/avisos.conf` y la confirmación de cada persona. Formato habitual (una clave por línea):

```text
fecha=<AAAA-MM-DDThh:mm:ssZ>
huella=<SHA-256 de /etc/sarc/avisos.conf>
confirmado=<usuario de GitHub del responsable>
confirmado=<usuario de GitHub del suplente>
```

   La huella se obtiene con `sha256sum /etc/sarc/avisos.conf`.

5. Cerrar el *issue* `prueba-recepcion`.

Si alguien no recibe un tipo de aviso: corregir el canal (destinatario, notificaciones de GitHub, filtros de correo) y **repetir** la prueba. La publicación permanece bloqueada.

## Revisiones periódicas

- **Mensual**: comprobar que los flujos programados `sonda-externa` y `control-ensayos` siguen activos (pestaña *Actions*). GitHub desactiva los flujos programados tras 60 días sin actividad en el repositorio; si aparecen desactivados, reactivarlos.
- **Semanal**: abrir el panel de Auto Backup de Contabo y comprobar que hay puntos diarios recientes (no existe un aviso automático documentado).
- **Tras cambiar al responsable o suplente**: repetir la prueba de recepción (ver `publicar.md`).

## Limitaciones declaradas

- Las notificaciones nativas de GitHub Actions llegan al autor del cambio o de la ejecución, no a ambas personas: por eso se usan *issues* y comentarios con menciones.
- Los flujos programados de GitHub pueden retrasarse; los latidos de Healthchecks.io son la fuente principal para detectar la caída del VPS.
- Healthchecks.io gratuito no tiene SLA: el plan se confirma en `decisiones-previas.md`.

## Si falla

Un aviso de la tabla no llega: revisar primero el estado de la comprobación en Healthchecks.io y la carpeta de correo no deseado; después repetir la prueba de recepción. Si el problema persiste, avisar al responsable del producto.
