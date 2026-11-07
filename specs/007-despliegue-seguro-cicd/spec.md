# Feature Specification: Preparación de SARC para despliegue seguro y CI/CD

**Feature**: `007-despliegue-seguro-cicd`
**Feature Branch**: ninguna creada; trabajo sin commits sobre `main` (a petición del responsable)
**Created**: 2026-11-06
**Status**: Draft — especificación funcional aclarada (5 decisiones en la sesión 2026-11-06). Plan técnico y tareas generados; sin implementación.
**Baseline**: `main` en `e85c910` (features 001–006 integradas). Esta feature no cambia reglas de negocio, permisos, importaciones, indicadores, historiales ni reportes.
**Input**: Dejar definido cómo construir, verificar, publicar y recuperar SARC de forma segura y repetible para un futuro despliegue en un VPS de Contabo (6 vCPU, 12 GB de RAM, 200 GB SSD, Ubuntu, respaldo automático del proveedor). El despliegue real ocurrirá después, con el servidor contratado y una decisión explícita.

## Clarifications

### Session 2026-11-06

- Q: ¿SARC se publicará en producción con un dominio propio o solo con la dirección IP del VPS? → A: Inicialmente con la IP pública del VPS, sin comprar dominio. HTTPS con certificado reconocido para esa IP, con renovación automática y verificación de que funciona; si la emisión o renovación no puede garantizarse, se informa el bloqueo antes de publicar. Debe poder incorporarse un dominio o subdominio más adelante sin perder datos ni cambiar funcionalidades.
- Q: ¿Dónde se guardarán las copias independientes de la base de datos fuera del VPS, y con qué presupuesto? → A: Se contratará el Auto Backup de Contabo (USD 4 al mes según el precio mostrado). Además, una copia independiente de PostgreSQL se descarga a un computador local autorizado, sin contratar otro proveedor de almacenamiento; esa copia no depende de la cuenta ni del servidor de Contabo. El plan define la frecuencia de descarga acorde con la pérdida de datos aceptable, la protección de las copias, la verificación de restauración y el tratamiento de los archivos guardados en Cloudinary.
- Q: ¿Cuánta pérdida máxima de datos se acepta si hay que recuperar SARC desde la copia independiente local? → A: 24 horas: al menos una descarga diaria verificada al computador local; el Auto Backup del proveedor complementa.
- Q: ¿Por qué medio deben llegar los avisos de fallos al responsable? → A: GitHub y correo: los fallos de verificación y publicación por las notificaciones de GitHub; los del servidor (servicio caído, copia fallida o atrasada, certificado por vencer, disco lleno) por correo. Incluye esas condiciones y una prueba de recepción para el responsable y su suplente.
- Q: ¿Cada cuánto debe ensayarse la restauración de la copia en un entorno aislado, además de tras cambios críticos? → A: Mensual, más un ensayo tras cada cambio crítico (migración de esquema, cambio de computador local o de proveedor).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Saber que una versión es apta antes de publicarla (Priority: P1)

Como responsable técnico, quiero que cada cambio pase por verificaciones automáticas de backend y frontend y que de una versión aprobada quede un conjunto identificable y trazable, para decidir con evidencia si puede llegar a producción.

**Why this priority**: Es la base de todo lo demás: sin una versión verificada e identificable no hay nada que autorizar, publicar ni recuperar.
**Independent Test**: Proponer un cambio que pase y otro que falle una prueba; comprobar que el primero produce una versión candidata identificable y el segundo no, y que el fallo se ve con claridad.

**Acceptance Scenarios**:

1. **Given** un cambio propuesto que cumple todas las verificaciones, **When** terminan las comprobaciones de backend y frontend, **Then** se genera una versión candidata con identificador único, el cambio exacto que la originó y el resultado de cada verificación.
2. **Given** un cambio con una prueba, análisis de calidad o compilación fallidos, **When** terminan las comprobaciones, **Then** se muestra qué verificación falló y por qué, y no se genera ni se presenta ninguna versión como lista para producción.
3. **Given** una versión candidata, **When** una persona consulta su trazabilidad, **Then** puede saber de qué cambio proviene, qué verificaciones pasó, cuándo se generó y qué contenido entregable la compone.
4. **Given** una versión candidata ya generada, **When** se publican cambios posteriores en el repositorio, **Then** la versión candidata original no cambia y sigue siendo identificable.
5. **Given** que el contrato de la API y los tipos que usa la interfaz se desincronizan, **When** corren las verificaciones, **Then** el cambio se rechaza.

---

### User Story 2 - Publicar solo con autorización explícita y poder comprobar el resultado (Priority: P1)

Como responsable del producto, quiero decidir de forma explícita cuándo una versión candidata llega a producción y comprobar que quedó funcionando, para que ningún cambio llegue a los usuarios solo por haberse integrado.

**Why this priority**: Evita publicaciones accidentales y protege los datos reales; es la condición de seguridad del proceso de entrega.
**Independent Test**: Solicitar la publicación de una versión candidata en un entorno de ensayo aislado: debe quedar detenida hasta recibir autorización; tras autorizarla, se ejecutan las comprobaciones previas y posteriores y se registra el resultado.

**Acceptance Scenarios**:

1. **Given** una versión candidata aprobada, **When** se integra en la rama principal, **Then** no se publica en producción hasta que una persona autorizada lo decide de forma explícita y queda registrado quién, cuándo y qué versión.
2. **Given** una solicitud de publicación sin autorización, **When** nadie la aprueba, **Then** no ocurre ningún cambio en producción.
3. **Given** una publicación autorizada, **When** las comprobaciones previas detectan que no hay una copia reciente verificable de la base de datos, que la versión no está aprobada o que el estado del esquema no es el esperado, **Then** la publicación se detiene antes de cambiar nada y explica el motivo.
4. **Given** una publicación autorizada con migraciones de esquema pendientes, **When** se aplican, **Then** se aplican en orden y de forma controlada, sin modificar migraciones ya aplicadas, y el éxito se confirma antes de continuar.
5. **Given** una migración o un paso de publicación que falla, **When** ocurre el fallo, **Then** el proceso se detiene, no se presenta como terminado, se conserva el estado anterior utilizable cuando sea posible y se indica al operador cómo recuperar.
6. **Given** una publicación terminada, **When** se ejecutan las comprobaciones posteriores (disponibilidad, conexión a la base de datos, ingreso de un usuario, funcionamiento de cada módulo y acceso a archivos), **Then** el resultado de cada una queda registrado y, si alguna falla, la publicación se marca como fallida y se actúa según el procedimiento de recuperación.

---

### User Story 3 - Configuración y secretos separados por entorno (Priority: P1)

Como responsable técnico, quiero que desarrollo, pruebas y producción tengan configuraciones y secretos separados y que nada sensible viva en el repositorio, para que un ensayo nunca toque datos reales y una filtración de código no entregue credenciales.

**Why this priority**: Un error de entorno o un secreto expuesto compromete datos reales; es una barrera previa a cualquier publicación.
**Independent Test**: Revisar el repositorio, los entregables y los registros de una ejecución buscando credenciales; ejecutar las pruebas y comprobar que no alcanzan ni modifican la base local de desarrollo ni producción.

**Acceptance Scenarios**:

1. **Given** el repositorio, las versiones candidatas y los registros de las verificaciones, **When** se buscan contraseñas, claves, tokens o certificados reales, **Then** no aparece ninguno.
2. **Given** un entorno de producción nuevo, **When** el operador sigue la documentación, **Then** sabe qué valores de configuración y secretos debe proporcionar, de dónde proceden, quién los custodia y cómo se rotan, sin que ninguno viaje dentro de la versión candidata.
3. **Given** una falta o un valor inválido de configuración obligatoria en producción, **When** la aplicación intenta arrancar, **Then** no arranca y informa qué falta sin mostrar valores sensibles.
4. **Given** las pruebas automáticas y los ensayos de esta feature, **When** se ejecutan, **Then** usan únicamente bases y almacenamientos desechables o de ensayo, nunca la base local de desarrollo ni datos de producción.
5. **Given** un entorno de pruebas o de ensayo, **When** necesita archivos externos, **Then** usa un espacio propio separado del de producción.
6. **Given** el primer arranque de producción con una base vacía, **When** se crea la cuenta administrativa inicial, **Then** su credencial inicial se entrega por un canal protegido, es de un solo uso (cambio obligatorio al ingresar) y no queda guardada en el repositorio ni en los registros.

---

### User Story 4 - Respaldos independientes y recuperación comprobada (Priority: P1)

Como responsable del producto, quiero copias independientes de la base de datos y de los archivos externos, y la evidencia de que se pueden restaurar en un entorno aislado, para recuperar SARC completo sin depender de una sola fuente.

**Why this priority**: SARC conserva históricos que no se pueden reconstruir; sin una restauración probada, el respaldo es una suposición.
**Independent Test**: Tomar una copia de una base de ensayo con datos representativos, restaurarla en un entorno aislado y verificar la integridad y la accesibilidad de los archivos asociados.

**Acceptance Scenarios**:

1. **Given** el servicio en producción, **When** transcurre el intervalo definido, **Then** existe una copia de la base de datos guardada fuera del servidor y fuera del respaldo automático del proveedor, protegida frente a lecturas no autorizadas y con evidencia de su integridad.
2. **Given** los archivos que SARC guarda en un servicio externo (imágenes de equipos, originales de importación y copias exactas de reportes emitidos), **When** se diseña la recuperación, **Then** queda definido cómo se protegen y se recuperan, de modo que un reporte emitido o un original importado siguen disponibles tras una recuperación.
3. **Given** una copia de la base de datos, **When** se restaura en un entorno aislado, **Then** el esquema y los datos coinciden con el origen según las comprobaciones definidas (conteos, historiales, auditoría, referencias a archivos) y el entorno de producción no se ve afectado.
4. **Given** una copia o un respaldo que falla, está atrasado o no se puede leer, **When** se detecta, **Then** se avisa al responsable y la próxima publicación autorizada queda bloqueada hasta contar con una copia reciente verificable.
5. **Given** un fallo tras publicar, **When** una reversión al estado anterior pondría en riesgo datos creados después de la publicación, **Then** el procedimiento indica avanzar con una corrección o restaurar de forma controlada, y deja explícito qué datos podrían perderse antes de decidir.
6. **Given** el procedimiento, **When** se lee, **Then** distingue sin ambigüedad una prueba de restauración en un entorno aislado (rutinaria, sin riesgo para producción) de una restauración real en producción (excepcional, con autorización y registro).

---

### User Story 5 - Servicio expuesto de forma mínima y observable (Priority: P2)

Como responsable técnico, quiero que SARC solo sea accesible por canales cifrados, que la base de datos y los servicios internos no sean alcanzables desde Internet y que existan registros y comprobaciones de salud útiles, para detectar problemas sin exponer datos sensibles.

**Why this priority**: Reduce la superficie de ataque y permite operar con fundamento; depende del servidor real, por lo que se define ahora y se verifica después.
**Independent Test**: Revisar, en un entorno de ensayo equivalente, qué servicios responden desde fuera, que el acceso sin cifrar no entrega la aplicación y qué contienen los registros tras una sesión con errores simulados.

**Acceptance Scenarios**:

1. **Given** el servicio publicado, **When** una persona accede por una dirección sin cifrado, **Then** no se le sirve la aplicación ni se le pide autenticarse en claro; se le dirige al acceso cifrado.
2. **Given** el servidor en producción, **When** se intenta alcanzar la base de datos u otros servicios internos desde una red externa, **Then** el acceso es rechazado; solo son accesibles desde el propio servidor o desde una vía autorizada.
3. **Given** que se necesita acceso administrativo al servidor, **When** se concede, **Then** queda definido quién puede, por qué medio, con qué nivel de privilegio y cómo se revoca.
4. **Given** la aplicación en funcionamiento, **When** se consultan sus comprobaciones de salud, **Then** informan si está viva y lista (incluida su conexión a la base de datos) sin revelar contraseñas, tokens ni datos de usuarios, y los detalles internos solo son visibles para quien está autorizado.
5. **Given** un error en la aplicación, **When** se revisa el registro, **Then** permite diagnosticarlo (momento, módulo, identificador de seguimiento) sin contener contraseñas, tokens, identificadores sensibles de los equipos ni datos personales innecesarios.
6. **Given** que se acumulan registros, **When** se supera el plazo definido de conservación, **Then** se rotan sin afectar la auditoría funcional del sistema, que no se elimina por antigüedad.

---

### User Story 6 - Un operador sin contexto puede preparar, verificar y recuperar (Priority: P2)

Como operador que no participó en esta conversación, quiero procedimientos claros para preparar una publicación, verificarla, identificar un fallo, recuperar el servicio y mantener el entorno, para actuar sin depender de la memoria de quien lo diseñó.

**Why this priority**: Un proceso que solo entiende su autor no es repetible; además deja visibles las verificaciones que aún dependen de recursos reales.
**Independent Test**: Entregar la documentación a una persona ajena y pedirle que, en un entorno de ensayo, prepare una publicación, la verifique, simule un fallo y siga el procedimiento de recuperación, sin ayuda adicional.

**Acceptance Scenarios**:

1. **Given** la documentación operativa, **When** el operador la sigue, **Then** puede preparar una versión candidata, solicitar su publicación, ejecutar las comprobaciones previas y posteriores e interpretar su resultado.
2. **Given** un fallo de publicación, de migración o de servicio, **When** el operador consulta el procedimiento, **Then** identifica el síntoma, el criterio de decisión (corregir hacia adelante o recuperar) y los pasos de recuperación con sus responsables.
3. **Given** actualizaciones del sistema operativo, de dependencias o de certificados, **When** llega el momento, **Then** el procedimiento de mantenimiento indica cómo aplicarlas, cómo verificarlas y cómo recuperarse si algo falla.
4. **Given** la documentación completa, **When** se revisa, **Then** marca qué comprobaciones no pudieron ejecutarse todavía porque dependen del servidor real, del dominio, de credenciales o de servicios externos, y bajo qué condición se cerrarán.

---

### Edge Cases

- La misma versión candidata se solicita publicar dos veces, o dos personas intentan publicar versiones distintas a la vez: solo una publicación está activa; la otra espera o se rechaza con explicación.
- Una verificación automática falla de forma intermitente (infraestructura, red) y no por el código: el fallo se muestra y se distingue de un error del cambio; la versión no se promueve hasta repetir con éxito.
- La versión candidata se aprueba, pero la autorización llega después de que cambió el estado de producción (nuevas migraciones, otra publicación): las comprobaciones previas se repiten y pueden detener la publicación.
- La migración se aplica parcialmente o su validación de esquema falla: la publicación se detiene y no continúa como si hubiera terminado; el operador sabe si el esquema quedó en el estado anterior o intermedio.
- La nueva versión necesita un esquema que la versión anterior no entiende, y se pide volver atrás tras haberse creado datos nuevos: el procedimiento advierte la incompatibilidad y prioriza una corrección hacia adelante o una restauración controlada, nunca una reversión ciega.
- El servicio externo de archivos no está disponible o sus credenciales caducaron: la aplicación sigue funcionando en lo que no depende de archivos, informa que la carga o descarga de archivos no está disponible y la comprobación posterior lo detecta.
- La copia independiente falla, se llena el almacenamiento o el respaldo del proveedor no está disponible: se avisa y se bloquea la siguiente publicación.
- El computador local que recibe la copia independiente está apagado, sin conexión o se pierde o se lo roban: la descarga atrasada se avisa y bloquea la siguiente publicación (FR-043); las copias cifradas en reposo no son legibles sin autorización (FR-039a), y el procedimiento indica cómo designar otro computador y revocar el acceso del anterior.
- El espacio en disco del servidor se agota (registros, copias locales): hay aviso previo y el procedimiento indica cómo liberar espacio sin tocar datos.
- Un certificado está por vencer o no se puede renovar: se avisa con antelación suficiente; si vence, el servicio no cae a un acceso sin cifrar.
- Los certificados para una dirección IP suelen tener vigencia corta, o la IP del servidor cambia (por migración del proveedor): la renovación automática y su comprobación cubren ese ciclo, y un cambio de IP se trata como cambio de dirección de acceso según el procedimiento de mantenimiento (FR-033a).
- Se detecta un secreto expuesto (en un registro, un cambio o un entregable): hay un procedimiento para revocarlo, rotarlo y confirmar que ya no sirve.
- Un cambio de esta feature intenta alterar una migración ya aplicada, un permiso o una regla de negocio: se rechaza en la revisión o por las verificaciones.
- Se ensaya una restauración y el entorno aislado comparte, por error, la base o el espacio de archivos de producción: el procedimiento previene ese uso compartido y la comprobación de aislamiento forma parte del ensayo.
- La cuenta de la persona autorizada para publicar no está disponible durante una urgencia: existe una persona o rol suplente definido, con el mismo registro.

## Requirements *(mandatory)*

### Functional Requirements

**Verificación e integración continua**

- **FR-001**: Todo cambio propuesto en el repositorio MUST ejecutar automáticamente las verificaciones de backend y de frontend, incluidas pruebas, análisis de calidad y compilación, y mostrar su resultado de forma visible para quien lo propuso y para quien lo revisa.
- **FR-002**: Las verificaciones MUST incluir la comprobación de que el contrato público de la API y los tipos que consume la interfaz están sincronizados, y de que el conjunto completo de pruebas de integración usa bases de datos desechables.
- **FR-003**: Una verificación fallida MUST impedir que la versión se genere o se presente como lista para producción, e indicar cuál falló y la causa.
- **FR-004**: El proceso MUST distinguir un fallo del cambio de un fallo de la infraestructura de verificación, y permitir repetir la ejecución sin alterar el resultado de las demás.
- **FR-005**: Las verificaciones MUST incluir una revisión de las dependencias en busca de vulnerabilidades conocidas y una búsqueda de secretos expuestos, con umbrales de severidad definidos para bloquear o solo advertir.

**Versión candidata y trazabilidad**

- **FR-006**: Cada versión que supere las verificaciones MUST recibir un identificador único e inmutable, ligado al cambio exacto del repositorio que la originó.
- **FR-007**: La versión candidata MUST agrupar los entregables de backend y frontend, y conservar de forma consultable los resultados de las verificaciones, la fecha de generación y una suma de comprobación que permita verificar que lo publicado coincide con lo verificado.
- **FR-008**: Los entregables y los registros de la versión candidata MUST NOT contener secretos, credenciales, URLs sensibles ni datos reales.
- **FR-009**: Debe poder determinarse en todo momento qué versión candidata está publicada en producción, cuál estuvo antes y quién autorizó cada publicación.

**Autorización y publicación**

- **FR-010**: Publicar en producción MUST requerir una decisión explícita de una persona autorizada, distinta del simple hecho de redactar, probar o integrar el cambio; integrar o aprobar el cambio MUST NOT publicar por sí solo.
- **FR-011**: La autorización MUST quedar registrada con persona, fecha, hora y versión candidata, y MUST NOT poder reutilizarse para otra versión.
- **FR-012**: Deben definirse las personas o roles autorizados a decidir una publicación y un suplente, y el procedimiento para cambiarlos.
- **FR-013**: Antes de publicar, el proceso MUST comprobar al menos: que la versión está aprobada, que existe una copia reciente verificable de la base de datos, el estado del esquema y las migraciones pendientes, y que la configuración obligatoria está completa; si alguna falla, MUST detenerse sin cambiar producción.
- **FR-014**: Solo debe haber una publicación activa a la vez.
- **FR-015**: Después de publicar, el proceso MUST ejecutar comprobaciones de disponibilidad de la aplicación, conexión a la base de datos, ingreso de un usuario, funcionamiento básico de los módulos (usuarios, inventario, consumo y uptime) y acceso a archivos, y registrar el resultado de cada una.
- **FR-016**: Una publicación solo debe considerarse completada si todas las comprobaciones posteriores obligatorias pasan; en caso contrario MUST marcarse como fallida y activarse el procedimiento de recuperación.

**Base de datos y migraciones**

- **FR-017**: Las migraciones de esquema pendientes MUST aplicarse de forma controlada y ordenada, siempre antes de que la nueva versión atienda usuarios, y su resultado MUST confirmarse antes de continuar.
- **FR-018**: Una migración ya aplicada en cualquier entorno MUST NOT modificarse; los cambios de esquema entran solo como migraciones nuevas, conforme a la constitución (principio III).
- **FR-019**: El fallo de una migración o de una validación de esquema MUST detener la publicación, impedir que continúe como exitosa y dejar claro el estado en que quedó la base.
- **FR-020**: Debe documentarse la compatibilidad entre la versión de aplicación y la del esquema, de modo que se sepa si una versión anterior puede operar con el esquema vigente y cuándo no.
- **FR-021**: La información histórica (auditoría, revisiones, importaciones, reportes emitidos) MUST conservarse íntegra durante toda publicación y recuperación.
- **FR-022**: Ninguna operación destructiva de esquema o de datos MUST poder ejecutarse en producción como efecto de publicar o de probar.

**Entornos, configuración y secretos**

- **FR-023**: Deben existir tres entornos claramente separados —desarrollo, pruebas y producción— cada uno con su configuración, su base de datos y su espacio de archivos propios, sin posibilidad de apuntar uno al de otro por omisión.
- **FR-024**: Los valores de configuración y los secretos de producción MUST proporcionarse fuera del repositorio y de los entregables, mediante un mecanismo definido de custodia, y MUST documentarse (nombre, propósito, responsable, rotación) sin incluir su valor.
- **FR-025**: La aplicación MUST negarse a arrancar en producción si falta configuración obligatoria o es inválida, sin mostrar valores sensibles.
- **FR-026**: El entorno de pruebas y los ensayos MUST NOT leer ni modificar la base local de desarrollo ni datos de producción, y MUST usar datos sintéticos o anonimizados.
- **FR-027**: El acceso a los servicios externos que SARC necesita (almacenamiento de archivos, certificados, dominio) MUST definirse por entorno, con credenciales distintas para producción y para pruebas.
- **FR-028**: La credencial inicial de la cuenta administrativa de un entorno nuevo MUST entregarse por un canal protegido, MUST exigir su cambio en el primer ingreso y MUST retirarse de la configuración una vez usada.
- **FR-029**: Debe definirse cómo se rota o revoca un secreto y cómo se confirma que el anterior dejó de funcionar, incluido el caso de un secreto expuesto.

**Seguridad de acceso y exposición**

- **FR-030**: Toda comunicación de los usuarios con SARC en producción MUST ir cifrada; el acceso sin cifrar MUST NOT servir la aplicación ni pedir credenciales.
- **FR-031**: La base de datos y los servicios internos MUST NOT ser accesibles desde redes externas sin autorización explícita; el conjunto de servicios expuestos al exterior MUST estar enumerado y justificado.
- **FR-032**: Deben definirse las condiciones de acceso administrativo al servidor: quién, por qué medio, con qué privilegios mínimos, cómo se registra y cómo se revoca.
- **FR-033**: En su primera etapa, producción se publica con la dirección IP pública del servidor, sin dominio propio. El acceso cifrado MUST usar un certificado reconocido por los navegadores para esa IP (sin alertas de seguridad), con renovación automática antes de vencer y una comprobación de que el certificado vigente funciona. Si la emisión o la renovación no pueden garantizarse, MUST informarse el bloqueo antes de publicar y la publicación MUST NOT continuar con un certificado no reconocido o sin cifrado; si una renovación falla con el servicio ya publicado, debe avisarse con antelación al vencimiento.
- **FR-033a**: La preparación MUST permitir incorporar más adelante un dominio o subdominio sin perder datos, sin cambiar funcionalidades y sin invalidar las referencias a archivos guardadas; el cambio de dirección de acceso debe estar descrito en el procedimiento de mantenimiento.
- **FR-034**: La documentación de API interactiva y los puntos de supervisión que revelen información interna MUST NOT estar accesibles al público en producción.

**Registros y salud**

- **FR-035**: La aplicación MUST ofrecer comprobaciones de vida y de disposición (incluida la conexión a la base de datos) que no revelen contraseñas, tokens ni datos de usuarios, y cuyos detalles solo vea quien está autorizado.
- **FR-036**: Los registros técnicos MUST permitir diagnosticar fallos (momento, nivel, módulo, identificador de seguimiento) sin incluir contraseñas, tokens, identificadores sensibles de equipos ni datos personales innecesarios.
- **FR-037**: Los registros técnicos MUST conservarse por un plazo definido y rotarse; la auditoría funcional de SARC MUST NOT eliminarse por antigüedad.
- **FR-038**: El responsable y su suplente MUST recibir aviso ante: verificación fallida, migración o publicación fallida (por las notificaciones de GitHub), y servicio caído, copia fallida o atrasada, certificado próximo a vencer o no renovado y espacio en disco bajo (por correo electrónico). Los avisos MUST NOT contener secretos ni datos personales innecesarios.
- **FR-038a**: Antes de la primera publicación y tras cambiar al responsable, al suplente o el canal, MUST comprobarse la recepción de cada tipo de aviso (GitHub y correo) por los destinatarios designados, con fecha y resultado. Un aviso no recibido bloquea la publicación. Para la primera instalación y su verificación se permite temporalmente un único responsable si el producto aprobó y registró la excepción. En publicaciones normales se exigen las dos confirmaciones y revisión independiente tras incorporar al suplente.

**Respaldos y recuperación**

- **FR-039**: La estrategia de respaldo MUST combinar dos fuentes: el respaldo automático contratado al proveedor del VPS (Auto Backup de Contabo, presupuesto de referencia USD 4 al mes) y una copia independiente de PostgreSQL descargada a un computador local autorizado de la organización, que no dependa de la cuenta ni del servidor del proveedor. No se contrata otro proveedor de almacenamiento. Ninguna de las dos fuentes sustituye a la otra, y el disco del propio VPS no cuenta como copia independiente.
- **FR-039a**: El computador local que guarda la copia independiente MUST estar designado y autorizado por el responsable del producto, con acceso restringido a personas autorizadas y las copias cifradas en reposo; la descarga MUST viajar cifrada y con credenciales de solo lectura sobre las copias, y MUST ocurrir al menos una vez al día con verificación de integridad, conforme a la pérdida máxima de datos aceptada (FR-040). Si el computador no está disponible y la copia se atrasa, aplica FR-043.
- **FR-040**: Las copias de la base de datos MUST estar protegidas frente a lecturas no autorizadas, acompañadas de evidencia de integridad, y conservarse según una política definida de frecuencia y retención, con un punto objetivo de recuperación (pérdida máxima de datos) de 24 horas respecto de la copia independiente y un tiempo objetivo de recuperación documentado, ambos como objetivos y no como garantías. Los datos registrados después de la última copia (por ejemplo, importaciones) deben poder identificarse tras una recuperación para volver a cargarlos desde sus originales cuando existan.
- **FR-041**: Los archivos que SARC guarda en el servicio externo (imágenes de equipos, originales de importación de consumo, inventario y uptime, y copias exactas de reportes emitidos) MUST contar con una estrategia de protección y recuperación que garantice que las referencias guardadas en la base de datos siguen resolviendo tras una recuperación.
- **FR-042**: Debe comprobarse al menos una vez al mes, y además tras cada cambio crítico (una publicación con migraciones de esquema, el cambio del computador local de copias o del proveedor), que una copia reciente de la base de datos y los archivos asociados se restauran en un entorno aislado, con evidencia de integridad (esquema, conteos, historiales, auditoría y acceso a archivos) y sin tocar producción.
- **FR-043**: Una copia fallida, atrasada o ilegible MUST avisarse y MUST bloquear la siguiente publicación hasta contar con una copia reciente verificable.
- **FR-044**: Debe definirse cuándo recuperar una versión anterior y cuándo avanzar con una corrección, considerando que una reversión puede afectar datos nuevos, y exigir que esa pérdida potencial se declare antes de decidir.
- **FR-045**: El procedimiento MUST distinguir y nombrar por separado la prueba de restauración en entorno aislado de la restauración real en producción; esta última MUST requerir autorización explícita y registro.
- **FR-046**: Las pruebas de restauración MUST usar credenciales y espacios de archivos distintos de los de producción, de modo que un ensayo no pueda alterar archivos reales.

**Documentación y operación**

- **FR-047**: Debe existir documentación operativa comprensible para quien no participó en esta conversación, que cubra: preparar una versión, solicitar y autorizar una publicación, ejecutar las comprobaciones previas y posteriores, identificar un fallo, recuperar el servicio, rotar secretos, mantener el entorno y probar restauraciones.
- **FR-048**: La documentación MUST listar las verificaciones que aún dependen del servidor real, el dominio, las credenciales o servicios externos, su responsable y la condición para cerrarlas.
- **FR-049**: Debe definirse cómo se actualiza y se mantiene el entorno (sistema operativo, dependencias, certificados), con verificación posterior y vía de recuperación.
- **FR-050**: Esta feature MUST NOT modificar reglas de negocio, permisos, importaciones, indicadores, historiales ni reportes; cualquier cambio de comportamiento necesario para operar (por ejemplo, el aviso de un servicio de archivos no disponible) debe describirse y ser aprobado como parte de esta especificación.

### Key Entities

- **Versión candidata**: conjunto verificado y entregable de SARC, con identificador único inmutable, cambio de origen, resultados de verificación, fecha, contenido y suma de comprobación. Relaciona el repositorio con lo publicado.
- **Resultado de verificación**: resultado de cada comprobación automática de una versión (aprobada, fallida, con causa), conservado junto a ella.
- **Autorización de publicación**: decisión explícita de una persona autorizada sobre una versión concreta: quién, cuándo y para qué versión; no reutilizable.
- **Registro de publicación**: historial de cada publicación con versión, autorización, comprobaciones previas y posteriores, migraciones aplicadas, resultado final y, si falló, la recuperación realizada.
- **Entorno**: contexto de ejecución separado (desarrollo, pruebas, producción) con configuración, base de datos y espacio de archivos propios.
- **Valor de configuración / secreto**: parámetro necesario para ejecutar un entorno, con nombre, propósito, responsable, rotación y custodia; su valor nunca forma parte del repositorio ni de la versión.
- **Copia de base de datos**: respaldo independiente con fecha, evidencia de integridad, ubicación (computador local autorizado, fuera del servidor y de la cuenta del proveedor), política de retención y protección de acceso. Se distingue del respaldo automático del proveedor, que es complementario.
- **Copia de archivos externos**: protección y recuperación de las imágenes, originales de importación y copias de reportes emitidos, ligadas a sus referencias en la base de datos.
- **Prueba de restauración**: ensayo en un entorno aislado con fecha, copia usada, comprobaciones de integridad y resultado; distinta de una restauración real.
- **Guía de operación**: documentación de procedimientos con responsables y lista de verificaciones pendientes de recursos reales.

## Estado de partida *(verificado en el repositorio el 2026-11-06)*

Resuelto o parcialmente resuelto:

- Ya existe una automatización de GitHub (`.github/workflows/ci.yml`) que se ejecuta al integrar en `main` y en cada solicitud de cambios: en backend ejecuta la verificación completa (pruebas, reglas de arquitectura, integración con PostgreSQL 18 desechable y contrato de la API) y en frontend instala, comprueba que los tipos generados siguen el contrato, y ejecuta lint, formato, pruebas y compilación.
- La aplicación ya distingue un perfil de producción: la base de datos y sus credenciales llegan por variables de entorno, la documentación interactiva de la API y su esquema están desactivados, y se reconoce el reenvío de cabeceras tras un proxy. Los detalles de las comprobaciones de salud están ocultos y los demás puntos de supervisión exigen el rol administrativo.
- Los secretos locales viven en un archivo excluido del repositorio con una plantilla de ejemplo sin valores reales; las pruebas de integración usan bases desechables.
- El esquema se gestiona con 16 migraciones numeradas (V001–V016) cuya inmutabilidad ya exige la constitución.
- Los archivos (imágenes de equipos, originales de importación de consumo, inventario y uptime, y copias exactas de reportes emitidos) se guardan en un servicio externo con entrega autenticada; la base conserva solo sus referencias, por lo que una restauración de la base sin esos archivos dejaría referencias huérfanas.
- El TRD (secciones 17 y 18) ya fija la dirección técnica: servicio de aplicación y archivos estáticos servidos directamente en el VPS, base de datos en contenedor local, copia física diaria más archivado continuo con retención de 30 copias diarias y 12 mensuales, objetivos de pérdida máxima de 1 hora y de recuperación de 4 horas, aprobación manual de producción y retiro de la dependencia del respaldo pagado del proveedor.

Por definir (objeto de esta feature):

- No hay proceso que genere una versión candidata ni la identifique, ni suma de comprobación, ni conservación de resultados; la automatización actual solo verifica.
- No hay flujo de autorización ni de publicación a producción, ni comprobaciones previas y posteriores, ni registro de publicaciones.
- No hay análisis de vulnerabilidades de dependencias ni búsqueda de secretos en la automatización; el TRD también prevé una prueba de humo de navegador que aún no existe. Esta feature no la automatiza: la sustituyen las comprobaciones posteriores a nivel de API (FR-015) y una comprobación manual en navegador en la primera publicación real; su automatización queda para una feature posterior.
- No existe definición de entornos de pruebas y producción más allá del perfil de la aplicación, ni mecanismo documentado de custodia de secretos.
- No hay política implementada de copias independientes, retención, estrategia para los archivos externos, ni ensayo de restauración.
- No hay documento operativo, definición de accesos administrativos ni de exposición de servicios, ni política de registros y avisos.
- La cuenta administrativa inicial depende hoy de variables de entorno; su manejo seguro en producción debe definirse.

Diferencias entre fuentes que deben conciliarse en el plan (no se resuelven aquí en silencio):

- **Tamaño del servidor**: el TRD toma como referencia 4 vCPU y 8 GiB; esta solicitud considera 6 vCPU y 12 GB. Se toma el nuevo valor como referencia vigente, sin garantía de rendimiento, y el plan debe registrar la adaptación del TRD con su justificación.
- **Respaldo del proveedor y almacenamiento externo**: el TRD pide no depender del respaldo pagado del proveedor y guardar las copias en un almacenamiento externo privado aún no elegido. Aclarado el 2026-11-06: se contrata el Auto Backup de Contabo como complemento y la copia independiente se guarda en un computador local autorizado, sin otro proveedor (FR-039, FR-039a). La pérdida máxima aceptada pasa de 1 a 24 horas (FR-040). El plan debe registrar esta adaptación del TRD y verificar que el objetivo de recuperación es alcanzable restaurando desde el computador local.
- **Documentación interactiva de la API**: el TRD menciona comprobarla como administrador tras publicar, pero la configuración de producción vigente la desactiva (FR-034). La comprobación posterior no debe depender de ella.
- **Comprobaciones de salud**: la configuración vigente permite consultar las de vida y disposición sin autenticación, mientras el TRD pide red restringida o autorización fuerte; el plan decide el alcance de exposición.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los cambios propuestos ejecutan las verificaciones de backend y frontend, y el resultado de cada una es visible para el revisor sin buscarlo en registros técnicos.
- **SC-002**: En el 100 % de los casos de ensayo con una verificación fallida (prueba, calidad, compilación, contrato desincronizado, vulnerabilidad o secreto expuesto) no se genera ninguna versión candidata.
- **SC-003**: Toda versión candidata se puede relacionar con su cambio de origen y con los resultados de todas sus verificaciones en menos de 2 minutos, y la suma de comprobación de lo publicado coincide con la de lo verificado.
- **SC-004**: El 0 % de las integraciones de cambios producen una publicación en producción sin autorización explícita registrada; el 100 % de las publicaciones tienen una autorización con persona, fecha y versión.
- **SC-005**: En una búsqueda de secretos sobre el repositorio, los entregables y los registros de verificación y publicación, se encuentran 0 credenciales reales.
- **SC-006**: Ejecutar la suite de pruebas y los ensayos de esta feature deja la base local de desarrollo y cualquier dato de producción sin cambios (0 filas modificadas, comprobado antes y después).
- **SC-007**: Desde una red externa, el único acceso a servicios del servidor es el cifrado de la aplicación; el 100 % de los servicios internos (incluida la base de datos) rechazan conexiones externas.
- **SC-008**: Una publicación de ensayo con una migración deliberadamente fallida se detiene en el 100 % de los casos, no se presenta como completada y deja al operador con un estado y una instrucción de recuperación inequívocos.
- **SC-009**: Una copia de la base de datos de ensayo se restaura en un entorno aislado y supera el 100 % de las comprobaciones de integridad definidas (esquema, conteos por módulo, historiales, auditoría y referencias a archivos), sin afectar a producción.
- **SC-010**: En la restauración de ensayo, el 100 % de las referencias a archivos de una muestra de cada tipo (imagen, original de importación y reporte emitido) resuelven a un archivo recuperable.
- **SC-011**: Las comprobaciones posteriores a una publicación de ensayo cubren disponibilidad, base de datos, ingreso, los cuatro módulos y acceso a archivos, y devuelven un veredicto único en menos de 10 minutos.
- **SC-012**: Una persona que no participó en el diseño, usando solo la documentación, completa en un entorno de ensayo —preparar, publicar, verificar, simular un fallo y recuperar— sin consultar a nadie, en una sola jornada de trabajo.
- **SC-013**: Tras la restauración de ensayo se documentan el tiempo de recuperación y el punto de datos recuperado, y se comparan con los objetivos definidos.
- **SC-014**: El 100 % de las verificaciones que dependen de recursos aún inexistentes (servidor, dominio, credenciales, servicios externos) están listadas con responsable y condición de cierre.
- **SC-015**: Las suites de pruebas existentes de las features 001–006 y los contratos de la API siguen pasando sin modificación de comportamiento (0 regresiones).
- **SC-016**: Al acceder por la IP pública, los navegadores actuales muestran la conexión como segura sin advertencias; en el 100 % de los ensayos sin certificado emitible, la publicación no se inicia y el bloqueo queda informado.
- **SC-017**: En la prueba de recepción, cada destinatario requerido por FR-038a recibe el 100 % de los tipos de aviso definidos (GitHub y correo) en menos de 15 minutos desde la condición simulada.
- **SC-018**: En producción, nunca pasan más de 31 días sin un ensayo de restauración registrado con resultado aprobado, ni se cierra un cambio crítico sin su ensayo posterior.

## Assumptions

- La publicación real, la contratación del servidor, la creación de cuentas, certificados y secretos reales y cualquier restauración sobre producción quedan fuera de esta feature y requieren una decisión posterior explícita del responsable del producto.
- La feature entrega una definición verificable (requisitos, procedimientos, criterios y, en fases posteriores, la automatización necesaria); el despliegue real y las comprobaciones que dependen del servidor real se cerrarán cuando exista el servidor.
- La pérdida máxima de datos aceptada es de 24 horas (aclarado el 2026-11-06), en lugar de la hora que propone el TRD. Del TRD se toman como objetivos de partida la recuperación en 4 horas, la retención de 30 copias diarias y 12 mensuales y 30 días de registros técnicos, hasta que se ratifiquen o ajusten con evidencia de un ensayo; son metas, no garantías.
- Para la capacidad inicial se toma como referencia la carga del TRD (30 usuarios simultáneos; 250.000 registros importados al mes), sin garantía hasta medirla en el servidor real.
- Mientras exista un solo servidor, el entorno de pruebas es efímero y desechable (en la automatización o en un servidor de ensayo temporal) con datos sintéticos; no se asume un entorno de preproducción permanente.
- El responsable del producto designa a la persona autorizada para publicar y a su suplente; con un equipo reducido, quien decide la publicación puede ser quien la solicita, pero siempre mediante una decisión explícita y registrada.
- La cuenta del servicio externo de archivos es compartida, según las notas del proyecto, con otro producto del mismo propietario: producción y ensayos deben usar espacios claramente separados entre sí y de ese otro producto, y ningún ensayo debe borrar ni alterar archivos que no sean propios.
- Los archivos que ya existan en el servicio externo durante el desarrollo pueden quedar huérfanos respecto de la base local; no se migran a producción salvo decisión expresa.
- La automatización existente se reutiliza y se amplía; no se duplican verificaciones que ya funcionan.
- La proporcionalidad es un criterio de diseño: se prefieren pocas piezas y procedimientos simples, y no se añaden servicios que el tamaño actual de SARC no necesite.

### Dependencias

- Servidor VPS contratado y accesible (6 vCPU, 12 GB, 200 GB, Ubuntu) con su respaldo automático del proveedor.
- Dirección IP pública fija del servidor y una entidad emisora que otorgue y renueve automáticamente certificados reconocidos para esa IP (ver FR-033); un dominio o subdominio queda como incorporación posterior (FR-033a).
- Auto Backup de Contabo contratado y un computador local autorizado, con espacio y conexión suficientes, para la copia independiente (ver FR-039 y FR-039a).
- Cuenta y credenciales del servicio externo de archivos para producción y para ensayos.
- Repositorio en GitHub con la capacidad de exigir verificaciones y una aprobación manual antes de publicar.
- Persona autorizada para publicar y su suplente.
- Servicio externo de supervisión que detecte la caída completa del servidor y avise por correo (en el plan: Healthchecks.io, plan gratuito), con una cuenta a nombre de la organización.

### Fuera de alcance

- Cambiar reglas de negocio, permisos, importaciones, indicadores, historiales o reportes.
- Contratar, configurar o conectarse al servidor; desplegar; ejecutar migraciones sobre datos reales; modificar la base local de desarrollo.
- Crear secretos, certificados, cuentas o permisos reales.
- Definir la solución técnica detallada (archivos concretos de automatización, comandos, puertos, configuración de infraestructura): corresponde al plan técnico posterior.
- Alta disponibilidad, balanceo de carga, varios servidores o herramientas de orquestación.

### Pendientes de aclaración para `speckit-clarify`

1. ~~**Dominio o IP** (FR-033)~~: resuelto el 2026-11-06 (ver Clarifications): IP pública con certificado reconocido y renovación automática; dominio posible más adelante.
2. ~~**Almacenamiento externo de copias** (FR-039)~~: resuelto el 2026-11-06 (ver Clarifications): Auto Backup de Contabo más copia independiente en un computador local autorizado.

### Decisiones operativas pendientes (etapa E0 del plan)

No son ambigüedades funcionales sino decisiones del responsable del producto que deben registrarse por escrito antes de preparar el servidor:

1. **Visibilidad del repositorio**: hoy es público; decidir si pasa a privado o se mantiene público con las medidas del plan (R11).
2. **Cuenta del servicio de supervisión**: titular, correo de la organización y aceptación de sus condiciones de uso comercial.
3. **Backup de Cloudinary**: alcance y cuota disponibles en la cuenta compartida con el otro producto, y separación de carpetas.
4. **Rol de la cuenta de verificación**: el de menor privilegio que pueda leer los cuatro módulos; nunca ADMINISTRADOR.
5. **Computador local**: computador designado, su custodio y el sustituto si queda fuera de servicio.
6. **Suplente**: persona designada para aprobar publicaciones y recibir avisos.
