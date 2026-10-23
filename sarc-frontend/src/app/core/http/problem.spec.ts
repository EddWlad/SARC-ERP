import { HttpErrorResponse } from '@angular/common/http';

import { toProblem, toProblemFromBlob } from './problem';

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
      properties: {},
    });
  });

  it('keeps the error specific properties apart from the standard fields', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: {
        type: 'about:blank',
        title: 'Conflict',
        status: 409,
        code: 'EQUIPMENT_DELETED_EXISTS',
        detail: 'Ya existe un equipo eliminado',
        equipmentId: 'eq-1',
      },
    });

    expect(toProblem(error).properties).toEqual({ equipmentId: 'eq-1' });
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

  it('reads the problem of a file request from its Blob body', async () => {
    const body = new Blob([JSON.stringify({ code: 'REPORT_TOO_LARGE', detail: 'Demasiados' })], {
      type: 'application/problem+json',
    });

    const problem = await toProblemFromBlob(new HttpErrorResponse({ status: 422, error: body }));

    expect(problem.code).toBe('REPORT_TOO_LARGE');
    expect(problem.detail).toBe('Demasiados');
  });
});
