import { availabilityText, coverageText, EXCLUSION_LABELS } from './intermittency-availability';

describe('intermittency-availability', () => {
  it('«Sin datos» nunca se presenta como 100 %', () => {
    expect(availabilityText(null)).toBe('Sin datos');
    expect(availabilityText(undefined)).toBe('Sin datos');
  });

  it('la disponibilidad lleva dos decimales', () => {
    expect(availabilityText(95)).toMatch(/^95[.,]00 %$/);
    expect(availabilityText(99.5)).toMatch(/^99[.,]50 %$/);
    expect(availabilityText(100)).toMatch(/^100[.,]00 %$/);
  });

  it('la cobertura son dias cubiertos sobre dias en alcance', () => {
    expect(coverageText(1, 1)).toBe('1 de 1 día');
    expect(coverageText(3, 14)).toBe('3 de 14 días');
    expect(coverageText(0, 0)).toBe('0 de 0 días');
  });

  it('cada motivo de exclusion tiene su rotulo', () => {
    expect(Object.keys(EXCLUSION_LABELS).sort()).toEqual(['DELETED', 'DISCONNECTED', 'INACTIVE']);
  });
});
