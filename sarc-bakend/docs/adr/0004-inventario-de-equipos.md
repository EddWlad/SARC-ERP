# ADR 0004 - Inventario de equipos (RF-002): identidad, imagenes y reportes

Estado: aceptada (23-oct-2026). Backend de RF-002.

## Identidad del equipo

- El agregado es `Equipment`; su id interno es la identidad estable. Nombre, marca y ubicacion pueden cambiar sin crear otro equipo.
- Serie, codigo asignado y UID/PUK son unicos en todo el inventario, eliminados incluidos (`UNIQUE` sin indices parciales). Si el dueno es un equipo eliminado la API responde `EQUIPMENT_DELETED_EXISTS` con su id para recuperarlo; si los identificadores pertenecen a equipos distintos responde `IDENTIFIERS_BELONG_TO_DIFFERENT_EQUIPMENT` y no fusiona nada.
- Las reglas por red y tipo viven en `EquipmentClassification` (codigo KIA de 6 digitos, serie como codigo en el resto, UID/PUK y estado de uso solo en puntos, maximo de conectores). Los `CHECK` de `V004` repiten las reglas criticas como red de seguridad.
- Un reemplazo fisico es un equipo nuevo enlazado con `replaces_equipment_id`; el anterior debe estar fuera de servicio. El historico de cada uno se conserva.
- Los conectores nunca se borran: al reducir posiciones se retiran (`active = false`) y conservan su id, para que el historico de cargas siga apuntando a ellos.

## API

- `PUT /equipment/{id}` reemplaza los datos editables con la `version` leida (bloqueo optimista via `BaseAuditableEntity.requireVersion`). Corregir la serie se audita aparte (`EQUIPMENT_SERIAL_CORRECTED`).
- `PATCH /equipment/{id}/status` cubre activar, inactivar, eliminar logicamente y recuperar. No hay borrado fisico.
- UID/PUK y URL OCPP son datos restringidos: solo administradores y analistas los ven; los reportes nunca los incluyen.
- Las identidades externas (Wallbox, Enerlink, OCPP) se registran por plataforma y alcance para conciliar importaciones; solo las no verificadas se pueden quitar.

## Imagenes

- Almacenamiento en Cloudinary con entrega `authenticated`: la referencia guardada no es publica y la API devuelve una URL firmada. Credenciales en `.env` (`CLOUDINARY_*`); sin ellas la app arranca y la subida responde `STORAGE_NOT_CONFIGURED`.
- El contenido se valida por firma (JPEG, PNG, WebP) y tamano (10 MiB), no por nombre ni `Content-Type`.
- La subida ocurre fuera de la transaccion de base de datos para no retener una conexion durante la transferencia: se verifica la version, se sube, y en una transaccion corta se asocia la imagen. Si esa transaccion falla, la imagen recien subida se borra; la imagen anterior se borra solo tras confirmar (`AfterCommit`).
- `openpdf` excluye `brotli4j`: solo sirve para leer PDF con flujos Brotli y su presencia hace que `httpclient5` (cliente de Cloudinary) pida respuestas Brotli sin cargar la libreria nativa, lo que rompia la subida.

## Reportes

- PDF (OpenPDF) y Excel (Apache POI) se generan de forma sincronica con los mismos filtros del listado, hasta 5000 equipos (`REPORT_TOO_LARGE`). Si el volumen crece se pasara a generacion asincrona.
- Base comun en `shared/report`: `CorporatePdf` y `CorporateWorkbook` aplican logos de EOS Tech y Autoeastern, color de marca, fecha de corte en `America/Guayaquil` y numeracion. Cada modulo solo arma su contenido a traves del puerto `InventoryReportRenderer`.
- Cada generacion se audita (`INVENTORY_REPORT_GENERATED`) con filtros, filas y SHA-256 del archivo, para poder demostrar que un reporte entregado no fue alterado.
- Los logos viven en `src/main/resources/reports/logos`. El de Autoeastern es una copia de baja resolucion; reemplazar por el PNG o SVG oficial cuando este disponible.
