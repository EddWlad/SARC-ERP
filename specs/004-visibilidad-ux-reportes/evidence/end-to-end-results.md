# Recorrido end-to-end 004

Fecha 2026-11-01. `Feature004BrowserSandbox`, storage simulado, cuentas de los 5 roles y Gerente+Analista. Por API; pantallas en `visual-matrix.md`. Proveedor real no probado. Recorrido QA01–QA11 y QA15 repetido sobre el código optimizado con V012: todos PASS.

| ID | Resultado | Observado |
|---|---|---|
| QA01 | PASS | Oficiales iguales en 6 cuentas; propiedades operativas solo a operadores y combinación con Analista |
| QA02 | PASS | Gerencial solo validadas, 5 estados y revisión REJECTED dan 403, totalElements 10 |
| QA03 | PASS | No oficial e inexistente 404; gerente ve 1 revisión, operador 2 |
| QA04 | PASS | PDF y XLSX emitidos; XLSX gerencial sin residuos operativos; `no-store` |
| QA05 | PASS | Gerente 403 con motivo y acción, anónimo 401, operador bytes iguales |
| QA06 | PASS | OFFICIAL_ONLY e inventario con bytes y hash iguales, sin Location. Primer intento falló por ruta errónea del script, repetido con `/inventory/report` |
| QA07 | PASS | Nueva emisión con UUID y corte propios; original intacto. Filtro y emisión en pantalla: T043 |
| QA08 | PASS | Tras retirar rol, 200 pasa a 401 |
| QA09 | PASS | Periodo sin sesiones: 0. Puntos nulos por pruebas de gráficos |
| QA10 | PASS | PDF de 365 días, 168.718 bytes. No se abrió visualmente esta corrida |
| QA11 | PASS | XLSX 365 días con 6 gráficos nativos. Datos más allá de fila 200 y apertura: `excel-results.md`, `excel-windows-results.md` |
| QA12 | Fuera de T063 | Matriz de T043 en `visual-matrix.md`; su reauditoría es T064, que depende de esta tarea y sigue abierta |
| QA13 | PASS | Lote real cargado y confirmado entre lecturas: informe sin S-2 en resumen ni detalle, tablero sin mezcla entre oficiales y operativas, el siguiente sí lo incluye (`aBatchConfirmed*`). Corrección: `reportSummaryAndDetailComeFromTheSameSnapshot`. Con READ_COMMITTED ambas pruebas nuevas fallan. Descarga posterior sirve copia archivada con hash, sin recalcular: QA05, QA06 |
| QA14 | PASS | Regresiones de `mvnw verify` |
| QA15 | PASS | 367+ días 422 PERIOD_TOO_LONG; más de 100.000 filas 422 sin entrega. Storage caído: pruebas de integración de archivo |
