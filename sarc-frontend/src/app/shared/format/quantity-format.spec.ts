import { TestBed } from '@angular/core/testing';

import { QuantityFormat } from './quantity-format';

describe('QuantityFormat', () => {
  let format: QuantityFormat;

  beforeEach(() => {
    format = TestBed.inject(QuantityFormat);
  });

  it('distingue la energía ausente de una lectura de cero', () => {
    expect(format.reportedKwh(null)).toBe('No informado');
    expect(format.reportedEnergy(undefined)).toBe('No informado');
    expect(format.reportedKwh(0)).toBe(format.kwh(0));
    expect(format.reportedEnergy(0)).toBe(`${format.kwh(0)} kWh`);
  });

  it('informa "Sin datos" en totales sin lecturas, nunca cero', () => {
    expect(format.knownKwh(null)).toBe('Sin datos');
    expect(format.knownEnergy(null)).toBe('Sin datos');
    expect(format.knownEnergy(3.75)).toBe(`${format.kwh(3.75)} kWh`);
    expect(format.knownEnergy(0)).toBe(`${format.kwh(0)} kWh`);
  });
});
