/** Zona operativa: America/Guayaquil, UTC-5 todo el anio (no tiene horario de verano). */
export const OPERATING_OFFSET = '-05:00';

/** Un rango de dias aaaa-mm-dd con el fin exclusivo, como los recibe la API. */
export interface DateRange {
  readonly from: string;
  readonly to: string;
}

export type PeriodPreset = 'FIRST_HALF' | 'SECOND_HALF' | 'LAST_15_DAYS' | 'CUSTOM';

/** Un rango personalizado admite hasta 366 dias (FR-031). */
export const MAX_CUSTOM_DAYS = 366;

const MINUTES_PER_DAY = 1440;

/** El valor existe: ni nulo ni indefinido (las plantillas no admiten la comparacion floja). */
export function present<T>(value: T | null | undefined): value is T {
  return value !== null && value !== undefined;
}

/** Horas acumuladas y minutos, por ejemplo 36:42 (FR-009). */
export function durationText(minutes: number): string {
  const total = Math.max(0, Math.round(minutes));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`;
}

/** Minutos con separador de miles, por ejemplo «2.202 min». */
export function minutesLabel(minutes: number): string {
  const total = Math.max(0, Math.round(minutes));
  return `${String(total).replace(/\B(?=(\d{3})+(?!\d))/g, '.')} min`;
}

/** Instante con fecha, hora y desfase de la zona operativa; `dayOffset` suma dias a la fecha. */
export function operatingInstant(date: string, time: string, dayOffset = 0): string {
  const day = dayOffset === 0 ? date : addDays(date, dayOffset);
  return `${day}T${time.length === 5 ? time + ':00' : time}${OPERATING_OFFSET}`;
}

/** Minutos entre dos fecha y hora locales; nulo si falta alguno o no tienen formato valido. */
export function minutesBetween(
  startDate: string,
  startTime: string,
  endDate: string,
  endTime: string,
): number | null {
  const start = localMinutes(startDate, startTime);
  const end = localMinutes(endDate, endTime);
  return start === null || end === null ? null : end - start;
}

function localMinutes(date: string, time: string): number | null {
  const day = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  const clock = /^(\d{1,2}):(\d{2})$/.exec(time);
  if (!day || !clock) return null;
  return Date.UTC(+day[1], +day[2] - 1, +day[3], +clock[1], +clock[2]) / 60000;
}

/** Minutos de una ventana que caen en un dia local. */
export interface DaySlice {
  readonly day: string;
  readonly minutes: number;
}

/**
 * Reparte una ventana entre los dias locales que toca: una que cruza la medianoche aporta a cada dia solo su
 * parte (FR-013). Vacio si faltan datos o el fin no es posterior al inicio.
 */
export function splitByDay(
  startDate: string,
  startTime: string,
  endDate: string,
  endTime: string,
): DaySlice[] {
  const start = localMinutes(startDate, startTime);
  const end = localMinutes(endDate, endTime);
  if (start === null || end === null || end <= start) return [];
  const slices: DaySlice[] = [];
  for (let day = startDate; day <= endDate; day = addDays(day, 1)) {
    const dayStart = localMinutes(day, '00:00')!;
    const lower = Math.max(start, dayStart);
    const upper = Math.min(end, dayStart + MINUTES_PER_DAY);
    if (upper > lower) slices.push({ day, minutes: upper - lower });
  }
  return slices;
}

/** Suma dias a una fecha aaaa-mm-dd. */
export function addDays(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

/** Dias que hay entre dos fechas, con el fin exclusivo. */
export function daysBetween(from: string, to: string): number {
  return Math.round(
    (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / (MINUTES_PER_DAY * 60000),
  );
}

/** Hoy en la zona operativa, aaaa-mm-dd. */
export function todayInOperatingZone(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Guayaquil',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

/** Quincena en curso: del 1 al 15 es la primera; del 16 en adelante, la segunda (FR-031). */
export function currentFortnight(today: string): { preset: PeriodPreset; month: string } {
  return {
    preset: Number(today.slice(8, 10)) <= 15 ? 'FIRST_HALF' : 'SECOND_HALF',
    month: today.slice(0, 7),
  };
}

export interface PeriodChoice {
  readonly preset: PeriodPreset;
  /** aaaa-mm, para las quincenas. */
  readonly month?: string;
  /** Primer dia incluido, para el rango personalizado. */
  readonly from?: string;
  /** Ultimo dia incluido, para el rango personalizado. */
  readonly lastDay?: string;
  readonly today: string;
}

/**
 * Rango `[from, to)` de una opcion de periodo: la primera quincena es `[1, 16)`, la segunda `[16, dia 1 del
 * mes siguiente)`, los ultimos 15 dias `[hoy - 14, hoy + 1)` y un rango personalizado termina el dia
 * siguiente al ultimo elegido. Nulo si falta un dato o el rango pasa de 366 dias.
 */
export function periodRange(choice: PeriodChoice): DateRange | null {
  switch (choice.preset) {
    case 'FIRST_HALF':
      return choice.month ? { from: `${choice.month}-01`, to: `${choice.month}-16` } : null;
    case 'SECOND_HALF':
      return choice.month ? { from: `${choice.month}-16`, to: nextMonthStart(choice.month) } : null;
    case 'LAST_15_DAYS':
      return { from: addDays(choice.today, -14), to: addDays(choice.today, 1) };
    case 'CUSTOM': {
      if (!choice.from || !choice.lastDay || choice.lastDay < choice.from) return null;
      const range = { from: choice.from, to: addDays(choice.lastDay, 1) };
      return daysBetween(range.from, range.to) > MAX_CUSTOM_DAYS ? null : range;
    }
  }
}

function nextMonthStart(month: string): string {
  const [year, number] = month.split('-').map(Number);
  return new Date(Date.UTC(year, number, 1)).toISOString().slice(0, 10);
}

const PRESET_LABELS: Readonly<Record<PeriodPreset, string>> = {
  FIRST_HALF: 'Primera quincena',
  SECOND_HALF: 'Segunda quincena',
  LAST_15_DAYS: 'Últimos 15 días',
  CUSTOM: 'Rango personalizado',
};

export function presetLabel(preset: PeriodPreset): string {
  return PRESET_LABELS[preset];
}

/** Fecha y hora de un instante en la zona operativa. */
export function localParts(instant: string | Date): { date: string; time: string } {
  const text = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/Guayaquil',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(instant));
  const [date, time] = text.split(' ');
  return { date, time };
}

/** Un instante como «aaaa-mm-dd hh:mm» en la zona operativa. */
export function formatInstant(instant: string | Date): string {
  const { date, time } = localParts(instant);
  return `${date} ${time}`;
}

/** Fecha aaaa-mm-dd como dd/mm/aaaa. */
export function formatDay(date: string): string {
  const [year, month, day] = date.split('-');
  return `${day}/${month}/${year}`;
}

/** «Primera quincena · 01/10/2026–16/10/2026 (fin exclusivo)»: el rotulo del periodo consultado. */
export function periodLabel(preset: PeriodPreset, range: DateRange): string {
  return `${presetLabel(preset)} · ${formatDay(range.from)}–${formatDay(range.to)} (fin exclusivo)`;
}
