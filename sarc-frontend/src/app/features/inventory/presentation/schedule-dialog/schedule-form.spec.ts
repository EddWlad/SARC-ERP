import { sortIntervals } from '../../domain/schedule';
import { initialScheduleValue, toScheduleInput } from './schedule-form';

describe('schedule form', () => {
  it('shows backend times as HH:mm and closed exceptions as their own kind', () => {
    const value = initialScheduleValue({
      mode: 'WEEKLY',
      intervals: [{ day: 'MONDAY', opensAt: '22:00:00', closesAt: '06:00:00', overnight: true }],
      exceptions: [{ date: '2026-12-25', closed: true, note: 'Navidad' }],
    });

    expect(value.intervals).toEqual([{ day: 'MONDAY', opensAt: '22:00', closesAt: '06:00' }]);
    expect(value.exceptions[0]).toMatchObject({ kind: 'CLOSED', opensAt: '', note: 'Navidad' });
  });

  it('drops intervals unless the schedule is weekly and keeps the exceptions', () => {
    const input = toScheduleInput({
      mode: 'ALWAYS_OPEN',
      intervals: [{ day: 'MONDAY', opensAt: '08:00', closesAt: '12:00' }],
      exceptions: [
        { date: '2026-01-01', kind: 'CLOSED', opensAt: '09:00', closesAt: '10:00', note: ' ' },
        { date: '2026-05-24', kind: 'HOURS', opensAt: '09:00', closesAt: '13:00', note: 'Feriado' },
      ],
    });

    expect(input.intervals).toEqual([]);
    expect(input.exceptions).toEqual([
      {
        date: '2026-01-01',
        closed: true,
        opensAt: undefined,
        closesAt: undefined,
        note: undefined,
      },
      { date: '2026-05-24', closed: false, opensAt: '09:00', closesAt: '13:00', note: 'Feriado' },
    ]);
  });

  it('orders intervals by weekday and opening time', () => {
    const sorted = sortIntervals([
      { day: 'SUNDAY', opensAt: '08:00:00', closesAt: '10:00:00', overnight: false },
      { day: 'MONDAY', opensAt: '14:00:00', closesAt: '18:00:00', overnight: false },
      { day: 'MONDAY', opensAt: '08:00:00', closesAt: '12:00:00', overnight: false },
    ]);

    expect(sorted.map((interval) => `${interval.day} ${interval.opensAt}`)).toEqual([
      'MONDAY 08:00:00',
      'MONDAY 14:00:00',
      'SUNDAY 08:00:00',
    ]);
  });
});
