import { HttpErrorResponse } from '@angular/common/http';

import { isConnectionProblem, RefreshStatus } from './auto-refresh';

describe('RefreshStatus', () => {
  it('empieza sin actualizacion y sin aviso', () => {
    const status = new RefreshStatus();
    expect(status.lastUpdatedAt()).toBeNull();
    expect(status.connectionLost()).toBe(false);
  });

  it('una respuesta correcta anota la hora y apaga el aviso', () => {
    const status = new RefreshStatus();
    status.markFailure(new HttpErrorResponse({ status: 0 }));
    expect(status.connectionLost()).toBe(true);

    const now = new Date('2026-11-21T15:30:00Z');
    status.markSuccess(now);
    expect(status.lastUpdatedAt()).toBe(now);
    expect(status.connectionLost()).toBe(false);
  });

  it('una falla de red avisa sin borrar la ultima hora', () => {
    const status = new RefreshStatus();
    const now = new Date('2026-11-21T15:30:00Z');
    status.markSuccess(now);
    status.markFailure(new HttpErrorResponse({ status: 503 }));
    expect(status.connectionLost()).toBe(true);
    expect(status.lastUpdatedAt()).toBe(now);
  });

  it('un error del servidor que no es de conectividad no avisa de perdida de conexion', () => {
    const status = new RefreshStatus();
    status.markFailure(new HttpErrorResponse({ status: 422 }));
    expect(status.connectionLost()).toBe(false);
    status.markFailure(new Error('otro'));
    expect(status.connectionLost()).toBe(false);
  });

  it('reconoce los estados de conectividad', () => {
    for (const code of [0, 502, 503, 504]) {
      expect(isConnectionProblem(new HttpErrorResponse({ status: code }))).toBe(true);
    }
    for (const code of [400, 401, 404, 409, 500]) {
      expect(isConnectionProblem(new HttpErrorResponse({ status: code }))).toBe(false);
    }
    expect(isConnectionProblem(undefined)).toBe(false);
  });
});
