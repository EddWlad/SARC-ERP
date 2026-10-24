import { computed, Signal, signal } from '@angular/core';

import { DateRange, PeriodPreset, presetRange } from '../domain/period';

type QuickPreset = Exclude<PeriodPreset, 'custom'>;

/**
 * Periodo elegido en una pantalla: un acceso rapido o un rango personalizado. Se compone dentro
 * de cada facade (tablero, historico) para que todas calculen el rango igual. Los accesos rapidos
 * se recalculan cuando cambia {@code today}.
 */
export class PeriodSelection {
  readonly preset;
  /** Rango vigente; "personalizado" sin fechas aun usa el acceso rapido inicial. */
  readonly range;
  private readonly custom = signal<DateRange | undefined>(undefined);

  constructor(initial: QuickPreset, today: Signal<Date>) {
    this.preset = signal<PeriodPreset>(initial);
    this.range = computed<DateRange>(() => {
      const preset = this.preset();
      if (preset === 'custom') {
        return this.custom() ?? presetRange(initial, today());
      }
      return presetRange(preset, today());
    });
  }

  choose(preset: PeriodPreset): void {
    this.preset.set(preset);
  }

  chooseRange(range: DateRange): void {
    this.custom.set(range);
    this.preset.set('custom');
  }
}
