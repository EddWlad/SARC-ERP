import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { ChartView } from '../chart/chart-view';
import { RankedRow, TopEquipment } from './top-equipment';

@Component({ selector: 'sarc-chart-view', template: '' })
class ChartStub {
  readonly kind = input('');
  readonly horizontal = input(false);
  readonly labels = input<string[]>([]);
  readonly series = input();
  readonly format = input();
  readonly description = input('');
}

describe('TopEquipment', () => {
  function render(rows: readonly RankedRow[], inputs: Record<string, unknown> = {}): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [TopEquipment] });
    TestBed.overrideComponent(TopEquipment, {
      remove: { imports: [ChartView] },
      add: { imports: [ChartStub] },
    });
    const fixture = TestBed.createComponent(TopEquipment);
    fixture.componentRef.setInput('items', rows);
    fixture.componentRef.setInput('seriesLabel', 'Energía (kWh)');
    fixture.componentRef.setInput('chartFormat', (value: number) => String(value));
    fixture.componentRef.setInput('description', 'Los equipos con más energía');
    fixture.componentRef.setInput('tiesNote', 'El Top 5 incluye más de cinco por empate.');
    for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const row = (position: number, index: number): RankedRow => ({
    position,
    name: `Equipo ${index}`,
    code: `s${index}`,
    value: 0,
    valueText: '0 kWh',
    detail: '1 sesiones',
  });

  it('preserves shared positions and all rows; note appears only above the top size', () => {
    for (const positions of [
      [1, 2, 3, 4, 4, 4],
      [1, 2, 3, 4, 5, 5, 5],
      [1, 1, 1, 1, 1, 1],
      [1, 2, 3, 4, 5],
      [1, 2, 3],
    ]) {
      const host = render(positions.map(row));

      expect(Array.from(host.querySelectorAll('.position'), (e) => e.textContent?.trim())).toEqual(
        positions.map(String),
      );
      expect(host.textContent?.includes('por empate')).toBe(positions.length > 5);
    }
  });

  it('uses the given top size for the ties note', () => {
    const rows = Array.from({ length: 11 }, (_, index) => row(index < 10 ? index + 1 : 10, index));

    expect(render(rows, { topSize: 10 }).textContent).toContain('por empate');
    expect(render(rows.slice(0, 10), { topSize: 10 }).textContent).not.toContain('por empate');
  });

  it('shows the value with its unit, the complementary figure and the code', () => {
    const host = render([{ ...row(1, 0), valueText: '36:42 h', detail: '4 ventanas · 3 días' }]);

    expect(host.textContent).toContain('36:42 h');
    expect(host.textContent).toContain('4 ventanas · 3 días');
    expect(host.textContent).toContain('s0');
  });

  it('shows the notes at the bottom, and nothing when there are none', () => {
    const rows = [row(1, 0)];

    expect(render(rows, { notes: ['1 equipo fuera del ranking'] }).textContent).toContain(
      '1 equipo fuera del ranking',
    );
    expect(render(rows).querySelector('.excluded')).toBeNull();
  });
});
