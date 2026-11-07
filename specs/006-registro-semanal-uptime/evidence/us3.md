# US3 — Consulta semanal y composición

Ejecutado2026-11-05, PostgreSQL18 desechable/Testcontainers y FileStorage simulado. BD local intacta.

- RED: feature006-us3-red.log (faltaban modelos/cálculo), feature006-us3-summary-red.log (selección ausente), feature006-us3-angular-red.log (3peticiones en vez de6), feature006-states-red.log (componente sin comportamiento).
- GREEN backend: target/feature006-us3-final.log,68pruebas,0fallos (dominio, API, proyección por rol, ArchUnit, OpenAPI). Segunda regresión tras formato decimal: target/feature006-us3-regression.log,81pruebas,0fallos: UptimeSummaryApiTest17, UptimeImportApiTest21, UptimePendingLinkApiTest19, MeasurementRulesTest24. Son ejecuciones solapadas, no149casos únicos.
- GREEN Angular: feature006-us3-angular.log,3archivos/15pruebas; dashboard fija referencia y ambos apartados, ausencia/0/composición y cuarta serie. JSDOM avisa que no implementa canvas: estas pruebas no certifican renderizado visual, pendiente T076.
- Build: feature006-us3-build.log exit0, inicial342,14kB (transferencia94,98kB).

Verificado:0y100 producen50; precisión10,0049/10,0050 produce10,00 sin redondear cada fuente; empates al valor presentado/nombre, excluidos/pendientes fuera. Sin minutos evaluados ni peso168h para informes. Composición de cuatro estados fuente, Ocupado0 distinto de ausencia, conteos por estado y completitud. Datos fuente de cuatro estados se conservan también en referencias históricas.

Referencia global resuelta antes de filtros; dato2020 visible fuera de lookback366d. Explícita vacía no retrocede a otra semana. Resultados exigen apartado; cliente fija referencia en páginas, filtros, refresco y exportación. Rango real y referencia incompatibles. SQL no suma desconocidos como fuente; bigint/long. Una colisión atribuida queda pendiente una vez por conector; referencia cierta sin apartado suma conciliación separada, sin imputarla dos veces. Pendientes de vincular de otra semana no se cuentan, ni se distribuyen por filtros de equipo. Sin selección, pendientes0; rango real explícito mantiene su alcance aunque no tenga cifras.

Consulta gerencial sin operaciones ni valores pendientes; operador conserva consulta de alcance elegido. Pruebas de cinco roles ampliadas y reportes/transición corresponden aUS4. Casos13–25/44 cubiertos por pruebas citadas; auditoría visual completa/recorrido humano siguen en T076/T079. No certifica proveedor real ni Excel escritorio.
