import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { authenticatedGuard, guestGuard, passwordChangeGuard, roleGuard } from './auth-guards';
import { Role } from './role';
import { SessionStore } from './session-store';

describe('auth guards', () => {
  const authenticated = signal(false);
  const mustChange = signal(false);
  const roles = signal<Role[]>([]);

  beforeEach(() => {
    authenticated.set(false);
    mustChange.set(false);
    roles.set([]);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: SessionStore,
          useValue: {
            isAuthenticated: authenticated,
            mustChangePassword: mustChange,
            hasAnyRole: (wanted: readonly Role[]) => wanted.some((role) => roles().includes(role)),
          },
        },
      ],
    });
  });

  function run(guard: CanActivateFn, url = '/usuarios'): boolean | UrlTree {
    return TestBed.runInInjectionContext(
      () =>
        guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot) as boolean | UrlTree,
    );
  }

  function path(result: boolean | UrlTree): string {
    return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : String(result);
  }

  it('sends anonymous users to login keeping the requested URL', () => {
    expect(path(run(authenticatedGuard))).toBe('/login?returnUrl=%2Fusuarios');
  });

  it('forces a temporary password to be changed before entering the app', () => {
    authenticated.set(true);
    mustChange.set(true);

    expect(path(run(authenticatedGuard))).toBe('/cambiar-clave');
  });

  it('lets an authenticated user with a definitive password in', () => {
    authenticated.set(true);

    expect(run(authenticatedGuard)).toBe(true);
  });

  it('only shows the change password screen to authenticated users', () => {
    expect(path(run(passwordChangeGuard))).toBe('/login');
    authenticated.set(true);
    expect(run(passwordChangeGuard)).toBe(true);
  });

  it('keeps logged in users away from the login screen', () => {
    expect(run(guestGuard)).toBe(true);
    authenticated.set(true);
    expect(path(run(guestGuard))).toBe('/inicio');
  });

  it('restricts routes by role', () => {
    const adminOnly = roleGuard('ADMINISTRADOR');
    roles.set(['ANALISTA']);
    expect(path(run(adminOnly))).toBe('/inicio');
    roles.set(['ANALISTA', 'ADMINISTRADOR']);
    expect(run(adminOnly)).toBe(true);
  });
});
