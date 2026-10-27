import { DATE_PIPE_DEFAULT_OPTIONS, registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeEsEC from '@angular/common/locales/es-EC';
import {
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';

import { routes } from './app.routes';
import { SessionStore } from './core/auth/session-store';
import { sessionExpiredInterceptor } from './core/http/session-expired-interceptor';
import { ThemeStore } from './core/theme/theme-store';
import { OPERATING_UTC_OFFSET } from './core/time/local-day';

registerLocaleData(localeEsEC);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withViewTransitions()),
    // Incluye la proteccion XSRF de Angular: lee la cookie XSRF-TOKEN y envia X-XSRF-TOKEN.
    provideHttpClient(withInterceptors([sessionExpiredInterceptor])),
    { provide: LOCALE_ID, useValue: 'es-EC' },
    // Fechas y horas siempre en hora de Ecuador, aunque el navegador este en otra zona.
    { provide: DATE_PIPE_DEFAULT_OPTIONS, useValue: { timezone: OPERATING_UTC_OFFSET } },
    provideAppInitializer(() => {
      inject(ThemeStore);
      return inject(SessionStore).refresh();
    }),
  ],
};
