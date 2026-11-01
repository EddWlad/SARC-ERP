# Despliegue 004

Borrador: el ensayo de proveedor sigue pendiente (`evidence/private-storage-results.md`). No ejecutar en producción sin ese ensayo.

## Antes

1. Respaldar la base de datos.
2. Ensayar en QA: descarga autenticada, URL antigua caducada, sin enlace del proveedor en el navegador.
3. Confirmar que `report_issued` no se reescribe: V011 solo añade columnas nulas y restricciones.
4. Elegir ventana sin importaciones: V012 crea un índice en `energy_session` y bloquea escrituras en esa tabla mientras se construye.

## Despliegue

1. Desplegar backend: Flyway aplica V011 y V012 al arrancar.
2. Desplegar frontend: `GET /reports/issued/{id}/download` ya entrega binario, no JSON con URL; un frontend antiguo deja de descargar. Publicar ambos juntos.
3. Comprobar con un gerente: original con operativas 403 y nueva emisión; con un analista: descarga exacta.

Reportes legados tienen perfil nulo y quedan restringidos para roles oficiales.

## Rollback

1. Revertir frontend y backend juntos a la versión anterior **solo tras** retirar el endpoint que devolvía URL firmada del despliegue: restaurarlo sin más reabre la entrega insegura. Si no se puede, corregir hacia adelante.
2. No revertir V011 ni V012: columnas e índice son aditivos y la versión anterior los ignora con `validate`. No borrar metadatos.
3. Los documentos emitidos no se modifican ni eliminan.

No publicar, rotar secretos ni modificar originales de producción como parte de este ensayo.
