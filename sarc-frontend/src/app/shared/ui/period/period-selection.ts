import { computed, Signal, signal } from '@angular/core';

import { DateRange, PeriodPreset, presetRange, QuickPreset } from './period';

/**
 * Periodo elegido en una pantalla: un acceso rapido o un rango personalizado. Se compone dentro
 * de cada facade (tablero, historico) para que todas calculen el rango igual. Los accesos rapidos
 * se recalculan cuando cambia {@code today}. «Todo el historial» (informes de incidencias) no tiene rango: se puede
 * elegir como periodo inicial y {@link allHistory} lo avisa; el rango queda en el de {@code fallback}.
 */
export class PeriodSelection {
  readonly preset;
  /** Rango vigente; "personalizado" sin fechas aun usa el acceso rapido inicial. */
  readonly range;
  /** Se pidio todo el historial: no hay rango que enviar. */
  readonly allHistory;
  private readonly custom = signal<DateRange | undefined>(undefined);

  constructor(
    initial: QuickPreset | 'allHistory',
    today: Signal<Date>,
    fallback: QuickPreset = 'thisMonth',
  ) {
    const base: QuickPreset = initial === 'allHistory' ? fallback : initial;
    this.preset = signal<PeriodPreset>(initial);
    this.allHistory = computed(() => this.preset() === 'allHistory');
    this.range = computed<DateRange>(() => {
      const preset = this.preset();
      if (preset === 'custom') {
        return this.custom() ?? presetRange(base, today());
      }
      return presetRange(preset === 'allHistory' ? base : preset, today());
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
