# Quickstart: validación de Feature 010 (incidencias, tickets y tiempos)

Esta guía explica cómo comprobar de punta a punta que el módulo funciona. El modelo está en [data-model.md](data-model.md), los contratos en [contracts/](contracts/) y las decisiones en [research.md](research.md); aquí no se repiten.

## Prerrequisitos

- JDK 25, Node según `package.json` y Docker para Testcontainers (PostgreSQL 18).
- **No** se ensaya `V021` contra la base de compose ni contra otra base persistente. Se usa un PostgreSQL desechable y, antes de publicar, la última copia cifrada restaurada en un entorno aislado (research R18). Una vez aplicada en un ambiente compartido, V021 no se edita: cualquier cambio va en V022.
- **Numeración de prueba**: los ambientes de prueba configuran `sarc.incidents.numbering.environment=TEST` y un número inicial ficticio, por ejemplo `9001`, que produce `TICKET-9001`. El número de producción (P-01) **no** se usa en pruebas ni se siembra en la migración. Se pide al responsable justo antes de publicar.
- **Ambiente de numeración en pruebas**: `Feature010Fixtures` y `TestcontainersConfiguration` fijan `sarc.incidents.numbering.environment=TEST`. Sin la propiedad, la aplicación arranca y solo se bloquea la numeración.
- **Datos sintéticos**: equipos con códigos ficticios `9xxxxx`, sitios «CC Prueba Norte» y «Sitio sin equipos», y textos sin datos de clientes. No se usan tickets del Excel histórico (P-16).
- **Evidencias** con `FileStorage` simulado; no se escribe en Cloudinary. Si alguna vez hace falta probar con Cloudinary, se usa una carpeta propia `sarc-qa010-*`, vacía antes y después, porque la cuenta es compartida.
- Al terminar, se cierran los puertos 8080, 4200 y 5432 que se hayan abierto.

## Puertas automáticas

En `sarc-bakend`:

```bash
./mvnw.cmd test
```

La suite incluye:

- las pruebas de dominio de `TicketStateAt`, `MilestonesAsKnownAt`, `IncidentKpiCalculator`, `TicketMessageComposer`, `ClosureRequirements` e `IncidentType` (compatibilidad por clase igual al `CHECK`);
- las pruebas de persistencia con Testcontainers: restricciones `CHECK`, unicidad del número y de `client_request_id`, inmutabilidad de las revisiones;
- `IncidentNumberingConcurrencyTest`: 50 creaciones paralelas sin huecos ni duplicados; reintento idempotente;
- `IncidentSecurityTest`: cada endpoint con los cinco roles, sin sesión y por acceso directo;
- `ArchitectureTest`, con la regla del módulo `incident` y el puerto `IncidentAssetDirectory`;
- `IncidentMigrationTest`, con V021;
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

Resultado esperado: cero fallos y el *bundle* inicial por debajo de 500 kB. `/incidencias` se carga de forma diferida y no aumenta el *bundle* inicial.

## Escenarios de validación

Los instantes están en `America/Guayaquil`. «Corte» es el instante simulado con un `Clock` fijo en las pruebas.

| # | Escenario | Pasos | Resultado esperado | FR / US |
|---|-----------|-------|--------------------|---------|
| 1 | Numeración sin configurar | Servidor con ambiente TEST, sin configurar; crear un ticket | 409 `TICKET_NUMBERING_NOT_CONFIGURED`. La interfaz muestra la causa y, a un Administrador, el acceso a la configuración | FR-013 |
| 2 | Configuración de la numeración | Un Administrador configura 9001, TEST, con la confirmación «TICKET-9001» | El siguiente ticket es `TICKET-9001`; el número inicial ya no se puede cambiar (409 `TICKET_NUMBERING_LOCKED`) | FR-012, FR-013 |
| 3 | Ambiente cruzado | Configurar PRODUCTION en un servidor TEST | 422 `TICKET_NUMBERING_ENVIRONMENT_MISMATCH` | FR-014 |
| 4 | Formato | Números 86, 999 y 1000 en tres bases de prueba | `TICKET-086`, `TICKET-999`, `TICKET-1000` | FR-012 |
| 5 | Concurrencia | 50 creaciones simultáneas de equipos distintos | 50 números consecutivos, sin huecos ni duplicados | FR-012, FR-015, SC-003 |
| 6 | Doble clic | Repetir el mismo `clientRequestId` | Mismo ticket, 200; no se consume otro número | FR-015 |
| 7 | Creación con tres campos | Equipo, «Desconexión» y descripción | Ticket abierto; T0 igual al momento de creación; prioridad propuesta Alta; origen «Plataforma de monitoreo» | FR-010, FR-018, FR-034, US1, SC-001 |
| 8 | Conectores | Electrolinera de 2 conectores: sin elegir conectores, y otra con el conector 2 | El primero es del «equipo completo»; el segundo muestra «… / Conector 2». Nunca hay dos tickets para la misma falla general | FR-002, FR-003, SC-010 |
| 9 | Duplicado | Otro ticket del mismo equipo, otro del chatbot y otra barrera del mismo sitio y nombre, cada uno con uno abierto | 409 `TICKET_OPEN_DUPLICATE` con la lista en los tres casos. Se crea con motivo auditado o se agrega el seguimiento al existente. Una barrera con otro nombre no avisa | FR-016 |
| 10 | Falla común | Tres equipos del sitio con la misma falla | Tres tickets relacionados `COMMON_FAILURE`, visibles en ambos sentidos | FR-017, Q5 |
| 11 | Chatbot | Ticket «Falla del servicio de chatbot», alcance parcial | Sin equipo, conectores ni red. «Sin red» en los repartos; no tiene referencia de tiempo; el cierre no pide confirmaciones de cargador | FR-011, FR-048, US3, SC-011 |
| 12 | Barrera con equipo de referencia | Barrera de «CC Prueba Norte» con un equipo del sitio como referencia | Toma la ubicación y Maps del equipo; no tiene conectores ni red. Se muestra «CC Prueba Norte / Barrera vehicular» | FR-001a, US3-5 |
| 13 | Elemento sin equipo y sin Maps | «Otro elemento del sitio» en «Sitio sin equipos», sin enlace | Se crea sin error ni equipo; el mensaje tiene la línea `Google Maps: ` vacía y se copia | FR-001a, FR-057, US3-6, SC-007 |
| 14 | Hitos y retroactivos | T1 a 10 min, T2 hace 40 min con motivo, T3 «Sin dato» con motivo | Las marcas son R, R* y S/D; T3−T0 queda sin calcular (nunca 0) | FR-040 a FR-042, FR-045, SC-002 |
| 15 | Orden de hitos | T2 anterior a T1; un hito anterior a T0 | 422 `MILESTONE_ORDER_UNCONFIRMED`, que con la confirmación se acepta y queda marcado; 422 `MILESTONE_BEFORE_T0` | FR-043 |
| 16 | Corrección | Corregir T1 con justificación | Nueva fila de revisión; la cronología muestra el valor anterior, el nuevo, el autor y la hora | FR-036 |
| 17 | Referencias | Falla de conector con T3 a 200 min | «Fuera de referencia» (180 min), con la leyenda «no SLA». Se pide la justificación de la desviación al cerrar | FR-051 a FR-053 |
| 18 | Versión de referencias | Insertar en la prueba la versión 2 con 150 min y vigencia posterior; abrir un episodio nuevo | El episodio anterior se evalúa con 180 y el nuevo con 150; un informe ya emitido no cambia | FR-051a, P-03 |
| 19 | KIA persistente | Punto KIA con 30 min exactos y con 30 min 1 s | 30 min: todavía no es persistente; 30 min 1 s: persistente (estrictamente más de 30, sin redondear) | FR-051, P-04 |
| 20 | Relevo de turno | Relevo con estado actual, pendientes, próxima verificación y criterio de cierre | El responsable cambia; queda el evento `SHIFT_HANDOVER` con el saliente, el entrante y los cuatro campos | FR-033 |
| 21 | Visita | Solicitada, confirmada, aplazada sin condición, confirmada, ejecutada | El aplazamiento sin condición de reescalamiento da 422; los conteos por etapa alcanzada son correctos | FR-035, P-08 |
| 22 | Cierre incompleto | Cerrar un ticket de equipo sin T3 ni evidencia | 422 `TICKET_CLOSE_REQUIREMENTS` con `missing[]` | FR-047 |
| 23 | Reapertura | Cerrar y reabrir | El ticket conserva el código; hay un episodio 2 con T0 propio y la versión vigente; el episodio 1 no cambia | FR-044, FR-049, US5 |
| 24 | Reapertura prohibida | Cerrar como duplicado e intentar reabrir | 409 `TICKET_REOPEN_FORBIDDEN` | FR-049 |
| 25 | Mensaje con opciones | Activar «Incluir hora de detección» e «Incluir acciones» | Se añaden la línea `Detección:` y las acciones no anuladas; al reabrir el diálogo vuelven a estar desactivadas | FR-056a, Q3 |
| 26 | Copiar no es enviar | Copiar el mensaje y cerrar el diálogo | No se crea ninguna comunicación ni evento. «Registrar envío» con canal y hora real sí lo crea | FR-059 |
| 27 | Control: abierto al final del mes | T0 el 25/10, T4 el 03/11; informe de octubre y de noviembre | Octubre: Abierto; noviembre: Cerrado; todo el historial: Cerrado | FR-074, US6-4a |
| 28 | Control: reabierto | Cerrado el 20/10 y reabierto el 05/11 | Aparece una sola vez en cada informe: octubre Cerrado; noviembre según el episodio 2 | FR-074, US6-4b |
| 29 | Cierre en el límite | T4 exactamente a las 00:00 del 01/11 | En octubre está Abierto, porque el fin es exclusivo | Edge, FR-074 |
| 30 | Período que no ha terminado | «Este mes» con el corte a mitad de mes | El estado se calcula al corte | Edge |
| 31 | Corrección después de emitir | Emitir el informe de octubre; corregir T4 a una fecha de octubre; emitir otra vez | El primer informe archivado conserva su SHA-256 y su `rowsSha256`; el segundo muestra Cerrado. La reconstrucción con `MilestonesAsKnownAt(corte1)` reproduce las filas del primero | FR-074a, FR-086, SC-012 |
| 32 | Informe vacío | Período sin tickets | PDF y Excel con encabezado y «Sin tickets en el período» | Edge |
| 33 | Gestión: denominadores | Fixture con episodios abiertos, «No aplica» y «Sin dato» | Cada promedio con su `n` y los excluidos por causa; los abiertos muestran el tiempo transcurrido aparte; los cerrados por duplicado o error quedan aparte | FR-046, FR-050, FR-080, SC-004 |
| 34 | Coherencia | Mismo filtro y corte en el tablero, PDF y Excel | Mismas cifras. Las sumas de la hoja `Episodios` reproducen `Resumen` | FR-086, SC-005 |
| 35 | Red histórica | Pasar un equipo de JAC a KIA después del ticket | El ticket sigue en JAC en los repartos; el nombre mostrado es el actual | FR-007, P-14 |
| 36 | Equipo desactivado | Desactivar el equipo de referencia de una barrera | El ticket sigue mostrándose; un ticket nuevo no lo ofrece como referencia | Edge |
| 37 | Evidencia | Subir un PDF de 2 MB, un PNG con extensión `.pdf` y un archivo de 11 MB | El primero se acepta; el segundo se guarda como PNG por su firma binaria; el tercero da 413 `EVIDENCE_TOO_LARGE`. Un `.exe` renombrado da 422 | FR-037 |
| 38 | Conflicto de edición | Dos pestañas registran hitos con la misma `version` | La segunda recibe 409 `TICKET_VERSION_CONFLICT` y la interfaz ofrece recargar | Edge |
| 39 | Fuentes externas | Informe de gestión | Remite a Uptime e Intermitencias sin cifras propias de disponibilidad | FR-083, P-13 |
| 40 | Corrección de datos | Corregir la descripción y el origen sin justificación, y después con ella | Sin justificación: 400; con ella, dos eventos `DATA_CORRECTED` con el valor anterior y el nuevo. No existe forma de cambiar el elemento afectado | FR-036 |
| 41 | Código anterior al inicial | Con la numeración desde 9001, buscar `TICKET-085` | Lista vacía con `notice = TICKET_BEFORE_START` y el mensaje de la secuencia externa | Caso límite |
| 42 | Actualización y conectividad | Abrir el tablero y cortar la conexión con el backend | Se muestra la hora de la última actualización y un aviso de pérdida de conectividad, que desaparece al reconectar | FR-063 |
| 43 | Escalamiento a técnicos | Espera de conexión: (a) escalamiento `SUPERVISION` a 10 min y `TECHNICAL` a 70 min, T3 a 90 min; (b) sin escalamiento y T3 a 45 min; (c) abierto a 90 min sin escalamiento; (d) T3 a 45 min y `TECHNICAL` a 70 min; (e) `TECHNICAL` a 30 min y T3 a 120 min; (f) cerrado con T3 «Sin dato» y sin escalamiento | (a) `OUTSIDE`, porque solo cuenta el técnico; (b) `WITHIN`; (c) `OUTSIDE` al corte; (d) `WITHIN`, porque el escalamiento llegó después del fin de la espera; (e) `WITHIN`; (f) `NOT_EVALUABLE` | FR-052 |
| 44 | Causa del cierre | Cerrar con causa confirmada sin descripción, y con causa probable sin evidencias | `missing` con `CAUSE_TEXT` y con `PROBABLE_CAUSE_EVIDENCE` | FR-047 |
| 45 | Orden del listado | Ordenar por cada valor de `sort`, y pedir `sort=element` | El orden cambia según `sort`; por defecto, última actualización descendente; `element` da 400 | FR-061 |
| 46 | Todos los conectores | Ticket con los 2 conectores de una electrolinera elegidos | Se muestra como equipo completo y el detalle conserva la selección | Caso límite |
| 47 | Ambiente sin configurar en el servidor | Arrancar sin `sarc.incidents.numbering.environment` | La aplicación arranca; `GET /numbering` da `expectedEnvironment: null`; configurar da 422 y crear da 409, ambos `TICKET_NUMBERING_ENVIRONMENT_MISMATCH` | FR-013, research R2 |
| 48 | Responsables elegibles | Un Analista pide `GET /responsibles`, y asigna o releva a un Gerente, a un Analista inactivo y a un Analista activo | La lista trae solo editores activos, sin correo; Gerente e inactivo: 422 `TICKET_RESPONSIBLE_INVALID`; activo: cambia el responsable y su nombre en el listado. `GET /api/v1/users` sigue dando 403 al Analista | FR-033, research R8 |

## Matriz de permisos

| Acción | Administrador | Analista | Gerente, Supervisor, Asistente |
|--------|---------------|----------|--------------------------------|
| Configurar la numeración | Sí | 403 | 403 |
| Crear, hitos, eventos, visitas, comunicaciones, evidencias, cerrar, reabrir | Sí | Sí | 403, sin cambios en ninguna tabla |
| Listado, detalle, cronología, evidencias, mensaje, tablero e informes | Sí | Sí | Sí |
| Botones de edición en la interfaz | Visibles | Visibles | Ocultos (sin «Nuevo ticket»); `/incidencias/numeracion` bloqueada por `roleGuard` |

Comprobación adicional:

- Un usuario de consulta no puede escribir cambiando el método HTTP ni el cuerpo de la solicitud.
- Si un usuario pierde el rol, su siguiente solicitud se evalúa ya sin él.
- `allowedActions` está vacío para los roles de consulta.

## Regresiones

- Consumo, Inventario, Uptime, Intermitencias, Informes y autenticación pasan su suite completa sin cambios de contrato propios.
- `Feature010RegressionBaseline` compara las cifras y los informes de esos módulos antes y después de crear tickets: deben ser idénticos.
- Mover `period.ts` y `period-filter` a `shared/ui/period/` (research R13) no cambia Consumo ni Intermitencias. Se verifica con sus pruebas y con capturas comparadas.
- Las nuevas claves de `IssuedReportKinds` no cambian la lista de informes archivados ni sus permisos para los tipos existentes.

## Rendimiento (con `-Dsarc.qa.performance010=true`)

| Medición | Criterio |
|----------|----------|
| Listado paginado con 5.000 tickets y 3 filtros (mediana) | menos de 1 s |
| Tablero de un año con 5.000 tickets | menos de 2 s |
| Control de tickets, «Todo el historial» con 5.000 tickets, PDF y Excel | 10 s o menos (research R19) |
| Gestión de un año, PDF y Excel | 10 s o menos |
| Creación de un ticket (mediana) | menos de 500 ms |

## Comprobación en el navegador y revisión visual

1. Levantar `Feature010BrowserSandbox` (Testcontainers, puerto aleatorio y datos sintéticos de los escenarios) con `-Dsarc.qa.browser010=true`, y Angular con el proxy que genera (configuración `sarc-frontend-qa010`).
2. Recorrer estas rutas:
   - `/incidencias/tablero`;
   - `/incidencias/tickets` (listado);
   - el diálogo «Nuevo ticket» del listado, con sus tres clases de elemento y «Más datos»;
   - `/incidencias/numeracion` (solo Administrador);
   - `/incidencias/tickets/:id` (detalle con cronología, hitos y episodios);
   - el diálogo del mensaje;
   - los diálogos de hito, cierre, reapertura, visita, comunicación y relevo;
   - `/incidencias/control`.
3. Medidas y temas: 360, 390, 768, 1024, 1366 y 1440 px, en tema claro y oscuro.
4. Comprobar:
   - que no hay desplazamiento horizontal;
   - que ningún elemento queda sin nombre accesible;
   - un contraste de al menos 4,5:1 (3:1 en texto grande);
   - un foco visible de 3 px;
   - etiquetas `floatLabel="always"` sin solapamientos con textos largos, errores ni descripciones;
   - tarjetas por debajo de 768 px;
   - estados vacíos, de carga y de error.
5. Comparar con Consumo, Inventario e Intermitencias:
   - mismos *tokens* `--mat-sys-*` y `--sarc-*`, `list-page`, `kpi-tile`, insignias y `sarc-chart-view`;
   - acciones de descarga como botones *outlined* en `header-actions`;
   - acción principal a la derecha;
   - diálogos de 760 px con `guardUnsavedChanges`.

   Cualquier diferencia se corrige o se justifica.
6. Roles: como Analista y como Gerente, comprobar que aparecen o desaparecen las acciones y que la ruta directa queda bloqueada.
7. Guardar las capturas de evidencia en el registro de cierre y cerrar los puertos.

Quedan sin medir con una persona los tiempos de SC que requieren cronómetro, como crear un ticket con tres campos y copiar el mensaje. Se registran como pendientes con una persona del equipo.

## Antes de publicar (solo como referencia; en esta etapa no se ejecuta)

1. **P-01**: pedir al responsable el número inicial de producción el mismo día de la publicación. Después de publicar, un Administrador lo configura en la pantalla de numeración con la confirmación del código.
2. **P-02**: confirmar la versión del protocolo. Si se resuelve, se cambia solo el rótulo de la versión de referencias, con una migración de datos que no cambia los valores.
3. Respaldo previo, ensayo de V021 sobre la copia restaurada, y el flujo `ci.yml`, `version-candidata.yml` y `publicar.yml` con aprobación del entorno `production`.
4. Después de publicar, el *issue* `ensayo-restauracion` de cambio con migración se cierra con un ensayo aprobado en 7 días.


---

## Evidencia del cierre (2026-11-23)

Resultados de las puertas y mediciones al terminar US1 a US7. Nada está publicado ni en commit.

**Rendimiento** (`Feature010PerformanceBench`, 5.000 tickets y 50.000 eventos sintéticos, PostgreSQL 18 de Testcontainers; cada operación se calienta una vez y se mide la segunda llamada):

| Operación | Límite | Medido |
|-----------|--------|--------|
| Listado con 3 filtros | 1 s | 0,2 s |
| Resumen de un año | 2 s | 1,8 s (margen estrecho) |
| Control de todo el historial, PDF | 10 s | 2,2 s |
| Control de todo el historial, Excel | 10 s | 1,0 s |
| Gestión de un año, PDF | 10 s | 3,3 s |
| Gestión de un año, Excel | 10 s | 3,6 s |
| Creación de un ticket | 0,5 s | 0,4 s |

La primera versión del resumen tardaba 16,7 s: la regla de referencia se pedía a la base una vez por episodio. Se cacheó por versión y tipo, el tablero dejó de leer la bitácora completa y los lotes de carga de episodios e hitos subieron de 50 a 500.

**Puertas**:

- Backend: `./mvnw.cmd test` completo con código 0, con `OpenApiContractTest` sin actualizar.
- Frontend: `npm run api:types` sin cambios, 794 pruebas, `npm run lint` y `prettier --check` correctos, y `npm run build` correcto. El *bundle* inicial es de 348,30 kB (límite 500 kB) y `/incidencias` se carga de forma diferida. El estilo de `dashboard-page` supera en 450 bytes el presupuesto de 4 kB por componente, un aviso como el de otras tres pantallas.
- Regresión (`Feature010RegressionBaseline`): Consumo, Uptime, Intermitencias e Inventario dan lo mismo antes y después de crear tickets e emitir informes.
- Seguridad (`IncidentSecurityTest`): los seis conjuntos de roles contra cada endpoint de escritura, sin sesión (401), sin CSRF (403), cambio de método o de cuerpo y rol retirado. La carga de evidencia (multipart) no está en la matriz; se prueba en `TicketEvidenceApiTest`.

**Revisión del navegador**: se hizo por historias, con el sandbox `Feature010BrowserSandbox`, en 360 px y escritorio, y no cubre la matriz completa de T110 (390, 768, 1024, 1366 y 1440 px, tema oscuro, contraste y foco). Los tiempos con cronómetro (SC-001 y SC-007) no se midieron con una persona: SC-007 se contó por acciones en US4 y SC-001 queda pendiente.

**Revisión final del diff** (T112): se hicieron revisiones `code-review` al cierre de cada historia. En el cierre se revisaron a mano los puntos de seguridad nuevos (listado con `= ANY` sobre UUID, nombres de archivo de los informes, permisos de los servicios y filtros por enumeraciones). No se ejecutó la skill `security-review` sobre el diff completo, que sigue sin commit.

**Revisión del navegador, cierre de T110**: las cinco pantallas de lectura y los once diálogos del detalle se recorrieron a 360, 390, 768, 1024, 1366 y 1440 px, sin desbordes ni acciones fuera de pantalla, y con los estados de error de validación a 360 px. Contraste WCAG AA correcto en claro y oscuro; foco visible (2 px con separación de 2 px, regla global del sistema de diseño). Corregido: las etiquetas de confirmación de «Cerrar ticket» desbordaban a 360 px y se acortaron. Comparación con Inventario, Consumo e Intermitencias: mismos estilos de título, botones, campos y diálogos. `impeccable` sin hallazgos ni cambios. axe-core 4.14.1: 0 violaciones en los once diálogos a 360 px (claro y oscuro) y a 1366 px (claro). Pendiente con una persona: los cronómetros de SC-001 (menos de 1 minuto con tres datos) y SC-007 (no medido con reloj; contado en dos acciones). Protocolo: iniciar el reloj al abrir «Registrar», elegir equipo, tipo y escribir la descripción, y detenerlo al ver el ticket creado; repetir al menos cinco veces y anotar el mayor tiempo.

**Ensayo de V021 sobre una copia de producción (T009)**: hecho el 2026-11-23 con la copia nocturna del mismo día en un PostgreSQL 18 desechable (sin red, sin puertos, rol sin superusuario). `pg_restore` sin errores con V020 y 35 tablas iguales a los metadatos; V021 se aplicó en 251 ms y dejó intactos los conteos y las columnas de las 35 tablas existentes, con 13 tablas `inc_*` nuevas y la numeración sin configurar. Entorno destruido. Con esto T118 ya no está bloqueada por T009; sigue necesitando tu autorización.
