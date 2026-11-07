import { TestBed } from '@angular/core/testing';

import { StatesChart } from './states-chart';

describe('StatesChart', () => {
  it('conserva orden, cero y cobertura parcial sin inventar Ocupado', async () => {
    const fixture = TestBed.createComponent(StatesChart);

    fixture.componentRef.setInput('composition', {
      availableMinutes: 10,
      occupiedMinutes: null,
      faultMinutes: 0,
      offlineMinutes: 2,

      totalKnownMinutes: 12,
      selectedMeasurements: 2,
      completeMeasurements: 0,

      knownMeasurementsByState: { available: 2, occupied: 0, fault: 2, offline: 1 },
      isComplete: false,
    });

    await fixture.whenStable();

    const text = fixture.nativeElement.textContent;

    expect(text).toContain('Distribución de tiempos informados');

    expect(text).toContain('No informado');

    expect(text).toContain('1/2');

    expect(text.indexOf('Disponible')).toBeLessThan(text.indexOf('Ocupado'));

    expect(fixture.componentInstance.series().values).toEqual([10, null, 0, 2]);

    expect(fixture.componentInstance.series().colors).toEqual([
      'series-1',
      'series-2',
      'series-3',
      'series-4',
    ]);
  });

  it('no dibuja un círculo de duración cero', async () => {
    const fixture = TestBed.createComponent(StatesChart);

    fixture.componentRef.setInput('composition', {
      availableMinutes: 0,
      occupiedMinutes: 0,
      faultMinutes: 0,
      offlineMinutes: 0,

      totalKnownMinutes: 0,
      selectedMeasurements: 1,
      completeMeasurements: 1,

      knownMeasurementsByState: { available: 1, occupied: 1, fault: 1, offline: 1 },
      isComplete: true,
    });

    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('canvas')).toBeNull();

    expect(fixture.nativeElement.textContent).toContain('Sin tiempos positivos informados');

    expect(fixture.nativeElement.textContent).not.toContain('No informado');
  });
});
