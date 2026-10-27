# SARC frontend

SPA Angular 22 del Sistema Administrativo de Reportes de Carga. Consume el backend de `../sarc-bakend`.

## Requisitos

- Node 24 y npm 11.
- Backend corriendo en `http://localhost:8080` (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` en `sarc-bakend`).

## Comandos

```bash
npm install
npm start          # http://localhost:4200, con proxy de /api al backend (proxy.conf.json)
npm test           # pruebas unitarias (Vitest)
npm run lint       # ESLint (angular-eslint)
npm run format     # Prettier
npm run build      # build de produccion en dist/
npm run api:types  # regenera los tipos desde ../sarc-bakend/docs/api/openapi.json
```

En desarrollo el proxy hace que navegador, cookies de sesion y CSRF sean del mismo origen. En produccion Nginx sirve el build y reenvia `/api` al backend (TRD seccion 17).

## Estructura

```
src/app/
  core/            api/generated (tipos del contrato OpenAPI, no editar), auth, http (y refresco
                   automatico de pantallas de consulta), notificaciones, tema, i18n, archivos (descargas),
                   time (dia y hora de operacion en America/Guayaquil)
  layout/          shell (sidebar + barra superior) y menu de navegacion
  shared/ui/       componentes genericos (dialogo de confirmacion, graficas Chart.js en ui/chart), deteccion
                   de telefono, guardia de cambios sin guardar en dialogos y parciales SCSS de listados,
                   dialogos, etiquetas y tableros
  shared/imports/  ciclo comun de importaciones: historial, vista previa, sondeo con reintento y dialogo
  shared/format/   cifras con Intl (conteos, kWh, porcentajes)
  shared/vocabulary/  vocabulario comun entre modulos (redes, tipos de infraestructura)
  design-system/   tokens, tema Material 3 claro/oscuro y estilos base
  features/<modulo>/
    domain/        tipos y reglas del modulo
    data-access/   llamadas HTTP
    application/   estado y casos de uso (facades con Signals)
    presentation/  paginas y componentes
    <modulo>.routes.ts  rutas del modulo, cargadas con loadChildren
```

Reglas principales:

- Los tipos de requests y responses salen del contrato generado (`core/api/generated`); no se escriben a mano.
- Los componentes no llaman a `HttpClient`: usan facades (`application`) o servicios de `core`. ESLint
  hace cumplir las capas (`presentation -> application -> data-access -> domain`) y que un modulo no
  importe el interior de otro.
- Fechas y horas siempre en hora de Ecuador (`DatePipe` con `-0500`, `LocalDay` con America/Guayaquil),
  aunque el navegador este en otra zona.
- Los dialogos con formulario usan `guardUnsavedChanges`: Esc, clic fuera y Cancelar piden confirmacion
  si hay cambios. Las pantallas de consulta usan `refreshWhileVisible` para mostrar cambios de otros
  usuarios.
- Formularios con Signal Forms; estado con Signals; rutas de modulos con carga diferida.
- La sesion vive en el servidor (cookie HttpOnly). Angular reenvia la cookie `XSRF-TOKEN` como cabecera `X-XSRF-TOKEN`; al arrancar se llama a `GET /api/v1/auth/me` para obtenerla.
- Los permisos en pantalla (menu, guards) son solo de experiencia de usuario; el backend aplica la autorizacion real.
- Cada modulo nuevo agrega su entrada en `layout/navigation.ts` y una sola ruta `loadChildren` en `app.routes.ts` hacia su `<modulo>.routes.ts`, para que el bundle inicial (presupuesto 500 kB) no crezca con cada modulo.
- Evitar pipes de `@angular/common` (`DecimalPipe`, etc.) que aun no use el bundle inicial: esbuild los lleva al chunk compartido inicial. Para cifras usar `Intl.NumberFormat`.
- El bundle inicial no importa modulos de Material con overlay: `Notifier` carga el snackbar con el primer aviso. Un import estatico de algo que usa overlays desde `core` mete el CDK completo en el arranque.
- Graficas: `shared/ui/chart` (Chart.js, solo los controladores registrados), una serie por grafica y nunca doble eje; colores de serie con los tokens `--sarc-series-*`. Cifras visibles en la leyenda o en una tabla, no solo en el tooltip.
