import { Routes } from '@angular/router';

import {
  authenticatedGuard,
  guestGuard,
  passwordChangeGuard,
  roleGuard,
} from './core/auth/auth-guards';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Ingresar | SARC',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/presentation/login-page/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'cambiar-clave',
    title: 'Cambiar contraseña | SARC',
    canActivate: [passwordChangeGuard],
    loadComponent: () =>
      import('./features/auth/presentation/change-password-page/change-password-page').then(
        (m) => m.ChangePasswordPage,
      ),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    canActivate: [authenticatedGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      {
        path: 'inicio',
        title: 'Inicio | SARC',
        loadComponent: () =>
          import('./features/home/presentation/home-page/home-page').then((m) => m.HomePage),
      },
      {
        path: 'inventario',
        loadChildren: () =>
          import('./features/inventory/inventory.routes').then((m) => m.INVENTORY_ROUTES),
      },
      {
        path: 'consumo',
        loadChildren: () =>
          import('./features/consumption/consumption.routes').then((m) => m.CONSUMPTION_ROUTES),
      },
      {
        path: 'usuarios',
        title: 'Usuarios | SARC',
        canActivate: [roleGuard('ADMINISTRADOR')],
        loadComponent: () =>
          import('./features/users/presentation/users-page/users-page').then((m) => m.UsersPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
