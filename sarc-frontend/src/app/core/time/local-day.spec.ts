import { operatingParts } from './local-day';

describe('operatingParts', () => {
  it('reads the calendar day and time in Guayaquil, not in the browser zone', () => {
    // 03:30 UTC del 21/oct son las 22:30 del 20/oct en Ecuador: la sesion cuenta el dia 20.
    expect(operatingParts(new Date('2026-10-21T03:30:00Z'))).toEqual({
      year: 2026,
      month: 10,
      day: 20,
      hour: 22,
      minute: 30,
      second: 0,
    });
  });
});
