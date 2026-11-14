# Evidencia — Hotfix de fecha de origen Enerlink (Fase H)

**Rama**: `fix/009-fecha-origen-enerlink` (desde `origin/main` 0ce8921). **Fecha**: 2026-11-13.

## Causa confirmada

`EnergySession.apply` asignaba `startDate = OperatingZone.dateOf(startedAt)`, es decir, la fecha en
`America/Guayaquil` para toda fuente. `EnerlinkSessionParser` ya leía bien el desplazamiento del encabezado
(`INICIO (UTC+00:00)`), así que el instante guardado era correcto. Pero una sesión escrita entre las 00:00 y las
05:00 UTC quedaba con fecha del día anterior. El histórico, los filtros, el tablero, el PDF y el Excel agrupan por
`start_date` (`SessionPredicates`, `EnergySessionRepositoryAdapter`), por eso todos mostraban el mismo
desplazamiento. Wallbox no se veía afectado porque su zona de origen es la de operación.

## Cambio

| Pieza | Cambio |
| --- | --- |
| `SessionReading` | Nuevo `sourceOffset` (desplazamiento con que la fuente escribió el inicio) y `sourceStartDate()`. `sameAs` lo compara. |
| `TabularSessionParser` | Guarda `zone.getRules().getOffset(start)`: el desplazamiento declarado por Enerlink o −05:00 para Wallbox y Enerlink sin encabezado. |
| `EnergySession`, `ImportRow` | Columna `source_utc_offset_minutes`. `startDate` = fecha de origen. Sin desplazamiento (histórico sin evidencia) se conserva la fecha de Ecuador. |
| `V019__consumo_desplazamiento_origen.sql` | Añade la columna en `energy_session` y `energy_import_row`, rellena Wallbox −300 y Enerlink según el encabezado de su lote (H006) y recalcula `start_date` de los lotes UTC. No toca instantes. |
| API | `SessionResponse` e `ImportRowResponse` exponen `sourceUtcOffsetMinutes`. OpenAPI y tipos regenerados. |
| Excel | «Inicio/Término (hora de la fuente)» con segundos y desplazamiento, «Fecha de inicio según la fuente», hoja Diario rotulada con el criterio. |
| Angular | Lista, tarjetas, vista previa de importación y diálogo de energía muestran la hora de la fuente y la etiqueta `UTC±hh:mm` cuando no es hora de Ecuador. |

## Pruebas

| Prueba | Qué demuestra |
| --- | --- |
| `SessionParsersTest.enerlinkKeepsTheDeclaredUtcOffsetSoTheSessionKeepsItsSourceDate` | 1212 `11-11-2026 01:36:15 (UTC+00:00)` conserva offset 0 y fecha 11/11. |
| `SessionParsersTest.sourcesWithoutDeclaredOffsetUseTheOperatingZone` | Wallbox `2026-11-10 23:59:11` sigue en el 10/11. |
| `EnergySessionTest.aSourceThatDeclaresItsOffsetKeepsItsOwnStartDate` | Atribución por fuente. Sin offset se conserva la fecha previa. Otro offset no es la misma lectura. |
| `ConsumptionApiTest.eachSessionCountsOnTheStartDateDeclaredByItsSourceAlsoInTheConsolidatedView` | Escenario 6 de la spec: 10/11 = 1 sesión / 5 kWh, 11/11 = 1 / 8 kWh, rango = 2 / 13 kWh. El filtro Enerlink no mueve la 1212 al 10/11. |
| `SourceOffsetMigrationTest` | V018 → V019 sobre datos sembrados: 1212 pasa del 10/11 al 11/11, Wallbox se queda en el 10/11, `started_at` no cambia y se rellena la fila de importación. |
| `MigrationUpgradeTest` | Actualización desde la penúltima migración con `ddl-auto=validate`. |

Resultados: ver la sección «Ejecución» al final.

## Fuera de alcance (siguen en sus fases)

- Zona distinta por extremo (Fin usa el desplazamiento de Inicio, igual que antes): T007/T011.
- Desglose «Fecha operativa heredada» (FR-006a). Con V019 todas las sesiones existentes quedan con desplazamiento, así que no hay filas heredadas salvo que H006 lo exija.
- `energy_session_revision` no guarda el desplazamiento: T006.
- PDF: solo muestra agregados diarios, que ya usan la nueva fecha. No lista horas.
- US2–US4.

## H006 — Diagnóstico sobre copia de producción (2026-11-13)

Copia `sarc-20261113T2319Z-manual` (V018, 16 130 sesiones, 34 lotes) restaurada en un PostgreSQL 18 local
aislado (`sarc-prod-local`, 127.0.0.1:55432). Los originales de los 24 lotes Enerlink se leyeron de Cloudinary en
solo lectura.

**Encabezados declarados**: no todos son `UTC+00:00`. 18 lotes declaran `INICIO (UTC-05:00)` y 6 declaran
`INICIO (UTC+00:00)` (5 completados y 1 descartado). La primera versión de V019 asignaba 0 a todo Enerlink y
habría movido 2 291 sesiones (54 603,58 kWh) de forma incorrecta. V019 ahora asigna el desplazamiento por lote:
0 solo para los seis lotes UTC y −300 para el resto (misma fecha que ya tenían). Ninguna sesión aparece a la vez
en un lote UTC y en uno UTC-05:00, y no hay sesiones sin lote.

| Lote UTC+00:00 (completado) | Sesiones | Cambian de día | kWh |
| --- | --- | --- | --- |
| Transacciones_01-10-2026_07-11-2026.xlsx (KIA Choco 112, Village, City Mall) | 613 | 103 | 2 530,50 |
| Transacciones_10-11-2026_11-11-2026_3YTWm2W.xlsx (1211, 1212) | 24 | 2 | 47,86 |
| Transacciones_11-11-2026_13-11-2026.xlsx (1230) | 22 | 1 | 22,89 |
| KIA Transacciones_07-11-2026_08-11-2026.xlsx | 24 | 0 | — |
| Transacciones_10-11-2026_12-11-2026.xlsx | 17 | 0 | — |

**Ensayo de V019** (aplicada por Flyway al arrancar el backend de la rama con el rol de la aplicación, 1,2 s):

| Medida | Resultado |
| --- | --- |
| Sesiones que cambian de día | 106 (todas Enerlink de lotes UTC; 0 Wallbox), 2 601,25 kWh |
| Por mes (fecha anterior) | octubre 86 sesiones / 2 066,93 kWh; noviembre 20 / 534,32 kWh |
| Totales mensuales | iguales antes y después (octubre 11 606 / 223 028,11 kWh; noviembre 4 524 / 87 575,37 kWh) |
| Total general | 16 130 sesiones / 310 603,48 kWh, sin cambio |
| Instantes modificados | 0 |
| Mayor variación diaria | +1,6 % (04/11, +112,87 kWh); el resto entre −1,3 % y +1,3 % |
| 10–12/11 | 10/11 −2 sesiones (−47,86 kWh), 11/11 +1 (+24,97), 12/11 +1 (+22,89) |

Riesgo que sigue abierto (US2): la fecha depende del desplazamiento con que se exportó el archivo. Si una sesión
de un lote UTC se reimporta desde un archivo UTC-05:00, queda como modificada y vuelve a la fecha de Ecuador.
Conviene exportar Enerlink siempre con la misma zona.

## Pendiente (H007)

Publicar con el flujo de Feature 007 (respaldo previo y versión candidata), después de que el usuario apruebe
el resultado del ensayo.

## Ejecución

| Comando | Resultado (2026-11-13) |
| --- | --- |
| `./mvnw test` (backend completo, Testcontainers PG18) | 887 pruebas, 0 fallos, 0 errores; tras ajustar V019 por lote (H006): 889, 0 fallos, 0 errores |
| `npm test` (frontend) | 88 archivos, 478 pruebas, todas correctas |
| `npm run build` | Compilación correcta |
| OpenAPI y tipos | Regenerados con `OpenApiContractTest -Dopenapi.update=true` y `npm run api:types` |

Ajuste de regresión: `IntermittencySchemaTest.upgradeFromV017LeavesEveryExistingTableUnchanged` migraba hasta la
última versión. Ahora migra hasta V018, porque su propósito es demostrar que la Feature 008 no altera tablas
existentes, y V019 sí evoluciona `energy_session` de forma intencional.

Sin verificación en navegador: probarla exigía aplicar V019 en la base de desarrollo de compose, y H006 todavía
puede cambiar V019. El formato está cubierto por `source-time.spec.ts`.
