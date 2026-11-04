import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import {
  BatchValidationResult,
  Item,
  MeasurementDetailResponse,
} from '../../../core/api/generated';
import { toProblem } from '../../../core/http/problem';
import { UptimeApi } from '../data-access/uptime-api';
import { MeasurementQueue } from '../domain/uptime-labels';

/** Tamano de bloque que acepta el backend por peticion. */
const BATCH_SIZE = 500;
const PAGE_SIZE = 100;

const isSelectable = (item: MeasurementDetailResponse) => !item.observations?.length;

/** Cola de revision. Las observadas se validan una a una, con justificacion. */
@Injectable()
export class ReviewFacade {
  private readonly api = inject(UptimeApi);

  readonly queue = signal<MeasurementQueue>('PENDING');
  readonly items = signal<MeasurementDetailResponse[]>([]);
  readonly selected = signal<ReadonlySet<string>>(new Set());
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly result = signal<BatchValidationResult | null>(null);
  readonly selectable = computed(() => this.items().filter(isSelectable));

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const items: MeasurementDetailResponse[] = [];
      let page = 0;
      let totalPages = 1;
      while (page < totalPages) {
        const found = await firstValueFrom(
          this.api.search({ queue: this.queue(), page, size: PAGE_SIZE }),
        );
        items.push(...found.content);
        totalPages = found.totalPages;
        page++;
      }
      this.items.set(items);
    } catch (error) {
      this.error.set(toProblem(error).detail);
    } finally {
      this.loading.set(false);
    }
  }

  toggle(id: string): void {
    if (!this.selectable().some((item) => item.id === id)) return;
    this.selected.update((selected) => {
      const next = new Set(selected);
      if (!next.delete(id)) next.add(id);
      return next;
    });
  }

  selectAll(): void {
    this.selected.set(new Set(this.selectable().map((item) => item.id)));
  }

  clearSelection(): void {
    this.selected.set(new Set());
  }

  /** Cada bloque es independiente: un error detiene los siguientes, no revierte los anteriores. */
  async validateSelected(): Promise<void> {
    const selected = this.selected();
    const chosen: Item[] = this.items()
      .filter((item) => selected.has(item.id))
      .map(({ id, version }) => ({ id, version }));
    this.busy.set(true);
    this.error.set('');
    const total: BatchValidationResult = { validated: 0, skipped: [] };
    try {
      for (let start = 0; start < chosen.length; start += BATCH_SIZE) {
        const done = await firstValueFrom(
          this.api.validateBatch({ items: chosen.slice(start, start + BATCH_SIZE) }),
        );
        total.validated += done.validated;
        total.skipped.push(...done.skipped);
      }
      this.result.set(total);
      this.clearSelection();
      await this.load();
    } catch (error) {
      this.result.set(total.validated > 0 ? total : null);
      this.error.set(toProblem(error).detail);
    } finally {
      this.busy.set(false);
    }
  }
}
