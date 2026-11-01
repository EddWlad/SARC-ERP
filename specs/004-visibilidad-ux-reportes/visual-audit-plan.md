# Plan de auditoría visual — Feature 004

No es un informe de auditoría ejecutada. Este archivo organiza la evidencia que debe producir implementación/QA. Impeccable está instalado; contexto PRODUCT/DESIGN y sidecar ya se consultaron/sincronizaron. Conservar identidad y fuente normativa; la especificación/PRD prevalecen sobre supuestos de roles o móvil en PRODUCT.

## Inventario obligatorio inicial

Derivar rutas exactas de app.routes.ts, inventory.routes.ts, consumption.routes.ts y reports.routes.ts; añadir los diálogos/componentes invocados. Roles autorizados únicamente.

| Familia | Pantallas/diálogos que registrar |
|---|---|
| Acceso | /login, /cambiar-clave; contraseña inválida/temporal y errores |
| Shell/Inicio | /inicio, sidebar/drawer, menú, usuario, cambio de tema, salto al contenido |
| Usuarios | /usuarios; alta/edición, roles, restablecimiento temporal, confirmación |
| Inventario | /inventario, /inventario/:id; equipo nuevo/editar/reemplazo y conectores/identidades/horarios |
| Catálogo conector | /inventario/tipos-de-conector; nuevo/editar, ayuda multilínea, errores |
| Importación inventario | /inventario/importaciones, /inventario/importaciones/:id; carga, vista previa, confirmación/descarte |
| Consumo | /consumo/tablero y /consumo/sesiones; filtros, tablas/tarjetas, ranking extenso y evidencia/revisión energética |
| Importación consumo | /consumo/importaciones y /consumo/importaciones/:id; carga/pegado, clasificación y confirmación |
| Emitidos | /reportes; listado, descarga, denegación y nueva emisión |
| Compartidos | Confirmación, cambios sin guardar, avisos, errores, paginación y botones ocupados |

Cada pantalla/estado aplicable: 360,390,768,1024,1440px × claro/oscuro (diez combinaciones); registrar también alto, zoom y navegador. No sustituir diez combinaciones por un único escritorio/móvil. Estados: normal,carga,vacío,error servidor,validación/ayuda,contenido largo,ocupado,confirmación,acceso limitado y resultados extensos. Marcar N/A justificadamente, nunca asumir que no existe un estado porque no se probó.

## Procedimiento

1. Iniciar fixture/BD/almacenamiento aislados según quickstart; datos sintéticos realistas con nombres largos, energía ausente/0, cinco categorías y empates. Solo QA, nunca datos inventados en producto/informes reales.
2. Ejecutar `$impeccable audit` siguiendo `.agents/skills/impeccable/reference/audit.md`; usar detector según ayuda de la versión instalada para origen/archivo/navegador disponible. No inventar flags ni repetir context en la misma sesión.
3. Registrar señales deterministas separadas de juicio visual. Verificar cada hallazgo en navegador; descartar falsos positivos con motivo. Cinco dimensiones: accesibilidad,rendimiento,tema,responsive,integridad. No asignar puntuación a dimensiones no comprobadas.
4. Capturar defecto antes de editar; medir solapamiento/overflow y comprobar asociación texto/control, foco y acción afectada.
5. Aplicar corrección mínima por API pública/tokens/componente reutilizable; revisar puntos consumidores afectados y flujos de guardado.
6. Repetir matriz correspondiente y capturar después, mismo rol/estado/ancho/tema. Reauditar y cerrar solo con prueba.

## Primeros candidatos, no defectos adicionales confirmados

- `features/inventory/presentation/connector-type-dialog/connector-type-dialog.ts`: hint largo, fixed subscript de Material y gap8; medir tamaño de ayuda y comienzo del siguiente control.
- `features/inventory/presentation/external-identity-dialog/external-identity-dialog.ts`: patrón de ayuda/gap parecido.
- Formulario de equipo: rejilla dos columnas y gap8; ayudas/errores/anchos reales y diálogo760/95vw.
- Top de consumo: nombres con ellipsis; verificar acceso al nombre completo sin hover y todos los empatados. No eliminar filas ni la tabla para caber.
- Revisión de nuevos estados de acceso/reemisión y gráficos nulos; ocupar/cargar/error no debe perder acción ni foco.

No convertir diferencia doctrinal de elevación en rediseño masivo. No tocar cada pantalla si un patrón compartido corrige el defecto confirmado; tampoco aplicar un default global sin medir regresiones/bundle.

## Accesibilidad manual y automática

- Contraste calculado en ambos temas, pares de tokens, indicadores no dependientes solo del color.
- Etiqueta/ayuda/error asociados; contenido largo legible; lectura asistida y orden semántico; tablas/texto equivalentes para gráficos.
- Teclado completo, foco visible/no oculto, abrir/cerrar diálogo, Esc y retorno de foco, acciones alcanzables con altura reducida y formulario dirty.
- Reflujo/ampliación (incluido200% texto y400% zoom cuando aplique), menús/paginación sin acciones perdidas. Scroll horizontal localizado de tabla densa permitido con contexto.
- WCAG2.2AA SC2.5.8: objetivos24×24 CSSpx o excepción verificable;44×44 recomendación de comodidad, no reglaAA universal.
- Reduced motion conserva feedback útil/foco; no modificar animaciones solo porque detector señala reducción global. Distinguir viewport emulado, gesto táctil sintetizado y dispositivo físico.

Axe/detector automáticos no acreditan por sí solos WCAG global. Si no hay herramientas o dispositivos para un criterio, registrar limitación; no declararlo aprobado ni cerrar un incumplimiento confirmado en alcance.

## Evidencia prevista

Crear durante implementación `evidence/visual-audit.md` con inventario completo, matriz de estados y hallazgos. Carpeta de capturas antes/después; resultados automáticos y revisión manual trazables. Campos: ID,rol,ruta,estado,ancho/alto,tema,navegador,pasos,impacto P0–P3,causa,componente,archivos,pruebas y estado final.

Las preferencias P3 sin defecto funcional/coherencia/accesibilidad se separan y no se implementan por defecto. Antes del cierre SC-005/006, todo defecto confirmado dentro del alcance debe estar corregido/verificado. Limitación de prueba no equivale a excepción automática al criterio.
