import { DatePipe } from '@angular/common';
import { Component, input, signal } from '@angular/core';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { PeriodSelection } from '../../application/period-selection';
import {
  inclusiveRange,
  lastDay,
  MAX_PERIOD_DAYS,
  parseIsoDate,
  PERIOD_LABELS,
  PERIOD_PRESETS,
  PeriodPreset,
  rangeDays,
} from '../../domain/period';

/**
 * Accesos rapidos de periodo (Hoy, ultimos 7 dias, este mes, mes anterior, este año) y rango
 * personalizado. Los chips se acomodan en varias lineas en pantallas angostas.
 */
@Component({
  selector: 'sarc-period-filter',
  imports: [DatePipe, MatChipsModule, MatDatepickerModule, MatFormFieldModule, MatInputModule],
  providers: [provideNativeDateAdapter()],
  template: `
    <mat-chip-listbox
      aria-label="Periodo"
      [value]="period().preset()"
      (change)="onPreset($event.value)"
      class="presets"
    >
      @for (preset of presets; track preset) {
        <mat-chip-option [value]="preset" [selectable]="period().preset() !== preset">
          {{ labels[preset] }}
        </mat-chip-option>
      }
    </mat-chip-listbox>

    @if (period().preset() === 'custom') {
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="range">
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

    <p class="current" aria-live="polite">
      {{ from() | date: 'longDate' }}
      @if (days() > 1) {
        al {{ to() | date: 'longDate' }}
      }
    </p>
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--sarc-space-2) var(--sarc-space-4);
    }

    .range {
      min-width: 260px;
    }

    .current {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class PeriodFilter {
  readonly period = input.required<PeriodSelection>();

  protected readonly presets = PERIOD_PRESETS;
  protected readonly labels = PERIOD_LABELS;
  protected readonly start = signal<Date | null>(null);
  protected readonly rangeError = signal<string | undefined>(undefined);

  protected from(): Date {
    return parseIsoDate(this.period().range().from);
  }

  protected to(): Date {
    return lastDay(this.period().range());
  }

  protected days(): number {
    return rangeDays(this.period().range());
  }

  protected onPreset(preset: PeriodPreset | undefined): void {
    // Un chip ya elegido no se deselecciona: siempre hay un periodo.
    if (preset !== undefined) {
      this.period().choose(preset);
    }
  }

  protected onEnd(end: Date | null): void {
    const start = this.start();
    if (start === null || end === null) {
      return;
    }
    const range = inclusiveRange(start, end);
    if (rangeDays(range) > MAX_PERIOD_DAYS) {
      this.rangeError.set(`El periodo no puede superar ${MAX_PERIOD_DAYS} días`);
      return;
    }
    this.rangeError.set(undefined);
    this.period().chooseRange(range);
  }
}
