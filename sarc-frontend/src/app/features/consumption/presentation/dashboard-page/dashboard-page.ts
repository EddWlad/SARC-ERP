import { DatePipe } from '@angular/common';
import { Component, computed, inject, LOCALE_ID } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { LucideFileSpreadsheet, LucideFileText } from '@lucide/angular';

import { Problem } from '../../../../core/http/problem';
import { Notifier } from '../../../../core/notifications/notifier';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { ReportFormat } from '../../../../shared/reports/report-format';
import { ChartSeries, ChartView } from '../../../../shared/ui/chart/chart-view';
import { NETWORK_LABELS, TYPE_LABELS } from '../../../../shared/vocabulary/charging-infrastructure';
import { ConsumptionDashboardFacade } from '../../application/consumption-dashboard-facade';
import { SOURCE_LABELS } from '../../domain/consumption';
import { parseIsoDate } from '../../domain/period';
import { ConsumptionFilterBar } from '../consumption-filter-bar/consumption-filter-bar';
import { DailyTable } from '../daily-table/daily-table';
import { ShareChart, ShareItem } from '../share-chart/share-chart';
import { TopEquipment } from '../top-equipment/top-equipment';

/**
 * Tablero gerencial de consumo (PRD 7.3): KPIs oficiales, pendientes y pruebas aparte, tendencia
 * diaria de energia y sesiones (graficas separadas: nunca doble eje), Top 5 por energia y
 * participacion por red, plataforma y tipo.
 */
@Component({
  selector: 'sarc-dashboard-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatProgressBarModule,
    LucideFileText,
    LucideFileSpreadsheet,
    ChartView,
    ConsumptionFilterBar,
    DailyTable,
    ShareChart,
    TopEquipment,
  ],
  providers: [ConsumptionDashboardFacade],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class DashboardPage {
  private readonly notifier = inject(Notifier);
  private readonly dayLabel = new Intl.DateTimeFormat(inject(LOCALE_ID), {
    day: '2-digit',
    month: 'short',
  });

  protected readonly facade = inject(ConsumptionDashboardFacade);
  protected readonly quantities = inject(QuantityFormat);
  protected readonly formatKwh = (value: number) => this.quantities.kwh(value);
  protected readonly formatCount = (value: number) => this.quantities.count(value);

  protected readonly days = computed(() =>
    (this.facade.summary()?.daily ?? []).map((day) => this.dayLabel.format(parseIsoDate(day.date))),
  );
  protected readonly dailyEnergy = computed<ChartSeries>(() => ({
    label: 'Energía (kWh)',
    values: (this.facade.summary()?.daily ?? []).map((day) => day.energyKwh ?? 0),
    colors: 'series-1',
  }));
  protected readonly dailySessions = computed<ChartSeries>(() => ({
    label: 'Sesiones',
    values: (this.facade.summary()?.daily ?? []).map((day) => day.sessions),
    colors: 'series-1',
  }));
  protected readonly byNetwork = computed<ShareItem[]>(() =>
    (this.facade.summary()?.byNetwork ?? []).map((share) => ({
      label: NETWORK_LABELS[share.network],
      energyKwh: share.energyKwh,
      sessions: share.sessions,
    })),
  );
  protected readonly bySource = computed<ShareItem[]>(() =>
    (this.facade.summary()?.bySource ?? []).map((share) => ({
      label: SOURCE_LABELS[share.source],
      energyKwh: share.energyKwh,
      sessions: share.sessions,
    })),
  );
  protected readonly byType = computed<ShareItem[]>(() =>
    (this.facade.summary()?.byType ?? []).map((share) => ({
      label: TYPE_LABELS[share.type],
      energyKwh: share.energyKwh,
      sessions: share.sessions,
    })),
  );

  protected async download(format: ReportFormat): Promise<void> {
    try {
      await this.facade.downloadReport(format);
    } catch (error) {
      this.notifier.error((error as Problem).detail);
    }
  }
}
