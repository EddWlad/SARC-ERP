# Contrato de reportes y archivo — Feature-006

Prefijo GET /api/v1/uptime/report?format=PDF|XLSX. Usar la selección/filtros de [api-ui.md](api-ui.md), misma calculadora/proyección y snapshot REPEATABLE READ. Mantener render fuera de transacción y descarga autenticada, kind UPTIME/perfil OFFICIAL_ONLY, X-Report-Id/X-Report-Cut-Off y private/no-store.

## Contenido y cálculo

- Oficial: solo conocidos exactos verificados; los Excel005 reclasificados no vuelven por el enum histórico EXACT_WEEK.
- Con horario real: rango observado y método ponderado por minutos existente.
- Informe sin horario: referenceWeek, Horario real no informado, Promedio del informe/media aritmética de porcentajes incluidos; sin minutos evaluados imitados ni desconexión deducida del porcentaje.
- Ambos apartados con resultados/ranking/equipo/cobertura separados, cantidades y sin atribuir del mismo alcance. Sin total de uptime conjunto ni duplicar un conector en un denominador combinado.
- Conservar tendencia oficial/huecos y gráficos existentes. Composición de estados conocida puede tabularse si es útil, declarando faltantes; el pastel nuevo es requisito de dashboard/detalle, no obligación de añadir todos los gráficos a PDF/Excel.
- Nombres actuales, etiqueta del hecho diferenciable, zona y corte. Nada de UID/PUK/OCPP, motivos/revisiones operativas ni fuentes sin validar en indicadores.

PDF mantiene CorporatePdf/branding y orden oficial → provisionales separados → resultados/cobertura. Excel mantiene sus siete hojas: Resumen, Semanas oficiales, Provisionales, Por conector, Por equipo, Cobertura y exclusiones, Gráficos. Añadir columnas de precisión/referencia/método y dividir filas por apartado; no cambiar las celdas numéricas a texto salvo las ausencias, ni redondear valores fuente antes de su media.

## Criterios archivados, sin fechas falsas

Ampliar shared ReportPublication con discriminante `selectionBasis=REFERENCE_WEEK` y `referenceWeek` (lunes), `temporalSection` opcional validado, zona y tipo/class correspondientes. En esa variante `from/to` pueden omitirse; no poner hoy/mañana/lunes para cumplir un constructor. Variante anterior de rango real conserva from/to obligatorios y to>from, con validación de enums/claves permitidas. Ambos presentes junto a referencia canónica incompatible →error, no inferencia silenciosa.

Este cambio es aditivo: los criterios de Inventario/Consumo y los documentos existentes permanecen válidos sin nuevas claves. shared no importa enums privados de uptime: valida vocabulario neutral explícito, igual que hace con periodKind/periodType actuales.

Usar `view.cutOff()` como fecha del contenido y publicación en lugar de tomar otro instante para el reporte. La lectura del documento se completa en una transacción, luego se renderiza y archiva exactamente esa copia; los headers corresponden al mismo corte. Una referencia seleccionada vacía se puede informar como sin datos sin fabricar horario.

## Inmutabilidad y transición

FR020 cambia el tratamiento de nuevas consultas/documentos, no los archivos emitidos. No actualizar report_issued, contenido/hash/file_ref/cut_off/created_at para corregir retrospectivamente el significado de un reporte anterior. Los permisos y flags de descarga actuales siguen; no regenerar uptime mediante un diálogo de Consumo ni emitir una URL privada reutilizable.

Pruebas de comparación: datos/corte fijados,0/100→50, ponderados conocidos con duraciones diferentes, ambos apartados, ausencia/0, una referencia sin datos, sin atribuir y exacta005 reclasificada fuera de oficiales. Abrir Excel en el entorno Windows previsto y comprobar estructura/celdas/fórmulas y ausencia de reparación del archivo; comparar archivos archivados byte a byte antes/después de transición/corrección. No se requiere proveedor real para las regresiones aisladas.

Una solicitud posterior a escrituras concurrentes puede tener otro corte y otro resultado legítimo. No añadir en006 un almacén histórico de snapshots de dashboard para fingir una igualdad temporal que la arquitectura actual no ofrece; los contratos exponen claramente cada corte.
