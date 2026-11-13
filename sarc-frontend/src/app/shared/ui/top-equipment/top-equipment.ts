import { Component, computed, input } from '@angular/core';

import { ChartSeries, ChartView } from '../chart/chart-view';

/** Una fila del ranking. La posicion se repite entre empatados, asi que la trae quien lo arma. */
export interface RankedRow {
  readonly position: number;
  readonly name: string;
  readonly code?: string | null;
  /** Lo que ordena y se grafica. */
  readonly value: number;
  /** El valor escrito con su unidad. */
  readonly valueText: string;
  /** Cifra complementaria (sesiones, ventanas y dias); se oculta en pantallas angostas. */
  readonly detail: string;
}

/** Top de equipos: barras horizontales y, debajo, el ranking con sus cifras. No sabe de unidades. */
@Component({
  selector: 'sarc-top-equipment',
  imports: [ChartView],
  template: `
    <sarc-chart-view
      [style.--sarc-chart-height]="chartHeight()"
      kind="bar"
      [horizontal]="true"
      [labels]="labels()"
      [series]="series()"
      [format]="chartFormat()"
      [description]="description()"
    />
    <ol class="ranking">
      @for (item of items(); track $index) {
        <li>
          <span class="position">{{ item.position }}</span>
          <span class="name">
            {{ item.name }}
            @if (item.code) {
              <span class="muted">· {{ item.code }}</span>
            }
          </span>
          <span class="muted detail">{{ item.detail }}</span>
          <strong>{{ item.valueText }}</strong>
        </li>
      }
    </ol>
    @if (tiesNote() && items().length > topSize()) {
      <p class="muted">{{ tiesNote() }}</p>
    }
    @for (note of notes(); track note) {
      <p class="muted excluded">{{ note }}</p>
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

          .detail {
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
  readonly items = input.required<readonly RankedRow[]>();
  /** Nombre de la serie y unidad, para el grafico (p. ej. "Energía (kWh)"). */
  readonly seriesLabel = input.required<string>();
  /** Como se escribe un valor en el grafico. */
  readonly chartFormat = input.required<(value: number) => string>();
  /** Que muestra el grafico, para lectores de pantalla. */
  readonly description = input.required<string>();
  /** Tamano del ranking: con mas filas que esto, hubo empate en el ultimo puesto. */
  readonly topSize = input(5);
  /** Aviso cuando el ranking supera su tamano por empate; sin texto, no se avisa. */
  readonly tiesNote = input('');
  /** Avisos al pie, por ejemplo lo que quedo fuera del ranking y por que. */
  readonly notes = input<readonly string[]>([]);

  protected readonly chartHeight = computed(() => `${Math.max(220, this.items().length * 36)}px`);
  protected readonly labels = computed(() => this.items().map((item) => item.name));
  protected readonly series = computed<ChartSeries>(() => ({
    label: this.seriesLabel(),
    values: this.items().map((item) => item.value),
    colors: 'series-1',
  }));
}
