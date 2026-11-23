import { Routes } from '@angular/router';

import { roleGuard } from '../../core/auth/auth-guards';
import { INCIDENT_NUMBERING } from './domain/incident-permissions';

/**
 * Rutas de `/incidencias` (feature 010). Cada historia registra aqui sus paginas: `tickets` y `tickets/:id` son
 * consulta para todos los roles; registrar un ticket es un dialogo del listado. `numeracion` la configura solo el Administrador.
 * `control` es el informe «Control de tickets» (US6), de consulta para todos los roles. Abre en los tickets; el tablero
 * y la gestion de incidencias (US7) se agregan despues.
 */
export const INCIDENTS_ROUTES: Routes = [
  {
    path: '',
    title: 'Incidencias | SARC',
    loadComponent: () =>
      import('./presentation/incidents-page/incidents-page').then((m) => m.IncidentsPage),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tickets' },
      {
        path: 'tickets',
        title: 'Tickets | SARC',
        loadComponent: () =>
          import('./presentation/tickets-page/tickets-page').then((m) => m.TicketsPage),
      },
      {
        path: 'tickets/:id',
        title: 'Ticket | SARC',
        loadComponent: () =>
          import('./presentation/ticket-detail-page/ticket-detail-page').then(
            (m) => m.TicketDetailPage,
          ),
      },
      {
        path: 'tablero',
        title: 'Tablero de incidencias | SARC',
        loadComponent: () =>
          import('./presentation/dashboard-page/dashboard-page').then((m) => m.DashboardPage),
      },
      {
        path: 'control',
        title: 'Control de tickets | SARC',
        loadComponent: () =>
          import('./presentation/control-page/control-page').then((m) => m.ControlPage),
      },
      {
        path: 'numeracion',
        title: 'Numeración de tickets | SARC',
        canActivate: [roleGuard(...INCIDENT_NUMBERING)],
        loadComponent: () =>
          import('./presentation/numbering-page/numbering-page').then((m) => m.NumberingPage),
      },
    ],
  },
];
