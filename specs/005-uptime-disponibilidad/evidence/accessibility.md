# Accesibilidad (T121)

Verificación automática y manual dirigida, no certificación WCAG 2.2 AA. No se usó lector de pantalla real ni axe.

| Criterio | Resultado |
|---|---|
| Nombres accesibles | Cero controles visibles sin nombre en las rutas auditadas. Filas de la grilla con nombre propio |
| Encabezados | Un `h1` por pantalla y orden sin saltos, tras corregir dos pantallas |
| Contraste de texto | Sin hallazgos con tema asentado, en ambos temas, sobre las rutas medidas. No mide contraste de gráficos |
| Foco visible | `:focus-visible` activo; los campos Material lo indican con su línea activa |
| Foco en diálogo | Entra al primer campo y, tras «Guardar y siguiente», permanece en el diálogo |
| Teclado | Registro completo con Tab y teclado, salvo el botón final, pulsado con ratón en el recorrido |
| Errores | `role="alert"` en errores y `role="status"` en avisos de `measurement-form`, comprobado en código; no oído con lector |
| Estados sin color | OFICIAL, PROVISIONAL y situaciones de grilla en texto |
| Gráficos | Tendencia con `aria-label`, «Uptime oficial por semana»; ranking y resúmenes en tablas |

**Pendiente**: pasada con lector de pantalla y axe en navegador.
