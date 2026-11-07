# Compatibilidad de reportes006

Fecha:2026-11-06. Archivos nuevos generados por UptimeReportConsistencyTest contra PostgreSQL18 desechable. Sin archivos originales del usuario, sin guardarlos de nuevo en Excel.

## Excel real

Microsoft Excel de escritorio para Windows, versión2608 compilación20326.20232 (Hacer clic y ejecutar), comprobada en Archivo→Cuenta.

- target/feature006-report-reference.xlsx: apertura normal, sin reparación. Las siete hojas Resumen/Semanas oficiales/Provisionales/Por conector/Por equipo/Cobertura y exclusiones/Gráficos presentes. Resumen97,68 numérico, rótulo Informe sin horario real y cobertura16,67; evaluados/ausencia No informado. Gráficos vacío coherente con ausencia de semanas oficiales.
- target/feature006-report-full.xlsx: apertura normal, sin reparación. Resumen oficial99,61, provisional97,29 y horas0,65 coincidentes con dashboard/test. Navegación por las siete hojas con Ctrl+PgDn hasta Gráficos. Excel reconoce gráfico de línea con marcadores, título Uptime oficial por semana (%), serie % y punto12/10 valor99,61; imagen y celdas fuente presentes.
- No se introducen fórmulas nuevas ni se sustituyen valores numéricos por texto. Pruebas POI conservan tipos/hojas/gráficos; Excel confirma lectura de la estructura y representación real. No se afirma haber ensayado otras versiones de Office ni un conjunto ilimitado de archivos.
- Capturas de apertura, hojas y gráfico registradas en el recorrido CUA de este chat. Se cerró Excel; inventario confirma proceso sin ventanas. No se guardaron cambios ni se enviaron archivos.

## PDF

PDF de referencia2páginas y oficial+provisional3páginas renderizados con pypdfium2 e inspeccionados página por página. Se confirmó defecto de nota tapada por tarjetas; corregido mediante espaciado explícito del helper corporativo, manteniendo su comportamiento anterior por defecto. Metodología de referencia ya no atribuye ponderación por minutos al promedio del informe. Render final inspeccionado: nota legible, sin tarjetas superpuestas; encabezado Cobertura unido a sus tarjetas. Evidencias [referencia1](pdf-reference-1.png), [referencia2](pdf-reference-2.png), [oficial1](pdf-full-1.png), [oficial2](pdf-full-2.png), [oficial3](pdf-full-3.png).

Las pruebas automatizadas contrastan cifras/textos/corte/criterios y archivo anterior inmutable. No sustituyen el acceso al proveedor real, pendiente por ausencia de QA autorizado.
