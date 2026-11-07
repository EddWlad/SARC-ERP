# Comprobaciones automatizadas006

2026-11-06. Backend Java25.0.3/Spring Boot4.1.1/Maven wrapper; frontend Angular22. Entorno Windows. PostgreSQL18 solo Testcontainers; Compose desactivado y almacenamiento simulado.

## Frontend

| Comando | Resultado | Evidencia |
|---|---|---|
|npm run api:types|OK,2archivos regenerados|feature006-final-api.log|
|npm test -- --watch=false|OK,53archivos/218pruebas|feature006-final-tests.log|
|npm run lint|OK|feature006-final-lint.log|
|npm run format:check|Falla:96archivos con diferencia de finales de línea/formato del checkout|feature006-final-format.log|
|npx prettier --check --end-of-line auto 'src/**/*.{ts,html,scss}'|OK para todo src|feature006-format-auto.log|
|npm run build|OK,342,14kB inicial/95,01kB transferencia|feature006-final-build.log|

Logs locales en sarc-frontend/. Se corrigieron archivos propios señalados por formato; no se reformatearon módulos ajenos. El gate estricto no se presenta como verde. Vitest emite aviso de canvas no implementado por jsdom; la dona real se comprueba en navegador.

## Backend

Ejecución final: ./mvnw.cmd -o -Dsarc.qa.transitionPerformance006=true test. BUILD SUCCESS,576pruebas,0fallos/errores/omitidas,7m33s. Log target/feature006-final-all-tests.log. Incluye ArchUnit7 y OpenApiContractTest sin actualizar contrato. El benchmark de transición requiere selección explícita por su nombre Bench y se ejecuta aparte.

Ejecución dirigida final previa: UptimeMeasurementApiTest,Feature006RolloutTest,Feature006PerformanceBench con sarc.qa.performance006=true:18pruebas,0fallos/errores,BUILD SUCCESS,14m49s. Incluye la nueva regresión de la grilla semanal y pg_dump/pg_restore en otra base desechable.

Otras ejecuciones por historia y fases rojas documentadas en foundation/us1/us2/us3/us4.md. No sumar conteos repetidos. OpenApiContractTest se regenera con openapi.update=true solo al cambiar el contrato; la suite final debe pasar sin ese parámetro.

[migration-hashes.json](migration-hashes.json): contenido Git normalizado y SHA256 de V001–V015,15/15 iguales a HEAD. V016 nueva. No se modificaron migraciones aplicadas.

El formato estricto y los objetivos de rendimiento no cumplidos son limitaciones registradas, no fallos funcionales ocultados. No certifica proveedor real, operación en dev/producción ni compatibilidad de binario005 con datos006.

## Ajustes posteriores y gate dirigido

Después del gate global se añadió espaciado opcional CorporatePdf.note(text,spacingAfter), usado solo por uptime; el método anterior conserva su valor0. Se ejecutaron UptimeReportConsistencyTest3,UptimePdfRendererTest3,ReportPublicationTest5 y Feature006TransitionPerformanceBench1, todos verdes. La misma ejecución detectó un error en el fixture nuevo sin porcentaje (omitía cabecera obligatoriaUPTIME). Se corrigió el fixture incluyendo la columna con valor vacío, sin relajar el parser. Reejecución UptimeTemporalEvidenceTest9/9 verde, target/feature006-final-evidence.log. Incluye los dos nuevos casos sin porcentaje y cadenaBLOCK. El fallo previo y su corrección quedan en target/feature006-final-closure.log.

Los21 casos dirigidos finales están cubiertos en esas dos ejecuciones; no se declara que el primer comando dirigido completo terminó verde. No se repitió la suite global por el ajuste visual opcional que mantiene comportamiento anterior de los otros renderizadores.

## Formato y fechas (2026-11-06)

`.prettierrc` con `endOfLine: auto`: `npm run format:check` queda en verde sin reescribir archivos ajenos. Frontend: 54 archivos/221 pruebas, lint y format:check OK, build 342,14 kB inicial/94,99 kB. Desde/Hasta de captura pasan a fechas (`dayStartInstant`/`dayEndInstant`).

## Corrección del pegado (2026-11-06)

`EnerlinkBlockParser` acepta la duración antes de la etiqueta ("163 hrs 35 mins - Disponible"). Backend completo: 582 pruebas, 0 fallos/errores/omitidas. Frontend: 53 archivos/218 pruebas y lint OK. No se repitió build ni format:check por no haber cambiado el frontend.
