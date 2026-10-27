import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject } from '@angular/core';

/** Cada cuanto se actualiza una pantalla de consulta mientras esta a la vista. */
export const AUTO_REFRESH_MS = 60_000;

/**
 * Mantiene al dia una pantalla de consulta con los cambios de otros usuarios (RN-G-014): recarga
 * al volver a la pestaña y periodicamente mientras esta visible; oculta, no consulta. Llamar en un
 * contexto de inyeccion; se detiene al destruirse quien la llamo.
 */
export function refreshWhileVisible(reload: () => void, intervalMs = AUTO_REFRESH_MS): void {
  const document = inject(DOCUMENT);
  let timer: ReturnType<typeof setInterval> | undefined;

  const start = () => {
    timer ??= setInterval(reload, intervalMs);
  };
  const stop = () => {
    clearInterval(timer);
    timer = undefined;
  };
  const onVisibility = () => {
    if (document.visibilityState === 'visible') {
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
