import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { EMPTY } from 'rxjs';

import { MeasurementForm } from './measurement-form';

describe('MeasurementForm', () => {
  const blank = {
    periodStart: '',
    periodEnd: '',
    available: '',
    occupied: '',
    fault: '',
    offline: '',
    reportedPercent: '',
  };

  function setup() {
    const current = signal({
      equipmentName: 'Estación',
      ordinal: 1,
      connectorTypeName: 'CCS2',
    });
    const draft = signal({
      ...blank,
      periodStart: '2026-10-26',
      periodEnd: '2026-11-01',
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
      parseBlock: vi.fn(async () => true),
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

  it('solicita solo las fechas del conector, sin hora ni segundos', () => {
    const { fixture, host } = setup();
    const dates = host.querySelectorAll<HTMLInputElement>('input[type="date"]');
    expect(dates.length).toBe(2);
    expect(host.querySelector('input[type="datetime-local"]')).toBeNull();
    expect(dates[0].value).toBe('2026-10-26');
    expect(dates[1].value).toBe('2026-11-01');
    fixture.nativeElement.remove();
  });

  it('entrega a la fachada lo digitado antes de interpretar el bloque', async () => {
    const { fixture, host } = setup();
    const facade = TestBed.inject<{
      updateDraft: ReturnType<typeof vi.fn>;
      parseBlock: ReturnType<typeof vi.fn>;
    }>(MAT_DIALOG_DATA);
    const text = host.querySelector('textarea') as HTMLTextAreaElement;
    text.value = '163 hrs 35 mins - Disponible';
    text.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const parse = Array.from(host.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Interpretar'),
    ) as HTMLButtonElement;

    parse.click();
    await fixture.whenStable();

    expect(facade.updateDraft).toHaveBeenCalledWith(
      expect.objectContaining({ periodStart: '2026-10-26', periodEnd: '2026-11-01' }),
    );
    expect(facade.updateDraft.mock.invocationCallOrder[0]).toBeLessThan(
      facade.parseBlock.mock.invocationCallOrder[0],
    );
    expect(facade.parseBlock).toHaveBeenCalledWith('163 hrs 35 mins - Disponible');
    fixture.nativeElement.remove();
  });

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
