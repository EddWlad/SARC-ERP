import { formatDate } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';

import {
  ConnectorResultResponse,
  EquipmentResultResponse,
  ExclusionResponse,
  ScopeConnectorResponse,
  UptimeSummaryResponse,
} from '../../../core/api/generated';
import { FileSaver } from '../../../core/files/file-saver';
import { refreshWhileVisible } from '../../../core/http/auto-refresh';
import { toProblem } from '../../../core/http/problem';
import { ReportFormat } from '../../../shared/reports/report-format';
import { UptimeApi, UptimeFilters } from '../data-access/uptime-api';

export type { UptimeFilters };

const PAGE_SIZE = 100;
const OPERATING_OFFSET = '-0500';

const NOTICE_LABELS: Readonly<Record<string, string>> = {
  NO_OFFICIAL_WEEK: 'No hay cifra oficial: no existe una semana exacta validada en esta vista.',
  LATEST_WEEK_MISSING: 'Falta validar la semana oficial cerrada más reciente.',
  NO_EVALUABLE_CONNECTORS: 'No hay conectores evaluables con estos filtros.',
};

interface Page<T> {
  readonly content: T[];
  readonly totalPages: number;
}

/** Indicadores comunes. Toda cifra llega calculada del backend; aqui solo se rotula. */
@Injectable()
export class DashboardFacade {
  private readonly api = inject(UptimeApi);
  private readonly fileSaver = inject(FileSaver);

  private ticket = 0;
  private referenceContext?: string;

  readonly filters = signal<UptimeFilters>({});
  readonly summary = signal<UptimeSummaryResponse | null>(null);
  readonly connectors = signal<ConnectorResultResponse[]>([]);
  readonly equipment = signal<EquipmentResultResponse[]>([]);
  readonly referenceConnectors = signal<ConnectorResultResponse[]>([]);
  readonly referenceEquipment = signal<EquipmentResultResponse[]>([]);
  readonly referenceExclusions = signal<ExclusionResponse[]>([]);
  readonly exclusions = signal<ExclusionResponse[]>([]);
  readonly scope = signal<ScopeConnectorResponse[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly refreshFailed = signal(false);
  readonly downloading = signal<ReportFormat | undefined>(undefined);

  readonly hasFilters = computed(() => Object.keys(this.filters()).length > 0);
  readonly headline = computed(() => {
    const summary = this.summary();
    const official = summary?.resolvedPeriod.kind === 'OFFICIAL';
    const block = official ? summary?.official : summary?.provisional;
    return block ? { label: official ? 'OFICIAL' : 'PROVISIONAL', ...block } : null;
  });
  /** El apartado secundario: una cifra provisional nunca se mezcla con la oficial. */
  readonly secondary = computed(() => {
    const summary = this.summary();
    return summary?.resolvedPeriod.kind === 'OFFICIAL' ? (summary.provisional ?? null) : null;
  });
  readonly rangeLabel = computed(() => {
    const summary = this.summary();
    if (summary?.selection?.basis === 'REFERENCE_WEEK')
      return (
        'Semana de referencia ' +
        summary.selection.referenceWeek +
        ' · horarios por conector o no informados'
      );
    const period = summary?.resolvedPeriod;
    if (!period?.from || !period.to) return '';
    const day = (value: string) => formatDate(value, 'dd/MM/yyyy', 'en-US', OPERATING_OFFSET);
    return `${day(period.from)} a ${day(period.to)} · ${period.timeZone}`;
  });
  readonly notices = computed(() =>
    (this.summary()?.notices ?? []).map((code) => NOTICE_LABELS[code]),
  );
  readonly trendAvailable = computed(() => (this.summary()?.trend.length ?? 0) > 0);

  constructor() {
    refreshWhileVisible(() => void this.refresh());
  }

  /** Cada cambio aplica de inmediato, como en Consumo; un valor vacio quita el filtro. */
  updateFilters(changes: Partial<UptimeFilters>): void {
    if (['periodKind', 'periodType', 'from', 'to', 'referenceWeek'].some((key) => key in changes))
      this.referenceContext = undefined;
    const merged = { ...this.filters(), ...changes };
    this.filters.set(
      Object.fromEntries(Object.entries(merged).filter(([, value]) => value)) as UptimeFilters,
    );
    void this.load();
  }

  clearFilters(): void {
    this.referenceContext = undefined;
    this.filters.set({});
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    const expected = this.ticket + 1;
    try {
      await this.fetch();
    } catch (error) {
      if (expected === this.ticket) this.error.set(toProblem(error).detail);
    } finally {
      if (expected === this.ticket) this.loading.set(false);
    }
  }

  /** Un fallo automatico no borra lo que se esta mirando. */
  async refresh(): Promise<void> {
    const expected = this.ticket + 1;
    try {
      await this.fetch();
    } catch {
      if (expected === this.ticket) this.refreshFailed.set(true);
    }
  }

  /** Si falla, rechaza con el `Problem` del backend. */
  async downloadReport(format: ReportFormat): Promise<void> {
    this.downloading.set(format);
    try {
      await this.fileSaver.download(
        this.api.downloadReport(format, this.resolvedFilters()),
        `uptime.${format.toLowerCase()}`,
      );
    } finally {
      this.downloading.set(undefined);
    }
  }

  async searchScope(text: string): Promise<void> {
    this.scope.set(await firstValueFrom(this.api.scope(text)));
  }

  private resolvedFilters(): UptimeFilters {
    return this.referenceContext
      ? { ...this.filters(), referenceWeek: this.referenceContext }
      : this.filters();
  }

  private async fetch(): Promise<void> {
    const ticket = ++this.ticket;
    const filters = this.resolvedFilters();
    const summary = await firstValueFrom(this.api.summary(filters));
    const week =
      summary.selection?.basis === 'REFERENCE_WEEK' ? summary.selection.referenceWeek : null;
    const knownFilters: UptimeFilters = week
      ? {
          ...filters,
          from: undefined,
          to: undefined,
          referenceWeek: week,
          temporalSection: 'KNOWN_RANGE',
        }
      : filters;
    const reportFilters: UptimeFilters = { ...knownFilters, temporalSection: 'REFERENCE_WEEK' };
    const [
      connectors,
      equipment,
      exclusions,
      referenceConnectors,
      referenceEquipment,
      referenceExclusions,
    ] = await Promise.all([
      allPages((page) => this.api.connectorResults(knownFilters, page, PAGE_SIZE)),
      allPages((page) => this.api.equipmentResults(knownFilters, page, PAGE_SIZE)),
      allPages((page) => this.api.exclusionResults(knownFilters, page, PAGE_SIZE)),
      week
        ? allPages((page) => this.api.connectorResults(reportFilters, page, PAGE_SIZE))
        : Promise.resolve([]),
      week
        ? allPages((page) => this.api.equipmentResults(reportFilters, page, PAGE_SIZE))
        : Promise.resolve([]),
      week
        ? allPages((page) => this.api.exclusionResults(reportFilters, page, PAGE_SIZE))
        : Promise.resolve([]),
    ]);
    // Una respuesta lenta de filtros ya reemplazados no pisa la vigente.
    if (ticket !== this.ticket) return;
    this.referenceContext = week ?? undefined;
    this.summary.set(summary);
    this.connectors.set(connectors);
    this.equipment.set(equipment);
    this.exclusions.set(exclusions);
    this.referenceConnectors.set(referenceConnectors);
    this.referenceEquipment.set(referenceEquipment);
    this.referenceExclusions.set(referenceExclusions);
    this.refreshFailed.set(false);
  }
}

async function allPages<T>(read: (page: number) => Observable<Page<T>>): Promise<T[]> {
  const items: T[] = [];
  let page = 0;
  let totalPages = 1;
  while (page < totalPages) {
    const found = await firstValueFrom(read(page));
    items.push(...found.content);
    totalPages = found.totalPages;
    page++;
  }
  return items;
}
