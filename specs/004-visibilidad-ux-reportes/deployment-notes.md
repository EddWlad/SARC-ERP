# Despliegue 004

Borrador: el ensayo de proveedor sigue pendiente (`evidence/private-storage-results.md`). No ejecutar en producción sin ese ensayo.

## Antes

1. Respaldar la base de datos.
2. Ensayar en QA: descarga autenticada, URL antigua caducada, sin enlace del proveedor en el navegador.
3. Confirmar que `report_issued` no se reescribe: V011 solo añade columnas nulas y restricciones.

## Despliegue

1. Desplegar backend: Flyway aplica V011 al arrancar.
2. Desplegar frontend: `GET /reports/issued/{id}/download` ya entrega binario, no JSON con URL; un frontend antiguo deja de descargar. Publicar ambos juntos.
3. Comprobar con un gerente: original con operativas 403 y nueva emisión; con un analista: descarga exacta.

Reportes legados tienen perfil nulo y quedan restringidos para roles oficiales.

## Rollback

1. Revertir frontend y backend juntos a la versión anterior **solo tras** retirar el endpoint que devolvía URL firmada del despliegue: restaurarlo sin más reabre la entrega insegura. Si no se puede, corregir hacia adelante.
2. No revertir V011: sus columnas son aditivas y la versión anterior las ignora con `validate`. No borrar metadatos.
3. Los documentos emitidos no se modifican ni eliminan.

No publicar, rotar secretos ni modificar originales de producción como parte de este ensayo.
