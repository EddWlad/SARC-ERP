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
  /** Datos propios del error, p. ej. `equipmentId` del equipo que ya usa un identificador. */
  readonly properties: Readonly<Record<string, unknown>>;
}

interface ProblemBody {
  readonly code?: string;
  readonly detail?: string;
  readonly traceId?: string;
  readonly fieldErrors?: readonly FieldError[];
}

/** Campos estandar de RFC 9457 y del backend; el resto son propiedades del error. */
const STANDARD_KEYS = new Set([
  'type',
  'title',
  'status',
  'detail',
  'instance',
  'code',
  'traceId',
  'fieldErrors',
]);

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
      properties: {},
    };
  }
  const body = isProblemBody(error.error) ? error.error : {};
  return {
    status: error.status,
    code: body.code ?? (error.status === 0 ? 'NETWORK_ERROR' : `HTTP_${error.status}`),
    detail: body.detail ?? DEFAULT_DETAILS[error.status] ?? 'La operación no se pudo completar.',
    traceId: body.traceId,
    fieldErrors: body.fieldErrors ?? [],
    properties: Object.fromEntries(Object.entries(body).filter(([key]) => !STANDARD_KEYS.has(key))),
  };
}

/** Igual que `toProblem`, para peticiones de archivos (`responseType: 'blob'`): el error llega como Blob. */
export async function toProblemFromBlob(error: unknown): Promise<Problem> {
  if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
    let body: unknown = null;
    try {
      body = JSON.parse(await error.error.text());
    } catch {
      // Cuerpo que no es JSON: se usa el mensaje por defecto del estado HTTP.
    }
    return toProblem(new HttpErrorResponse({ status: error.status, error: body }));
  }
  return toProblem(error);
}

function isProblemBody(value: unknown): value is ProblemBody {
  return typeof value === 'object' && value !== null && ('code' in value || 'detail' in value);
}
