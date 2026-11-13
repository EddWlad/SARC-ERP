import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { LucideFilterX } from '@lucide/angular';

import { KiaPointResponse } from '../../../../core/api/generated';
import {
  MAX_CUSTOM_DAYS,
  PeriodPreset,
  presetLabel,
  todayInOperatingZone,
} from '../../domain/intermittency-time';
import { PointsFilter } from '../points-filter/points-filter';

const PRESETS: readonly PeriodPreset[] = ['FIRST_HALF', 'SECOND_HALF', 'LAST_15_DAYS', 'CUSTOM'];

/**
 * Filtros del tablero de intermitencias en una fila sobre las graficas, con el mismo diseno que Consumo y
 * Uptime: periodo (primera quincena, segunda quincena, ultimos 15 dias o personalizado), mes, rango y puntos
 * KIA (FR-031, FR-032).
 */
@Component({
  selector: 'sarc-intermittency-filter-bar',
  imports: [
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    LucideFilterX,
    PointsFilter,
  ],
  template: `
    <mat-chip-listbox
      class="presets"
      aria-label="Periodo"
      [value]="preset()"
      (change)="onPreset($event.value)"
    >
      @for (option of presets; track option) {
        <mat-chip-option [value]="option" [selectable]="preset() !== option">
          {{ labelOf(option) }}
        </mat-chip-option>
      }
    </mat-chip-listbox>

    <section class="fields" aria-label="Filtros">
      @if (usesMonth()) {
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Mes</mat-label>
          <input
            matInput
            type="month"
            [max]="currentMonth()"
            [value]="month()"
            (change)="onMonth($event)"
          />
        </mat-form-field>
      }
      @if (preset() === 'CUSTOM') {
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Primer día</mat-label>
          <input
            matInput
            type="date"
            [max]="today()"
            [value]="firstDay()"
            (change)="rangeChange.emit({ firstDay: valueOf($event), lastDay: lastDay() })"
          />
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>Último día (incluido)</mat-label>
          <input
            matInput
            type="date"
            [min]="firstDay()"
            [max]="today()"
            [value]="lastDay()"
            (change)="rangeChange.emit({ firstDay: firstDay(), lastDay: valueOf($event) })"
          />
          <mat-hint>Hasta {{ maxDays }} días</mat-hint>
        </mat-form-field>
      }

      <sarc-points-filter [points]="points()" (pointsChange)="pointsChange.emit($event)" />

      @if (points().length > 0) {
        <button matButton type="button" class="clear" (click)="cleared.emit()">
          <svg lucideFilterX [size]="18" aria-hidden="true"></svg>
          Limpiar filtros
        </button>
      }
    </section>

    @if (rangeError()) {
      <p class="issue" role="alert">{{ rangeError() }}</p>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--sarc-space-3);
      margin-bottom: var(--sarc-space-5);
    }

    .fields {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: var(--sarc-space-3);
      align-items: center;
    }

    .clear {
      justify-self: start;
    }

    .issue {
      margin: 0;
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class IntermittencyFilterBar {
  readonly preset = input.required<PeriodPreset>();
  /** aaaa-mm de las quincenas. */
  readonly month = input.required<string>();
  readonly firstDay = input('');
  readonly lastDay = input('');
  readonly points = input.required<readonly KiaPointResponse[]>();
  /** Por que el rango personalizado no se consulta; vacio si se consulta. */
  readonly rangeError = input('');
  /** Hoy en la zona operativa: limita los dias y el mes elegibles; el tablero lo renueva al actualizar. */
  readonly today = input(todayInOperatingZone());

  readonly presetChange = output<PeriodPreset>();
  readonly monthChange = output<string>();
  readonly rangeChange = output<{ firstDay: string; lastDay: string }>();
  readonly pointsChange = output<readonly KiaPointResponse[]>();
  readonly cleared = output<void>();

  protected readonly presets = PRESETS;
  protected readonly maxDays = MAX_CUSTOM_DAYS;
  protected readonly currentMonth = computed(() => this.today().slice(0, 7));
  protected readonly usesMonth = computed(
    () => this.preset() === 'FIRST_HALF' || this.preset() === 'SECOND_HALF',
  );

  protected labelOf(preset: PeriodPreset): string {
    return presetLabel(preset);
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  /** Un mes vacio no es un periodo: el campo vuelve al mes vigente en lugar de quedar en blanco. */
  protected onMonth(event: Event): void {
    const value = this.valueOf(event);
    if (value) {
      this.monthChange.emit(value);
    } else {
      (event.target as HTMLInputElement).value = this.month();
    }
  }

  protected onPreset(preset: PeriodPreset | undefined): void {
    // Un chip ya elegido no se deselecciona: siempre hay un periodo.
    if (preset !== undefined) this.presetChange.emit(preset);
  }
}
