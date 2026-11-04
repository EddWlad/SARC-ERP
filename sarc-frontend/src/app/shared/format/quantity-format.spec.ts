import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { QuantityFormat } from './quantity-format';

describe('QuantityFormat', () => {
  let format: QuantityFormat;

  beforeEach(() => {
    format = TestBed.inject(QuantityFormat);
  });

  it('muestra porcentajes y medidas con coma decimal y sin convertir ausencia en cero', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [{ provide: LOCALE_ID, useValue: 'es-EC' }],
    });
    const format = TestBed.inject(QuantityFormat);

    expect(format.decimal('97.29')).toBe('97,29');
    expect(format.percentage('97.29')).toBe('97,29 %');
    expect(format.percentage('0.00')).toBe('0,00 %');
    expect(format.percentage(undefined)).toBe('—');
    expect(format.measure(10080)).toBe('10.080');
    expect(format.measure(9.57)).toBe('9,57');
    expect(format.measure(undefined)).toBe('—');
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
