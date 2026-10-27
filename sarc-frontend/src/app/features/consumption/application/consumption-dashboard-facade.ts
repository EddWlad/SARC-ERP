import { httpResource } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';

import { FileSaver } from '../../../core/files/file-saver';
import { refreshWhileVisible } from '../../../core/http/auto-refresh';
import { LocalDay } from '../../../core/time/local-day';
import { toProblem } from '../../../core/http/problem';
import { ReportFormat } from '../../../shared/reports/report-format';
import { ConsumptionApi } from '../data-access/consumption-api';
import {
  ConsumptionFilters,
  ConsumptionSummary,
  NO_CONSUMPTION_FILTERS,
} from '../domain/consumption';
import { PeriodSelection } from './period-selection';

/**
 * Estado del tablero de consumo (PRD 7.3): periodo, filtros, indicadores y reportes. El PDF y el
 * Excel se piden con el mismo filtro que la pantalla, por eso muestran los mismos totales.
 */
@Service({ autoProvided: false })
export class ConsumptionDashboardFacade {
  private readonly api = inject(ConsumptionApi);
  private readonly fileSaver = inject(FileSaver);

  readonly period = new PeriodSelection('thisMonth', inject(LocalDay).today);
  readonly filters = signal<ConsumptionFilters>(NO_CONSUMPTION_FILTERS);
  readonly downloading = signal<ReportFormat | undefined>(undefined);

  private readonly result = httpResource<ConsumptionSummary>(() =>
    this.api.summaryRequest(this.period.range(), this.filters()),
  );

  readonly summary = computed(() => (this.result.hasValue() ? this.result.value() : undefined));
  readonly loading = this.result.isLoading;
  /** Mensaje del backend si no se pudo calcular (p. ej. periodo mayor a 366 dias). */
  readonly error = computed(() => {
    const error = this.result.error();
    return error === undefined ? undefined : toProblem(error).detail;
  });

  updateFilters(changes: Partial<ConsumptionFilters>): void {
    this.filters.update((current) => ({ ...current, ...changes }));
  }

  clearFilters(): void {
    this.filters.set(NO_CONSUMPTION_FILTERS);
  }

  constructor() {
    // Los datos los cambian tambien otros usuarios: se actualizan solos mientras se miran.
    refreshWhileVisible(() => this.reload());
  }

  reload(): void {
    this.result.reload();
  }

  /** Si falla, rechaza con el `Problem` del backend. */
  async downloadReport(format: ReportFormat): Promise<void> {
    this.downloading.set(format);
    try {
      await this.fileSaver.download(
        this.api.downloadReport(format, this.period.range(), this.filters()),
        `consumo.${format.toLowerCase()}`,
      );
    } finally {
      this.downloading.set(undefined);
    }
  }
}
