import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth-guards';
import { CONSUMPTION_EDITORS } from './domain/consumption-permissions';

/** Rutas de `/consumo` (RF-003). Se cargan al entrar al modulo, no al iniciar la aplicacion. */
export const CONSUMPTION_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./presentation/consumption-page/consumption-page').then((m) => m.ConsumptionPage),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tablero' },
      {
        path: 'tablero',
        title: 'Consumo | SARC',
        loadComponent: () =>
          import('./presentation/dashboard-page/dashboard-page').then((m) => m.DashboardPage),
      },
      {
        path: 'sesiones',
        title: 'Sesiones de carga | SARC',
        loadComponent: () =>
          import('./presentation/sessions-page/sessions-page').then((m) => m.SessionsPage),
      },
      {
        path: 'importaciones',
        canActivate: [roleGuard(...CONSUMPTION_EDITORS)],
        children: [
          {
            path: '',
            title: 'Importaciones | SARC',
            loadComponent: () =>
              import('./presentation/imports-page/imports-page').then((m) => m.ImportsPage),
          },
          {
            path: ':id',
            title: 'Lote de importación | SARC',
            loadComponent: () =>
              import('./presentation/import-batch-page/import-batch-page').then(
                (m) => m.ImportBatchPage,
              ),
          },
        ],
      },
    ],
  },
];
