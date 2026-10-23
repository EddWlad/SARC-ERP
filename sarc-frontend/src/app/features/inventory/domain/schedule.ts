import {
  ExceptionResponse,
  IntervalResponse,
  ScheduleRequest,
  ScheduleResponse,
} from '../../../core/api/generated';

export type Schedule = ScheduleResponse;
export type ScheduleInput = ScheduleRequest;
export type WeeklyInterval = IntervalResponse;
export type ScheduleException = ExceptionResponse;
export type ScheduleMode = Schedule['mode'];
export type DayOfWeek = WeeklyInterval['day'];

export const SCHEDULE_MODE_LABELS: Readonly<Record<ScheduleMode, string>> = {
  NOT_DEFINED: 'Sin definir',
  ALWAYS_OPEN: 'Abierto 24/7',
  WEEKLY: 'Horario semanal',
};

export const DAY_LABELS: Readonly<Record<DayOfWeek, string>> = {
  MONDAY: 'Lunes',
  TUESDAY: 'Martes',
  WEDNESDAY: 'Miércoles',
  THURSDAY: 'Jueves',
  FRIDAY: 'Viernes',
  SATURDAY: 'Sábado',
  SUNDAY: 'Domingo',
};

export const SCHEDULE_MODES = Object.keys(SCHEDULE_MODE_LABELS) as readonly ScheduleMode[];
export const DAYS = Object.keys(DAY_LABELS) as readonly DayOfWeek[];

/** El backend envia horas ISO (`HH:mm:ss`); en pantalla se muestran como `HH:mm`. */
export function shortTime(time: string | null | undefined): string {
  return time?.slice(0, 5) ?? '';
}

/** Intervalos ordenados por dia de la semana y hora de apertura. */
export function sortIntervals(intervals: readonly WeeklyInterval[]): WeeklyInterval[] {
  return [...intervals].sort(
    (a, b) => DAYS.indexOf(a.day) - DAYS.indexOf(b.day) || a.opensAt.localeCompare(b.opensAt),
  );
}
