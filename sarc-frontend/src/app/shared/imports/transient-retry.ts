import { HttpErrorResponse, HttpResourceRef } from '@angular/common/http';
import { computed, effect, Signal, signal, untracked } from '@angular/core';

/** Intentos automaticos antes de dejar solo la accion manual: 1x, 2x, 4x y 8x la espera base. */
const MAX_ATTEMPTS = 4;

/** Reintento automatico de una consulta que se sondea. */
export interface TransientRetry {
  /** Fallo transitorio y aun quedan intentos automaticos. */
  readonly retrying: Signal<boolean>;
  /** Vuelve a habilitar los intentos (p. ej. tras un reintento manual). */
  reset(): void;
}

/**
 * Tras un fallo transitorio (sin red o 5xx) vuelve a consultar con espera creciente, para que un
 * corte breve no detenga el seguimiento de un lote. Los errores 4xx no se reintentan. Debe
 * llamarse en un contexto de inyeccion.
 */
export function retryTransientFailures(
  resource: HttpResourceRef<unknown>,
  baseDelayMs: number,
): TransientRetry {
  const attempts = signal(0);

  effect(() => {
    if (resource.hasValue()) {
      untracked(() => attempts.set(0));
    }
  });
  // Temporizador externo: efecto legitimo, se cancela si la consulta cambia antes de vencer.
  effect((onCleanup) => {
    if (!isTransient(resource.error())) {
      return;
    }
    const done = untracked(attempts);
    if (done >= MAX_ATTEMPTS) {
      return;
    }
    const timer = setTimeout(
      () => {
        attempts.set(done + 1);
        resource.reload();
      },
      baseDelayMs * 2 ** done,
    );
    onCleanup(() => clearTimeout(timer));
  });

  return {
    retrying: computed(() => isTransient(resource.error()) && attempts() < MAX_ATTEMPTS),
    reset: () => attempts.set(0),
  };
}

function isTransient(error: unknown): boolean {
  return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}
