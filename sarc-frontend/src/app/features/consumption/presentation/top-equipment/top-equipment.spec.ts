import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ChartView } from '../../../../shared/ui/chart/chart-view';
import { RankedEquipment } from '../../domain/consumption';
import { TopEquipment } from './top-equipment';

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
  it('preserves shared positions and all rows; note appears only above five', async () => {
    TestBed.configureTestingModule({ imports: [TopEquipment] });
    TestBed.overrideComponent(TopEquipment, {
      remove: { imports: [ChartView] },
      add: { imports: [ChartStub] },
    });
    const fixture = TestBed.createComponent(TopEquipment);
    for (const positions of [
      [1, 2, 3, 4, 4, 4],
      [1, 2, 3, 4, 5, 5, 5],
      [1, 1, 1, 1, 1, 1],
      [1, 2, 3, 4, 5],
      [1, 2, 3],
    ]) {
      fixture.componentRef.setInput(
        'items',
        positions.map(
          (position, i) =>
            ({
              position,
              sessions: 1,
              energyKwh: 0,
              equipment: {
                id: `s${i}`,
                name: `Equipo ${i}`,
                assignedCode: `s${i}`,
                network: 'JAC',
                type: 'CHARGING_STATION',
              },
            }) satisfies RankedEquipment,
        ),
      );
      fixture.detectChanges();
      expect(
        Array.from(fixture.nativeElement.querySelectorAll('.position'), (e: unknown) =>
          (e as HTMLElement).textContent?.trim(),
        ),
      ).toEqual(positions.map(String));
      expect(fixture.nativeElement.textContent.includes('por empate')).toBe(positions.length > 5);
    }
  });
});
