import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { SessionStore } from '../auth/session-store';
import { Notifier } from '../notifications/notifier';
import { sessionExpiredInterceptor } from './session-expired-interceptor';

describe('sessionExpiredInterceptor', () => {
  const authenticated = signal(true);
  const clear = vi.fn(() => authenticated.set(false));
  const navigate = vi.fn(() => Promise.resolve(true));
  const notifyError = vi.fn();
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    authenticated.set(true);
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionExpiredInterceptor])),
        provideHttpClientTesting(),
        { provide: SessionStore, useValue: { isAuthenticated: authenticated, clear } },
        { provide: Router, useValue: { url: '/usuarios', navigate } },
        { provide: Notifier, useValue: { error: notifyError } },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  async function failWith401(url: string): Promise<void> {
    const call = firstValueFrom(http.get(url));
    backend.expectOne(url).flush(null, { status: 401, statusText: 'Unauthorized' });
    await expect(call).rejects.toBeDefined();
  }

  it('clears the session and goes to login when an authenticated request gets 401', async () => {
    await failWith401('/api/v1/users');

    expect(clear).toHaveBeenCalled();
    expect(notifyError).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/usuarios' } });
  });

  it('ignores the expected 401 of the session probe', async () => {
    await failWith401('/api/v1/auth/me');

    expect(clear).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does nothing when the user was already anonymous', async () => {
    authenticated.set(false);

    await failWith401('/api/v1/users');

    expect(navigate).not.toHaveBeenCalled();
  });
});
