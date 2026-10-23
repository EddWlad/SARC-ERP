import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { LOGIN_PATH } from '../auth/auth-guards';
import { SessionStore } from '../auth/session-store';
import { Notifier } from '../notifications/notifier';
import { API_BASE } from './api';

const AUTH_ENDPOINTS = [`${API_BASE}/auth/me`, `${API_BASE}/auth/login`];

/**
 * Si el servidor responde 401 a una sesion que el cliente creia vigente (expiro a los 60 min,
 * o fue revocada al cambiar roles o clave), se limpia el estado y se vuelve al login.
 */
export const sessionExpiredInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(SessionStore);
  const router = inject(Router);
  const notifier = inject(Notifier);

  return next(request).pipe(
    catchError((error: unknown) => {
      const expired =
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !AUTH_ENDPOINTS.includes(request.url) &&
        session.isAuthenticated();
      if (expired) {
        session.clear();
        notifier.error('Su sesión terminó. Ingrese nuevamente.');
        void router.navigate([LOGIN_PATH], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
