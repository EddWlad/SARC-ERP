import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth-guards';
import { UPTIME_EDITORS } from './domain/uptime-permissions';

/** Rutas de `/uptime` (RF-004). Cada historia registra aqui sus paginas. */
export const UPTIME_ROUTES: Routes = [
  {
    path: '',
    title: 'Uptime | SARC',
    loadComponent: () => import('./presentation/uptime-page/uptime-page').then((m) => m.UptimePage),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tablero' },
      {
        path: 'tablero',
        title: 'Disponibilidad | SARC',
        loadComponent: () =>
          import('./presentation/dashboard-page/dashboard-page').then((m) => m.DashboardPage),
      },
      {
        path: 'captura',
        title: 'Captura de uptime | SARC',
        canActivate: [roleGuard(...UPTIME_EDITORS)],
        loadComponent: () =>
          import('./presentation/capture-page/capture-page').then((m) => m.CapturePage),
      },
      {
        path: 'revision',
        title: 'Revisión de uptime | SARC',
        canActivate: [roleGuard(...UPTIME_EDITORS)],
        loadComponent: () =>
          import('./presentation/review-page/review-page').then((m) => m.ReviewPage),
      },
      {
        path: 'importaciones',
        title: 'Importaciones de uptime | SARC',
        canActivate: [roleGuard(...UPTIME_EDITORS)],
        loadComponent: () =>
          import('./presentation/imports-page/imports-page').then((m) => m.UptimeImportsPage),
      },
      {
        path: 'importaciones/:id',
        title: 'Lote de uptime | SARC',
        canActivate: [roleGuard(...UPTIME_EDITORS)],
        loadComponent: () =>
          import('./presentation/import-batch-page/import-batch-page').then(
            (m) => m.UptimeImportBatchPage,
          ),
      },
      {
        path: 'vinculacion',
        title: 'Vinculación de uptime | SARC',
        canActivate: [roleGuard(...UPTIME_EDITORS)],
        loadComponent: () =>
          import('./presentation/pending-links-page/pending-links-page').then(
            (m) => m.PendingLinksPage,
          ),
      },
      {
        path: 'mediciones/:id',
        title: 'Medición de uptime | SARC',
        loadComponent: () =>
          import('./presentation/measurement-page/measurement-page').then((m) => m.MeasurementPage),
      },
    ],
  },
];
