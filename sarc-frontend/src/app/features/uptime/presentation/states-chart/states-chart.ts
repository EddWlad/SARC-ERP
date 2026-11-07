import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { StateCompositionResponse } from '../../../../core/api/generated';

import { QuantityFormat } from '../../../../shared/format/quantity-format';

import { ChartSeries, ChartView } from '../../../../shared/ui/chart/chart-view';

const STATES = [
  { key: 'available', field: 'availableMinutes', color: 'series-1', label: 'Disponible' },
  { key: 'occupied', field: 'occupiedMinutes', color: 'series-2', label: 'Ocupado' },
  { key: 'fault', field: 'faultMinutes', color: 'series-3', label: 'Con falla' },
  { key: 'offline', field: 'offlineMinutes', color: 'series-4', label: 'Fuera de línea' },
] as const;

/** Distribución de los tiempos fuente de la misma población/revisión visible. */

@Component({
  selector: 'sarc-states-chart',
  imports: [ChartView],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section [attr.aria-label]="title()">
      <h3 [attr.aria-level]="level()">{{ title() }}</h3>

      <p>
        {{ composition().completeMeasurements }} de
        {{ composition().selectedMeasurements }} mediciones con los cuatro estados informados.
      </p>

      @if (composition().totalKnownMinutes > 0) {
        <sarc-chart-view
          kind="doughnut"
          [labels]="labels"
          [series]="series()"
          [format]="formatMinutes"
          [description]="title() + '. Porciones del total conocido; cobertura en la leyenda.'"
        />
      } @else {
        <p role="status">Sin tiempos positivos informados; no se dibuja un círculo.</p>
      }

      <ul class="legend">
        @for (state of states; track state.key) {
          <li>
            <span
              class="swatch"
              [style.background-color]="'var(--sarc-' + state.color + ')'"
              aria-hidden="true"
            ></span>
            <span class="name">{{ state.label }}</span>
            <strong>{{ value(state.field) }}</strong>
            <span class="coverage">
              {{ known(state.key) }}/{{ composition().selectedMeasurements }} mediciones
            </span>
          </li>
        }
      </ul>
    </section>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }

    h3 {
      font: var(--mat-sys-title-medium);
      margin: 0 0 var(--sarc-space-2);
    }

    p {
      color: var(--mat-sys-on-surface-variant);
      overflow-wrap: anywhere;
    }

    .swatch {
      grid-area: swatch;
      width: var(--sarc-space-4);
      height: var(--sarc-space-4);
      border-radius: 50%;
      border: 1px solid var(--mat-sys-on-surface);
      align-self: center;
    }

    .legend {
      list-style: none;
      padding: 0;
      margin: var(--sarc-space-3) 0 0;
    }

    /* Cada estado ocupa una fila: marca, nombre con su cobertura debajo y la cifra a la derecha. */
    li {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      grid-template-areas:
        'swatch name value'
        'swatch coverage value';
      align-items: center;
      column-gap: var(--sarc-space-3);
      padding: var(--sarc-space-2) 0;

      & + & {
        border-top: 1px solid var(--mat-sys-outline-variant);
      }
    }

    .name {
      grid-area: name;
      overflow-wrap: anywhere;
    }

    strong {
      grid-area: value;
      text-align: end;
      font-variant-numeric: tabular-nums;
    }

    .coverage {
      grid-area: coverage;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class StatesChart {
  readonly composition = input.required<StateCompositionResponse>();
  /** Nivel del encabezado: 3 dentro de una seccion del tablero, 2 en paginas sin seccion previa. */
  readonly level = input(3);

  protected readonly states = STATES;

  protected readonly labels = STATES.map((state) => state.label);

  private readonly quantities = inject(QuantityFormat);

  readonly title = computed(() =>
    this.composition().isComplete
      ? 'Distribución de estados'
      : 'Distribución de tiempos informados',
  );

  readonly series = computed<ChartSeries>(() => ({
    label: 'Minutos informados',
    values: STATES.map((state) => this.composition()[state.field] ?? null),
    colors: ['series-1', 'series-2', 'series-3', 'series-4'],
  }));

  protected readonly formatMinutes = (value: number) => this.quantities.measure(value) + ' min';

  protected known(key: string): number {
    return this.composition().knownMeasurementsByState[key] ?? 0;
  }

  protected value(field: (typeof STATES)[number]['field']): string {
    const value = this.composition()[field];

    return value == null ? 'No informado' : this.formatMinutes(value);
  }
}
