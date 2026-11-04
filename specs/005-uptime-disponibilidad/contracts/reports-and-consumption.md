# Contrato reportes uptime y ajustes Consumo — Feature-005

GET /api/v1/uptime/report?format=PDF|XLSX, filtros equivalentes al dashboard. Snapshot REPEATABLE READ, render fuera de transacción, ReportArchive kind UPTIME/perfil OFFICIAL_ONLY, auditoría, X-Report-Id/X-Report-Cut-Off y no-store. Contenido gerencial permitido igual para cinco roles, copia privada inmutable.

PDF: identidad/rango/zona/corte; oficial o aviso de ausencia; provisional separada; tendencia oficial con huecos; horas offline/fallo; ranking por conector; promedio por equipo con cobertura; cobertura/exclusiones y total Pendientes de vincular (sin atribuir). Sin UID/PUK/OCPP, valores pendientes ni motivos restringidos.

Excel conserva siete hojas: Resumen, Semanas oficiales, Provisionales, Por conector, Por equipo, Cobertura y exclusiones y Gráficos. Valores numéricos reales; ausencia como Sin datos; sin atribuir con su cantidad. No gráficas/cálculos nuevos de 006. Ambos formatos usan nombres actuales y misma selección/corte.

Consumo: reorganiza información secundaria con paneles/títulos y cinco categorías para operativos. PDF/resumen Excel sin acceso operativo ocultan Aprobadas sobre umbral; detalle Excel y nota dashboard intactos. No cambia API de cálculo, energía nula/0, revisión, aprobaciones, históricos, importaciones ni empates. Los reportes archivados anteriores conservan bytes y permisos.

Pruebas: cifras dashboard/documento, perfiles/roles, nulos y 0, sin atribuir, archivo inmutable después de corrección, filtros/rango, OpenAPI, y regresiones de Consumo. La planificación de referencia semanal sin horario queda fuera de esta entrega.
