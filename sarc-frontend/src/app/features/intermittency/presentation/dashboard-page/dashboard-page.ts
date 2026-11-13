import { DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { LucideFileSpreadsheet, LucideFileText } from '@lucide/angular';

import { SessionStore } from '../../../../core/auth/session-store';
import { Problem } from '../../../../core/http/problem';
import { Notifier } from '../../../../core/notifications/notifier';
import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { ReportFormat } from '../../../../shared/reports/report-format';
import { ChartSeries, ChartView } from '../../../../shared/ui/chart/chart-view';
import { ShareChart, ShareItem } from '../../../../shared/ui/share-chart/share-chart';
import { RankedRow, TopEquipment } from '../../../../shared/ui/top-equipment/top-equipment';
import { DashboardFacade } from '../../application/dashboard-facade';
import { DURATION_BAND_LABELS } from '../../domain/intermittency-labels';
import {
  INTERMITTENCY_AVAILABILITY,
  INTERMITTENCY_EDITORS,
} from '../../domain/intermittency-permissions';
import {
  BAND_ORDER,
  hoursFromChart,
  hoursText,
  rankPositions,
  summaryPeriodLabel,
  toHours,
} from '../../domain/intermittency-summary';
import { durationText, formatDay, formatInstant, present } from '../../domain/intermittency-time';
import { AvailabilitySection } from '../availability-section/availability-section';
import { IntermittencyFilterBar } from '../intermittency-filter-bar/intermittency-filter-bar';
import { OutsideDetails } from '../outside-details/outside-details';

/**
 * Tablero gerencial de intermitencias (FR-031 a FR-037): indicadores oficiales, lo que queda fuera de ellos,
 * tendencia diaria, Top 10 por tiempo con empates y tres pasteles, para el periodo y los puntos elegidos. Todo
 * sale de las ventanas confirmadas; lo abierto, por revisar y de prueba se informa aparte.
 */
@Component({
  selector: 'sarc-intermittency-dashboard-page',
  imports: [
    DecimalPipe,
    MatButtonModule,
    MatProgressBarModule,
    RouterLink,
    AvailabilitySection,
    ChartView,
    IntermittencyFilterBar,
    LucideFileSpreadsheet,
    LucideFileText,
    OutsideDetails,
    ShareChart,
    TopEquipment,
  ],
  providers: [DashboardFacade],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class IntermittencyDashboardPage {
  private readonly notifier = inject(Notifier);
  protected readonly facade = inject(DashboardFacade);
  protected readonly quantities = inject(QuantityFormat);
  private readonly session = inject(SessionStore);
  protected readonly canEdit = this.session.hasAnyRole(INTERMITTENCY_EDITORS);
  /** La disponibilidad estimada es una excepcion de la prueba: sin el permiso, la seccion ni se monta (FR-006). */
  protected readonly canSeeAvailability = this.session.hasAnyRole(INTERMITTENCY_AVAILABILITY);

  protected readonly hoursText = hoursText;
  protected readonly durationText = durationText;
  protected readonly formatHours = hoursFromChart;
  protected readonly formatCount = (value: number) => this.quantities.count(value);
  protected readonly periodLabel = summaryPeriodLabel;
  protected readonly present = present;
  protected readonly emptyShare = 'Sin tiempo confirmado en este periodo.';
  protected readonly tiesNote =
    'El Top 10 incluye más de diez cargadores por empate en el décimo lugar.';

  protected async download(format: ReportFormat): Promise<void> {
    try {
      await this.facade.downloadReport(format);
    } catch (error) {
      this.notifier.error(
        (error as Partial<Problem> | undefined)?.detail || 'No se pudo generar el informe.',
      );
    }
  }

  protected clock(instant: string): string {
    return formatInstant(instant);
  }

  protected plural(count: number, one: string, many: string): string {
    return `${this.quantities.count(count)} ${count === 1 ? one : many}`;
  }

  /** Dias del periodo hasta el corte, como «05/10». */
  protected readonly days = computed(() =>
    (this.facade.summary()?.daily ?? []).map((day) => formatDay(day.day).slice(0, 5)),
  );
  protected readonly dailyHours = computed<ChartSeries>(() => ({
    label: 'Tiempo confirmado (h)',
    values: (this.facade.summary()?.daily ?? []).map((day) => toHours(day.minutes)),
    colors: 'series-1',
  }));
  protected readonly dailyWindows = computed<ChartSeries>(() => ({
    label: 'Ventanas',
    values: (this.facade.summary()?.daily ?? []).map((day) => day.windows),
    colors: 'series-1',
  }));

  protected readonly byBand = computed<ShareItem[]>(() => {
    const bands = this.facade.summary()?.byDurationBand ?? [];
    return BAND_ORDER.map((band) => {
      const share = bands.find((candidate) => candidate.band === band);
      const minutes = share?.minutes ?? 0;
      return {
        label: DURATION_BAND_LABELS[band],
        value: minutes,
        valueText: `${hoursText(minutes)} · ${this.plural(share?.windows ?? 0, 'ventana', 'ventanas')}`,
      };
    });
  });
  protected readonly concentration = computed<ShareItem[]>(() => {
    const summary = this.facade.summary();
    if (!summary) return [];
    return [
      {
        label: summary.topExtendedByTies ? 'Top 10 (con empates)' : 'Top 10',
        value: summary.concentration.topMinutes,
        valueText: hoursText(summary.concentration.topMinutes),
      },
      {
        label: 'Resto de cargadores afectados',
        value: summary.concentration.restMinutes,
        valueText: hoursText(summary.concentration.restMinutes),
      },
    ];
  });
  protected readonly recurrence = computed<ShareItem[]>(() => {
    const summary = this.facade.summary();
    if (!summary) return [];
    return [
      {
        label: 'Recurrentes (3 o más días)',
        value: summary.recurrence.recurrentMinutes,
        valueText: hoursText(summary.recurrence.recurrentMinutes),
      },
      {
        label: 'No recurrentes',
        value: summary.recurrence.nonRecurrentMinutes,
        valueText: hoursText(summary.recurrence.nonRecurrentMinutes),
      },
    ];
  });

  protected readonly topRows = computed<RankedRow[]>(() => {
    const top = this.facade.summary()?.top ?? [];
    const positions = rankPositions(top);
    return top.map((point, index) => ({
      position: positions[index],
      name: point.name,
      code: point.code,
      value: toHours(point.minutes),
      valueText: hoursText(point.minutes),
      detail: `${this.plural(point.windows, 'ventana', 'ventanas')} · ${this.plural(point.days, 'día', 'días')}`,
    }));
  });

  /** Por revisar, en conflicto y pendientes de vincular: lo que alguien tiene que mirar (FR-033). */
  protected readonly toReview = computed(() => {
    const outside = this.facade.summary()?.outsideOfficial;
    return outside ? outside.review + outside.conflict + outside.pendingLink : 0;
  });
}
