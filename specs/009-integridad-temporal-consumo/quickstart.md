# Quickstart: Validación de la Feature-009

Guía para la implementación y su verificación. Los comandos usan suites existentes que se ampliarán con los casos de esta guía. El documento define resultados esperados y no certifica que las pruebas nuevas se hayan ejecutado.

## Preparación

Usar Java 25, Maven Wrapper, Docker con PostgreSQL 18 vía Testcontainers, Node 24 y npm. Trabajar sobre un checkout autorizado aislado del árbol de pruebas de Feature-008, con datos sintéticos o copia autorizada y almacenamiento local de prueba. Mantener deshabilitados los destinos de producción. Los scripts se ejecutan desde la raíz salvo donde se indica.

Para levantar una referencia visual con los componentes actuales, utilizar la fixture aislada existente `Feature003BrowserSandbox` descrita en [la guía de Feature-003](../003-revision-energia-ranking-empates/quickstart.md). En dos terminales PowerShell, desde la raíz de ese checkout:

```powershell
Set-Location sarc-bakend
.\mvnw.cmd test '-Dtest=Feature003BrowserSandbox' '-Dsarc.qa.browser=true'
```

```powershell
Set-Location sarc-frontend
npm ci
npm start -- --host 127.0.0.1 --port 4201 --proxy-config ../sarc-bakend/target/feature003-browser-proxy.json
```

Abrir `http://127.0.0.1:4201` una vez que la fixture genere su proxy. Para finalizar el sandbox, seguir el mecanismo de señalización de la guía de Feature-003. Para la validación funcional posterior, usar una fixture de Feature-009 con la misma topología y sus datos de aceptación. El sandbox previo es útil para intentar reproducir el parpadeo con el código anterior.

## 1. Atribución temporal y reportes

Validar US1 con estados de ensayo preparados en una base aislada. Los cambios entre lecturas se realizan con la fixture, que confirma cada revisión antes de consultar. Los flujos de aprobación y edición se ejercitan en las secciones 2 y 3.

1. Preparar dos sesiones oficiales elegibles, con vínculos verificados. Enerlink 1212 declara inicio 11/11/2026 01:36:15 con `UTC+00:00` y 8 kWh. Wallbox declara 10/11/2026 23:59:11 en Ecuador y 5 kWh. Fijar el corte y usar la misma selección para todos los canales.
2. Consultar los dos días juntos y por separado en histórico y dashboard. Exportar PDF y Excel. Esperar 1 sesión y 5 kWh el 10/11, 1 sesión y 8 kWh el 11/11, y 2 sesiones y 13 kWh en total. El filtro exclusivo de Enerlink conserva 1212 el 11/11. El desglose autorizado presenta su inicio 01:36:15 y conserva el instante real. Incluir la sesión 1211 en una prueba independiente para no alterar el total del conjunto de dos.
3. Verificar los cuatro casos de [encabezados Enerlink](contracts/consumo.md#importación-y-correcciones). Un inicio sin declaración a las 01:36:15 del 11/11 se interpreta como 06:36:15 UTC con zona aplicada de Ecuador. Un inicio a las 01:36:15 con `UTC+00:00` y un fin a las 01:40:15 con `UTC+01:00` son incoherentes por instante. Un fin a las 02:40:15 con ese mismo desplazamiento sí es válido. Comprobar que Fin sin declaración hereda el desplazamiento resuelto y que una declaración ilegible o contradictoria deja la fila inválida. Incluir cruces de medianoche, energía 0 kWh y energía ausente.
4. Simular en pruebas el 30/11/2026 a las 20:30 en Ecuador, 01/12/2026 a la 01:30 UTC. El rango inicial queda `[01/11, 01/12)` para dashboard e histórico. La sesión con fecha de origen 01/12 aparece al seleccionar ese día y no en el rango inicial. Comprobar que la atribución no depende de la zona del navegador y que los accesos rápidos se actualizan con el día de Ecuador.
5. Descargar un original archivado de la fixture y verificar su identificación, corte y huella. Preparar con la fixture una revisión posterior, emitir un informe nuevo y volver a descargar el original. El informe nuevo refleja la revisión aplicable y el original conserva su contenido y corte. Probar el acceso permitido y bloqueado según los roles vigentes de Feature-004.
6. Añadir a la fixture una sesión histórica oficial de 2 kWh con fecha heredada 10/11. Esperar 7 kWh y dos sesiones el 10/11, 8 kWh y una sesión el 11/11 y 15 kWh y tres sesiones en total. Comprobar la marca de la fila y el desglose oficial de 2 kWh y una sesión en dashboard, PDF y Excel. Repetir con energía ausente en esa sesión, conservando el conteo y mostrando su energía como desconocida. Sembrar un segundo estado de la misma sesión con su fecha de origen verificada y dentro del rango consultado. Comprobar que mantiene su identidad y se cuenta una sola vez, fuera del desglose heredado.
7. Comparar las superficies con las revisiones de la fixture sin cambios y con los mismos filtros y permisos. En una prueba separada, consultar el dashboard con las dos sesiones iniciales, preparar y confirmar con la fixture la siguiente revisión de la sesión de 8 a 9 kWh, avanzar el reloj de ensayo y exportar. El dashboard original muestra 13 kWh y su hora de cálculo. El informe posterior muestra 14 kWh y su corte posterior, también en `X-Report-Cut-Off`. El dashboard actualizado muestra 14 kWh.

## 2. Auditoría histórica

1. Preparar cuatro casos: lectura correcta, diferencia de atribución con instante correcto, error de instante comprobable y evidencia no recuperable. Usar originales con huella válida y un lote con una revisión posterior para probar la selección de la fila efectiva. Incluir también un original con huella discordante.
2. Ejecutar el diagnóstico en la copia de ensayo. Comprobar universo, cobertura, clasificación y diferencias por período. Verificar que por sí solo no escribe en sesiones. Los casos sin evidencia suficiente quedan indeterminados y se distinguen en la revisión de cobertura.
3. Intentar aprobar como Analista y comprobar `403` y cero constancias. Aprobar como Administrador autenticado y verificar que la constancia persistida registra su identidad, el momento, la propuesta y la huella. Ejecutar el aplicador sin constancia, con identidad indicada solo en el archivo, con huella cambiada y con una constancia de otra propuesta. En todos esos casos deben quedar cero cambios. Con una constancia válida, probar conflicto tras una edición concurrente y reintento después de una aplicación parcial. Comprobar una sola revisión por candidato aplicado y valores anteriores disponibles.
4. Para un cambio exclusivamente temporal, cotejar conteo y kWh globales con igual elegibilidad. Comparar días y meses afectados. Conservar evidencia, corte e informes previos. El [contrato operativo](contracts/consumo.md) y el [modelo](data-model.md) definen los datos a comparar.
5. Antes de revisar la publicación del histórico, ejecutar el diagnóstico de solo lectura sobre una copia autorizada y aislada del conjunto histórico pertinente. Registrar la identificación y el corte de la copia, universo, fuentes y períodos cubiertos, cantidad y kWh de casos indeterminados e impacto esperado por fuente y período. Registrar los límites de la evidencia y verificar que no se escribieron sesiones. Si no hay una copia autorizada o falta el diagnóstico, dejar pendiente la revisión del alcance histórico. La ejecución de correcciones sobre el histórico real exige una autorización separada.
6. En el ensayo de US2, repetir la transición del paso 6 de la sección 1 mediante el flujo completo: acreditar la fecha de origen de la sesión heredada, registrar la aprobación y aplicar la propuesta. Verificar que la sesión sale del desglose, mantiene su identidad y se cuenta una sola vez. Descargar el informe archivado y comprobar que conserva sus bytes, identificación y corte.

## 3. Acciones, permisos y reimportaciones

1. Probar Admin, Analista, consulta y combinaciones de roles. Verificar el detalle permitido, el rechazo directo de escrituras sin permiso y el acceso restringido a evidencia y auditoría. Comprobar que una sesión no visible conserva la respuesta de no encontrada para el rol sin alcance.
2. Editar con motivo una sesión, comprobar revisión anterior/nueva y reintentar con versión obsoleta. Repetir el escenario del paso 7 de la sección 1 con las dos sesiones iniciales y guardar el cambio de 8 a 9 kWh mediante la acción Editar. Comprobar 13 kWh en la vista inicial, 14 kWh en el informe posterior y en el dashboard actualizado, y la revisión registrada. Probar fin anterior al inicio, energía negativa, energía nula, 0 kWh, redondeo por lectura, energía sobre 1.000 kWh y cambio de equipo que requiere reevaluar la fuente. Verificar que los casos pendientes siguen fuera del total oficial.
3. Cancelar la baja y luego confirmarla con motivo. Verificar la exclusión en lista oficial, resumen, tendencia y reportes, y que la auditoría autorizada permite reconstruirla. Reimportar lecturas idénticas y discrepantes de sesiones editadas y dadas de baja. Confirmar cero duplicados y cero reactivaciones o reversiones automáticas.
4. Preparar un lote antes de editar o dar de baja una sesión y confirmarlo después. Probar también una versión previa de lote sin token de versión. La importación revalida antes de escribir, conserva la decisión vigente y puede incorporar filas nuevas no conflictivas.

## 4. Estabilidad visual

1. Capturar trazas de red, navegación y ciclo de vida del gráfico con la aplicación anterior. Registrar vista, viewport, tema, acción, cambios en el DOM o lienzo y reproducción observable. Mantener activa la sesión. Observar al menos dos intervalos de 60 s, volver a la pestaña y simular una respuesta tardía, idéntica, modificada y fallida. Distinguir una animación del gráfico de una navegación o del reemplazo completo de la vista.
2. Repetir el mismo recorrido después del ajuste. Para el mismo filtro y datos equivalentes, verificar cero reemplazos injustificados del gráfico o la tabla y cero repeticiones de la animación de entrada. Los datos nuevos aparecen y una respuesta tardía de otro filtro nunca se muestra como vigente. Comprobar que se mantiene el corte por inactividad y que la sesión puede expirar.
3. Repetir con diálogo abierto, campos sin guardar, navegación por teclado y desplazamiento. Verificar que se conserva el contexto aplicable y se indica un error de refresco sin ocultar resultados todavía válidos para la misma consulta. Probar los controles a 360, 768 y 1440 px en tema claro y oscuro, foco visible, etiquetas, contraste y orden de teclado. Si se toca un componente compartido, repetir en Feature-008 tras coordinar su integración.
4. Adjuntar evidencia antes/después. Si el síntoma no se reproduce, registrar condiciones y resultado y mantener pendiente su verificación en lugar de certificar SC-006.

## 5. Verificaciones automatizadas y publicación

Comandos de PowerShell para ejecutar después de implementar y agregar los casos a las suites existentes. En Linux, usar `./mvnw` en lugar de `mvnw.cmd`.

```powershell
Push-Location sarc-bakend
.\mvnw.cmd -B test '-Dtest=SessionParsersTest,EnergySessionSchemaTest,ConsumptionCalculatorTest,ConsumptionApiTest,ConsumptionVisibilityApiTest,ConsumptionPdfRendererTest,ConsumptionExcelRendererTest,ConsumptionReportPrivacyTest,ConsumptionReadAccessTest'
.\mvnw.cmd -B verify
Pop-Location

Push-Location sarc-frontend
npm ci
npm run lint
npm run format:check
npm test -- --watch=false
npm run build
Pop-Location
```

Comprobar que el OpenAPI generado y `npm run api:types` coinciden con los artefactos versionados del checkout integrado. Revisar las pruebas de compatibilidad de esquema, roles, reportes archivados y los módulos RF-001, RF-002, RF-003 y Feature-008. El ensayo de actualización usa una base de la versión anterior y una migración nueva. Separar el respaldo previo a publicación del respaldo previo a la aplicación histórica. Verificar en una restauración aislada el contenido de sesiones, bajas y reportes y el estado del historial Flyway. Ante un incidente posterior, seguir [recuperación](../../docs/operacion/recuperar.md) y conciliar las escrituras posteriores al respaldo. Usar los procedimientos de [versión candidata](../../docs/operacion/version-candidata.md), [restauración](../../docs/operacion/restaurar.md) y [publicación](../../docs/operacion/publicar.md) de Feature-007. La publicación requiere su autorización habitual y cualquier aplicación histórica requiere la aprobación del conjunto verificado.
