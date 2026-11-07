import { of } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { JustificationDialog } from './justification-dialog';

describe('JustificationDialog temporal', () => {
  it('conserva motivo y evidencia cuando backend rechaza la decisión', async () => {
    const close = vi.fn(),
      submitTemporal = vi.fn().mockResolvedValue(false);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: MatDialogRef,
          useValue: { close, backdropClick: () => of(), keydownEvents: () => of() },
        },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            title: 'Conciliar período',
            action: 'Guardar decisión',
            required: true,
            temporal: { referenceWeek: '2026-10-26' },
            submitTemporal,
            error: () => 'El registro cambió; recarga su versión.',
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(JustificationDialog);
    await fixture.whenStable();
    const fields = fixture.nativeElement.querySelectorAll('textarea');
    for (const [index, text] of ['Decisión explícita', 'Original semanal cotejado'].entries()) {
      fields[index].value = text;
      fields[index].dispatchEvent(new Event('input', { bubbles: true }));
    }
    await fixture.whenStable();
    const save = Array.from(fixture.nativeElement.querySelectorAll('button')).find((button) =>
      (button as HTMLElement).textContent?.includes('Guardar decisión'),
    ) as HTMLButtonElement;
    expect(save.disabled).toBe(false);
    save.click();
    await fixture.whenStable();
    expect(submitTemporal).toHaveBeenCalledOnce();
    expect(close).not.toHaveBeenCalled();
    expect(fields[0].value).toBe('Decisión explícita');
    expect(fields[1].value).toBe('Original semanal cotejado');
    expect(fixture.nativeElement.textContent).toContain('El registro cambió');
  });
});
