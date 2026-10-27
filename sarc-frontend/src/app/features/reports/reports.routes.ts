import { Routes } from '@angular/router';

/** Rutas de `/reportes` (RF-RPT-08). Se cargan al entrar al modulo, no al iniciar la aplicacion. */
export const REPORTS_ROUTES: Routes = [
  {
    path: '',
    title: 'Reportes emitidos | SARC',
    loadComponent: () =>
      import('./presentation/issued-reports-page/issued-reports-page').then(
        (m) => m.IssuedReportsPage,
      ),
  },
];
