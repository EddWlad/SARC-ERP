import { Component, computed, inject, input } from '@angular/core';

import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { SeriesColor } from '../../../../shared/ui/chart/chart-theme';
import { ChartView, ChartSeries } from '../../../../shared/ui/chart/chart-view';

export interface ShareItem {
  readonly label: string;
  readonly energyKwh?: number | null;
  readonly sessions: number;
}

/** Orden fijo de colores: cada categoria conserva el suyo aunque cambien los filtros. */
const SLOT_COLORS: readonly SeriesColor[] = ['series-1', 'series-2', 'series-3'];

/**
 * Participacion de cada categoria en la energia (dona) con su leyenda: color, nombre, kWh y
 * porcentaje visibles, para que la lectura nunca dependa solo del color.
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
        [format]="formatKwh"
        [description]="description()"
      />
      <ul class="legend">
        @for (item of items(); track item.label; let index = $index) {
          <li>
            <span class="swatch" [style.background]="'var(--sarc-' + colors[index] + ')'"></span>
            <span class="name">{{ item.label }}</span>
            <span class="value">{{ energyOf(item) }}</span>
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

  protected readonly quantities = inject(QuantityFormat);
  protected readonly colors = SLOT_COLORS;
  protected readonly formatKwh = (value: number) => this.quantities.kwh(value);

  protected readonly total = computed(() =>
    this.items().reduce((sum, item) => sum + (item.energyKwh ?? 0), 0),
  );
  protected readonly labels = computed(() => this.items().map((item) => item.label));
  protected readonly series = computed<ChartSeries>(() => ({
    label: 'Energía (kWh)',
    values: this.items().map((item) => item.energyKwh ?? null),
    colors: this.items().map((_, index) => SLOT_COLORS[index % SLOT_COLORS.length]),
  }));
  protected readonly description = computed(
    () =>
      `${this.title()}: ` +
      this.items()
        .map((item) => `${item.label} ${this.shareOf(item)}`)
        .join(', '),
  );
  /** Sin total no hay porcentajes: ni sesiones, ni lecturas y un cero conocido se dicen distinto. */
  protected readonly emptyMessage = computed(() => {
    const items = this.items();
    if (items.every((item) => item.sessions === 0)) {
      return 'Sin sesiones oficiales en este periodo.';
    }
    return items.every((item) => item.sessions === 0 || item.energyKwh == null)
      ? 'Sin lecturas de energía en este periodo.'
      : `Energía conocida: ${this.quantities.knownEnergy(0)} en este periodo.`;
  });

  protected energyOf(item: ShareItem): string {
    return item.sessions === 0 ? 'Sin sesiones' : this.quantities.knownEnergy(item.energyKwh);
  }

  protected shareOf(item: ShareItem): string {
    return item.energyKwh == null
      ? 'Sin datos'
      : this.quantities.percent(item.energyKwh, this.total());
  }
}
