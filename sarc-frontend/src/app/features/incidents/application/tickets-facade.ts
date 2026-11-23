import { computed, inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { MessageResponse, TicketSummaryResponse } from '../../../core/api/generated';
import { refreshWhileVisible, RefreshStatus } from '../../../core/http/auto-refresh';
import { toProblem } from '../../../core/http/problem';
import { IncidentsApi, TicketQuery } from '../data-access/incidents-api';
import { MessageOptions } from '../domain/incident-message';

/** Campos por los que se ordena el listado (FR-061). El elemento afectado no se ordena. */
export type TicketSortField =
  'code' | 'state' | 'type' | 'network' | 'priority' | 'responsible' | 't0' | 'lastActivityAt';

export const TICKET_PAGE_SIZE = 20;

/**
 * Listado de tickets: busqueda, filtros, orden y pagina (FR-061). Se actualiza solo mientras la pantalla esta a la vista
 * (FR-063) y, si una actualizacion falla, conserva lo que ya mostraba, avisa y anota si fue por conectividad.
 */
@Service({ autoProvided: false })
export class TicketsFacade {
  private readonly api = inject(IncidentsApi);
  private ticket = 0;

  readonly search = signal('');
  readonly state = signal('');
  readonly elementClass = signal('');
  readonly incidentType = signal('');
  readonly priority = signal('');
  readonly network = signal('');
  readonly condition = signal('');
  readonly sortField = signal<TicketSortField>('lastActivityAt');
  readonly sortDescending = signal(true);
  readonly page = signal(0);

  readonly items = signal<readonly TicketSummaryResponse[]>([]);
  readonly total = signal(0);
  /** `TICKET_BEFORE_START` cuando se busco un codigo anterior al numero inicial. */
  readonly notice = signal<string | null>(null);
  readonly loading = signal(false);
  /** La primera carga fallo: no hay nada que mostrar. */
  readonly failed = signal(false);
  /** Una actualizacion fallo, pero se sigue mostrando la anterior. */
  readonly refreshFailed = signal(false);
  readonly errorMessage = signal('');
  readonly refresh = new RefreshStatus();

  private loaded = false;

  readonly hasFilters = computed(
    () =>
      this.search() !== '' ||
      this.state() !== '' ||
      this.elementClass() !== '' ||
      this.incidentType() !== '' ||
      this.priority() !== '' ||
      this.network() !== '' ||
      this.condition() !== '',
  );

  readonly sort = computed(() => `${this.sortField()},${this.sortDescending() ? 'desc' : 'asc'}`);

  /** El mensaje para WhatsApp de un ticket de la lista; solo lectura (FR-059). */
  message(id: string, options: MessageOptions): Promise<MessageResponse> {
    return firstValueFrom(this.api.ticketMessage(id, options));
  }

  constructor() {
    refreshWhileVisible(() => void this.load());
  }

  async load(): Promise<void> {
    const ticket = ++this.ticket;
    this.loading.set(true);
    try {
      const page = await firstValueFrom(this.api.tickets(this.query()));
      // Una respuesta tardia de una consulta anterior no pisa la actual.
      if (ticket !== this.ticket) return;
      this.items.set(page.items);
      this.total.set(page.total);
      this.notice.set(page.notice ?? null);
      this.failed.set(false);
      this.refreshFailed.set(false);
      this.errorMessage.set('');
      this.loaded = true;
      this.refresh.markSuccess();
    } catch (error) {
      if (ticket !== this.ticket) return;
      this.errorMessage.set(toProblem(error).detail);
      this.refresh.markFailure(error);
      if (this.loaded) {
        this.refreshFailed.set(true);
      } else {
        this.failed.set(true);
      }
    } finally {
      if (ticket === this.ticket) this.loading.set(false);
    }
  }

  private query(): TicketQuery {
    return {
      search: this.search().trim(),
      state: this.state(),
      elementClass: this.elementClass(),
      incidentType: this.incidentType(),
      priority: this.priority(),
      network: this.network(),
      condition: this.condition(),
      sort: this.sort(),
      page: this.page(),
      size: TICKET_PAGE_SIZE,
    };
  }

  /** Cambia un filtro, vuelve a la primera pagina y consulta. */
  filter(
    name: 'state' | 'elementClass' | 'incidentType' | 'priority' | 'network' | 'condition',
    value: string,
  ): void {
    this[name].set(value);
    this.page.set(0);
    void this.load();
  }

  searchFor(text: string): void {
    this.search.set(text);
    this.page.set(0);
    void this.load();
  }

  clearFilters(): void {
    for (const name of [
      'state',
      'elementClass',
      'incidentType',
      'priority',
      'network',
      'condition',
    ] as const) {
      this[name].set('');
    }
    this.search.set('');
    this.page.set(0);
    void this.load();
  }

  /** Ordena por un campo: el mismo campo invierte la direccion; otro empieza ascendente. */
  sortBy(field: TicketSortField): void {
    if (this.sortField() === field) {
      this.sortDescending.update((value) => !value);
    } else {
      this.sortField.set(field);
      this.sortDescending.set(false);
    }
    this.page.set(0);
    void this.load();
  }

  goToPage(page: number): void {
    this.page.set(page);
    void this.load();
  }
}
