import { httpResource } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged, firstValueFrom } from 'rxjs';

import { PendingLinkResponse } from '../../../core/api/generated';
import { LocalDay } from '../../../core/time/local-day';
import { ConsumptionApi } from '../data-access/consumption-api';
import {
  ConsumptionFilters,
  NO_CONSUMPTION_FILTERS,
  Session,
  SessionPage,
  SessionState,
} from '../domain/consumption';
import { PeriodSelection } from './period-selection';

const SEARCH_DEBOUNCE_MS = 300;
const DEFAULT_PAGE_SIZE = 20;

/** Historico de sesiones (PRD RF-003.11): filtros, paginacion y operaciones de calidad del dato. */
@Service({ autoProvided: false })
export class SessionsFacade {
  private readonly api = inject(ConsumptionApi);

  readonly period = new PeriodSelection('thisMonth', inject(LocalDay).today);
  readonly filters = signal<ConsumptionFilters>(NO_CONSUMPTION_FILTERS);
  readonly state = signal<SessionState | ''>('');
  readonly text = signal('');
  readonly pageIndex = signal(0);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  private readonly searchText = toSignal(
    toObservable(this.text).pipe(debounceTime(SEARCH_DEBOUNCE_MS), distinctUntilChanged()),
    { initialValue: '' },
  );

  private readonly result = httpResource<SessionPage>(() =>
    this.api.sessionsRequest({
      range: this.period.range(),
      filters: this.filters(),
      state: this.state(),
      text: this.searchText(),
      page: this.pageIndex(),
      size: this.pageSize(),
    }),
  );

  readonly sessions = computed(() => (this.result.hasValue() ? this.result.value().content : []));
  readonly totalElements = computed(() =>
    this.result.hasValue() ? this.result.value().totalElements : 0,
  );
  readonly loading = this.result.isLoading;
  readonly failed = computed(() => this.result.error() !== undefined);
  /** Filtros propios del historico (estado y busqueda), ademas de los compartidos. */
  readonly hasOwnFilters = computed(() => this.state() !== '' || this.text() !== '');

  updateFilters(changes: Partial<ConsumptionFilters>): void {
    this.filters.update((current) => ({ ...current, ...changes }));
    this.pageIndex.set(0);
  }

  clearFilters(): void {
    this.state.set('');
    this.text.set('');
    this.updateFilters(NO_CONSUMPTION_FILTERS);
  }

  filterState(state: SessionState | ''): void {
    this.state.set(state);
    this.pageIndex.set(0);
  }

  search(text: string): void {
    this.text.set(text);
    this.pageIndex.set(0);
  }

  changePage(pageIndex: number, pageSize: number): void {
    this.pageIndex.set(pageIndex);
    this.pageSize.set(pageSize);
  }

  reload(): void {
    this.result.reload();
  }

  async markTest(session: Session, test: boolean): Promise<Session> {
    const updated = await firstValueFrom(this.api.markTest(session, test));
    this.reload();
    return updated;
  }

  /** Vincula las pendientes cuyas identidades ya se verificaron en el inventario. */
  async linkPending(): Promise<PendingLinkResponse> {
    const result = await firstValueFrom(this.api.linkPending());
    this.reload();
    return result;
  }
}
