import { inclusiveRange, lastDay, parseIsoDate, presetRange, rangeDays } from './period';

describe('consumption periods', () => {
  // Mediodia local: cualquier zona horaria lo deja en el mismo dia.
  const today = new Date(2026, 9, 23, 12, 30);

  it('computes each quick preset as [from, to) local days', () => {
    expect(presetRange('today', today)).toEqual({ from: '2026-10-23', to: '2026-10-24' });
    expect(presetRange('last7', today)).toEqual({ from: '2026-10-17', to: '2026-10-24' });
    expect(presetRange('thisMonth', today)).toEqual({ from: '2026-10-01', to: '2026-10-24' });
    expect(presetRange('lastMonth', today)).toEqual({ from: '2026-09-01', to: '2026-10-01' });
    expect(presetRange('thisYear', today)).toEqual({ from: '2026-01-01', to: '2026-10-24' });
  });

  it('crosses month and year boundaries', () => {
    const newYear = new Date(2027, 0, 3, 8);

    expect(presetRange('last7', newYear)).toEqual({ from: '2026-12-28', to: '2027-01-04' });
    expect(presetRange('lastMonth', newYear)).toEqual({ from: '2026-12-01', to: '2027-01-01' });
  });

  it('turns a picked first and last day into an exclusive end', () => {
    const range = inclusiveRange(new Date(2026, 1, 27, 18), new Date(2026, 2, 1, 6));

    expect(range).toEqual({ from: '2026-02-27', to: '2026-03-02' });
    expect(rangeDays(range)).toBe(3);
    expect(lastDay(range)).toEqual(new Date(2026, 2, 1));
  });

  it('parses ISO days as local dates, not UTC', () => {
    const day = parseIsoDate('2026-10-01');

    expect([day.getFullYear(), day.getMonth(), day.getDate(), day.getHours()]).toEqual([
      2026, 9, 1, 0,
    ]);
  });
});
