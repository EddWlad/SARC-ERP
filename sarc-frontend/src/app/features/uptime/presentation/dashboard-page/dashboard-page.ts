import { formatDate } from '@angular/common';
import { Component, computed, inject, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { LucideFileSpreadsheet, LucideFileText } from '@lucide/angular';

import { Problem } from '../../../../core/http/problem';
import { Notifier } from '../../../../core/notifications/notifier';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { ReportFormat } from '../../../../shared/reports/report-format';
import { ChartSeries, ChartView } from '../../../../shared/ui/chart/chart-view';
import { DashboardFacade } from '../../application/dashboard-facade';
import { StatesChart } from '../states-chart/states-chart';
import { DashboardResults } from '../dashboard-results/dashboard-results';
import { UptimeFilterBar } from '../uptime-filter-bar/uptime-filter-bar';

const LOWEST_SHOWN = 10;

/** Indicadores por red, equipo, conector y periodo. */
@Component({
  selector: 'sarc-uptime-dashboard',
  imports: [
    ChartView,
    StatesChart,
    DashboardResults,
    MatButtonModule,
    MatProgressBarModule,
    LucideFileSpreadsheet,
    LucideFileText,
    UptimeFilterBar,
  ],
  providers: [DashboardFacade],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class DashboardPage implements OnInit {
  protected readonly facade = inject(DashboardFacade);
  protected readonly quantities = inject(QuantityFormat);
  private readonly notifier = inject(Notifier);
  protected readonly formatPercent = (value: number): string => this.quantities.percentage(value);

  protected readonly trendLabels = computed(
    () =>
      this.facade
        .summary()
        ?.trend.map((point) => formatDate(point.weekStart, 'dd/MM', 'en-US', '-0500')) ?? [],
  );
  protected readonly trendSeries = computed<ChartSeries>(() => ({
    label: 'Uptime',
    values: this.facade.summary()?.trend.map((point) => Number(point.uptimePercent)) ?? [],
    colors: 'series-1',
  }));
  private readonly lowest = computed(() => this.facade.connectors().slice(0, LOWEST_SHOWN));
  protected readonly lowestLabels = computed(() =>
    this.lowest().map((item) => `${item.equipmentName} · ${item.ordinal}`),
  );
  protected readonly lowestSeries = computed<ChartSeries>(() => ({
    label: 'Uptime',
    values: this.lowest().map((item) => Number(item.uptimePercent)),
    colors: 'series-1',
  }));
  private readonly reportLowest = computed(() =>
    this.facade.referenceConnectors().slice(0, LOWEST_SHOWN),
  );
  protected readonly reportLowestLabels = computed(() =>
    this.reportLowest().map((item) => item.equipmentName + ' · ' + item.ordinal),
  );
  protected readonly reportLowestSeries = computed<ChartSeries>(() => ({
    label: 'Promedio del informe',
    values: this.reportLowest().map((item) => Number(item.uptimePercent)),
    colors: 'series-1',
  }));
  protected readonly cutOff = computed(() => {
    const cutOff = this.facade.summary()?.cutOff;
    return cutOff
      ? new Date(cutOff).toLocaleString('es-EC', { timeZone: 'America/Guayaquil' })
      : '';
  });

  ngOnInit(): void {
    void this.facade.load();
    void this.facade.searchScope('');
  }

  protected async download(format: ReportFormat): Promise<void> {
    try {
      await this.facade.downloadReport(format);
    } catch (error) {
      this.notifier.error((error as Problem).detail);
    }
  }
}
