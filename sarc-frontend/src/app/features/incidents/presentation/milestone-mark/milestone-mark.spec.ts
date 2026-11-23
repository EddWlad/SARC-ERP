import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { MilestoneStatus } from '../../domain/incident-labels';
import { MilestoneMark } from './milestone-mark';

function render(status: MilestoneStatus, retroactive = false): HTMLElement {
  const fixture = TestBed.createComponent(MilestoneMark);
  fixture.componentRef.setInput('status', status);
  fixture.componentRef.setInput('retroactive', retroactive);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('MilestoneMark', () => {
  it.each([
    ['RECORDED', false, 'R', 'Registrado'],
    ['RECORDED', true, 'R*', 'Registrado con hora retroactiva'],
    ['NOT_APPLICABLE', false, 'N/A', 'No aplica'],
    ['NO_DATA', false, 'S/D', 'Sin dato'],
    ['PENDING', false, '—', 'Pendiente'],
  ] as const)('%s retroactivo=%s muestra %s', (status, retroactive, mark, text) => {
    const element = render(status, retroactive);
    expect(element.querySelector('.mark')?.textContent?.trim()).toBe(mark);
    expect(element.querySelector('.visually-hidden')?.textContent?.trim()).toBe(text);
  });

  it('oculta la abreviatura a los lectores de pantalla y deja el estado como texto', () => {
    const element = render('NO_DATA');
    expect(element.querySelector('.mark')?.getAttribute('aria-hidden')).toBe('true');
    expect(element.querySelector('.visually-hidden')).not.toBeNull();
  });
});
