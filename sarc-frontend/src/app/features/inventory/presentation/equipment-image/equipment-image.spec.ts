import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { EquipmentImage } from './equipment-image';

describe('EquipmentImage', () => {
  it('names the file input and keeps it out of the tab order behind its button', () => {
    const fixture = TestBed.createComponent(EquipmentImage);
    fixture.componentRef.setInput('name', 'Equipo');
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();

    const input = (fixture.nativeElement as HTMLElement).querySelector('input[type=file]');
    expect(input?.getAttribute('aria-label')).toBe('Archivo de imagen del equipo');
    expect(input?.getAttribute('tabindex')).toBe('-1');
  });
});
