import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PeriodSelection } from '../period-selection';
import { PeriodFilter } from './period-filter';

describe('PeriodFilter', () => {
  const today = signal(new Date(2026, 9, 23));

  function render(
    allowAllHistory: boolean,
    initial: 'thisMonth' | 'allHistory' = 'thisMonth',
  ): { fixture: ComponentFixture<PeriodFilter>; period: PeriodSelection } {
    TestBed.resetTestingModule();
    const period = new PeriodSelection(initial, today);
    const fixture = TestBed.createComponent(PeriodFilter);
    fixture.componentRef.setInput('period', period);
    fixture.componentRef.setInput('allowAllHistory', allowAllHistory);
    fixture.detectChanges();
    return { fixture, period };
  }

  const chips = (fixture: ComponentFixture<PeriodFilter>) =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('mat-chip-option')).map(
      (chip) => chip.textContent?.trim(),
    );

  it('por defecto no ofrece «Todo el historial»: Consumo no cambia', () => {
    const { fixture } = render(false);

    expect(chips(fixture)).toEqual([
      'Hoy',
      'Últimos 7 días',
      'Este mes',
      'Mes anterior',
      'Este año',
      'Personalizado',
    ]);
  });

  it('si la pantalla lo pide, «Todo el historial» va al inicio', () => {
    const { fixture } = render(true, 'allHistory');

    expect(chips(fixture)[0]).toBe('Todo el historial');
    expect(chips(fixture)).toHaveLength(7);
  });

  it('en «Todo el historial» el texto del periodo no inventa fechas', () => {
    const { fixture } = render(true, 'allHistory');

    const current = (fixture.nativeElement as HTMLElement).querySelector('.current');
    expect(current?.textContent?.trim()).toBe('Todo el historial');
  });

  it('elegir un acceso con rango vuelve a mostrar las fechas', () => {
    const { fixture, period } = render(true, 'allHistory');

    period.choose('thisMonth');
    fixture.detectChanges();

    const current = (fixture.nativeElement as HTMLElement).querySelector('.current');
    expect(current?.textContent).toContain('2026');
    expect(period.allHistory()).toBe(false);
  });
});
