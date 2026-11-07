import { dayEndInstant, dayStartInstant } from './uptime-time';

describe('uptime-time', () => {
  it('toma el dia completo en la zona operativa', () => {
    expect(dayStartInstant('2026-10-26')).toBe('2026-10-26T00:00:00-05:00');
    expect(dayEndInstant('2026-11-01')).toBe('2026-11-02T00:00:00-05:00');
  });

  it('el fin inclusivo cruza de mes y de anio', () => {
    expect(dayEndInstant('2026-10-31')).toBe('2026-11-01T00:00:00-05:00');
    expect(dayEndInstant('2026-12-31')).toBe('2027-01-01T00:00:00-05:00');
  });
});
