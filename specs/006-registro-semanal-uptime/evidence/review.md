# Revisión Java / Angular / PostgreSQL — 006

Fecha:2026-11-06. Alcance exclusivo006 y frontera neutral ReportPublication.

## Cambios revisados

- Domain no depende de API/aplicación/infraestructura; reglas de formato/números/temporalidad en backend. Signal Forms y facades conservan la separación actual; no motor, rutas, dependencias ni arquitectura nuevos.
- SQL de referencia filtra semana/IDs en BD; proyección JDBC masiva, sin cargar entidades/revisiones por fila del dashboard. Última referencia global antes de filtros, sin recorte366d para elegir la referencia.
- Transición con @Version, restricciones parciales UNIQUE por identidad y transacciones REQUIRES_NEW cortas. Originales fuera de escritura y cache LRU limitada a dos inspecciones por trabajo. Eventos append-only; nunca modificar revisiones para reclasificar.
- Null≠0: límites/evaluados/Ocupado desconocidos siguen nulos. Dona usa estados informados y su cobertura; no reconstruye energía/horas ni completa al nominal.
- Las salidas no exponen rutas privadas ni excepciones del proveedor. API operacional protegida por rol y CSRF; DTOs separados de persistencia. Render fuera de snapshot de lectura, sin modificar archivos previos.
- Revisión de concurrencia: conflicto UNIQUE se resuelve desde transacción nueva; progreso atómico; reintento no renueva versiones de origen. Sincronización de OpenAPI/tipos generados mediante scripts existentes.

## Defectos confirmados y corregidos durante cierre

1. Grilla por referencia incorporaba capturas24h: filtro explícito ROLLING_7D, prueba roja y luego verificación dirigida.
2. Paginación con multiplicación int podía desbordar: cálculo long en resultados y trabajo temporal.
3. Apertura conocida podía cambiar al filtrar una red vacía: resolución global antes de atribución, regresión de sin atribuir.
4. Fixtures dependían de la fecha del reloj: fecha sintética fija para consulta acotada; no se relajó la regla de producto.
5. PDF de referencia: nota tapada y metodología ponderada incorrecta para informe solo de referencia; ajuste local y nuevo render.
6. Formulario temporal: protección de cambios sin guardar reutilizando helper existente; error backend conserva borrador.

V001–V015 se contrastan SHA256 contra HEAD en automated-checks.md; V016 es nueva. No se ejecutaron migraciones sobre la BD del usuario. Los tiempos/limitaciones medidos se documentan aparte; no se añaden índices preventivos sin evidencia.
