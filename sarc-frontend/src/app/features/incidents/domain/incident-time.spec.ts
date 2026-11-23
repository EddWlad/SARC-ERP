import {
  ageText,
  durationText,
  elapsedMinutes,
  formatInstant,
  localParts,
  NO_VALUE,
} from './incident-time';

describe('incident-time', () => {
  describe('durationText', () => {
    it('muestra horas acumuladas y minutos', () => {
      expect(durationText(109)).toBe('1:49');
      expect(durationText(2202)).toBe('36:42');
      expect(durationText(5)).toBe('0:05');
      expect(durationText(60)).toBe('1:00');
    });

    it('un cero real es 0:00, pero un valor ausente nunca se muestra como cero', () => {
      expect(durationText(0)).toBe('0:00');
      expect(durationText(null)).toBe('—');
      expect(durationText(undefined)).toBe('—');
      expect(durationText(Number.NaN)).toBe(NO_VALUE);
    });

    it('descarta los segundos y no admite negativos', () => {
      expect(durationText(5.9)).toBe('0:05');
      expect(durationText(-3)).toBe('0:00');
    });
  });

  describe('zona operativa', () => {
    it('muestra un instante en America/Guayaquil (UTC-5)', () => {
      expect(formatInstant('2026-11-20T20:30:00Z')).toBe('2026-11-20 15:30');
      expect(formatInstant('2026-11-21T04:59:00Z')).toBe('2026-11-20 23:59');
      expect(formatInstant('2026-11-21T05:00:00Z')).toBe('2026-11-21 00:00');
      expect(localParts('2026-11-20T20:30:00Z')).toEqual({ date: '2026-11-20', time: '15:30' });
    });

    it('un instante ausente es «—»', () => {
      expect(formatInstant(null)).toBe('—');
      expect(formatInstant(undefined)).toBe('—');
      expect(formatInstant('')).toBe('—');
    });
  });

  describe('minutos transcurridos y antiguedad', () => {
    it('redondea hacia abajo los minutos entre dos instantes', () => {
      expect(elapsedMinutes('2026-11-20T15:00:00Z', '2026-11-20T15:05:59Z')).toBe(5);
      expect(elapsedMinutes('2026-11-20T15:00:00Z', '2026-11-20T16:49:00Z')).toBe(109);
      expect(elapsedMinutes(null, '2026-11-20T16:49:00Z')).toBeNull();
      expect(elapsedMinutes('2026-11-20T15:00:00Z', undefined)).toBeNull();
    });

    it('la antiguedad muestra dias solo cuando hay al menos uno', () => {
      const now = new Date('2026-11-23T18:15:00Z');
      expect(ageText('2026-11-23T15:00:00Z', now)).toBe('3:15');
      expect(ageText('2026-11-21T15:00:00Z', now)).toBe('2 d 3:15');
      expect(ageText('2026-11-22T18:15:00Z', now)).toBe('1 d 0:00');
    });

    it('sin T0 o con T0 futuro no inventa una antiguedad', () => {
      const now = new Date('2026-11-23T18:15:00Z');
      expect(ageText(null, now)).toBe('—');
      expect(ageText('2026-11-23T19:00:00Z', now)).toBe('—');
    });
  });
});
