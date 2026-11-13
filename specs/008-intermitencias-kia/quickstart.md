# Quickstart: validación de Feature 008 (intermitencias KIA)

Esta guía explica cómo comprobar de punta a punta que el módulo funciona. Los contratos están en [contracts/](contracts/), el modelo en [data-model.md](data-model.md) y las decisiones en [research.md](research.md). Aquí no se repiten.

## Prerrequisitos

- JDK 25, Node según `.nvmrc` o `package.json`, y Docker para Testcontainers (PostgreSQL 18).
- No se usa la base de datos de compose para ensayar migraciones. V018 se ensaya en un PostgreSQL desechable (research R13). T006 pasó el 2026-11-13. Aun así, hasta publicar el backend con V018 no se arranca contra la base de compose ni contra otra base persistente; solo se usan Testcontainers y la fixture de navegador (research R2). V018 ya no se edita: cualquier cambio va en V019.
- No se usan las plantillas reales de `documents/plantillas/`. Las pruebas usan fixtures sintéticas en `sarc-bakend/src/test/resources/intermittency/`, con códigos ficticios `9xxxxx` que reproducen la forma de las filas 3, 57, 61, 94/240, 99, 117, 155, 163 y 228 (research R15).
- La carga de archivos usa `FileStorage` simulado. No se escribe en Cloudinary.

## Puertas automáticas

En `sarc-bakend`:

```bash
./mvnw.cmd test
```

Incluye:
- las pruebas de dominio de `PeriodSlicer`, `IntermittencyCalculator` y `AvailabilityEstimator`;
- las pruebas de API con roles;
- `ArchitectureTest`, con la nueva regla del módulo y el puerto `KiaPointDirectory`;
- `SchemaMigrationTest`, con V018 y el *exclusion constraint*;
- `OpenApiContractTest`, sin actualizar el contrato.

Para regenerar el contrato a propósito:

```bash
./mvnw.cmd test "-Dtest=OpenApiContractTest" "-Dopenapi.update=true"
```

En `sarc-frontend`:

```bash
npm run api:types
```

```bash
npm test -- --watch=false
```

```bash
npm run lint
```

```bash
npm run format:check
```

```bash
npm run build
```

Resultado esperado: cero fallos y el *bundle* inicial por debajo de 500 kB. Si el checkout con CRLF produce avisos, se registran sin reformatear módulos ajenos.

## Escenarios de validación

| # | Escenario | Pasos | Resultado esperado | FR / US |
|---|-----------|-------|--------------------|---------|
| 1 | Importación de la plantilla sintética | Analista sube el XLSX sintético | Vista previa con el archivo, la fila, el par, el valor original, la interpretación y el motivo de cada fila | FR-022 a FR-025, US2 |
| 2 | Duplicado de la fila 3 | Revisar la fila 3 | Las ventanas 3 y 4 aparecen como `DUPLICATE_IN_BATCH`, con 109 y 83 min; confirmar exige elegir OMIT; después aparece `TOTAL_MISMATCH` (83 frente a 109), que se reconoce; el total queda en 83 min | US2-2 |
| 3 | Fin anterior al inicio (fila 61) | Revisar la fila 61 | `END_BEFORE_START`; no se calcula ningún valor; editar la hora o marcar `endsNextDay` la vuelve a validar | FR-020, FR-026 |
| 4 | Total que no coincide (fila 99) | Revisar la fila 99 | `TOTAL_MISMATCH` (98 frente a 1.331); se resuelve con `acknowledgeTotal` | FR-027 |
| 5 | Ventana larga (fila 163) | `endsNextDay` en 20:03–12:22 | 979 min en REVIEW con `LONG_WINDOW`; fuera de los indicadores oficiales hasta su confirmación con justificación | FR-021a, US2-4 |
| 6 | Solapamiento dentro del lote (fila 228) | Elegir MERGE | Queda 02:24–03:11 (47 min); la revisión guarda el origen | FR-014 |
| 7 | Más de cinco pares | Fixture con 7 pares | Se crean 7 ventanas | FR-010 |
| 8 | Reimportación idéntica | Confirmar un lote y volver a subirlo | Todas las filas quedan IDENTICAL; los totales no cambian | FR-028, SC-004 |
| 9 | Reimportación con diferencias | Cambiar un fin y volver a subir | Queda en CONFLICT; con KEEP_EXISTING no cambia nada; con REPLACE se crea una revisión `REPLACED_BY_IMPORT` | FR-029 |
| 10 | Código desconocido | Fila con un código `9xxxxx` que no está en Inventario | PENDING_LINK; se vincula de forma explícita o queda con KEEP_PENDING; los días afectados quedan bloqueados para la disponibilidad | FR-003, FR-045 |
| 11 | Equipo fuera de alcance | Código de una electrolinera | `NOT_KIA_CHARGING_POINT`; no entra en ninguna métrica | FR-001 |
| 12 | Instante futuro | Captura con un fin 10 minutos después del momento actual | 422 `WINDOW_FUTURE_INSTANT`; el minuto en curso sí se acepta | FR-021b, US1-8 |
| 13 | Ventana abierta y su cierre | Crear una ventana sin fin y cerrarla después de 4 h | Mientras está abierta, solo minutos provisionales fuera de los oficiales, y una ventana nueva del punto posterior a su inicio recibe 409 `WINDOW_OVERLAP`; al cerrarla pasa a REVIEW | FR-012 |
| 14 | Cruce de medianoche y de quincena | Ventana del 15 a las 23:50 al 16 a las 00:20 | Primera quincena: 10 min; segunda: 20 min; franja [15,60) en ambas, porque se clasifica por la duración total. En un rango que incluye ambos días, la barra diaria cuenta 1 ventana el 15 y 1 el 16, con la nota de que la suma supera el total (1) | FR-013, FR-035, FR-036 |
| 14b | Punto de prueba por rango | Marcar el punto «PRUEBAS CDA» como prueba del 1 al 15 y registrar una ventana el 16 | Las ventanas del 1 al 15 quedan en TEST; la del 16 queda CONFIRMED; desmarcar el rango las devuelve a CONFIRMED | FR-016 |
| 15 | Captura manual concurrente | Dos pestañas editan la misma ventana | La segunda recibe 409 `CONCURRENT_MODIFICATION` | FR-018 |
| 16 | Top 10 con empates | Fixture con empate en el décimo puesto | Se muestran todos los empatados con la nota «incluye empates»; el orden es por minutos y, en empate, por más ventanas, más días afectados y nombre actual alfabético | FR-034 |
| 17 | Día revisado | Marcar ayer; intentar marcar hoy | Ayer se marca con aviso de los lotes sin confirmar; hoy recibe 422 `DAY_NOT_FINISHED` | FR-044 |
| 18 | Disponibilidad estimada | Marcar 2 días y registrar 72 min de ventanas | (2.880 − 72) / 2.880 = 97,50 %; la cobertura es de 2 días; los días sin marca no se cuentan como 100 % | FR-042 a FR-046a, US6 |
| 18b | Punto creado después | Marcar ayer; dar de alta un punto hoy | Ese punto queda «Sin datos» ayer (0 días cubiertos) y no pesa en el agregado; no cuenta como 100 % | FR-045 |
| 19 | Consistencia | Mismo filtro en dashboard, PDF y Excel | Mismas cifras; la suma de la hoja Ventanas es igual a Resumen | FR-039, SC-006 |

## Matriz de permisos

| Acción | Administrador | Analista | Otros roles (consulta) | Usuario con varios roles |
|--------|---------------|----------|------------------------|--------------------------|
| Capturar, importar, corregir, confirmar o marcar días | Sí | Sí | 403 | Sí, si alguno de sus roles es Administrador o Analista |
| `/summary`, `/points`, `/windows` (solo CONFIRMED) e informes | Sí | Sí | Sí, sin detalle operativo | Según su rol con más permisos |
| Pestañas Registrar, Revisión, Días revisados e Importaciones | Visibles | Visibles | Ocultas, y la ruta directa queda bloqueada por `roleGuard` | Visibles si alguno de sus roles es Administrador o Analista |
| `/availability` y apartado de disponibilidad | Sí | Sí | 403 `AVAILABILITY_RESTRICTED`, sin el apartado en PDF ni en Excel | Sí, si tiene el permiso D |
| Descargar un informe archivado con perfil `WITH_OPERATIONS` | Sí | Sí | 403 `REPORT_OPERATIONAL_CONTENT_RESTRICTED` | Según el permiso |

La comprobación incluye tres casos:
- La respuesta JSON de `/summary` no contiene ninguna clave de disponibilidad para ningún rol.
- Un usuario de consulta no puede obtener la disponibilidad cambiando parámetros.
- Si un usuario pierde el rol, su siguiente solicitud ya se evalúa sin el permiso.

## Regresiones

- Consumo, Inventario, Uptime, Informes y autenticación pasan su suite completa sin cambios de contrato propios.
- Las cifras de Uptime no cambian, porque las intermitencias no escriben en las tablas `upt_*`.
- Mover `share-chart` y `top-equipment` a `shared/ui` no cambia el tablero de Consumo. Se comprueba con sus pruebas y una captura comparada.

## Comprobación en el navegador

1. Levantar la fixture de navegador del backend (patrón de `Feature00xBrowserSandbox`, con Testcontainers y un puerto aleatorio) y Angular con el proxy que genera.
2. Recorrer `/intermitencias`: el tablero, las ventanas, el registro, la revisión, los días revisados, las importaciones y el detalle de un lote.
3. Comparar con Consumo e Inventario: los mismos *tokens*, tarjetas, tablas, filtros, estados vacíos y colores de gráficos. Ningún estilo propio fuera del sistema.
4. Comprobar a 375 px y a 768 px que no hay desplazamiento horizontal, que se navega con el teclado y que los gráficos tienen texto alternativo y leyenda textual.
5. Entrar como Analista, Administrador y un rol de consulta, y comprobar que la sección de disponibilidad aparece o desaparece según el rol.
6. Al terminar, cerrar los puertos 8080, 4200 y 5432 que se hayan abierto.

## Evidencia del cierre (2026-11-13)

Sin datos reales, secretos ni la base de compose: todo corrió sobre PostgreSQL 18 de Testcontainers.

**Puertas completas (T126)**
- Backend: `./mvnw.cmd test` con `OpenApiContractTest` sin actualizar: 885 pruebas, 0 fallos, 0 errores. Incluye `ArchitectureTest` (11).
- Frontend: `npm run api:types` sin cambios pendientes, `npm run lint` limpio, `npm run format:check` limpio, `npm test -- --watch=false` con 475 pruebas, y `npm run build` sin avisos con un *bundle* inicial de 347,74 kB (límite 500 kB).

**Seguridad (T121)**: `IntermittencySecurityTest` recorre cada endpoint del contrato con los cinco roles y las tres combinaciones, sin sesión y por acceso directo. Quien solo consulta recibe 403 en todo lo de edición y no cambia ninguna tabla; la disponibilidad responde 403 `AVAILABILITY_RESTRICTED`; un informe archivado con operaciones se bloquea para los roles de consulta.

**Regresión (T122)**: `Feature008RegressionBaseline` compara las cifras, rankings, coberturas e informes de Consumo, Uptime e Inventario antes y después de crear ventanas, lotes, días revisados e informes de intermitencias: idénticos.

**Rendimiento (T123)**, con `-Dsarc.qa.performance008=true`:

| Medición | Resultado | Criterio |
|----------|-----------|----------|
| Vista previa de 248 filas | 691 ms | menos de 30 s (SC-002) |
| Resumen quincenal con 6.000 ventanas (mediana de 15) | 95 ms | menos de 2 s |
| Resumen de un año con 6.000 ventanas (mediana) | 182 ms | menos de 2 s |
| Disponibilidad de 10 meses (mediana) | 109 ms | informativa |
| Informe PDF de un año con 6.000 ventanas | 754 ms | informativa |
| Informe Excel de un año con 6.000 ventanas | 2.007 ms | informativa |
| Captura de 5 ventanas (mediana) | 166 ms | menos de 1 s |

**Navegador (T124 y T125)**: `Feature008BrowserSandbox` con `-Dsarc.qa.browser008=true` y Angular con el proxy que genera (configuración `sarc-frontend-qa008`).
- Siete rutas (tablero, registrar, importaciones, revisión, días revisados, ventanas y detalle de un lote) a 360, 390, 768, 1024 y 1440 px, en tema claro y oscuro: sin desplazamiento horizontal, sin elementos sin nombre accesible, con contraste de texto de al menos 4,5:1 (3:1 en texto grande), con `h1` y con `lang`. Revisado con un script propio sobre el DOM; no se descargó ninguna herramienta externa.
- Los gráficos tienen `role="img"`, texto alternativo y leyenda textual con valor y porcentaje. El foco con teclado muestra un contorno visible de 3 px.
- Como Analista: el tablero muestra la sección «Disponibilidad estimada (prueba)»; el panel «Días revisados» marca un día, avisa del lote en vista previa y el selector de mes muestra los 31 días de octubre, todos marcables. Como Gerente: solo las pestañas Tablero y Ventanas, sin disponibilidad ni detalle operativo, `/intermitencias/dias-revisados` lo devuelve al inicio y la API responde 403 `AVAILABILITY_RESTRICTED` y 403 `ACCESS_DENIED`.
- Quedó sin medir con una persona: los tiempos de SC-001 (5 ventanas en menos de 2 minutos), SC-008 (marcar un día en menos de 30 segundos) y SC-010 (distinguir oficiales, provisionales y rango en menos de 10 segundos). Requieren a alguien del equipo con un cronómetro.

**Migración y restauración (T006 y T127 cerradas)**: `IntermittencyMigrationRehearsalTest`, con un rol que no es superusuario y es propietario de su base.
- V018 se aplica en menos de 5 s, crea `btree_gist`, deja intactas las columnas de las tablas existentes y la restricción de exclusión rechaza un solapamiento.
- Un `pg_dump` con 200 ventanas, 10 días revisados y 10 eventos se restaura con los mismos conteos por tabla y la migración 18 como última.
- **Ensayo con la última copia cifrada de producción (T006, 2026-11-13)**: la copia manual del 2026-11-07 coincidió con su SHA-256, se descifró con la clave del responsable solo en memoria y en un temporal, y se restauró en un PostgreSQL 18 desechable, sin puertos publicados y sin variables `CLOUDINARY_*` ni `SARC_DB_*`, con un rol de aplicación que no es superusuario y es propietario de su base. Resultado: `pg_restore` sin errores, última migración V017 igual a los metadatos y las 29 tablas con el mismo conteo. V018 se aplicó con ese rol en 752 ms: creó `btree_gist`, seis tablas `itm_` y la restricción de exclusión, no cambió ninguna columna existente ni creó columnas fuera de `itm_`, y los conteos siguieron iguales. El entorno y los temporales se destruyeron; no se guardó ningún dato de filas.
- **Originales en Cloudinary (T127, 2026-11-13)**: se subió un archivo sintético a `sarc-qa008-t127/imports/intermittency/` (carpeta propia, vacía antes y después), `Exportar-Cloudinary.ps1` lo exportó con su SHA-256 igual al del original y la comprobación de referencias del ensayo de restauración lo encontró en el índice. El recurso se borró y los temporales también. `Ensayar-Restauracion.ps1` ahora incluye `itm_import_batch` en los historiales y en la muestra de referencias.
- Sigue pendiente en la Feature 007 (T083) crear la clave de API dedicada a la exportación y activar el Backup de Cloudinary para `sarc-prod`; sin eso no se hace la primera exportación mensual real.

## Publicación (2026-11-13)

- **Versión**: `2026.11.13-35-9937edb` (commit `9937edb`, PR #8), creada por `version-candidata` con las seis comprobaciones de CI en verde. Se publicó con el flujo `publicar` en modo `normal`, con la aprobación del entorno `production` por el responsable del producto. Duración: unos 50 segundos.
- **Comprobaciones previas**: bloqueo, sumas, configuración, prueba de recepción de avisos, certificado, copia local reciente, esquema y copia previa (`sarc-20261113T2312Z-previa`), todas en `ok`. Migración aplicada: `V018`.
- **Comprobaciones posteriores**: disponibilidad, base de datos, versión, ingreso, usuarios, inventario, consumo, uptime, reportes y archivos, todas en `OK`; resultado `COMPLETADA`. SARC, PostgreSQL y Nginx activos; readiness `UP`.
- **Datos existentes**: contra los metadatos de la copia previa, las 29 tablas existentes tienen los mismos conteos, salvo `flyway_schema_history` (17 a 18 por V018) y las dos tablas de sesión (la sesión de la verificación). Se crearon las seis tablas `itm_*`, vacías.
- **Intermitencias con la cuenta técnica (rol `ASISTENTE`)**: el resumen responde 200 con 0 minutos confirmados, las ventanas responden 200 y la disponibilidad estimada responde 403 `AVAILABILITY_RESTRICTED`, como debe ser. Consumo e Inventario siguen respondiendo 200.
- **Observación abierta**: las entradas de `deploy-history.jsonl` tienen `runId` y `approvedBy` vacíos; la aprobación consta en el historial de ejecuciones de GitHub Actions.
- **Pendiente tras publicar**: el flujo abrió el *issue* #9 `ensayo-restauracion` `pendiente` (cambio crítico con migración); debe cerrarse con un ensayo aprobado en 7 días.

## Despliegue (solo como referencia; en esta etapa no se ejecuta)

El despliegue sigue el flujo de Feature 007:

1. Hacer el respaldo previo y verificar el recibo.
2. Pasar por `ci.yml`, luego `version-candidata.yml` y después `publicar.yml`, con la aprobación del entorno `production`.
3. V018 se aplica una sola vez y es solo aditiva.
4. Si hay que volver atrás, se reinstala la imagen anterior. Las tablas `itm_*` vacías no afectan a los módulos existentes (plan.md, «Migraciones, respaldo y despliegue»).
