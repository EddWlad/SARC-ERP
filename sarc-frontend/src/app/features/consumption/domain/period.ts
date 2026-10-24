/**
 * Periodos del tablero de consumo (PRD 3.2): dias locales, desde inclusivo y hasta exclusivo, que
 * es como los espera la API. Todo se calcula con la fecha local del navegador (hora de Ecuador en
 * operacion), nunca con UTC, para que "hoy" sea el dia que ve el usuario.
 */
export type PeriodPreset = 'today' | 'last7' | 'thisMonth' | 'lastMonth' | 'thisYear' | 'custom';

export interface DateRange {
  /** Primer dia, ISO yyyy-MM-dd, inclusivo. */
  readonly from: string;
  /** Dia siguiente al ultimo, ISO yyyy-MM-dd, exclusivo. */
  readonly to: string;
}

export const PERIOD_LABELS: Readonly<Record<PeriodPreset, string>> = {
  today: 'Hoy',
  last7: 'Últimos 7 días',
  thisMonth: 'Este mes',
  lastMonth: 'Mes anterior',
  thisYear: 'Este año',
  custom: 'Personalizado',
};

export const PERIOD_PRESETS = Object.keys(PERIOD_LABELS) as readonly PeriodPreset[];

/** El backend rechaza periodos mas largos. */
export const MAX_PERIOD_DAYS = 366;

export function presetRange(preset: Exclude<PeriodPreset, 'custom'>, today: Date): DateRange {
  const day = startOfDay(today);
  switch (preset) {
    case 'today':
      return range(day, addDays(day, 1));
    case 'last7':
      return range(addDays(day, -6), addDays(day, 1));
    case 'thisMonth':
      return range(new Date(day.getFullYear(), day.getMonth(), 1), addDays(day, 1));
    case 'lastMonth':
      return range(
        new Date(day.getFullYear(), day.getMonth() - 1, 1),
        new Date(day.getFullYear(), day.getMonth(), 1),
      );
    case 'thisYear':
      return range(new Date(day.getFullYear(), 0, 1), addDays(day, 1));
  }
}

/** Rango de un selector de fechas donde el usuario elige el primer y el ultimo dia (inclusivos). */
export function inclusiveRange(start: Date, end: Date): DateRange {
  return range(startOfDay(start), addDays(startOfDay(end), 1));
}

/** Ultimo dia incluido, para mostrar "del 1 al 31". */
export function lastDay(range: DateRange): Date {
  return addDays(parseIsoDate(range.to), -1);
}

export function rangeDays(range: DateRange): number {
  return Math.round(
    (parseIsoDate(range.to).getTime() - parseIsoDate(range.from).getTime()) / 86_400_000,
  );
}

/** yyyy-MM-dd como fecha local (no UTC: `new Date('2026-10-01')` seria el dia anterior en Ecuador). */
export function parseIsoDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}

export function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function range(from: Date, to: Date): DateRange {
  return { from: isoDate(from), to: isoDate(to) };
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}
