# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Cinco roles funcionales del PRD (RF-001), todos usuarios reales y activos del sistema, no una
jerarquía de permisos sobre una sola pantalla:

- **Analista de Monitoreo** — carga los Excel de Enerlink y Wallbox, revisa filas inválidas, vincula
  sesiones a equipos y resuelve lecturas con energía por revisar. Sesiones largas, de escritorio.
- **Supervisor y Asistente de Electromovilidad** — mantienen el catálogo de cargadores y conectores:
  altas, reemplazos, carga masiva. Uso frecuente, por lotes.
- **Gerente de Electromovilidad** — consulta tablero e informes para decidir y reportar. Uso corto y
  periódico, concentrado en el cierre de mes.
- **Administrador** — cuentas, roles, restablecimientos de contraseña. Uso esporádico.

Los permisos en pantalla (menú, guards) son solo experiencia de usuario; el backend aplica la
autorización real.

## Product Purpose

Sistema Administrativo de Reportes de Carga: consolida las sesiones de carga que llegan de Enerlink
y Wallbox sobre un inventario propio de equipos y conectores, y las convierte en informes emitidos
y archivados. Éxito es que el informe de cierre salga sin que nadie reconstruya el dato a mano en
Excel.

## Positioning

El producto termina en un **informe listo para presentar**: PDF y Excel emitidos, archivados y
atribuibles, no una consulta que el usuario debe interpretar o exportar. Todo lo anterior —
importación, vinculación, revisión de energía, correcciones — existe para que ese documento sea
defendible ante gerencia y auditoría. Una pantalla que muestre números correctos pero no deje el
informe emitido no cumple el propósito.

## Operating Context

- Dos fuentes externas de datos, en Excel: Enerlink y Wallbox (muestras reales en
  `../documents/informes_de_enerlink_wallbox/`). En un Excel solo la primera hoja cuenta como
  entrada (D09).
- Plantilla de carga masiva de cargadores en `../documents/plantillas/`.
- Ciclo real de trabajo: importar lote, ver vista previa, confirmar, resolver lo pendiente
  (sin vínculo, energía por revisar, fuente no aprobada), emitir informe.
- Fechas y horas siempre en hora de Ecuador (America/Guayaquil, `-0500`), aunque el navegador esté
  en otra zona.
- El período ejecutivo inicial es el mes en curso, incluido hoy (D13).
- Varios usuarios trabajan a la vez: las pantallas de consulta se refrescan mientras están visibles.

## Capabilities and Constraints

Módulos implementados: autenticación y cambio de clave, inicio, inventario (equipos, tipos de
conector, carga masiva), consumo (tablero, sesiones, importaciones), reportes emitidos, usuarios.

Reglas de negocio no negociables, en `../documents/Reglas que no se negocian en SARC.txt`
(D01–D14) y en las especificaciones de `../specs/`. El PRD manda en reglas de negocio y el TRD en
lo técnico; ninguna regla vive solo en el código. Las que más tocan la interfaz:

- Una sesión sobre 1.000 kWh no se rechaza: queda como «Energía por revisar», fuera de indicadores,
  y requiere decisión humana con justificación, reversible y con historial (D10, spec 003).
- Los estados de una sesión son categorías excluyentes y en este orden: prueba, pendiente de
  vínculo, revisión energética, aprobación de fuente. El filtro energético la encuentra aunque su
  estado principal sea otro.
- El Top 5 por energía incluye a todos los empatados del quinto puesto, lo indica en la presentación
  y numera con posición repetida y salto (1, 2, 3, 4, 4, 4, 7) (D14, spec 003).
- El histórico y los informes nuevos usan el nombre y código actuales del equipo (D12).
- Reemplazo de equipo es uno a uno (D07). El UID/PUK se conserva literalmente (D04).
- Una coincidencia por normalización de identidad externa autoriza la vinculación automática (D05).
- Contraseña temporal utilizable 60 minutos (D02). El correo de una cuenta puede cambiar
  conservando la misma cuenta (D01). Un administrador puede autodesactivarse si queda otro
  administrador activo (D03).

Restricciones técnicas vigentes: Angular 22 con Signals y Signal Forms, Angular Material 3,
`@lucide/angular`; capas por módulo (`presentation -> application -> data-access -> domain`)
impuestas por ESLint; tipos de request y response generados del contrato OpenAPI del backend;
sesión en cookie HttpOnly con CSRF por cabecera. Presupuesto de bundle inicial de 500 kB tratado
como límite real, no como advertencia: los módulos de Material con overlay y los pipes de
`@angular/common` se cargan en la pantalla que los usa. Textos de interfaz en español (es-EC) con
tildes; código e identificadores en inglés.

Pendiente de decidir por producto: no está definido qué pasa con la operación en móvil más allá de
consultar; tampoco hay un requisito de accesibilidad institucional confirmado (ver más abajo).

## Brand Commitments

Los logos de **AUTOEASTERN** y **EOS** (`../documents/logos/AUTOEASTERN.jpg`,
`../documents/logos/EOSbrand.png`) y sus colores son compromiso de marca y deben respetarse.

Hoy la aplicación no los usa: `src/app/design-system/_theme.scss` declara explícitamente una paleta
neutra provisional en espera de la marca de EOS Tech / Autoeastern, y `public/` solo trae
`favicon.ico`. Aplicar esa identidad es trabajo pendiente, no una decisión ya tomada.

## Evidence on Hand

- PRD v1.1 y TRD v1.0 en `../documents/` (PDF).
- Auditoría de 14 decisiones de producto implícitas, con su resolución, en
  `../documents/auditoria-decisiones.md` y el archivo de reglas no negociables.
- Especificaciones con escenarios de aceptación en `../specs/001-codigo-uso-kia/`,
  `../specs/002-vinculacion-enerlink-sesiones/`, `../specs/003-revision-energia-ranking-empates/`.
- Archivos reales de Enerlink y Wallbox, y plantilla de carga masiva, en `../documents/`.
- Backend con su contrato OpenAPI en `../sarc-bakend/docs/api/openapi.json`.
- No hay testimonios, clientes, benchmarks, precios ni métricas de adopción: ningún trabajo futuro
  debe inventarlos.

## Product Principles

1. **El informe es el entregable.** Cada pantalla se juzga por lo cerca que deja un informe emitido
   y defendible, no por lo que muestra.
2. **Nada suma sin cumplir todos los controles.** Un número oficial supone equipo identificado,
   fuente aprobada, energía decidida y sesión no marcada como prueba; la interfaz dice qué falta,
   nunca lo esconde.
3. **Lo dudoso se conserva, no se descarta.** Un dato fuera de rango se registra aparte y espera una
   decisión humana con justificación y historial reversible.
4. **La decisión humana queda escrita.** Quién decidió, cuándo y por qué es parte del dato y debe
   poder verse donde se ve el dato.
5. **La regla vive en la especificación.** La interfaz refleja PRD, TRD y specs; no introduce ni
   suaviza reglas de negocio por su cuenta.

## Accessibility & Inclusion

Las instrucciones del repositorio exigen pasar AXE y los mínimos WCAG AA (foco, contraste, ARIA), y
ese piso se mantiene. No se ha confirmado un requisito institucional ni una necesidad de usuario
específica por encima de eso.

Contexto de dispositivo confirmado: **escritorio primero**; el móvil solo debe permitir consultar
tablero e informes, no importar. Hay equipos y redes corporativas limitadas — navegadores antiguos,
pantallas de 1366x768, conexión lenta — y el diseño debe seguir funcionando ahí.
