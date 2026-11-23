import { signal } from '@angular/core';

import { PeriodSelection } from './period-selection';

describe('PeriodSelection', () => {
  const today = signal(new Date(2026, 9, 23));

  beforeEach(() => today.set(new Date(2026, 9, 23)));

  it('starts on the initial preset and follows the chosen one', () => {
    const period = new PeriodSelection('thisMonth', today);
    expect(period.range()).toEqual({ from: '2026-10-01', to: '2026-10-24' });

    period.choose('today');

    expect(period.preset()).toBe('today');
    expect(period.range()).toEqual({ from: '2026-10-23', to: '2026-10-24' });
  });

  it('moves the quick presets when the day changes', () => {
    const period = new PeriodSelection('today', today);
    expect(period.range()).toEqual({ from: '2026-10-23', to: '2026-10-24' });

    today.set(new Date(2026, 9, 24));

    expect(period.range()).toEqual({ from: '2026-10-24', to: '2026-10-25' });
  });

  it('keeps the initial range while a custom period has no dates yet', () => {
    const period = new PeriodSelection('thisMonth', today);

    period.choose('custom');

    expect(period.range()).toEqual({ from: '2026-10-01', to: '2026-10-24' });
  });

  it('uses the picked range as a custom period', () => {
    const period = new PeriodSelection('today', today);

    period.chooseRange({ from: '2026-03-01', to: '2026-04-01' });

    expect(period.preset()).toBe('custom');
    expect(period.range()).toEqual({ from: '2026-03-01', to: '2026-04-01' });
  });

  it('D13: the initial month starts on the first operating day and rolls with the month', () => {
    today.set(new Date(2026, 10, 1));
    const period = new PeriodSelection('thisMonth', today);
    expect(period.range()).toEqual({ from: '2026-11-01', to: '2026-11-02' });

    today.set(new Date(2026, 10, 30));
    expect(period.range()).toEqual({ from: '2026-11-01', to: '2026-12-01' });

    today.set(new Date(2026, 11, 1));
    expect(period.range()).toEqual({ from: '2026-12-01', to: '2026-12-02' });
  });

  it('«Todo el historial» puede ser el periodo inicial y no tiene rango propio', () => {
    const period = new PeriodSelection('allHistory', today);

    expect(period.preset()).toBe('allHistory');
    expect(period.allHistory()).toBe(true);
    // El rango queda en el del acceso de reserva, por si la pantalla lo necesita al elegir un periodo.
    expect(period.range()).toEqual({ from: '2026-10-01', to: '2026-10-24' });

    period.choose('today');

    expect(period.allHistory()).toBe(false);
    expect(period.range()).toEqual({ from: '2026-10-23', to: '2026-10-24' });
  });

  it('un rango personalizado sustituye a «Todo el historial»', () => {
    const period = new PeriodSelection('allHistory', today);

    period.chooseRange({ from: '2026-03-01', to: '2026-04-01' });

    expect(period.allHistory()).toBe(false);
    expect(period.range()).toEqual({ from: '2026-03-01', to: '2026-04-01' });
  });
});
