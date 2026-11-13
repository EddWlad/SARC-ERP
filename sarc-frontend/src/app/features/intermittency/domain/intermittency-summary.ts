import { IntermittencySummaryResponse } from '../../../core/api/generated';
import { DurationBand } from './intermittency-labels';
import { addDays, durationText, formatDay, presetLabel } from './intermittency-time';

export type IntermittencySummary = IntermittencySummaryResponse;
export type SummaryPeriod = IntermittencySummary['period'];
export type TopPoint = IntermittencySummary['top'][number];

/** «1:30 h»: horas y minutos con su unidad, como se muestra todo tiempo del tablero. */
export function hoursText(minutes: number): string {
  return `${durationText(minutes)} h`;
}

/** Minutos como horas decimales, para las graficas; el tooltip las escribe con {@link hoursFromChart}. */
export function toHours(minutes: number): number {
  return minutes / 60;
}

/** Valor de una grafica en horas, escrito como horas y minutos. */
export function hoursFromChart(hours: number): string {
  return hoursText(Math.round(hours * 60));
}

/**
 * «Primera quincena · 01/10/2026 al 15/10/2026»: el tipo de periodo y su rango real, con el ultimo dia
 * incluido (la API entrega el fin exclusivo) (FR-031).
 */
export function summaryPeriodLabel(period: SummaryPeriod): string {
  const lastDay = addDays(period.to, -1);
  const range =
    lastDay === period.from
      ? formatDay(period.from)
      : `${formatDay(period.from)} al ${formatDay(lastDay)}`;
  return `${presetLabel(period.preset)} · ${range}`;
}

/** Franjas del pastel de duracion con sus etiquetas (FR-036). */
export const BAND_ORDER: readonly DurationBand[] = [
  'UNDER_5',
  'FROM_5_TO_14',
  'FROM_15_TO_59',
  'FROM_60_TO_239',
  'FROM_240',
];

/**
 * Posicion de cada fila del Top: los empatados en tiempo comparten posicion (1, 2, 2, 4), como en Consumo.
 * Las filas llegan ordenadas de mayor a menor tiempo.
 */
export function rankPositions(top: readonly TopPoint[]): number[] {
  return top.map((point) => top.findIndex((other) => other.minutes === point.minutes) + 1);
}
