import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from './role';
import { SessionStore } from './session-store';

export const LOGIN_PATH = '/login';
export const CHANGE_PASSWORD_PATH = '/cambiar-clave';
export const HOME_PATH = '/inicio';

/** Zona privada: exige sesion y que la clave ya no sea temporal. */
export const authenticatedGuard: CanActivateFn = (_route, state) => {
  const session = inject(SessionStore);
  const router = inject(Router);
  if (!session.isAuthenticated()) {
    return router.createUrlTree([LOGIN_PATH], { queryParams: { returnUrl: state.url } });
  }
  return session.mustChangePassword() ? router.createUrlTree([CHANGE_PASSWORD_PATH]) : true;
};

/** Pantalla de cambio de clave: solo con sesion. */
export const passwordChangeGuard: CanActivateFn = () => {
  const session = inject(SessionStore);
  return session.isAuthenticated() || inject(Router).createUrlTree([LOGIN_PATH]);
};

/** Login: si ya hay sesion se envia a su destino. */
export const guestGuard: CanActivateFn = () => {
  const session = inject(SessionStore);
  if (!session.isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree([
    session.mustChangePassword() ? CHANGE_PASSWORD_PATH : HOME_PATH,
  ]);
};

/** Restringe una ruta a ciertos roles. La autorizacion real la aplica el backend. */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => inject(SessionStore).hasAnyRole(roles) || inject(Router).createUrlTree([HOME_PATH]);
}
