# Entrega coordinada de Feature-006

Procedimiento operativo; no ejecutado contra dev ni producción. Todo ensayo de esta implementación usa PostgreSQL18 desechable y documentos simulados.

1. Fijar versiones de aplicación y artefactos frontend/backend006. Inventariar originales privados y documentos emitidos por UUID/huella/corte; conservarlos sin reescritura. Antes de la ventana, probar acceso autorizado al proveedor real: pendiente por falta de QA autorizado, no certificado con mocks.
2. Detener las escrituras de005 mediante el mecanismo de operación habitual; impedir su reanudación durante la ventana. Congelar cambios de negocio. Obtener backup completo de PostgreSQL y copia verificable de originales/documentos/configuración operativa, usando el canal de secretos autorizado. No introducir secretos en scripts/evidencias.
3. Ensayar restauración de ese respaldo en una instancia separada. `Feature006RolloutTest` prueba pg_dump/pg_restore en otra base dentro del contenedor: comparación exacta de cabeceras UUID/horarios nulos/referencias/punteros/versiones y revisiones. No equivale a una restauración del proveedor real.
4. Aplicar V016 nueva mediante Flyway con backend006 y frontend006 coordinados. No modificar V001–V015. Los candidatos pierden oficialidad efectiva antes de concluir su diagnóstico; no exponer como reales los horarios asignados.
5. Administrador/Analista: consultar cola TEMPORAL_RECONCILIATION; solicitar comprobaciones por bloques de hasta500 IDs/versiones con Idempotency-Key estable. Consultar trabajo persistente; FAILED/INTERRUPTED requieren retry explícito con versión actual del trabajo. No cambiar versiones de origen ni repetir terminales. Originales se leen fuera de la transacción de escritura.
6. Conciliar ambigüedades por versión/motivo/evidencia. Colisión409 no se resuelve por último registro ni fusionando historias. Documentar aplicados/verificados/pendientes; contrastar por apartado el dashboard y nuevos documentos. Comprobar bytes/huellas/cortes/UUID de informes emitidos anteriormente. Los registros pendientes permanecen fuera del numerador.
7. Habilitar consultas con backend/frontend006 solamente tras controles del alcance y de las autorizaciones. Conservar los respaldos y evidencia de decisiones. No publicar un porcentaje conjunto ni certificar el horario de un Excel por su semana de entrega.

## Recuperación

- Antes de nuevas escrituras de negocio006, se puede restaurar íntegramente la instantánea previa junto con los binarios y documentos que le correspondían, dentro de una ventana congelada y comprobada.
- Después de crear referencias006 o aceptar nuevas escrituras, priorizar avance compatible. No ejecutar005 contra columnas nulas ni contra candidatos cuya precisión005 ignoraría. Antes de cualquier recuperación, preservar también el estado posterior y todos los documentos nuevos; no descartar registros para volver a un binario anterior.
- No revertir únicamente el frontend o únicamente el esquema; no editar migraciones aplicadas. Un procedimiento de recuperación de datos reales requiere la instantánea y verificación operativa de esos datos, no la extrapolación del sandbox.

## Ensayo y límites

Resultados concretos del test de restauración y los gates se registran en automated-checks.md/acceptance.md al finalizar su ejecución. El ensayo no arranca Compose, no detiene procesos del usuario y no aplica transición real. El acceso al proveedor privado continúa pendiente según la decisión explícita del usuario.
