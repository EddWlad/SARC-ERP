import { summary } from '../testing/summary-fixtures';
import {
  hoursFromChart,
  hoursText,
  rankPositions,
  summaryPeriodLabel,
  toHours,
  TopPoint,
} from './intermittency-summary';

const point = (minutes: number): TopPoint => ({
  equipmentId: `p${minutes}`,
  name: `Punto ${minutes}`,
  minutes,
  windows: 1,
  days: 1,
});

describe('intermittency-summary', () => {
  it('escribe el tiempo en horas y minutos con su unidad', () => {
    expect(hoursText(2202)).toBe('36:42 h');
    expect(hoursText(30)).toBe('0:30 h');
    expect(hoursText(0)).toBe('0:00 h');
  });

  it('las graficas usan horas decimales y el tooltip las vuelve horas y minutos', () => {
    expect(toHours(90)).toBe(1.5);
    expect(hoursFromChart(1.5)).toBe('1:30 h');
    expect(hoursFromChart(toHours(2202))).toBe('36:42 h');
  });

  it('rotula el periodo con su rango real: el ultimo dia se incluye aunque la API lo entregue exclusivo', () => {
    expect(summaryPeriodLabel(summary().period)).toBe(
      'Primera quincena · 01/10/2026 al 15/10/2026',
    );
    expect(
      summaryPeriodLabel({
        ...summary().period,
        preset: 'CUSTOM',
        from: '2026-10-05',
        to: '2026-10-06',
      }),
    ).toBe('Rango personalizado · 05/10/2026');
  });

  it('los empatados en tiempo comparten posicion', () => {
    expect(
      rankPositions([point(300), point(200), point(100), point(100), point(100), point(50)]),
    ).toEqual([1, 2, 3, 3, 3, 6]);
    expect(rankPositions([])).toEqual([]);
  });
});
