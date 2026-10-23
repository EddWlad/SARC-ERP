import { DayOfWeek, Schedule, ScheduleInput, ScheduleMode, shortTime } from '../../domain/schedule';

export interface IntervalRow {
  day: DayOfWeek;
  opensAt: string;
  closesAt: string;
}

export interface ExceptionRow {
  date: string;
  /** CLOSED: no atiende ese dia; HOURS: atiende en un horario especial. */
  kind: 'CLOSED' | 'HOURS';
  opensAt: string;
  closesAt: string;
  note: string;
}

export interface ScheduleFormValue {
  mode: ScheduleMode;
  intervals: IntervalRow[];
  exceptions: ExceptionRow[];
}

export function newInterval(): IntervalRow {
  return { day: 'MONDAY', opensAt: '08:00', closesAt: '18:00' };
}

export function newException(): ExceptionRow {
  return { date: '', kind: 'CLOSED', opensAt: '', closesAt: '', note: '' };
}

export function initialScheduleValue(schedule: Schedule): ScheduleFormValue {
  return {
    mode: schedule.mode,
    intervals: schedule.intervals.map((interval) => ({
      day: interval.day,
      opensAt: shortTime(interval.opensAt),
      closesAt: shortTime(interval.closesAt),
    })),
    exceptions: schedule.exceptions.map((exception) => ({
      date: exception.date,
      kind: exception.closed ? 'CLOSED' : 'HOURS',
      opensAt: shortTime(exception.opensAt),
      closesAt: shortTime(exception.closesAt),
      note: exception.note ?? '',
    })),
  };
}

/** Peticion sin version: solo un horario semanal lleva intervalos; las excepciones aplican siempre. */
export function toScheduleInput(value: ScheduleFormValue): Omit<ScheduleInput, 'version'> {
  return {
    mode: value.mode,
    intervals: value.mode === 'WEEKLY' ? value.intervals.map((interval) => ({ ...interval })) : [],
    exceptions: value.exceptions.map((exception) => {
      const closed = exception.kind === 'CLOSED';
      const note = exception.note.trim();
      return {
        date: exception.date,
        closed,
        opensAt: closed ? undefined : exception.opensAt,
        closesAt: closed ? undefined : exception.closesAt,
        note: note === '' ? undefined : note,
      };
    }),
  };
}
