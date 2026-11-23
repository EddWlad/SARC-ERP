import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, inject, Service, signal } from '@angular/core';

/** Cada cuanto se actualiza una pantalla de consulta mientras esta a la vista. */
export const AUTO_REFRESH_MS = 60_000;
/**
 * Tiempo sin teclado, raton, toque ni desplazamiento tras el cual se deja de refrescar. Cada
 * consulta renueva la sesion del servidor: sin este corte, una pantalla abierta impediria el cierre
 * por inactividad (RF-001.10).
 */
export const HUMAN_IDLE_MS = 5 * 60_000;

const HUMAN_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart', 'scroll'] as const;

/** Ultima interaccion de la persona con la aplicacion; las consultas automaticas no cuentan. */
@Service()
export class HumanActivity {
  private last = Date.now();

  constructor() {
    const document = inject(DOCUMENT);
    const mark = () => this.mark();
    for (const type of HUMAN_EVENTS) {
      document.addEventListener(type, mark, { capture: true, passive: true });
    }
  }

  mark(): void {
    this.last = Date.now();
  }

  /** Hubo interaccion en los ultimos {@link HUMAN_IDLE_MS}. */
  isRecent(): boolean {
    return Date.now() - this.last < HUMAN_IDLE_MS;
  }
}

/**
 * Mantiene al dia una pantalla de consulta con los cambios de otros usuarios (RN-G-014): recarga
 * al volver a la pestaña y periodicamente mientras esta visible y la persona sigue activa. Oculta o
 * sin actividad, no consulta, para que la sesion pueda vencer por inactividad. Llamar en un contexto
 * de inyeccion; se detiene al destruirse quien la llamo.
 */
export function refreshWhileVisible(reload: () => void, intervalMs = AUTO_REFRESH_MS): void {
  const document = inject(DOCUMENT);
  const activity = inject(HumanActivity);
  let timer: ReturnType<typeof setInterval> | undefined;

  const start = () => {
    timer ??= setInterval(() => {
      if (activity.isRecent()) {
        reload();
      }
    }, intervalMs);
  };
  const stop = () => {
    clearInterval(timer);
    timer = undefined;
  };
  const onVisibility = () => {
    if (document.visibilityState === 'visible') {
      // Volver a la pestaña es una accion de la persona.
      activity.mark();
      reload();
      start();
    } else {
      stop();
    }
  };

  document.addEventListener('visibilitychange', onVisibility);
  if (document.visibilityState === 'visible') {
    start();
  }
  inject(DestroyRef).onDestroy(() => {
    stop();
    document.removeEventListener('visibilitychange', onVisibility);
  });
}

/**
 * Estado de la ultima actualizacion de una pantalla de consulta (FR-063): cuando fue la ultima respuesta correcta y si
 * la ultima consulta fallo por falta de conectividad. La pantalla lo muestra para que nadie tome por vigente un dato
 * que no se pudo refrescar. No cambia {@link refreshWhileVisible}: las facades lo actualizan al cargar.
 */
export class RefreshStatus {
  /** Cuando llego la ultima respuesta correcta; nulo si todavia no hubo ninguna. */
  readonly lastUpdatedAt = signal<Date | null>(null);
  /** La ultima consulta no llego al servidor: se muestran los datos de la anterior. */
  readonly connectionLost = signal(false);

  markSuccess(now: Date = new Date()): void {
    this.lastUpdatedAt.set(now);
    this.connectionLost.set(false);
  }

  /** Una falla de red o de pasarela es perdida de conectividad; otro error no lo es (lo explica la pantalla). */
  markFailure(error: unknown): void {
    this.connectionLost.set(isConnectionProblem(error));
  }
}

/** Sin respuesta del servidor (estado 0) o pasarela caida (502, 503, 504). */
export function isConnectionProblem(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    (error.status === 0 || error.status === 502 || error.status === 503 || error.status === 504)
  );
}
