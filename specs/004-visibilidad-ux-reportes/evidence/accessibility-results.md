# Accesibilidad 004

Chromium del panel de Claude Desktop, 2026-10-31. Teclas enviadas como eventos de confianza por automatización, sin dispositivo físico.

| Criterio | Tipo | Método | Resultado |
|---|---|---|---|
| Reglas WCAG A/AA, contraste de texto incluido | Automático | axe-core 4.14, matriz de visual-audit.md, ambos temas | 0 violaciones tras A-03, V-02, V-03 y V-06 |
| Tema oscuro real | Automático | `prefers-color-scheme: dark` emulado, modo «sistema», tablero | Fondo oscuro aplicado, 0 violaciones |
| 1.4.10 Reflow | Medido | 320px en tablero, sesiones, inventario y reportes; matriz 360–1440 | PASS, sin scroll horizontal |
| 1.4.4 Texto 200% | Medido | Zoom de navegador: equivale a reflow 360–768, PASS (técnica G142). Prueba adicional con `rem` al doble, ver límites | PASS por zoom |
| 2.4.1 Saltar bloques | Manual | Tab + Enter en /consumo/sesiones | PASS tras A-01 |
| 2.1.1 Teclado y 2.4.3 Orden | Manual | Conector, usuario, identidad, horario, revisión energética | PASS: abre con Enter, foco dentro, Esc cierra, foco vuelve al botón |
| Guardia de cambios | Manual | Conector con datos: Esc abre confirmación, foco dentro; Esc vuelve con valor intacto | PASS |
| Menús | Manual | Tema, acciones de usuario y de sesión: Enter, flechas, Esc y retorno | PASS |
| Menú móvil | Manual | Abre, Tab recorre enlaces, Esc cierra, foco vuelve | PASS tras A-02 |
| 2.4.7 y 1.4.11 Foco visible | Manual + medido | 22 paradas de Tab en tablero y anillo en botones | PASS tras A-03. Campos con borde de foco propio |
| 2.5.8 Objetivos 24px | Medido | Toda la matriz | PASS, excepción de espaciado para `a.link` de fecha |
| 1.4.11 Gráficos | Medido + criterio | Ver abajo | PASS por alternativa textual |
| 3.3.1 Errores | Manual + automático | Validación en diálogos: mensajes visibles, sin violaciones axe | PASS. Anuncio por lector no probado |

## Defectos corregidos

| ID | Defecto | Causa | Corrección |
|---|---|---|---|
| A-01 | Saltar al contenido recargaba la app: observado en /inicio; salir de otra ruta es deducción, no se reprodujo antes de corregir | Con `<base href="/">`, `#contenido` apunta a la raíz | `skipToContent` en `shell.ts`, spec |
| A-02 | Menú móvil cerrado dejaba foco en `body` | El drawer no restauraba foco | `restoreMenuFocus` en `shell.ts`, spec |
| A-03 | Botones sin anillo de foco, solo tinte tenue | `.mdc-button` anula `:focus-visible` con igual especificidad | `button:focus-visible, a:focus-visible` en `_base.scss` |
| A-04 | Etiqueta «Revisión energética» cortada de 360 a 1280 | Campo más estrecho que la etiqueta | Etiqueta «Revisión» en sesiones |

A-04 corrige una conclusión previa: etiquetas flotantes se habían dado por falso positivo. Medidas con su escala de 0,75, solo esa se cortaba.

## Gráficos

Contraste sobre superficie: serie 1, 4,2:1 claro y 5,1:1 oscuro; serie 2, 3,1:1 y 4,8:1; serie 3, 2,7:1 claro y 5,5:1 oscuro. La serie 3 en claro queda bajo 3:1. No impide comprender: cada reparto lista categoría, kWh y porcentaje en texto, y los `canvas` tienen `role=img` con nombre. Se mantuvo el token para no alterar la identidad; oscurecerlo sería mejora, no requisito.

## Límites

- Lector de pantalla y dispositivo físico: no probados.
- Diálogos de equipo, importación y emisión gerencial: nombres por axe; recorrido completo de teclado no repetido en ellos. Comparten `MatDialog` con los probados.
- Con `rem` al doble, sin zoom de navegador (fuente muy grande del navegador): sin solapes medidos, pero chips de altura fija recortan texto, «Plataforma» se corta, título largo se trunca y aparece scroll horizontal interno a 1024. No es incumplimiento AA por G142; arreglarlo exigiría overrides de Material fuera de alcance.
- `reduced-motion` no comprobado.
- T043: A-04 repetido en toda la matriz, sin etiquetas recortadas. A-03 medido con Tab real en tablero, sesiones y usuarios, no en cada ruta.
