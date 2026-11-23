import { computed, effect, inject, Service, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { IncidentSummaryResponse } from '../../../core/api/generated';
import { refreshWhileVisible, RefreshStatus } from '../../../core/http/auto-refresh';
import { toProblem } from '../../../core/http/problem';
import { FileSaver } from '../../../core/files/file-saver';
import { LocalDay } from '../../../core/time/local-day';
import { ReportFormat } from '../../../shared/reports/report-format';
import { isoDate, lastDay } from '../../../shared/ui/period/period';
import { PeriodSelection } from '../../../shared/ui/period/period-selection';
import { IncidentsApi, ManagementQuery } from '../data-access/incidents-api';

/** Abiertos por condicion, derivados de los hitos del episodio vigente (FR-062): no son estados nuevos. */
export interface OpenConditions {
  readonly pendingValidation: number;
  readonly inAttention: number;
  readonly recoveredObserving: number;
}

/**
 * Tablero de gestion de incidencias (FR-080 a FR-086): el periodo inicial es «Este mes» (D13), con los mismos accesos
 * rapidos y filtros de red, clase, tipo y prioridad. Las cifras salen del mismo calculo que el PDF y el Excel, y se
 * actualizan solas mientras se ve el tablero. Si una actualizacion falla conserva lo que mostraba y lo avisa.
 */
@Service({ autoProvided: false })
export class DashboardFacade {
  private readonly api = inject(IncidentsApi);
  private readonly fileSaver = inject(FileSaver);
  private ticket = 0;
  private loaded = false;

  readonly period = new PeriodSelection('thisMonth', inject(LocalDay).today);
  readonly network = signal('');
  readonly elementClass = signal('');
  readonly incidentType = signal('');
  readonly priority = signal('');

  readonly summary = signal<IncidentSummaryResponse | null>(null);
  readonly conditions = signal<OpenConditions | null>(null);
  readonly loading = signal(false);
  readonly failed = signal(false);
  readonly refreshFailed = signal(false);
  readonly errorMessage = signal('');
  readonly refresh = new RefreshStatus();
  readonly downloading = signal<ReportFormat | undefined>(undefined);

  readonly hasFilters = computed(
    () =>
      this.network() !== '' ||
      this.elementClass() !== '' ||
      this.incidentType() !== '' ||
      this.priority() !== '',
  );

  /** Periodo en dias inclusivos y filtros vigentes; es lo que piden las cifras y la descarga. */
  readonly query = computed<ManagementQuery>(() => {
    const range = this.period.range();
    return {
      from: range.from,
      to: isoDate(lastDay(range)),
      network: this.network(),
      elementClass: this.elementClass(),
      incidentType: this.incidentType(),
      priority: this.priority(),
    };
  });

  constructor() {
    // Cambiar el periodo o un filtro consulta de nuevo.
    effect(() => {
      this.query();
      untracked(() => void this.load());
    });
    refreshWhileVisible(() => void this.load());
  }

  async load(): Promise<void> {
    const ticket = ++this.ticket;
    this.loading.set(true);
    try {
      const query = this.query();
      const [summary, pending, attention, observing] = await Promise.all([
        firstValueFrom(this.api.summary(query)),
        this.countOpen(query, 'PENDING_VALIDATION'),
        this.countOpen(query, 'IN_ATTENTION'),
        this.countOpen(query, 'RECOVERED_OBSERVING'),
      ]);
      // Una respuesta tardia de una consulta anterior no pisa la actual.
      if (ticket !== this.ticket) return;
      this.summary.set(summary);
      this.conditions.set({
        pendingValidation: pending,
        inAttention: attention,
        recoveredObserving: observing,
      });
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

  /** Cuantos tickets abiertos hay en esa condicion, con los filtros del tablero (el periodo no aplica a lo abierto hoy). */
  private async countOpen(query: ManagementQuery, condition: string): Promise<number> {
    const page = await firstValueFrom(
      this.api.tickets({
        condition,
        network: query.network,
        elementClass: query.elementClass,
        incidentType: query.incidentType,
        priority: query.priority,
        page: 0,
        size: 1,
      }),
    );
    return page.total;
  }

  filter(name: 'network' | 'elementClass' | 'incidentType' | 'priority', value: string): void {
    this[name].set(value);
  }

  clearFilters(): void {
    this.network.set('');
    this.elementClass.set('');
    this.incidentType.set('');
    this.priority.set('');
  }

  /**
   * Descarga el reporte con el periodo y los filtros que se ven (FR-086). Si falla, rechaza con el `Problem` del
   * servidor. Cada descarga queda archivada en el backend.
   */
  async downloadReport(format: ReportFormat): Promise<void> {
    this.downloading.set(format);
    try {
      await this.fileSaver.download(
        this.api.downloadManagementReport(format, this.query()),
        `gestion-incidencias.${format.toLowerCase()}`,
      );
    } finally {
      this.downloading.set(undefined);
    }
  }
}
