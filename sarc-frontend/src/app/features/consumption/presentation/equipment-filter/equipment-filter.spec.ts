import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';

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

  it('selects a code-less equipment by UUID and displays its name', async () => {
    const fixture = TestBed.createComponent(EquipmentFilter);
    fixture.componentRef.setInput('equipmentId', '');
    const selected = vi.fn();
    fixture.componentInstance.equipmentChange.subscribe(selected);
    await fixture.whenStable();
    fixture.componentInstance['onSelect']({
      option: { value: { id: 'free-1', name: 'KIA Libre', assignedCode: null } },
    } as MatAutocompleteSelectedEvent);
    await fixture.whenStable();
    expect(selected).toHaveBeenCalledWith('free-1');
    expect((fixture.nativeElement.querySelector('input') as HTMLInputElement).value).toBe(
      'KIA Libre',
    );
  });
});
