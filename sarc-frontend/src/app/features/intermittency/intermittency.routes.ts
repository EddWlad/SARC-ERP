import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth-guards';
import { INTERMITTENCY_EDITORS } from './domain/intermittency-permissions';

/**
 * Rutas de `/intermitencias` (feature 008). Cada historia registra aqui sus paginas: `tablero` y `ventanas` son
 * la consulta, para todos los roles; el resto se limita a los editores. Todos abren en el tablero.
 */
export const INTERMITTENCY_ROUTES: Routes = [
  {
    path: '',
    title: 'Intermitencias | SARC',
    loadComponent: () =>
      import('./presentation/intermittency-page/intermittency-page').then(
        (m) => m.IntermittencyPage,
      ),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tablero' },
      {
        path: 'tablero',
        title: 'Tablero de intermitencias | SARC',
        loadComponent: () =>
          import('./presentation/dashboard-page/dashboard-page').then(
            (m) => m.IntermittencyDashboardPage,
          ),
      },
      {
        path: 'registrar',
        title: 'Registrar intermitencias | SARC',
        canActivate: [roleGuard(...INTERMITTENCY_EDITORS)],
        loadComponent: () =>
          import('./presentation/capture-page/capture-page').then((m) => m.CapturePage),
      },
      {
        path: 'importaciones',
        title: 'Importaciones de intermitencias | SARC',
        canActivate: [roleGuard(...INTERMITTENCY_EDITORS)],
        loadComponent: () =>
          import('./presentation/imports-page/imports-page').then(
            (m) => m.IntermittencyImportsPage,
          ),
      },
      {
        path: 'revision',
        title: 'Revisión de intermitencias | SARC',
        canActivate: [roleGuard(...INTERMITTENCY_EDITORS)],
        loadComponent: () =>
          import('./presentation/review-page/review-page').then((m) => m.IntermittencyReviewPage),
      },
      {
        path: 'dias-revisados',
        title: 'Días revisados de intermitencias | SARC',
        canActivate: [roleGuard(...INTERMITTENCY_EDITORS)],
        loadComponent: () =>
          import('./presentation/reviewed-days-panel/reviewed-days-panel').then(
            (m) => m.ReviewedDaysPanel,
          ),
      },
      {
        path: 'ventanas',
        title: 'Ventanas de intermitencias | SARC',
        loadComponent: () =>
          import('./presentation/windows-page/windows-page').then(
            (m) => m.IntermittencyWindowsPage,
          ),
      },
      {
        path: 'importaciones/:id',
        title: 'Lote de intermitencias | SARC',
        canActivate: [roleGuard(...INTERMITTENCY_EDITORS)],
        loadComponent: () =>
          import('./presentation/import-batch-page/import-batch-page').then(
            (m) => m.IntermittencyImportBatchPage,
          ),
      },
    ],
  },
];
