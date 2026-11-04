# ADR 0011: módulo de uptime

**Estado**: aceptada, feature 005.

## Contexto

RF-004 pide mediciones por conector, historial y validación. TRD §10 sugiere tablas y campos que el repositorio real ya no sigue.

## Decisión

- Módulo `modules/uptime`, con las capas de `consumption`, sin dependencias nuevas.
- Prefijo de tabla `upt_`, no los nombres del TRD. Migración `V013`.
- Cabecera `upt_measurement` para la identidad y revisiones `upt_measurement_revision` para los valores. Una corrección espera validación mientras la versión validada sigue vigente; el modelo de una fila con revisiones de solo escritura de Consumo no lo permite.
- Dos ejes de estado, `reading_class` y `review_state`. «Sin medición» se calcula, no se guarda.
- `PeriodType` se limita a tres ventanas móviles y `EXACT_WEEK`. No se adopta `CUSTOM_EXACT`.
- Identidad de conector por UUID de posición, conforme a D06. `upt_connector_alias` traduce etiquetas externas a ese UUID; no crea identidad.
- Importación tipada sobre `shared/importing`, como ADR 0005; no se usa la tabla genérica del TRD.
- Reportes con un solo perfil `OFFICIAL_ONLY`, porque solo presentan datos validados y cantidades.
- El uptime no se persiste. `UptimeFormula` y `UptimeCalculator` son la fuente única de dashboard y reportes.

## Consecuencias

Una fuente nueva exige migración y regla aprobada. El tipo de período vive en la revisión, de modo que confirmar una semana exacta es una corrección.

## Estado de la implementación (2026-11-02)

Captura, validación, tablero, reportes, bloque, pegado, carga de archivo y vinculación operativos. Pendiente:

- T091–T095 y T128–T134 cerradas el 2026-11-02 con la muestra real. Se importa el informe semanal tal como se envía por correo: todas sus hojas (D09, Constitución 1.2.0), semana elegida al cargar (la fila 1 se ignora), horas al minuto más cercano, Activo y Cargando opcionales, equipo por Cargador cuando falta Activo y una sola identidad verificada lo usa, etiqueta de D12 desde Cargador y número de conector leído también tras la palabra «Conector». Sin Cargando, la revisión guarda el uptime informado (`uptime_basis = REPORTED_PERCENT`, V014): no se recalcula y, al agregar, pesa la duración del período y descuenta lo que el porcentaje deja fuera.
- DP-05: 0,01 puntos se mantiene; margen de suma de 1 minuto o 0,02 % en períodos de más de 7 días.
- Llevar a control de cambios del PRD: tolerancia de 1 minuto y 0,02 % en períodos largos, conversión de horas a minutos, preferencia por 7 días en apertura, conteo de pares conector-semana en cobertura y excluidas dentro del denominador.

Las lecturas de indicadores cargan el período y filtran en memoria, con mapeo JDBC por volumen. Medido en `specs/005-uptime-disponibilidad/evidence/performance.md`.

## Cierre técnico 2026-11-04

Se corrigen los tres hallazgos QA de sin atribuir por período, inclusión de ese total en PDF/Excel y desbordamiento del número inicial de una etiqueta. Se conservan los servicios/capas y migraciones V013–V015. Evidencia: specs/005-uptime-disponibilidad/evidence/closure-qa.md. T124 humana permanece pendiente.

Los ajustes posteriores de referencia semanal, horario real desconocido, media del informe y pasteles se separan como Feature-006; no se implementan ni se declaran entregados en 005. Los intervalos declarados del Excel actual no acreditan los límites observados en Enerlink.
