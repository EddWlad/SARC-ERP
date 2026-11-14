import { Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { LucideFilterX } from '@lucide/angular';

import { ScopeConnectorResponse } from '../../../../core/api/generated';
import {
  BRAND_LABELS,
  BRANDS,
  NETWORK_LABELS,
  NETWORKS,
  TYPE_LABELS,
  TYPES,
} from '../../../../shared/vocabulary/charging-infrastructure';
import { UptimeFilters } from '../../application/dashboard-facade';
import { ConnectorFilter } from '../connector-filter/connector-filter';

type PeriodChoice = 'LATEST' | 'OFFICIAL' | 'ROLLING_24H' | 'ROLLING_7D' | 'ROLLING_30D' | 'CUSTOM';

const PERIOD_LABELS: Readonly<Record<PeriodChoice, string>> = {
  LATEST: 'Más reciente',
  OFFICIAL: 'Semanas oficiales',
  ROLLING_24H: 'Últimas 24 horas',
  ROLLING_7D: 'Últimos 7 días',
  ROLLING_30D: 'Últimos 30 días',
  CUSTOM: 'Personalizado',
};

const NO_PERIOD = {
  periodKind: undefined,
  periodType: undefined,
  from: undefined,
  to: undefined,
  referenceWeek: undefined,
  temporalSection: undefined,
};
const MAX_PERIOD_DAYS = 366;
const DAY_MS = 86_400_000;

const isoDate = (date: Date): string =>
  [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-');

/** Periodo en chips y filtros en una fila, el mismo patron de la barra de Consumo. */
@Component({
  selector: 'sarc-uptime-filter-bar',
  imports: [
    MatButtonModule,
    MatChipsModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    LucideFilterX,
    ConnectorFilter,
  ],
  providers: [provideNativeDateAdapter()],
  template: `
    <div class="period">
      <mat-chip-listbox aria-label="Período" [value]="choice()" (change)="choose($event.value)">
        @for (option of choices; track option) {
          <mat-chip-option [value]="option" [selectable]="choice() !== option">
            {{ labels[option] }}
          </mat-chip-option>
        }
      </mat-chip-listbox>
      @if (choice() === 'ROLLING_7D') {
        <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
          <mat-label>Semana de referencia (lunes)</mat-label>
          <input
            matInput
            type="date"
            [value]="filters().referenceWeek ?? ''"
            (change)="referenceChanged($any($event.target).value)"
          />
          <mat-hint>Vacío: última referencia con datos validados.</mat-hint>
          @if (rangeError(); as message) {
            <mat-error>{{ message }}</mat-error>
          }
        </mat-form-field>
      }
      @if (choice() === 'CUSTOM') {
        <mat-form-field
          floatLabel="always"
          appearance="outline"
          subscriptSizing="dynamic"
          class="range"
        >
          <mat-label>Rango de fechas</mat-label>
          <mat-date-range-input [rangePicker]="picker">
            <input matStartDate placeholder="Desde" (dateChange)="start.set($event.value)" />
            <input matEndDate placeholder="Hasta" (dateChange)="onEnd($event.value)" />
          </mat-date-range-input>
          <mat-datepicker-toggle matIconSuffix [for]="picker" />
          <mat-date-range-picker #picker />
          @if (rangeError(); as message) {
            <mat-error>{{ message }}</mat-error>
          }
        </mat-form-field>
      }
    </div>
    <section class="fields" aria-label="Filtros">
      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Red</mat-label>
        <mat-select
          [value]="filters().network ?? ''"
          (selectionChange)="filtersChange.emit({ network: $event.value })"
        >
          <mat-option value="">Todas</mat-option>
          @for (network of networks; track network) {
            <mat-option [value]="network">{{ networkLabels[network] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Tipo</mat-label>
        <mat-select
          [value]="filters().type ?? ''"
          (selectionChange)="filtersChange.emit({ type: $event.value })"
        >
          <mat-option value="">Todos</mat-option>
          @for (type of types; track type) {
            <mat-option [value]="type">{{ typeLabels[type] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <mat-form-field floatLabel="always" appearance="outline" subscriptSizing="dynamic">
        <mat-label>Marca</mat-label>
        <mat-select
          [value]="filters().brand ?? ''"
          (selectionChange)="filtersChange.emit({ brand: $event.value })"
        >
          <mat-option value="">Todas</mat-option>
          @for (brand of brands; track brand) {
            <mat-option [value]="brand">{{ brandLabels[brand] }}</mat-option>
          }
        </mat-select>
      </mat-form-field>

      <sarc-connector-filter
        [options]="scope()"
        [equipmentId]="filters().equipmentId ?? ''"
        [connectorId]="filters().connectorId ?? ''"
        (equipmentChange)="filtersChange.emit({ equipmentId: $event, connectorId: undefined })"
        (connectorChange)="filtersChange.emit({ connectorId: $event })"
        (searched)="searched.emit($event)"
      />

      @if (hasFilters()) {
        <button matButton type="button" class="clear" (click)="clear()">
          <svg lucideFilterX [size]="18"></svg>
          Limpiar filtros
        </button>
      }
    </section>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      margin-bottom: var(--sarc-space-5);
    }

    .period {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-2) var(--sarc-space-4);
    }

    .range {
      min-width: 260px;
    }

    .fields {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(136px, 1fr));
      gap: var(--sarc-space-3);
      // Arriba y no al centro: una ayuda bajo un campo no debe subir su contorno respecto de los demás.
      align-items: start;
    }

    // Botón de 40px centrado sobre el contorno de 56px de los campos.
    .clear {
      justify-self: start;
      margin-top: var(--sarc-space-2);
    }
  `,
})
export class UptimeFilterBar {
  readonly filters = input.required<UptimeFilters>();
  readonly scope = input.required<readonly ScopeConnectorResponse[]>();
  readonly hasFilters = input.required<boolean>();
  readonly filtersChange = output<Partial<UptimeFilters>>();
  readonly cleared = output<void>();
  readonly searched = output<string>();

  protected readonly choices = Object.keys(PERIOD_LABELS) as PeriodChoice[];
  protected readonly labels = PERIOD_LABELS;
  protected readonly networks = NETWORKS;
  protected readonly networkLabels = NETWORK_LABELS;
  protected readonly types = TYPES;
  protected readonly typeLabels = TYPE_LABELS;
  protected readonly brands = BRANDS;
  protected readonly brandLabels = BRAND_LABELS;
  protected readonly choice = signal<PeriodChoice>('LATEST');
  protected readonly start = signal<Date | null>(null);
  protected readonly rangeError = signal<string | undefined>(undefined);

  protected choose(choice: PeriodChoice | undefined): void {
    // Un chip ya elegido no se deselecciona: siempre hay un periodo.
    if (choice === undefined) return;
    this.choice.set(choice);
    this.rangeError.set(undefined);
    if (choice === 'LATEST') {
      this.filtersChange.emit(NO_PERIOD);
    } else if (choice === 'OFFICIAL') {
      this.filtersChange.emit({ ...NO_PERIOD, periodKind: 'OFFICIAL' });
    } else if (choice !== 'CUSTOM') {
      this.filtersChange.emit({ ...NO_PERIOD, periodKind: 'PROVISIONAL', periodType: choice });
    }
  }

  /** El rango elegido incluye el ultimo dia; la API espera el dia siguiente, exclusivo. */
  protected onEnd(end: Date | null): void {
    const start = this.start();
    if (start === null || end === null) return;
    const next = new Date(end.getFullYear(), end.getMonth(), end.getDate() + 1);
    if (Math.round((next.getTime() - start.getTime()) / DAY_MS) > MAX_PERIOD_DAYS) {
      this.rangeError.set(`El período no puede superar ${MAX_PERIOD_DAYS} días`);
      return;
    }
    this.rangeError.set(undefined);
    this.filtersChange.emit({ ...NO_PERIOD, from: isoDate(start), to: isoDate(next) });
  }

  protected referenceChanged(value: string): void {
    if (value && new Date(value + 'T12:00:00Z').getUTCDay() !== 1) {
      this.rangeError.set('Elige un lunes como referencia.');
      return;
    }
    this.rangeError.set(undefined);
    this.filtersChange.emit({
      ...NO_PERIOD,
      periodKind: 'PROVISIONAL',
      periodType: 'ROLLING_7D',
      referenceWeek: value || undefined,
    });
  }

  protected clear(): void {
    this.choice.set('LATEST');
    this.start.set(null);
    this.rangeError.set(undefined);
    this.cleared.emit();
  }
}
