# Quickstart: validar el ajuste semanal de uptime

**Guía de implementación futura, revisión 2026-11-04.** No afirma que el código actual cumpla. Diseño en [plan.md](plan.md), modelo en [data-model.md](data-model.md), contratos en [contracts/uptime-api.md](contracts/uptime-api.md) y [reportes](contracts/reports-and-consumption.md).

## Preparación y aislamiento

- Ejecutar `$speckit-tasks` incremental y `$speckit-analyze` antes de implementar; no desmarcar tareas anteriores para fingir trabajo nuevo.
- PostgreSQL 18 desechable mediante Testcontainers, Docker Compose desactivado; FileStorage simulado. No aplicar Flyway a BD dev, copiar credenciales ni publicar archivos reales en Git.
- Fixtures sintéticas: conector de punto JAC/CONECTZ y dos conectores de electrolinera JAC/KIA/CONECTZ; nombres repetidos pero UUID distintos. Casos válidos, pendientes, observados, excluidos, 0 real y ausencias.
- Roles ADMINISTRADOR, ANALISTA, GERENTE, SUPERVISOR, ASISTENTE y ANALISTA+GERENTE. Mantener sesión/CSRF de la aplicación.
- Upgrade desde V015 en esquema/BD desechable con manuales, Excel declarados, revisión validada y corrección pendiente, EXACT_WEEK confirmado previamente, colisiones de referencia y archivos archivados. Guardar hashes V001–V015, conteos, UUID/revisiones y bytes/hash de archivo antes de migrar.

## Recorridos y resultados esperados

| # | Acción | Resultado |
|---|---|---|
| 1 | Abrir captura | 7 d inicial; 24 h/30 d disponibles; grilla de inventario con semana de referencia separada |
| 2 | Conector 1, rango 28-10-2026 08:06:20 a 04-11-2026 08:06:20; pegar 163h35m / 0h31m / 0h / 3h54m | 9815/31/0/234 minutos, 97,68 %, pendiente, segundos conservados |
| 3 | Guardar y siguiente al conector 2, hora distinta | No arrastra valores ni impone horario anterior; conserva ambos historiales |
| 4 | Validar la captura | Aparece en bloque con horario, PROVISIONAL, nunca oficial solo por ser 7 d |
| 5 | Importar modelo de dos hojas sin Ocupado/horarios y elegir referencia | Hoja/fila conservadas, límites reales nulos, 7 d, horario no informado; original intacto |
| 6 | Confirmar exacta esa referencia mediante API directa | Rechazo claro y sin escritura; UI no ofrece la acción |
| 7 | Reimportar mismo Excel o filas reordenadas | Cero mediciones nuevas; igualdad usa conector/fuente/referencia y valores, no archivo |
| 8 | Cambiar lectura de ese conector/referencia | CONFLICT; corrección exige motivo y vigente no cambia hasta validación |
| 9 | Capturar con horario el mismo conector/referencia | Registro aparte, sin fusión con el Excel ni suma en KPI conjunto |
| 10 | Dos cargas concurrentes iguales | Una identidad persistida; segunda reconoce idéntica/conflicto controlado, sin duplicados |
| 11 | Informe con conectores validados a 0 % y 100 %, otro pendiente | Promedio del informe 50,00 %, pendiente no participa; evaluado no informado |
| 12 | Cambiar precisión de fuente de porcentajes en fixtures | Se promedia antes del redondeo de presentación |
| 13 | Dos semanas de referencia con capturas y Excel | Abre la más reciente con incluidos; dos apartados y filtros coherentes |
| 14 | Referencia sin datos y pendientes de otras semanas | Sin porcentajes ficticios; sin atribuir solo de referencia seleccionada, no toda historia |
| 15 | Filtrar red/equipo/conector | Indicadores/composición del apartado responden; total sin atribuir de la selección no cambia |
| 16 | Dona de captura completa | Cuatro estados y leyenda textual; suma conocida completa, 0 min real visible |
| 17 | Dona del Excel sin Ocupado | No informado, composición parcial y cobertura; no 0 imputado ni complemento de 168 h |
| 18 | Todos los estados ausentes o todos conocidos cero | Mensaje/leyenda; no círculo ni uptime inventado |
| 19 | Tabla/detalle de lector con revisión pendiente sobre vigente | Solo vigente permitida, composición sin datos pendientes/restringidos |
| 20 | Etiqueta de conector con 20 dígitos iniciales | Fila pendiente con motivo; otras filas del lote siguen procesándose |
| 21 | Minutos observados grandes | No overflow SQL/Java que inutilice dashboard; siguen observados y fuera de indicadores |
| 22 | PDF/Excel para mismos filtros/corte | Cifras iguales, dos métodos rotulados, referencia/horario ausente explícitos y sin atribuir incluido |
| 23 | Corregir después de emitir | Original archivado mantiene UUID/bytes/hash/corte y permisos |
| 24 | Upgrade V015 con datos legacy | Sin pérdida de columnas/valores/revisiones; fechas asignadas identificadas como declaradas; sin conciliación automática |
| 25 | Reimportar sobre candidato legacy o colisión | Conflicto para conciliación, sin duplicar, fusionar ni cambiar una aprobación exacta automáticamente |
| 26 | Semanas oficiales, ventanas 24 h/30 d y manuales anteriores sin referencia | Reglas previas y consulta real intactas, sin asignarles referencia supuesta |
| 27 | Cinco roles y combinación, lectura/escritura directa | Matriz actual efectiva en backend, sin UID/PUK/OCPP/versiones o motivos restringidos |
| 28 | 360/390/768/1024/1440 × claro/oscuro | Mismo diseño; fechas, leyenda y cobertura legibles, sin overflow/superposición; foco, contraste y nombres accesibles |
| 29 | Consumo/inventario/reporting y nuevas migraciones | Cálculos, importaciones, alias, nulos/0, históricos y empates sin regresión |
| 30 | Modelo final y rendimiento | Inicial ≤500 kB; p95 documentado con misma carga/baseline y alcance, sin afirmar 30 usuarios si no se prueba |

## Comandos al implementar

Desde `sarc-bakend/`, después de escribir pruebas enfocadas y comprobar su fase roja cuando proceda:

```powershell
.\mvnw.cmd '-Dtest=ArchitectureTest,OpenApiContractTest' test
.\mvnw.cmd test
```

OpenAPI solo al cambiar intencionalmente contrato:

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

En checkout Windows, registrar la advertencia CRLF y comprobar también Prettier `--end-of-line auto` para distinguir fin de línea de formato; no reformatear otros módulos como parte de esta feature. Pruebas de UI con sandbox y puertos propios; cerrar solo procesos/contenedores creados para QA.

## Evidencia

Guardar resultados nuevos con fecha en evidence/, diferenciando plan, baseline y prueba realmente ejecutada. La evidencia anterior de 005 no prueba los ajustes de noviembre 4. Mantener pendientes T124 de usabilidad y cualquier recorrido no realizado; no marcar aprobaciones de pruebas sin ejecutar. Verificar hashes de migraciones existentes y documentos archivados. No se necesita proveedor de almacenamiento real para estas regresiones.
