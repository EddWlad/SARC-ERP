# Implementation Plan: Uptime y disponibilidad — Feature-005

**Alcance entregado:** RF-004 y ajuste de presentación de Consumo. **Fecha de consolidación:** 2026-11-04. **Rama de integración:** `codex/005-uptime-disponibilidad` → `main`. Especificación: [spec.md](spec.md). Esta consolidación documenta el diseño de 005 implementado; no incorpora el diseño posterior de 006.

## Summary

Monolito modular existente, nuevo módulo uptime con captura por conector, cuatro estados, cálculo, revisión/validación, ventanas provisionales y semanas exactas, grilla, importación/vinculación, dashboard y reportes. Consumo conserva sus cifras y reorganiza información secundaria, retirando el subconjunto de aprobadas sobre umbral solo del PDF/resumen Excel sin acceso operativo.

Los ajustes discutidos el 2026-11-04 de horario real por conector en el formulario semanal, Excel sin horario, referencia independiente, promedio del informe y pasteles pertenecen a Feature-006. Sus borradores se preservan localmente aparte y no se dan por implementados en 005.

## Technical Context

Java 25 / Spring Boot 4.1.1, Spring Security, MVC, Data JPA/Hibernate, Jakarta Validation, PostgreSQL 18, Flyway; Angular 22, Material, Signals/Signal Forms, Sass, Chart.js existente. Mapeos record/from/toCommand. Motor ImportJobs/ImportFiles/TabularReader propio; no MapStruct ni Spring Batch añadidos. Pruebas JUnit/MockMvc/Testcontainers/ArchUnit/OpenAPI, Vitest/ESLint/Prettier/build.

## Constitution Check

Principios I–III: especificación y decisiones trazadas, revisiones auditadas, nuevas V013/V014/V015 sin editar V001–V012. D05 identidades verificadas/ambiguas pendientes; D06/D07 UUID de conector/equipo; D09 excepción aprobada 1.2.0 para ambas hojas del informe semanal; D12 etiqueta histórica y nombres actuales, copias archivadas inmutables. Las reglas de Consumo no se extienden al cálculo de uptime. Sin nuevas dependencias ni acceso a entidades privadas de otro módulo.

## Arquitectura y estructura

Backend `modules/uptime/{api,application,domain,infrastructure}`; controllers/DTOs en api, servicios y puertos en application, entidades/políticas/repositorios en domain, adaptadores/parser/reportes en infrastructure. Inventario publica ConnectorDirectory/EquipmentDirectory por shared.catalog. ArchUnit verifica las dependencias.

Frontend `features/uptime/{domain,data-access,application,presentation}`. HTTP en data-access, facades para estado/operaciones, componentes Material y shared/ui para presentación. Rutas tablero, captura, revisión, detalle, importaciones/lote y vinculación. ESLint protege límites entre features. ChartView y estilos compartidos existentes; no rediseño general.

## Diseño implementado

- Measurement identifica conector/fuente/inicio/fin; revisiones guardan valores, crudos, etiquetas, autor y validación. @Version/UNIQUE protegen concurrencia. Una corrección espera validación mientras la vigente computa.
- UptimeFormula y MeasurementRules concentran cálculo y observaciones. Cuatro estados suman tiempo evaluado; offline descuenta, fallo no. Uptime informado del Excel sin Ocupado usa su porcentaje y peso del período declarado según el modelo de 005.
- PeriodRules distingue ventanas 24 h/7 d/30 d y semana exacta confirmada; no fusiona solapamientos ni tipos. Apertura oficial más reciente o provisional preferida de 7 d.
- UptimeCalculator calcula cobertura, equipos, ranking completo/empates y tendencia oficial. Lectura/reportes comparten snapshot REPEATABLE READ; render fuera de transacción.
- ImportJobs prepara/confirma por bloques; clasificación, decisiones, outcomes y recuperación al iniciar. Identidad verificada de equipo, alias/número/tipo de conector, propuestas sujetas a confirmación y pendientes sin atribución arbitraria.
- El informe semanal actual lee ambas hojas, conserva hoja/fila, convierte horas al minuto y requiere semana cerrada elegida. En 005 esa semana proporciona el intervalo declarado; no es evidencia de que Enerlink haya observado esos límites. La representación de horario desconocido queda para 006.
- Permisos backend: ADMINISTRADOR/ANALISTA operan; cinco roles consultan. Lectores no reciben valores no validados, motivos, revisiones ni detalle de excluidas. Cobertura/cantidades y pendientes sin atribuir siguen visibles.
- PDF/Excel corporativos, siete hojas en Excel, archivo privado inmutable y kind UPTIME. Contenido gerencial sin UID/PUK/OCPP.

## Correcciones de cierre QA

1. El total sin atribuir respeta el rango solicitado aun vacío; sin rango se resuelve sobre mediciones incluidas antes de filtrar red/equipo/conector, con lookback acotado si no hay datos.
2. Ese total se transmite al modelo de reporte y ambos formatos.
3. Una etiqueta de conector con número desbordado queda pendiente con motivo, sin abortar otras filas.

## Validación y límites

Ver [quickstart.md](quickstart.md) y evidence/. Tests de fórmula, períodos, observaciones, correcciones, concurrencia, alias, importación/idempotencia, seguridad, cobertura, ranking y reportes. Baseline 005 y recorridos visuales no prueban la futura 006. Pruebas DB solo con PostgreSQL desechable, almacenamiento simulado, sin migrar BD dev.

Objetivos: inicial ≤500 kB; p95 consultas 2 s/dashboard 5 s a 30 usuarios es referencia no demostrada por bench secuencial. evidence/performance.md documenta que ranking/equipos a 1000 conectores superan 2 s. T124 humana sigue sin medir. No se modifican permisos/cálculos/importaciones de otros módulos.

## Complexity Tracking

Se mantiene la arquitectura original; no servicios externos, motor de gráficos o módulos paralelos. V014/V015 amplían la importación semanal sin reescribir V013. El cambio temporal de 006 requiere especificación y migración propias posteriores.
