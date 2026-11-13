import { NO_ERRORS_SCHEMA } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { KiaPointResponse } from '../../../../core/api/generated';
import { PeriodPreset } from '../../domain/intermittency-time';
import { PointsFilter } from '../points-filter/points-filter';
import { IntermittencyFilterBar } from './intermittency-filter-bar';

const POINT: KiaPointResponse = {
  id: 'p1',
  name: 'Punto A',
  assignedCode: '900001',
  adminStatus: 'ACTIVE',
};

describe('IntermittencyFilterBar', () => {
  function render(inputs: Record<string, unknown> = {}) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [IntermittencyFilterBar] });
    TestBed.overrideComponent(IntermittencyFilterBar, {
      remove: { imports: [PointsFilter] },
      add: { schemas: [NO_ERRORS_SCHEMA] },
    });
    const fixture = TestBed.createComponent(IntermittencyFilterBar);
    const values: Record<string, unknown> = {
      preset: 'FIRST_HALF',
      month: '2026-10',
      points: [],
      ...inputs,
    };
    for (const [name, value] of Object.entries(values)) fixture.componentRef.setInput(name, value);
    fixture.detectChanges();
    const bar = fixture.componentInstance;
    return { fixture, bar, root: fixture.nativeElement as HTMLElement };
  }

  const labels = (root: HTMLElement) =>
    Array.from(root.querySelectorAll('mat-chip-option'), (chip) => chip.textContent?.trim());

  it('ofrece las cuatro opciones de periodo del PRD', () => {
    expect(labels(render().root)).toEqual([
      'Primera quincena',
      'Segunda quincena',
      'Últimos 15 días',
      'Rango personalizado',
    ]);
  });

  it('las quincenas piden el mes y no el rango', () => {
    const { root } = render();

    expect(root.querySelector('input[type="month"]')).not.toBeNull();
    expect(root.querySelector('input[type="date"]')).toBeNull();
  });

  it('los ultimos 15 dias no piden nada mas', () => {
    const { root } = render({ preset: 'LAST_15_DAYS' });

    expect(root.querySelector('input[type="month"]')).toBeNull();
    expect(root.querySelector('input[type="date"]')).toBeNull();
  });

  it('el rango personalizado pide el primer y el ultimo dia, y el aviso de su limite', () => {
    const { root } = render({ preset: 'CUSTOM', firstDay: '2026-10-01', lastDay: '2026-10-15' });

    const dates = Array.from(root.querySelectorAll<HTMLInputElement>('input[type="date"]'));
    expect(dates.map((input) => input.value)).toEqual(['2026-10-01', '2026-10-15']);
    expect(root.textContent).toContain('Hasta 366 días');
  });

  it('cambiar un dia avisa con el otro sin tocarlo', () => {
    const { bar, root } = render({
      preset: 'CUSTOM',
      firstDay: '2026-10-01',
      lastDay: '2026-10-15',
    });
    const emitted: { firstDay: string; lastDay: string }[] = [];
    bar.rangeChange.subscribe((range) => emitted.push(range));
    const [first, last] = Array.from(root.querySelectorAll<HTMLInputElement>('input[type="date"]'));

    first.value = '2026-10-03';
    first.dispatchEvent(new Event('change'));
    last.value = '2026-10-20';
    last.dispatchEvent(new Event('change'));

    expect(emitted).toEqual([
      { firstDay: '2026-10-03', lastDay: '2026-10-15' },
      { firstDay: '2026-10-01', lastDay: '2026-10-20' },
    ]);
  });

  it('un mes vaciado vuelve al mes vigente y no avisa', () => {
    const { bar, root } = render();
    const months: string[] = [];
    bar.monthChange.subscribe((month) => months.push(month));
    const input = root.querySelector('input[type="month"]') as HTMLInputElement;

    input.value = '';
    input.dispatchEvent(new Event('change'));

    expect(months).toEqual([]);
    expect(input.value).toBe('2026-10');
  });

  it('los limites de dia y mes siguen al dia de hoy que entrega el tablero', () => {
    const { fixture, root } = render({ preset: 'CUSTOM', today: '2026-10-20' });

    const dates = Array.from(root.querySelectorAll<HTMLInputElement>('input[type="date"]'));
    expect(dates.map((input) => input.max)).toEqual(['2026-10-20', '2026-10-20']);

    fixture.componentRef.setInput('today', '2026-10-21');
    fixture.componentRef.setInput('preset', 'FIRST_HALF');
    fixture.detectChanges();
    expect((root.querySelector('input[type="month"]') as HTMLInputElement).max).toBe('2026-10');

    fixture.componentRef.setInput('today', '2026-11-01');
    fixture.detectChanges();
    expect((root.querySelector('input[type="month"]') as HTMLInputElement).max).toBe('2026-11');
  });

  it('avisa el mes elegido', () => {
    const { bar, root } = render();
    const months: string[] = [];
    bar.monthChange.subscribe((month) => months.push(month));
    const input = root.querySelector('input[type="month"]') as HTMLInputElement;

    input.value = '2026-09';
    input.dispatchEvent(new Event('change'));

    expect(months).toEqual(['2026-09']);
  });

  it('un chip ya elegido no se deselecciona', () => {
    const { bar } = render();
    const presets: PeriodPreset[] = [];
    bar.presetChange.subscribe((preset) => presets.push(preset));

    (bar as unknown as { onPreset(value: PeriodPreset | undefined): void }).onPreset(undefined);
    (bar as unknown as { onPreset(value: PeriodPreset | undefined): void }).onPreset(
      'LAST_15_DAYS',
    );

    expect(presets).toEqual(['LAST_15_DAYS']);
  });

  it('limpiar solo se ofrece con puntos elegidos', () => {
    expect(render().root.textContent).not.toContain('Limpiar filtros');

    const { bar, root } = render({ points: [POINT] });
    let cleared = 0;
    bar.cleared.subscribe(() => cleared++);
    const button = Array.from(root.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes('Limpiar filtros'),
    ) as HTMLButtonElement;
    button.click();

    expect(cleared).toBe(1);
  });

  it('muestra por que el rango no se consulta', () => {
    const { root } = render({ preset: 'CUSTOM', rangeError: 'Elija el primer y el último día.' });

    expect(root.querySelector('[role="alert"]')?.textContent).toContain(
      'Elija el primer y el último día.',
    );
  });
});
