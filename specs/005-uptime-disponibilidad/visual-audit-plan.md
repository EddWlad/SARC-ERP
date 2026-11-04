# Plan de auditoría visual — Feature 005

No es un informe de auditoría ejecutada. Organiza la evidencia que debe producir la implementación. Adapta el enfoque de `specs/004-visibilidad-ux-reportes/visual-audit-plan.md`.

## Matriz

Cada pantalla y estado aplicable: 360, 390, 768, 1024 y 1440 px, en tema claro y oscuro. Diez combinaciones por pantalla. Solo con roles autorizados.

| Familia | Pantallas y diálogos |
|---------|----------------------|
| Captura | `/uptime/captura`; grilla, formulario, «Guardar y siguiente», aviso de observación |
| Revisión | `/uptime/revision`; colas, validación individual y por lote, historial, corrección, exclusión |
| Tablero | `/uptime/tablero`; semana oficial, provisional, sin datos; rol operativo y de consulta |
| Importación | `/uptime/importaciones` y `/uptime/importaciones/:id`; pegado, vista previa, vinculación, confirmación |
| Bloque | Diálogo de bloque de texto |
| Reportes de uptime | Emisión PDF y Excel desde el tablero; aparición en `/reportes` |
| Consumo | `/consumo/tablero`; bloque «Fuera de los indicadores oficiales» para operativos, ausencia para consulta |

## Estados

Normal, carga, vacío, error de servidor, validación y ayuda, contenido largo, ocupado, confirmación, acceso limitado y resultados extensos. Marcar N/A con motivo; un estado no se da por inexistente porque no se probó.

## Procedimiento

1. Levantar `Feature005BrowserSandbox`, sin tocar la base de Compose, con datos sintéticos de nombres largos, 0 %, sin medición y tres conectores.
2. Ejecutar la auditoría de Impeccable según su referencia instalada. Registrar señales deterministas aparte del juicio visual, y verificar cada hallazgo en navegador.
3. Capturar el defecto antes de editar. Medir solapamiento y desborde, y comprobar asociación entre texto y control, foco y acción afectada.
4. Aplicar la corrección mínima con API pública, tokens o componente reutilizable.
5. Repetir la combinación con igual rol, estado, ancho y tema. Cerrar solo con prueba.

## Comprobaciones propias de la feature

- El rótulo provisional y la semana oficial se distinguen sin depender solo del color.
- 0 % validado y «sin medición» nunca se presentan igual.
- Un rol de consulta no ve porcentajes pendientes, observaciones ni justificaciones.
- Los cuatro tiempos se leen a 360 px sin desplazamiento horizontal de página.
