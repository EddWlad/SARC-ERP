# ADR 0005 - Consumo diario (RF-003): importacion, conciliacion e indicadores

Estado: aceptada (23-oct-2026). Backend de RF-003.

## Modulo y fronteras

- Modulo nuevo `consumption` (TRD 03.2). Como ArchUnit prohibe dependencias entre modulos, inventario publica el puerto `shared/catalog/EquipmentDirectory` (series, identidades externas verificadas, equipos por id) y lo implementa `EquipmentDirectoryService`. Consumo nunca toca entidades ni repositorios de inventario.
- `ChargingNetwork`, `InfrastructureType` y `ExternalPlatform` pasan a `shared/domain`: son vocabulario comun de ambos modulos.
- La sesion guarda `network` e `infrastructure_type` copiados del equipo al vincularla. Ambos son inmutables en RF-002, asi los indicadores filtran por red sin unir tablas de otro modulo. `energy_session.equipment_id` si tiene FK a `asset_equipment` (misma base, integridad garantizada).
- Nuevo rol de paquete `infrastructure/parser` (adaptadores de formatos externos) y `domain/model` partido por agregado (`session`, `batch`), como pide la ADR 0002 al superar ~10 clases.

## Motor de importacion (en vez de Spring Batch)

- Decision del usuario: motor propio en lugar del Spring Batch que sugiere el TRD. Menos infraestructura (sin tablas de metadatos) y suficiente para el objetivo de 250.000 registros/mes.
- `shared/importing` ofrece lo reutilizable por RF-004/RF-005: lectura tabular (`TabularReader`: XLSX/XLS, CSV, pegado), `HeaderLayout` (columnas por nombre, sin importar orden ni extras), `DecimalText`, `DateTimeText` e `ImportJobs` (pool de 2 hilos que propaga el usuario a la auditoria).
- Estados y clasificaciones van como VARCHAR con CHECK (igual que V004) y no como ENUM de PostgreSQL: agregar un valor no exige `ALTER TYPE`. Las observaciones de cada fila son JSONB con CHECK de arreglo; no se consultan por contenido, por eso no llevan indice GIN.
- Cada modulo persiste sus lotes y filas con columnas tipadas (`energy_import_batch`, `energy_import_row`), en lugar del `imp_import_row` generico con JSON del TRD. Si RF-004/005 repiten el mismo ciclo de vida, se extrae a `shared`.
- Ciclo: `PARSING -> READY -> CONFIRMING -> COMPLETED`, o `FAILED`/`DISCARDED`. Interpretar y confirmar corren en segundo plano; la API responde 202 y el cliente consulta el lote.
- Confirmar procesa tramos de 500 filas por transaccion. Cada fila registra su resultado (`INSERTED`, `CORRECTED`, `UNCHANGED`, `SKIPPED`) en la misma transaccion que escribe la sesion, por eso reintentar continua donde quedo. La clave unica `(source, source_scope, external_session_id)` impide duplicar aunque dos lotes coincidan.
- Al arrancar, los lotes que quedaron en proceso se marcan fallidos (`IMPORT_INTERRUPTED`). Supone una sola instancia del backend, como el despliegue del TRD.
- El original se guarda en Cloudinary como documento privado (`raw`, entrega autenticada) con su SHA-256. No se expone descarga.

## Reglas de los datos

- Fuentes: Wallbox identifica el cargador por su serie, la misma del inventario. Enerlink lo identifica por ACTIVO + CARGADOR, que deben registrarse en el inventario como identidad externa verificada. Solo se concilia con claves confiables; lo demas queda `PENDING_LINK` y se reintenta con `POST /energy/sessions/link-pending`.
- Fechas: Wallbox sin zona (se toma America/Guayaquil); Enerlink declara la zona en el encabezado ("INICIO (UTC-05:00)"). Se guarda el instante UTC y el dia local de inicio (`start_date`), que es el dia en que se cuenta la sesion.
- Numeros: cada fuente declara su separador decimal (Wallbox coma, Enerlink punto). Un valor con el otro separador se acepta si no hay duda; "1.234" en una fuente con coma decimal se rechaza como ambiguo.
- Clasificacion de la vista previa: `NEW`, `IDENTICAL`, `MODIFIED` (requiere aprobacion; la version anterior se guarda en `energy_session_revision`), `INVALID`, `DUPLICATE` (mismo ID antes en el lote), `PENDING_LINK`.
- Fuente no preferente (PRD 7.2) segun la red y el tipo del equipo: la fila requiere aprobacion para no contar dos veces una carga reportada por ambas plataformas.
- Pruebas: el analista las marca en la vista previa o despues en el historico. Se conservan y se informan aparte.

## Indicadores y reportes

- `ConsumptionCalculator` es la unica fuente de los KPI: tablero, PDF y Excel usan el mismo calculo y el mismo filtro (CA-003-05). Solo suman las sesiones `VALIDATED`; pendientes y pruebas se informan aparte. El periodo es `[from, to)` por dia de inicio, hasta 366 dias.
- `CorporateWorkbook` pasa a escritura en streaming (SXSSF) con ancho de columna por longitud de texto: el detalle de transacciones puede tener decenas de miles de filas y el autoajuste de POI era el cuello de botella (9 s a 1,6 s con 5.000 sesiones).

## Pendiente

- Aviso a analistas de sesiones pendientes de vincular: depende del modulo de notificaciones (RF-006). Mientras tanto, los conteos de pendientes aparecen en la vista previa, el tablero y los reportes.
- Los reportes cargan en memoria hasta 100.000 sesiones (`REPORT_TOO_LARGE` por encima); un periodo mayor se divide. Si el volumen real lo exige, se pasara a lectura por cursor hacia el libro en streaming.
- `POST /energy/sessions/{id}/correct` (correccion manual) del TRD no se implemento: las correcciones llegan por reimportacion aprobada, que conserva la version anterior.
