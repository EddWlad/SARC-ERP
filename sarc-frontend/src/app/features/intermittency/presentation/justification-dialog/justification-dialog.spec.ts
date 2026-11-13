import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { JustificationDialog, JustificationDialogData } from './justification-dialog';

describe('JustificationDialog (intermitencias)', () => {
  const close = vi.fn();

  function render(submit: JustificationDialogData['submit']) {
    close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: MatDialogRef,
          useValue: { close, backdropClick: () => of(), keydownEvents: () => of() },
        },
        {
          provide: MAT_DIALOG_DATA,
          useValue: {
            title: 'Anular la ventana',
            action: 'Anular',
            target: 'Punto A (900001) · 2026-10-05 08:00',
            hint: 'El historial no se borra.',
            submit,
          } satisfies JustificationDialogData,
        },
      ],
    });
    const fixture = TestBed.createComponent(JustificationDialog);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const field = root.querySelector('textarea') as HTMLTextAreaElement;
    const save = root.querySelector('button[type="submit"]') as HTMLButtonElement;
    const type = async (text: string) => {
      field.value = text;
      field.dispatchEvent(new Event('input', { bubbles: true }));
      fixture.detectChanges();
      await fixture.whenStable();
    };
    return { fixture, root, field, save, type };
  }

  it('muestra el objetivo y no deja confirmar sin justificacion', async () => {
    const { root, save, type } = render(vi.fn());

    expect(root.textContent).toContain('Punto A (900001)');
    expect(root.textContent).toContain('El historial no se borra.');
    expect(save.disabled).toBe(true);

    await type('   ');
    expect(save.disabled).toBe(true);
  });

  it('envia la justificacion sin espacios sobrantes y cierra con el resultado', async () => {
    const submit = vi.fn().mockResolvedValue({ id: 'w1' });
    const { fixture, save, type } = render(submit);

    await type('  Duplicada del 5 de octubre  ');
    expect(save.disabled).toBe(false);
    save.click();
    await fixture.whenStable();

    expect(submit).toHaveBeenCalledWith('Duplicada del 5 de octubre');
    expect(close).toHaveBeenCalledWith({ id: 'w1' });
  });

  it('si el servidor rechaza el cambio muestra el motivo y conserva lo escrito', async () => {
    const submit = vi.fn().mockRejectedValue(new Error('La ventana cambió; recárguela.'));
    const { fixture, root, field, save, type } = render(submit);

    await type('Motivo válido');
    save.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('La ventana cambió');
    expect(field.value).toBe('Motivo válido');
    expect(save.disabled).toBe(false);
  });

  it('la justificacion tiene el maximo del servidor', () => {
    const { field } = render(vi.fn());

    expect(field.getAttribute('maxlength')).toBe('500');
  });
});
