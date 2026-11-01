# Almacenamiento privado 004

Ensayo real contra Cloudinary, 2026-11-01. `Feature004ProviderRehearsal`, opt-in con `-Dsarc.qa.provider=true`, PostgreSQL desechable y reporte PDF sintético. Autorizado por el responsable del proyecto con las credenciales de `sarc-bakend/.env`. Carpeta propia `sarc-qa004-t066`. Registra estados, nunca URLs firmadas.

| Comprobación | Resultado |
|---|---|
| Descarga autenticada por SARC | 200, bytes iguales al emitido |
| Enlace del proveedor en la respuesta | Sin `Location`, ninguna cabecera lo menciona |
| Sin firma, entrega `authenticated` | 401 |
| Sin firma, tipos `upload` y `private` | 404 |
| URL firmada recién creada | 200, sin redirección |
| Misma URL a los 5 min 30 s | 401: caducó y la caché o CDN no la sirvió |
| Descarga por SARC tras caducar | 200, bytes iguales: SARC firma de nuevo por petición |

Verificado además sin proveedor real:

- Respuesta distinta de 200 da 503 sin filtrar URL ni secreto: `CloudinaryFileStorageTest`.
- Copia alterada no se entrega: 503 `ISSUED_REPORT_COPY_MISMATCH`.

## Límites

- La cuenta no es QA dedicada: el mismo `cloud_name` lo usa MedicApp. El ensayo solo añadió archivos en su carpeta y no leyó, cambió ni borró otros.
- Queda un PDF sintético por corrida en `sarc-qa004-t066`, sin borrar.
- Probado con reporte PDF. Originales de importación comparten `readDocument` pero no se ensayaron.
- Cuerpo detenido contra el proveedor real: no se puede provocar. El corte a 60 s existe por código, sin prueba contra Cloudinary.
- Caché comprobada una vez, a los 5 min 30 s.
