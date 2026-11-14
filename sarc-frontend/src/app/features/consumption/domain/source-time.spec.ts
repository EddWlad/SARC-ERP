import { formatDate } from '@angular/common';

import { sourceUtcLabel, sourceUtcOffset } from './source-time';

describe('source time', () => {
  it('shows Enerlink 1212 at the time written in its UTC+00:00 file', () => {
    const offset = sourceUtcOffset(0);

    expect(offset).toBe('+0000');
    expect(formatDate('2026-11-11T01:36:15Z', 'dd/MM/yyyy HH:mm:ss', 'en-US', offset)).toBe(
      '11/11/2026 01:36:15',
    );
    expect(sourceUtcLabel(0)).toBe('UTC+00:00');
  });

  it('keeps Wallbox and sessions without evidence in Ecuador time without an extra label', () => {
    expect(sourceUtcOffset(-300)).toBe('-0500');
    expect(sourceUtcOffset(null)).toBe('-0500');
    expect(sourceUtcOffset(undefined)).toBe('-0500');
    expect(sourceUtcLabel(-300)).toBeNull();
    expect(
      formatDate('2026-11-11T04:59:11Z', 'dd/MM/yyyy HH:mm', 'en-US', sourceUtcOffset(-300)),
    ).toBe('10/11/2026 23:59');
  });

  it('formats offsets with minutes', () => {
    expect(sourceUtcOffset(330)).toBe('+0530');
    expect(sourceUtcLabel(-270)).toBe('UTC-04:30');
  });
});
