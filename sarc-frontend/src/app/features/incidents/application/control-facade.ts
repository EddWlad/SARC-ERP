import { computed, effect, inject, Service, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { TicketSummaryResponse } from '../../../core/api/generated';
import { refreshWhileVisible, RefreshStatus } from '../../../core/http/auto-refresh';
import { toProblem } from '../../../core/http/problem';
import { FileSaver } from '../../../core/files/file-saver';
import { LocalDay } from '../../../core/time/local-day';
import { ReportFormat } from '../../../shared/reports/report-format';
import { isoDate, lastDay } from '../../../shared/ui/period/period';
import { PeriodSelection } from '../../../shared/ui/period/period-selection';
import { ControlReportQuery, IncidentsApi, TicketQuery } from '../data-access/incidents-api';
import { TicketState } from '../domain/incident-labels';

export const CONTROL_PAGE_SIZE = 20;

/** Una fila del informe de control: numero, elemento afectado y estado al final del periodo (FR-070, FR-074). */
export interface ControlRow {
  readonly id: string;
  readonly code: string;
  readonly elementLabel: string;
  readonly state: TicketState;
}

/**
 * Informe «Control de tickets» en pantalla (FR-070 a FR-077): la misma tabla del PDF y del Excel, con «Todo el
 * historial» de inicio, el estado de cada ticket al final del periodo y el pie de totales. Usa el listado con periodo
 * del backend, que comparte consulta con el informe, asi que las filas coinciden. Se actualiza solo mientras se ve y,
 * si una actualizacion falla, conserva lo que mostraba.
 */
@Service({ autoProvided: false })
export class ControlFacade {
  private readonly api = inject(IncidentsApi);
  private readonly fileSaver = inject(FileSaver);
  private ticket = 0;
  private loaded = false;

  readonly period = new PeriodSelection('allHistory', inject(LocalDay).today);
  readonly state = signal('');
  readonly network = signal('');
  readonly page = signal(0);

  readonly rows = signal<readonly ControlRow[]>([]);
  readonly total = signal(0);
  readonly open = signal(0);
  readonly closed = computed(() => this.total() - this.open());
  /** El corte del servidor con el que se armo lo que se ve (FR-076). */
  readonly cutOff = signal<string | null>(null);
  readonly loading = signal(false);
  readonly failed = signal(false);
  readonly refreshFailed = signal(false);
  readonly errorMessage = signal('');
  readonly refresh = new RefreshStatus();
  readonly downloading = signal<ReportFormat | undefined>(undefined);

  /** Dias inclusivos del periodo; vacios en «Todo el historial». */
  private readonly days = computed<{ from?: string; to?: string }>(() => {
    if (this.period.allHistory()) return {};
    const range = this.period.range();
    return { from: range.from, to: isoDate(lastDay(range)) };
  });

  readonly hasFilters = computed(() => this.state() !== '' || this.network() !== '');

  constructor() {
    // Cambiar el periodo, un filtro o la pagina consulta de nuevo.
    effect(() => {
      this.days();
      this.state();
      this.network();
      this.page();
      untracked(() => void this.load());
    });
    refreshWhileVisible(() => void this.load());
  }

  async load(): Promise<void> {
    const ticket = ++this.ticket;
    this.loading.set(true);
    try {
      const base = this.query();
      const [page, opened] = await Promise.all([
        firstValueFrom(this.api.tickets({ ...base, page: this.page(), size: CONTROL_PAGE_SIZE })),
        this.openCount(base),
      ]);
      // Una respuesta tardia de una consulta anterior no pisa la actual.
      if (ticket !== this.ticket) return;
      this.rows.set(controlRows(page.items));
      this.total.set(page.total);
      this.open.set(opened ?? (this.state() === 'OPEN' ? page.total : 0));
      this.cutOff.set(page.cutOff);
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

  /** Los abiertos del periodo con los mismos filtros; con un estado elegido, el total ya lo dice. */
  private async openCount(base: TicketQuery): Promise<number | undefined> {
    if (this.state() !== '') return undefined;
    const page = await firstValueFrom(
      this.api.tickets({ ...base, state: 'OPEN', page: 0, size: 1 }),
    );
    return page.total;
  }

  private query(): TicketQuery {
    return {
      ...this.days(),
      state: this.state(),
      network: this.network(),
      sort: 'code,asc',
    };
  }

  private reportQuery(): ControlReportQuery {
    return { ...this.days(), state: this.state(), network: this.network() };
  }

  filter(name: 'state' | 'network', value: string): void {
    this[name].set(value);
    this.page.set(0);
  }

  clearFilters(): void {
    this.state.set('');
    this.network.set('');
    this.page.set(0);
  }

  goToPage(index: number): void {
    this.page.set(index);
  }

  /**
   * Descarga el informe con el periodo y los filtros que se ven (FR-077). Si falla, rechaza con el `Problem` del
   * servidor. Cada descarga queda archivada en el backend (FR-074a).
   */
  async downloadReport(format: ReportFormat): Promise<void> {
    this.downloading.set(format);
    try {
      await this.fileSaver.download(
        this.api.downloadControlReport(format, this.reportQuery()),
        `control-tickets.${format.toLowerCase()}`,
      );
    } finally {
      this.downloading.set(undefined);
    }
  }
}

/** Las filas de una pagina del listado, como las muestra el informe: el estado es el del final del periodo. */
export function controlRows(items: readonly TicketSummaryResponse[]): readonly ControlRow[] {
  return items.map((item) => ({
    id: item.id,
    code: item.code,
    elementLabel: item.elementLabel,
    state: (item.stateAtPeriodEnd ?? item.state) as TicketState,
  }));
}
