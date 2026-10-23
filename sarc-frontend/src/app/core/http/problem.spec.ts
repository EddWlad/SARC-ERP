import { HttpErrorResponse } from '@angular/common/http';

import { toProblem } from './problem';

describe('toProblem', () => {
  it('reads code, detail, traceId and fieldErrors from a problem+json body', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        code: 'VALIDATION_FAILED',
        detail: 'Datos de entrada invalidos',
        traceId: 'trace-1',
        fieldErrors: [{ field: 'email', message: 'requerido' }],
      },
    });

    expect(toProblem(error)).toEqual({
      status: 400,
      code: 'VALIDATION_FAILED',
      detail: 'Datos de entrada invalidos',
      traceId: 'trace-1',
      fieldErrors: [{ field: 'email', message: 'requerido' }],
    });
  });

  it('falls back to a readable message when the body is not a problem', () => {
    const problem = toProblem(new HttpErrorResponse({ status: 403, error: 'Forbidden' }));

    expect(problem.code).toBe('HTTP_403');
    expect(problem.detail).toBe('No tiene permisos para esta operación.');
    expect(problem.fieldErrors).toEqual([]);
  });

  it('reports a network error when the server is unreachable', () => {
    const problem = toProblem(new HttpErrorResponse({ status: 0 }));

    expect(problem.code).toBe('NETWORK_ERROR');
    expect(problem.detail).toContain('No se pudo conectar');
  });

  it('handles non HTTP errors', () => {
    expect(toProblem(new Error('boom')).code).toBe('UNEXPECTED');
  });
});
