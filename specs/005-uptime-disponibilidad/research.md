# Research: Feature-005 — decisiones de implementación

Consolidación 2026-11-04 del alcance existente. Las seis decisiones posteriores sobre registro semanal/referencia/horario desconocido y pasteles se conservan en borradores 006, no en esta entrega.

## Arquitectura

**Decisión:** monolito modular y frontend por funcionalidades actuales. **Motivo:** ArchUnit y ESLint ya fijan los límites; puertos shared.catalog permiten reutilizar inventario. **Alternativas descartadas:** microservicio, acceso directo a entidades de otro módulo, nuevas dependencias o duplicar el motor de importación.

## Medición y correcciones

**Decisión:** cabecera por conector/fuente/período, valores en revisiones, vigente independiente de última pendiente; exclusión reversible. **Motivo:** unicidad/idempotencia y continuidad de históricos. **Alternativas:** sobrescribir o borrar versiones, generar otros conectores o duplicar por archivo.

## Fórmula y calidad

**Decisión:** fórmula por minutos evaluados y offline, una política reutilizada en captura/importación; observaciones y validación expresa. Tolerancias confirmadas en spec y muestra real. **Alternativas:** horas decimales ambiguas, 0 como ausencia o rechazo silencioso de valores numéricos inválidos.

## Períodos y cobertura

**Decisión:** exactas confirmadas separadas de móviles, una última incluida por conector en provisional, pares conector-semana en oficial. **Motivo:** no doble conteo ni inferir semanas desde ventanas. **Alternativas:** sumar ventanas solapadas o promediar porcentajes redondeados.

## Informe semanal

**Decisión de 005:** muestra real de dos hojas, semana elegida, porcentaje informado cuando falta Ocupado, horas convertidas al minuto; etiqueta y crudos conservados. Identidades verificadas por Cargador si no hay Activo; alias aprobado y sugerencia explícita cuando número/tipo no resuelven. **Motivo:** aprovechar el archivo actual sin columnas nuevas. **Limitación:** la semana asignada no prueba límites observados; se revisará en 006, junto con nulos temporales, identidad por referencia y media del informe.

## Consulta y reportes

**Decisión:** misma calculadora/snapshot para dashboard y reportes, proyección de lectura por rol, render fuera de transacción y archivo privado inmutable. **Alternativas:** ocultar solo UI, incorporar valores pendientes a indicadores o reutilizar URL externa pública.

## Cierre QA

Regresiones reproducen: sin atribuir de otros períodos ante rango vacío; omisión en PDF/Excel; NumberFormatException al resolver una etiqueta de 20 dígitos. Corregir en servicios/pipeline/renderizadores existentes; no introducir funcionalidad de 006. Información anterior y posterior en evidence/closure-qa.md.

## Fuentes

spec.md; V013–V015; modules/uptime; ConnectorDirectoryService; shared/importing; frontend uptime y estilos compartidos; ArchitectureTest; OpenApiContractTest; ESLint y DESIGN.md. Evidence/enerlink-sample.md conserva el contraste de muestra. No se consultan cuentas ni secretos reales para estos tests.
