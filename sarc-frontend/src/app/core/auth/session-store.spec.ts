import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CurrentUser } from './auth-api';
import { SessionStore } from './session-store';

const ADMIN: CurrentUser = {
  id: 'u-1',
  email: 'admin@eos.com',
  fullName: 'Admin Uno',
  roles: ['ADMINISTRADOR', 'ANALISTA'],
  mustChangePassword: false,
};

describe('SessionStore', () => {
  let store: SessionStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(SessionStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('restores the current user from /auth/me', async () => {
    const refreshed = store.refresh();
    http.expectOne('/api/v1/auth/me').flush(ADMIN);
    await refreshed;

    expect(store.isAuthenticated()).toBe(true);
    expect(store.hasAnyRole(['ANALISTA'])).toBe(true);
    expect(store.hasAnyRole(['GERENTE'])).toBe(false);
  });

  it('stays anonymous when there is no session', async () => {
    const refreshed = store.refresh();
    http
      .expectOne('/api/v1/auth/me')
      .flush({ code: 'UNAUTHENTICATED' }, { status: 401, statusText: 'Unauthorized' });
    await refreshed;

    expect(store.isAuthenticated()).toBe(false);
    expect(store.hasAnyRole(['ADMINISTRADOR'])).toBe(false);
  });

  it('stores the user returned by login and exposes the forced password change', async () => {
    const loggedIn = store.login('admin@eos.com', 'secreta');
    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.body).toEqual({ email: 'admin@eos.com', password: 'secreta' });
    request.flush({ ...ADMIN, mustChangePassword: true });
    await loggedIn;

    expect(store.user()?.email).toBe('admin@eos.com');
    expect(store.mustChangePassword()).toBe(true);
  });

  it('clears the user after changing the password because the backend revokes every session', async () => {
    const loggedIn = store.login('admin@eos.com', 'secreta');
    http.expectOne('/api/v1/auth/login').flush(ADMIN);
    await loggedIn;

    const changed = store.changePassword('secreta', 'nueva-clave-segura');
    http
      .expectOne('/api/v1/auth/password/change')
      .flush(null, { status: 204, statusText: 'No Content' });
    await changed;

    expect(store.isAuthenticated()).toBe(false);
  });

  it('asks for a new session (and CSRF cookie) after logging out', async () => {
    const loggedIn = store.login('admin@eos.com', 'secreta');
    http.expectOne('/api/v1/auth/login').flush(ADMIN);
    await loggedIn;

    const loggedOut = store.logout();
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    await Promise.resolve();
    http.expectOne('/api/v1/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await loggedOut;

    expect(store.isAuthenticated()).toBe(false);
  });
});
