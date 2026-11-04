import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { EMPTY } from 'rxjs';

import { MeasurementForm } from './measurement-form';

describe('MeasurementForm', () => {
  const blank = { available: '', occupied: '', fault: '', offline: '', reportedPercent: '' };

  function setup() {
    const current = signal({
      equipmentName: 'Estación',
      ordinal: 1,
      connectorTypeName: 'CCS2',
    });
    const draft = signal({
      ...blank,
      available: '1296',
      occupied: '105',
      fault: '0',
      offline: '39',
    });
    const busy = signal(false);
    const facade = {
      current,
      draft,
      busy,
      conflict: signal(false),
      notice: signal(undefined),
      error: signal(undefined),
      updateDraft: vi.fn(),
      save: vi.fn(async () => {
        busy.set(true);
        await Promise.resolve();
        current.set({ equipmentName: 'Estación', ordinal: 2, connectorTypeName: 'CCS2' });
        draft.set({ ...blank });
        busy.set(false);
        return true;
      }),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: facade },
        { provide: MatDialog, useValue: { open: vi.fn() } },
        {
          provide: MatDialogRef,
          useValue: {
            close: vi.fn(),
            backdropClick: () => EMPTY,
            keydownEvents: () => EMPTY,
            disableClose: false,
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(MeasurementForm);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    return { fixture, host: fixture.nativeElement as HTMLElement };
  }

  it('keeps the keyboard inside the form after moving to the next connector', async () => {
    const { fixture, host } = setup();
    const next = Array.from(host.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Guardar y siguiente'),
    ) as HTMLButtonElement;

    next.focus();
    next.click();
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(host.querySelector('.target')?.textContent).toContain('Conector 2');
      expect(document.activeElement).toBe(host.querySelector('textarea'));
    });
    fixture.nativeElement.remove();
  });
});
