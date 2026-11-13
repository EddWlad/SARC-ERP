import { IntermittencySummary } from '../domain/intermittency-summary';

/** Cifras de prueba del tablero, con codigos ficticios. Los minutos de las porciones suman el total oficial. */
export function summary(overrides: Partial<IntermittencySummary> = {}): IntermittencySummary {
  return {
    period: {
      preset: 'FIRST_HALF',
      from: '2026-10-01',
      to: '2026-10-16',
      cutOff: '2026-10-20T15:00:00Z',
      timeZone: 'America/Guayaquil',
    },
    official: {
      minutes: 2282,
      affectedPoints: 2,
      windows: 5,
      pointDays: 4,
      recurrentPoints: 1,
      averageMinutes: 456.4,
    },
    outsideOfficial: {
      open: { count: 1, provisionalMinutes: 135 },
      review: 2,
      test: 3,
      pendingLink: 1,
      conflict: 1,
    },
    top: [
      { equipmentId: 'p1', code: '900001', name: 'Punto A', minutes: 2202, windows: 4, days: 3 },
      { equipmentId: 'p2', code: '900002', name: 'Punto B', minutes: 80, windows: 1, days: 1 },
    ],
    topExtendedByTies: false,
    daily: [
      { day: '2026-10-05', minutes: 1200, windows: 2 },
      { day: '2026-10-06', minutes: 0, windows: 0 },
      { day: '2026-10-07', minutes: 1082, windows: 3 },
    ],
    dailyWindowsMayExceedTotal: false,
    byDurationBand: [
      { band: 'UNDER_5', minutes: 0, windows: 0 },
      { band: 'FROM_5_TO_14', minutes: 0, windows: 0 },
      { band: 'FROM_15_TO_59', minutes: 80, windows: 1 },
      { band: 'FROM_60_TO_239', minutes: 0, windows: 0 },
      { band: 'FROM_240', minutes: 2202, windows: 4 },
    ],
    concentration: { topMinutes: 2282, restMinutes: 0 },
    recurrence: { recurrentMinutes: 2202, nonRecurrentMinutes: 80 },
    generatedAt: '2026-10-20T15:00:00Z',
    ...overrides,
  };
}
