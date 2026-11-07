# Quickstart — Validación de Feature-006

**Guía propuesta 2026-11-04, no pruebas ejecutadas.** Requiere implementar después de `$speckit-tasks` y `$speckit-analyze`. Plan activo [plan.md](plan.md), [modelo](data-model.md), [API/UI](contracts/api-ui.md) y [reportes](contracts/reporting.md). No usar drafts/ como contrato vigente.

## Preparación

- Usar PostgreSQL18 desechable/Testcontainers, Compose desactivado y FileStorage simulado; nunca sembrar ni migrar la BD dev del usuario.
- Base desde V015 y aplicación005 `9739aca`, sin reescribir migraciones. Registrar hashesV001–V015, versiones/cantidades/UUID y archivos emitidos antes de actualizar.
- Originales sintéticos de dos hojas con checksum, hoja/fila, porcentajes0/100/decimales y duraciones; archivo sin horarios/Ocupado y otro con horarios auténticos para no confundir procedencia.
- Inventario sintético: puntos JAC/CONECTZ, electrolineras KIA/JAC/CONECTZ con dos conectores y nombre repetido; identidades verificadas/ambiguas, inactivos/retirados y un punto KIA fuera de alcance.
- Cuentas QA de los cinco roles y ANALISTA+GERENTE; sin credenciales reales. Archivo emitido sintético inmutable para contrastar bytes/hash/ID/corte.
- Fixture de historial005: Excel declarado validado, falso EXACT_WEEK, vigente validada+corrección pendiente, excluida, captura real, cadena mixta FILE/MANUAL, colisión por referencia, original ausente/inaccesible/huella distinta. Nada de archivos de clientes en Git.

## Recorridos de aceptación

| # | Acción | Resultado verificable |
|---|---|---|
| 1 | Abrir captura | Default7d, referencia de contexto, grilla de inventario y opciones24h/30d separadas |
| 2 | Conector1: pegar 163h35m/0h31m/0h/3h54m, desde28-10-2026 08:06:20 hasta04-11-2026 08:06:20 | 9815/31/0/234 minutos;97,68%; segundos conservados; pendiente hasta validar |
| 3 | Guardar y siguiente al segundo conector, capturar otras horas | No arrastre de minutos ni límites; contexto de referencia conservado |
| 4 | Error de fecha parcial/invertida/futura o bloque inválido | Error español, sin guardar, borrador intacto |
| 5 | Validar captura móvil alineada/sin confirmar exacta | Sigue PROVISIONAL; no convierte semana de referencia en intervalo |
| 6 | Importar dos hojas sin horarios/Ocupado, weekStart como referencia | Hoja/fila/crudos presentes; límites reales desconocidos; porcentaje fuente; Ocupado no informado |
| 7 | Nombre/cabecera del archivo con otra semana | No genera horas desde ellos; referencia es la elegida |
| 8 | Intentar exactConfirmed en referencia sin horario por acceso directo | 422 claro, sin promoción ni escritura parcial |
| 9 | Reimportar lectura idéntica desde archivo reordenado/renombrado | Cero nuevas mediciones/indicadores; no identidad por checksum/posición |
| 10 | Reimportar distinto conector/fuente/referencia igual | Conflicto y corrección justificada; vigente anterior hasta validar nueva |
| 11 | Dos cargas concurrentes iguales/diferentes | Una identidad canónica, idéntica/conflicto controlado; sin duplicados |
| 12 | Captura real y Excel de igual conector/referencia | Dos registros/apartados, sin fusión ni KPI conjunto |
| 13 | Referencia con0%y100%validados y otra pendiente | Promedio del informe50,00%; sin minutos evaluados inventados; pendiente no altera media |
| 14 | Porcentajes con cuatro decimales elegidos para diferir de promedio redondeado | Promedio desde precisión fuente, redondeo final únicamente |
| 15 | Conocidos de distinta duración con mismo contexto | Ponderación por minutos005 conservada, no media aritmética accidental |
| 16 | Dos referencias con incluidos y filtros que dejan vacía la más reciente | Latest global estable; vacío explícito, no salto de semana por red/equipo/conector |
| 17 | Referencia explícita vacía con pendientes de otras semanas | Sin promedio ficticio y sin atribuir solo de esa referencia; no fallback a historia |
| 18 | Filtrar tipo/red/marca/equipo/conector | Cifras/dona/cobertura del apartado cambian coherentemente; sin atribuir de referencia no cambia por atribución |
| 19 | Más páginas de resultados que el tamaño de página | KPIs/dona de todo el conjunto incluido; nunca de Top/primera página |
| 20 | Dona completa, ceros reales | Cuatro estados, duraciones/colores fijos y cobertura completa;0min sigue0 |
| 21 | Excel sin Ocupado | No informado, composición parcial y leyenda; no complemento hasta168h ni porcentaje del período |
| 22 | Agregado2mediciones, solo1con Ocupado0 | Ocupado0con cobertura1/2, no ausencia ni cobertura2/2 |
| 23 | Todos nulos o suma conocida0 | Leyenda/mensaje sin círculo artificial ni uptime0inventado |
| 24 | Detalle lector con corrección pendiente | Solo valores de revisión permitida; dona no reconstruye desde historial restringido |
| 25 | Rango real/oficial/ventanas24h30d e históricos sin referencia | Caminos anteriores conservados, max366d y sin referencia retroactiva supuesta |
| 26 | Lote con etiqueta numérica desbordada o identidad ambigua | Pendiente con motivo; demás filas procesadas sin atribución arbitraria |

## Transición e históricos

| # | Fixture/operación | Resultado |
|---|---|---|
| 27 | Upgrade V015 con Excel inequivocamente sin horarios y validación vigente | REFERENCE_WEEK, referencia probada, límites canónicos nulos; porcentajes/UUID/revisiones/validaciones conservados, evento antes/después |
| 28 | Ese Excel estaba marcado EXACT_WEEK | No computa en oficiales nuevos; antecedente de confirmación conservado; porcentaje validado participa en referencia sin revalidación |
| 29 | Vigente validada y última pendiente | Ambos punteros/estados/valores originales intactos; no activa la pendiente por reclasificar |
| 30 | Medición excluida | Sigue excluida y con su justificación/historial |
| 31 | Original con horarios reales concordantes/captura real | Se verifica y conserva conocido, no reclasifica por origin FILE o basis |
| 32 | Original ausente/inaccesible/checksum distinto/hoja o fila no verificable | Requiere conciliación; no inventa prueba ni clasifica automáticamente |
| 33 | Cadena mixta FILE/MANUAL/BLOCK o dos identidades destino iguales | Conflicto en Revisión sin merge/borrado/last-wins; con destino probado figura pendingReview una vez por conector; referencia sin destino muestra cantidad temporal separada, nunca imputada a ambos bloques |
| 34 | Operador concilia con motivo/evidencia/version | Cambio explícito trazado; identidad ocupada devuelve409; no muta validaciones de valores |
| 35 | Intento de restaurar excluida ambigua para sortear conflicto | No aporta mientras no se resuelva; guarda antecedente/exclusión |
| 36 | Cambio concurrente mientras se lee original | Versión obsoleta impide aplicar; sin escritura sobre otro estado |
| 37 | Reejecución/interrupción entre bloques | Reconoce aplicados, no duplica eventos/validaciones; continúa con resultados persistentes |
| 38 | Reportes emitidos antes de transición/corrección | Mismos bytes/hash/ID/file_ref/created_at/cut_off; descarga y permisos conservados |

## API, reportes y presentación

| # | Acción | Resultado |
|---|---|---|
| 39 | Summary y documentos con dataset/corte fijados | Misma selección y cifras por apartado, métodos y cobertura; sin atribuir correcto |
| 40 | Publicar informe solo de referencia o referencia vacía | Criterios con referencia validada, sin hoy/mañana/lunes como from/to falsos |
| 41 | Exportar PDF/Excel con ambos apartados | Horario real no informado y Promedio del informe; sin minutos reconstruidos,7hojasExcel y celdas numéricas sanas |
| 42 | Abrir Excel en Windows previsto por el proyecto | Archivo sin reparación; datos/columnas/fórmulas preservados; registrar versión y evidencia |
| 43 | Cinco roles + unión, escrituras/conciliación/historial por acceso directo | Solo operativos modifican; consulta sin datos restringidos/UID/PUK/OCPP/refprivada |
| 44 | Cambiar filtros mientras llega respuesta lenta/refresh falla | No pisa selección nueva; conserva última vista con aviso/corte, no se declara actualizada |
| 45 | Pantallas afectadas a360/390/768/1024/1440 × claro/oscuro | Sin superposición/overflow; diálogos accionables, leyenda y horas legibles, diseño coherente |
| 46 | Teclado/foco/lectores/contraste/movimiento reducido | Cumplimiento de controles aplicables WCAG2.2AA, alternativa textual y sin depender solo del color |
| 47 | Regresión ChartView/serie4 y criterios ReportPublication | Tres colores anteriores/otros charts y criterios de Consumo/Inventario intactos |
| 48 | Regresión completa001–005 | Sin cambios de consumo nulo/0, aprobación energética/empates, inventario, importaciones o permisos |

Los #39–41 comparan el mismo estado/corte de generación; no exigir que una exportación posterior a escrituras concurrentes reproduzca un snapshot previo de pantalla que no se almacena. Cada respuesta conserva su corte visible.

## Validaciones añadidas por U1/U2 — 2026-11-05

Los escenarios1–48 conservan sus números. Estos siete se incorporan a T073/T079 y no son pruebas ya ejecutadas.

| # | Fixture/operación | Resultado verificable |
|---|---|---|
| 49 | Importar porcentaje97,6800 y estados9815/null/0/234 sin límites, confirmar/validar, corregir por API directa y validar corrección | Sin NPE ni duración0/10080/nominal; porcentaje fuente preservado, nueva revisión PENDING y anterior vigente hasta validación; sin exactitud ni cambio de identidad |
| 50 | Misma referencia: minutos negativos/desbordados, porcentaje ausente/fuera de0–100/escala>4; repetir en confirmación, vínculo y corrección | Reglas numéricas/crudos/errores u observaciones no validables de005 conservados; filas válidas continúan, inválidos no computan; Ocupado ausente no se vuelve0 |
| 51 | Suma informada superior al nominal sin límites y captura conocida equivalente con suma superior al rango observado | Referencia no genera comprobaciones de duración desconocida; conocido mantiene tolerancias y observación temporal de005; demás reglas numéricas y SUM_ZERO aplicables no se eliminan |
| 52 | Confirmar solicitudU2 y caer antes del primer item | Trabajo/items/actor/hash/clave existen; al reiniciar queda INTERRUPTED con0procesados; GET lo muestra y retry autorizado procesa pendientes, sin usuario recuperado desde memoria |
| 53 | Caer antes/después del commit de un item, y después del último item antes de cerrar | Medición/evento/audit/resultado todos confirmados o todos ausentes; retry no repite resultados terminales, conserva versiones originales y puede solo cerrar el trabajo |
| 54 | Misma Idempotency-Key/mismo actor con items reordenados, luego distintos; saturar cola | Mismo trabajo sin duplicar, contenido distinto409; rechazo deja FAILED/UPTIME_TRANSITION_QUEUE_FULL durable y ninguna medición modificada; consulta/retry disponibles |
| 55 | Dos retries concurrentes, medición cambiada, otro operador autorizado y lector por acceso directo | @Version admite un solo intento, versión origen no se renueva y devuelve STALE_VERSION cuando corresponde; actor de cada intento trazado, permisos/CSRF y paginado correctos, UI detiene polling al salir o terminar |

## Comandos de implementación/QA futura

Desde `sarc-bakend/`:

```powershell
.\mvnw.cmd '-Dtest=ArchitectureTest,OpenApiContractTest' test
.\mvnw.cmd test
```

Regenerar contrato solo tras cambio intencional:

```powershell
.\mvnw.cmd '-Dtest=OpenApiContractTest' '-Dopenapi.update=true' test
```

Desde `sarc-frontend/`:

```powershell
npm run api:types
npm test -- --watch=false
npm run lint
npm run format:check
npm run build
```

Tests nuevos/ampliados se definen en tasks; verificar fase roja antes del cambio correspondiente. Si hace falta limitar concurrencia Vitest para el entorno Windows, usar configuración temporal ignorada con maxWorkers=2 como en005, sin aumentar timeout de producto ni commitear configuración local. Registrar format:check estricto y, si solo es CRLF del checkout, Prettier --end-of-line auto; no reformatear otros módulos.

Arnés visual futuro opt-in equivalente a Feature005BrowserSandbox, puerto propio y almacenamiento simulado. No conectar BD real para una prueba ni declarar el proveedor real verificado con un mock. Cerrar únicamente procesos/contenedores propios al finalizar.

## Rendimiento, rollout y evidencia

- Repetir cargas sintéticas comparables005 de300/1000conectores, histórico y2000filas, añadiendo referencias/candidatos/conflictos. Medir últimas referencias, summary/ranking/cobertura, preview/confirmación, reporte y transición por bloques.
- Registrar p95, rondas, filas, hardware/concurrencia y consultas/índices observados. Referencias nuevas se filtran en SQL; no “probar30usuarios” con llamadas secuenciales. Objetivos TRD2s/5s y presupuesto500kB no se dan por cumplidos sin resultados.
- Guardar evidencia nueva en evidence/ con fecha, fixtures y distinción entre baseline005 y pruebas006. Comparar hashesV001–V015 y archivos archivados antes/después. T124humana005 permanece pendiente hasta medición real.
- Despliegue coordinado: backup íntegro, escritor005 detenido, V016, backend/frontend006, diagnóstico/automáticos y revisión de conflictos antes de exponer nuevos indicadores. No IO de archivos en Flyway, no eliminación histórica ni fusión automática. Tras escribir referencias006 no ejecutar binario005; recuperación por backup coordinado o avance compatible.

Esta guía no ejecuta migraciones, escribe pruebas ni marca tareas completadas. Tareas generadas; ajustes documentalesU1/U2 incorporados el2026-11-05. Repetir `$speckit-analyze` antes de `$speckit-implement`.


## Ejecución006 y recuperación coordinada

Procedimiento de entrega: [evidence/rollout.md](evidence/rollout.md). No iniciar un binario005 contra datos006. El ensayo de restauración se ejecuta con `./mvnw.cmd -Dtest=Feature006RolloutTest test` y solo crea/restaura una base dentro de PostgreSQL desechable.

Benchmark opt-in: `./mvnw.cmd -Dtest=Feature006PerformanceBench -Dsarc.qa.performance006=true test`; resultados en target/uptime-performance006.txt, con volúmenes/rondas y concurrencia explícitos. Arnés visual: `./mvnw.cmd -Dtest=Feature006BrowserSandbox -Dsarc.qa.browser006=true test`; iniciar Angular en puerto propio con target/feature006-browser-proxy.json y finalizar creando target/feature006-browser.done. No utilizar Compose/dev ni originales privados para estos recorridos.

Los resultados ejecutados se documentan en evidence/, distinguiendo pruebas automatizadas, navegador, Excel real y proveedor aún no verificado. Las secciones de planificación anteriores describen el estado al redactarse; las casillas de tasks.md y la evidencia de ejecución son la referencia del progreso actual.
