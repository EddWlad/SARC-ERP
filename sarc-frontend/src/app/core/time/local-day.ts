import { DOCUMENT } from '@angular/common';
import { inject, Service, signal } from '@angular/core';

/**
 * Dia local del navegador como signal. Cambia a medianoche y al volver a la pestaña, para que una
 * pantalla abierta de un dia para otro (el tablero de consumo se mira a diario) pida "hoy" de nuevo.
 */
@Service()
export class LocalDay {
  private readonly current = signal(startOfToday());
  readonly today = this.current.asReadonly();

  constructor() {
    const document = inject(DOCUMENT);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        this.refresh();
      }
    });
    this.scheduleMidnight();
  }

  private scheduleMidnight(): void {
    const now = new Date();
    const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
    setTimeout(() => {
      this.refresh();
      this.scheduleMidnight();
    }, midnight.getTime() - now.getTime());
  }

  private refresh(): void {
    const day = startOfToday();
    if (day.getTime() !== this.current().getTime()) {
      this.current.set(day);
    }
  }
}

function startOfToday(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}
