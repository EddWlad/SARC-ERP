import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { windowResponse } from '../../testing/window-fixtures';
import { ClosedWindow, CorrectionForm, CorrectionFormData, isClosed } from './correction-form';

// 13:00Z es 08:00 en la zona operativa (UTC-5).
const CLOSED = windowResponse({
  startAt: '2026-10-05T13:00:00Z',
  endAt: '2026-10-05T13:30:00Z',
  comment: 'Sin enlace',
}) as ClosedWindow;

describe('CorrectionForm', () => {
  const close = vi.fn();

  function render(window: ClosedWindow, submit: CorrectionFormData['submit'] = vi.fn()) {
    close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: MatDialogRef,
          useValue: { close, backdropClick: () => of(), keydownEvents: () => of() },
        },
        { provide: MAT_DIALOG_DATA, useValue: { window, submit } satisfies CorrectionFormData },
      ],
    });
    const fixture = TestBed.createComponent(CorrectionForm);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const inputs = Array.from(root.querySelectorAll<HTMLInputElement>('input'));
    const areas = Array.from(root.querySelectorAll<HTMLTextAreaElement>('textarea'));
    const save = root.querySelector('button[type="submit"]') as HTMLButtonElement;
    const type = async (field: HTMLInputElement | HTMLTextAreaElement, text: string) => {
      field.value = text;
      field.dispatchEvent(new Event('input', { bubbles: true }));
      fixture.detectChanges();
      await fixture.whenStable();
    };
    return { fixture, root, inputs, areas, save, type };
  }

  it('parte de los valores guardados en hora local y no deja guardar sin cambios', () => {
    const { root, inputs, save } = render(CLOSED);

    expect(inputs.map((input) => input.value)).toEqual([
      '2026-10-05',
      '08:00',
      '2026-10-05',
      '08:30',
    ]);
    expect(root.textContent).toContain('2026-10-05 08:00 a 2026-10-05 08:30');
    expect(root.textContent).toContain('Duración: 0:30');
    expect(save.disabled).toBe(true);
  });

  it('recalcula la duracion al corregir el fin y avisa cuando pasa a por revisar', async () => {
    const { root, inputs, type } = render(CLOSED);

    await type(inputs[3], '12:30');

    expect(root.textContent).toContain('Duración: 4:30');
    expect(root.textContent).toContain('quedará por revisar');
  });

  it('un fin anterior al inicio bloquea el guardado', async () => {
    const { root, inputs, areas, save, type } = render(CLOSED);

    await type(inputs[3], '07:30');
    await type(areas[1], 'Se equivocó la hora');

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('posterior al inicio');
    expect(save.disabled).toBe(true);
  });

  it('exige justificacion', async () => {
    const { inputs, save, type } = render(CLOSED);

    await type(inputs[3], '08:45');

    expect(save.disabled).toBe(true);
  });

  it('envia solo lo que cambio, con instantes de la zona operativa, y cierra con el resultado', async () => {
    const submit = vi.fn().mockResolvedValue(CLOSED);
    const { fixture, inputs, areas, save, type } = render(CLOSED, submit);

    await type(inputs[3], '08:45');
    await type(areas[1], '  Fin real según el reporte  ');
    save.click();
    await fixture.whenStable();

    expect(submit).toHaveBeenCalledWith({
      endAt: '2026-10-05T08:45:00-05:00',
      justification: 'Fin real según el reporte',
    });
    expect(close).toHaveBeenCalledWith(CLOSED);
  });

  it('corregir solo el comentario no toca los instantes', async () => {
    const submit = vi.fn().mockResolvedValue(CLOSED);
    const { fixture, areas, save, type } = render(CLOSED, submit);

    await type(areas[0], 'Enlace caído en la zona');
    await type(areas[1], 'Aclaración del operador');
    save.click();
    await fixture.whenStable();

    expect(submit).toHaveBeenCalledWith({
      comment: 'Enlace caído en la zona',
      justification: 'Aclaración del operador',
    });
  });

  it('solo las ventanas cerradas se corrigen: una abierta no pasa el filtro', () => {
    expect(isClosed(CLOSED)).toBe(true);
    expect(
      isClosed(
        windowResponse({
          state: 'OPEN',
          endAt: null,
          durationMinutes: null,
          provisionalMinutes: 20,
        }),
      ),
    ).toBe(false);
  });

  it('un comentario mas largo que el maximo no se envia', async () => {
    const { areas, save, type } = render(CLOSED);

    await type(areas[0], 'x'.repeat(501));
    await type(areas[1], 'Aclaracion');

    expect(save.disabled).toBe(true);
  });

  it('si el servidor rechaza la correccion conserva lo escrito y muestra el motivo', async () => {
    const submit = vi.fn().mockRejectedValue(new Error('Se solapa con otra ventana del punto.'));
    const { fixture, root, inputs, areas, save, type } = render(CLOSED, submit);

    await type(inputs[3], '08:45');
    await type(areas[1], 'Fin real');
    save.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect(root.querySelector('.form-alert')?.textContent).toContain('Se solapa');
    expect(inputs[3].value).toBe('08:45');
    expect(areas[1].value).toBe('Fin real');
  });
});
