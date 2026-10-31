# Quickstart: validación

## Registro de implementación — 2026-10-30

Rama `codex/003-revision-energia-ranking-empates`, base `cb9a763` (002 integrada en main). El usuario confirmó su recorrido real; no se modifica T075 de 002. V010 estaba libre y se utiliza únicamente aquí. Los nombres LINKABLE/linkableRows/LINKED se conservan.

Línea base reproducida desde `git archive cb9a763 sarc-bakend` en `sarc-bakend/target/feature003-baseline-source/`, con target independiente: **220 pruebas backend, cero fallos/errores**. Comando efectivo: wrapper Maven con `-o -f target/feature003-baseline-source/sarc-bakend/pom.xml test` (ruta desde raíz adaptada al wrapper). Angular previo: **25 archivos/96 pruebas**, lint y build correctos. Dependencias ya instaladas; sin actualización de paquetes. Java 25 configurado mediante JAVA_HOME. `-o` evita consultas de metadatos remotos. Logs de trabajo bajo `sarc-bakend/target/feature003-*.log` (ignorados por Git).

Un primer intento de línea base compartió target con pruebas dirigidas y se descartó por interferencia de compilación. Se detuvo y repitió en la copia independiente anterior. Las ejecuciones Maven posteriores sobre el checkout se realizan secuencialmente. Un error inicial de compilación Angular fue causado por restricciones de lectura del sandbox; la ejecución con permisos de herramienta pasó. Los resultados citados corresponden a las repeticiones válidas.

### Matriz implementada de correcciones y controles

| Entrada/operación | Condición energética | Historial y concurrencia |
| --- | --- | --- |
| Alta nueva >1.000 normalizada | PENDING | Revisión de lectura 1, sin decisión en preview |
| Otros datos, mismos kWh normalizados | Conserva PENDING/APPROVED/REJECTED | Snapshot previo; ninguna decisión automática |
| 1.500→1.800→1.500 | PENDING en cada cambio alto | No rescata decisiones históricas |
| <=1.000 o ausencia autorizada | Nula | Historial conservado |
| Aprobar/rechazar, incluso mismo estado | Nueva decisión justificada | Avanza versión de concurrencia, no revisión de lectura; CAS y rollback |
| TEST / sin equipo / fuente pendiente | Mantiene condición independiente | Categoría principal exclusiva según precedencia aprobada |
| Reimportación idéntica dos veces | Conserva decisión | Cero sesiones/revisiones/decisiones nuevas |

I1 se cubre en ParsedSessionTest y EnergyThresholdParserTest: solo el aviso conocido es no bloqueante; lectura nula u otro error invalida. A1 se cubre en ConsumptionApiTest y filtros Angular: contador de lecturas, independiente de clasificación/decisión, y aviso neutral. G1 usa exactamente el lote de nueve filas descrito más abajo y confirma 7 sesiones/5 oficiales/34 kWh/1 sin energía.

### Cobertura final FR/SC

| Requisitos | Evidencia automatizada |
| --- | --- |
| FR-001/002/011, SC-001/007 | ParsedSessionTest, EnergyThresholdParserTest, SessionParsersTest, EnergySessionTest, G1 |
| FR-003/004/005/007, SC-003 | EnergyReviewTest; API de decisiones, permisos, CSRF, versiones y carrera; EnergyReviewDialog |
| FR-006/009/010, SC-002 | Precedencia dominio/API, ambos órdenes, filtros independientes, resumen y componentes Angular |
| FR-008, SC-004 | Correcciones/reimportaciones API y dominio, G1 aprobado y rechazado |
| FR-012–016, SC-005/006 | ConsumptionCalculatorTest, API con PDF/Excel, TopEquipment |
| FR-017 | EnergySessionSchemaTest V009→V010, archivo de informes, suites 001/002 y hashes V001–V009 |

La matriz se verificó mediante las ejecuciones siguientes. Las pruebas de cada fila pasaron; las limitaciones de accesibilidad se detallan aparte.

### Resultados de ejecución

| Comprobación | Resultado |
| --- | --- |
| Backend completo (`./mvnw.cmd -o test`) | 236 pruebas; cero fallos, errores u omisiones. Incluye regresiones 001/002, esquema, OpenAPI sin update y 7 reglas ArchUnit |
| Últimas modificaciones de PDF/API/contrato/arquitectura | 51 pruebas dirigidas; cero fallos/errores, más 2 casos API específicos de contador con duplicadas altas y límite de informes |
| PDF mensual, resumen en primera página | Prueba roja reproducida antes del ajuste; prueba de renderer verde después. Verificación visual de las tres páginas |
| Angular completo (`npm test -- --watch=false`) | 29 archivos / 113 pruebas, cero fallos; prueba adicional del último texto del tablero correcta |
| `npm run lint` y `npm run build` | Correctos; bundle inicial 341,33 kB, presupuesto 500 kB |
| OpenAPI y tipos Angular | Contrato regenerado con OpenApiContractTest y `-Dopenapi.update=true`, luego `npm run api:types`; comprobación final sin update correcta |
| V001–V009 | Nueve SHA-256 idénticos a la línea base; V010 nueva, aplicada solo en contenedores de prueba |
| G1 | 9 filas: 4 NEW, 1 PENDING_LINK, 1 LINKABLE, 1 DUPLICATE, 1 MODIFIED, 1 INVALID; 7 sesiones / 5 oficiales / 34 kWh conocidos / 1 sin energía. Reimportaciones con aprobación/rechazo estables |
| Concurrencia | Dos decisiones iguales con la misma versión: 200 y 409; sin decisión/auditoría parcial. Orden de historial verificado también con timestamps iguales |

Los logs efectivos se conservan en `sarc-bakend/target/feature003-backend-full.log`, `feature003-final-verification.log`, `feature003-last-cases.log`, `feature003-pdf-final.log`, `feature003-frontend-final.log`, `feature003-dashboard-final.log`, `feature003-lint-final.log` y `feature003-build-final.log`. Son salidas de QA ignoradas por Git. La evidencia compartible queda en [evidence](evidence/).

### Recorrido real del sandbox

Comando backend: `./mvnw.cmd -o test -Dtest=Feature003BrowserSandbox -Dsarc.qa.browser=true`. Spring Boot RANDOM_PORT: **56199**, PostgreSQL **18.6** mediante Testcontainers, FileStorage mock. Angular: `npm start -- --host 127.0.0.1 --port 4201 --proxy-config ../sarc-bakend/target/feature003-browser-proxy.json`. La fixture genera el proxy y los CSV sintéticos; solo permanece abierta hasta crear `sarc-bakend/target/feature003-browser.done`. Resultado de la fixture: una prueba correcta.

- Ranking inicial de siete equipos: 540 kWh, Top de seis con posiciones 1,2,3,4,4,4 y nota; séptimo con posición 7 en el informe completo. Revisado en escritorio y móvil.
- Se cargó realmente `feature003-enerlink.csv`: 1.500 kWh sin término, aviso neutral, contador y filtro de lecturas altas, confirmación sin decisión energética en preview. Quedó fuera de oficiales hasta aprobar.
- Aprobación Enerlink: 8 oficiales / 2.040 kWh. Rechazo posterior: 7 / 540. Historial conserva ambas decisiones con persona/hora/lectura/revisión y razones recortadas.
- Wallbox mediante filas pegadas: 1.500 kWh. Aprobación ordinaria de fuente en preview no resuelve la energía. Se marcó prueba, se filtró por energía pendiente y se aprobó energía desde móvil: permaneció TEST y fuera de oficiales. Reincorporar conservó aprobación y dejó 8 oficiales / 2.040 kWh; Enerlink rechazada siguió aparte (1 / 1.500).
- PDF y Excel descargados con datos sintéticos. Excel: 8 oficiales / 2.040 kWh, aprobada sobre umbral como subconjunto 1 / 1.500, una rechazada aparte; justificación vigente y posiciones 1,2,3,4,4,4,7 verificadas. La inspección del PDF detectó que el bloque energético quedaba después del diario; se corrigió su ubicación al resumen inicial y se añadió regresión con período mensual.
- Los dos menús (tabla/tarjeta), permisos de lector y estados ENERGY_REVIEW/TEST se comprobaron con ocho casos Angular. Los dos órdenes de aprobación, reimportaciones, correcciones de término conservando aprobación/rechazo, reinicio al cambiar kWh, obsolescencia y carrera se comprobaron por dominio/API; no se afirma haber repetido manualmente toda esa matriz.

### Accesibilidad y límites de QA

Foco inicial en Decisión, Tab a Justificación y Escape para cerrar comprobados en navegador. Diálogo y botones visibles a 390×844 y escritorio a 1280×900; historial desplazable, textos de estado además de color.

AXE 4.14.0 se descargó temporalmente desde npm sin añadir dependencias al proyecto. Se analizaron instantáneas DOM reales en JSDOM: **0 violaciones WCAG 2 A/AA y 2.1 AA** en tablero y diálogo. El diálogo produjo una comprobación incompleta `aria-hidden-focus` por el fondo oculto; el foco y cierre se revisaron manualmente. JSDOM no certifica geometría ni contraste. Contraste calculado de los textos del diálogo oscuro: **14,38:1**, **14,41:1** y etiqueta de foco **10,92:1**, todos sobre 4,5:1. Véanse `evidence/axe-results.json` y `evidence/contrast.json`. Esto es una revisión focal, no una certificación completa de todos los temas, lectores de pantalla o plataformas.

Las pruebas usan almacenamiento simulado: no validan credenciales ni disponibilidad de Cloudinary. El recorrido con archivos reales de la Feature 002 sigue siendo el confirmado por el usuario; no se repitió sobre su BD local.

### Aislamiento y cierre

Antes del sandbox no había contenedores activos ni listeners en 8080, 4200, 5432 o 4201. No se arrancó Compose local ni se accedió a la BD dev; las URL JDBC de prueba usan puertos aleatorios y base `test`. No se modificó `.env`. El backend de QA finalizó correctamente por su archivo `.done`, Testcontainers eliminó sus contenedores y se detuvo únicamente la sesión Angular propia. Se cerró la pestaña y se restauró el viewport. Las dos descargas sintéticas se copiaron a evidencia y se retiraron de Downloads únicamente después de comparar sus hashes. Las salidas locales de pruebas se conservan bajo `target/`; no son datos del usuario.

El recorrido de implementación se validó antes de crear commits, sin push ni despliegue. V010 todavía debe aplicarse por el arranque normal del backend cuando el usuario decida probar esta funcionalidad en su entorno.

### Preparación de integración — 2026-10-31

El usuario solicitó agrupar los commits por funcionalidad, integrar en `main` y borrar la rama local `codex/003-revision-energia-ranking-empates`. Se incluyen las mejoras posteriores de confirmación de importaciones, estado vacío de conectores y documentación de interfaz en grupos separados. Las configuraciones locales de agentes y el estado del modo live quedan fuera de los commits.

Validación del frontend actualizado antes del merge: **30 archivos / 132 pruebas**, cero fallos; `npm run lint` y `npm run build` correctos. Bundle inicial **341,56 kB**, por debajo de 500 kB. Logs: `sarc-bakend/target/feature003-premerge-tests.log`, `feature003-premerge-lint.log` y `feature003-premerge-build.log`. No se modificó el backend durante esta preparación; se conserva la evidencia de sus ejecuciones anteriores. No se publica al remoto.

Guía para comprobar la feature ya implementada. Reglas en [data-model.md](data-model.md) y
[contracts/api-changes.md](contracts/api-changes.md).

## Requisitos

- Feature 002 integrada con V009 y sus regresiones verificadas; recorrido confirmado por el usuario. El plan reserva V010.
- Docker en marcha para Testcontainers.
- Probar la migración sobre PostgreSQL desechable.
- PostgreSQL 18 Testcontainers, almacenamiento mock/temporal y datos sintéticos. Para navegador, backend de prueba en puerto aleatorio y Angular en puerto libre separado (por ejemplo 4201), con proxy a ese backend. No arrancar perfil dev con Compose local.
- Al acabar detener únicamente recursos de QA identificados al arrancar, no servicios por número de puerto. No usar/descartar lotes reales ni modificar `.env` o la base del usuario.
- Reutilizar el patrón opt-in `Feature001BrowserSandbox.java` para un futuro `Feature003BrowserSandbox.java` con Testcontainers y FileStorage de prueba. El fixture de 003 todavía no existe: esta guía no es evidencia de recorrido ejecutado.

## Pruebas automatizadas

```bash
cd sarc-bakend && ./mvnw test
```

```bash
cd sarc-frontend && npm run build && npm test -- --watch=false && npm run lint
```

## Revisión de energía

Usar un archivo por fuente con una sesión de 1.500 kWh.

| Paso | Esperado |
| --- | --- |
| Vista previa | Fila nueva con aviso; filtro la encuentra; no pide aprobación |
| Confirmar | «Energía por revisar»; no suma; aparece en su total aparte |
| Aprobar sin justificación | Rechazado; sesión sin cambios |
| Aprobar con justificación | Suma una vez; historial con persona, fecha, texto y lectura |
| Rechazar después | «Energía rechazada»; deja de sumar; dos decisiones en historial |
| Aprobar de nuevo | Suma una vez; tres decisiones |
| Reimportar igual, dos veces | Cero sesiones y cero decisiones nuevas |
| Reimportar alta idéntica aprobada o rechazada | Conserva decisión; aviso neutral, sin anunciar nueva revisión. Contador «Lecturas superiores a 1.000 kWh» incluye esa fila sin representar pendientes |
| Corregir a 1.800 kWh | Vuelve a por revisar; versión previa conservada |
| Corregir a 900 kWh | Sin observación; historial conservado |
| Aprobada o rechazada, corregir solo término con iguales kWh normalizados | Conserva decisión; revalida otros controles y no genera otra decisión |
| Cambiar 1.500→1.800→1.500 kWh | Cada cambio alto reinicia revisión; no restaura aprobación antigua |
| Decidir con versión vieja | Revisión obsoleta; sin cambios |
| Dos decisiones simultáneas iguales con misma versión | Una aceptada, otra 409, sin decisión ni auditoría parcial |
| Repetir decisión con versión nueva | Nueva decisión justificada y versión incrementada; historial ordenado aun con timestamps iguales |

Límites: 1.000,000 sin aviso; 1.000,001 con aviso; 1.000,0004 sin aviso; 1.000,0005 con aviso; 10.000.000.000 inválida y
el lote continúa.

## Combinaciones

| Caso | Esperado |
| --- | --- |
| Sobre umbral sin equipo, aprobada | «Pendiente de vincular» hasta vincular |
| Fuente no preferente, aprobar energía y luego fuente | Suma al final |
| Fuente no preferente, aprobar fuente y luego energía | Suma al final |
| Aprobada, marcar prueba y reincorporar | Conserva decisión y vuelve a sumar |
| Sobre umbral con término anterior | Inválida |
| TEST + pendiente + revisión energética | Solo cuenta en pruebas; filtro energético sí la encuentra |
| Pendiente de vínculo + revisión energética | Solo cuenta en pendientes; tras vincular cambia de categoría sin doble conteo |
| Aprobada sobre umbral con todos los controles | Subconjunto de oficiales, no se suma otra vez |

Ejecutar ambos órdenes de aprobación desde ambos menús reales: fuente debe estar disponible con permiso aunque prevalezca estado energético. Aprobar fuente con energía pendiente no debe mostrar «ya suma».

## Ranking

| Datos | Esperado |
| --- | --- |
| Cuarto, quinto y sexto empatados, séptimo menor | 1, 2, 3, 4, 4, 4; nota de empate; séptimo fuera |
| Sin empate en quinto | Cinco, sin nota |
| Empate entre segundo y tercero | Cinco, sin nota |
| Tres equipos | Tres, sin nota |
| Diferencia solo en tercer decimal | Sin empate |

Tablero, PDF y Excel con mismos filtros coinciden. PDF y ranking completo usan posiciones con salto.

## Informes

Con una aprobada sobre umbral: PDF indica cantidad y energía; Excel marca la sesión y muestra su justificación vigente.
Regenerar un período no cambia reportes archivados.

Probar siete/todos empatados sin recorte visual. Ranking completo Excel conserva todos los equipos; su subconjunto Top coincide con tablero/PDF. Verificar límite de filas incluyendo los nuevos estados excluidos y justificación vigente leída en la misma instantánea de los datos.

## Migración y evidencia

En PostgreSQL desechable aplicar V001–V009 con datos sintéticos y luego V010; comparar datos previos, CHECK/UNIQUE/FK y nulabilidad de 002. Verificar versión del servidor, catálogo, historial Flyway y `ddl-auto=validate` después de mapear columnas nuevas. No editar migraciones aplicadas. Estas comprobaciones no se ejecutaron en planificación: no había PostgreSQL activo.

Registrar matriz FR/SC, unitarias/integración/API/Angular, ArchUnit, OpenAPI sin modo update, lint/build, capturas y verificación PDF/Excel. Probar foco/teclado, etiquetas, AXE y contraste WCAG AA. No marcar aceptación hasta disponer de resultados; esta guía define pruebas futuras.

## Regresión

### Lote mixto obligatorio de protección 002/003 (G1)

Preparación sintética en el mismo ámbito Enerlink, día operativo y filtros: sesión L de 20 kWh pendiente de vínculo y sesión C oficial de 10 kWh. Antes de la nueva vista previa, verificar una identidad única para L. Preparar otra identidad ambigua para U sin cambiar las reglas de inventario; todos los demás casos usan equipo y fuente preferente válidos. No crear decisiones energéticas previas para este primer lote.

| Orden/ID | Entrada | Clasificación esperada | Efecto al confirmar |
| --- | --- | --- | --- |
| 1 / N | Nueva, 2 kWh | NEW | Oficial |
| 2 / Z | Nueva, 0 kWh real | NEW | Oficial, cero conocido |
| 3 / M | Nueva, energía y término vacíos, inicio válido | NEW | Oficial, energía ausente |
| 4 / H | Nueva, 1.500 kWh | NEW con aviso energético | ENERGY_REVIEW, fuera de oficiales |
| 5 / U | Nueva, 5 kWh, identidad ambigua | PENDING_LINK | Pendiente con motivo, sin atribución arbitraria |
| 6 / L | Misma sesión/lectura de 20 kWh, identidad ahora única | LINKABLE | Vincula sin duplicar, oficial |
| 7 / N repetida | Copia exacta de fila 1 | DUPLICATE | No inserta otra sesión |
| 8 / C | Misma sesión C, cambia 10→12 kWh | MODIFIED | Corrección aprobada, conserva versión previa |
| 9 / X | Nueva, energía negativa y demás campos válidos | INVALID | Error bloqueante, no persiste sesión |

Primera vista previa: nueve filas = cuatro NEW, una PENDING_LINK, una LINKABLE, una DUPLICATE, una MODIFIED y una INVALID; `energyReviewRows=1` y el filtro de umbral devuelve H. Aprobar únicamente la corrección C y confirmar. Esperar siete sesiones persistidas: cinco oficiales, 34,000 kWh conocidos y una sesión oficial sin energía; una ENERGY_REVIEW con 1.500,000 kWh y una PENDING_LINK con 5,000 kWh. Ninguna decisión energética todavía; C conserva la lectura previa de 10 kWh. Tablero, PDF y Excel coinciden con esos filtros.

Reimportar el mismo lote dos veces sin nuevas aprobaciones: siete sesiones y mismos indicadores, revisiones y decisiones; N repetida sigue duplicada, X sigue inválida, L ya no crea vínculo adicional y C no crea otra corrección. No esperar conservar las clasificaciones NEW/MODIFIED/LINKABLE cuando el registro ya quedó aplicado: comprobar el resultado idempotente.

Después aprobar H con justificación: seis oficiales, 1.534,000 kWh conocidos, una sin energía y U aún pendiente. Reimportar dos veces: nada vuelve a revisión ni añade decisiones; H se cuenta como lectura alta del lote pero conserva aprobación. En una prueba independiente rechazar H y reimportar: conservar rechazo y oficiales en cinco/34,000 kWh. Los mensajes no anuncian pérdida de decisiones; los lotes anteriores no se reescriben.

Pruebas de validez I1 complementarias: lectura presente + solo ENERGY_ABOVE_THRESHOLD es válida; lectura ausente nunca es válida; aviso junto a error temporal/formato sigue inválido. Verificar `ParsedSession.isValid()`, clasificación y confirmación de ambas fuentes; los errores reales no se convierten en avisos.

### Suites existentes

- Sesiones hasta 1.000 kWh sin cambios en estado y totales.
- Ranking sin empate en quinto igual que antes.
- Fuente no preferente, pruebas y vinculación siguen funcionando.
