# Implementation Plan: Integridad temporal, gestión de sesiones y estabilidad visual

**Branch**: `main` (checkout actual) | **Date**: 2026-11-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature 009, aclaración A del 2026-11-11 y encargo técnico adjunto. La selección temporal que devuelve Spec Kit es `009-integridad-temporal-consumo`.

## Summary

Extender RF-003 para agrupar sesiones por la fecha de inicio que corresponde a cada fuente. Enerlink conserva la fecha, hora, segundos y desplazamiento de origen. Wallbox mantiene su política actual. Se conserva el instante real y el reloj operativo de Ecuador para D13. La atribución, la elegibilidad y las revisiones vigentes alimentarán el histórico, los agregados y los informes nuevos con la misma semántica.

La entrega se divide en integridad temporal y conciliación histórica, gestión individual de sesiones y estabilidad visual. La baja lógica conserva identidad y trazabilidad. El parpadeo se aborda con reproducción local y una corrección limitada a la causa demostrada. [Investigación y alternativas](research.md).

## Technical Context

| Aspecto | Base comprobada |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1.1, MVC, Security, Jakarta Validation, JPA/Hibernate, Maven Wrapper. DTO y mapeo existentes. |
| Frontend | Angular 22.2.x, Signals, RxJS, Material/CDK, SCSS, Chart.js 4.6.x. Node 24 en CI. |
| Persistencia y archivos | PostgreSQL 18, Flyway, almacenamiento privado por `FileStorage`. Reportes OpenPDF y Apache POI. |
| Pruebas | JUnit, MockMvc, ArchUnit, Testcontainers PostgreSQL 18, Vitest y comprobación visual en navegador. OpenAPI generado y tipos Angular verificados por CI. |
| Plataforma | Aplicación web de escritorio, tablet y móvil. Monolito modular en Linux y publicación por GitHub Actions según Feature-007. |
| Rendimiento y alcance | Mantener paginación, predicados indexados y el rango máximo vigente de agregación. Comparar planes de consulta y tiempos con el mismo conjunto sintético antes y después. Revisar evidencia histórica en lotes acotados y registrar el universo realmente cubierto. |
| Restricciones | Aplicar autorización en servidor y preservar el refresco legítimo. Usar datos sintéticos o copias autorizadas aisladas. Las escrituras históricas requieren una propuesta aprobada. |

## Constitution Check

| Control | Antes de investigar | Reevaluación después del diseño |
| --- | --- | --- |
| Principios I y II | Spec aclarada, checklist 16/16 y escenarios verificables. | FR-001 a FR-022 vinculados a artefactos y ensayos. La fecha de atribución se define en Consumo y la usan sus consultas y exportaciones. |
| Principio III | Cualquier cambio de esquema requiere una migración nueva. | Evolución aditiva con prueba de actualización desde la versión anterior. Numeración asignada al integrar con el historial Flyway y Feature-008. |
| D05, D09, D10 y D11 | Vinculación por identidades verificadas, primera hoja, revisión humana sobre umbral y redondeo por lectura. | Se reutilizan políticas y controles vigentes. La corrección manual no sustituye la revisión de energía ni autoriza vínculos ambiguos. |
| D12, D13 y D14 | Nombres actuales en nuevos informes, rango inicial por día operativo, Top 5 con empates. | El corte y nombres de publicación conservan sus reglas. El reloj de Ecuador selecciona el rango y la fecha de atribución decide qué sesiones contiene. Se conserva el ranking de Consumo. |
| PRD y TRD | PRD §3.2, §4, RF-003.3–11, RF-003.13–17 y §11. TRD §§3.3, 9.2, 14.1. | La decisión A concreta RF-003.10 y es compatible con D13. Registrar formalmente el alcance de FR-002/006 y de las acciones en el control de cambios del PRD antes de implementar el comportamiento afectado. Alinear el TRD y la referencia de la Feature-002/ADR 0005 con el criterio aprobado cuando corresponda. |
| Feature-008 | Pruebas y cambios en el checkout compartido. | El plan se limita a documentación 009. Para implementar, acordar el punto de integración antes de regenerar OpenAPI, numerar migraciones o editar archivos compartidos. El diseño se concentra en Consumo. |

**Resultado del gate**: conforme para planificación y tareas. El registro documental y la coordinación de los archivos compartidos son condiciones de las tareas que implementen esos cambios. No se identifica una enmienda constitucional necesaria.

## Project Structure

### Documentation (this feature)

```text
specs/009-integridad-temporal-consumo/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/
│   └── consumo.md
├── quickstart.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
sarc-bakend/
├── src/main/java/com/tidsec/sarc_bakend/modules/consumption/
│   ├── api/
│   ├── application/
│   ├── domain/
│   └── infrastructure/
├── src/main/resources/db/migration/
├── src/test/java/com/tidsec/sarc_bakend/modules/consumption/
└── docs/api/openapi.json
sarc-frontend/src/app/
├── features/consumption/
│   ├── application/
│   ├── data-access/
│   ├── domain/
│   └── presentation/
├── core/api/generated/
└── shared/ui/chart/    # referencia; cambio solo si lo demuestra el diagnóstico
```

**Structure Decision**: Consumo conserva la propiedad de las sesiones y su política de períodos. Resolver referencias de equipo mediante el puerto de catálogo existente. Reusar las publicaciones de Reporting y los componentes visuales de SARC. Aplicar mejoras al nivel más localizado que permita comprobar el resultado.

## Diagnóstico y clasificación

| Requisitos | Estado constatado | Diseño |
| --- | --- | --- |
| FR-001–006 | Los importadores convierten a instantes. La entidad calcula `start_date` en Ecuador para ambas fuentes. Angular y Excel presentan la hora con la zona operativa. D13 y los predicados por fecha existen. | Transportar procedencia temporal desde el importador, persistir fecha de atribución efectiva, presentar datos aprobados de origen y reutilizar selección y agrupación. |
| FR-007–010 | Hay archivos o pegados con huella, filas normalizadas, revisiones de lote y archivo de informes. El historial disponible requiere comprobarse por sesión. | Diagnóstico reproducible de solo lectura y aplicación idempotente por propuesta aprobada. Conservar el snapshot y el archivo existente. |
| FR-011–016 | Existen histórico, acciones de prueba y revisión energética, roles, control de versión y revisiones de importación. Faltan detalle general, edición directa y baja lógica. | Ampliar controlador, servicios y modelo de Consumo. Excluir bajas de consultas oficiales y proteger la identidad en todas las rutas de importación. |
| FR-017–020 | Hay refresco periódico, retención de resultados, animación de gráficos y controles de fila. La causa del síntoma en navegador está pendiente de reproducción. | Comparar antes y después con las mismas secuencias. Probar retención según la consulta, estabilidad de gráficos y acciones accesibles según la causa observada. |
| FR-021–022 | Existen control documental, CI y flujo de versiones de Feature-007. | Registrar la decisión y el impacto, y ejecutar regresiones en un entorno aislado que incluya la versión de Feature-008 pertinente. |

## Phase 0 — Outline & Research

[research.md](research.md) registra la evidencia y alternativas sobre importadores, persistencia, evidencia histórica, revisión de sesiones, reportes, gráficos y dependencias. La lectura de código confirma el mecanismo de atribución anterior, pero el diagnóstico de datos concretos requiere verificar los originales. Para el parpadeo, la reproducción distingue recarga de datos, reemplazo de componentes, animación y navegación. La evidencia necesaria está definida en [quickstart.md](quickstart.md).

## Phase 1 — Design & Contracts

### 1. Integridad temporal y reportes (US1)

- Los parsers construyen una lectura con instantes y procedencia temporal. La política de Consumo decide la fecha de atribución. `start_date` sigue siendo el punto común de filtros y grupos diarios. Se verifican dependencias, índices y plan de consulta antes de migrar.
- El backend entrega fechas de origen explícitas y una fecha de atribución. Angular usa esos campos para Enerlink. Mantiene el formateador actual para Wallbox y los helpers de D13. Las horas de origen se representan sin volver a convertirlas.
- Resumen, tendencia, histórico, PDF y Excel aplican los mismos criterios y reglas de elegibilidad. FR-006a conserva la atribución operativa de históricos indeterminados y su aporte oficial cuando ya eran elegibles. El agregador calcula sus sesiones y kWh conocidos como parte del total con los mismos filtros. El histórico marca la fila y el dashboard y los informes muestran el desglose. Conservar la diferencia entre energía desconocida y cero.
- Los informes nuevos rotulan la base temporal y el corte. Preservar la transacción de lectura consistente de cada emisión, las etiquetas corporativas y la autorización del archivo histórico. Usar la fecha de cálculo del dashboard y el corte propio de cada informe. Verificar cifras con revisiones, filtros y permisos idénticos, y verificar cortes distintos con una edición entre solicitudes. La hora de cálculo no identifica por sí sola una revisión de datos.

### 2. Conciliación del histórico (US2)

- En una copia autorizada, recorrer lotes verificando huellas, procedencia, fila y revisión aplicada. Clasificar coincidencias, diferencias de atribución, errores comprobados de instante e indeterminados. Cuantificar la cobertura por fuente y período, incluidos los originales no recuperables. Antes de la revisión de publicación, completar este diagnóstico de solo lectura sobre el conjunto histórico pertinente y dejar constancia de alcance, corte, cobertura e impacto. El ensayo sintético valida el mecanismo y tiene evidencia propia.
- Preparar un manifiesto con identidad, versión, evidencia y valores antes/después. Distinguir un cambio de fecha de reporte de un cambio de instante. Calcular el impacto oficial por día, mes y filtros relevantes, con reconciliación global de conteos y kWh. Registrar la aprobación mediante una operación de Consumo protegida con la autenticación existente y el rol Administrador. El servidor obtiene la identidad de la sesión autenticada y guarda una constancia persistida para la huella exacta, con acceso restringido.
- Aplicar por lotes acotados mediante el comando operativo explícito. Antes de escribir, leer la constancia de aprobación desde el almacenamiento controlado por el sistema y comprobar identidad registrada, propuesta y huella recalculada. Rechazar una aprobación ausente, inválida o ajena al conjunto. Controlar la versión y registrar el resultado auditable por candidato. Reintentar sin duplicar revisiones. Los candidatos modificados o sin evidencia suficiente quedan fuera de la aplicación y se reportan. El ensayo de migración y reversión usa el flujo de Feature-007.

### 3. Ver, editar y eliminar lógicamente (US3)

- Ampliar el detalle bajo los permisos de lectura vigentes. Edición y baja pasan por el servicio autorizado y transaccional. Usar versión esperada, confirmación en la interfaz, motivo y revisión de valores anteriores. Conservar la evidencia original y resolver el equipo por el catálogo.
- Si cambia la lectura energética normalizada, reevaluar la elegibilidad según D10/D11 y las reglas existentes. Una aprobación anterior se conserva únicamente cuando sigue siendo válida. Una corrección que cambia equipo o red reevalúa la admisibilidad de la fuente.
- La baja lógica mantiene la clave natural y separa su marca del estado de calidad. El predicado de Consumo la excluye de resultados oficiales. Las rutas de búsqueda por identidad, vínculo pendiente, confirmación y reintento impiden duplicación o reactivación. Los lotes en vuelo revalidan la versión antes de tocar una sesión existente.
- Ampliar las acciones existentes en la tabla y en las tarjetas móviles. Usar los diálogos, motivos, mensajes y convenciones de Consumo e Inventario. La gestión de bajas permanece en el alcance de auditoría autorizado.

### 4. Estabilidad visual y regresión (US4)

- Capturar una reproducción local con respuesta idéntica, cambio real, error de red controlado y regreso a pestaña visible. Registrar si cambian el componente, el lienzo, la animación o la navegación. Mantener el refresco y su corte por inactividad.
- Si se confirma el cambio de entradas equivalentes, estabilizar las opciones en Consumo. Para actualizaciones de la misma consulta, conservar resultados visibles y usar una actualización en el gráfico que no repita la animación si el diagnóstico lo justifica. Si cambia el filtro, mostrar los estados de la nueva consulta. Ignorar respuestas tardías de otra consulta. Si la causa exige tocar un componente compartido, acordar la integración y ejecutar pruebas cruzadas con Feature-008.
- Probar teclado, foco, desplazamiento y edición en curso durante refrescos. Revisar ambos temas, móvil, tablet y escritorio con los criterios aplicables de WCAG 2.2 AA. Usar Impeccable en la revisión visual si está disponible y aporta evidencia adicional.

**Artefactos**: [modelo de datos](data-model.md), [contratos HTTP, reportes y operación](contracts/consumo.md), [guía de verificación](quickstart.md).

## Riesgos y dependencias de ejecución

| Riesgo | Control y evidencia esperada |
| --- | --- |
| Histórico sin evidencia o impacto no previsto | Diagnóstico de solo lectura sobre la copia autorizada antes de revisar la publicación, desglose oficial de FR-006a y conciliación de subtotales. La aplicación requiere constancia válida del conjunto exacto. |
| Reimportación o edición concurrente tras una baja | Identidad natural conservada, estado y versión reevaluados al confirmar, prueba con lote preparado antes de la baja. |
| Diferencia por corte o por fecha de origen | Fixture mixta 5/8/13 kWh, pruebas de cambio de mes, snapshot de emisión, rótulo temporal y verificación de originales archivados. |
| Recuperación después de migrar o aplicar el histórico | Separar copia previa a publicación y copia previa a aplicación histórica. Una versión anterior interpretaría `start_date` con su semántica anterior y desconoce las bajas. Tras una migración no se presupone recuperación por reemplazo de binarios. Mantener el procedimiento de recuperación de Feature-007, ensayar restauración aislada y reconciliar escrituras posteriores antes de autorizar una restauración o corrección compensatoria. |
| Interferencia con Feature-008 | Comprobar migración reservada, regenerar contratos al integrar y probar módulos consumidores compartidos con el flujo de CI. |
| Corrección visual sin causa comprobada | Evidencia de reproducción, trazas y comparación antes/después. Si no se reproduce, entregar el diagnóstico y continuar la investigación del síntoma antes de declarar resuelto SC-006. |

## Complexity Tracking

Sin excepciones a la Constitución. Se reutilizan el módulo, las dependencias y el flujo de entrega existentes.

