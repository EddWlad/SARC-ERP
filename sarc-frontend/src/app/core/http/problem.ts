import { HttpErrorResponse } from '@angular/common/http';

export interface FieldError {
  readonly field: string;
  readonly message: string;
}

/** Error normalizado a partir del problem+json (RFC 9457) del backend. */
export interface Problem {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
  readonly traceId?: string;
  readonly fieldErrors: readonly FieldError[];
}

interface ProblemBody {
  readonly code?: string;
  readonly detail?: string;
  readonly traceId?: string;
  readonly fieldErrors?: readonly FieldError[];
}

const DEFAULT_DETAILS: Readonly<Record<number, string>> = {
  0: 'No se pudo conectar con el servidor. Revise su conexión.',
  401: 'Su sesión no es válida. Ingrese nuevamente.',
  403: 'No tiene permisos para esta operación.',
  404: 'El recurso solicitado no existe.',
  500: 'Error interno del servidor.',
};

export function toProblem(error: unknown): Problem {
  if (!(error instanceof HttpErrorResponse)) {
    return {
      status: 0,
      code: 'UNEXPECTED',
      detail: 'Ocurrió un error inesperado.',
      fieldErrors: [],
    };
  }
  const body = isProblemBody(error.error) ? error.error : {};
  return {
    status: error.status,
    code: body.code ?? (error.status === 0 ? 'NETWORK_ERROR' : `HTTP_${error.status}`),
    detail: body.detail ?? DEFAULT_DETAILS[error.status] ?? 'La operación no se pudo completar.',
    traceId: body.traceId,
    fieldErrors: body.fieldErrors ?? [],
  };
}

function isProblemBody(value: unknown): value is ProblemBody {
  return typeof value === 'object' && value !== null && ('code' in value || 'detail' in value);
}
