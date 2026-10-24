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
});
