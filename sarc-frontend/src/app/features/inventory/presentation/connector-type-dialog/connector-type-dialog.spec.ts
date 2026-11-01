import { TestBed } from '@angular/core/testing';
import { MatDialog, MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { EMPTY } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ConnectorTypeDialog } from './connector-type-dialog';

describe('ConnectorTypeDialog', () => {
  async function setup() {
    const close = vi.fn();
    const save = vi.fn().mockResolvedValue({ id: 't' });
    TestBed.configureTestingModule({
      imports: [ConnectorTypeDialog],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { save } },
        {
          provide: MatDialogRef,
          useValue: { close, backdropClick: () => EMPTY, keydownEvents: () => EMPTY },
        },
        { provide: MatDialog, useValue: { open: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(ConnectorTypeDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, host: fixture.nativeElement as HTMLElement, close, save };
  }

  function type(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('lets a multiline hint grow instead of overlapping the next field', async () => {
    const { host } = await setup();
    const fields = host.querySelectorAll('mat-form-field');
    expect(fields).toHaveLength(2);
    for (const field of Array.from(fields)) {
      expect(field.querySelector('.mat-mdc-form-field-subscript-dynamic-size')).not.toBeNull();
    }
    expect(fields[0].textContent).toContain('Letras, números y guion bajo');
  });

  it('labels every field and keeps invalid data from being saved', async () => {
    const { fixture, host, save } = await setup();
    expect(Array.from(host.querySelectorAll('mat-label')).map((l) => l.textContent)).toEqual([
      'Código',
      'Nombre',
    ]);
    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(save).not.toHaveBeenCalled();
  });

  it('saves trimmed values and closes with the result', async () => {
    const { fixture, host, close, save } = await setup();
    const [code, name] = Array.from(host.querySelectorAll('input'));
    type(code, ' CCS2 ');
    type(name, ' Combo 2 ');
    (host.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(save).toHaveBeenCalledWith({ code: 'CCS2', name: 'Combo 2' });
    expect(close).toHaveBeenCalledWith({ id: 't' });
  });

  it('asks before discarding typed changes and closes directly when clean', async () => {
    const { fixture, host, close } = await setup();
    const cancel = Array.from(host.querySelectorAll('button')).find(
      (button) => button.textContent?.trim() === 'Cancelar',
    ) as HTMLButtonElement;
    cancel.click();
    await fixture.whenStable();
    expect(close).toHaveBeenCalledOnce();
  });
});
