import { Component, computed, inject, input } from '@angular/core';

import { QuantityFormat } from '../../format/quantity-format';
import { SeriesColor } from '../chart/chart-theme';
import { ChartView, ChartSeries } from '../chart/chart-view';

/** Una categoria del reparto. `value` es lo que se suma y se grafica; nulo es «sin datos», distinto de cero. */
export interface ShareItem {
  readonly label: string;
  readonly value: number | null;
  /** Como se escribe el valor en la leyenda, con su unidad; lo arma quien usa el grafico. */
  readonly valueText: string;
}

/** Orden fijo de colores: cada categoria conserva el suyo aunque cambien los filtros. */
const SLOT_COLORS: readonly SeriesColor[] = ['series-1', 'series-2', 'series-3', 'series-4'];

/**
 * Participacion de cada categoria en un total (dona) con su leyenda: color, nombre, valor y porcentaje
 * visibles, para que la lectura nunca dependa solo del color. No sabe de unidades: las pone quien lo usa.
 */
@Component({
  selector: 'sarc-share-chart',
  imports: [ChartView],
  template: `
    @if (total() > 0) {
      <sarc-chart-view
        kind="doughnut"
        [labels]="labels()"
        [series]="series()"
        [format]="chartFormat()"
        [description]="description()"
      />
      <ul class="legend">
        @for (item of items(); track item.label; let index = $index) {
          <li>
            <span class="swatch" [style.background]="'var(--sarc-' + colors[index] + ')'"></span>
            <span class="name">{{ item.label }}</span>
            <span class="value">{{ item.valueText }}</span>
            <strong class="share">{{ shareOf(item) }}</strong>
          </li>
        }
      </ul>
    } @else {
      <p class="empty">{{ emptyMessage() }}</p>
    }
  `,
  styles: `
    :host {
      display: block;
      --sarc-chart-height: 190px;
    }

    .legend {
      margin: var(--sarc-space-3) 0 0;
      padding: 0;
      list-style: none;

      li {
        display: grid;
        grid-template-columns: auto 1fr auto auto;
        align-items: center;
        gap: var(--sarc-space-2);
        padding: var(--sarc-space-1) 0;
        font: var(--mat-sys-body-medium);
      }
    }

    .swatch {
      width: 12px;
      height: 12px;
      border-radius: var(--sarc-radius-sm);
    }

    .value {
      color: var(--mat-sys-on-surface-variant);
    }

    .share {
      min-width: 56px;
      color: var(--mat-sys-on-surface);
      text-align: end;
    }

    .empty {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class ShareChart {
  readonly items = input.required<readonly ShareItem[]>();
  /** Que se reparte, para lectores de pantalla (p. ej. "Energía por red"). */
  readonly title = input.required<string>();
  /** Nombre de la serie y unidad, para el grafico (p. ej. "Energía (kWh)"). */
  readonly seriesLabel = input.required<string>();
  /** Como se escribe un valor en el grafico. */
  readonly chartFormat = input.required<(value: number) => string>();
  /** Lo que se dice cuando no hay total: depende de por que falta, asi que lo decide quien lo usa. */
  readonly emptyMessage = input.required<string>();

  protected readonly quantities = inject(QuantityFormat);
  protected readonly colors = SLOT_COLORS;

  protected readonly total = computed(() =>
    this.items().reduce((sum, item) => sum + (item.value ?? 0), 0),
  );
  protected readonly labels = computed(() => this.items().map((item) => item.label));
  protected readonly series = computed<ChartSeries>(() => ({
    label: this.seriesLabel(),
    values: this.items().map((item) => item.value),
    colors: this.items().map((_, index) => SLOT_COLORS[index % SLOT_COLORS.length]),
  }));
  protected readonly description = computed(
    () =>
      `${this.title()}: ` +
      this.items()
        .map((item) => `${item.label} ${this.shareOf(item)}`)
        .join(', '),
  );

  protected shareOf(item: ShareItem): string {
    return item.value === null ? 'Sin datos' : this.quantities.percent(item.value, this.total());
  }
}
