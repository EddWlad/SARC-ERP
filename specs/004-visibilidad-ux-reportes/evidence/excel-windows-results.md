# Apertura en Excel de escritorio Windows

Excel 16.0, build 20326, automatización COM con la aplicación oculta, fecha 2026-10-31. Archivos generados por `ConsumptionExcelRendererTest` con datos sintéticos de 366 días.

| Archivo | Abrió | Reparación | Hojas | Gráficos |
|---|---|---|---|---|
| target/feature004-gerencial.xlsx | Sí | Sin indicio (nombre sin marca de reparación) | Resumen, Distribución, Diario, Por equipo, Transacciones, Gráficos | 6 |
| target/feature004-operador.xlsx | Sí | Sin indicio | Las anteriores más Excluidas | 6 |

Comprobado en ambos: líneas de 366 puntos, barras con 6 empatados/3 redes/2 plataformas/2 tipos, rangos `Gráficos!$A$6:$A$371` y siguientes, `B16` vacía (energía desconocida), `B17`=0 (cero real), `B371`=12,345, huecos como no trazados.

**Límites.** Con `DisplayAlerts` apagado un aviso de reparación no se mostraría: la conclusión se apoya en el nombre del libro y en que todo el contenido llegó completo. Falta apertura manual para confirmar el aviso y mirar la legibilidad. Archivos con la fixture de API completa (filtros y fórmulas existentes) no se abrieron en esta ronda.

**Segunda ronda.** Excel visible con `DisplayAlerts` activo: los dos archivos de fixture API y los de 366 días abrieron sin diálogo de reparación ni marca en el nombre. Legibilidad vista por exportación PNG de los seis gráficos del libro gerencial.
