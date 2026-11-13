import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  KiaPointResponse,
  WindowDetailResponse,
  WindowResponse,
} from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { IntermittencyApi } from '../data-access/intermittency-api';
import { WindowState } from '../domain/intermittency-labels';
import { addDays } from '../domain/intermittency-time';

const DEFAULT_PAGE_SIZE = 20;

/**
 * Historial de ventanas: filtros por punto y rango de dias, estados (solo los editores ven mas que las
 * confirmadas) y paginacion. Quien no edita recibe siempre solo las confirmadas, pida lo que pida (FR-005).
 */
@Injectable()
export class WindowsFacade {
  private readonly api = inject(IntermittencyApi);
  private ticket = 0;

  readonly point = signal<KiaPointResponse | null>(null);
  /** Primer y ultimo dia incluidos, aaaa-mm-dd; vacios sin limite. */
  readonly firstDay = signal('');
  readonly lastDay = signal('');
  readonly states = signal<readonly WindowState[]>([]);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  readonly items = signal<WindowResponse[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly empty = computed(() => !this.loading() && !this.error() && this.items().length === 0);

  readonly detail = signal<WindowDetailResponse | null>(null);
  readonly detailError = signal('');

  async load(): Promise<void> {
    const ticket = ++this.ticket;
    this.loading.set(true);
    this.error.set('');
    try {
      const page = await firstValueFrom(
        this.api.listWindows({
          ...(this.point() ? { equipmentIds: [this.point()!.id] } : {}),
          states: this.states(),
          from: this.firstDay() || undefined,
          // El fin de la API es exclusivo: el ultimo dia elegido se incluye sumando uno.
          to: this.lastDay() ? addDays(this.lastDay(), 1) : undefined,
          page: this.pageIndex(),
          size: this.pageSize(),
        }),
      );
      // Una respuesta tardia de un filtro anterior no pisa la actual.
      if (ticket !== this.ticket) return;
      this.items.set(page.content);
      this.total.set(page.totalElements);
    } catch (error) {
      if (ticket !== this.ticket) return;
      this.items.set([]);
      this.total.set(0);
      this.error.set(toProblem(error).detail);
    } finally {
      if (ticket === this.ticket) this.loading.set(false);
    }
  }

  /** Cambia los filtros y vuelve a la primera pagina. */
  async filter(patch: {
    point?: KiaPointResponse | null;
    firstDay?: string;
    lastDay?: string;
    states?: readonly WindowState[];
  }): Promise<void> {
    if (patch.point !== undefined) this.point.set(patch.point);
    if (patch.firstDay !== undefined) this.firstDay.set(patch.firstDay);
    if (patch.lastDay !== undefined) this.lastDay.set(patch.lastDay);
    if (patch.states !== undefined) this.states.set(patch.states);
    this.pageIndex.set(0);
    await this.load();
  }

  async changePage(pageIndex: number, pageSize: number): Promise<void> {
    this.pageIndex.set(pageIndex);
    this.pageSize.set(pageSize);
    await this.load();
  }

  /** Detalle con el historial de revisiones (solo para editores); el servidor responde 404 al resto. */
  async openDetail(id: string): Promise<WindowDetailResponse | null> {
    this.detailError.set('');
    try {
      const detail = await firstValueFrom(this.api.windowDetail(id));
      this.detail.set(detail);
      return detail;
    } catch (error) {
      this.detail.set(null);
      this.detailError.set(toProblem(error).detail);
      return null;
    }
  }
}
