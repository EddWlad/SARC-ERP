import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { EquipmentFilter } from './equipment-filter';

describe('EquipmentFilter', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('empties the field when the filter is cleared from outside', async () => {
    const fixture = TestBed.createComponent(EquipmentFilter);
    const input = () => fixture.nativeElement.querySelector('input') as HTMLInputElement;
    fixture.componentRef.setInput('equipmentId', '');
    await fixture.whenStable();

    input().value = 'Punto';
    input().dispatchEvent(new Event('input'));
    fixture.componentRef.setInput('equipmentId', 'eq-1');
    await fixture.whenStable();
    expect(input().value).toBe('Punto');

    fixture.componentRef.setInput('equipmentId', '');
    await fixture.whenStable();

    expect(input().value).toBe('');
  });
});
