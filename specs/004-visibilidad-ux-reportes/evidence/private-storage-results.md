# Almacenamiento privado 004

**Validación externa pendiente.** No hay entorno QA autorizado del proveedor ni credenciales, según `qa-environment.md`. No se usaron credenciales reales ni se tocaron originales.

Verificado sin proveedor real:

- El navegador recibe bytes de SARC, nunca URL del proveedor: `IssuedReportAccessApiTest`, QA05, QA06.
- Sin redirecciones, respuesta distinta de 200 da 503 sin filtrar URL ni secreto: `CloudinaryFileStorageTest`.
- Copia alterada no se entrega: 503 `ISSUED_REPORT_COPY_MISMATCH`.

Sin verificar: descarga real, caducidad de URLs antiguas a 5 minutos, caché del proveedor o CDN, comportamiento ante un cuerpo detenido contra el proveedor real.
