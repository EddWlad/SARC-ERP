# Control de cambios del PRD SARC

## CC-PRD-009-20261113 — Consumo: fecha de la fuente, acciones por sesión y vistas estables

**Estado:** precisiones especificadas en `specs/009-integridad-temporal-consumo/spec.md` (aclaraciones del 2026-11-11 y del 2026-11-13). La atribución por fecha de la fuente se publicó como hotfix el 2026-11-14 (`2026.11.14-41-793e92f`, V019). Las acciones por sesión (US3) y la estabilidad visual (US4) están en implementación. El PDF maestro aprobado permanece intacto.

**Base:** PRD v1.1 §3.2, §7.1–7.3 y §11 (RF-003, en especial RF-003.10, atribución por inicio), TRD 03.3 (zona de operación) y Feature 002 (importación y revisión de sesiones).

**Decisión A (2026-11-11):** cada sesión se atribuye a la fecha de inicio que corresponde a su fuente, también en el consolidado. Enerlink cuenta en la fecha que declara su archivo (`INICIO (UTC±hh:mm)`) y Wallbox conserva la fecha de Ecuador. Un filtro de plataforma cambia el conjunto, no la fecha. El rango inicial y los accesos rápidos siguen el día operativo de Ecuador (D13). Esto precisa RF-003.10 sin cambiar la regla de «se cuenta el día en que inicia».

**Alcance de las acciones de sesión (US3):** ver, editar y dar de baja lógica una sesión, con motivo obligatorio, historial, control de versión y los mismos permisos de edición de Consumo. La baja excluye la sesión de los indicadores oficiales, no se borra y no reaparece al reimportar. Amplía RF-003 (antes solo había marcado de prueba, aprobación de fuente y revisión energética).

**Aclaraciones del 2026-11-13 (después del hotfix):**

- US2 (auditoría y corrección del histórico) queda acreditada por H006–H007: se auditaron los 24 lotes Enerlink de producción contra sus originales y se aplicó la corrección con copia previa y aprobación del responsable. No se construye un subsistema genérico de propuestas y constancias.
- FR-006a («Fecha operativa heredada») se retira: después de V019 todas las sesiones tienen su desplazamiento verificado, así que no queda ninguna excepción histórica.

**Impacto documental:** al consolidar el PRD, RF-003.10 debe citar la Decisión A, y RF-003 debe incorporar las acciones de ver, editar y dar de baja. La Feature 002 conserva su flujo de importación, con la protección de bajas y la revisión explícita de las discrepancias de zona.

## CC-PRD-008-20261110 — Intermitencias KIA: precisiones propuestas

**Estado:** precisiones funcionales indicadas por el responsable del producto el 2026-11-10 y especificadas en `specs/008-intermitencias-kia/spec.md`. El cálculo, la cobertura y la visibilidad de la disponibilidad se aprobaron explícitamente el mismo día, junto con las aclaraciones 4 a 15. Plan técnico, tareas y análisis de consistencia elaborados el 2026-11-10. Implementadas y verificadas con pruebas las historias US1 a US6 (captura, importación, revisión, dashboard, informes y disponibilidad estimada de prueba), con la matriz de seguridad, la regresión de Consumo y Uptime, el rendimiento y la revisión en navegador hechas el 2026-11-13. Falta para cerrar: ensayar V018 sobre una copia restaurada de producción (T006), restaurar con los originales de Cloudinary (T127), medir con una persona los tiempos de SC-001, SC-008 y SC-010, y publicar con el flujo de la Feature 007 cuando el responsable lo autorice (T131). El análisis y la revisión del 2026-11-13 agregaron las precisiones 16 a 21, que se anotan abajo y están ratificadas. El PDF maestro aprobado permanece intacto.

**Base:** PRD v1.1 §9 (RF-005) y §11.1, módulo de Consumo y plantillas operativas y gerenciales de intermitencias.

**Precisiones solicitadas:** adaptar la experiencia de Consumo a la importación y registro manual de ventanas de puntos de carga KIA del inventario; permitir más de cinco ventanas por equipo y día; calcular la duración a partir de cada ventana; ofrecer validación y revisión de datos, dashboard y reportes gerenciales con gráficos de pastel y de tendencia. El tiempo acumulado fuera de línea es la medida principal del ranking; la cantidad de ventanas es secundaria. Se solicitan las mismas autorizaciones que Consumo.

**Aclaraciones del responsable:** ranking Top 10 por duración acumulada, con número de ventanas como dato secundario; permitir corregir datos inválidos en la vista previa; incluir los gráficos de pastel necesarios para una lectura gerencial clara. Solicita calcular disponibilidad siguiendo Enerlink y mostrarla de manera experimental únicamente a Analistas, en dashboard e informes, hasta nueva orden. Los demás indicadores conservan las autorizaciones de Consumo.

**Por resolver en la especificación:** definir una fórmula respaldada por los datos realmente capturados, denominador, cobertura, tratamiento de días sin registro y de estados que no se observan; no presentar como disponibilidad medida por Enerlink un cálculo estimado. Precisar el contenido de cada pastel a partir de datos fiables. Aplicar la restricción de disponibilidad también a respuestas de consulta y exportaciones, incluyendo usuarios con múltiples roles, de acuerdo con la decisión explícita del responsable. Registrar cualquier aclaración adicional sin sobrescribir el PDF.

**Comparación con el PRD (2026-11-10, `specs/008-intermitencias-kia/spec.md`):**

- *Conforme a RF-005, sin cambio:*
  - importación por pegado, CSV o XLSX;
  - una ventana por cada par Inicio/Fin, sin límite de cinco;
  - vinculación por código de punto KIA;
  - evento abierto con su duración provisional aparte;
  - reparto del tiempo entre días y quincenas;
  - detección de duplicados y solapamientos;
  - exclusión de datos de prueba;
  - recurrencia desde 3 días;
  - Top 10 por duración total;
  - PDF y Excel con las mismas reglas.
- *Aclaraciones dentro del PRD:*
  - la corrección de datos inválidos en la vista previa amplía §3.1, que solo pide clasificar y confirmar, sin cambiar sus reglas;
  - Total inactivo del archivo se usa solo para comparar, nunca como duración;
  - un fin anterior al inicio nunca se interpreta solo como cruce de medianoche;
  - pasteles elegidos: tiempo por franja de duración, concentración entre el Top 10 y el resto, y tiempo de recurrentes frente a no recurrentes;
  - empates en el décimo puesto incluidos por analogía con D14;
  - período inicial: la quincena en curso.
- *Diferencia con la plantilla gerencial:* su Top 5 ordenado por ventanas y sus horas en decimales no se adoptan, porque contradicen RF-005.10 y CA-005-01.
- *Ampliación del PRD:* disponibilidad estimada de prueba con la fórmula de Enerlink, (tiempo evaluado − fuera de línea confirmado) / tiempo evaluado. Se calcula solo sobre días acreditados como revisados, con cobertura visible y rótulo «estimada · prueba», y queda separada de RF-004.
- *Decisiones aprobadas explícitamente por el responsable del producto el 2026-11-10:*
  1. **Visibilidad:** la disponibilidad estimada la ven quienes tengan el rol Analista o el rol Administrador. Esto precisa la indicación inicial «únicamente a Analistas» y conserva el acceso total del Administrador del PRD §4. Gerente, Supervisor y Asistente, solos o combinados, no la reciben en dashboard, consultas ni exportaciones hasta nueva orden.
  2. **Denominador:** el tiempo evaluado es 1.440 minutos (24 h) por día cubierto, como la medición Enerlink de CA-004-01. El horario de atención no reduce el tiempo evaluado.
  3. **Cobertura:** al cerrar su turno, un Analista o Administrador marca cada día terminado como «revisado» para toda la red KIA. Los puntos en alcance sin ventanas ese día cuentan con 0 minutos fuera de línea. Los días sin marca quedan «sin datos», no suman tiempo evaluado y nunca se presentan como 100 %.

  Estas decisiones quedan integradas en `specs/008-intermitencias-kia/spec.md` (Clarifications, FR-006, FR-043 y FR-044). El PDF maestro permanece intacto.

- *Aclaraciones posteriores aprobadas por el responsable del producto el 2026-11-10,* dentro del alcance de RF-005 y sin cambiarlo:
  4. **Ventanas largas:** sin duración máxima. Toda ventana cerrada de 240 minutos o más queda «Por revisar» hasta que se confirme que es real; también al cerrar un evento abierto (FR-012 y FR-021a). Una caída de varios días puede registrarse con su fin completo en la captura o en la vista previa (FR-026).
  5. **Confirmación:** cualquier Administrador o Analista, incluso quien registró la ventana, con justificación obligatoria y sin segundo revisor (FR-021a).
  6. **Instantes futuros:** se rechazan los inicios y fines posteriores al minuto en curso del registro, la validación, la confirmación o el cierre (FR-021b).
  7. **Cobertura con pendientes de vincular:** una fila sin punto conocido deja sin cubrir para toda la red los días que toca, hasta resolverla (FR-045).
  8. **Pastel por duración:** franjas [0, 5), [5, 15), [15, 60), [60, 240) y 240 minutos o más. La franja se fija por la duración total de la ventana y la ventana solo aporta su porción dentro del período (FR-036).
  9. **Presentación de la disponibilidad:** sección propia «Disponibilidad estimada (prueba)», con KPI ponderado, cobertura, tabla de menor a mayor y exclusiones; en PDF y Excel, como apartado separado (FR-046a).

- *Aclaraciones aprobadas por el responsable del producto el 2026-11-10 tras el análisis de consistencia,* dentro del alcance de RF-005 y sin cambiarlo:
  10. **Ventanas por día:** en la barra diaria, una ventana cuenta en cada día en que tiene tiempo confirmado. Una ventana que cruza la medianoche cuenta en ambos días, y el gráfico avisa que la suma de las barras puede superar el total de ventanas (FR-035).
  11. **Punto de prueba:** marcar un punto como prueba es una operación sobre un rango de fechas, reversible sobre el mismo rango y con justificación; no es una marca permanente del punto. Alcanza a las ventanas confirmadas y a las por revisar, que no necesitan confirmarse antes para que nunca computen en lo oficial (FR-016).
  12. **Denominador de la cobertura:** los días en alcance son los días del rango ya terminados hasta el corte; hoy y los días futuros no cuentan (FR-046).
  13. **Porcentaje de días en el Excel:** días afectados sobre los días calendario del rango hasta el día del corte, incluido hoy; en un período cerrado, todos sus días (FR-039).
  14. **Prueba siempre cerrada:** en la vista previa no puede marcarse como prueba una ventana sin fin registrada como abierta; se rechaza con su motivo (FR-016).
  15. **No solapamiento en todo estado:** ninguna ventana no anulada de un punto puede solaparse con otra, sea Confirmada, Abierta, Por revisar o Prueba. Una ventana abierta ocupa desde su inicio hasta que se cierra; lo registrado después de su inicio se rechaza en la captura o queda en conflicto en la importación, hasta cerrarla (FR-014).

- *Precisiones de implementación registradas el 2026-11-13 (análisis de consistencia) y ratificadas ese mismo día por el responsable del producto:*
  16. **Repartos en el PDF:** en pantalla los tres repartos de FR-036 son pasteles (dona). En el PDF se dibujan como barras de participación con la misma leyenda, valores y porcentajes, porque el generador corporativo no dibuja gráficos circulares y el PDF de Consumo tampoco los usa. Si se prefieren pasteles en el PDF, hay que ampliar el generador compartido (FR-036, contracts/reports.md §6).
  17. **Tope de informes:** un informe de intermitencias se rechaza con 422 `REPORT_TOO_LARGE` cuando tendría más de 50.000 ventanas confirmadas con tiempo en el rango, como ya ocurre en Consumo e Inventario con sus propios topes. El dashboard no tiene este tope (FR-041a). Una prueba unitaria cubre el límite exacto.
  18. **Alcance impreso de los informes:** el encabezado, que también se archiva, nombra con su nombre actual y su Código asignado solo a los puntos elegidos que sean puntos KIA en alcance y sean 5 o menos; con más de 5 solo los cuenta. Un equipo fuera de alcance o inexistente se muestra «no disponible», sin nombre ni código, para que un filtro manipulado no revele datos de otros equipos (FR-038).
  19. **Registros por revisar en los informes:** el PDF y el Excel incluyen el total de registros por revisar de FR-033 (ventanas por revisar + filas en conflicto + filas por vincular) y los mismos rótulos del dashboard (FR-038).
  20. **Alcance de la disponibilidad en el tiempo:** un punto solo se evalúa desde el primer día completo en que ya existía (el día de su alta si se creó a las 00:00 exactas y, si no, el siguiente). Antes de su alta no cuenta, ni como 100 %, y no pesa en el agregado. El estado actual del punto (inactivo, desconectado, eliminado) se sigue evaluando al consultar. Elegida por el responsable entre tres opciones; descarta congelar el alcance al marcar cada día (FR-045).
  21. **Marcar días históricos:** un editor puede marcar como revisado cualquier día ya terminado, de cualquier fecha pasada. El panel muestra por defecto los últimos 31 días y ofrece un selector de mes para el histórico, como el de octubre de 2026 (FR-044).

  Además, la tabla de hallazgos de la spec ya no incluye nombres ni Códigos asignados reales de los puntos, porque el repositorio es público.

## CC-PRD-006-20261104 — Registro semanal de uptime Enerlink

**Estado:** seis decisiones previas y la transición de históricos aprobadas explícitamente por el responsable del producto el 2026-11-04, conservadas en `specs/006-registro-semanal-uptime/spec.md`. Aclaración funcional concluida y plan técnico elaborado en Feature-006; pendientes tareas y análisis de consistencia antes de implementar.

**Base:** PRD v1.1 §8.1/8.2; Constitución 1.2.0, principios I–III y jerarquía documental. 005 permanece como entrega técnica publicada; no se reescribe su evidencia ni se dan sus limitaciones por resueltas.

**Excepciones aprobadas:** admitir Excel semanal sin horario real, con referencia independiente y sin Ocupado inventado; identidad por conector/fuente/ventana 7 d/referencia, separada de captura real; promedio aritmético de porcentajes validados rotulado Promedio del informe, como excepción al agregado por minutos de las capturas reales; apertura provisional por referencia reciente con ambos apartados separados. Captura pegada conserva límites reales por conector. Pastel en dashboard/detalle declara completitud y duraciones conocidas. No convertir móviles en semanas exactas.

**Conservación:** valores, revisiones, permisos, trazabilidad y copias archivadas. La transición aprobada en FR-020 traslada los Excel de 005 con horario asignado al apartado sin horario real, conserva porcentajes/validaciones/historial y traza el cambio. Esas fechas no respaldan semanas oficiales en consultas nuevas; las capturas reales verificadas se conservan. Los conflictos o procedencias ambiguas exigen conciliación explícita, sin fusión, borrado o selección automática. No se modifica el PDF maestro del PRD ni una regla constitucional mediante este registro.
