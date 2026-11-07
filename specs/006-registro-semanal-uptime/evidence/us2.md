# US2 — Informe semanal sin horario

2026-11-05. Roja backend:36pruebas,4fallos/1timeout por horario impuesto, exactitud improcedente y carrera de confirmacion. Verde: mvnw.cmd -o -Dtest=EnerlinkUptimeParserTest,UptimeImportApiTest,UptimeConcurrencyTest,UptimePendingLinkApiTest test;55pruebas,exit0. OpenApiContractTest con openapi.update=true:1prueba,exit0.

El parser conserva solo limites de fuente y evidencia de presencia; inspectSource permite revisar originales sin contexto. El lote/filas guardan referencia/precision y no declaran limites artificiales. Preview/confirm/vinculo comparten identidad por precision y semana; fuente con tiempos reales no se reemplaza. Correccion directa de referencia funciona con limites null; confirmar exactitud sin rango conocido devuelve422 sin promover filas. Colision de confirmaciones aborta el chunk y se relee una vez en transaccion nueva, sin sobrescritura ni duplicados; los conflictos de valores siguen exigiendo justificacion.

Frontend: api:types;8pruebas de dominio/HTTP/dialogo compartido,exit0; build completo exit0. Rotulos de referencia/horario desconocido y Ocupado no informado; exactitud oculta y deshabilitada en fachada para referencia. Se reutiliza ImportDialog.fileChoice, sin cambiar el componente compartido ni importadores ajenos. La decision se mantiene en backend incluso por API directa.

Logs target/feature006-us2-{red,backend,contract,angular-red,angular,build}.log. Faltan dashboard/revision/reportes y QA transversal; no se declara proveedor real verificado.
