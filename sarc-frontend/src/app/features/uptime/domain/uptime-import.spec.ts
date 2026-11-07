import { reportWeeks, uptimeTemporalLabel, uptimeCanConfirmExact } from './uptime-import';

describe('reportWeeks', () => {
  it('ofrece las semanas ya terminadas, de lunes a domingo, la más reciente primero', () => {
    const weeks = reportWeeks(new Date(2026, 10, 2), 3);

    expect(weeks.map((week) => week.value)).toEqual(['2026-10-26', '2026-10-19', '2026-10-12']);
    expect(weeks[1].label).toBe('19/10/2026 – 25/10/2026');
  });

  it('un domingo aún no cierra su semana', () => {
    expect(reportWeeks(new Date(2026, 10, 1), 1)[0].value).toBe('2026-10-19');
  });
});

describe('precision del informe de uptime', () => {
  it('rotula referencia sin fabricar horario ni habilitar exactitud', () => {
    const batch = { temporalPrecision: 'REFERENCE_WEEK' as const, referenceWeek: '2026-10-19' };
    expect(uptimeTemporalLabel(batch)).toContain('Horario real no informado');
    expect(uptimeTemporalLabel(batch)).toContain('2026-10-19');
    expect(uptimeCanConfirmExact(batch)).toBe(false);
    expect(uptimeCanConfirmExact({ temporalPrecision: 'KNOWN_RANGE' })).toBe(true);
    expect(uptimeCanConfirmExact({ temporalPrecision: 'REQUIRES_RECONCILIATION' })).toBe(false);
  });
});
