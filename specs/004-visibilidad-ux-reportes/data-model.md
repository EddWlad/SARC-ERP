# Modelo de datos y presentación — Feature 004

Diseño, no migración ejecutada. Conservar entidades de sesiones/equipos/identidades y tablas energéticas001–003.

## 1. Perfil de acceso efectivo (calculado, no persistido por usuario)

| Campo | Regla |
|---|---|
| canReadOperational | Usuario activo con ADMINISTRADOR o ANALISTA actuales; unión OR |
| officialScope | VALIDATED para lector gerencial; no cambia cálculo oficial |
| effectiveFilters | Intersección del alcance autorizado con filtros pedidos |

No añadir roles, permisos de escritura, segmentación por red ni un perfil seleccionable por el cliente. Reutilizar sesión JDBC y revocación actual. No guardar la audiencia de un usuario como sustituto de la autorización vigente.

## 2. Proyección inmutable de consumo (no entidad JPA)

- Datos oficiales: período, totales, energía conocida/ausente, series diarias, distribuciones, ranking/posiciones, equipos sin lectura, subconjunto aprobado sobre umbral y corte.
- Sección operativa opcional: los cinco agregados y detalle excluido, solo cuando está autorizada. Ausente no significa cero.
- Evidencia oficial: justificación APPROVED vigente de las sesiones oficiales excepcionales; no todo el historial operativo.
- Series de presentación: identificador, unidad, etiquetas, valores BigDecimal/null, posiciones y estados de ausencia/cero. Conversión a coordenadas solo en renderer. No consultas ni sumas alternativas.

El DTO HTTP conserva nombres oficiales actuales y omite cinco propiedades operativas para gerenciales. El modelo entregado al renderer no incluye datos restringidos ni hojas de soporte ocultas. La respuesta de operador conserva las cinco categorías, incluso vacías.

## 3. Informe emitido (entidad existente)

Tabla `report_issued`, creada en V007. Mantener id, kind, format, file_name, scope, checksum, size_bytes, file_ref y campos de auditoría/versionado existentes.

### Extensión propuesta V011

Nombre propuesto `V011__visibilidad_reportes.sql`; antes de implementar comprobar que el número siga disponible. Nunca renumerar migraciones aplicadas para hacerlo encajar.

| Columna nueva | Tipo propuesto | Semántica |
|---|---|---|
| content_profile | VARCHAR(24), nullable en legado | OFFICIAL_ONLY o WITH_OPERATIONS para nuevas emisiones de consumo |
| cut_off | TIMESTAMPTZ, nullable en legado | Mismo instante del modelo y documento recién emitidos, almacenado UTC |
| criteria | JSONB, nullable en legado | from/to, network/type/source/equipmentId y zona operativa de esa emisión |

Validaciones técnicas: content_profile nulo o uno de los dos valores; criteria nulo u objeto JSON. Una emisión de consumo nueva debe aportar perfil, corte y criterios juntos mediante validación de aplicación; restricciones de coherencia aditivas pueden exigir que un perfil no nulo vaya acompañado de los otros campos. Migración admite filas legadas y emisores de inventario sin esos metadatos.

No añadir índice de audiencia sin necesidad de consulta: el listado sigue usando kind/created_at existentes. No añadir tablas de versiones de sesiones, motor de snapshots o tablas de gráficos por004.

### Criterios permitidos

Fechas ISO, from inclusivo/to exclusivo, enums actuales y equipmentId UUID opcional; zona America/Guayaquil. No estado operativo, texto libre sensible, URLs, secretos, hashes de sesión ni entidades serializadas. El objeto expresa filtros, no una reconstrucción histórica completa del dataset.

Mantener `scope` como texto descriptivo para UI/documento, nunca como fuente canónica de filtros. Conservar límite existente de500 caracteres y no interpretar un nombre como UUID.

### Compatibilidad de módulos

Ampliar `shared/catalog/ReportArchive.IssuedReportFile` con metadatos opcionales y constructor/fábrica compatible con inventario existente. Reporting persiste el contrato público, sin importar SessionCriteria ni entidades privadas de consumo. El nuevo resultado de emisión de consumo puede envolver ReportFile + UUID de archivo + corte para headers; no cambiar bytes de inventario.

## 4. Clasificación y autorización de originales

| Caso | Operador | Gerencial sin rol operativo |
|---|---|---|
| CONSUMPTION + OFFICIAL_ONLY | Permitir copia existente | Permitir copia existente |
| CONSUMPTION + WITH_OPERATIONS | Permitir copia existente | Bloquear original; ofrecer nueva emisión |
| CONSUMPTION legado perfil nulo | Permitir copia existente | Bloquear: renderer anterior incluía operativas |
| INVENTORY existente | Mantener permiso vigente | Mantener permiso vigente |

Para consumo desconocido/incoherente no asumir OFFICIAL_ONLY; denegar entrega gerencial hasta clasificación fiable. Validar coherencia antes de archivar nuevas emisiones. No cambiar políticas de módulos futuros no implementados.

La política se ejecuta antes de leer almacenamiento, según roles actuales. Un original no cambia al modificar roles. Una emisión nueva es otro registro: nuevo UUID, corte, checksum y copia; ninguna relación de reemplazo modifica el original. No hace falta nueva FK para ofrecerla desde la UI.

## 5. Legado y despliegue

Sin backfill de corte/criteria: created_at no prueba el corte exacto del conjunto y scope truncado no prueba filtros exactos. El fallback por kind/perfil nulo permite aplicar política sin tocar la fila histórica. Conservar bytes,checksum,referencias,ID,fechas y versión histórica.

Aplicar migración nueva antes de publicar código que usa columnas. Migración nullable compatible con versión anterior para lectura, pero no volver al código antiguo que entrega URL sin evaluar seguridad. Un rollback de aplicación no debe reabrir descargas restringidas: mantener bloqueo del endpoint de consumo o parche de autorización hasta restaurar versión corregida, sin borrar columnas/históricos.

Probar migración desde V010 con originales de ambos tipos, apertura JPA ddl-auto=validate y hashes/fechas previas. Documentar en despliegue cómo cortar emisión de URLs antiguas y verificar expiración5min/caché, sin rotar credenciales globales ni borrar archivos.

## 6. Hallazgo visual (registro QA, no tabla de negocio)

ID, pantalla/ruta, rol, estado, ancho/alto CSS, tema, navegador, pasos, impacto P0–P3, evidencia previa, causa confirmada, componente corregido, evidencia posterior, estado y limitaciones. Persistir como documentación/evidencia de feature, no como nueva entidad PostgreSQL.

Estados QA: detectado → reproducido → corregido → verificado; no reproducido/exclusión justificada permanecen explícitos. Ningún marcador equivale a una prueba que no se ejecutó.
