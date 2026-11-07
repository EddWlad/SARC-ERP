# US1 — Captura por conector

2026-11-05. Roja API:15pruebas/2fallos, faltaba referenceWeek en respuesta y grilla devolvia400 sin limites comunes. Roja Angular: faltaban propiedades temporales de contexto/borrador.

Backend verde: mvnw.cmd -o -Dtest=UptimeMeasurementApiTest,MeasurementPeriodTest,OpenApiContractTest -Dopenapi.update=true test;25pruebas,exit0. Dos conectores con segundos distintos, referencia independiente,97.68%, duplicado200 y metadata contradictoria409; grilla por referencia y rechazo de mezcla. Dominio cubre limites parciales/invertidos/futuros y conservacion de validaciones.

Frontend: api:types;14pruebas en capture-facade/measurement-form,exit0; build completo exit0. Formularios solicitan Desde/Hasta por conector, segundos preservados, guardar/siguiente limpia limites/valores y conserva referencia. Default7d;24h/30d siguen como consulta separada. Vista revisada maneja periodType ausente como Por conciliar. Se corrigio expectativa de prueba de datetime-local: DOM puede serializar segundos con .000 sin cambiarlos.

La grilla pertenece al UptimeMeasurementController existente (ruta intacta); T019 actualizado a su archivo real. No se movieron controllers ni capas.

Logs: target/feature006-us1-red.log, capture-red.log, us1-contract.log, us1-angular.log, us1-build.log. Recorrido visual completo pendienteT076/T079; esto no declara acceso real a Enerlink ni proveedor.
