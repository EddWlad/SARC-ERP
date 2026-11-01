# Guía de validación — Feature 004

Guía para implementación/QA posterior, no registro de pruebas realizadas. Consultar [contratos](contracts/api-ui-reporting.md), [modelo](data-model.md) y [auditoría visual](visual-audit-plan.md). No incluye código completo de implementación ni suites.

## Prerrequisitos y aislamiento

- Java25, Maven Wrapper, Node/npm compatibles con el lockfile actual y Docker con imagen PostgreSQL18 para Testcontainers.
- Ejecutar desde la raíz del repositorio; revisar cambios/servicios existentes antes de iniciar QA. No arrancar Spring Boot sin aislamiento: el perfil por defecto es dev y puede usar/migrar la BD local.
- TestcontainersConfiguration ya proporciona PostgreSQL18 desechable. Crear durante implementación fixture opt-in `Feature004BrowserSandbox` siguiendo el patrón de Feature003BrowserSandbox, con puerto backend aleatorio, usuarios de cinco roles, almacenamiento simulado que retenga bytes para comprobar descargas y datos sintéticos.
- Incluir `@ServiceConnection`, deshabilitar Docker Compose en procesos QA y no usar volumen `pg_sarc` ni puerto5432 dev. No copiar secretos Cloudinary al sandbox. No subir documentos QA al proveedor real.
- Fixture nueva debe generar proxy/ready/done en `sarc-bakend/target/feature004-*`, credenciales temporales solo de QA y limpieza al cerrar. Estos archivos/fixture son trabajo futuro; no existen por este plan.
- Para verificación real de almacenamiento privado, usar entorno/carpeta QA independiente autorizado, nunca documentos/históricos locales. Distinguir explícitamente evidencia de mock y proveedor real.

## Comandos de comprobación existentes

Desde la raíz, comandos por separado:

```powershell
git status --short
Push-Location sarc-bakend
./mvnw.cmd verify
Pop-Location
Push-Location sarc-frontend
npm ci
npm run api:types
npm test -- --watch=false
npm run lint
npm run build
Pop-Location
```

No ejecutar npm ci innecesariamente si dependencias/lockfile ya están instalados y verificados. api:types procede cuando OpenAPI de la implementación esté actualizado; revisar su diff, no editar generado a mano. Durante esta planificación no se ejecutaron estos comandos.

El build exitoso no prueba SC-010: registrar tamaño inicial sin comprimir y exigir≤500kB; la configuración actual solo hace warning a500kB/error a1MB. Conservar lazy loading y presupuesto de estilos.

## Recorrido de navegador (después de implementar la fixture)

En una terminal desde `sarc-bakend`:

```powershell
./mvnw.cmd '-Dsarc.qa.browser=true' '-Dspring.docker.compose.enabled=false' '-Dtest=Feature004BrowserSandbox' test
```

Esperar archivo ready y leer el puerto/proxy generados; no asumir8080. En otra terminal desde frontend, si4201 está libre:

```powershell
npm start -- --host 127.0.0.1 --port 4201 --proxy-config ../sarc-bakend/target/feature004-browser-proxy.json
```

Si4201 está ocupado elegir otro y registrarlo, sin detener procesos ajenos. El proxy debe apuntar exclusivamente al puerto aleatorio de la fixture. Mantener servicios mientras dure QA, producir evidencia y escribir marcador done solo al terminar; la fixture cierra su contenedor. No ejecutar la fixture003 de treinta minutos suponiendo que es004.

## Datos de prueba mínimos

- Sesiones oficiales con lectura conocida,0 real y energía/término ausentes; inicio válido en ambas fuentes.
- Cinco categorías operativas con valores reconocibles y casos de precedencia003; repetir escenario con las cinco en0.
- Sesión oficial >1.000 aprobada con justificación vigente, con rechazo histórico previo; otra pendiente/rechazada y una de prueba aprobada energéticamente.
- Ranking de menos de5, exactamente5 y más de5 por empate; nombres largos, muchos empatados,366 días y filas más allá de ventana SXSSF200.
- Originales previos CONSUMPTION e INVENTORY con bytes/hash/fechas registrados; nuevos OFFICIAL_ONLY y WITH_OPERATIONS; archivo ausente y storage caído.
- Usuarios cinco roles y combinaciones Analista+Gerente, Admin+Asistente, Gerente+Supervisor+Asistente; cuenta que pierde rol operativo y sesión previa.

Los datos sintéticos están confinados al sandbox. Para archivos aportados por el usuario, copiarlos o leerlos como entrada sin modificarlos; el recorrido no debe importar sobre BD dev.

## Escenarios y resultados esperados

| ID | Acción | Resultado | Requisitos |
|---|---|---|---|
| QA01 | Consultar summary por cinco roles y combinaciones | Solo operadores reciben las cinco propiedades; todos mismos oficiales/aprobadas. Ausencia de propiedad no es0 | FR-001/002/004/006; SC-001/002 |
| QA02 | Consultar sesiones sin estado y manipular state/energyReview/text/equipmentId/página | Gerencial solo oficiales; filtro operativo explícito403; totalElements y páginas sin datos internos | FR-002/009; SC-001 |
| QA03 | Pedir revisión por UUID no oficial/inexistente y oficial con rechazo previo | Gerencial404 uniforme en no oficial/inexistente; solo aprobación vigente en oficial. Operador conserva todo | FR-002/006/009 |
| QA04 | Emitir PDF/XLSX con mismo conjunto/filtros/corte controlados | Cifras/series/tablas iguales; gerente sin operativas en ninguna parte; operador con cinco categorías | FR-003/004/016/017; SC-002/007/008 |
| QA05 | Descargar original previo restringido, enlace compartido y sin sesión | Gerencial403 con motivo/acción, sin lectura storage; anónimo401; operador original exacto | FR-007/008/009; SC-003 |
| QA06 | Descargar inventario anterior y OFFICIAL_ONLY nuevo | Permisos/datos autorizados conservados; bytes/hash iguales; sin URL del proveedor | FR-004/007/009; SC-003/009 |
| QA07 | Nueva emisión desde denegación, con metadata nueva y con legado | Filtros confirmados, UUID/corte propios; flujo vista→IssuedReportsFacade→IssuedReportsApi→GET /energy/report, sin imports privados de Consumo ni excepciones lint; original intacto | FR-008/017; SC-003 |
| QA08 | Retirar rol, reutilizar cookies/enlace propio | Revocación existente impide siguientes accesos; sin payload/cache operativo reutilizable dentro de SARC | FR-009; SC-001 |
| QA09 | Graficar energía nula,0 real,sin sesiones y mezclas | Huecos/estados fieles; sesiones iniciadas contadas; no energía/porcentajes inventados | FR-019; SC-002/007/008 |
| QA10 | PDF con366 días,Top ampliado y nombres largos | Seis representaciones o ausencia explícita; todo legible/paginado, sin perder empates/datos | FR-016/018; SC-007 |
| QA11 | Excel de la ruta seleccionada con366 días y datos más allá de fila200 | Todas: privacidad, datos/fórmulas/blancos≠0 y apertura Windows sin reparación con versión. NATIVO: gráficos/rangos/cachés; IMAGEN: imágenes fieles/legibles; TABULAR_CON_EXCEPCION: integridad sin gráficos y justificación de alternativas, sin soportes descartados | FR-020/021; SC-008 |
| QA12 | Matriz visual completa y conector con ayudas/errores | Diez combinaciones por estado aplicable,acciones/foco accesibles,antes/después y correcciones verificadas | FR-010–015; SC-004–006 |
| QA13 | Importación/corrección concurrente durante snapshot | Resumen coherente internamente e informe sin mezcla de revisiones; descarga posterior no recalcula | FR-005/017; SC-002/009 |
| QA14 | Reimportar iguales/correcciones,aprobación,link/test,identidades y código KIA | Reglas001/002/003 intactas y sin duplicados. Solo cambia audiencia de lectura autorizada | FR-005/006; SC-009 |
| QA15 | Superar366 días/100.000 filas autorizadas,storage ausente/timeout | Errores existentes/contratados claros,sin datos restringidos,sin entrega parcial ni corrupción de archivo | FR-003/021/022 |

QA de archivos debe inspeccionar contenido, no buscar solo el título de la tarjeta. PDF: extraer texto, comparar tablas/valores, renderizar todas las páginas e inspeccionar cortes/etiquetas. XLSX: abrir POI y ZIP/XML; revisar todas las hojas incluidas ocultas/veryHidden, sharedStrings, rangos definidos, cachés/drawings/charts y vínculos; no debe existir información operativa para gerenciales. Conservar evidencias de excepción aprobada oficial que sí corresponde recibir.

Excel Windows real: registrar versión/build, abrir sin reparación, revisar gráficos/leyendas/rangos/blancos, filtros/tipos/datos y fórmulas preexistentes. POI no sustituye esta prueba. Si Excel no está disponible, declarar esta validación pendiente; no presentar ausencia de acceso como inviabilidad técnica ni cerrar SC-008. Evaluación de excepción incluye nativos/imágenes, limitación reproducible, impacto e integridad tabular.

Seleccionar primero dictamen preliminar NATIVO, IMAGEN o TABULAR_CON_EXCEPCION con prototipos de test; integrar únicamente la ruta elegida. Los gráficos/rangos/cachés específicos se comprueban solo si existen por esa ruta. La excepción tabular válida tiene un caso explícito que pasa sin gráficos ni soportes residuales, siempre con evaluación documentada; no implementar nativos ni alterar CorporateWorkbook solo para cumplir una tarea no aplicable. T056/T057 pueden resolverse como NO_APLICA justificado; T059 permanece obligatorio en todas las rutas. Si la apertura real invalida el dictamen, reevaluar alternativas, actualizar pruebas y repetir apertura antes de aceptarlo.

## Regresiones automatizadas

- Reutilizar ConsumptionApiTest, ConsumptionPdfRendererTest, EnergySessionSchemaTest, parser/normalización, idempotencia002, revisión003, AssignedCode/InventoryApiTest001, ArchUnit y OpenApiContractTest.
- Mantener todas las expectativas de reglas002/003: cambiar cookie manager por analyst/admin en inspecciones operativas; añadir matriz específica de lectura gerencial. No eliminar tests para acomodar nuevas restricciones.
- Añadir integración V010→V011 con filas legadas, ddl-auto=validate y conservación exacta de bytes/referencias/hash/fechas. Nuevo archivo tiene metadatos coherentes, inventario sigue funcionando.
- Angular: tarjeta/roles/propiedades omitidas, filtro oficial, historial proyectado, Blob ProblemDetail, descarga/nueva emisión mediante fachada y cliente propios de Reportes, puntos nulos y distribución0/sin datos, conector y dirty guard. Reutilizar toProblemFromBlob existente y comprobar lint sin relajar fronteras. Vitest comprueba estado/lógica; geometría solo navegador.

## Rendimiento y aceptación

Registrar antes/después con30 concurrentes y dataset250.000 registros/mes, incluyendo reportes dentro del límite100.000: p95 consulta usual≤2s y dashboard≤5s según TRD. Declarar CPU/RAM,red,almacenamiento,filtros,rango y calentamiento; no extrapolar un portátil a VPS4vCPU/8GiB como prueba de capacidad.

Mantener límites de filas/días y snapshot breve; medir CPU,RAM,tiempo/tamaño PDF/XLSX y archivos temporales SXSSF. No OOM, archivos dañados ni bloqueo de consultas durante emisión. No existe SLA numérico de emisión aprobado: conservar objetivos existentes de consulta y registrar comparación de emisión sin inventar garantía. Investigar regresiones antes del cierre.

## Evidencia y cierre

Durante implementación producir `evidence/validation-results.md`, `visual-audit.md`, capturas, PDF/XLSX sintéticos y evaluación Excel con versión. Registrar fecha,commit,roles,comandos,exit codes,fixtures,medidas y limitaciones; ningún resultado se marca pasado sin ejecución.

Antes de cierre: cero datos restringidos en canales comprobados; oficiales coincidentes; originales intactos; diez combinaciones/AA verificadas; seis gráficos PDF; salida Excel con gráficos viables o excepción tabular demostrada; regresión y rendimiento conformes. Pruebas de proveedor externo deben comprobar caducidad de URLs antiguas y ausencia de nuevos enlaces del proveedor. No se declara cerrada esa protección solo porque el mock pasó.
