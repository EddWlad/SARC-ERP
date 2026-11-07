# Control de cambios del PRD SARC

## CC-PRD-005-20261104 — Captura semanal de uptime Enerlink

**Fecha y aprobación:** 2026-11-04, responsable del producto mediante seis respuestas explícitas en este chat. **Referencia:** PRD v1.1, §8.1 RF-004.3–RF-004.6, RF-004.12 y §8.2; Constitución 1.2.0, principios I–III y jerarquía documental. Especificación vinculante: `specs/005-uptime-disponibilidad/spec.md`, aclaraciones del 2026-11-04 y FR-048–FR-052.

**Motivo:** Enerlink permite ventanas móviles de 24 horas, 7 días y 30 días. El informe semanal se arma por conector y se remite cada lunes; no aporta horas reales de consulta ni Ocupado. Asignar lunes 00:00 como límite observado inventaría información.

**Cambio aprobado:** las capturas pegadas conservan límites reales por conector. El Excel semanal admite horario real no informado y una semana de referencia independiente. Su identidad es conector + fuente + ventana de 7 días + semana; valores iguales se omiten, diferentes requieren corrección, sin fusionarse con capturas con horario. Los porcentajes validados del Excel se resumen por promedio aritmético rotulado «Promedio del informe», como excepción al cálculo ponderado por minutos reales. La apertura provisional usa la semana de referencia más reciente con datos incluidos y separa ambas clases. Se añade pastel de estados en dashboard y detalle, declarando cobertura y valores ausentes.

**Permanece:** medición por conector del inventario; 0 real distinto de ausencia; validación expresa, permisos y trazabilidad; ventanas PROVISIONALES separadas de semanas oficiales; no reconstruir Ocupado por diferencia; reportes archivados inmutables; diseño y arquitectura existentes. La excepción de lectura de las dos hojas ya está aprobada en D09; no requiere enmienda nueva.

**Impacto:** identidad temporal, persistencia, captura, importación, filtros, agregados y DTOs de uptime, PDF/Excel y composición de estados. El plan propone una migración nueva posterior a V015. No se autoriza borrar o reconciliar automáticamente registros anteriores ni modificar copias emitidas. La planificación no ejecuta el cambio ni modifica el PDF original del PRD.
