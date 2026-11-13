import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { KiaPointResponse, WindowResponse } from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { IntermittencyApi } from '../data-access/intermittency-api';

/** Lo que el dialogo de cierre necesita: registrar el fin de una ventana abierta. */
export interface WindowCloser {
  /** Cierra la ventana con el fin dado; falla con el mensaje del servidor. */
  close(window: WindowResponse, endAt: string): Promise<WindowResponse>;
}

/** Ventanas abiertas de un punto y el cierre de cada una. El backend decide el estado resultante. */
@Injectable()
export class OpenWindowsFacade implements WindowCloser {
  private readonly api = inject(IntermittencyApi);
  private current: string | null = null;

  readonly windows = signal<WindowResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');

  async load(point: KiaPointResponse | null): Promise<void> {
    this.current = point?.id ?? null;
    this.error.set('');
    if (!point) {
      this.windows.set([]);
      return;
    }
    this.loading.set(true);
    try {
      const page = await firstValueFrom(
        this.api.listWindows({ equipmentIds: [point.id], states: ['OPEN'], size: 50 }),
      );
      // Una respuesta tardia de otro punto no pisa la del punto actual.
      if (this.current === point.id) this.windows.set(page.content);
    } catch (error) {
      this.windows.set([]);
      this.error.set(toProblem(error).detail);
    } finally {
      this.loading.set(false);
    }
  }

  /** Cierra la ventana con el fin dado; falla con el mensaje del servidor (por ejemplo, un conflicto de version). */
  async close(window: WindowResponse, endAt: string): Promise<WindowResponse> {
    try {
      return await firstValueFrom(
        this.api.closeWindow(window.id, { version: window.version, endAt }),
      );
    } catch (error) {
      throw new Error(toProblem(error).detail, { cause: error });
    }
  }
}
