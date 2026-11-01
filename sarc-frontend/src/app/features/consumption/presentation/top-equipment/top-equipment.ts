import { Component, computed, inject, input } from '@angular/core';

import { QuantityFormat } from '../../../../shared/format/quantity-format';
import { ChartSeries, ChartView } from '../../../../shared/ui/chart/chart-view';
import { RankedEquipment } from '../../domain/consumption';

/** Top de equipos por energia: barras horizontales y, debajo, el ranking con sus cifras. */
@Component({
  selector: 'sarc-top-equipment',
  imports: [ChartView],
  template: `
    <sarc-chart-view
      [style.--sarc-chart-height]="chartHeight()"
      kind="bar"
      [horizontal]="true"
      [labels]="labels()"
      [series]="energy()"
      [format]="formatKwh"
      description="Los equipos con más energía entregada en el periodo"
    />
    <ol class="ranking">
      @for (item of items(); track item.equipment?.id ?? $index) {
        <li>
          <span class="position">{{ item.position }}</span>
          <span class="name">
            {{ item.equipment?.name ?? 'Sin nombre' }}
            @if (item.equipment?.assignedCode) {
              <span class="muted">· {{ item.equipment.assignedCode }}</span>
            }
          </span>
          <span class="muted sessions">{{ quantities.count(item.sessions) }} sesiones</span>
          <strong>{{ quantities.kwh(item.energyKwh) }} kWh</strong>
        </li>
      }
    </ol>
    @if (items().length > 5) {
      <p class="muted">El Top 5 incluye más de cinco equipos por empate en el quinto lugar.</p>
    }
    @if (withoutEnergy() > 0) {
      <p class="muted excluded">
        {{
          quantities.amount(
            withoutEnergy(),
            'equipo fuera del ranking por no tener energía informada',
            'equipos fuera del ranking por no tener energía informada'
          )
        }}
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
      --sarc-chart-height: 220px;
    }

    .excluded {
      margin: var(--sarc-space-2) 0 0;
    }

    .ranking {
      margin: var(--sarc-space-3) 0 0;
      padding: 0;
      list-style: none;

      li {
        display: grid;
        grid-template-columns: 32px minmax(0, 1fr) auto auto;
        align-items: center;
        gap: var(--sarc-space-3);
        padding: var(--sarc-space-2) 0;

        & + li {
          border-top: 1px solid var(--mat-sys-outline-variant);
        }

        @media (max-width: 599.98px) {
          grid-template-columns: 32px minmax(0, 1fr) auto;

          .sessions {
            display: none;
          }
        }
      }

      strong {
        color: var(--mat-sys-primary);
        font: var(--mat-sys-title-medium);
      }
    }

    .position {
      display: grid;
      place-items: center;
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      font: var(--mat-sys-label-large);
    }

    .name {
      overflow-wrap: anywhere;
    }

    .muted {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class TopEquipment {
  readonly items = input.required<readonly RankedEquipment[]>();
  /** Equipos con sesiones oficiales que no entran al ranking por no tener energia informada. */
  readonly withoutEnergy = input(0);

  protected readonly quantities = inject(QuantityFormat);
  protected readonly formatKwh = (value: number) => this.quantities.kwh(value);

  protected readonly chartHeight = computed(() => `${Math.max(220, this.items().length * 36)}px`);
  protected readonly labels = computed(() =>
    this.items().map((item) => item.equipment?.name ?? 'Sin nombre'),
  );
  protected readonly energy = computed<ChartSeries>(() => ({
    label: 'Energía (kWh)',
    values: this.items().map((item) => item.energyKwh),
    colors: 'series-1',
  }));
}
