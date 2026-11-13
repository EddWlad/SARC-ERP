import { httpResource } from '@angular/common/http';
import { computed, inject, Service, signal } from '@angular/core';

import { KiaPointResponse } from '../../../core/api/generated';
import { FileSaver } from '../../../core/files/file-saver';
import { refreshWhileVisible } from '../../../core/http/auto-refresh';
import { retainedValue } from '../../../core/http/retained-value';
import { toProblem } from '../../../core/http/problem';
import { ReportFormat } from '../../../shared/reports/report-format';
import { IntermittencyApi, SummaryQuery } from '../data-access/intermittency-api';
import { IntermittencySummary } from '../domain/intermittency-summary';
import {
  currentFortnight,
  MAX_CUSTOM_DAYS,
  PeriodPreset,
  periodRange,
  todayInOperatingZone,
} from '../domain/intermittency-time';

/**
 * Estado del tablero de intermitencias (FR-031, FR-032): periodo, puntos, cifras y su actualizacion. Abre en
 * la quincena en curso con corte hoy. Un rango personalizado incompleto o demasiado largo no consulta: dice
 * por que.
 */
@Service({ autoProvided: false })
export class DashboardFacade {
  private readonly api = inject(IntermittencyApi);
  private readonly fileSaver = inject(FileSaver);

  /** Hoy en la zona operativa; se renueva en cada actualizacion para no quedarse en el dia anterior. */
  readonly today = signal(todayInOperatingZone());
  private readonly initial = currentFortnight(this.today());

  readonly preset = signal<PeriodPreset>(this.initial.preset);
  /** aaaa-mm, para las quincenas. */
  readonly month = signal(this.initial.month);
  /** Primer y ultimo dia incluidos del rango personalizado. */
  readonly firstDay = signal('');
  readonly lastDay = signal('');
  readonly points = signal<readonly KiaPointResponse[]>([]);
  /** Formato que se esta generando; solo una descarga a la vez. */
  readonly downloading = signal<ReportFormat | undefined>(undefined);

  /** Rango `[from, to)` elegido; nulo si falta algo o pasa de 366 dias. */
  readonly range = computed(() =>
    periodRange({
      preset: this.preset(),
      month: this.month(),
      from: this.firstDay(),
      lastDay: this.lastDay(),
      today: this.today(),
    }),
  );

  /** Por que el rango personalizado no se consulta, si no se consulta. */
  readonly rangeError = computed(() => {
    if (this.preset() !== 'CUSTOM' || this.range() !== null) return '';
    const first = this.firstDay();
    const last = this.lastDay();
    if (!first || !last) return 'Elija el primer y el último día.';
    if (last < first) return 'El último día no puede ser anterior al primero.';
    return `El rango no puede superar ${MAX_CUSTOM_DAYS} días.`;
  });

  /** Filtros vigentes del tablero; los usa tambien la disponibilidad estimada para consultar lo mismo. */
  readonly query = computed<SummaryQuery | undefined>(() => {
    const range = this.range();
    if (range === null) return undefined;
    const equipmentIds = this.points().map((point) => point.id);
    switch (this.preset()) {
      case 'FIRST_HALF':
      case 'SECOND_HALF':
        return { preset: this.preset(), month: this.month(), equipmentIds };
      case 'LAST_15_DAYS':
        return { preset: 'LAST_15_DAYS', equipmentIds };
      case 'CUSTOM':
        return { preset: 'CUSTOM', from: range.from, to: range.to, equipmentIds };
    }
  });

  private readonly result = httpResource<IntermittencySummary>(() => {
    const query = this.query();
    return query ? this.api.summaryRequest(query) : undefined;
  });

  private readonly retained = retainedValue(this.result);
  /** Sin un rango consultable no hay cifras: las del periodo anterior se leerian como las del nuevo. */
  readonly summary = computed(() => (this.range() === null ? undefined : this.retained()));
  readonly loading = this.result.isLoading;
  /** Mensaje del servidor si no se pudo calcular. */
  readonly error = computed(() => {
    const error = this.result.error();
    return error === undefined ? '' : toProblem(error).detail;
  });
  /** Hay un filtro de puntos o un rango que limpiar. */
  readonly filtered = computed(() => this.points().length > 0);

  constructor() {
    // Los datos los cambian tambien otros usuarios: se actualizan solos mientras se miran.
    refreshWhileVisible(() => this.reload());
  }

  choosePreset(preset: PeriodPreset): void {
    this.preset.set(preset);
  }

  chooseMonth(month: string): void {
    if (month) this.month.set(month);
  }

  chooseRange(firstDay: string, lastDay: string): void {
    this.firstDay.set(firstDay);
    this.lastDay.set(lastDay);
  }

  choosePoints(points: readonly KiaPointResponse[]): void {
    this.points.set(points);
  }

  clearFilters(): void {
    this.points.set([]);
  }

  /**
   * Descarga el informe con el periodo y los puntos vigentes (FR-038): las mismas cifras que se ven. Sin un
   * rango consultable no se pide nada. Si falla, rechaza con el `Problem` del servidor. No envia correo (FR-041).
   */
  async downloadReport(format: ReportFormat): Promise<void> {
    const query = this.query();
    if (!query) return;
    this.downloading.set(format);
    try {
      await this.fileSaver.download(
        this.api.downloadReport(format, query),
        `intermitencias.${format.toLowerCase()}`,
      );
    } finally {
      this.downloading.set(undefined);
    }
  }

  reload(): void {
    this.today.set(todayInOperatingZone());
    this.result.reload();
  }
}
