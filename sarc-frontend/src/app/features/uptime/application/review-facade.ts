import { computed, DestroyRef, DOCUMENT, inject, Injectable, signal } from '@angular/core';
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
  private readonly window = inject(DOCUMENT).defaultView;
  private loadTicket = 0;
  private timer?: ReturnType<typeof setTimeout>;
  private destroyed = false;
  private readonly contextKey = 'sarc.uptime.temporal-work';
  private pendingRequest?: { key: string; items: { id: string; version: number }[] };
  readonly work = signal<
    import('../../../core/api/generated').TemporalTransitionWorkResponse | null
  >(null);
  readonly workPage = signal(0);
  readonly workId = signal<string | null>(null);
  constructor() {
    try {
      const saved = this.window?.sessionStorage.getItem(this.contextKey);
      if (saved) {
        const data = JSON.parse(saved);
        this.workId.set(data.workId ?? null);
        this.pendingRequest = data.request;
      }
    } catch {
      /* Contexto recuperable por cola si no hay almacenamiento del navegador. */
    }
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      clearTimeout(this.timer);
    });
  }
  private saveContext(): void {
    try {
      this.window?.sessionStorage.setItem(
        this.contextKey,
        JSON.stringify({ workId: this.workId(), request: this.pendingRequest }),
      );
    } catch {
      /* La persistencia real del trabajo permanece en backend. */
    }
  }
  async transitionSelected(): Promise<void> {
    if (this.work()?.status === 'RUNNING' || this.work()?.status === 'QUEUED') return;
    this.busy.set(true);
    this.error.set('');
    try {
      this.pendingRequest ??= {
        key: crypto.randomUUID(),
        items: this.items()
          .filter((i) => this.selected().has(i.id))
          .slice(0, BATCH_SIZE)
          .map(({ id, version }) => ({ id, version })),
      };
      if (!this.pendingRequest.items.length) return;
      this.saveContext();
      const done = await firstValueFrom(
        this.api.transition(this.pendingRequest.items, this.pendingRequest.key),
      );
      this.work.set(done);
      this.workId.set(done.workId);
      this.pendingRequest = undefined;
      this.saveContext();
      this.clearSelection();
      await this.readWork();
    } catch (error) {
      this.error.set(toProblem(error).detail);
    } finally {
      this.busy.set(false);
    }
  }
  async readWork(page = this.workPage()): Promise<void> {
    const id = this.workId();
    if (!id || this.destroyed) return;
    clearTimeout(this.timer);
    try {
      const done = await firstValueFrom(this.api.transitionWork(id, page));
      if (this.destroyed || id !== this.workId()) return;
      this.work.set(done);
      this.workPage.set(page);
      if (done.status === 'QUEUED' || done.status === 'RUNNING')
        this.timer = setTimeout(() => void this.readWork(), 2000);
    } catch (error) {
      this.error.set(toProblem(error).detail);
    }
  }
  async retryWork(): Promise<void> {
    const work = this.work();
    if (!work || !['FAILED', 'INTERRUPTED'].includes(work.status)) return;
    this.busy.set(true);
    this.error.set('');
    try {
      this.work.set(await firstValueFrom(this.api.retryTransition(work.workId, work.version)));
      await this.readWork();
    } catch (error) {
      this.error.set(toProblem(error).detail);
    } finally {
      this.busy.set(false);
    }
  }

  readonly queue = signal<MeasurementQueue>('PENDING');
  readonly items = signal<MeasurementDetailResponse[]>([]);
  readonly selected = signal<ReadonlySet<string>>(new Set());
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly result = signal<BatchValidationResult | null>(null);
  readonly selectable = computed(() =>
    this.queue() === 'TEMPORAL_RECONCILIATION' ? this.items() : this.items().filter(isSelectable),
  );

  async load(): Promise<void> {
    const ticket = ++this.loadTicket;
    const queue = this.queue();
    this.loading.set(true);
    this.error.set('');
    try {
      const items: MeasurementDetailResponse[] = [];
      let page = 0;
      let totalPages = 1;
      while (page < totalPages) {
        const found = await firstValueFrom(this.api.search({ queue, page, size: PAGE_SIZE }));
        items.push(...found.content);
        totalPages = found.totalPages;
        page++;
      }
      if (ticket === this.loadTicket) this.items.set(items);
    } catch (error) {
      if (ticket === this.loadTicket) this.error.set(toProblem(error).detail);
    } finally {
      if (ticket === this.loadTicket) this.loading.set(false);
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
    if (this.queue() === 'TEMPORAL_RECONCILIATION') {
      await this.transitionSelected();
      return;
    }
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
