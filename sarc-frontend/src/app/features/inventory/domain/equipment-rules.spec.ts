import { isPoint, KIA_POINT_CODE, maxConnectors, usesSerialAsCode } from './equipment-rules';

describe('equipment rules', () => {
  it('lets the user choose the code only on KIA charging points', () => {
    expect(usesSerialAsCode('KIA', 'CHARGING_POINT')).toBe(false);
    expect(usesSerialAsCode('JAC', 'CHARGING_POINT')).toBe(true);
    expect(usesSerialAsCode('CONECTZ', 'CHARGING_POINT')).toBe(true);
    expect(usesSerialAsCode('KIA', 'CHARGING_STATION')).toBe(true);
  });

  it('accepts KIA codes of 6 digits that do not start with 0 (CA-002-01)', () => {
    expect(KIA_POINT_CODE.test('123456')).toBe(true);
    expect(KIA_POINT_CODE.test('012345')).toBe(false);
    expect(KIA_POINT_CODE.test('12345')).toBe(false);
    expect(KIA_POINT_CODE.test('1234567')).toBe(false);
    expect(KIA_POINT_CODE.test('12345a')).toBe(false);
  });

  it('allows one connector on points and up to three on stations', () => {
    expect(isPoint('CHARGING_POINT')).toBe(true);
    expect(maxConnectors('CHARGING_POINT')).toBe(1);
    expect(maxConnectors('CHARGING_STATION')).toBe(3);
  });
});
